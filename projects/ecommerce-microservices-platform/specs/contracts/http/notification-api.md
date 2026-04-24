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
      "createdAt": "string (ISO 8601)"
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
| `WELCOME` | UserSignedUp |

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
