# Task ID

TASK-BE-349

# Title

ADR-MONO-025 § 3.3 follow-on — close the wms ABAC data-scope **LIST leak**. `WarehouseController.list` (`GET /api/v1/master/warehouses`) confines a deliberately-scoped operator's result page to warehouses whose code is in its `data_scope`/`org_scope` set, pushing the filter into the SQL `WHERE … IN (:codes)` so pagination (`totalElements`/`totalPages`) stays correct. Unrestricted (`"*"`) and unscoped (empty/absent — base authorization_code + machine tokens) operators are unaffected (net-zero): the existing `JpaWarehouseRepository.search` query is left byte-identical and used verbatim for the net-zero path; a new `searchScoped` adds the `IN` clause only when the operator is deliberately scoped.

# Status

ready

# Owner

backend

# Task Tags

- abac
- wms
- security
- adr

---

# Dependency Markers

- **builds on**: TASK-MONO-215 (wms `getById` data-scope enforcement) + TASK-MONO-214 (`platform/abac-data-scope.md` + `com.example.security.jwt.AbacDataScope`).
- **implements**: ADR-MONO-025 § 3.3 follow-on (the documented "warehouse LIST filter" item). Completes the wms first-domain enforcement so a scoped operator cannot enumerate out-of-scope warehouses via the list endpoint after being blocked on `getById`.
- **closes leak**: before this, `getById` 403s an out-of-scope warehouse but `GET /warehouses` still returns it — the data-scope was enforced on single-resource read only.

# Goal

Make the warehouse **list** endpoint honour the operator's data-scope with the SAME shared reader and net-zero rule as `getById`, doing the filtering in the database so the page metadata is accurate and the net-zero golden path is untouched.

# Scope

- `wms .../application/query/ListWarehousesQuery.java` — add `Set<String> scopeWarehouseCodes` (null = unrestricted/net-zero); keep a 2-arg compatibility constructor delegating `null` so existing callers/tests compile unchanged.
- `wms .../adapter/in/web/controller/WarehouseController.java` — `list` gains `@AuthenticationPrincipal Jwt`; a private `scopeWarehouseCodes(Jwt)` returns the deliberately-scoped token set, or `null` when unrestricted/unscoped (net-zero), and is threaded into the query.
- `wms .../application/service/WarehouseService.java` — `list` forwards `query.scopeWarehouseCodes()` to the port.
- `wms .../application/port/out/WarehousePersistencePort.java` — `findPage` gains a `Collection<String> scopeWarehouseCodes` param (null = unrestricted).
- `wms .../adapter/out/persistence/WarehouseRepositoryImpl.java` — branch: empty/null scope → existing `search` (net-zero, unchanged); non-empty → new `searchScoped`.
- `wms .../adapter/out/persistence/JpaWarehouseRepository.java` — keep `search` byte-identical; add `searchScoped` = `search` + `AND w.warehouseCode IN :codes`.
- `wms .../application/service/FakeWarehousePersistencePort.java` (test fake) + the two repository test call sites (`WarehouseRepositoryImplH2Test`, `WarehouseRepositoryImplTest`) — signature update (pass `null` where unscoped) + new scoped assertions.
- `wms .../adapter/in/web/controller/WarehouseSecurityTest.java` — capture the `ListWarehousesQuery` argument and assert `scopeWarehouseCodes()` for scoped / wildcard / no-claim / org_scope-alias.
- `platform/abac-data-scope.md` § 3 — extend the wms adopter note: enforcement now covers BOTH `getById` (403) AND `list` (page-level confinement, DB-side `IN` filter).

**Out of scope** (follow-on): zone/location reachability (a row whose *zone/location's* warehouse is scoped — the contract reach rule's second clause); finance accounting-unit; erp re-point onto the shared reader; producer change; a dedicated federation-e2e scoped-operator spec; 2단계 conditions (DEFERRED — no policy engine).

# Acceptance Criteria

- **AC-1** A deliberately-scoped operator (`data_scope=["WH-A","WH-B"]`) calling `GET /api/v1/master/warehouses` gets a page containing ONLY warehouses coded `WH-A`/`WH-B`; `totalElements` reflects the filtered count (DB-side filter, not a post-slice trim).
- **AC-2** The legacy `org_scope` alias scopes the list identically.
- **AC-3 (net-zero)** `data_scope=["*"]` → full unfiltered page; NO data-scope claim → full unfiltered page (base/machine token path). The existing `WarehouseRepositoryImpl(H2)Test.findPagePaginatesAndFilters` passes byte-identically (it calls the net-zero path).
- **AC-4** `:wms-platform:apps:master-service:test` green: the new H2 `searchScoped` filtering test + the controller slice scope-capture tests + all existing tests. The net-zero path provably routes through the unchanged `search` query.
- **AC-5 (federation net-zero)** The federation-hardening-e2e wms golden path still passes — the SUPER_ADMIN base token has no `data_scope` → `scopeWarehouseCodes` is `null` → `search` (unchanged) runs → no behaviour change. Verified on the next workflow run.

# Related Specs

- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` § 3.3 (follow-on) / § D3 / § D4 (net-zero)
- `platform/abac-data-scope.md` § 2–3

# Related Contracts

- `platform/abac-data-scope.md`

# Edge Cases

- **Empty IN avoidance**: a restricted operator always carries ≥1 token, but the adapter still normalises empty/null scope → the net-zero `search` path so an empty `IN ()` (invalid on some dialects) can never be emitted.
- **Net-zero crux (same as MONO-215)**: the federation wms golden path calls list with a base SUPER_ADMIN token (`tenant_id='*'`) carrying NO `org_scope` → `scopeWarehouseCodes=null` → unchanged `search`. Fail-closing here would empty the operator console's warehouse list.
- **Page correctness**: filtering in the controller after the page is fetched would corrupt `totalElements`/`totalPages` and could yield short/empty pages mid-result; the filter MUST be in the query — hence `searchScoped`.
- Scope tokens are warehouse **codes** (human-assignable), matched against `w.warehouseCode`, consistent with `getById`'s `result.warehouseCode()` check.
- `jwt == null` defensive → no restriction (the resource server already 401s unauthenticated requests).

# Failure Scenarios

- If the filter were applied post-fetch, page 2 of a scoped operator could be empty even though in-scope rows exist beyond the first slice — AC-1's `totalElements` assertion + the DB-side `IN` prevent it.
- If `search` were modified in place to add a nullable `IN`, a Hibernate null-collection / empty-`IN` edge could regress the net-zero golden path — keeping `search` untouched and adding `searchScoped` isolates the risk (AC-3/AC-5).
- If the check used RBAC roles instead of the data_scope attribute, it would conflate the axes — this is ABAC (data visibility), composed with the existing RBAC + tenant checks.
