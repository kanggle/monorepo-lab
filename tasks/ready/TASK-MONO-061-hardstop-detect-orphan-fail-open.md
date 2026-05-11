# Task ID

TASK-MONO-061

# Title

`hardstop-detect.ps1` — fail-open when file is outside any monorepo (HARDSTOP-01 false-positive fix)

# Status

ready

# Owner

monorepo

# Task Tags

- fix
- hooks

---

# Goal

Fix the HARDSTOP-01 false-positive in `.claude/hooks/hardstop-detect.ps1` discovered immediately post-merge of TASK-MONO-060 (PR #386, commit `73120990`).

When the edited file path is **entirely outside any monorepo** (e.g. `C:\Users\<u>\.claude\projects\...\memory\<m>.md` — the user-level auto-memory directory), the hook's `Get-RepoRoot` returns `$null`. The current behaviour treats this as HARDSTOP-01 (no PROJECT.md locatable). That's wrong — the hook's jurisdiction is the monorepo; outside it, the hook should fail-open (exit 0) silently.

The bug masks: auto-memory writes from agent sessions, Edit/Write to scratch files in `/tmp` or `$env:TEMP`, edits to other repositories elsewhere on the machine.

The intended HARDSTOP-01 case — file IS inside the monorepo (under `projects/<name>/`) but no `PROJECT.md` walking up — is already handled by the subsequent `projects/`-prefix check via `Find-ProjectMdAncestor`. The orphan-repo-root branch is redundant *and* over-broad.

---

# Scope

## In Scope

- `.claude/hooks/hardstop-detect.ps1` — replace the "no repo root → emit HARDSTOP-01" branch with `exit 0` (fail-open).
- `.claude/hooks/__tests__/hardstop-01-no-project-md.ps1` — adapt fixture to reflect the corrected semantics: HARDSTOP-01 fires only for paths under a monorepo `projects/<orphan>/` without `PROJECT.md`. Add a new negative fixture for a path entirely outside any monorepo.

## Out of Scope

- Other HARDSTOP detectors — no behavioural change.
- `Get-RepoRoot` helper — no change (still used by HARDSTOP-09 / -10 to resolve the architecture spec path; those return early if file isn't under `projects/`).

---

# Acceptance Criteria

- [ ] Writing to a path outside any monorepo (e.g. `$env:TEMP` scratch, `C:\Users\<u>\.claude\projects\...\memory\`) → hook emits no decision (silent allow).
- [ ] Writing to `projects/<orphan>/apps/<svc>/Foo.java` inside a monorepo where the orphan project carries no `PROJECT.md` → hook still fires HARDSTOP-01 (existing fixture remains green).
- [ ] All 15 prior fixture assertions still PASS.
- [ ] New negative fixture asserts the outside-monorepo case fails-open.

---

# Related Specs

- `platform/lint-remediation-message-standard.md` § Multiple simultaneous violations (no change needed — this is a detector bug, not a format change).
- `CLAUDE.md § Hard Stop Rules HARDSTOP-01` (no change needed — the canonical stanza body stays the same; only the hook's trigger boundary tightens).

# Related Skills

N/A.

---

# Related Contracts

None.

---

# Target Service

N/A — `.claude/hooks/` infrastructure only.

---

# Architecture

N/A — bug fix to existing detector.

---

# Implementation Notes

Two minimal edits in `hardstop-detect.ps1`:

```diff
 $repoRoot = Get-RepoRoot -StartPath $filePath
 if (-not $repoRoot) {
-    # No repo root found — file is genuinely orphaned. Emit HARDSTOP-01.
-    $stanza = @"
-[VIOLATION] HARDSTOP-01: ...
-"@
-    Write-Block $stanza
+    # File is entirely outside any monorepo — hook has no jurisdiction. Fail open.
+    exit 0
 }
```

The HARDSTOP-01 stanza emission stays — it now fires only via the `projects/`-prefix branch that already exists below, which is the correct trigger.

---

# Edge Cases

- **`Get-RepoRoot` returns null when file is at a path that *should* be inside the monorepo but the monorepo lacks `.git` / `CLAUDE.md+tasks/INDEX.md`**: edge case impossible in normal operation (the repo always has both); fail-open is acceptable.
- **Path under a different monorepo on the same machine** (e.g. another monorepo-lab clone): `Get-RepoRoot` resolves to that monorepo, then the projects/ check evaluates against ITS layout. Same behaviour as if the file were edited from that clone's working tree — correct.

---

# Failure Scenarios

- Fail-open suppresses HARDSTOP-01 entirely → no. The `projects/`-prefix branch still emits HARDSTOP-01 for the intended case (file under `projects/<orphan>/` without `PROJECT.md`).

---

# Test Requirements

`.claude/hooks/__tests__/hardstop-01-no-project-md.ps1` updated:

- Keep positive case: file under `<tempRoot>/projects/orphan-no-projectmd/apps/foo.md` with a `.git` dir at `<tempRoot>` → emit HARDSTOP-01.
- Add negative case: file at `<tempRoot>/some-orphan-dir/file.md` with NO `.git` ancestor → silent allow.

`pwsh .claude/hooks/__tests__/run-all.ps1` → 16 assertions all PASS.

---

# Definition of Done

- [ ] All Acceptance Criteria pass.
- [ ] Diagnostic runner shows 16 assertions PASS.
- [ ] PR description quotes the dogfooding context (false positive observed during TASK-MONO-060 close chore, post-merge).

---

# Provenance

Discovered post-merge of TASK-MONO-060 (PR #386, commit `73120990`) while writing a closure project memory file under the auto-memory directory `C:\Users\<u>\.claude\projects\...\memory\`. The hook fired HARDSTOP-01 because `Get-RepoRoot` couldn't resolve a monorepo ancestor for a path outside any monorepo — the second dogfooding incident for this hook series (first was the contextual Status-field move, fixed in `9a81b459`).

D4 OVERRIDE applies (OpenAI Harness gap series scope per ADR-MONO-003 § 3.4 risk 2 + ADR-MONO-006 § Provenance).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (1-line fix + 1 fixture case).
