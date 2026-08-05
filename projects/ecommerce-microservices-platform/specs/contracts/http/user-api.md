# HTTP Contract: user-service

## Overview
User profile management APIs provided by user-service.
All endpoints are routed through gateway-service and require authentication.

---

## Base Path
`/api/users`

---

## Endpoints

### GET /api/users/me
Get the authenticated user's profile.

**Auth required:** Yes (Bearer JWT)

**Headers**
- `X-User-Id` — injected by gateway

**Response 200**
```json
{
  "userId": "string (UUID)",
  "email": "string",
  "name": "string",
  "nickname": "string | null",
  "phone": "string | null",
  "profileImageUrl": "string | null",
  "status": "ACTIVE",
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 404 | USER_PROFILE_NOT_FOUND | No profile for this user. Not reachable for a gateway-authenticated caller since TASK-BE-575 — see § Profile provisioning |

---

### PATCH /api/users/me
Update the authenticated user's profile.

**Auth required:** Yes (Bearer JWT)

**Headers**
- `X-User-Id` — injected by gateway

**Request Body** (partial update)
```json
{
  "nickname": "string",
  "phone": "string",
  "profileImageUrl": "string"
}
```

**Response 200**
```json
{
  "userId": "string (UUID)",
  "email": "string",
  "name": "string",
  "nickname": "string | null",
  "phone": "string | null",
  "profileImageUrl": "string | null",
  "status": "ACTIVE",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 404 | USER_PROFILE_NOT_FOUND | No profile for this user. Not reachable for a gateway-authenticated caller since TASK-BE-575 — see § Profile provisioning |

---

### GET /api/users/me/addresses
Get the authenticated user's address list.

**Auth required:** Yes (Bearer JWT)

**Response 200**
```json
{
  "addresses": [
    {
      "id": "string (UUID)",
      "label": "string",
      "recipientName": "string",
      "phone": "string",
      "zipCode": "string",
      "address1": "string",
      "address2": "string | null",
      "isDefault": true
    }
  ]
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### POST /api/users/me/addresses
Add a new shipping address.

**Auth required:** Yes (Bearer JWT)

**Request Body**
```json
{
  "label": "string",
  "recipientName": "string",
  "phone": "string",
  "zipCode": "string",
  "address1": "string",
  "address2": "string | null",
  "isDefault": false
}
```

**Response 201**
```json
{
  "id": "string (UUID)"
}
```

> The field is `id`, verified against a live response (TASK-BE-575 AC-5). This document said
> `addressId`; `CreateAddressResponse` has always serialised `id`.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 404 | USER_PROFILE_NOT_FOUND | No profile for this user — see § Profile provisioning. Until TASK-BE-575 this case escaped as a **500** from the `fk_user_addresses_user_id` violation |
| 422 | ADDRESS_LIMIT_EXCEEDED | Maximum number of addresses reached |

---

### PATCH /api/users/me/addresses/{addressId}
Update an existing address.

**Auth required:** Yes (Bearer JWT)

**Request Body** (partial update)
```json
{
  "label": "string",
  "recipientName": "string",
  "phone": "string",
  "zipCode": "string",
  "address1": "string",
  "address2": "string | null",
  "isDefault": true
}
```

**Response 200**
```json
{
  "id": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 404 | ADDRESS_NOT_FOUND | Address with given ID does not exist |

---

### DELETE /api/users/me/addresses/{addressId}
Delete a shipping address.

**Auth required:** Yes (Bearer JWT)

**Response 204**

No body.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 404 | ADDRESS_NOT_FOUND | Address with given ID does not exist |
| 422 | DEFAULT_ADDRESS_CANNOT_BE_DELETED | Cannot delete the default address while other addresses exist |

---

### Account withdrawal — owned by IAM (no ecommerce HTTP endpoint)

There is **no consumer-facing ecommerce withdrawal endpoint.** Post-IAM (TASK-BE-132), account lifecycle — including withdrawal / deletion — is owned by the **IAM identity authority** (`iam-platform` account-service), which runs the withdrawal / GDPR-delete flow and emits the `account.deleted` domain event. A "delete my account" action belongs at IAM (reached via the gateway), not as a direct ecommerce profile-mutation endpoint that would bypass the identity authority.

**ecommerce reaction (wired — TASK-BE-388):** the profile-withdrawal machinery in user-service — `UserProfileService.withdrawProfile()` transitions the profile to `WITHDRAWN` and publishes the ecommerce `UserWithdrawn` event (consumed by order-service, etc.). IAM's `account.deleted` is consumed by `AccountDeletedConsumer` (`@KafkaListener(topics = "account.deleted", groupId = "user-service")`, `@Profile("!standalone")`), which invokes `withdrawProfile()` / `anonymizeProfile()`. The cross-project deletion wiring is live (see [`account-lifecycle-subscriptions.md`](../events/account-lifecycle-subscriptions.md)).

---

### GET /api/admin/users
List user profiles with filtering and pagination. Requires admin role.

**Auth required:** Yes (Bearer JWT, admin role)

**Query Parameters**
- `status` (optional) — filter by status: `ACTIVE`, `SUSPENDED`, `WITHDRAWN`
- `email` (optional) — filter by email (partial match)
- `page` (default: 0) — page number
- `size` (default: 20) — page size

**Response 200**
```json
{
  "content": [
    {
      "userId": "string (UUID)",
      "email": "string",
      "name": "string",
      "nickname": "string | null",
      "status": "ACTIVE",
      "createdAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Admin role required |

---

### GET /api/admin/users/{userId}
Get a specific user's profile. Requires admin role.

**Auth required:** Yes (Bearer JWT, admin role)

**Response 200**
```json
{
  "userId": "string (UUID)",
  "email": "string",
  "name": "string",
  "nickname": "string | null",
  "phone": "string | null",
  "profileImageUrl": "string | null",
  "status": "ACTIVE",
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Admin role required |
| 404 | USER_PROFILE_NOT_FOUND | User profile does not exist |

---

## Error Response Format
```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

## Profile provisioning (TASK-BE-575)

The profile is the ecommerce projection of an IAM identity; the caller never creates it, and
there is no `POST /api/users`. Two sources create it, and both produce the same row:

| Source | When | Notes |
|---|---|---|
| IAM `account.created` consumer | onboarding, asynchronously | ADR-MONO-037 P1. **Dead in the per-project compose topology** — IAM publishes to its own Kafka cluster and this consumer subscribes to ecommerce's (measured: TASK-BE-575 AC-0, tracked as TASK-MONO-511) |
| Request-time pull-through | first `/api/**` request carrying a gateway-verified `X-User-Id` | The fallback the IAM consumer-integration guide names for an unavailable event stream. Idempotent; the profile lands in the request's `X-Tenant-Id` |

Consequently `404 USER_PROFILE_NOT_FOUND` is **not** reachable on the self-service endpoints for
a caller the gateway has authenticated — it remains the documented answer for any other caller,
and for the case where a profile exists for this `user_id` under a different tenant
(`uq_user_profiles_user_id` is global while reads are tenant-scoped).

`/api/admin/**` is excluded from pull-through: an operator's subject must not become a consumer
profile in the list they are reading.

## Notes
- User ID is the IAM `accountId`. **`email` arrives at provisioning time when the token carries it; `name` never does.** The `account.created` payload is emailHash-only, so the event path still provisions both as null. On the pull-through path the gateway maps `X-User-Email` with `skipIfNull(JwtClaims::email)`: until 2026-08-06 the SAS access token carried no `email` claim at all, so the header was absent and every profile was born with a null email (measured, TASK-BE-575) — TASK-BE-577 mints the claim on identity-bearing grants that were granted the `email` scope, and a first request through the gateway now provisions the email (measured 2026-08-06). A token without that scope, or a profile born from the event, still yields null. **Nothing may depend on either field being non-null** (ADR-MONO-037 P5/P6) — that has not changed, and `name` in particular has no source: no claim carries a display name and no gateway maps `X-User-Name`.
- user-service must not expose or modify authentication credentials.
- All profile endpoints use `X-User-Id` header injected by gateway for identity.
- Maximum 10 addresses per user.
