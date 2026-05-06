# Task ID

TASK-MONO-047

# Title

Template Repository extraction — readiness checklist + extract-template.sh script

# Status

ready

# Owner

infra / monorepo

# Task Tags

- code
- adr
- onboarding

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Edge Cases
- Failure Scenarios

---

# Goal

Move the monorepo from **Phase 4 catalyst evaluation (1차 complete)** toward **Phase 5 Template Extraction**. Two deliverables:

1. **Authored extraction script** — `scripts/extract-template.sh <target-dir>` that produces a fresh single-project template repository from the current monorepo state. Until now this has only been a sketch in `TEMPLATE.md § Phase 5 — Template Extraction`.
2. **Readiness checklist** — explicit, machine-verifiable preconditions that must be true before invoking the script, plus a one-shot `scripts/verify-template-readiness.sh` that runs them.

Phase 5 extraction itself (the actual fork into a new GitHub repo) is **out of scope** — this task only authors the tooling and gating so the project owner can pull the trigger when the catalyst evaluation full-cycle completes (see `TASK-SCM-BE-002d` + `TASK-SCM-INT-001` 진입 후 + ≥ 1 month no-churn period).

---

# Scope

## In Scope

### scripts/extract-template.sh

A bash script (idempotent, dry-run capable) that produces a clean template repo at `<target-dir>`:

1. Fail fast if `<target-dir>` already exists and is non-empty.
2. Copy the shared library layer:
   - `.claude/` (entire tree)
   - `platform/` (entire tree)
   - `rules/` (entire tree)
   - `libs/` (entire tree)
   - `tasks/templates/` (only — root `tasks/{ready,in-progress,review,done}/` are not template content)
   - `docs/guides/` (entire tree)
   - `CLAUDE.md`, `TEMPLATE.md` (with note inserted at top: "this file is template-distributed; project-specific edits go in `projects/<name>/...`")
   - Root Gradle: `build.gradle`, `settings.gradle`, `gradle/`, `gradlew`, `gradlew.bat`, `gradle.properties` (if present)
   - Repo meta: `.gitignore`, `.gitattributes`, `.editorconfig`, `.npmrc` (if present)
3. Create an **empty single-project shell** at `<target-dir>/projects/<placeholder>/`:
   - `PROJECT.md.example` (with frontmatter scaffold + `## TODO` markers)
   - `README.md.example`
   - `apps/.gitkeep`
   - `specs/{contracts/{http,events},services,features,use-cases,integration}/.gitkeep`
   - `tasks/{backlog,ready,in-progress,review,done,archive}/.gitkeep`
   - `tasks/INDEX.md.example` (copy structural shell from `projects/scm-platform/tasks/INDEX.md`, strip task list)
   - `knowledge/.gitkeep`
   - `docs/.gitkeep`
   - `infra/.gitkeep`
   - `build.gradle.example`
   - `docker-compose.yml.example` (Traefik hostname routing template)
   - `.env.example`
4. Replace root `settings.gradle` with a template version that includes nothing under `projects/` (or includes a single placeholder commented out).
5. Replace root `README.md` with a template-introduction README (clearly marks the repo as a template).
6. Optionally `git init && git add -A && git commit -m "initial template from <monorepo-commit-sha>"` if `--init-git` flag passed.
7. Print a summary: file counts, total size, `<target-dir>` path.

Flags:
- `--dry-run`: list what would be copied, no actual writes
- `--init-git`: bootstrap git repo at `<target-dir>`
- `--verbose`: detailed progress

### scripts/verify-template-readiness.sh

A bash script (exit 0 = ready, exit non-zero = blockers) that runs the readiness checklist:

1. **Boundary check** (already exists in TEMPLATE.md § Validation L753): grep service-name patterns under shared paths. Exclude false positives:
   - `platform/error-handling.md § Domain-Specific Error Codes` — explicit cross-domain catalog by design (L134 framing)
   - `rules/domains/<d>.md` — domain-scoped, service-name examples are intentional
   - `libs/java-messaging` test fixtures + Javadoc `e.g.` examples
2. **Phase 4 outstanding** check: search `TASK-SCM-BE-002d` and `TASK-SCM-INT-001` task files — must be in `done/`, not `ready/`/`in-progress/`/`review/`.
3. **No-churn period**: `git log --since="1 month ago" -- libs/ platform/ rules/ .claude/ tasks/templates/ CLAUDE.md TEMPLATE.md` must be empty (or only the no-op extraction-readiness commit).
4. **CI baseline green**: latest main `Build & Test` + `Integration (...)` jobs SUCCESS (via `gh run list --branch main --workflow ci.yml --limit 1 --json conclusion`).
5. **All projects PROJECT.md valid**: for each `projects/<name>/PROJECT.md`, verify required frontmatter fields (`name`, `domain`, `traits`, `service_types`, `taxonomy_version`) and that `domain` + each `trait` exist in `rules/taxonomy.md`.
6. **No `PORT_PREFIX`** legacy anywhere under `projects/` (TEMPLATE.md § Validation L763).

Output: per-check PASS / FAIL with remediation hints. Final exit code reflects worst.

### TEMPLATE.md updates

- `§ Phase 5 — Template Extraction` body: replace bullet sketch with a one-line pointer to `scripts/extract-template.sh` + `scripts/verify-template-readiness.sh`.
- New `§ Phase 5 — Operational checklist` (or extend existing) section listing the verify-template-readiness checks in narrative form.

### tasks/INDEX.md updates

- ready/ list: remove this task, add to in-progress/ in impl PR; close to done/ in chore PR.

## Out of Scope

- The actual extraction (Phase 5 trigger) — that is a follow-up after the readiness checks pass + project owner decision.
- New GitHub repo creation (`Use this template` setup, repo settings).
- Periodic template-resync mechanism — sync from monorepo to extracted template after Phase 5 lands. This is Phase 6 work.
- Changes to `scripts/sync-portfolio.sh` — distinct purpose (per-project portfolio extraction, not template extraction).

---

# Acceptance Criteria

## scripts

1. `scripts/extract-template.sh --dry-run /tmp/template-dryrun` runs cleanly, lists every shared library file + every empty shell file, exits 0.
2. `scripts/extract-template.sh /tmp/template-real` produces a populated tree:
   - `ls /tmp/template-real/projects/<placeholder>/` shows the empty shell layout
   - `ls /tmp/template-real/.claude/` matches `ls .claude/` (count + name)
   - `ls /tmp/template-real/libs/` matches `ls libs/`
   - No `projects/wms-platform/` or other real project content present
3. `scripts/extract-template.sh --init-git /tmp/template-git && cd /tmp/template-git && git log --oneline | wc -l` shows exactly 1 commit referencing the source monorepo SHA.
4. `scripts/verify-template-readiness.sh` runs, prints a readable checklist, exits non-zero when invoked from current monorepo state (because Phase 4 outstanding tasks are not yet done).
5. After Phase 4 outstanding lands (TASK-SCM-BE-002d + TASK-SCM-INT-001 → done/), `scripts/verify-template-readiness.sh` exits 0.

## Documentation

6. `TEMPLATE.md § Phase 5 — Template Extraction` no longer contains the inline sketch; instead points to the two scripts.
7. `TEMPLATE.md § Validation` block updated to reference `verify-template-readiness.sh` (replacing the manual grep instructions).

## CI

8. No regression — `:check` + `Integration (...)` jobs PASS on main after merge. Path-filter (TASK-MONO-045) means CI mostly skips since changes are confined to `scripts/` + `TEMPLATE.md` + `tasks/`.

## Cleanup

9. Both temp dirs `/tmp/template-dryrun` and `/tmp/template-real` removed before merging the impl PR.

---

# Related Specs

- `TEMPLATE.md` — primary target document
- `tasks/INDEX.md` — root lifecycle (PR Separation Rule)
- `scripts/sync-portfolio.sh` — sibling extraction script (per-project, not template)
- `docs/guides/monorepo-workflow.md` — workflow guide that may reference these new scripts after merge
- `projects/scm-platform/tasks/ready/TASK-SCM-BE-002d-procurement-testcontainers-it.md` — Phase 4 outstanding 1
- `projects/scm-platform/tasks/ready/TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md` — Phase 4 outstanding 2
- ADR-MONO-002 — Phase 4 trigger (catalyst design)
- 향후 ADR-MONO-003 candidate — Phase 5 actual trigger decision (separate task / ADR after this lands)

---

# Implementation Notes

## Boundary check refinement

Current TEMPLATE.md L753 grep is too coarse — it false-positives on the intentional cases above. The refined grep in `verify-template-readiness.sh` should:

```bash
# Pseudo-code
violations=$(grep -rE "(auth-service|product-service|order-service|payment-service|inventory-service|master-service|community-service)" \
  platform/ rules/ .claude/ libs/ tasks/templates/ docs/guides/ CLAUDE.md TEMPLATE.md \
  --include='*.md' --include='*.java' \
  | grep -vE "^(platform/error-handling\.md|rules/domains/[a-z-]+\.md):" \
  | grep -vE ":\s*//.*e\.g\.|@code|spring\.application\.name=auth-service")
```

Adjust as the audit catches new false-positive patterns.

## Phase 4 outstanding detection

Read both spec files; they must be relocated from `ready/` to `done/`. Use `find` over the four lifecycle dirs. Any presence outside `done/` blocks readiness.

## No-churn 1-month gate

Use `git log --since="1 month ago" --pretty=format:%H -- <shared-paths>`. If the only commits are the ones from this very task (the script-authoring commit) plus possibly a `chore: update INDEX.md` from the merge, treat as no-churn (allow-list the script paths themselves). Document the rationale in the script body.

## extract-template.sh design

- Use `cp -r` for tree copies, but enumerate excluded subpaths explicitly (e.g., `tasks/{ready,in-progress,review,done,archive}` excluded from `tasks/`; only `tasks/templates/` copied).
- `<placeholder>` token used in `projects/<placeholder>/...` — the user is expected to rename to actual project name on first use.
- `.example` suffix on files the user must edit — prevents accidentally committing the scaffold as-is.

## Test strategy

- Pure-bash scripts; no JVM tests.
- `bats` (bash test framework) is overkill — use a single `scripts/test/test-extract-template.sh` that invokes both scripts on a temp dir, checks file counts/presence, then cleans up. Run as part of `:check` 가 아니라 manual verification (CI gating not required).
- Manual run during impl: dry-run + real run + verify-readiness on current monorepo (expect non-zero exit) + after-fixture run (mock TASK-SCM-* moved to done/, expect zero exit).

---

# Edge Cases

1. **Symlinks in shared paths** — `cp -r` follows symlinks by default; if any of `.claude/`, `platform/`, `rules/`, `libs/`, `docs/guides/` contain symlinks, decide explicit policy (preserve as symlink → `cp -r --no-dereference` or `cp -a`, vs flatten to file). Likely none present, but check.
2. **Hidden files in shared paths** — `cp -r` does not skip them; verify `.git*` dotfiles in `libs/` (none expected) but also `.tool-versions` if present.
3. **Gradle caches in `<target-dir>`** — must NOT copy `.gradle/`, `build/`, `.idea/`, `node_modules/` if they happen to exist. Explicit excludes.
4. **`tasks/templates/` vs `tasks/` lifecycle dirs** — only `templates/` is copied. `tasks/INDEX.md` itself is part of the lifecycle, not a template; an `INDEX.md.example` 가 새 프로젝트 안에 들어가는 게 맞고, 루트 `tasks/INDEX.md` 는 template 에서 reset 되어야 함 (placeholder).
5. **CLAUDE.md / TEMPLATE.md project-specific examples** — current CLAUDE.md does not name specific services (verified at Phase 4 catalyst eval); TEMPLATE.md mentions wms-platform / fan-platform as Phase 3/4 historical narrative. Decide policy: (a) preserve as-is (history is value), (b) sanitize on copy. Likely (a) — Phase history reads as documentation of strategy evolution. Annotate at top of copied file.
6. **`scripts/sync-portfolio.sh` 자체** — shared infrastructure, but it carries `PROJECT_REMOTES` map that is project-specific. Either copy it as `.example` or sanitize the map to placeholders on copy.
7. **`infra/traefik/`** — root-level shared dev infra. Currently lives under `infra/` not under shared paths list above. Decide: include as shared (it serves cross-project hostname routing, template-worthy) or exclude (template starts with no infra). Recommended: include with `<placeholder>` hostname rewrites.

---

# Failure Scenarios

## A. extract-template.sh produces an unusable repo

The user runs the script, then `cd /tmp/template-real && ./gradlew projects` fails to even resolve. RC = `settings.gradle` references nonexistent project paths.

Mitigation: `settings.gradle` template version includes only `libs/` (verified buildable in isolation) — projects: include block fully removed, instructed in comments.

## B. verify-template-readiness.sh false-positive blocking

The script blocks readiness due to a flagged file that is not actually a violation (intentional cross-domain catalog), and the user has no clean way to override.

Mitigation: explicit `--ignore=<path>` flag with logging — but ignored paths must remain visible in output for audit.

## C. extracted template gets out of sync with monorepo

Phase 6 work — periodic resync mechanism not in this task. Document as "manual rerun of `extract-template.sh` to a fresh dir + diff against existing template repo + cherry-pick".

## D. Hidden state under `projects/<placeholder>/`

If user invokes the script repeatedly to same `<target-dir>`, second invocation fails because dir non-empty (Step 1). User must `rm -rf` first or use a fresh dir. Documented in `--help`.

---

# Test Requirements

- Manual: dry-run + real run + verify-template-readiness from monorepo root, all exit codes documented in PR description.
- Manual: after-fixture (move TASK-SCM-* to done/ in a throwaway local commit, run verify-template-readiness, expect 0; revert the commit). Document outcome in PR description.
- No automated `:check` test (pure-bash scripts, manual gates appropriate at this maturity).

---

# Definition of Done

- [ ] `scripts/extract-template.sh` authored, executable, idempotent, dry-run capable
- [ ] `scripts/verify-template-readiness.sh` authored, exits with meaningful codes
- [ ] `TEMPLATE.md § Phase 5` body trimmed to script pointers + readiness narrative section added
- [ ] `tasks/INDEX.md` ready list updated (this task removed at impl-merge time)
- [ ] PR description includes: dry-run output, real-run summary (file counts), verify-readiness output (current state — expect FAIL with Phase 4 outstanding listed)
- [ ] No regression — `:check` + Integration jobs PASS
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — pure-bash scripting + doc edits, deterministic, no domain reasoning required. Could also be done by Haiku.
- **분량 추정**: small-medium — 2 bash scripts (~100-150 lines each) + TEMPLATE.md tweak + INDEX.md tweak. ~2-4 hours.
- **dependency**:
  - 선행: 없음 (본 task 가 Phase 5 진입 게이팅 자체를 정의)
  - 병렬 가능: TASK-SCM-BE-002d / TASK-SCM-INT-001 (Phase 4 outstanding) — 두 task 진행과 본 task 가 충돌 0.
- **after merge**: project owner 가 `scripts/verify-template-readiness.sh` 를 주기적으로 (e.g. monthly) 실행해 readiness 추적. 0 exit 시점에 Phase 5 trigger 결정 (별도 ADR-MONO-003 candidate).
- **Phase ordering**: 본 task 머지 후, Phase 4 outstanding 2 task 가 done/ 진입 + 1 month no-churn 충족 시점에 ADR-MONO-003 (Phase 5 trigger) 발행 → extract-template.sh 실행 → 별 GitHub repo 등록 → Phase 5 진입.
