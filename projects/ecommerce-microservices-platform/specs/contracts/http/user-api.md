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
| 404 | USER_PROFILE_NOT_FOUND | Profile not yet created |

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
| 404 | USER_PROFILE_NOT_FOUND | Profile not yet created |

---

### GET /api/users/me/addresses
Get the authenticated user's address list.

**Auth required:** Yes (Bearer JWT)

**Response 200**
```json
{
  "addresses": [
    {
      "addressId": "string (UUID)",
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
  "addressId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
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
  "addressId": "string (UUID)"
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
  "totalElements": 100
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

## Notes
- User ID is the IAM `accountId`, sourced via the IAM `account.created` event (ADR-MONO-037). **Email/name are NOT in that event** (it is emailHash-only) — the profile is created minimal and email/name are populated later from the OIDC token / profile-update; both may be null on a freshly-onboarded profile.
- user-service must not expose or modify authentication credentials.
- All profile endpoints use `X-User-Id` header injected by gateway for identity.
- Maximum 10 addresses per user.
