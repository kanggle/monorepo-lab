# Task ID

TASK-MONO-215

# Title

ADR-MONO-025 § 3.3 step 2 — first ABAC data-scope **extension domain (wms)**. wms master-service `WarehouseController.getById` enforces the operator's `data_scope`/`org_scope` claim (read via the shared `AbacDataScope`, interpreted by wms as warehouse codes per `platform/abac-data-scope.md`): a deliberately-scoped operator targeting a warehouse outside its scope → 403 `DATA_SCOPE_FORBIDDEN`; unrestricted (`"*"`) and unscoped (empty/absent — base authorization_code + machine tokens) operators are unaffected (net-zero). Also corrects the `platform/abac-data-scope.md` + `AbacDataScope` javadoc empty-semantics to the verified net-zero-first rule (empty = no filter, NOT fail-closed for the visibility filter) — the producer injects `org_scope` only on the assume-tenant token (`["*"]` for unscoped), so base/machine tokens carry none and must keep working.

# Status

ready

# Owner

backend

# Task Tags

- abac
- wms
- iam
- security
- adr

---

# Dependency Markers

- **implements**: ADR-MONO-025 § 3.3 step 2 (ACCEPTED #1270) + D3 (wms is the chosen first extension domain).
- **builds on**: TASK-MONO-214 (`platform/abac-data-scope.md` + `com.example.security.jwt.AbacDataScope`).
- **corrects**: the MONO-214 contract/helper empty-semantics — for a data-scope FILTER, empty/absent is **net-zero (no filter)**, not fail-closed; verified necessary because the federation wms golden path calls wms with a base SUPER_ADMIN token (`tenant_id='*'`, NO `org_scope`), which fail-closing would break.

# Goal

Prove the ABAC data-scope pattern generalises beyond erp: wms reads the SAME claim via the SAME shared reader and applies its own (warehouse-code) interpretation, with a guaranteed net-zero default so the existing federation golden path is unaffected.

# Scope

- NEW `wms .../domain/exception/DataScopeForbiddenException.java` (`MasterDomainException`, code `DATA_SCOPE_FORBIDDEN`).
- `wms .../adapter/in/web/controller/WarehouseController.java` — `getById` gains `@AuthenticationPrincipal Jwt`; `requireWarehouseInScope` denies a deliberately-scoped operator targeting an out-of-scope warehouse code.
- `wms .../adapter/in/web/advice/GlobalExceptionHandler.java` — `DataScopeForbiddenException` → 403.
- `wms .../adapter/in/web/controller/WarehouseSecurityTest.java` — 5 slice tests (out-of-scope 403, in-scope 200, org_scope alias 403, wildcard 200, no-claim 200 net-zero).
- `platform/abac-data-scope.md` + `libs/java-security AbacDataScope` javadoc — empty-semantics correction (net-zero filter, `allows()` is the strict per-token primitive used only inside the deliberately-scoped branch).

**Out of scope** (follow-on): warehouse LIST filtering by scope; zone/location scope (warehouse-of-zone reachability); finance accounting-unit; erp re-point onto the shared reader; producer change; 2단계 conditions; a dedicated federation-e2e scoped-operator spec (the existing golden path already exercises the net-zero path with the base SUPER_ADMIN token).

# Acceptance Criteria

- **AC-1** A deliberately-scoped operator (`data_scope=["WH-A","WH-B"]`) calling `GET /warehouses/{id}` for a warehouse coded `WH-OTHER` → 403 `DATA_SCOPE_FORBIDDEN`; for `WH-A` → 200.
- **AC-2** The legacy `org_scope` alias is honoured identically (403 on out-of-scope).
- **AC-3 (net-zero)** `data_scope=["*"]` → 200; NO data-scope claim → 200 (base/machine token path). The existing `roleClaimArray_mapsAllEntriesToAuthorities` getById test (no data_scope) still passes byte-identically.
- **AC-4** `:wms-platform:apps:master-service:test` green (the 5 new slice tests + existing); the contract/helper javadoc state the net-zero-first rule.
- **AC-5 (federation net-zero)** The federation-hardening-e2e wms golden path still passes (SUPER_ADMIN base token has no `data_scope` → unrestricted → unaffected) — verified on the next workflow run.

# Related Specs

- `docs/adr/ADR-MONO-025-abac-data-scope-generalization.md` § D3 / § 3.3 step 2
- `platform/abac-data-scope.md`

# Related Contracts

- `platform/abac-data-scope.md`

# Edge Cases

- **The crux**: the wms golden path calls wms with a base authorization_code SUPER_ADMIN token (`tenant_id='*'`) that carries NO `org_scope` (the producer injects it only on the assume-tenant token). So empty/absent MUST be net-zero (no filter), not fail-closed — else the golden path 403s. AC-3/AC-5 guard it.
- Scope tokens are warehouse **codes** (human-assignable), not UUIDs; wms matches `result.warehouseCode()`.
- `jwt == null` defensive → no restriction (the resource server already 401s unauthenticated requests).
- `@WebMvcTest` slice + `SecurityMockMvcRequestPostProcessors.jwt()` forges the `data_scope` claim in-process (no Testcontainers needed).

# Failure Scenarios

- If empty fail-closed, the base-token golden path (and any platform `'*'`-tenant operator) 403s — AC-3/AC-5 + the net-zero correction prevent it.
- If wms matched warehouse UUIDs instead of codes, an admin could not assign a human-meaningful scope — AC-1 pins matching on `warehouseCode()`.
- If the check used RBAC roles instead of the data_scope attribute, it would conflate the axes — this is ABAC (data visibility), composed with the existing RBAC + tenant checks.
