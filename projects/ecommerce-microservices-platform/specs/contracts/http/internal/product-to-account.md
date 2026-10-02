# Internal Contract — product-service → account-service (seller-operator provisioning)

> **ADR-MONO-042 / TASK-BE-402** (ADR-MONO-030 Step 4 facet f). product-service calls
> account-service (iam-platform) internal endpoints to provision + deactivate the IAM
> seller-operator account that backs a marketplace seller. **product-service is the
> CALLER**; it does NOT own or modify these endpoints (they pre-exist — TASK-BE-231 /
> ADR-034 U4 / TASK-BE-258). This contract records product-service's *usage*.

## Authentication

GAP `client_credentials` Bearer JWT (`Authorization: Bearer <jwt>`), exchanged per target
tenant by `TenantScopedIamTokenProvider` (ADR-MONO-076 — the `/internal/tenants/{tenantId}/**`
surface authorises `tenant_id == path tenant`, so the base credential is refused there). Every
call below names a tenant in its path (TASK-MONO-737, § 3), so every call carries the
tenant-scoped token. The caller also stamps `X-Tenant-Id` as defense-in-depth (the receiver
re-checks it against the path `{tenantId}`).

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

**The same call as §4** — `PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status` with
`{ "status": "LOCKED", "operatorId": "product-service" }`, the tenant-scoped bearer
(ADR-MONO-076) and `X-Tenant-Id: {tenantId}` (the seller's tenant, the one §1 minted the account
in). An `accountId` that lives in another tenant is `404` and is **not** locked. The recorded
reason is `OPERATOR_PROVISIONING_STATUS_CHANGE` (the EP hardcodes it); consumers of
`account.locked` read the reason for logging only. SUSPEND and CLOSE therefore leave the account
in the same state (`LOCKED`) and differ only on the seller side.
Called only when the seller has a stored `accountId` (null-safe / net-zero otherwise).
Idempotent (`LOCKED → LOCKED` is permitted for that reason).

> **TASK-MONO-737 — why not `POST /internal/accounts/{accountId}/lock`.** SUSPEND used that
> endpoint until 2026-09-29. It names no tenant in its path, and in the deployed topology
> product-service does not reach account-service directly: its base URL is the IAM gateway
> (`infra/demo/demo.env` `ACCOUNT_SERVICE_BASE_URL=http://iam.<domain>`), whose only internal
> route is `/internal/tenants/**`. Every SUSPEND was therefore a gateway **no-route 404**,
> swallowed by the fail-soft stance above — measured live 2026-09-27: seller `SUSPENDED`,
> account still `ACTIVE`. `TASK-MONO-735` had just added `X-Tenant-Id` to that call; the header
> was right about the lookup and irrelevant to the route. No suite saw it because every suite
> calls account-service (or a mock of it) directly — so the rule this contract now carries is
> structural: **every call in this contract names a tenant in its path.**

### 4. Deactivate the backing account on seller CLOSE (D4)

`PATCH /internal/tenants/{tenantId}/accounts/{accountId}/status` —
`TenantProvisioningController#changeStatus`.
Request: `{ "status": "LOCKED", "operatorId": "product-service" }`.
Called only when the seller has a stored `accountId`. Idempotent + fail-soft.

- `status` **MUST** be a valid account-service `AccountStatus` enum constant
  (`ACTIVE`, `LOCKED`, `DORMANT`, `DELETED`). The EP does `AccountStatus.valueOf(status)`, so a
  non-enum value (e.g. the never-valid `DEACTIVATED`) 400s server-side and the fail-soft swallow
  would make CLOSE a cosmetic no-op — the D4-B alternative the ADR REJECTED.
- **`LOCKED`** is the chosen deactivation status: the EP hardcodes reason
  `OPERATOR_PROVISIONING_STATUS_CHANGE`, which `AccountStatusMachine` permits for `ACTIVE→LOCKED`
  (and the idempotent `LOCKED→LOCKED`) but NOT for `→DORMANT` (only `DORMANT_365D` reaches
  DORMANT). `LOCKED` revokes the seller-operator's ability to authenticate (D4: "deactivation is
  real, not just a label"), sets no `deleted_at`, and emits only `account.status.changed` /
  `account.locked` — it does **NOT** fire `account.deleted`, so a seller CLOSE never triggers the
  ADR-037 PII-anonymization deletion cascade (only the separate GDPR delete path emits that).
  `DELETED` is deliberately avoided: it sets `deleted_at` and is the GDPR/deletion lifecycle state
  — destructive and semantically wrong for an operator seller CLOSE.

The minted-account `displayName` (§1) is truncated to **100 chars** caller-side before the mint:
account-service `ProvisionAccountRequest.displayName` is `@Size(max=100)` but product-service
`sellers.display_name` is `VARCHAR(255)`, so a 101–255-char name would 400 the mint and strand the
seller in `PENDING_PROVISIONING`.

### 5. Grant the store `SELLER` site role to an accepting member (TASK-MONO-752 — ADR-MONO-079 D5)

`PATCH /internal/tenants/{tenantId}/accounts/{accountId}/site-roles:grant` — iam
[`consumer-site-roles.md`](../../../../iam-platform/specs/contracts/http/internal/consumer-site-roles.md).
`{tenantId}` = the store tenant of the request (`X-Tenant-Id` of the accept call), `{accountId}` = the
accepting person's pool account (`X-User-Id` = token `sub`). Same tenant-scoped bearer + `X-Tenant-Id` as §1–§4.

Request: `{ "roleName": "SELLER", "expectedEmail": "<the invitation's email>", "operatorId": "product-service" }`.

🔴 **This call is FAIL-CLOSED** — the opposite of §1–§4. It is the authorization step of a person
becoming a seller member; without IAM's answer nobody is linked (`503` to the person, invitation left
`PENDING`). Refusals map 1:1 to the accept endpoint's errors (product-api.md § accept):
`403 SITE_ROLE_EMAIL_MISMATCH` → `SELLER_INVITATION_EMAIL_MISMATCH`;
`409 SITE_ROLE_REQUIRES_POOL_ACCOUNT` / `409 SITE_MEMBERSHIP_REQUIRED` / `404 ACCOUNT_NOT_FOUND` →
`SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE`; anything else (5xx, timeout, other 4xx) → `SERVICE_UNAVAILABLE`.
The email comparison happens in IAM because IAM owns the account email — product-service never
compares the invitation email with a header.

### 6. Revoke the members' `SELLER` site role on seller SUSPEND / CLOSE (TASK-MONO-752)

`PATCH /internal/tenants/{tenantId}/accounts/{accountId}/site-roles:revoke` with
`{ "roleName": "SELLER", "operatorId": "product-service" }`, once per `ACTIVE` member whose role is not
still needed by another `ACTIVE` membership in an `ACTIVE` seller.

**Fail-soft per member, but retryable**: a failed revoke is logged `warn` and the member row stays
`ACTIVE`; only a confirmed revoke (2xx) turns it `REVOKED`. Re-sending SUSPEND / CLOSE retries the
remaining ones (product-api.md § close). 🔴 It is never a lock: the person's account and store
membership are not touched (ADR-MONO-079 D5).

## Net-zero / idempotency invariants

- **null-safe** — a seller with null `accountId` (pre-ADR-042 legacy or still-PENDING)
  transitions state WITHOUT any account-service call.
- **idempotent** — re-onboard / re-provision converges (deterministic email + EP
  idempotency); re-suspend / re-lock is a no-op; a stored non-null `accountId`/`identityId`
  is never overwritten.
- **authz net-zero (D6)** — these calls only make the existing seller-scope claim *backed*
  by a real account; the runtime seller-scope enforcement path is unchanged.

## Reverse projection (IMPLEMENTED — separate event contract)

- Reverse `account.status.changed → LOCKED` → seller-SUSPENDED projection (ADR-042 D4-C) is
  **IMPLEMENTED** by TASK-BE-421. It is an inbound Kafka subscription, not an HTTP call, so it
  lives in the event contract: [`events/account-lifecycle-subscriptions.md` § Reverse
  seller-suspend projection](../../events/account-lifecycle-subscriptions.md). The consumer
  resolves the seller by backing `account_id` and suspends it; it does **NOT** call any endpoint
  in this HTTP contract back (the account is already locked — no loop).

## Deferred follow-ups (not in this contract)

- Async `seller.onboarded` provisioning event (ADR-042 D2-B).
