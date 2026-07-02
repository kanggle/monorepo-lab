# TASK-BE-467 — Confine account MUTATION endpoints to the target account's tenant (GAP-ACCOUNT-01)

- **Status**: ready
- **Domain**: iam (admin-service + account-service)
- **Type**: security fix (backend)
- **분석=Opus 4.8 / 구현 권장=Opus 4.8 (security-critical cross-service)**

## Goal

Close **GAP-ACCOUNT-01**: the admin account **mutation** endpoints
(`lock` / `unlock` / session-`revoke` / `gdpr-delete` / `export` / `bulk-lock`)
enforce **no tenant confinement on the target account**. Only the RBAC
permission (`account.lock` etc.) is checked; no `QueryTenantScopeGate` /
`TenantScopeGuard` call happens in `AccountAdminUseCase` / `SessionAdminUseCase`
/ `GdprAdminUseCase` / `BulkLockAccountUseCase`, and the account-service internal
controllers accept `accountId` with **no `X-Tenant-Id` scope check**. Today a
platform-scope `SUPPORT_LOCK` operator can lock/delete **any tenant's account**
by accountId. This is a latent cross-tenant vulnerability AND the prerequisite
for ever delegating account management to a tenant-scoped role.

The READ path (`GET /api/admin/accounts`) is ALREADY confined via
`QueryTenantScopeGate` (TASK-BE-357) — this task brings the mutation path to
parity, **net-zero for the only current holder (SUPER_ADMIN `'*'`)**.

## Design (authoritative — mirror the read path + the existing `X-Tenant-Id` defense-in-depth)

Two-layer confinement, keyed on the actor's **active tenant** (`X-Tenant-Id`):

**Layer 1 — admin-service (actor may act in tenant T):**
- Each mutation controller reads `@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId`.
- Resolve via the EXISTING read-path gate:
  `resolved = queryTenantScopeGate.resolve(op, tenantId, <ActionCode>, <Permission>).tenantId()`
  (omitted header → operator's own tenant; out-of-scope → `403 TENANT_SCOPE_DENIED` + best-effort DENIED row — identical to the list).
- Thread `resolved` into the command → use-case → `AccountServiceClient`.

**Layer 2 — account-service (target account is in tenant T):**
- The internal mutation controllers (`/internal/accounts/{id}/lock|unlock|delete|export`, and the session-revoke internal EP) read
  `@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId`.
- Enforce: if `tenantId` is present AND not `'*'`, the loaded account's
  `tenant_id` MUST equal it; mismatch (or account not found) → **`404` `ACCOUNT_NOT_FOUND`** (enumeration-safe — never leak cross-tenant existence, never 403 that confirms the account exists elsewhere).
- **NET-ZERO**: `tenantId` absent OR `'*'` → skip the check (platform scope / legacy caller — byte-identical to today).

**admin `AccountServiceClient`**: switch the mutation calls (`lock`, `unlock`,
`gdprDelete`, `export`, and the session-revoke call — check
`SessionAdminUseCase`'s client) to stamp `X-Tenant-Id` using the EXISTING
`callPostWithTenant` / `callGetWithTenant` helpers (already present for the
identity EPs). Add a `tenantId` parameter to each mutation method.

`bulk-lock`: `BulkLockAccountUseCase` delegates per-row to
`AccountAdminUseCase.lock(...)`; threading `resolved` through the bulk command →
per-row lock automatically confines every row (a cross-tenant row → that row's
result = the mapped `404`/failure, not a whole-batch failure — preserve the
per-row outcome shape).

## Scope

- admin-service: `AccountAdminController`, `SessionAdminController`,
  `AdminGdprController` (+ their use-cases + command records +
  `AccountServiceClient`), `BulkLockAccountUseCase` / `BulkLockAccountCommand`.
- account-service: the internal mutation controllers
  (`AccountLockController` lock/unlock/delete, the export controller, the
  session/token-revoke internal controller) + the tenant-equality check
  (load account, compare `tenant_id`; reuse the domain/repo lookup — do NOT add
  a new cross-tenant finder that bypasses isolation).
- Contract: `iam/specs/contracts/http/internal/admin-to-account.md` +
  `admin-api.md` account mutation sections — document the `X-Tenant-Id` header +
  the `404 ACCOUNT_NOT_FOUND` cross-tenant behavior.

Out of scope: granting any account.* permission to TENANT_ADMIN (that is the
follow-up delegation task); GDPR/export delegation policy.

## Acceptance Criteria

- AC-1: A non-platform operator (or a SUPER_ADMIN with active tenant = T) can
  only lock/unlock/revoke/gdpr/export/bulk-lock accounts whose `tenant_id == T`;
  a cross-tenant target → `404 ACCOUNT_NOT_FOUND` (enumeration-safe), audited as
  FAILURE (not SUCCESS).
- AC-2: **NET-ZERO** — SUPER_ADMIN `'*'` with no active tenant (X-Tenant-Id
  absent/`'*'`) behaves byte-identically to today (any account). No existing
  test regresses.
- AC-3: The confinement reuses `QueryTenantScopeGate` (admin) + `X-Tenant-Id`
  defense-in-depth at account-service — NO new bespoke scope logic, NO new
  cross-tenant repository finder.
- AC-4: Out-of-scope active tenant (non-platform requests a tenant not in
  home ∪ assignments) → `403 TENANT_SCOPE_DENIED` (same as the list read).
- AC-5: `./gradlew :apps:admin-service:test :apps:account-service:test` green;
  Testcontainers IT covering cross-tenant lock → 404 and same-tenant → success
  (IT is the authority — the Docker-free `:test` will not exercise the wiring).
- AC-6: Contracts updated (admin-to-account.md + admin-api.md).

## Related Specs / Contracts

- `iam/specs/services/admin-service/rbac.md` (account.* keys, QueryTenantScopeGate vs TenantScopeGuard)
- `iam/specs/contracts/http/internal/admin-to-account.md`
- `iam/specs/contracts/http/admin-api.md` (account mutation sections)

## Edge Cases

- Account exists in tenant A, operator scoped to B → `404` (not 403; no existence leak).
- bulk-lock mixing in-tenant + cross-tenant accountIds → in-tenant locked, cross-tenant rows → per-row `ACCOUNT_NOT_FOUND` outcome (batch still 200).
- X-Tenant-Id `'*'` from a non-platform operator → the gate already rejects (they can't resolve `'*'`); belt-and-suspenders at account-service treats `'*'` as skip, but the admin gate is the primary guard.
- export uses `audit.read` (not account.lock) — still must be tenant-confined (same header treatment).

## Failure Scenarios

- account-service down → existing circuit-breaker/DownstreamFailure path unchanged (audit FAILURE).
- Missing X-Tenant-Id from a non-platform operator → gate defaults to their own tenant (safe confinement), never unscoped.
