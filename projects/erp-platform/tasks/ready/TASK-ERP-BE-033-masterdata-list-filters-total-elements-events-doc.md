# TASK-ERP-BE-033 — Masterdata list: implement documented filters + real totalElements; fix events-doc class name

**Status:** ready

**Type:** TASK-ERP-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 for AC-1/AC-2 (cross-cutting change across 5 entities + pagination semantics); Sonnet 4.6 for AC-3 (doc-only)

> Filed from the 2026-07-21 reconciliation audit round-2 re-measurement (origin/main `aa535c22b`). The audit said "5 filters
> missing"; re-measurement corrects this — **3** are missing (`asOf`, `active`, `parentId`); `page`/`size` are implemented.
> The Kafka envelope drift for this service is a **separate**, already-filed task (`TASK-ERP-BE-032`, review/) — do not touch it here.

---

## Goal

The masterdata list contract promises query filters and a real total-row count that the code does not provide. Either
implement them (preferred — the spec is the source of truth) or, if filtering is intentionally deferred, narrow the spec.
Plus one trivial events-doc class-name correction.

## Scope

**In scope:**

1. **List filters (AC-1) — functional gap.** `specs/contracts/http/masterdata-api.md:96` documents
   `?asOf=&active=true|false&parentId=&page=&size=`. The list controllers bind only `page`/`size`
   (`DepartmentController.java:62-69`; same shape in `EmployeeController`, `JobGradeController`, `CostCenterController`,
   `BusinessPartnerController`), and `MasterdataApplicationService.listDepartments` (`:230-234`) forwards only
   `(tenantId, page, size)` — `asOf`/`active`/`parentId` never reach the query. Applies to all 5 masterdata entities.
2. **totalElements (AC-2) — pagination semantics.** Spec `masterdata-api.md:33,57-58` describes `PageMeta.totalElements` as a
   real total-row count, but `ApiEnvelope.java:35-42` (`ofList`) sets `totalElements = data.size()` (the current page length),
   because repositories return `List<T>`, not `Page<T>` (e.g. `DepartmentJpaRepository.java:16`), so no count query runs.
3. **events-doc class name (AC-3) — doc fix.** `specs/contracts/events/erp-masterdata-events.md:4-5` names
   `MasterdataOutboxPollingScheduler extends OutboxPollingScheduler`; the code is
   `MasterdataOutboxPublisher extends AbstractOutboxPublisher<MasterdataOutboxJpaEntity>` (`MasterdataOutboxPublisher.java:42`;
   the code's own javadoc `:33-34` and `OutboxConfig.java:23-24` already record the v1→v2 migration). Update the spec line.

**Out of scope:** the event envelope shape (`TASK-ERP-BE-032`); any masterdata write-path change.

## Acceptance Criteria

- **AC-0 (gate — re-measure; code wins)** — Re-confirm at current `main`: exactly which of `asOf`/`active`/`parentId` are
  unbound (per entity), and that `ofList` still assigns `data.size()`. If some filters were added since, scope to the remainder.
- **AC-1 (RULING + implement)** — Decide **implement filters** (thread `asOf`/`active`/`parentId` as optional `@RequestParam`
  through controller → service → repository query for all 5 entities, with tests) **or** **narrow the spec** (remove the
  unimplemented params from `masterdata-api.md:96` and note them as out-of-scope). State the choice + rationale in the PR body.
  Implementing is preferred unless there is a spec/ADR reason filtering is deferred.
- **AC-2 (RULING + implement)** — Either make `totalElements` a real total (switch the 5 repositories to `Page<T>` or add a
  `count(...)` query and set `PageMeta` from it) **or**, if page-length is intentional, correct the spec's `PageMeta`
  description and rename the field to reflect page semantics. Cover with a test that lists across >1 page and asserts the total.
- **AC-3** — `erp-masterdata-events.md:4-5` reads `MasterdataOutboxPublisher extends AbstractOutboxPublisher<MasterdataOutboxJpaEntity>`,
  matching the code's javadoc wording.
- **AC-4** — All 5 masterdata entities are treated consistently (AC-1/AC-2) — no half-migration where one entity paginates
  correctly and four don't. Existing masterdata read tests stay green (or are updated with the new semantics).

## Related Specs
- `projects/erp-platform/specs/contracts/http/masterdata-api.md` (§ list query + § PageMeta)
- `projects/erp-platform/specs/services/masterdata-service/architecture.md` (Dependencies/pagination context)
- `projects/erp-platform/specs/contracts/events/erp-masterdata-events.md`

## Related Contracts
- `masterdata-api.md` list responses — the `PageMeta` shape is the contract being reconciled.

## Edge Cases
- `asOf` already exists on `GET /{id}` (detail) — reuse its temporal semantics for the list filter rather than inventing new ones.
- Switching to `Page<T>` adds a count query per list call — acceptable for masterdata volumes, but confirm no N+1 or perf spec is violated.
- `active`/`parentId` semantics: `active` is a soft-delete/status flag, `parentId` a hierarchy filter — confirm the entity columns exist before wiring (some entities may lack `parentId`).

## Failure Scenarios
- **F1 — implementing filters for Department only.** The contract covers all 5 entities; AC-4 guards the census.
- **F2 — leaving `totalElements` as page length "because tests pass".** Existing tests seed ≤1 page, so they never exercise the
  bug ([[env_test_fixture_impossible_input_proves_nothing]]); AC-2 mandates a multi-page assertion.
