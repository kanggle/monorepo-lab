# TASK-MONO-463 — Correct CLAUDE.md Repository Layout active-project count (5 → 8)

> **Renumbered 461 → 463**: TASK-MONO-461 was concurrently claimed and merged to `main` by another session
> (`controller-slice-test-naming-convention`, #2839) while this task was being filed. 462 remains this round's
> error-catalog task; 463 is this one.

**Status:** done

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Haiku 4.5 (single stale-fact correction in a shared file)

> Root-level because `CLAUDE.md` is a shared monorepo-level file (§ Task Rules: shared paths → root task). Deferred from the
> 2026-07-21 reconciliation audit, which flagged it but correctly routed it to a root task rather than editing inline.

---

## Goal

`CLAUDE.md` § Repository Layout line 21 reads `└── projects/<project>/    ← one directory per project (5 active)`. The repo
now has **8** active projects, so the parenthetical is stale.

## Scope

**In scope:** `CLAUDE.md:21` — change `(5 active)` to `(8 active)`.

**Out of scope:** any other CLAUDE.md content; the project list itself (not enumerated on that line).

## Acceptance Criteria

- **AC-0 (gate — recount; the tree wins)** — Recount `projects/*/PROJECT.md` at current `main`
  (`ls projects/*/PROJECT.md | wc -l`). At the time of filing there are 8: `erp-platform`, `ecommerce-microservices-platform`,
  `fan-platform`, `finance-platform`, `iam-platform`, `platform-console`, `scm-platform`, `wms-platform`. Use the recounted
  number, not the literal "8" if a project was added/removed since.
- **AC-1** — `CLAUDE.md:21` states the recounted active-project count.
- **AC-2** — Census: grep CLAUDE.md (and TEMPLATE.md/README.md if they mirror it) for other "5 active"/"5 projects"
  statements and fix or note them; a project-count claim tends to be duplicated.
- **AC-3** — No other change; this is a one-token doc correction.

## Related Specs
- None (CLAUDE.md is operating-rules, not a spec).

## Related Contracts
- None.

## Edge Cases
- CLAUDE.md is not classifier-blocked (unlike `.claude/hooks/` and `settings.json`) — MONO-424 landed CLAUDE.md edits — so
  this can be committed directly; attempt once and hand the patch over only on an actual block.
- Verify the count against `PROJECT.md` files, not against `apps/` dirs or docker-compose service counts.

## Failure Scenarios
- **F1 — hard-coding "8" without recounting.** Guarded by AC-0; the count is a live fact, not a constant.
