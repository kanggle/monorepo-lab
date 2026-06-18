# Internal Contract — product-service → account-service (seller-operator provisioning)

> **ADR-MONO-042 / TASK-BE-402** (ADR-MONO-030 Step 4 facet f). product-service calls
> account-service (iam-platform) internal endpoints to provision + deactivate the IAM
> seller-operator account that backs a marketplace seller. **product-service is the
> CALLER**; it does NOT own or modify these endpoints (they pre-exist — TASK-BE-231 /
> ADR-034 U4 / TASK-BE-258). This contract records product-service's *usage*.

## Authentication

GAP `client_credentials` Bearer JWT (`Authorization: Bearer <jwt>`), obtained + cached by
product-service `IamClientCredentialsTokenProvider` (mirrors admin-service, ADR-005 단계 3b).
The account `/internal/**` chain dual-allows JWT or `X-Internal-Token` (BE-317). The
caller also stamps `X-Tenant-Id` as defense-in-depth (the receiver re-checks it against
the path `{tenantId}`).

## Availability stance — FAIL-SOFT (ADR-042 D3)

**Onboarding never blocks on account-service availability.** Every call below is wrapped
fail-soft: a 4xx/5xx/timeout/connection failure is swallowed (logged `warn`), the seller
stays `PENDING_PROVISIONING`, and the call is retryable (`POST /api/admin/sellers/{id}/provision`).
Deactivation calls are likewise fail-soft (the seller's domain transition still applies).

## Endpoints used

### 1. Mint the seller-operator account (D2)

`POST /internal/tenants/{tenantId}/accounts` — `TenantProvisioningController` →
`ProvisionAccountUseCase`.

Request:
```json
{
  "email": "seller+<tenantId>+<sellerId>@marketplace.local",
  "password": "<random-strong>",
  "displayName": "<seller display name>",
  "roles": ["SELLER"],
  "operatorId": "product-service"
}
```
- `email` is **deterministic** on `(tenantId, sellerId)` so a re-onboard converges on the
  same account/identity at account-service (idempotency, ADR-036 `uk_*` race-safe).
- `roles: ["SELLER"]` — the seller-scoped operator role. Accepted by `AccountRoleName`
  (`^[A-Z][A-Z0-9_]*$` ≤64, no allowlist). It yields the existing ADR-025 axis-2
  `X-Seller-Scope` claim at runtime (D6 net-zero) — **no authz-model change**.
- `password` is random/strong: the seller-operator never authenticates by password (it
  operates via the assume-tenant seller-scope claim); the field only satisfies validation.

Response `201`:
```json
{ "accountId": "...", "tenantId": "...", "email": "...", "status": "ACTIVE", "roles": ["SELLER"], "createdAt": "..." }
```
product-service stores `accountId` on the seller and transitions `PENDING_PROVISIONING → ACTIVE`.

### 2. Born-unified central identity (D5)

`POST /internal/tenants/{tenantId}/identities:resolveOrCreate` —
`ResolveOrCreateIdentityController`.

Request: `{ "email": "<same deterministic email>", "reuseExisting": true }`
Response `200`: `{ "identityId": "...", "outcome": "CREATED|REUSED|EXISTS_NOT_REUSED" }`

`reuseExisting=true` converges a same-email consumer + seller-operator onto **one** central
identity (ADR-036 born-unified, same-origin issuance — NOT email auto-merge). **Best-effort**:
a failed identity mint leaves `identity_id` null (the account already makes the seller
operable); filled on re-provision.

### 3. Lock the backing account on seller SUSPEND (D4)

`POST /internal/accounts/{accountId}/lock` — `AccountLockController`.
Request: `{ "reason": "ADMIN_LOCK", "operatorId": "product-service" }` + `Idempotency-Key`.
Called only when the seller has a stored `accountId` (null-safe / net-zero otherwise).
Idempotent (re-locking an already-locked account is a no-op at the EP).

### 4. Deactivate the backing account on seller CLOSE (D4)

`PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status` —
`TenantProvisioningController#changeStatus`.
Request: `{ "status": "DEACTIVATED", "operatorId": "product-service" }`.
Called only when the seller has a stored `accountId`. Idempotent + fail-soft.

## Net-zero / idempotency invariants

- **null-safe** — a seller with null `accountId` (pre-ADR-042 legacy or still-PENDING)
  transitions state WITHOUT any account-service call.
- **idempotent** — re-onboard / re-provision converges (deterministic email + EP
  idempotency); re-suspend / re-lock is a no-op; a stored non-null `accountId`/`identityId`
  is never overwritten.
- **authz net-zero (D6)** — these calls only make the existing seller-scope claim *backed*
  by a real account; the runtime seller-scope enforcement path is unchanged.

## Deferred follow-ups (not in this contract)

- Reverse `account.status.changed` → seller-SUSPENDED projection (ADR-042 D4-C).
- Async `seller.onboarded` provisioning event (ADR-042 D2-B).
