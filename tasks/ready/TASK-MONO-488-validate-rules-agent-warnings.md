# TASK-MONO-488 — `/validate-rules` (2026-07-29) agent-tier Warning fixes

**Status:** ready

**Type:** TASK-MONO
**Analysis model:** Sonnet 5 / **Recommended impl model:** Sonnet 5 (agent metadata + boundary documentation
fixes, no behavior change)

> Surfaced by the 2026-07-29 `/validate-rules` sweep, Warning tier. Sibling of `TASK-MONO-485`/`486`/`487`
> (Critical + platform + skill tiers, all merged).

---

## Goal

Nine independent agent-definition fixes across 7 agent files plus one supporting fix in
`platform/naming-conventions.md` (needed to actually back a claim two agents make).

## Scope

### In Scope

1. **`platform/naming-conventions.md`** — `backend-engineer.md` and `api-designer.md` both assert a
   `{UseCase}Command`/`{UseCase}Result` and `{UseCase}Request`/`{UseCase}Response` naming pattern "per
   `platform/naming-conventions.md`", but that file never stated it. Add both rows to the Classes table
   (verified this is an established repo convention already used in both agent files, not a new rule).
2. **`.claude/agents/common/backend-engineer.md`** — `capabilities` lists `event-publishing`,
   `event-consumption`, but `skills:` had zero `messaging/*` entries. Add
   `messaging/{event-implementation,outbox-pattern,idempotent-consumer,consumer-retry-dlq}`.
3. **`.claude/agents/common/event-architect.md`** — Design Rules § Messaging Patterns (outbox, idempotent
   consumer, DLQ, retry) maps directly onto the `messaging/*` skill cluster, but the file had no `skills:`
   field at all. Add it.
4. **`.claude/agents/common/devops-engineer.md`** — same gap for the `infra/*` skill cluster vs. its
   `capabilities` list (docker, kubernetes, ci-cd, terraform, monitoring, secrets, cost, service-mesh). Add
   `skills:`.
5. **`.claude/agents/common/refactoring-engineer.md`** — `service_types` includes `frontend-app`, but its only
   skill (`backend/refactoring`) is explicitly Java/Spring-scoped, and no frontend-refactoring skill exists in
   the catalog. Add an explicit note in the workflow step rather than implying the Java skill transfers.
6. **`.claude/agents/common/architect.md`** — new "Ownership Boundary" section covering the layer-violation
   *detection* overlap with both `refactoring-engineer` and `code-reviewer` (architect = design-time,
   refactoring-engineer = dedicated fix pass, code-reviewer = per-PR check via the shared review-checklist
   skill — same underlying rule, different trigger).
7. **`.claude/agents/common/refactoring-engineer.md`** — reciprocal one-line pointer back to architect's new
   boundary section.
8. **`.claude/agents/common/qa-engineer.md`** — its "Review Checklist" section duplicated
   `.claude/skills/review-checklist/SKILL.md` content (code-reviewer's single source of truth) without
   referencing it, including a "No layer violations" item that isn't qa-engineer's concern. Renamed to
   "Coverage Checklist", trimmed to test-completeness-only items, and added an explicit non-duplication note.
   `.claude/agents/common/code-reviewer.md` gets a reciprocal `Does NOT` bullet.
9. **`.claude/agents/common/qa-engineer.md`** — added a "Division of Labor" section: implementers write
   unit/slice/component tests as part of their own workflow step 4 (already true in
   `backend-engineer.md`/`frontend-engineer.md`, just never stated as a boundary); `qa-engineer` owns
   integration/contract/e2e tests and coverage verification, not a duplicate pass over unit tests.

### Out of Scope

- Critical/platform/skill-tier Warning findings (`TASK-MONO-485`/`486`/`487`, all merged).
- Info-tier findings from the same sweep (not filed).
- Creating a new frontend-refactoring skill (item 5 documents the gap; filling it is a larger, separate
  scoping decision, not a Warning-tier doc fix).

---

## Acceptance Criteria

- **AC-0 (gate)** — re-confirm each item live before editing (done at filing time via read).
- **AC-1** — `naming-conventions.md`'s Classes table includes both new rows; `backend-engineer.md` and
  `api-designer.md`'s existing claims now resolve against it (no further edit needed in those two files —
  verified their wording already matches the new rows).
- **AC-2** — `backend-engineer.md`'s `skills:` field includes all four `messaging/*` skills.
- **AC-3** — `event-architect.md` and `devops-engineer.md` both have a `skills:` field matching their stated
  capabilities.
- **AC-4** — `refactoring-engineer.md` explicitly flags the frontend-refactoring skill gap instead of
  implying `backend/refactoring/SKILL.md` covers TypeScript.
- **AC-5** — `architect.md`, `refactoring-engineer.md`, `code-reviewer.md`, and `qa-engineer.md` all state
  their boundary with the agent(s) they overlap with; no file duplicates another's checklist without a
  cross-reference.
- **AC-6** — no `.claude/agents/**` frontmatter field was removed or made invalid (`skills:` entries all
  resolve to real `.claude/skills/**/SKILL.md` paths — re-verified via Glob after edit).

## Related Specs

- `platform/naming-conventions.md`, `.claude/skills/INDEX.md`, `.claude/skills/review-checklist/SKILL.md`
- Prior art: `tasks/done/TASK-MONO-485-*.md`/`486-*.md`/`487-*.md` (same sweep, same "cross-reference / pointer,
  not new rule" discipline)

## Related Contracts

- None.

## Edge Cases

- Item 1 adds table rows to a shared spec — verified the pattern is already load-bearing convention (used by
  two existing agent files, not invented for this task) before adding it as normative.
- `.claude/agents/**` and `platform/**` are not classifier-blocked paths.

## Failure Scenarios

- **F1** — adding a `skills:` reference that doesn't resolve to a real file. Guarded by AC-6.
- **F2** — inventing a naming convention not actually followed in code. Guarded by the Edge Cases note (the
  convention was already asserted by two agent files before this task; this task only backs it in the
  canonical doc).
