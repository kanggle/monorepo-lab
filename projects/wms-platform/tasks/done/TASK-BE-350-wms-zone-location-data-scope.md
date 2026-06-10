# Task ID

TASK-BE-350

# Title

ADR-MONO-025 § 3.3 follow-on — extend wms ABAC data-scope to the **child entities** (zone + location), fulfilling the reach rule already written in `platform/abac-data-scope.md §3`: *"a row is in-scope iff its warehouse (or the warehouse of its zone/location) is a scoped id."* A deliberately data-scoped operator may only read zones/locations whose parent warehouse code is in its `data_scope`/`org_scope` set. Zone (nested under `/warehouses/{warehouseId}/zones`) is enforced as a single-warehouse **gate**; Location (flat, cross-warehouse list) is enforced as a per-row **DB-side filter** via a `warehouseId IN (SELECT w.id … WHERE w.warehouseCode IN :codes)` subquery. Unrestricted (`"*"`) and unscoped (empty/absent) operators are unaffected (net-zero).

# Status

done

> **완료 (2026-06-11)**: impl PR #1278 (squash `619d9829`). 3차원 ✓ (MERGED / origin/main tip=`619d9829` 일치 / 20 체크 pass·0 fail). **AC-6 federation net-zero ✓**: workflow_dispatch run `27313607342` GREEN (14 passed) — wms 골든패스(base SUPER_ADMIN 토큰 data_scope 부재→null scope) 무영향. **계약 reach rule 2절(zone/location) 이행**: zone=nested route 단일창고 **게이트**(`ZoneService.findById/list` 403, `ZonePersistencePort` 무변경), location=횡단 **per-row DB 필터**(`JpaLocationRepository.searchScoped` = `warehouseId IN (SELECT w.id … WHERE w.warehouseCode IN :codes)` 서브쿼리, codes→ids 1문 해석·신규 포트메서드 불요). **net-zero 격리**: 기존 `search` byte-identical 유지. 스코프=공유 `DataScopeSupport` 헬퍼로 컨트롤러서 읽어 쿼리객체(2-arg 호환 ctor)+findById 오버로드로 전달. **`@PreAuthorize` self-invocation 우회 회피**: findById 1·2-arg 둘 다 명시 구현(인터페이스 default 금지). 테스트=zone/location 서비스 게이트+403+net-zero·location H2 searchScoped 서브쿼리(필터+count+미매칭 deny-all)·DataScopeSupport 클레임리더. **ABAC 데이터스코프 wms 3엔티티(warehouse/zone/location) 완성**. 잔여 follow-on=finance·erp re-point·2단계 조건식(보류). 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend

# Task Tags

- abac
- wms
- security
- adr

---

# Dependency Markers

- **builds on**: TASK-MONO-215 (warehouse `getById`) + TASK-BE-349 (warehouse `list`) + TASK-MONO-214 (`AbacDataScope` + `platform/abac-data-scope.md`).
- **implements**: the second clause of the wms reach rule in `platform/abac-data-scope.md §3` ("the warehouse of its zone/location"), which was written at MONO-214 but only the direct-warehouse case was enforced.
- **reuses**: `com.example.security.jwt.AbacDataScope` (claim read) and `com.wms.master.domain.exception.DataScopeForbiddenException` (403 `DATA_SCOPE_FORBIDDEN`, added MONO-215).

# Goal

Close the child-entity enumeration leak: after a scoped operator is 403'd on an out-of-scope warehouse, it must also be unable to read that warehouse's zones/locations (single-resource or list). Enforce with the SAME claim + the SAME net-zero rule, interpreted per entity via the parent warehouse code.

# Scope

**Shared**
- NEW `wms .../adapter/in/web/support/DataScopeSupport.java` — `warehouseScopeCodes(Jwt)` returns the operator's deliberately-scoped warehouse codes, or `null` when unrestricted/unscoped (net-zero). Pure claim read (no DB). Used by the zone + location controllers.

**Zone (single-warehouse gate, service-side — Zone carries only `warehouseId`)**
- `ListZonesQuery` — add `Set<String> scopeWarehouseCodes` (null = net-zero) + 2-arg compat ctor.
- `ZoneQueryUseCase` — add `findById(UUID, Collection<String> scopeWarehouseCodes)` (the existing 1-arg stays, both `@PreAuthorize`'d in the impl — NO interface default, to avoid a self-invocation security bypass).
- `ZoneService` — `findById(id, scope)` loads the zone, then (when scoped) loads the parent warehouse (port already injected) and 403s if its code is out of scope; `list` gates on the already-loaded parent warehouse's code. `ZonePersistencePort` is UNCHANGED (zone list is inherently single-warehouse → a gate, not a row filter).
- `ZoneController` — `getById` + `list` gain `@AuthenticationPrincipal Jwt`; thread `DataScopeSupport.warehouseScopeCodes(jwt)`.

**Location (cross-warehouse per-row filter — Location carries `warehouseId`, not code)**
- `ListLocationsQuery` — add `Set<String> scopeWarehouseCodes` (null = net-zero) + 2-arg compat ctor.
- `LocationQueryUseCase` — add `findById(UUID, Collection<String> scopeWarehouseCodes)` (same 1-arg+2-arg `@PreAuthorize` shape).
- `LocationPersistencePort.findPage` — add `Collection<String> scopeWarehouseCodes`.
- `JpaLocationRepository` — keep `search` byte-identical; add `searchScoped` = `search` + `AND l.warehouseId IN (SELECT w.id FROM WarehouseJpaEntity w WHERE w.warehouseCode IN :codes)` (subquery resolves codes→ids in one statement; no service-side resolution, no new warehouse port method).
- `LocationRepositoryImpl.findPage` — branch: null/empty scope → `search` (net-zero, unchanged); non-empty → `searchScoped`.
- `LocationService` — `findById(id, scope)` loads the location, then (when scoped) loads the parent warehouse and 403s if its code is out of scope; `list` threads `scopeWarehouseCodes` to the port.
- `LocationController` — `getById` + `list` gain `@AuthenticationPrincipal Jwt`; thread scope.
- `FakeLocationPersistencePort.findPage` — signature update (the real subquery filter is proven by the H2 repo test; the fake records/ignores the codes).

**Contract**
- `platform/abac-data-scope.md §3` — note that the wms reach rule's zone/location clause is now enforced (zone = gate, location = subquery filter).

**Out of scope** (follow-on): finance accounting-unit; erp re-point onto the shared reader; producer change; a dedicated federation-e2e scoped-operator spec; 2단계 conditions (DEFERRED — no policy engine). Write paths (create/update/deactivate) are unchanged — data-scope is a read-visibility axis here, consistent with warehouse.

# Acceptance Criteria

- **AC-1 (zone getById)** A scoped operator (`data_scope=[<codeA>]`) reading a zone whose parent warehouse code ∉ scope → 403 `DATA_SCOPE_FORBIDDEN`; ∈ scope → 200.
- **AC-2 (zone list gate)** `GET /warehouses/{id}/zones` for a warehouse whose code ∉ scope → 403; ∈ scope → 200 page.
- **AC-3 (location getById)** Scoped operator reading a location whose parent warehouse code ∉ scope → 403; ∈ scope → 200.
- **AC-4 (location list filter)** `GET /locations` (cross-warehouse) returns only locations whose parent warehouse code ∈ scope; `totalElements` reflects the filtered count (DB-side subquery, not a post-slice trim). H2 test with locations across ≥2 warehouses.
- **AC-5 (net-zero)** `data_scope=["*"]` and NO data-scope claim → every endpoint behaves exactly as today (zone/location getById 200, full lists). The existing zone/location list + getById tests pass byte-identically; location list net-zero routes through the unchanged `search`.
- **AC-6 (federation net-zero)** federation-hardening-e2e wms golden path still passes (base SUPER_ADMIN token has no `data_scope`) — verified on the next workflow run.
- **AC-7** `:projects:wms-platform:apps:master-service:test` green (new zone/location service scope tests + location H2 `searchScoped` test + controller scope-threading + all existing).

# Related Specs

- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` § 3.3 (follow-on) / § D3 / § D4 (net-zero)
- `platform/abac-data-scope.md` § 2–3

# Related Contracts

- `platform/abac-data-scope.md`

# Edge Cases

- **Net-zero crux (same as MONO-215/BE-349)**: the federation golden path calls these endpoints with a base SUPER_ADMIN token (`tenant_id='*'`) carrying NO `org_scope` → `warehouseScopeCodes`=null → no gate, unchanged `search`.
- **Zone is a gate, not a row filter** — every zone list is already confined to one warehouse by the nested route, so the parent-warehouse check is a single 403 decision, not a per-row predicate. Location list IS cross-warehouse → must be a per-row DB filter.
- **Subquery, not empty IN**: location `searchScoped` runs only when `:codes` is non-empty (restricted operator always has ≥1 code); the inner `IN :codes` is therefore never empty. If the codes match no warehouse, the subquery is empty and the outer `IN (<empty subquery>)` yields no rows — valid SQL, correct deny-all.
- **`@PreAuthorize` self-invocation**: the 1-arg `findById` is NOT an interface `default` (that would run unsecured and self-call the 2-arg, bypassing method security). Both arities are concrete `@PreAuthorize`'d service methods; the 1-arg delegates to the 2-arg with `null` scope.
- Scope tokens are warehouse **codes**; zone/location resolve them against their parent warehouse's code (zone via a warehouse load, location via the `searchScoped` subquery / a warehouse load on getById).
- `jwt == null` defensive → no restriction (resource server already 401s).

# Failure Scenarios

- If only getById were scoped (not list), a scoped operator could still enumerate every zone/location — AC-2/AC-4 close the list leak.
- If the location filter were applied post-fetch, `totalElements` would be wrong and pages could be short/empty mid-result — AC-4 pins the DB-side subquery.
- If the 1-arg `findById` were an interface default, method security would be bypassed for any 1-arg caller — the explicit dual `@PreAuthorize` impl prevents it.
- If zone matched warehouse UUIDs instead of codes, an admin could not assign a human-meaningful scope — the parent warehouse code is the match key, consistent with warehouse + location.
