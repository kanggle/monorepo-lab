# TASK-MONO-281 — Task-template consolidation: delete iam-platform mirror + orphan .claude task-template + align root 3 templates

Status: ready

## Goal

Fix the three findings from the 2026-06-16 task-template audit. Root `tasks/templates/` is the single shared, project-agnostic home for the backend/frontend/integration task templates (per `CLAUDE.md`, `TEMPLATE.md`, `platform/repository-structure.md`). Two redundant copies and one internal drift have accumulated:

1. **`projects/iam-platform/tasks/templates/`** holds an **exact mirror** of all three root templates. No other project has this. `TEMPLATE.md` line 374 already mandates its removal: "`projects/<name>/tasks/templates/` | **Delete.** Root `tasks/templates/` is shared." It is residue from the gap→iam standalone extraction; `sync-portfolio.sh` carries the root `tasks/templates/` into standalone repos via `SHARED_PATHS`, so the project-level copy is redundant in both the monorepo and the standalone publish.
2. **`.claude/templates/task-template.md`** is a generic, ~90%-identical copy of `tasks/templates/backend-task-template.md` that is **referenced by nothing** (repo-wide grep = 0; the canonical task templates are the three typed files under `tasks/templates/`, referenced across CLAUDE.md/TEMPLATE.md/docs). It is a dead, confusing duplicate.
3. The three root templates have **drifted**: `backend-task-template.md` carries a "# Required Sections (must exist)" block and `---` section separators, while `frontend-task-template.md` and `integration-task-template.md` have neither (all three do contain the seven required sections, but the reminder + formatting are inconsistent).

## Scope

In one atomic PR (monorepo-level — touches shared `tasks/templates/`, project `projects/iam-platform/`, and `.claude/templates/`):

1. **Delete** the three files under `projects/iam-platform/tasks/templates/` (`backend-task-template.md`, `frontend-task-template.md`, `integration-task-template.md`). Confirm no spec/doc/script references the project-level path before deletion.
2. **Delete** `.claude/templates/task-template.md` (orphan duplicate). Confirm zero references first. (`.claude/templates/` is NOT classifier-hard-blocked — only `.claude/hooks|agents|commands` are — so the edit/commit should pass like `.claude/config/`; if the commit is unexpectedly gated, hand the patch to the user.)
3. **Align** `frontend-task-template.md` and `integration-task-template.md` to `backend-task-template.md`: add the "# Required Sections (must exist)" block after the Task Tags section, and standardize the `---` section separators. Do not change the substantive section content (the seven required sections already exist in all three). Generalize the frontend `# Target App` placeholder `apps/web` or `apps/admin` → `apps/<app>` (remove the admin-dashboard-evoking example).

Out of scope: the other possibly-orphan `.claude/templates/` files (`completion-report-template.md`, `feature-spec-template.md`, `use-case-template.md`) — a broader `.claude/templates/` orphan sweep is a separate follow-up; this task touches only the task-template duplicate. No change to `adr-template.md` / `service-architecture-template.md` (both are actively referenced).

## Acceptance Criteria

- `projects/iam-platform/tasks/templates/` no longer exists (3 files deleted); `git ls-files 'projects/*/tasks/templates/*'` returns empty.
- `.claude/templates/task-template.md` no longer exists; repo-wide grep for it returns zero references (it was already unreferenced).
- `frontend-task-template.md` and `integration-task-template.md` each contain the "# Required Sections (must exist)" block and use `---` separators, matching `backend-task-template.md`'s structure; all three still contain the seven required sections (Goal / Scope / Acceptance Criteria / Related Specs / Related Contracts / Edge Cases / Failure Scenarios).
- Frontend `# Target App` uses a generic `apps/<app>` placeholder (no `apps/admin`).
- No behavior change to the task lifecycle; root `tasks/templates/` remains the single shared template home; `TEMPLATE.md` line 374's directive is now satisfied.
- No new project-specific content introduced into shared paths (HARDSTOP-03 clean).

## Related Specs

- `TEMPLATE.md` (line 374 — the "Delete project-level tasks/templates" directive this satisfies; line 44 — template inventory).
- `CLAUDE.md` § "Shared vs project boundary" + § Task Rules (the seven required sections).
- `platform/repository-structure.md` (`tasks/templates/` shared at root).
- `tasks/INDEX.md` (root task lifecycle; PR Separation Rule).
- `scripts/sync-portfolio.sh` — confirm `SHARED_PATHS` carries root `tasks/templates/` (so the iam project-level copy is redundant for standalone publish too).

## Related Contracts

- None (doc/template-only; no API/event contract touched).

## Edge Cases

- Standalone publish (`sync-portfolio.sh`): deleting the iam project-level templates must not break the published iam-platform repo — verify `SHARED_PATHS` injects root `tasks/templates/` into the standalone (it does per TASK-MONO-002), so the standalone keeps templates via the shared carry, not the project copy.
- `.claude/templates/task-template.md` deletion: re-confirm zero references at impl time (a new reference could have been added since the audit).
- Template alignment must be **structure-only** — do not invent new required sections or alter the seven canonical ones; backend is the reference shape.

## Failure Scenarios

- **Breaking standalone publish** — deleting iam templates without confirming the shared carry. Mitigated by the sync-portfolio `SHARED_PATHS` check in AC/Related Specs.
- **Deleting a referenced file** — removing `.claude/templates/task-template.md` while something references it. Mitigated by the re-confirm-zero-references AC.
- **Over-alignment** — rewriting frontend/integration section *content* instead of just adding the Required-Sections block + separators. Keep it structural.
- **`.claude/` commit gating** — unlikely for `.claude/templates/` (not hooks/agents/commands), but if the commit is gated, stage everything and hand the user the `git commit` per `env_classifier_claude_self_mod_block`.

## Notes

Surfaced by the 2026-06-16 task-template audit (the fourth in the same-day series: memory optimization → MONO-278 CLAUDE.md catalog trim → MONO-279 skills ADR-032/iam fixes → this). The audit confirmed root `tasks/templates/` is referenced as the shared canonical across ~20 docs; the iam mirror and the `.claude/templates/task-template.md` orphan are the only task-template duplicates. MONO-280 was in flight in a concurrent session at draft time, so this took MONO-281.
