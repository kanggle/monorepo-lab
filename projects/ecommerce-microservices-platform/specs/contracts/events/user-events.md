# Event Contract: user-service

## Overview
Domain events published by user-service.
Consumers must not depend on fields not defined in this contract.

---

## Topics

Canonical Kafka topic names for events declared in this contract. Topic
names follow the ecommerce convention `<context>.<aggregate>.<event>` —
multi-word event suffixes use hyphens (mirroring `shipping.shipping.status-changed`).

| Event | Kafka topic | Status |
|---|---|---|
| `UserProfileUpdated` | `user.user.profile-updated` | live (no production consumer in v1; declared for platform-console / notification-service future use) |
| `UserWithdrawn` | `user.user.withdrawn` | live (consumed by order-service + auth-service) |

> **Renaming history (TASK-BE-134, 2026-05-11)**: prior to this task the
> publisher used `user.user-profile.updated` and `user.user-withdrawn`
> (hyphen as the separator between aggregate and event), but every
> consumer subscribed to `user.user.withdrawn` (dot). UserWithdrawn events
> were silently lost. This contract now pins the canonical names; the
> publisher was aligned in the same PR. No backwards-compatibility window
> was retained — there were no consumers receiving the broken topic.

---

## Event Envelope (common to all events)
```json
{
  "event_id": "string (UUID)",
  "event_type": "string",
  "occurred_at": "string (ISO 8601)",
  "source": "user-service",
  "tenant_id": "string (owning tenant; default 'ecommerce')",
  "payload": {}
}
```

> **`tenant_id` (ADR-MONO-030 Step 4 / TASK-BE-367, M5).** The outer-axis tenant
> that owns the user the event concerns. Carried on the envelope (not the payload)
> so consumers can route/scope per tenant across the async boundary without parsing
> the payload. Derived from the request's tenant context (gateway `X-Tenant-Id`,
> entitlement-trust gate of TASK-BE-357) at publish time. A standalone deployment or
> a pre-multi-tenant user resolves to the default tenant `'ecommerce'` (net-zero, D8).
> It is always present (never blank). Consumers performing tenant-scoped processing
> must read this field; consumers not yet tenant-aware may ignore it (additive).

---

## UserProfileUpdated

Published when a user updates their profile information.

**Consumers:** platform-console (future), notification-service (future)

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
