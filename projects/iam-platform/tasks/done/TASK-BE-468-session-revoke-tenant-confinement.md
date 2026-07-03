# TASK-BE-468 — Session-revoke (force-logout) tenant confinement (auth-service)

- **Status**: done
- **Domain**: iam (auth-service)
- **Type**: correctness + confinement (backend) — follow-up to TASK-BE-467
- **분석=Opus 4.8 / 구현 권장=Opus 4.8**

## Implementation Notes (2026-07-04)

Self-contained in auth-service, mirrors BE-467:

- `ForceLogoutUseCase` gained `CredentialRepository` + a tenant-aware overload
  `execute(accountId, requestedTenantId)`; the existing `execute(accountId)` delegates
  with `null` (NET-ZERO). Gate: absent/blank/`'*'` → net-zero (revoke across the
  account's tenant, today's behavior); a concrete tenant that does not own the account
  (credential's tenant differs, or no credential) → **no-op** (`revokedTokenCount=0`,
  no DB revoke, no Redis invalidation — enumeration-safe, never disrupts another
  tenant's sessions); a concrete owning tenant → normal revoke + Redis. No new
  tenant-scoped query (an account's tokens are all in one tenant; the confinement is
  the credential-tenant gate). No 404 (would mis-map to 503 in admin's
  `SessionAdminUseCase`) — a confined call is a clean 200 `count=0`.
- `InternalCredentialController.forceLogout` reads `X-Tenant-Id` → `execute(id, tenant)`.
- No admin-service change (BE-467 already stamps `X-Tenant-Id` on the force-logout call).

**Tests**: `ForceLogoutUseCaseTest` extended (+`CredentialRepository` mock; cross-tenant
no-op, no-credential no-op, same-tenant revoke, wildcard net-zero). AC-4 Testcontainers
IT `ForceLogoutTenantConfinementIntegrationTest` (drives the use-case against real
MySQL+Redis: cross-tenant → 0 revoked + wms sessions survive; same-tenant → revoked;
net-zero + `'*'` → revoked; seeds two tenants' credentials + refresh tokens). Full
`:apps:auth-service:test` green locally (`--rerun-tasks`, Mockito ctor-dep change);
**CI Linux is the IT authority** (`@Tag("integration")` Docker-skipped locally).
Contracts updated: `admin-to-auth.md` (force-logout confinement) + `admin-api.md`
(session-revoke note: BE-467 propagation + BE-468 enforcement).

## Context — the BE-467 residual

TASK-BE-467 brought the admin account-**mutation** path (lock/unlock/bulk-lock/
gdpr-delete/export) to tenant parity: the actor's active tenant is resolved via
`QueryTenantScopeGate` and stamped as `X-Tenant-Id`, and account-service confines
the target (cross-tenant → `404 ACCOUNT_NOT_FOUND`). For **session-revoke**, BE-467
delivered only the admin-side propagation — `AuthServiceClient.forceLogout` already
stamps `X-Tenant-Id` — and explicitly deferred the **actual enforcement** to
auth-service as a "separate enforcement point" (documented in `admin-api.md`). This
task closes that residual.

Today `POST /internal/auth/accounts/{accountId}/force-logout` →
`ForceLogoutUseCase.execute(accountId)` revokes **all** refresh tokens for the
account (`revokeAllByAccountId`) + invalidates the account's Redis bulk/access
markers — with **no tenant scoping**. A non-platform operator scoped to tenant B
who targets an account in tenant A would revoke A's sessions (a cross-tenant
disruption of another tenant's user).

## Design (authoritative — mirror BE-467, self-contained in auth-service)

An account belongs to exactly one tenant, so its refresh tokens all carry that one
`tenant_id` and its credential row (`credentials`, unique `account_id`) records the
owning tenant. The confinement is therefore a **tenant gate** on the credential's
tenant — no new tenant-scoped revoke query is needed (`revokeAllByAccountId` is
already single-account/single-tenant by construction).

- `ForceLogoutUseCase` gains `CredentialRepository` and a tenant-aware overload
  `execute(String accountId, String requestedTenantId)`; the existing
  `execute(String accountId)` delegates with `null` (NET-ZERO).
- Resolution: absent / blank / `'*'` (SUPER_ADMIN platform scope) →
  **NET-ZERO** (revoke across the account's tenant — byte-identical to today). A
  **concrete** tenant that does NOT own the account (`credential.tenantId != T`, or
  no credential) → **no-op**: return `revokedTokenCount=0` with **no DB revoke and
  no Redis invalidation** (enumeration-safe — never reveals the account exists in
  another tenant; never disrupts the other tenant's sessions). A concrete tenant
  that owns the account → the normal revoke + Redis path.
- `InternalCredentialController.forceLogout` reads
  `@RequestHeader(value = "X-Tenant-Id", required = false) String tenantId` and
  threads it into the use-case.

Response shape unchanged (`accountId`, `revokedTokenCount`, `revokedAt`). No new
exception / 404: revoke is idempotent and count-returning, so a confined cross-tenant
call is a clean `200` with `revokedTokenCount=0` — this also avoids a 404 that
admin-service's `SessionAdminUseCase` would mis-map to `503` (its downstream 4xx
path finalizes audit FAILURE). No admin-service change required (BE-467 already
stamps the header).

## Scope

- auth-service: `ForceLogoutUseCase` (tenant gate + overload + `CredentialRepository`),
  `InternalCredentialController` (read `X-Tenant-Id`).
- Contract: `iam/specs/contracts/http/internal/admin-to-auth.md` (force-logout
  section — document `X-Tenant-Id` + the cross-tenant no-op confinement).

Out of scope: the consumer self-service session endpoints (`AccountSessionController`
revoke-others — device-scoped, not operator-tenant-scoped); adding a tenant column
to `refresh_tokens` (already present, TASK-BE-229, but unused by this gate); any
admin-service change (BE-467 already propagates `X-Tenant-Id`).

## Acceptance Criteria

- AC-1: A concrete `X-Tenant-Id = T` that does not own the target account →
  `revokedTokenCount=0`, **no** refresh-token rows revoked, **no** Redis
  bulk/access invalidation for that account (verified: the other tenant's sessions
  survive). Enumeration-safe (no existence leak, no 404 vs 200 distinction beyond
  the count).
- AC-2: **NET-ZERO** — `X-Tenant-Id` absent or `'*'` behaves byte-identically to
  today (revoke all of the account's tokens + Redis). The existing
  `ForceLogoutUseCase` unit test and any force-logout IT do not regress.
- AC-3: A concrete `X-Tenant-Id = T` that owns the account → identical to the
  net-zero path (revoke + Redis) — because the account's tokens are all in T.
- AC-4: `./gradlew :apps:auth-service:test` green; Testcontainers IT covering
  cross-tenant no-op (0 revoked, other-tenant session survives) + same-tenant revoke
  + net-zero. Requires seeding two tenants' credentials + refresh tokens.
- AC-5: Contract `admin-to-auth.md` force-logout section updated.

## Related Specs / Contracts

- `iam/specs/contracts/http/internal/admin-to-auth.md`
- `iam/specs/contracts/http/admin-api.md` (session-revoke — the BE-467 "separate
  enforcement point" note is resolved by this task)
- TASK-BE-467 (done) — the admin-side propagation this task's enforcement pairs with.

## Edge Cases

- Account has an active refresh token but no `credentials` row (should not happen —
  every account has a credential) → concrete-tenant path treats "no credential" as
  not-owned → 0 revoked (fail-closed confinement). Net-zero path still revokes.
- Concrete tenant owns the account but it has zero active refresh tokens → normal
  path runs, `revokedTokenCount=0`, Redis markers still written (force-logout is a
  blacklist, not only a DB revoke).
- `X-Tenant-Id='*'` from a non-platform operator → admin-service's gate already
  rejects it before reaching auth (BE-467); belt-and-suspenders here treats `'*'`
  as net-zero.

## Failure Scenarios

- CredentialRepository lookup failure → propagates (transactional rollback); the
  operation is not silently treated as confined (fail-closed on infra error is
  acceptable — the admin path audits FAILURE).
- account-service / Redis unavailable → unchanged from today's force-logout paths.
