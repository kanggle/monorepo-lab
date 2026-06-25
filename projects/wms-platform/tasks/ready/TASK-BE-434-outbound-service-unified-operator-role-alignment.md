# TASK-BE-434 — Align outbound-service authorization to the unified operator-role model (adopt the `WMS_*` role hierarchy)

**Status:** ready

**Owner:** backend

**Task Tags:** code, api

> **Recommendation annotation** (CLAUDE.md): 분석=Opus 4.8 / 구현 권장=Opus — authorization/security model change (role-hierarchy gating, dual-accept migration, cross-layer composition with tenant-scoping). Not a mechanical fix.

---

## Goal

Make `outbound-service` admit a federated console operator who holds the IAM-issued wms domain role (`roles ∋ WMS_OPERATOR`), by **adopting the same `WMS_*` role hierarchy the sibling `admin-service` already uses** — the decided resolution (see Decision below). After this task an operator who assumed a wms-entitled tenant reaches the outbound operator surface with the correct READ/WRITE granularity, with **no throwaway bridge**.

Today this is **broken**: `outbound-service` `SecurityConfig:91-96` gates `/api/**` on `OUTBOUND_READ` / `OUTBOUND_WRITE` / `OUTBOUND_ADMIN`, but IAM (`OperatorRoleDerivation`, `case "wms" -> "WMS_OPERATOR"`) derives only `WMS_OPERATOR` at assume-tenant and **never issues `OUTBOUND_*`**. So a federated operator passes the JWT + `TenantClaimValidator` (entitled) but is **403'd at the Spring Security role gate** (`HTTP_403`, no domain body) before any application logic, and the console `/wms/outbound` screen shows *"이 화면을 조회할 권한이 없습니다"* for every federated operator.

### The drift (verified 2026-06-25)

| Source | Role name(s) for wms outbound |
|---|---|
| Contract `platform/contracts/jwt-standard-claims.md` (L28, L79, L241) | `WMS_OPERATOR` + `OUTBOUND_MANAGER` |
| IAM issuance `OperatorRoleDerivation.fromEntitledDomains` | `WMS_OPERATOR` only |
| `outbound-service` `SecurityConfig` enforcement | `OUTBOUND_READ` / `OUTBOUND_WRITE` / `OUTBOUND_ADMIN` |
| *(aligned sibling)* `admin-service` `SecurityConfig` | `WMS_SUPERADMIN > WMS_ADMIN > WMS_OPERATOR > WMS_VIEWER` |

`admin-service` is the model: it consumes exactly what IAM issues. `outbound-service` is the outlier still on its standalone-era `OUTBOUND_*` taxonomy.

---

## Decision (resolved 2026-06-25 — was a 3-option backlog choice)

**CHOSEN = Option A — `outbound-service` adopts the `WMS_*` role hierarchy, mirroring `admin-service`.**

Concretely:
- Introduce the same role hierarchy as `admin-service`: `ROLE_WMS_SUPERADMIN > ROLE_WMS_ADMIN > ROLE_WMS_OPERATOR > ROLE_WMS_VIEWER`.
- **READ** (`GET /api/**`) requires `WMS_VIEWER` (and-above via the hierarchy) — so a read-only wms-entitled operator (entitlement-trust `WMS_VIEWER`, ADR-035 / BE-383) can read, consistent with admin-service.
- **WRITE** (`POST/PATCH/PUT/DELETE /api/**`) requires `WMS_OPERATOR` (and-above) — preserving the READ/WRITE split the old `OUTBOUND_READ` vs `OUTBOUND_WRITE/ADMIN` encoded, and matching BE-383's invariant *"WRITE authority comes from `WMS_OPERATOR`; entitlement-trust never grants WRITE"*.
- **Backward-compat (standalone): retain the legacy `OUTBOUND_READ/WRITE/ADMIN` authorities as a dual-accept** (`hasAnyRole(WMS_* tier … , OUTBOUND_* tier …)`), so any standalone deployment whose RBAC assigns `OUTBOUND_*` keeps working. This mirrors the ADR-032 D5 step-1 "dual-read" migration philosophy already used by the gateways — no mis-authorization window, reversible.

**Rejected:**
- **Option B (honor contract `OUTBOUND_MANAGER`)** — would require an IAM-platform change (derive/emit `OUTBOUND_MANAGER`) AND still leave `OUTBOUND_*` unaddressed; more moving parts, pulls in `iam-platform` + a `platform/` contract edit. Deferred unless A proves insufficient.
- **Option C (gateway/converter role-translation `WMS_OPERATOR→OUTBOUND_*`)** — introduces a new translation component and does not help when the service is reached directly (the federation demo bypasses the gateway); also the wms gateway as-built does no role translation.

> **Contract note (no shared edit required):** `jwt-standard-claims.md` already lists `WMS_OPERATOR` as a wms operator-facing role, and Option A consumes exactly what IAM issues — so this task is **`wms-platform`-internal** and needs **no** change to the shared contract or to IAM. The contract's additional `OUTBOUND_MANAGER` entry is left as-is (a finer role the platform may use later); AC-3 only requires that what `outbound-service` enforces is a subset consistent with the contract + what IAM issues, which `WMS_*` satisfies. (If review finds the contract must explicitly bless `WMS_*` for the outbound surface, that single-line contract clarification is the only thing that would escalate this to monorepo-level — flagged, not expected.)

---

## Scope

### In Scope

- `outbound-service` `SecurityConfig`: add the `WMS_*` role hierarchy bean (mirror `admin-service`), re-map the `requestMatchers` READ/WRITE tiers to `WMS_VIEWER`/`WMS_OPERATOR` **plus** the retained legacy `OUTBOUND_*` (dual-accept).
- Any `@PreAuthorize` / method-security annotations on outbound controllers that reference `OUTBOUND_*` — extend to accept the `WMS_*` equivalent.
- Confirm **TASK-MONO-304 tenant-scoping** (application-layer `CallerScope` 403 / list scoping) still composes correctly **after** the security-filter role gate change — the two layers are independent and must both hold.
- Tests: unit (role-hierarchy gating matrix) + Testcontainers `@SpringBootTest` IT (real JWT → authority wiring).

### Out of Scope

- The throwaway demo bridge (`entitled_domains∋wms → ROLE_OUTBOUND_*`) — a local-demo hack, explicitly NOT this fix.
- Any IAM (`OperatorRoleDerivation`) or shared-contract (`jwt-standard-claims.md`) change — Option A needs none (see Contract note). If a reviewer mandates one, it is a separate cross-project task.
- The ecommerce→wms fulfillment loop progression (picking/pack/ship operator steps) — separate concern.
- Deploying a wms gateway into the demo stack.

---

## Acceptance Criteria

- [ ] **AC-1** — A federated operator JWT with `roles ∋ WMS_OPERATOR` (IAM-issued at assume-tenant for a wms-entitled tenant), tenant-validated, receives **200** on `GET /api/v1/outbound/orders` (no `HTTP_403` role-gate rejection).
- [ ] **AC-2 (READ tier)** — A `roles = [WMS_VIEWER]` token (read-only, e.g. entitlement-trust synthesis) gets **200** on outbound GETs and **403** on outbound writes.
- [ ] **AC-3 (WRITE tier)** — A `roles = [WMS_OPERATOR]` (or `WMS_ADMIN`/`WMS_SUPERADMIN`) token is admitted on `POST/PATCH/PUT/DELETE /api/**`; a token with neither the `WMS_*` write tier nor a legacy write authority is **403**.
- [ ] **AC-4 (backward-compat)** — A legacy `roles = [OUTBOUND_READ]` / `[OUTBOUND_WRITE]` token still passes the corresponding read/write gates (dual-accept; standalone not regressed).
- [ ] **AC-5 (consistency)** — The role vocabulary `outbound-service` enforces is consistent with `platform/contracts/jwt-standard-claims.md` and with what IAM `OperatorRoleDerivation` issues for wms (`WMS_OPERATOR`); no residual issuance↔enforcement gap for the federated path.
- [ ] **AC-6 (tenant-scope composition)** — TASK-MONO-304 still holds: an ecommerce-tenant operator sees only `tenant_id=ecommerce` + `source=FULFILLMENT_ECOMMERCE` outbound orders, and a cross-tenant order fetch still returns `TENANT_SCOPE_DENIED` 403. The security-filter change did not bypass the application-layer scope.
- [ ] **AC-7 (IT authority)** — A Testcontainers `@SpringBootTest` IT pins AC-1/AC-2/AC-3/AC-6 against a real JWT (Docker-free `:check` cannot prove the JWT→authority wiring — full-boot IT is authority).

---

## Related Specs

> **Before reading**: follow `platform/entrypoint.md` Step 0 — read `projects/wms-platform/PROJECT.md`, load `rules/common.md` + matched `rules/domains/<domain>.md` + `rules/traits/<trait>.md`. Determine outbound-service `Service Type` from its `architecture.md` → read the one matching `platform/service-types/<type>.md`.

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § O1 / O5 4a — operator domain-role derivation at assume-tenant (`wms → WMS_OPERATOR`).
- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` — unified identity, `roles` sole authz axis; D5 step-1 dual-read pattern (basis for the dual-accept decision).
- `projects/wms-platform/tasks/done/TASK-BE-383-wms-roles-only-operator-spec-alignment.md` — **lineage**; documented that `WMS_OPERATOR` grants outbound read+WRITE and that entitlement-trust `WMS_VIEWER` is read-only — but did **not** align the outbound-service code (this task completes that).
- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `projects/wms-platform/specs/services/admin-service/architecture.md` — the **reference model** (WMS_* hierarchy + entitlement-trust `WMS_VIEWER`).
- `projects/wms-platform/specs/integration/iam-integration.md`

# Related Skills

- `.claude/skills/backend/` — consult `.claude/skills/INDEX.md` for auth/Spring Security gating skills if present.

---

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` — SHARED source of truth for the operator role vocabulary (lists `WMS_OPERATOR`). Read-only for this task (Option A needs no amend; see Contract note).
- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` — outbound operator surface (the gated endpoints); § Global Conventions auth model. Update its role-requirement wording to `WMS_*` (+ retained legacy) if it currently states `OUTBOUND_*`.

---

## Target Service

- `outbound-service` (wms-platform)

---

## Architecture

Follow:

- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- Mirror the role-hierarchy + `@EnableMethodSecurity` pattern from `projects/wms-platform/apps/admin-service/src/main/java/com/wms/admin/config/SecurityConfig.java`.

---

## Implementation Notes

- Reuse `admin-service`'s `RoleHierarchy` string (`ROLE_WMS_SUPERADMIN > ROLE_WMS_ADMIN` / `ROLE_WMS_ADMIN > ROLE_WMS_OPERATOR` / `ROLE_WMS_OPERATOR > ROLE_WMS_VIEWER`) so a single `hasRole("WMS_VIEWER")` on reads admits all higher tiers.
- Keep the existing `jwtAuthenticationConverter` reading the `roles`/`role` claims (no claim-shape change); only the `requestMatchers` authorities and the hierarchy bean change.
- Dual-accept = `hasAnyRole("WMS_VIEWER", …, "OUTBOUND_READ", …)` per tier — do NOT drop `OUTBOUND_*` in this task (that legacy removal, if ever, is a separate migration after standalone RBAC is migrated).
- Do NOT touch the application-layer `CallerScope` (TASK-MONO-304) logic; only verify it still composes (AC-6).

---

## Edge Cases

- **READ vs WRITE coarseness** — `WMS_OPERATOR` is coarse; the hierarchy + tier split (VIEWER read / OPERATOR write) preserves the granularity the old `OUTBOUND_*` split encoded. Do not collapse reads and writes onto one role.
- **Two authz layers compose** — the security-filter role gate (this task) sits in front of the application-layer `CallerScope` tenant-scope (TASK-MONO-304); changing the former must not bypass the latter.
- **Standalone vs federated token shapes** — standalone may carry `OUTBOUND_*`; federated carries `WMS_*`. Dual-accept admits both.
- **`gap`/IdP-platform token** — an operator who has not assumed a wms-entitled tenant has no wms role and correctly stays 403 (least privilege; do not weaken).

---

## Failure Scenarios

- **F1 — over-grant** — accepting `WMS_OPERATOR` for all methods would grant WRITE to read-only operators, violating the BE-383 "entitlement-trust never grants WRITE" invariant. Guarded by AC-2/AC-3 (tiered).
- **F2 — standalone regression** — switching to `WMS_*` without retaining `OUTBOUND_*` 403s standalone deployments. Guarded by AC-4 (dual-accept).
- **F3 — tenant-scope bypass** — a SecurityConfig refactor that alters JWT→claim handling could drop the `tenant_id` path `CallerScope` relies on (cross-tenant leak). Guarded by AC-6 + IT.
- **F4 — wiring not exercised by `:check`** — a Docker-free pass mocking the JWT goes green while the real JWT→authority conversion still 403s on full boot. Guarded by AC-7 (Testcontainers IT is authority).
- **F5 — contract wording drift** — changing enforcement without updating `outbound-service-api.md`'s role wording re-creates a spec↔code gap. Guarded by AC-5 + the Related Contracts update.

---

## Test Requirements

- Unit: role-hierarchy gating matrix (VIEWER/OPERATOR/ADMIN/SUPERADMIN × GET/POST/PATCH/PUT/DELETE) + legacy `OUTBOUND_*` dual-accept.
- Integration: Testcontainers `@SpringBootTest` proving AC-1/AC-2/AC-3/AC-6 against a real signed JWT.
- Contract-related: assert the `outbound-service-api.md` role wording matches the enforced authorities.

---

## Definition of Done

- [ ] `WMS_*` hierarchy gating implemented in `outbound-service` `SecurityConfig` with legacy `OUTBOUND_*` dual-accept
- [ ] Unit + Testcontainers IT added and passing
- [ ] TASK-MONO-304 tenant-scoping verified intact (AC-6)
- [ ] `outbound-service-api.md` role wording updated
- [ ] No IAM / shared-contract change required (or, if review mandates one, split to a separate cross-project task)
- [ ] Ready for review

---

## Provenance

Discovered 2026-06-25 restoring the federation-hardening-e2e console `/wms/outbound` view. After fixing console-web base-URL plumbing and confirming ADR-032 is fully implemented (IAM **does** issue a unified `roles` claim), the live screen surfaced a bare `HTTP_403` from the outbound-service Spring Security role gate. Root cause = the 3-way role-vocabulary drift above, `admin-service` aligned, `outbound-service` the outlier. Backlog candidate created, then sharpened to the decided Option A (adopt `WMS_*` hierarchy + dual-accept) and promoted to ready 2026-06-25.
