import type { Plugin } from "@opencode-ai/plugin"
import { execFile } from "node:child_process"
import * as fs from "node:fs"
import * as path from "node:path"

type RunResult = {
  ok: boolean
  output: string
  durationMs: number
  exitCode?: number
  timedOut: boolean
  unavailable?: boolean
}

type CheckPlan = {
  label: string
  run: () => Promise<RunResult>
}

const EDIT_TOOLS = new Set(["edit", "write", "patch"])
const SKIP_DIRS =
  /(^|\/)(node_modules|target|dist|build|\.git|coverage|test-results|out|\.next|__pycache__)(\/|$)/
const SETTLE_MS = 150
const MVN_TIMEOUT_MS = 120_000
const QUICK_TIMEOUT_MS = 15_000

let queue: Promise<unknown> = Promise.resolve()

function serialize<T>(fn: () => Promise<T>): Promise<T> {
  const run = queue.then(fn, fn)
  queue = run.then(
    () => {},
    () => {},
  )
  return run
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

function extractFile(args: unknown): string | null {
  if (!args || typeof args !== "object") return null
  const record = args as Record<string, unknown>
  const direct = record.filePath ?? record.file_path ?? record.path
  if (typeof direct === "string" && direct.trim()) return direct.trim()
  const files = record.files
  if (Array.isArray(files)) {
    for (const entry of files) {
      if (entry && typeof entry === "object") {
        const candidate = (entry as Record<string, unknown>).filePath
        if (typeof candidate === "string" && candidate.trim()) return candidate.trim()
      }
    }
  }
  return null
}

function run(cmd: string, args: string[], cwd: string, timeoutMs: number): Promise<RunResult> {
  const start = Date.now()
  return new Promise((resolve) => {
    execFile(cmd, args, { cwd, timeout: timeoutMs, maxBuffer: 8 * 1024 * 1024 }, (error, stdout, stderr) => {
      const output = `${stdout ?? ""}${stderr ?? ""}`.trim()
      const durationMs = Date.now() - start
      if (!error) {
        resolve({ ok: true, output, durationMs, timedOut: false })
        return
      }
      const code = (error as NodeJS.ErrnoException).code
      if (code === "ENOENT" || code === "EACCES") {
        resolve({ ok: false, output: `${cmd} unavailable (${String(code)})`, durationMs, timedOut: false, unavailable: true })
        return
      }
      resolve({
        ok: false,
        output,
        durationMs,
        timedOut: (error as { killed?: boolean }).killed === true,
        exitCode: typeof code === "number" ? code : undefined,
      })
    })
  })
}

function planCheck(root: string, file: string): CheckPlan | null {
  const abs = path.isAbsolute(file) ? path.resolve(file) : path.resolve(root, file)
  if (!fs.existsSync(abs)) return null
  if (fs.statSync(abs).isDirectory()) return null

  const rel = path.relative(root, abs).split(path.sep).join("/")
  const insideRoot = !rel.startsWith("..") && rel !== ""
  if (rel && SKIP_DIRS.test(rel)) return null

  const ext = path.extname(abs).toLowerCase()
  const base = path.basename(abs)

  if (ext === ".java") {
    if (!insideRoot) return null
    if (!fs.existsSync(path.join(root, "pom.xml"))) return null
    return { label: "mvn -q test-compile", run: () => run("mvn", ["-q", "-DskipTests", "test-compile"], root, MVN_TIMEOUT_MS) }
  }
  if (ext === ".js" || ext === ".mjs" || ext === ".cjs") {
    return { label: `node --check ${base}`, run: () => run("node", ["--check", abs], root, QUICK_TIMEOUT_MS) }
  }
  if (ext === ".py") {
    return {
      label: `py-syntax ${base}`,
      run: () => run("python3", ["-c", "import ast,sys; ast.parse(open(sys.argv[1], encoding='utf-8').read())", abs], root, QUICK_TIMEOUT_MS),
    }
  }
  if (ext === ".json") {
    return {
      label: `json-parse ${base}`,
      run: () => run("node", ["-e", 'JSON.parse(require("fs").readFileSync(process.argv[1],"utf8"))', abs], root, QUICK_TIMEOUT_MS),
    }
  }
  return null
}

function tail(text: string, maxLines = 40, maxChars = 2500): string {
  const lines = text.split(/\r?\n/)
  let out = lines.slice(-maxLines).join("\n")
  if (out.length > maxChars) out = out.slice(out.length - maxChars)
  return out
}

function format(result: RunResult, label: string): string {
  const secs = (result.durationMs / 1000).toFixed(1)
  const head = "\n\n[auto-verify] "
  if (result.unavailable) return `${head}skipped — ${result.output}`
  if (result.ok) return `${head}PASS ${label} (${secs}s)`
  if (result.timedOut) return `${head}TIMEOUT ${label} (${secs}s)\n${tail(result.output)}`
  const exit = result.exitCode !== undefined ? `, exit ${result.exitCode}` : ""
  const tailText = tail(result.output)
  const hint =
    "\n\nIf this change-set has more edits pending, finish them all, then re-check before fixing errors reported here."
  return `${head}FAIL ${label} (${secs}s${exit})${tailText ? `\n${tailText}` : ""}${hint}`
}

const plugin: Plugin = async ({ directory, worktree }) => {
  const root = path.resolve(worktree || directory)
  return {
    "tool.execute.after": async (input, output) => {
      try {
        if (!EDIT_TOOLS.has(input.tool)) return
        const file = extractFile(input.args)
        if (!file) return
        const plan = planCheck(root, file)
        if (!plan) return
        const suffix = await serialize(async () => {
          await sleep(SETTLE_MS)
          const result = await plan.run()
          return format(result, plan.label)
        })
        output.output = `${output.output}${suffix}`
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error)
        output.output = `${output.output}\n\n[auto-verify] plugin error: ${message}`
      }
    },
  }
}

export default plugin
