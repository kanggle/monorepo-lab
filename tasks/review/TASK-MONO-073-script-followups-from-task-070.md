# Task ID

TASK-MONO-073

# Title

verify-template-readiness Check 6 hang fix + extract-template.sh `git init -b main` — TASK-MONO-070 § Edge Cases follow-ups

# Status

review

# Owner

monorepo

# Task Tags

- fix
- tooling
- followup

---

# Goal

Land the two small script fixes recorded as follow-ups in [TASK-MONO-070 § Edge Cases](../done/TASK-MONO-070-phase-5-launch-execution.md) during the Phase 5 launch:

1. **`scripts/verify-template-readiness.sh` Check 6 hang** — observed twice during launch dress rehearsal; script hung after Check 5 without producing Check 6's PASS/FAIL or final summary. Root cause: file-by-file `grep` loop over 6,545 tracked files under `projects/`, with per-file process spawn overhead on Windows/Git Bash making the loop take many minutes.

2. **`scripts/extract-template.sh` `git init -b main`** — currently runs plain `git init` which produces a `master` branch (depends on local git config `init.defaultBranch`). The launch-day operator had to `git branch -m master main` before pushing to `kanggle/project-template` because GitHub default is `main`. Adding `-b main` removes the rename step.

Both are pure quality-of-life fixes — no behaviour change, no contract impact. Bundled because they share scope (Phase 5 launch follow-ups) and trigger.

---

# Scope

## In Scope

### A. `scripts/verify-template-readiness.sh` — Check 6 performance fix

Replace the per-file `grep` loop with two `git grep` passes (one for `*.md` prose pattern, one for non-markdown active-config pattern). `git grep` operates on the index in a single pass via git's internal parallelisation; orders of magnitude faster than spawning grep N times.

Measured: 26s end-to-end after fix (Check 1+2+3+4+5+6 + summary), vs hangs indefinitely before (manual TaskStop at ~5 min in launch session).

Behaviour preservation:
- Same patterns (markdown: `^[[:space:]]*[A-Z0-9_]*PORT_PREFIX[[:space:]]*=`; non-markdown: assignment OR interpolation)
- Same comment-line exclusion (`^[^:]+:[0-9]+:[[:space:]]*(#|//|\*)`)
- Same output format
- Same PASS / FAIL signalling

### B. `scripts/extract-template.sh` — `git init -b main`

Replace `git -C "$TARGET_DIR" init --quiet` with `git -C "$TARGET_DIR" init -b main --quiet` (with fallback to plain init + `symbolic-ref HEAD refs/heads/main` for `git < 2.28`).

Update the `--dry-run` message correspondingly.

Update the success log line ("Git repository initialised") to mention `(1 commit on main)`.

### C. Inline comment justifications

Both fixes annotated with one-line `# TASK-MONO-073` references explaining why the change exists (for future readers wondering "why two passes?" or "why `-b main`?").

## Out of Scope

- Other Check 6 logic changes (pattern, comment exclusion, etc.).
- `git ls-files` removal from other checks (Check 1 uses it differently — `git grep` doesn't fit there).
- `extract-template.sh` behaviour beyond the branch name.
- TASK-MONO-070 follow-up beyond these two specific items.
- Performance fix for Check 1 (uses different scanning pattern with intentional false-positive ignore list).

---

# Acceptance Criteria

- [ ] `scripts/verify-template-readiness.sh` Check 6 completes in < 60s on a clean monorepo checkout.
- [ ] Check 6 PASS / FAIL output unchanged in format from pre-fix (PASS message text, FAIL listing format, remediation hint).
- [ ] `scripts/extract-template.sh --init-git <dir>` produces a git repo with HEAD on `main` (not `master`).
- [ ] `git -C <dir> branch` shows `main` as the only branch after init.
- [ ] `bash scripts/verify-template-readiness.sh` exit code matches pre-fix behaviour: 0 if all checks PASS, blocker count otherwise (Check 3 still FAIL per ADR-MONO-003a § D4 diagnostic-only semantics; this fix does not change that).
- [ ] CI green.

# Related Specs

- `tasks/done/TASK-MONO-070-phase-5-launch-execution.md` § Edge Cases (origin)
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § D4 (verify is diagnostic-only — this fix preserves that)
- `scripts/verify-template-readiness.sh` (file under modification)
- `scripts/extract-template.sh` (file under modification)

# Related Contracts

None.

# Edge Cases

- **`git grep` not finding files** — `git grep` only scans tracked files (same as the previous `git ls-files | xargs grep` approach), so behaviour is preserved.
- **`git init -b main` on git < 2.28** — handled by fallback `git init` + `symbolic-ref HEAD refs/heads/main`. Affects systems with old git only; modern git ≥ 2.28 (released 2020-07) is universal in CI and current dev environments.
- **Empty git repo behaviour** — `git init -b main` works the same as `git init` for empty repos; the `-b` flag only sets the initial branch name. No commits exist yet, so `symbolic-ref` is what `-b` essentially does internally.

# Failure Scenarios

- **Reviewer asks "is this OVERRIDE-class?"** — yes. Scripts under `scripts/` are shared paths (TASK-MONO-070 § D2 verified this). Pre-authorised under ADR-MONO-003a § D1.2 (this is a follow-up to Phase 5 launch which inherits the OVERRIDE umbrella) OR under § D1.1 B-refactor adjacent (cleanup of shared tooling). Either framing works; the bundle is small enough that the meta-rule § D3 implicit "minor follow-up to recent OVERRIDE-class work" applies without fresh ADR.
- **Reviewer asks "why bundled, not 2 separate PRs?"** — both fixes share trigger (Phase 5 launch friction) + audience (anyone running these scripts) + scope (script-only, no docs/test diff). Per `feedback_pr_bundling.md`, bundling is preferred when scope is coherent.
- **Reviewer asks "any test coverage?"** — both scripts are tooling, not application code. Verification = manual dry run in this task spec's Implementation Plan (already executed during authoring; 26s end-to-end measured).

---

# Implementation Plan

1. Fix `scripts/verify-template-readiness.sh` Check 6 (replace per-file loop with `git grep` passes).
2. Fix `scripts/extract-template.sh` `git init -b main` (with `git < 2.28` fallback).
3. Run `bash scripts/verify-template-readiness.sh` end-to-end — confirm < 60s total + Check 6 PASS.
4. Run `bash scripts/extract-template.sh --dry-run /tmp/test-073` — confirm dry-run output mentions `git init -b main`.
5. (Optional sanity) Run `bash scripts/extract-template.sh --init-git /tmp/test-073-real` — confirm `git -C /tmp/test-073-real branch` shows `main`. Clean up after.
6. Single bundled commit.
7. Lifecycle: ready → review on PR creation.
8. Push branch + open PR.

# Estimated Cost

- Files: `verify-template-readiness.sh` (~30 LOC delta) + `extract-template.sh` (~10 LOC delta) + this task file. Total ≈ 200 LOC including task spec.
- CI: path-filter `scripts/` → typically lightweight; may also trigger `Build & Test` due to script files being shared. ~20-30s baseline.
- Time: ~30 min total (already inspected + implemented during authoring).

분석=Opus 4.7 / 구현=Opus 4.7 (small but careful — Check 6 fix preserves output semantics; `git init -b main` fallback handles edge case correctly).
