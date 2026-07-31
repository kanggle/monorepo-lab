# notification-api (notification-service HTTP contract)

> Spec created by **TASK-FAN-BE-043** (ADR-MONO-058 § D3 pagination-carrier adoption) —
> this file did not previously exist (only `artist-api.md`, `community-api.md`,
> `membership-api.md` were present under this directory); it documents the
> **secondary `rest-api` inbox surface** of `notification-service`
> (`projects/fan-platform/specs/services/notification-service/architecture.md`
> § Inbox Read API). The service's primary role is `event-consumer` (Kafka) —
> that side has no HTTP contract.
>
> All endpoints require an `Authorization: Bearer <RS256 JWT>` issued by IAM
> with `tenant_id ∈ { fan-platform, * }`. Tokens with any other tenant value
> get 403 `TENANT_FORBIDDEN`.
>
> All requests are routed through the fan-platform gateway under the prefix
> `/api/v1/notifications/**`; the gateway forwards to the service which serves
> `/api/fan/notifications/**`. Path examples below use the service-internal path.
>
> This surface also conforms to the domain-agnostic
> [`platform/contracts/notification-inbox-contract.md`](../../../../../platform/contracts/notification-inbox-contract.md)
> (ADR-MONO-043 § D3) for the **item shape** (§ 1) and the **verb/paging/read
> semantics** (§ 2). Per that contract § 2.2, "the wrapper shape is domain-owned"
> — the `meta` block's exact fields (this file's normative content below) are
> **not** dictated by the cross-domain contract.

## Envelope shapes

### Success (single item)
```json
{
  "data": { ... },
  "meta": {
    "timestamp": "2026-06-11T08:00:00Z"
  }
}
```

### Success (paginated list)
```json
{
  "data": [ { ... }, { ... } ],
  "meta": {
    "timestamp": "2026-06-11T08:00:00Z",
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3
  }
}
```

`totalPages` was added by **TASK-FAN-BE-043** (ADR-MONO-058 § D3 — pagination-carrier
adoption). It is an **additive** `meta` field: existing fields (`timestamp`, `page`,
`size`, `totalElements`) and every `data` item field are unchanged. Prior to this
task the domain type backing this endpoint (`NotificationPage`) carried no
`totalPages`, and `ApiEnvelope.ofList(...)` had no parameter for it — this was the
exact "some hand-rolled shapes omit `totalPages`" divergence `ADR-MONO-058 § D3`
names by example. See `platform/error-handling.md` § permitted-to-extend precedent
(the same class of backward-compatible addition `TASK-FAN-BE-038` established for
this project).

### Error (matches `platform/error-handling.md` flat shape)
```json
{
  "code": "NOTIFICATION_NOT_FOUND",
  "message": "Notification not found: 0190f3e2-...",
  "timestamp": "2026-06-11T08:00:00Z"
}
```

### Common error codes

| HTTP | code | When |
|---|---|---|
| 400 | VALIDATION_ERROR | `page < 0`, `size` outside `1..100`, or an invalid `status` value |
| 401 | UNAUTHORIZED | missing / expired / invalid signature |
| 403 | TENANT_FORBIDDEN | `tenant_id` claim does not match `fan-platform` (and is not `*`) |
| 404 | NOTIFICATION_NOT_FOUND | missing OR cross-account OR cross-tenant; existence not leaked |
| 409 | CONFLICT | optimistic-lock collision on concurrent mark-read |

---

## Notifications (inbox)

### `GET /api/fan/notifications` — List (paginated inbox)

Auth: any authenticated fan (`sub` = recipient `accountId`, tenant-scoped). Always
returns only the caller's own notifications.

Query parameters:

| Param | Type | Default | Notes |
|---|---|---|---|
| `unread` | boolean | absent = all | normative filter (`notification-inbox-contract.md` § 2.1); `true` → unread only, `false` → read only |
| `status` | `UNREAD \| READ` | absent | back-compat alias, applied only when `unread` is absent (ADR-MONO-043 P2 / `TASK-FAN-BE-023`) |
| `page` | int ≥ 0 | `0` | |
| `size` | int 1–100 | `20` | |

Response 200:
```json
{
  "data": [
    {
      "id": "0190f3e2-...",
      "sourceDomain": "fan",
      "type": "WELCOME",
      "title": "Welcome to PREMIUM membership",
      "body": "...",
      "status": "UNREAD",
      "read": false,
      "membershipId": "mem-1",
      "createdAt": "2026-06-11T08:00:00Z"
    }
  ],
  "meta": {
    "timestamp": "2026-06-11T08:05:00Z",
    "page": 0,
    "size": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

Notes on the `data` item shape (conforms to `notification-inbox-contract.md` § 1;
`deepLink` and `readAt` are `@JsonInclude(NON_NULL)` and omitted here since this
item is unread with no deep link): `status` and `membershipId` are non-normative
fan-domain extensions (contract § 1.2) preserved alongside the normative `read`
boolean.

### `POST /api/fan/notifications/{id}/read` — Mark read

Auth: same as list; the notification must belong to the caller (tenant + account
scoped) or `404 NOTIFICATION_NOT_FOUND`. Idempotent — re-marking an already-READ
notification is a 200 no-op that preserves the original `readAt`.

Response 200:
```json
{
  "data": {
    "id": "0190f3e2-...",
    "sourceDomain": "fan",
    "type": "WELCOME",
    "title": "Welcome to PREMIUM membership",
    "body": "...",
    "status": "READ",
    "read": true,
    "membershipId": "mem-1",
    "createdAt": "2026-06-11T08:00:00Z",
    "readAt": "2026-06-11T09:00:00Z"
  },
  "meta": {
    "timestamp": "2026-06-11T09:00:00Z"
  }
}
```

---

## Relationship

- [`projects/fan-platform/specs/services/notification-service/architecture.md`](../../services/notification-service/architecture.md)
  § Inbox Read API — the service architecture this contract documents.
- [`platform/contracts/notification-inbox-contract.md`](../../../../../platform/contracts/notification-inbox-contract.md)
  — the cross-domain item shape (§ 1) and verb/paging/read semantics (§ 2) this
  surface conforms to; the `meta` wrapper shape above is domain-owned (§ 2.2).
- [`docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md`](../../../../../docs/adr/ADR-MONO-058-fleet-wide-shared-technical-scaffolding-consolidation.md)
  § D3 — the pagination-carrier adoption that added `totalPages` to this endpoint's
  `meta` block.
- [`projects/fan-platform/tasks/done/TASK-FAN-BE-023-notification-inbox-shape-conformance.md`](../../../tasks/done/TASK-FAN-BE-023-notification-inbox-shape-conformance.md)
  — the prior ADR-MONO-043 conformance work on this same endpoint (`sourceDomain`,
  `deepLink`, `unread` alias); unaffected by this task's `totalPages` addition.
