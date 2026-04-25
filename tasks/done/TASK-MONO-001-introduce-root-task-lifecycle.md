# TASK-MONO-001 — Introduce repo-root task lifecycle for monorepo-level work

## Goal

Define and bootstrap a task lifecycle at the monorepo root so that
monorepo-level work (shared library, root build/CI, cross-project
infrastructure) can follow the same `ready → in-progress → review → done`
discipline that project-level work already does, instead of being
implemented directly via PR with no task artefact.

This is the bootstrap task. The PR that introduces the lifecycle and the
PR that lands this task doc are the same PR. The task lands in `done/`
immediately because the work it describes is precisely the act of
introducing the lifecycle.

## Background

`CLAUDE.md` § Task Rules states "Do not implement work without a task" and
"Do not implement tasks outside the target project's `tasks/ready/`".
Until now, `projects/<name>/tasks/` provided that lifecycle for project-
internal work, but monorepo-level work (e.g. PR #58 libs consolidation,
PR #59 wms CI stability, PR #61 sync-portfolio mapping flip, PR #64
ecommerce gradle wrapper removal) had no defined task home and was
implemented directly. The user flagged this in 2026-04-26.

## Scope

**In scope:**

1. Create `tasks/{ready,in-progress,review,done}/` with `.gitkeep`
   placeholders at repo root.
2. Write `tasks/INDEX.md` that mirrors the structure of
   `projects/<name>/tasks/INDEX.md` but scoped to monorepo-level work,
   including:
   - Lifecycle states and move rules
   - `TASK-MONO-XXX` task type
   - "When to use root vs project tasks" decision table
   - Self-bootstrap exception note pointing at this task
3. Update `CLAUDE.md`:
   - Add the new `tasks/{ready,...}` lifecycle dirs to the Repository
     Layout section's tree.
   - Reference root `tasks/INDEX.md` alongside the project-level
     `tasks/INDEX.md` mention in the Required Workflow / Task Rules
     sections.
4. Land this task doc in `tasks/done/` together with the structure (the
   self-bootstrap exception is documented in `tasks/INDEX.md` § Rule).

**Out of scope:**

- Retrospective task docs for already-merged monorepo-level PRs
  (#58, #59, #60, #61, #63, #64). The user's call: not worth the time,
  history in the merged commits and memory is sufficient.
- Updating `tasks/templates/` to add a monorepo-task template. The
  existing templates (backend / frontend / integration) skew toward
  service work; monorepo-level tasks are diverse enough (build, CI,
  scripts, rules promotion) that a single template would be lossy. New
  monorepo tasks can copy structure from existing project task docs in
  `tasks/done/` until a clear pattern emerges.

## Acceptance Criteria

1. `tasks/ready/`, `tasks/in-progress/`, `tasks/review/`, `tasks/done/`
   exist at repo root and are tracked in git (via `.gitkeep`).
2. `tasks/INDEX.md` exists and documents the lifecycle, naming, move
   rules, and the root-vs-project decision table.
3. `CLAUDE.md` references the new lifecycle in its Repository Layout
   section and at least one of the workflow / task-rules sections.
4. This task doc lives at `tasks/done/TASK-MONO-001-introduce-root-task-lifecycle.md`.
5. The next monorepo-level work (TASK-MONO-002+) starts in
   `tasks/ready/` per the new lifecycle.

## Related Specs / Contracts

- `CLAUDE.md` (this PR updates it)
- `projects/wms-platform/tasks/INDEX.md` (template / structural reference)
- `projects/ecommerce-microservices-platform/tasks/INDEX.md` (template / structural reference)

## Edge Cases

- **Self-bootstrap chicken-and-egg.** The task doc itself violates
  "tasks must not be implemented from `done/`" since it lands directly in
  `done/`. The exception is explicit in `tasks/INDEX.md` § Rule and
  applies only to this single bootstrap task.
- **Path collision with `tasks/templates/`.** The lifecycle dirs sit
  alongside `tasks/templates/`; both are tracked at repo root. No
  collision because the subdir names are disjoint.
- **Cross-project tasks.** The decision table says cross-project
  structural changes go in root `tasks/`, but a "drives a project change
  + lands a library promotion" pair is captured as two paired tasks (one
  in the project, one at root). The pairing convention is informal —
  reference the partner task ID in each task's Goal section.

## Failure Scenarios

- If a future contributor implements monorepo-level work without
  creating a task in `tasks/ready/`, that is a process violation
  identical to the one this task addresses; review feedback should ask
  for a retrospective task doc or block the PR until one is added.

## Outcome (2026-04-26)

Implemented as part of the same PR that introduces the lifecycle.
Acceptance criteria 1-4 all met by the PR's own contents. AC #5 is
verified once the next monorepo-level work (currently planned: #3
sync-portfolio live extraction) starts in `tasks/ready/` per the new
lifecycle.
