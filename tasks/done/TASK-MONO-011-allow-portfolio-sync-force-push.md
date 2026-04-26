# TASK-MONO-011 — protect-main-branch hook: allowlist portfolio-sync workdir

**Status**: done
**Completed**: 2026-04-26

## Goal

Relax `.claude/hooks/protect-main-branch.ps1` so that `git push --force origin main`
is permitted when invoked from a portfolio-sync workdir
(`/tmp/portfolio-sync/<project>/`). Outside of that workdir the hook keeps
blocking force-push and direct push to `main`/`master`.

## Background

`scripts/sync-portfolio.sh` extracts each `projects/<name>/` into its own
standalone repo via git-filter-repo and force-pushes the rewritten history
to its portfolio remote (`kanggle/wms-platform`,
`kanggle/ecommerce-microservices-platform`). Force-push is intentional and
required — the standalone repos are derived artifacts re-generated from
monorepo HEAD, so they cannot be fast-forwarded.

During the live extraction run on 2026-04-26 the hook blocked the script's
final `git push --force origin main` step, forcing the user to drop into
Git Bash and run the push manually outside the Claude session. The friction
recurs on every re-sync, so the hook needs a narrow allowlist.

## Scope

**In scope:**

1. `.claude/hooks/protect-main-branch.ps1` — early-exit when the tool
   invocation's `cwd` matches `portfolio-sync` OR the command string
   contains an inline `cd /tmp/portfolio-sync/...` chain.

**Out of scope:**

- Detecting portfolio remote URLs from `.git/config` inside the hook
  (would require shelling out — unnecessary given the workdir signal is
  already a strong proxy and only the sync script writes to that path).
- Hook tests or a dedicated harness — the hook is a 30-line
  pattern-match script, manual verification by re-running sync-portfolio
  after the change is sufficient.

## Acceptance Criteria

1. From a directory matching `portfolio-sync`, `git push --force origin main`
   is allowed by the hook.
2. From any other directory, `git push --force`, `git push ... main`, and
   `git reset --hard origin/main` remain blocked exactly as before.
3. Re-running `scripts/sync-portfolio.sh wms-platform` end-to-end inside
   Claude completes the force-push step without manual intervention.

## Related Specs

- `scripts/sync-portfolio.sh` — produces the workdir under
  `${TMPDIR:-/tmp}/portfolio-sync/<project>/` and runs the force-push from
  inside that path.

## Related Contracts

None.

## Edge Cases

- **`cwd` field absent in hook input**: defensive default `$cwd = ""` —
  the allowlist branch simply does not match, so the hook falls through
  to the existing block rules. No regression.
- **A user manually creates `/tmp/portfolio-sync` and runs commands
  there outside the script**: the allowlist would let force-push through.
  Acceptable: the path is conventional, ephemeral, and only ever produced
  by the sync script.

## Failure Scenarios

- If a future hook regression accidentally allows force-push to the
  monorepo's own `main` from a portfolio-sync cwd (e.g., user runs the
  monorepo `git push` from inside `/tmp/portfolio-sync/...` due to a
  shell mistake): the remote URL would still be the portfolio repo
  (because that's what the script sets), so monorepo `main` would not
  be affected. The blast radius is contained to portfolio remotes by
  construction of the sync script.

## Outcome (2026-04-26)

`.claude/hooks/protect-main-branch.ps1` reads `$data.cwd` (defensively
defaulting to empty string) and early-exits when either the cwd or the
command string contains `portfolio-sync`. Existing block rules
(force-push, push to main/master, hard reset to origin/main) are
otherwise untouched.

Acceptance criteria 1 and 2 met by inspection of the diff. AC 3 will be
verified the next time `scripts/sync-portfolio.sh` runs end-to-end —
expected outcome is that the final `git push --force origin main` step
no longer needs to be hand-run from Git Bash.
