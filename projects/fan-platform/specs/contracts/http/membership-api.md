# membership-api (membership-service HTTP contract)

> Spec authored by **TASK-FAN-BE-008**. Implementation = **TASK-FAN-BE-009**.
>
> **Public** endpoints require an `Authorization: Bearer <RS256 JWT>` issued by IAM
> with `tenant_id ∈ { fan-platform, * }`. Tokens with any other tenant value get
> 403 `TENANT_FORBIDDEN`.
>
> Public requests are routed through the fan-platform gateway under the prefix
> `/api/v1/memberships/**`; the gateway forwards to the service which serves
> `/api/fan/memberships/**`. Path examples below use the service-internal path.
>
> The **internal** endpoint `/internal/membership/access` is NOT gateway-routed —
> it is reachable only on the internal docker network and is authenticated by a
> IAM `client_credentials` workload-identity JWT (ADR-MONO-005), NOT an end-user
> token.

## Envelope shapes

### Success
```json
{
  "data": { ... },
  "meta": {
    "timestamp": "2026-06-06T00:00:00Z"
  }
}
```

### Error (matches `platform/error-handling.md` flat shape)
```json
{
  "code": "MEMBERSHIP_NOT_FOUND",
  "message": "Membership not found: 0190f3e2-...",
  "details": { "...": "..." },
  "timestamp": "2026-06-06T00:00:00Z"
}
```

### Common error codes

| HTTP | code | When |
|---|---|---|
| 400 | VALIDATION_ERROR | malformed JSON / type mismatch / missing `Idempotency-Key` on subscribe |
| 401 | UNAUTHORIZED | missing / expired / invalid signature (public); end-user-only on `/internal/**` |
| 403 | TENANT_FORBIDDEN | `tenant_id` claim does not match `fan-platform` (and is not `*`) |
| 403 | FORBIDDEN | `/internal/**` called with a non-workload-identity token |
| 404 | MEMBERSHIP_NOT_FOUND | missing OR cross-tenant OR cross-account; existence not leaked |
| 409 | IDEMPOTENCY_KEY_CONFLICT | `Idempotency-Key` reused with a different payload |
| 409 | CONFLICT | optimistic-lock collision |
| 422 | PAYMENT_DECLINED | PG declined / verification failed (mock: `tok_decline`; portone: status ≠ PAID or amount mismatch); no membership created |
| 422 | MEMBERSHIP_TIER_INVALID | `tier` not in `{ MEMBERS_ONLY, PREMIUM }` |
| 422 | MEMBERSHIP_NOT_RENEWABLE | renew called on a CANCELED membership (deliberate opt-out — subscribe fresh instead) |
| 422 | VALIDATION_ERROR | constraint violation (`@Valid`, e.g. `planMonths < 1`) |

---

## Memberships (public)

### `POST /api/fan/memberships` — Subscribe

Auth: any authenticated fan (`accountId` = `sub`). **`Idempotency-Key` header is
required** (transactional.md T1). Payment runs synchronously through
`PaymentGatewayPort`; on approval a membership is created directly in `ACTIVE`, on
decline NO row is created and the API returns 422 `PAYMENT_DECLINED`.

Headers:
```
Idempotency-Key: <client-generated opaque key, ≤ 80 chars>   # REQUIRED
```

Request:
```json
{
  "tier": "MEMBERS_ONLY | PREMIUM",
  "planMonths": 1,
  "paymentId": "<PG payment reference; optional>"
}
```

**`paymentId` semantics are profile-dependent** (see architecture.md § PG Boundary,
[ADR-001](../../../docs/adr/ADR-001-real-pg-portone-verification-boundary.md)):

- **`portone` profile (real PG):** `paymentId` is the PortOne payment reference the
  browser SDK returned after the payment window completed. The backend **verifies**
  it server-side (`status == PAID` AND paid amount == charged amount) before
  creating the membership — the client's success signal alone is never trusted. A
  verification failure → 422 `PAYMENT_DECLINED`, no row.
- **default mock profile (`!portone`, CI/dev):** `paymentId` is an opaque token; the
  reserved sentinel `tok_decline` forces a 422 `PAYMENT_DECLINED`, any other value
  (incl. omitted) approves.

> **Compatibility note (Phase 0):** this contract change (`paymentToken` → `paymentId`)
> is documented ahead of implementation per the repo's contract-first policy. The
> field-name/verification switch lands atomically with the PortOne adapter
> (TASK-FAN-BE-031 / TASK-FAN-FE-010) so `main` never carries a half-migrated payment
> path.

Response 201:
```json
{
  "data": {
    "membershipId": "0190f3e2-...",
    "tenantId": "fan-platform",
    "accountId": "uuid",
    "tier": "PREMIUM",
    "status": "ACTIVE",
    "validFrom": "2026-06-06T00:00:00Z",
    "validTo": "2026-07-06T00:00:00Z",
    "planMonths": 1,
    "paymentRef": "pgmock_0190f3e2-... (mock) | <verified paymentId> (portone)",
    "createdAt": "2026-06-06T00:00:00Z",
    "canceledAt": null
  },
  "meta": { "timestamp": "..." }
}
```

Idempotent replay: a repeat with the same `(accountId, Idempotency-Key)` and an
identical payload returns the same membership (the first result) — 201/200 with the
same `membershipId` and `paymentRef`, no new row, no re-authorization.

Errors: 400 (missing `Idempotency-Key`), 401, 403 (TENANT_FORBIDDEN), 409
(IDEMPOTENCY_KEY_CONFLICT — same key, different payload), 422 (PAYMENT_DECLINED /
MEMBERSHIP_TIER_INVALID / VALIDATION_ERROR for `planMonths < 1`).

### `POST /api/fan/memberships/{id}/cancel` — Cancel

Auth: the membership owner (`accountId` = `sub`). `ACTIVE → CANCELED` (terminal).
Cancel of an already-`CANCELED` membership is an **idempotent no-op** — returns the
membership unchanged (NOT an error).

Request:
```json
{ "reason": "string (optional, ≤ 200)" }
```

Response 200:
```json
{
  "data": {
    "membershipId": "0190f3e2-...",
    "tenantId": "fan-platform",
    "accountId": "uuid",
    "tier": "PREMIUM",
    "status": "CANCELED",
    "validFrom": "2026-06-06T00:00:00Z",
    "validTo": "2026-07-06T00:00:00Z",
    "planMonths": 1,
    "paymentRef": "pgmock_0190f3e2-... (mock) | <verified paymentId> (portone)",
    "createdAt": "2026-06-06T00:00:00Z",
    "canceledAt": "2026-06-10T00:00:00Z"
  },
  "meta": { "timestamp": "..." }
}
```

Errors: 401, 403, 404 (MEMBERSHIP_NOT_FOUND — unknown id / cross-account /
cross-tenant), 409 (CONFLICT — optimistic-lock race).

### `POST /api/fan/memberships/{id}/renew` — Renew (seamless re-activation)

Auth: the membership owner (`accountId` = `sub`). **`Idempotency-Key` header is
required** (same as subscribe). Creates a **new** membership continuing the prior
one's `tier` with a **seamless window**:

- `validFrom = max(now, prior.validTo)` — renewing **early** stacks onto the end of
  the current window (no lost days); renewing **after expiry** starts from `now`.
- `validTo = validFrom + planMonths·30d`.

The prior membership row is **never mutated**. The renewed membership is a fresh
activation and emits `fan.membership.activated.v1` (→ a WELCOME notification); there
is no distinct `renewed.v1` event in this increment. A **CANCELED** prior cannot be
renewed (422 `MEMBERSHIP_NOT_RENEWABLE`) — subscribe fresh instead.

Headers:
```
Idempotency-Key: <client-generated opaque key, ≤ 80 chars>   # REQUIRED
```

Request (the `tier` is inherited from the prior membership — not in the body):
```json
{
  "planMonths": 1,
  "paymentToken": "tok_visa_demo (optional; mock — 'tok_decline' forces a decline)"
}
```

Response 201: the full membership payload (same shape as Subscribe) for the **new**
membership — `status: "ACTIVE"`, the seamless `validFrom`/`validTo`, a fresh
`membershipId` and `paymentRef`.

Idempotent replay: a repeat with the same `(accountId, Idempotency-Key)` + identical
payload returns the same renewed membership (no new row, no re-authorization).

Errors: 400 (missing `Idempotency-Key`), 401, 403 (TENANT_FORBIDDEN), 404
(MEMBERSHIP_NOT_FOUND — unknown id / cross-account / cross-tenant), 409
(IDEMPOTENCY_KEY_CONFLICT), 422 (PAYMENT_DECLINED / MEMBERSHIP_NOT_RENEWABLE —
CANCELED prior / VALIDATION_ERROR for `planMonths < 1`).

### `GET /api/fan/memberships` — List (the caller's memberships)

Auth: bearer. Returns memberships where `accountId == sub` and
`tenantId == token tenant`. Newest window first.

Response 200:
```json
{
  "data": {
    "content": [
      {
        "membershipId": "...",
        "tier": "PREMIUM",
        "status": "ACTIVE",
        "validFrom": "...",
        "validTo": "...",
        "planMonths": 1,
        "active": true,
        "createdAt": "...",
        "canceledAt": null
      }
    ]
  },
  "meta": { "timestamp": "..." }
}
```

`active` is the **read-time** evaluation (`status == ACTIVE && now ∈ [validFrom,
validTo]`) — a stored-`ACTIVE` row past its window reads `active=false`.

Errors: 401, 403.

### `GET /api/fan/memberships/{id}` — Detail

Auth: bearer; the membership must belong to the caller and tenant.

Response 200: same shape as the cancel response (full membership payload, including
read-time `active`).

Errors: 401, 403, 404 (MEMBERSHIP_NOT_FOUND — missing / cross-account /
cross-tenant; existence not leaked).

---

## Internal access-check (workload identity — NOT gateway-routed)

### `GET /internal/membership/access?accountId={accountId}&tier={tier}&tenantId={tenantId}`

The **remote counterpart** of community-service's port
`MembershipChecker.hasAccess(String accountId, String tier, String tenantId) → boolean`.
Called by the FAN-BE-010 `HttpMembershipChecker` adapter.

Auth: **IAM `client_credentials` workload-identity JWT** (ADR-MONO-005). NOT an
end-user access token. The internal security chain validates issuer + signature +
a recognized internal client identity; an end-user token → 403 `FORBIDDEN`, no
token → 401.

Query parameters (1:1 with the port signature):

| Param | Required | Maps to port param | Meaning |
|---|---|---|---|
| `accountId` | YES | `accountId` (caller's `sub`) | the fan whose access is being checked |
| `tier` | YES | `tier` (`MEMBERS_ONLY`\|`PREMIUM`) | the **required** tier of the gated content |
| `tenantId` | YES | `tenantId` | tenant scope |

Response 200:
```json
{ "allowed": true }
```

`allowed` maps **1:1** to the port's `boolean` return value. `allowed=true` iff a
membership for `(accountId, tenantId)` is `status=ACTIVE` AND `now ∈ [validFrom,
validTo]` AND `tierGrants(membership.tier, tier)` (PREMIUM ⊇ MEMBERS_ONLY). See
`specs/services/membership-service/architecture.md` § Access Semantics.

**Fail-closed.** Any infrastructure error (DB unavailable, query failure) returns
`{ "allowed": false }` — never `true` on error. The calling adapter is ALSO
fail-closed: a timeout / non-2xx / malformed body → `false`. A missing membership,
expired window, canceled membership, insufficient tier, or cross-tenant/cross-account
lookup all return `{ "allowed": false }` (deny), never leaked as a different status.

> **1:1 mapping confirmation.** The three query parameters (`accountId`, `tier`,
> `tenantId`) correspond exactly, in name and meaning, to the three parameters of
> `MembershipChecker.hasAccess(accountId, tier, tenantId)`, and the boolean field
> `allowed` corresponds exactly to its boolean return value, including the
> fail-closed (deny-on-error) contract. This is the AC-2 / AC-7 guarantee that the
> FAN-BE-010 adapter swap is a drop-in replacement for the v1
> `AlwaysAllowMembershipChecker`.

Errors: 401 (no token), 403 (non-workload-identity token), 400 (missing required
param). Note: a domain "deny" is NOT an error — it returns 200 with
`allowed=false`.

---

## Health / metrics

| Path | Auth | Exposure | Response |
|---|---|---|---|
| `GET /actuator/health` | none | gateway-routed (`/actuator/health`) | 200 (composite of DB/Kafka) |
| `GET /actuator/info` | none | gateway-routed (`/actuator/info`) | 200 |
| `GET /actuator/prometheus` | none | **internal docker network only — NOT gateway-routed** | text/plain Prometheus format |

`/actuator/prometheus` is scraped by Prometheus directly within the
`fan-platform-net` docker network
(`http://membership-service:8080/actuator/prometheus`), never exposed through the
external gateway — same network-isolation approach as community-service
(TASK-FAN-BE-004 option c).

---

## Versioning

This is `v1`. The HTTP path is unversioned — the gateway maps
`/api/v1/memberships/**` to membership-service's `/api/fan/memberships/**`.
Breaking changes will branch the controller path (`/api/fan/memberships/v2/...`)
AND bump the event topic suffix (`fan.membership.*.v2`). The `/internal/**`
surface is versioned only by its query contract; a breaking change would add a new
path.
