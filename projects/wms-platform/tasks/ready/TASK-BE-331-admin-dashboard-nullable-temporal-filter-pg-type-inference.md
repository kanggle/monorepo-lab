# Task ID

TASK-BE-331

# Title

`admin-service` dashboard read-model queries — fix the PostgreSQL `42P18 could not determine data type of parameter` **500** on the unfiltered `GET /dashboard/alerts` (and the identical latent defect on `GET /dashboard/adjustments`). The `:param IS NULL OR …` guard on the nullable **temporal** bounds (`detectedAtFrom/To`, `occurredAtFrom/To`) binds an untyped null PostgreSQL cannot type, aborting the whole statement → an unhandled 500. This is the remaining blocker that keeps the platform-console **WMS 운영** page degraded.

# Status

ready

# Owner

backend-engineer (admin-service read-model repository `@Query` — query text only; no schema/entity/domain/contract change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **surfaced by**: TASK-MONO-170 platform-console per-domain ops live demo (2026-06-03). The WMS 운영 section (`getWmsSectionState` = `Promise.all([listInventory, listAlerts])`) degraded as a whole: the **inventory** leg returned `200` (seeded read-model) but the **alerts** leg returned `500`, and a 500 on either leg drops the whole section to `degraded` (the catch-all in `wms-state.ts`). The memory note had hypothesised "alerts unseeded → degraded", but an empty read-model returns an empty `200` — so the 500 was a real error, not missing data.
- **root cause (real stacktrace, not hypothesis)**: `org.postgresql.util.PSQLException: ERROR: could not determine data type of parameter $8` (SQLState `42P18`) at `AlertDashboardController.list` line 62. `AlertLogRepository.search` JPQL has `(:detectedAtFrom IS NULL OR a.detectedAt >= :detectedAtFrom)`. When the console sends NO date filters (the default), `:detectedAtFrom`/`:detectedAtTo` bind as untyped nulls; PostgreSQL's prepared-statement type inference fails on the standalone `IS NULL` occurrence of the temporal parameter and aborts the statement (the UUID/String/Boolean nulls resolve fine — only the temporal ones fail). `GlobalExceptionHandler` maps the resulting `InvalidDataAccessResourceUsageException` to a 500.
- **why undetected**: the WebMvc slice test (`AlertDashboardControllerWebMvcTest`) **mocks** `AlertLogRepository`, so the real query never runs; and the only test that hits real PostgreSQL (`ReadModelPersistenceIntegrationTest`, `@Tag("integration")`) is **excluded from CI `check`** (admin-service `build.gradle` `excludeTags 'integration'`; CI runs `:check`, never `integrationTest`). So no CI gate ever executed the unfiltered query against PostgreSQL. `AdjustmentAuditRepository.search` carries the byte-identical pattern (`occurredAtFrom/To`) → the same latent 500 on `GET /dashboard/adjustments` (not yet console-reached).
- **no dependency on**: any schema / entity / domain / contract / ADR change; the inventory leg + all other dashboards are unaffected. Only the two repositories with nullable **temporal** filters are touched.

---

# Goal

`GET /dashboard/alerts` and `GET /dashboard/adjustments` with no (or partial) filters return `200` against PostgreSQL instead of a 500, so the platform-console WMS 운영 alerts section renders (combined with the seeded inventory leg, the whole WMS section renders). Remove a latent 42P18 from every dashboard query with a nullable temporal bound.

# Scope

## In Scope

`projects/wms-platform/apps/admin-service` only — read-model repository query text + a regression test:

1. **`AlertLogRepository.search`** — wrap the `:detectedAtFrom`/`:detectedAtTo` `IS NULL` guards in `CAST(:p AS string)` so PostgreSQL can type the parameter for the null-check (the real `>=`/`<=` comparison keeps its temporal typing; a cast-to-string preserves IS-NULL semantics for any value).
2. **`AdjustmentAuditRepository.search`** — same fix for `:occurredAtFrom`/`:occurredAtTo` (the identical latent defect).
3. **Regression tests** in `ReadModelPersistenceIntegrationTest` (the real-PostgreSQL suite): call each `search(...)` with ALL filters null (mirroring the console's default + the controller's `detectedAt,desc` / `occurredAt,desc` sort) and assert it runs without 42P18 and returns the seeded row.

## Out of Scope

- Any schema / entity / domain / contract / ADR change (the cast is query-text only).
- The UUID/String/Boolean `IS NULL` guards (PostgreSQL resolves their untyped nulls — left untouched, minimal diff).
- **Wiring the admin-service `integrationTest` (real-PostgreSQL) suite into CI** — the deeper gap that let this ship (CI `check` excludes `@Tag("integration")` and never runs `integrationTest`). A monorepo-level CI task; noted as a follow-up, not done here.
- Seeding a non-empty `admin_alert_log` demo row (the empty-`200` already renders; a richer demo seed is optional polish).

# Acceptance Criteria

- [x] **AC-1** `AlertLogRepository.search` with all-null filters runs against PostgreSQL without `42P18` and returns the expected page — verified by the new `ReadModelPersistenceIntegrationTest.alertLog_search_allNullFilters_doesNotFailPgTypeInference` (Testcontainers PostgreSQL 16).
- [x] **AC-2** `AdjustmentAuditRepository.search` with all-null filters likewise — verified by `adjustmentAudit_search_allNullFilters_doesNotFailPgTypeInference`.
- [x] **AC-3** `./gradlew :projects:wms-platform:apps:admin-service:integrationTest` green (all 11 incl. the 2 new) — run locally (CI excludes it).
- [x] **AC-4** `./gradlew :projects:wms-platform:apps:admin-service:check` green (unit + WebMvc slice unaffected — the slice mocks the repository).
- [x] **AC-5** Diff confined to the two repository `@Query` strings + the 2 regression tests (+ task lifecycle). No schema/entity/domain/contract/ADR change.
- [ ] **AC-6** (live) admin-service rebuilt + redeployed into the MONO-170 demo stack → console WMS 운영 (active tenant acme-corp) renders both inventory + alerts (no degrade). — user browser smoke.

# Related Specs

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.6 (alerts) / § 1.5 (adjustments) — response shapes. **Unchanged** (the query is brought into PostgreSQL conformance).
- `projects/wms-platform/apps/admin-service/build.gradle` — `integrationTest` excludes from `check` (the CI-gap context).
- Consumer reference: `platform-console` `apps/console-web/src/features/wms-ops/{api/wms-state.ts,api/wms-api.ts}` (the `Promise.all([inventory, alerts])` composition + the unfiltered `listAlerts` call).

# Related Contracts

- `admin-service-api.md` — byte-unchanged; this brings the implementation into PostgreSQL conformance for the unfiltered path.

# Edge Cases

- All-null filters (the console default) — the path that 500'd; now `200`.
- Partial filters (e.g. only `acknowledged`, no date range) — still `200` (the temporal casts are independent).
- Non-null date range — the `>=`/`<=` comparison keeps temporal typing; the cast only guards the IS-NULL branch, so filtering is unchanged.
- Empty read-model → empty `200` page (never a 500).

# Failure Scenarios

- Casting to a type PostgreSQL/Hibernate rejects → context fails at startup (Hibernate validates `@Query`). Mitigation: `string` is a universally-supported HQL cast target; AC-3 (context boots + query runs) confirms.
- Missing one of the four temporal bounds → that endpoint still 500s on the unfiltered path. Mitigation: AC-1/AC-2 cover both repositories' from+to bounds.

# Test Requirements

- 2 regression tests in `ReadModelPersistenceIntegrationTest` (real PostgreSQL, all-null filters).
- `:integrationTest` + `:check` green locally.
- Live AC-6 = redeploy in the MONO-170 demo stack + console click.

# Definition of Done

- [x] Both repositories' nullable temporal `IS NULL` guards CAST; 2 regression tests added.
- [x] `:integrationTest` (11/11) + `:check` green locally.
- [x] Diff scope confined; schema/entity/contract/ADR untouched.
- [ ] AC-6 live-verified in the demo stack (user browser).
- [ ] Task md + `INDEX.md` updated.
- [ ] Ready for review.

---

분석=Opus 4.8 / 구현=Opus(직접, real-PostgreSQL integrationTest 실증). MONO-170 데모가 노출한 4번째이자 마지막 drift — producer 의 실제 PostgreSQL 쿼리 버그(시드/계약 아님). **메타: CI `check` 가 `@Tag("integration")` real-PG 테스트를 제외하고 WebMvc slice 는 repository 를 mock → unfiltered 쿼리가 PostgreSQL 에 한 번도 안 실려 42P18 이 shipped. read-model 쿼리의 nullable temporal `:p IS NULL` 은 PostgreSQL 에서 CAST 필요; mock-only + integration-excluded-from-CI 조합이 본 class 의 sender.** 별건 follow-up: admin-service integrationTest 를 CI 에 배선(이게 사전 포착했어야).
