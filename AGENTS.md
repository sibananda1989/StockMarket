# AGENTS.md — Mandatory agent rules (one rule per line, no exceptions unless stated)

Read docs/PROJECT_MANIFEST.md FIRST to locate every file; stop reading other docs after it.
Use the plan (`plan` skill/command) and build (`build` skill) workflows for feature work; keep changes minimal and verified.
Before finishing any change, self-review it and run the repo's lint/typecheck/test commands; fix issues found before declaring done.
Never bypass the pre-commit hook except with explicit, justified `git commit --no-verify`.
Delegate investigation to agents instead of reading files yourself; never redo reads another agent already completed.
Delegation efficiency: pass prior findings forward (file paths, line numbers, conclusions) in every follow-up prompt so agents never re-read analyzed files.
Context reuse: reference prior session IDs and answer follow-ups from stored context in one shot instead of re-delegating.
Cite code as file_path:line_number when referencing any function or behavior.
Mimic existing conventions (style, frameworks, libraries, patterns) found in neighboring code before writing anything new.
Never assume a library is available; verify it is already used in the codebase first.
Follow security best practices; never expose, log, or commit secrets or keys.
Never commit unless explicitly asked; stage only intended files; never push unless asked.
After every change, run the repo's lint/typecheck/test commands; never claim done without verification evidence.
If blocked, report the blocker plus a concrete next step instead of stopping silently.
Keep responses short and direct; no preamble, postamble, or unrequested summaries.
Before installing any tool, browser, package, or SDK, first check the system for an existing equivalent (`/Applications`, `which`, caches); never run `install`/download blindly — use `AGENT_BROWSER_EXECUTABLE_PATH=/Applications/Google Chrome.app/Contents/MacOS/Google Chrome` for browser automation.
