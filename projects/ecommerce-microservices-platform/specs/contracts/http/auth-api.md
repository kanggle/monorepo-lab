# HTTP Contract: auth-service

> **DEPRECATED** — 2026-05-04 (TASK-BE-132)
>
> This contract is retired. The ecommerce auth-service has been decommissioned
> and replaced by IAM (Global Account Platform) OIDC.
>
> Replacement references:
> - IAM auth contract: `projects/iam-platform/specs/contracts/http/auth-api.md`
> - Integration spec: `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md`
>
> This file is retained for historical reference only.

## Overview
Authentication APIs provided by auth-service (decommissioned).
All endpoints are routed through gateway-service.

---

## POST /api/auth/signup

Register a new user account.

**Auth required:** No

**Request**
```json
{
  "email": "string",
  "password": "string",
  "name": "string"
}
```

**Response 201**
```json
{
  "userId": "string (UUID)",
  "email": "string",
  "name": "string",
  "createdAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 409 | EMAIL_ALREADY_EXISTS | Email already registered |

---

## POST /api/auth/login

Authenticate and issue JWT tokens.

**Auth required:** No

**Request**
```json
{
  "email": "string",
  "password": "string"
}
```

**Response 200**
```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string (opaque)",
  "expiresIn": 3600
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing field |
| 401 | INVALID_CREDENTIALS | Email or password incorrect |
| 429 | RATE_LIMIT_EXCEEDED | Too many login attempts |

---

## POST /api/auth/refresh

Issue a new access token using a valid refresh token.

**Auth required:** No

**Request**
```json
{
  "refreshToken": "string"
}
```

**Response 200**
```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string (opaque UUID, rotated)",
  "expiresIn": 3600
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing field |
| 401 | INVALID_REFRESH_TOKEN | Token not found or expired |
| 401 | REFRESH_TOKEN_REVOKED | Token has been revoked |

---

## POST /api/auth/logout

Revoke the current refresh token.

**Auth required:** Yes (Bearer JWT)

**Request**
```json
{
  "refreshToken": "string"
}
```

**Response 204**

No body.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing field |
| 401 | UNAUTHORIZED | Invalid or missing access token |

---

## POST /api/internal/users/republish-signup-events

Internal operations endpoint. Iterates the `users` table and republishes
`auth.user.signed-up` events for every user. Used to recover `user_profiles`
rows in user-service when consumer failures caused missing profiles.

**Auth required:** No (network-level restriction only)

**Access policy**

- Path prefix `/api/internal/**` is NOT routed by gateway-service — it is
  reachable only inside the cluster (sidecar / ops bastion). External callers
  receive 404 from the gateway.
- Operators call the auth-service pod directly via kubectl port-forward or
  an equivalent internal channel.

**Request**

No body.

**Response 200**
```json
{
  "totalUsers": 152,
  "publishedCount": 150,
  "failedCount": 2
}
```

- `totalUsers`: number of rows iterated from the `users` table
- `publishedCount`: number of events handed to `AuthEventPublisher.publish` without throwing
- `failedCount`: number of users whose publish call threw (best-effort; see Notes)

**Behavior**

- Partial success is allowed. Kafka outage returns 200 with
  `failedCount == totalUsers`, not 5xx.
- Consumers are expected to be idempotent — republishing for users whose
  `user_profiles` already exist is a no-op.
- Inactive users (`active = false`) are included to preserve history.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 500 | INTERNAL_ERROR | Database query for users failed |

**Notes on failure counting**

`AuthEventPublisher.publish` is void and historically swallows broker errors
via the infrastructure adapter. The republish use-case publishes through a
counting wrapper (try/catch around each `publish` call) so that exceptions
surfacing synchronously from the adapter are counted. Asynchronous broker
failures that occur after the adapter returns are counted as `publishedCount`
and surface via metrics, not via this endpoint.

---

## Token Rules

- Access token: JWT, signed with HS256 (HMAC-SHA256, secret key must be at least 32 bytes), TTL 1 hour
- Refresh token: opaque UUID, stored in Redis, TTL 30 days
- On refresh: a new refresh token is issued and the old one is immediately revoked (token rotation). Clients must replace the stored refresh token after every refresh call.
- Token rotation must be atomic: old token revocation and new token creation must happen in a single Redis operation (Lua script or transaction). If atomicity cannot be guaranteed, fail the request rather than leaving both tokens valid.
- Reuse detection: if a previously rotated (already consumed) refresh token is used, the server must reject the request with `REFRESH_TOKEN_REVOKED`. The server should not invalidate the user's current valid session on reuse detection (future enhancement may add this).
- Concurrent refresh: if two refresh requests arrive simultaneously with the same token, only the first to execute succeeds. The second receives `REFRESH_TOKEN_REVOKED`.
- On logout: refresh token is removed from Redis (immediate revocation)
- Expired access tokens must not be accepted by any service

---

## Error Response Format (common)

```json
{
  "code": "string",
  "message": "string",
  "timestamp": "string (ISO 8601)"
}
```

## Notes
- Internal stack traces must not appear in error responses.
- Passwords must be hashed before storage; raw passwords must never be logged or returned.
- Refresh token rotation is mandatory on every refresh call (see Token Rules above).
