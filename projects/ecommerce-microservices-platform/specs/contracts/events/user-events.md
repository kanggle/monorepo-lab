# Event Contract: user-service

## Overview
Domain events published by user-service.
Consumers must not depend on fields not defined in this contract.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "user-service",
  "payload": {}
}
```

---

## UserProfileUpdated

Published when a user updates their profile information.

**Consumers:** admin-dashboard (future), notification-service (future)

**Payload**
```json
{
  "userId": "string (UUID)",
  "nickname": "string | null",
  "phone": "string | null",
  "profileImageUrl": "string | null",
  "updatedAt": "string (ISO 8601)"
}
```

---

## UserWithdrawn

Published when a user withdraws (deactivates) their account.

**Consumers:** order-service, auth-service

**Payload**
```json
{
  "userId": "string (UUID)",
  "withdrawnAt": "string (ISO 8601)"
}
```

---

## Consumer Rules

- Consumers must handle duplicate events idempotently.
- Consumers must not fail the entire pipeline on a single malformed event — route to DLQ instead.
- Consumers must not call user-service HTTP API to compensate for missing event data.
- Fields not listed in this contract must not be relied upon.
