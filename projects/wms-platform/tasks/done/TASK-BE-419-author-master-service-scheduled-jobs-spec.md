# TASK-BE-419 — Author missing master-service scheduled-jobs.md spec

**Status:** done
**Type:** docs (spec authoring — close doc debt)

## Goal

`master-service-api.md` § 6.7 points lot-expiration job design at
`specs/services/master-service/scheduled-jobs.md` "(to be authored in a later task)" — but that file was never written, while `LotExpirationScheduler` runs live in production. Author the spec and drop the "to be authored" deferral.

## Scope

- **New:** `projects/wms-platform/specs/services/master-service/scheduled-jobs.md` — document the daily lot-expiration job: cron `0 5 0 * * *`, strict `expiry_date < today` predicate, idempotency, outbox-coupled `master.lot.expired` event, top-level safety net, `wms.scheduler.lot-expiration.enabled` property gate + `runNow()` test seam, edge cases, v2 out-of-scope (timezone-aware boundaries).
- **Edit:** `master-service-api.md` § 6.7 — replace "(to be authored in a later task)" with a live relative link to the new spec.

## Acceptance Criteria

- [ ] **AC-1** — `scheduled-jobs.md` exists and accurately describes the live `LotExpirationScheduler` / `ExpireLotsBatchUseCase` / `LotExpirationBatchProcessor` behavior.
- [ ] **AC-2** — `master-service-api.md` § 6.7 links the new file (no "to be authored" wording).
- [ ] **AC-3** — Docs-only; no code touched. Spec matches implementation (cron, predicate, event name, property gate).

## Related Specs / Contracts

- `projects/wms-platform/specs/contracts/http/master-service-api.md` § 6.7
- `projects/wms-platform/specs/services/master-service/domain-model.md` §6 (lot expiry rule)
- Impl: `apps/master-service/.../scheduler/LotExpirationScheduler.java`, `application/port/in/ExpireLotsBatchUseCase.java`, `application/service/LotExpirationBatchProcessor.java`

## Edge Cases / Failure Scenarios

- Spec must match impl exactly (strict `<`, null expiry never expires, idempotent re-run, batch-crash → `failed=1`). Documented in the new file's edge-case table.
