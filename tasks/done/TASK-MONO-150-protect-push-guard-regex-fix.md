# Task ID

TASK-MONO-150

# Title

`.claude/hooks/protect-main-branch.ps1` regex over-match fix — the push-target guard `git\s+push.*\b(main|master)\b` false-blocks chained commands + hyphen-token branch names (validate-hooks audit, sole finding)

# Status

done

# Owner

monorepo (root tasks/ — shared `.claude/hooks/`)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **origin**: validate-hooks audit 2026-05-29 (read-only), follow-up to TASK-MONO-149. MONO-149 surfaced two hook bug classes — (A) a PreToolUse guard validating the Edit `new_string` fragment instead of the resulting file; (B) a regex over-matching across shell-operator chains. The audit scanned **all 9** `.claude/hooks/*.ps1` for both classes. Result: **8 clean, 1 finding** — `protect-main-branch.ps1` carries bug class B (the rest either correctly reconstruct post-edit content, e.g. `hardstop-detect.ps1` fixed in MONO-102, or are non-blocking / path-only).
- **prerequisite for**: nothing (tooling hygiene).
- **execution constraint (READ THIS)**: the impl edits `.claude/hooks/protect-main-branch.ps1`, a safety hook. The **auto-mode classifier hard-blocks the agent from both editing AND committing `.claude/` safety/config files even with explicit user approval** (observed on MONO-149; same layer as mass-`push --delete` / kill-process blocks). The exact patch is in § Scope below; **the user applies it + commits it directly** (or adds a settings permission rule). The agent does the audit, the task md, the lifecycle, and the push/PR/merge of non-`.claude/` parts.
- **spec-first**: spec PR (this task md + INDEX ready entry) → impl PR (the one-hook patch, user-applied) → close chore PR.
- **model**: 분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (single mechanical regex fix) / 리뷰=Opus 4.7.

---

# Goal

Make `protect-main-branch.ps1`'s push-target guard match `main`/`master` only as the **target ref of an actual `git push` segment**, eliminating two false-positive classes that currently block legitimate pushes: (1) the target match bleeding across shell operators (`&&`, `||`, `;`, `|`) into a later command, and (2) `main`/`master` matching as a hyphen-delimited token inside a feature-branch name. Behavior-preserving for true positives (push to remote `main`/`master`, force push, hard reset to origin/main).

# Scope

## In Scope

**Spec PR**: this task md + `tasks/INDEX.md` ready entry. No hook edit.

**Impl PR** — `.claude/hooks/protect-main-branch.ps1` ONLY (user-applied). Replace the first guard clause:

```powershell
    if ($command -match 'git\s+push.*\b(main|master)\b' -or
        $command -match 'git\s+push\s+--force(?!-with-lease)' -or
        $command -match 'git\s+push\s+-f\b' -or
        $command -match 'git\s+reset\s+--hard\s+origin/(main|master)') {
```

with a segment-scoped, ref-position-scoped match:

```powershell
    # TASK-MONO-150: scope the main/master target match to the actual `git push`
    # segment + ref position. The previous `git\s+push.*\b(main|master)\b`
    # matched across shell-operator chains (e.g. `git push origin feat &&
    # gh pr create --base main`) and against branch names with "main" as a
    # hyphen token (e.g. `feature-main-fix`) — both false positives. Split on
    # shell operators; within a `git push` segment, match main/master only as a
    # ref argument: preceded by whitespace or `:` and followed by whitespace or
    # end (covers `origin main`, `HEAD:main`, `:main`; rejects `main:feature`
    # (pushing FROM main) and `feature-main` (branch name)).
    $pushTargetsMainMaster = $false
    foreach ($seg in ($command -split '\s*(?:&&|\|\||;|\|)\s*')) {
        if ($seg -match 'git\s+push\b' -and $seg -match '(?:\s|:)(main|master)(?:\s|$)') {
            $pushTargetsMainMaster = $true
            break
        }
    }
    if ($pushTargetsMainMaster -or
        $command -match 'git\s+push\s+--force(?!-with-lease)' -or
        $command -match 'git\s+push\s+-f\b' -or
        $command -match 'git\s+reset\s+--hard\s+origin/(main|master)') {
```

The `--force` / `-f` / `reset --hard origin/(main|master)` clauses and the entire HEAD-based implicit-target block (the `TASK-MONO-135` section) are **byte-unchanged** — only the first target-ref clause is rescoped.

## Out of Scope

- **The other 8 hooks** — audit-confirmed clean; byte-unchanged. (`rule-consistency-check.ps1` was fixed in MONO-149; `hardstop-detect.ps1` already reconstructs post-edit content per MONO-102; `spec-check`/`format-check`/`test-on-edit`/`verify-worktree-isolation`/`task-completed`/`notify` do not block on a validated fragment or a command-chain regex.)
- **The `--force` / `-f` / `reset` clauses** — they are already specific (`git push --force`, `git push -f`, `git reset --hard origin/main`); not rescoped here to keep the change minimal and avoid false-negatives.
- **The portfolio-sync / project-template allowlists** and the HEAD-based block — byte-unchanged.

# Acceptance Criteria

- **AC-1 (spec PR atomic)**: this task md + INDEX ready entry land with no hook edit.
- **AC-2 (chained-command false positive gone)**: `git push -u origin task/x && gh pr create --base main` is NOT blocked.
- **AC-3 (hyphen-token false positive gone)**: `git push origin feature-main-fix` is NOT blocked.
- **AC-4 (true positives preserved)**: each of `git push origin main`, `git push -u origin main`, `git push origin HEAD:main`, `git push origin feature:master`, `git push --force origin x`, `git push -f origin x`, `git reset --hard origin/main` IS still blocked.
- **AC-5 (push-FROM-main allowed)**: `git push origin main:feature` (pushing local main to a remote feature branch) is NOT blocked (it does not target remote main).
- **AC-6 (scope-lock)**: `git diff origin/main` touches only `.claude/hooks/protect-main-branch.ps1` (+ task lifecycle files); the `__tests__/protect-main-branch.ps1` fixture is updated only if it asserts the old over-broad behavior.
- **AC-7 (tests pass)**: `.claude/hooks/__tests__/run-all.ps1` (or at least `protect-main-branch.ps1` fixture) passes; add cases for AC-2/AC-3/AC-5 if not present.

# Related Specs

- `.claude/hooks/protect-main-branch.ps1` — the file fixed.
- `.claude/hooks/__tests__/protect-main-branch.ps1` — the fixture to extend (AC-7).
- `.claude/hooks/README.md` — hook authoring + safety-rail conventions.
- `CLAUDE.md` § Cross-Project Changes (branch-name constraint) + § Git Safety Protocol — the rules this guard enforces.

# Related Contracts

- None. Tooling only.

# Edge Cases

- **Quoted `main` in a non-push segment** — `git push origin feat && echo "main"` → segment split isolates the `git push` segment (no main) from the `echo` segment (no `git push`) → not blocked. ✓
- **`main:feature` vs `feature:main`** — only the latter (target = remote main) is blocked; the former (source = local main) is allowed (AC-5). The `(?:\s|:)(main|master)(?:\s|$)` anchor distinguishes them.
- **Self-test push** — the branch implementing this must NOT carry `main`/`master` as a hyphen token (else the *current, unpatched* guard false-blocks the impl push). Use a token-free branch name (e.g. `task/mono-150-push-guard-regex-fix`).
- **Classifier block** — the impl edit + commit of `protect-main-branch.ps1` is agent-hard-blocked; user applies + commits.

# Failure Scenarios

- **A true-positive push to main slips through** → AC-4 fail; the guard must still block `git push origin main` and refspec targets ending in `:main`.
- **Another hook edited** → AC-6 fail; this is the single-hook fix.
- **Regex introduces a false-negative on `--force`/`reset`** → out of scope; those clauses are byte-unchanged.

# Verification

1. Spec PR: this md + INDEX ready entry; no hook edit.
2. Impl PR (user-applied): `protect-main-branch.ps1` only; run `.claude/hooks/__tests__/run-all.ps1`; manually confirm AC-2..AC-5 via the fixture or a dry-run harness.
3. CI `changes` fast-lane GREEN.
4. BE-303 3-dim at close chore.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (single-hook regex rescope) / 리뷰=Opus 4.7 (AC-2/3/5 false-positive gone + AC-4 true-positive preserved + AC-6 scope-lock + AC-7 fixtures + BE-303 3-dim).
