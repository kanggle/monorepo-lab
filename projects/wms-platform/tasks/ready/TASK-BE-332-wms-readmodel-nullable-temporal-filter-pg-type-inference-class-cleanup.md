# Task ID

TASK-BE-332

# Title

wms-platform PostgreSQL read-model queries — cast the nullable **temporal** filter bounds to remove the `42P18 could not determine data type of parameter` **500** class across ALL remaining occurrences (the BE-331 audit follow-up). Five repositories in three services (`admin-service` ×3, `master-service`, `outbound-service`) carry the same `:tempParam IS NULL OR …` pattern BE-331 fixed in `AlertLog`/`AdjustmentAudit` — each 500s on an unfiltered search against PostgreSQL.

# Status

ready

# Owner

backend-engineer (read-model repository `@Query` text only across admin/master/outbound — no schema/entity/domain/contract/ADR change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **driven by**: the TASK-BE-331 audit (user-chosen "read-model 쿼리 커버리지 audit"). BE-331 fixed the same 42P18 on `admin-service` `AlertLogRepository` + `AdjustmentAuditRepository`; the audit swept ALL `@Query` repositories for the same pattern.
- **root cause (identical to BE-331)**: a nullable **temporal** parameter (`Instant`/`LocalDate`) guarded by a bare `:param IS NULL` binds an untyped null PostgreSQL cannot type → `42P18` → `InvalidDataAccessResourceUsageException` → 500 on any unfiltered call. The CAST pins the type for the null-check; the `>=`/`<=`/`<`/`>` comparison keeps temporal typing.
- **audit scope + findings**:
  - **Fixed here (PostgreSQL + nullable-temporal bare IS-NULL + reachable + search-null path untested)** — 5 repositories:
    1. `admin-service` `ShipmentSummaryRepository.search` — `shippedAtFrom/To` (Instant)
    2. `admin-service` `OrderSummaryRepository.search` — `requiredShipDateFrom/To` (LocalDate)
    3. `admin-service` `AsnSummaryRepository.search` — `expectedArriveDateFrom/To` (LocalDate)
    4. `master-service` `JpaLotRepository.search` — `expiryBefore/After` (LocalDate)
    5. `outbound-service` `OrderJpaRepository.findFiltered` + `countFiltered` — `requiredShipAfter/Before` (LocalDate) + `createdAfter/Before` (Instant)
  - **Ruled out by verification (NOT bugs)**:
    - `global-account-platform` (security/admin/account), `erp-platform`, `finance-platform` = **MySQL** — `42P18` is PostgreSQL-specific; MySQL tolerates untyped-null IS-NULL. Proven by `AdminActionJpaRepositoryTest` (MySQLContainer) calling `search(null,…)` GREEN. The `:from`/`:to`-on-`startedAt`/`occurredAt` matches there are false positives.
    - `scm-platform` `procurement` (PG) — `IS NULL` only on `status`/`supplierId` (no temporal filter).
    - `Throughput*Repository.upsertIncrement` (required non-null `date`/`lastEventAt`), `NotificationDeliveryJpaRepository.findPendingDueForRetry(:now)`, `ReservationJpaRepository.findExpired(:asOf)` — required non-null temporal params, NOT in an IS-NULL guard.
- **why undetected (same class as BE-331)**: the dashboard WebMvc slice tests **mock** the repositories; the real-PostgreSQL integration tests existed but only **round-trip** these entities (save/findById) — none called the `search()`/`findFiltered()` query method with all-null filters. A test-coverage gap, not a CI-wiring gap (admin/master integration tests do run in CI).
- **no dependency on**: any schema/entity/domain/contract/ADR change.

---

# Goal

Every wms-platform PostgreSQL read-model search endpoint with an optional date range returns `200` on an unfiltered (or partially-filtered) call instead of a 500. Closes the 42P18 class opened by BE-331.

# Scope

## In Scope

`@Query` text + regression tests in `projects/wms-platform/apps/{admin-service,master-service,outbound-service}`:

1. **CAST the nullable temporal IS-NULL guards** in the 5 repositories (`CAST(:p AS string) IS NULL`).
2. **Regression tests** exercising the all-null-filter path against real PostgreSQL:
   - `admin-service` `ReadModelPersistenceIntegrationTest` — +3 (Order/Asn/Shipment summary search null).
   - `master-service` `LotRepositoryImplTest` — +1 (lot search null expiry).
   - `outbound-service` `OrderJpaRepositoryFilterIT` (new `@DataJpaTest` PG slice) — +2 (findFiltered/countFiltered null).

## Out of Scope

- Any schema/entity/domain/contract/ADR change.
- The non-temporal IS-NULL guards (UUID/String/Boolean — PostgreSQL resolves their untyped nulls).
- MySQL services / non-PostgreSQL domains (not affected by 42P18).
- **Wiring `outbound-service` `integrationTest` into CI** and fixing the pre-existing outbound `@SpringBootTest` context issues (`outboxPublisher` `BeanDefinitionOverrideException`; the `V13 tms_request_dedupe` Flyway test-time quirk). Orthogonal infra; the outbound regression test sidesteps both via a `@DataJpaTest` slice with `ddl-auto=create-drop`.
- Adding search()-path coverage to MySQL services (not 42P18-prone; lower value).

# Acceptance Criteria

- [ ] **AC-1** All 5 repositories' nullable temporal IS-NULL guards are `CAST(:p AS string)`; the `>=`/`<=`/`<`/`>` comparisons keep temporal typing.
- [ ] **AC-2** `admin-service` `:integrationTest` green incl. the 3 new search-null tests (real PostgreSQL 16) — verified locally.
- [ ] **AC-3** `master-service` `LotRepositoryImplTest` green incl. the new search-null-expiry test (real PostgreSQL 16) — verified locally.
- [ ] **AC-4** `outbound-service` `OrderJpaRepositoryFilterIT` green (findFiltered + countFiltered all-null, real PostgreSQL 16) — verified locally.
- [ ] **AC-5** Diff confined to the 5 repository `@Query` strings + the 4 touched/added test files (+ task lifecycle). No schema/entity/domain/contract/ADR change.
- [ ] **AC-6** `:check` green for the 3 services (CI).

# Related Specs

- `projects/wms-platform/apps/admin-service/build.gradle` — `integrationTest` includes `@Tag("integration")` (the admin search-null tests run there, CI-gated).
- BE-331 (`AlertLogRepository`/`AdjustmentAuditRepository`) — the proven CAST fix this generalises.

# Related Contracts

- `admin-service-api.md` (§1.4/1.5 ASN/order/shipment dashboards), `master-service` lot search, `outbound` order search — all byte-unchanged; the implementations are brought into PostgreSQL conformance.

# Edge Cases

- All-null filters (the unfiltered default) — the 500 path; now `200`.
- Partial date range (only `from`, or only `to`) — still `200` (each bound's cast is independent).
- Non-null range — `>=`/`<=` keeps temporal typing; the cast only guards the IS-NULL branch.
- `JpaLotRepository`: the existing `:expiryBefore IS NULL OR (l.expiryDate IS NOT NULL AND …)` shape is preserved; only the leading IS-NULL guard is cast.

# Failure Scenarios

- Missing one of the temporal bounds → that endpoint still 500s on the unfiltered path. Mitigation: AC-1 covers every from+to/before+after pair across the 5 repositories.
- Casting to a type Hibernate rejects → context fails at startup. Mitigation: `string` is the proven BE-331 token; AC-2/3/4 (context boots + query runs on PG) confirm.

# Test Requirements

- 6 regression tests across the 3 services (real PostgreSQL), all asserting the all-null path runs without 42P18.
- `:integrationTest` (admin) + `:test`/`LotRepositoryImplTest` (master) + `OrderJpaRepositoryFilterIT` (outbound) green locally.

# Definition of Done

- [ ] 5 repositories' temporal IS-NULL guards cast; 6 regression tests added.
- [ ] admin/master/outbound real-PostgreSQL tests green locally.
- [ ] Diff scope confined; schema/entity/contract/ADR untouched.
- [ ] Task md + `INDEX.md` updated.
- [ ] Ready for review.

---

분석=Opus 4.8 / 구현=Opus(직접, real-PostgreSQL 검증 admin+master+outbound). BE-331 audit 의 산출 — 같은 42P18 class 를 wms-platform PostgreSQL read-model 전반에 청소. **메타: ① 검증이 적중 — global-account/erp/finance 의 동일-패턴 매치는 MySQL 이라 false positive(42P18=PostgreSQL 전용); 가정 말고 DB 타입·실 테스트로 확인. ② outbound 는 선재 테스트-인프라 결함(@SpringBootTest bean-override + Flyway V13)으로 full-context IT 불가 → `@DataJpaTest` + `ddl-auto=create-drop` 슬라이스로 쿼리만 격리 검증(인프라 결함 우회, 별건 follow-up). ③ read-model 의 nullable temporal `:p IS NULL` 은 PostgreSQL 에서 CAST 필수 — round-trip 테스트만으론 못 잡으니 쿼리-실행(all-null) 테스트가 동반돼야.**
