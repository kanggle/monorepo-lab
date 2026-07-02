# HTTP Contract: notification-service

## Overview
Published HTTP API for notification-service.
All endpoints are accessible through gateway-service only.
Admin endpoints require an authenticated admin user (Bearer token with admin role).
User endpoints require an authenticated user (Bearer token).

---

## Base Path
`/api/notifications`

---

## Endpoints

### GET /api/notifications/me
List notifications for the authenticated user.

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size

**Response 200**
```json
{
  "content": [
    {
      "notificationId": "string (UUID)",
      "channel": "EMAIL",
      "subject": "string",
      "status": "SENT",
      "sentAt": "string (ISO 8601)",
      "createdAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 10
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### GET /api/notifications/me/{notificationId}
Get notification detail for the authenticated user.

**Response 200**
```json
{
  "notificationId": "string (UUID)",
  "channel": "EMAIL",
  "subject": "string",
  "body": "string",
  "status": "SENT",
  "sentAt": "string (ISO 8601)",
  "createdAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not the notification recipient |
| 404 | NOTIFICATION_NOT_FOUND | Notification does not exist |

---

### GET /api/notifications/me/preferences
Get notification preferences for the authenticated user.

**Response 200**
```json
{
  "userId": "string (UUID)",
  "emailEnabled": true,
  "smsEnabled": false,
  "pushEnabled": true
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### PUT /api/notifications/me/preferences
Update notification preferences for the authenticated user.

**Request Body**
```json
{
  "emailEnabled": true,
  "smsEnabled": false,
  "pushEnabled": true
}
```

**Response 200**
```json
{
  "userId": "string (UUID)",
  "emailEnabled": true,
  "smsEnabled": false,
  "pushEnabled": true
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### GET /api/notifications/vapid-public-key
Return the server's VAPID public key so a browser client can create a Web Push
subscription (`pushManager.subscribe({ applicationServerKey })`). The VAPID
**public** key is not a secret; this endpoint is unauthenticated and lets the
frontend stay independent of build-time config and survive key rotation.

> Alternative (documented, not chosen): bake the key into the frontend at build
> time via `NEXT_PUBLIC_VAPID_PUBLIC_KEY`. The endpoint approach is the contract
> here so the backend keypair is the single source of truth.

**Response 200**
```json
{
  "publicKey": "string (base64url-encoded VAPID application server key)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 503 | PUSH_NOT_CONFIGURED | Server has no VAPID keypair configured (push disabled) |

---

### POST /api/notifications/me/push-subscriptions
Register (or refresh) a Web Push subscription for the authenticated user. The
body is the W3C `PushSubscription` serialization produced by the browser. The
operation is **idempotent on `endpoint`** — re-posting the same endpoint with
rotated keys updates the stored keys rather than creating a duplicate. A user
may hold multiple subscriptions (one per browser/device).

**Request Body**
```json
{
  "endpoint": "string (push service URL, unique per browser subscription)",
  "expirationTime": "number | null (epoch millis, optional)",
  "keys": {
    "p256dh": "string (base64url client public key)",
    "auth": "string (base64url auth secret)"
  }
}
```

**Response 201** (new subscription) / **200** (existing endpoint refreshed)
```json
{
  "subscriptionId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing/invalid `endpoint` or `keys` |
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### DELETE /api/notifications/me/push-subscriptions
Remove one of the authenticated user's Web Push subscriptions (opt-out or
browser `unsubscribe()`). The subscription is identified by its `endpoint`.
Idempotent — deleting an already-absent endpoint returns 204.

**Request Body**
```json
{
  "endpoint": "string (the subscription endpoint to remove)"
}
```

**Response 204** — no body.

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing `endpoint` |
| 401 | UNAUTHORIZED | Missing or invalid access token |

---

### GET /api/notifications/templates (Admin)
List notification templates.

**Query Parameters**
- `page` (default: 0) — page number
- `size` (default: 20) — page size

**Response 200**
```json
{
  "content": [
    {
      "templateId": "string (UUID)",
      "type": "ORDER_PLACED",
      "channel": "EMAIL",
      "subject": "string",
      "createdAt": "string (ISO 8601)",
      "updatedAt": "string (ISO 8601)"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 5
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |

---

### GET /api/notifications/templates/{templateId} (Admin)
Get a single notification template by ID.

**Authorization**: `X-User-Role: ADMIN` header required.

**Response 200**
```json
{
  "templateId": "string (UUID)",
  "type": "ORDER_PLACED",
  "channel": "EMAIL",
  "subject": "string",
  "body": "string",
  "createdAt": "string (ISO 8601)",
  "updatedAt": "string (ISO 8601)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |
| 404 | TEMPLATE_NOT_FOUND | Template does not exist |

---

### POST /api/notifications/templates (Admin)
Create a notification template.

**Request Body**
```json
{
  "type": "ORDER_PLACED",
  "channel": "EMAIL",
  "subject": "string (supports {{variable}} placeholders)",
  "body": "string (supports {{variable}} placeholders)"
}
```

**Response 201**
```json
{
  "templateId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |
| 409 | TEMPLATE_ALREADY_EXISTS | Template for this type and channel already exists |

---

### PUT /api/notifications/templates/{templateId} (Admin)
Update a notification template.

**Request Body**
```json
{
  "subject": "string",
  "body": "string"
}
```

**Response 200**
```json
{
  "templateId": "string (UUID)"
}
```

**Error responses**
| Status | Code | Reason |
|---|---|---|
| 400 | VALIDATION_ERROR | Missing or invalid field |
| 401 | UNAUTHORIZED | Missing or invalid access token |
| 403 | ACCESS_DENIED | Not an admin user |
| 404 | TEMPLATE_NOT_FOUND | Template does not exist |

---

## Notification Status Values

| Status | Description |
|---|---|
| `PENDING` | Notification queued for delivery |
| `SENT` | Notification successfully delivered |
| `FAILED` | Notification delivery failed |

## Notification Channel Values

| Channel | Description |
|---|---|
| `EMAIL` | Email notification |
| `SMS` | SMS notification |
| `PUSH` | Push notification |

## Template Type Values

| Type | Trigger Event |
|---|---|
| `ORDER_PLACED` | OrderPlaced |
| `PAYMENT_COMPLETED` | PaymentCompleted |
| `SHIPPING_STATUS_CHANGED` | ShippingStatusChanged |
| `WELCOME` | IAM `account.created` (no PII personalization — emailHash-only; ADR-MONO-037) |

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
- `userId` is extracted from the authentication token, not from the request body.
- Admin endpoints require admin role via `X-User-Role` header forwarded by gateway.
- Notification sending is triggered by event consumption, not by HTTP API calls.
- Internal stack traces must not appear in error responses.
- **Web Push (`PUSH` channel)**: actual delivery requires the user to hold at least one
  active push subscription (registered via `POST /api/notifications/me/push-subscriptions`)
  **and** `pushEnabled = true`. With no subscription the `PUSH` channel is a silent no-op
  (email/SMS still deliver). Subscriptions that the push service reports as gone (`404`/`410`
  at delivery time) are lazily pruned server-side; the client should re-subscribe and
  re-register. VAPID keypair is server-owned config — see `GET /api/notifications/vapid-public-key`.
