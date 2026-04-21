# Event Contract: auth-service

## Overview
Domain events published by auth-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "auth-service",
  "payload": {}
}
```

---

## UserSignedUp

Published when a new user account is successfully registered.

**Consumers:** user-service, notification-service (future)

**Payload**
```json
{
  "userId": "string (UUID)",
  "email": "string",
  "name": "string"
}
```

---

## UserLoggedIn

Published when a user successfully authenticates.

**Consumers:** audit-service (future), analytics (future)

**Payload**
```json
{
  "userId": "string (UUID)",
  "email": "string",
  "ipAddress": "string",
  "userAgent": "string"
}
```

---

## UserLoggedOut

Published when a user explicitly logs out.

**Consumers:** audit-service (future), analytics (future)

**Payload**
```json
{
  "userId": "string (UUID)",
  "sessionId": "string (SHA-256 hex of refresh token)"
}
```

---

## TokenRefreshed

Published when a refresh token is successfully rotated.

**Consumers:** audit-service (future)

**Payload**
```json
{
  "userId": "string (UUID)",
  "sessionId": "string (SHA-256 hex of refresh token)"
}
```

---

## LoginFailed

Published when a login attempt fails (invalid credentials).

**Consumers:** audit-service (future), security-monitoring (future)

**Payload**
```json
{
  "email": "string",
  "ipAddress": "string",
  "reason": "string"
}
```

**reason values:** `INVALID_CREDENTIALS`, `RATE_LIMITED`

---

## SessionLimitExceeded

Published when a user's login causes an older session to be evicted due to concurrent session limits.

**Consumers:** audit-service (future)

**Payload**
```json
{
  "userId": "string (UUID)",
  "evictedSessionId": "string (SHA-256 hex of refresh token)",
  "newSessionId": "string (SHA-256 hex of refresh token)"
}
```

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call auth-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
