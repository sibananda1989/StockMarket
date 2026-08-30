# Pre-commit hook

Blocks any commit touching `src/**` unless `.full-cycle/review.md` and `.full-cycle/verify.md`
exist and are newer than the oldest staged src file (i.e., a full-cycle ran after your last edit).

Install (required once per clone — hooks are not versioned into `.git/`):

    cp scripts/hooks/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit

Bypass policy: `--no-verify` only with explicit justification; never as routine practice.
