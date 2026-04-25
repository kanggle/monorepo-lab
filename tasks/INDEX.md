# Tasks Index — Monorepo Root

This document defines the task lifecycle for **monorepo-level** work — changes
that touch the shared library layer (`libs/`, `platform/`, `rules/`, `.claude/`,
`tasks/templates/`, `docs/guides/`), monorepo infrastructure (root
`build.gradle`, `settings.gradle`, `.github/workflows/`, `scripts/`,
`TEMPLATE.md`, `CLAUDE.md`), or cross-project workflows where no single
project under `projects/<name>/` is the natural owner.

Project-internal work (anything inside `projects/<name>/apps/`, `specs/`,
`tasks/`, `knowledge/`, `docs/`, `infra/`) continues to use the
**project-level** task lifecycle defined in
`projects/<name>/tasks/INDEX.md`.

---

# Lifecycle

ready → in-progress → review → done

Only tasks in `ready/` may be implemented.

---

# Task Types

- `TASK-MONO-XXX`: monorepo infrastructure (build, CI, scripts, rules library, agents/skills/commands, shared platform docs)

Sequence numbers are global within the `MONO` namespace, starting at `001`.

---

# Move Rules

## (writing a new task) → ready
A new monorepo-level task may land directly in `ready/` when:
- the work is scoped to repo-root paths or cross-project concerns
- acceptance criteria are clear
- impact on `projects/<name>/` is enumerated (or explicitly "none")
- if it changes `CLAUDE.md`, `TEMPLATE.md`, `rules/`, or other shared
  contracts, the affected paths are listed in the task

## ready → in-progress
Allowed only when implementation starts.

## in-progress → review
Allowed only when:
- implementation is complete
- tests are added (where applicable — some monorepo tasks are pure
  configuration or documentation; the task should declare what counts as
  "verification" up front)
- shared spec / contract / rule updates are completed if required

## review → done
Allowed only after review approval.

### Review Rules
- Tasks in `review/` must not be re-implemented directly.
- If review reveals a bug or missing requirement, create a new fix task in `ready/` referencing the original task ID.
- Fix tasks must include the original task ID in their Goal section (e.g. "Fix issue found in TASK-MONO-002").
- Do not modify a task file after it moves to `review/` or `done/`.

---

# When to Use Root vs Project Tasks

| Scope | Use this lifecycle |
|---|---|
| Changes to `libs/`, `platform/`, `rules/`, `.claude/`, `tasks/templates/`, `docs/guides/`, `CLAUDE.md`, `TEMPLATE.md` | Root `tasks/` |
| Changes to root `build.gradle`, `settings.gradle`, `.github/workflows/`, root `package.json`, `scripts/sync-portfolio.sh` | Root `tasks/` |
| Cross-project changes (PR touches more than one `projects/<name>/` and the change is structural rather than feature-level) | Root `tasks/` |
| Changes inside a single `projects/<name>/` (apps, specs, services, contracts, project-internal docs/infra/scripts) | That project's `projects/<name>/tasks/` |
| Changes that span project tasks + library promotion (e.g. extracting common content from a project into `rules/`) | Pair: a project task that drives the change + a root task that lands the library promotion |

**When in doubt**: if the file paths in the change are purely under
`projects/<name>/`, use the project task lifecycle. If any path lies outside
that, use the root task lifecycle.

---

# Rule

Tasks must not be implemented from `in-progress/`, `review/`, or `done/`.
The single exception is a self-bootstrapping task that creates the root
lifecycle itself — see `done/TASK-MONO-001-introduce-root-task-lifecycle.md`.

---

# Task List

## ready

(empty)

## in-progress

(empty)

## review

(empty)

## done

- `TASK-MONO-001-introduce-root-task-lifecycle.md` — bootstrap of this lifecycle and `CLAUDE.md` integration. Self-referential; landed in the same PR that introduced the lifecycle. 2026-04-26.
- `TASK-MONO-002-sync-portfolio-dry-run-verification.md` — verified `scripts/sync-portfolio.sh --dry-run` is clean for both projects after Phase 7/8 changes. Recommendation: keep current `SHARED_PATHS` unchanged (root `tasks/` lifecycle correctly excluded). Live extraction deferred to a future TASK-MONO-003. 2026-04-26.
