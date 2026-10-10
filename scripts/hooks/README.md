# Pre-commit hook

A minimal pass-through hook. The full-cycle gate (previously blocking `src/**` commits
unless `.full-cycle/review.md` and `.full-cycle/verify.md` were fresh) was removed along
with the full-cycle lifecycle machinery.

Install (required once per clone — hooks are not versioned into `.git/`):

    cp scripts/hooks/pre-commit .git/hooks/pre-commit && chmod +x .git/hooks/pre-commit
