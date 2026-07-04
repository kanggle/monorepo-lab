# TASK-BE-471 — Reconcile BE-467/468 tenant-confinement decisions into design specs

- **Status**: review
- **Domain**: iam (admin-service + auth-service + account-service — specs only)
- **Type**: spec-documentation reconciliation (no code) — follow-up to TASK-BE-467 / TASK-BE-468
- **분석=Opus 4.8 / 구현 권장=Opus 4.8 (docs-only)**

## Goal

BE-467 (admin account-mutation tenant confinement) and BE-468 (session-revoke /
force-logout tenant confinement) shipped and updated the **HTTP contracts**
(`admin-api.md`, `admin-to-account.md`, `admin-to-auth.md`). The **service design
specs** — the authoritative design layer above the wire contracts — were listed as
"Related Specs" on both tasks but never updated, so they now under-document the
shipped behavior. This task closes that drift so a reader of the design specs learns
that the account-data mutation path and force-logout are tenant-confined.

Specs are the source of truth; this brings the design layer to parity with the
already-merged code + contracts. Net-zero on behavior (documentation only).

## Scope

- `specs/services/admin-service/rbac.md` — add an **Account-Data Mutation
  Confinement (TASK-BE-467)** subsection alongside the existing `TenantScopeGuard`
  (D2) / `RoleGrantGuard` (D3) axes. Distinct axis: the account-**data** surface
  (`lock`/`unlock`/`gdpr-delete`/`export`/`bulk-lock`) is confined by resolving the
  actor's active tenant through the **read-path** `QueryTenantScopeGate` (TASK-BE-357),
  stamping it as `X-Tenant-Id` to account-service, where a tenant-scoped `findById`
  yields **`404 ACCOUNT_NOT_FOUND`** (enumeration-safe, not 403) for a cross-tenant
  target. Net-zero for SUPER_ADMIN `'*'` / header-absent (→ `fan-platform`).
- `specs/services/auth-service/architecture.md` — document force-logout tenant
  confinement (BE-468) in the `application/` boundary rules: `ForceLogoutUseCase`
  credential-tenant gate → cross-tenant = **no-op `200 count=0`** (no DB revoke, no
  Redis invalidation, **no 404** — avoids admin's `SessionAdminUseCase` 503 mis-map),
  net-zero on absent/`'*'`. Correct the Forbidden-Dependencies ❌ line that lumps
  force-logout with refresh-rotation under a `401 TOKEN_TENANT_MISMATCH` description
  (force-logout confines via no-op, not 401).
- `specs/services/account-service/architecture.md` — note that admin-reachable
  mutation use-cases (`AccountStatusUseCase`/`GdprDeleteUseCase`/`DataExportUseCase`)
  honor `X-Tenant-Id` (absent/`'*'` → `FAN_PLATFORM` net-zero) instead of the old
  hard-pinned literal, and that the consumer-facing use-cases
  (signup/verify-email/profile/last-login) remain FAN-pinned as genuine BE-229
  Phase-3 debt (out of scope of BE-467).

Out of scope: any code change; any HTTP-contract change (already done by BE-467/468);
the BE-229 Phase-3 consumer-path FAN-pin removal.

## Acceptance Criteria

- AC-1: `rbac.md` documents the account-data mutation confinement axis (gate =
  `QueryTenantScopeGate`, header = `X-Tenant-Id`, cross-tenant = 404, net-zero) and
  distinguishes it from the D2 `TenantScopeGuard` operator-admin surface.
- AC-2: `auth-service/architecture.md` documents the force-logout no-op confinement
  (BE-468) and the ❌ line no longer implies force-logout returns 401.
- AC-3: `account-service/architecture.md` records the admin-mutation `X-Tenant-Id`
  threading + net-zero + the residual consumer-path FAN-pin (BE-229 debt).
- AC-4: No behavioral/code change; all edits are documentation. Cross-references to
  `admin-api.md` / `admin-to-account.md` / `admin-to-auth.md` resolve.

## Related Specs / Contracts

- `specs/services/admin-service/rbac.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/account-service/architecture.md`
- `specs/contracts/http/admin-api.md` (authoritative wire behavior — already updated)
- `specs/contracts/http/internal/admin-to-account.md`, `.../admin-to-auth.md`
- TASK-BE-467, TASK-BE-468 (done) — the shipped decisions being documented

## Edge Cases

- rbac.md must NOT conflate the two confinement axes (operator-admin surface via
  `TenantScopeGuard` → 403 vs account-data surface via `QueryTenantScopeGate` → 404).
- The force-logout confinement is deliberately a no-op 200 (not 404) — the doc must
  state the reason (admin `SessionAdminUseCase` maps downstream 4xx → 503 audit FAILURE).

## Failure Scenarios

- N/A (documentation-only; no runtime path changes).
