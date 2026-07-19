# TASK-BE-527 — master-service: Partner/Sku/Lot full-stack IT + RedisIdempotencyStore unit coverage

- **Type**: TASK-BE (test-coverage hardening — no production behavior change)
- **Status**: review
- **Service**: master-service (wms-platform)
- **Domain/traits**: wms / [transactional, event-driven, multi-tenant]
- **Analysis model**: Opus 4.8 · **Impl model**: Sonnet (mechanical IT authoring — mirror the existing Warehouse/Zone/Location IT pattern; no straggler/behavior judgment)

## Goal

Close the two REAL coverage gaps the 2026-07-19 wms audit + orchestrator recon verified in master-service — **no production behavior change** (`src/test` only).

**Verified refutation first (do not re-litigate):** the audit's "idempotency straggler" hypothesis is **FALSE for master-service** — idempotency is WIRED and proven. `IdempotencyConfig.java:43-57` registers `IdempotencyFilter` via `FilterRegistrationBean` on `/api/v1/master/*` ordered after Spring Security; the filter is the sole active consumer of `IdempotencyStore.lookup/put/tryAcquireLock/releaseLock`; and `WarehouseIntegrationTest`/`ZoneIntegrationTest`/`LocationIntegrationTest` prove replay + conflict against a real Redis Testcontainer. This matches admin-service and outbound-service exactly. **There is no wiring bug — do not add or change idempotency logic.**

The two genuine gaps (coverage only):

1. **Partner / Sku / Lot have NO Testcontainers integration test.** Of the 7 resources, only Warehouse, Zone, Location have a full-stack IT (HTTP → Spring Security → IdempotencyFilter → persistence → outbox → Kafka). Sku, Lot, Partner have MockMvc slices + repository tests but **no full-stack IT** — the audit flagged Partner; recon found Sku+Lot share the gap. 24 write endpoints, 100% slice-covered, 3/7 IT-covered.
2. **`RedisIdempotencyStore` has no isolated unit test** — only `InMemoryIdempotencyStore` does (`InMemoryIdempotencyStoreTest`, 4 tests). Redis-store serialization-failure / lock-key-prefix behavior is exercised only transitively through the 3 full-stack ITs, never in isolation.

## Scope

- **In scope** (master-service `src/test` only — NEW test code, no `src/main` change):
  - **AC-1**: `PartnerIntegrationTest`, `SkuIntegrationTest`, `LotIntegrationTest` extending `MasterServiceIntegrationBase` (the existing shared-static-container base: Postgres + Kafka + Redis). Mirror `WarehouseIntegrationTest`/`ZoneIntegrationTest`/`LocationIntegrationTest` structure. Each must exercise the full stack for its resource: create (POST 2xx with `MASTER_WRITE`), the idempotency replay (same `Idempotency-Key` → identical body) + conflict (same key, different body → 4xx), an authz negative (a caller lacking `MASTER_WRITE`/`MASTER_ADMIN` → 403; and a `MASTER_ADMIN`-only op like deactivate rejected for `MASTER_WRITE`), the PATCH update, deactivate/reactivate lifecycle, and assert the outbox row / Kafka event the sibling ITs assert (mirror exactly what Warehouse/Zone/Location assert — do not invent new assertions).
  - **AC-2**: `RedisIdempotencyStoreTest` — isolated unit/slice test of `RedisIdempotencyStore` against a Redis Testcontainer (or the same shared base if cheaper): store/lookup round-trip, lock acquire/release + prefix, serialization-failure handling (mirror what `InMemoryIdempotencyStoreTest` asserts, adapted for the Redis backing). If a real Redis container per test class is disproportionate, cover the Redis-specific logic (key prefixing, TTL, lock semantics) with the minimum container footprint; document the choice.
- **Out of scope**: any `src/main` change; idempotency wiring (proven correct — leave alone); the other services (notification = TASK-BE-528; inbound = concurrent sessions); the already-covered Warehouse/Zone/Location.

## Acceptance Criteria

- **AC-1**: `PartnerIntegrationTest` + `SkuIntegrationTest` + `LotIntegrationTest` exist, each extending `MasterServiceIntegrationBase`, each asserting (at minimum) create-2xx + idempotency replay/conflict + one authz-403 + the outbox/Kafka emission its sibling ITs assert. They must genuinely drive the real filter + real Redis + real Postgres (not mocks).
- **AC-2**: `RedisIdempotencyStoreTest` covers Redis-store round-trip + lock + a failure path in isolation.
- **AC-3**: `:master-service:test` (unit/slice) GREEN and `:master-service:integrationTest` (Testcontainers lane — authoritative in CI) GREEN. No `src/main` diff (`git diff --stat -- '*/master-service/src/main'` empty).
- **AC-4**: Every new IT genuinely asserts the property (idempotency replay must compare bodies; authz-403 must assert 403; outbox assertion must read the actual row/topic) — no positive-only tests dressed as negatives.

## Edge Cases / Failure Scenarios

- `MasterServiceIntegrationBase` boots shared STATIC containers — new ITs must not restart or `@DirtiesContext` unnecessarily; follow the sibling ITs' isolation (unique keys / cleanup) to avoid cross-test contamination on the shared Postgres/Kafka.
- Partner/Sku/Lot each have MASTER_ADMIN-only lifecycle ops (deactivate/reactivate) vs MASTER_WRITE create/update — assert the split the way the audit table records (create/update = WRITE|ADMIN; deactivate/reactivate = ADMIN only).
- Lot has expiry semantics (`LotExpirationBatchProcessor`) — the IT need only cover the write/lifecycle + idempotency path (the batch processor is already exercised in `LotServiceTest`); do not expand scope into batch expiry.
- The Testcontainers lane is authoritative (local Windows Docker flaky) — get it GREEN in the worktree if Docker is available, but CI is the final arbiter.

## Related

- Pattern to mirror: `master-service/.../integration/WarehouseIntegrationTest.java`, `ZoneIntegrationTest.java`, `LocationIntegrationTest.java` + `MasterServiceIntegrationBase.java`.
- Idempotency (proven WIRED — reference, do not change): `config/IdempotencyConfig.java:43-57`, `adapter/in/web/filter/IdempotencyFilter.java`, `adapter/out/idempotency/RedisIdempotencyStore.java` / `InMemoryIdempotencyStore.java` (+ `InMemoryIdempotencyStoreTest`).
- Memory: `project_enforcement_straggler_sibling_parity` (refutation harvested: master is at parity, no straggler — infra existence WAS matched by live consumption here), `feedback_repo_knows_what_it_does_not_say` (don't reflexively assume a gap = a bug), `project_testcontainers_docker_desktop_blocker` (CI master lane authoritative), `platform/testing-strategy.md`.
