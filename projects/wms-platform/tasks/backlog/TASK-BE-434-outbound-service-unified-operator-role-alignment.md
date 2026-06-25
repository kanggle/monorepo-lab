# TASK-BE-434 — Align outbound-service authorization to the unified operator-role model (resolve `OUTBOUND_*` vs `WMS_OPERATOR` drift)

**Status:** backlog

**Owner:** backend

**Task Tags:** code, api, adr

> **Recommendation annotation** (CLAUDE.md): 분석=Opus 4.8 / 구현 권장=Opus — authorization/security model decision (which role vocabulary is authoritative, cross-service + contract consistency). Not a mechanical fix.

---

## Goal

Make a federated console operator who holds the IAM-issued wms domain role (`roles ∋ WMS_OPERATOR`) able to reach the wms `outbound-service` operator endpoints, consistent with (a) the unified identity model (ADR-MONO-032 / ADR-MONO-035), (b) the sibling `admin-service` which already admits `WMS_OPERATOR`, and (c) the documented intent in **TASK-BE-383** that *"an operator's `WMS_OPERATOR` role grants read-everywhere + WRITE in inventory/inbound/**outbound**"*.

Today this is **broken**: `outbound-service` `SecurityConfig` gates `GET/POST/PATCH/PUT/DELETE /api/**` on `OUTBOUND_READ` / `OUTBOUND_WRITE` / `OUTBOUND_ADMIN`, but IAM (`OperatorRoleDerivation`) derives only `WMS_OPERATOR` for the wms domain at assume-tenant and **never issues `OUTBOUND_*`**. Result: a federated operator passes the JWT + `TenantClaimValidator` (entitled) but is **403'd at the Spring Security role gate** (`HTTP_403`, no domain body), before any application-layer logic. The console `/wms/outbound` screen therefore shows *"이 화면을 조회할 권한이 없습니다"* for every federated operator.

After this task, the outbound surface admits federated operators per a single, contract-authoritative role model, with no throwaway bridge.

### The drift (verified 2026-06-25)

| Source | Role name(s) it speaks for wms outbound |
|---|---|
| Contract `platform/contracts/jwt-standard-claims.md` (L28, L79, L241) | `WMS_OPERATOR` + `OUTBOUND_MANAGER` |
| IAM issuance `OperatorRoleDerivation.fromEntitledDomains` (`case "wms" -> "WMS_OPERATOR"`) | `WMS_OPERATOR` only |
| `outbound-service` `SecurityConfig:91-96` enforcement | `OUTBOUND_READ` / `OUTBOUND_WRITE` / `OUTBOUND_ADMIN` |
| *(aligned sibling)* `admin-service` `SecurityConfig` | `WMS_SUPERADMIN > WMS_ADMIN > WMS_OPERATOR > WMS_VIEWER` |

**Three vocabularies, none mutually consistent.** `admin-service` is the model to follow (it consumes what IAM issues). `outbound-service` is the outlier still on its standalone-era `OUTBOUND_*` taxonomy.

---

## Scope

### In Scope

- A **design decision** (this is the crux): which role(s) authorize the outbound operator surface — resolved against the contract `jwt-standard-claims.md` as the source of truth. Candidate resolutions:
  - **(A)** `outbound-service` admits `WMS_OPERATOR` (read-everywhere) + a wms WRITE-tier role for mutations — mirror the `admin-service` `WMS_*` role hierarchy. Smallest change; matches what IAM issues today.
  - **(B)** Honor the contract's `OUTBOUND_MANAGER`: IAM derivation also emits `OUTBOUND_MANAGER` for wms-entitled operators (or maps it), and `outbound-service` accepts it. Requires an IAM-platform change too.
  - **(C)** Keep `OUTBOUND_*` as the fine-grained service vocabulary but introduce a role-translation layer (gateway filter or service converter) `WMS_OPERATOR → OUTBOUND_*`. New translation component.
- Aligning `outbound-service` `SecurityConfig` (and any `@PreAuthorize`) to the chosen model.
- Preserving the **READ vs WRITE granularity** the current `OUTBOUND_*` split encodes (GET = read; POST/PATCH/PUT/DELETE = write), mapped onto the chosen role(s).
- Confirming **TASK-MONO-304 tenant-scoping** (the application-layer `CallerScope` 403 / list scoping) still holds **after** the security-filter role gate is changed — the two layers are independent and must compose.
- Contract/spec consistency: if the chosen model differs from `jwt-standard-claims.md` (e.g. the contract's `OUTBOUND_MANAGER`), the **contract is updated first** (per its § Change Rule) — note this may pull a `platform/` (shared) edit into the task, see Out of Scope boundary note.
- Testcontainers `@SpringBootTest` IT proving a `roles=[WMS_OPERATOR]` token gets 200 on the outbound reads and the correct 200/403 on writes.

### Out of Scope

- The throwaway demo bridge (`entitled_domains∋wms → ROLE_OUTBOUND_*`) — that is a local-demo hack, explicitly NOT the fix.
- wms **standalone** RBAC role assignment (`admin-service` `RoleService` assigning `OUTBOUND_*` directly) — must keep working, but is not modified here unless the chosen model requires it.
- The ecommerce→wms fulfillment loop progression (picking/pack/ship operator steps) — separate concern.
- Deploying a wms gateway into the federation-hardening-e2e demo stack (operational, not a code fix) — unless resolution (C) is chosen.

> **Shared-boundary note**: the role *enforcement* fix is `wms-platform`-internal (`outbound-service`). BUT the authoritative role vocabulary lives in `platform/contracts/jwt-standard-claims.md` (**shared**), and resolution (B)/(C) would also touch `iam-platform` (`OperatorRoleDerivation`). If the chosen resolution requires editing the shared contract or IAM, this task **escalates to monorepo-level / cross-project** (atomic PR: contract + iam + wms) per CLAUDE.md § Cross-Project Changes, and the contract change lands **first**. The backlog→ready gate must settle this.

---

## Acceptance Criteria

- [ ] **AC-1** — A federated operator JWT carrying `roles ∋ WMS_OPERATOR` (as issued by IAM `OperatorRoleDerivation` at assume-tenant for a wms-entitled tenant), tenant-validated, receives **200** on `GET /api/v1/outbound/orders` (no `HTTP_403` role-gate rejection).
- [ ] **AC-2** — Outbound **mutations** (POST/PATCH/PUT/DELETE) are admitted/denied per the chosen role model's WRITE tier, with a deterministic test for both the allowed and the denied role shape.
- [ ] **AC-3** — The role vocabulary used by `outbound-service` is **consistent with** `platform/contracts/jwt-standard-claims.md` and with what IAM actually issues (`OperatorRoleDerivation`); if any of the three was changed, all three now agree (no residual 3-way drift).
- [ ] **AC-4** — TASK-MONO-304 tenant-scoping still holds: an ecommerce-tenant operator sees only `tenant_id=ecommerce` + `source=FULFILLMENT_ECOMMERCE` outbound orders, and a cross-tenant order fetch still returns the `TENANT_SCOPE_DENIED` 403 (the security-filter change did not bypass the application-layer scope).
- [ ] **AC-5** — wms standalone authorization (direct `OUTBOUND_*` RBAC assignment) is not regressed: a token shape carrying the previously-working authorities still passes (or the migration of those grants is explicitly handled).
- [ ] **AC-6** — A Testcontainers `@SpringBootTest` IT pins AC-1/AC-2/AC-4 (Docker-free `:check` cannot prove the JWT→authority wiring — full-boot IT is authority).

---

## Related Specs

> **Before reading**: follow `platform/entrypoint.md` Step 0 — read `projects/wms-platform/PROJECT.md`, load `rules/common.md` + matched `rules/domains/<domain>.md` + `rules/traits/<trait>.md`. Determine the outbound-service `Service Type` from `specs/services/outbound-service/architecture.md` → read the one matching `platform/service-types/<type>.md`.

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` — unified identity, `roles` sole authz axis (COMPLETE, MONO-265).
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` § O1 / O5 4a — operator domain-role derivation at assume-tenant (`wms → WMS_OPERATOR`).
- `projects/wms-platform/tasks/done/TASK-BE-383-wms-roles-only-operator-spec-alignment.md` — **lineage**; documented that `WMS_OPERATOR` grants outbound read+WRITE but did **not** align the outbound-service code (this task completes that).
- `projects/wms-platform/specs/services/outbound-service/architecture.md`
- `projects/wms-platform/specs/services/admin-service/architecture.md` — the **aligned sibling** (WMS_* hierarchy + entitlement-trust `WMS_VIEWER` synthesis); reference model.
- `projects/wms-platform/specs/integration/iam-integration.md`

---

## Related Contracts

- `platform/contracts/jwt-standard-claims.md` — **SHARED / source of truth** for the operator role vocabulary (lists `WMS_OPERATOR`, `OUTBOUND_MANAGER`). Authoritative tiebreaker for the design decision. (Read-only unless resolution requires a contract amend — then contract-first, monorepo-level.)
- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` — outbound operator surface (the endpoints being gated); § Global Conventions auth model.

---

## Target Service

- `outbound-service` (wms-platform) — primary
- `iam-platform` `auth-service` (`OperatorRoleDerivation`) — only if resolution (B)/(C) chosen

---

## Edge Cases

- **`OUTBOUND_MANAGER` reconciliation** — the contract names `OUTBOUND_MANAGER` but IAM issues `WMS_OPERATOR` and the service requires `OUTBOUND_READ/WRITE/ADMIN`. The decision must pick one authoritative shape, not add a fourth.
- **READ vs WRITE coarseness** — `WMS_OPERATOR` is a single coarse role; the current `OUTBOUND_*` split gives READ/WRITE/ADMIN tiers. Mapping coarse `WMS_OPERATOR` onto all methods may **over-grant WRITE** to a read-only operator. Preserve the read-only path (cf. admin-service `WMS_VIEWER` synthesis is READ-only, never WRITE).
- **Two authz layers compose** — the security-filter role gate (this task) sits in front of the application-layer `CallerScope` tenant-scope (TASK-MONO-304). Changing the former must not bypass or duplicate the latter.
- **Standalone vs federated token shapes** — standalone wms tokens may carry `OUTBOUND_*` directly (RBAC); federated tokens carry `WMS_OPERATOR`. The chosen model must admit both or migrate one.
- **`gap`/IdP-platform token** — `OperatorRoleDerivation` maps `gap`/unknown → no role; an operator who has not assumed a wms-entitled tenant correctly stays 403 (least privilege; do not weaken).

---

## Failure Scenarios

- **F1 — over-grant** — naively accepting `WMS_OPERATOR` for all HTTP methods grants WRITE to operators who should be read-only, violating the BE-383 "entitlement-trust never grants WRITE" invariant. Guarded by AC-2/AC-5.
- **F2 — silent contract drift** — changing `outbound-service` role names without updating `jwt-standard-claims.md` (or vice-versa) re-creates the drift on a different axis. Guarded by AC-3 + contract-first rule.
- **F3 — tenant-scope bypass** — a security-filter refactor that changes how the JWT is converted could drop the `tenant_id` claim path that `CallerScope` relies on, silently disabling TASK-MONO-304 scoping (cross-tenant data leak). Guarded by AC-4 + IT.
- **F4 — standalone regression** — aligning to `WMS_OPERATOR` without handling existing `OUTBOUND_*`-bearing standalone tokens 403s the standalone deployment. Guarded by AC-5.
- **F5 — wiring not exercised by `:check`** — a Docker-free unit/`:check` pass that mocks the JWT will go green while the real JWT→authority conversion still 403s on full boot. Guarded by AC-6 (Testcontainers IT is authority).

---

## Provenance

Discovered 2026-06-25 while restoring the federation-hardening-e2e console demo `/wms/outbound` view. After fixing the console-web base-URL plumbing (network path) and confirming ADR-032 is fully implemented (IAM **does** issue a unified `roles` claim), the live screen surfaced a bare `HTTP_403` from the outbound-service Spring Security role gate. Root cause traced to the 3-way role-vocabulary drift above (contract `OUTBOUND_MANAGER` / IAM `WMS_OPERATOR` / service `OUTBOUND_*`), with `admin-service` already aligned and `outbound-service` the outlier. The local demo can be unblocked with a throwaway `WMS_OPERATOR→OUTBOUND_*` SecurityConfig bridge (NOT this task); this task is the durable alignment.
