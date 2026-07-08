# API Contract — notification-service (in-app inbox)

Base path: `/api/erp/notifications` (rewritten by the gateway from
`/api/v1/erp/notifications` once `gateway-service` is introduced; v1 = direct
JWT to the service).

Authoritative architecture:
[`notification-service/architecture.md`](../../services/notification-service/architecture.md).
Event-consumer (fan-out) contract:
[`notification-subscriptions.md`](../events/notification-subscriptions.md).
Domain rules: [`rules/domains/erp.md`](../../../../../rules/domains/erp.md)
(E5 — notification-service holds no approval business logic, the notified fact's
source of record is `approval-service`; E6 — recipient-scoped READ gate; E7 —
internal-only).

The inbox is the **read + acknowledge** surface over the notifications fanned out
by [`notification-subscriptions.md`](../events/notification-subscriptions.md).
It is otherwise read-shaped: the only mutation is the idempotent **mark-read**
acknowledgement.

All endpoints:
- Require `Authorization: Bearer <token>` satisfying the **entitlement-trust
  dual-accept** gate (`tenant_id ∈ {erp, *}` ∪ signed `entitled_domains ∋ erp`,
  RS256, IAM JWKS). Both branches fail → 403 `TENANT_FORBIDDEN`.
- Enforce the **READ authorization gate** (E6, fail-closed): `erp.read` scope ∨
  `isOperator()` ∨ entitled; else 403 `PERMISSION_DENIED`. Same
  `RoleScopeAuthorizationAdapter` READ semantics as
  [`read-model-api.md`](read-model-api.md).
- Are **recipient-scoped** (E6 data-scope, fail-closed): the caller sees **only
  their own** notifications — `recipient == caller.sub` (the employee id in the
  JWT `sub` claim). There is **no** cross-recipient inbox view; an operator token
  that passes the READ gate still only sees its own `sub`'s inbox in v1 (a
  cross-recipient operator surface is v2). A notification whose `recipient` is
  not the caller's `sub` is treated as **non-existent** to the caller → 404, not
  403 (avoids existence leak, mirroring the read-model detail rule).
- Success envelope: `{ "data": <payload>, "meta": { "timestamp": "<ISO-8601>",
  ... } }`. List responses extend `meta` with `PageMeta`.
- Error envelope: `{ "code": "<ERROR_CODE>", "message": "<human>", "details":
  <object?>, "timestamp": "<ISO-8601>" }`. Codes per
  [`platform/error-handling.md`](../../../../../platform/error-handling.md)
  erp section.
- Follow the `@JsonInclude(NON_NULL)` **absent-field** convention — an absent
  field is **omitted**, never serialized as `null` (`readAt` is ABSENT until the
  notification is read).
- No webhook / public-callback surface (erp is internal-only, E7). Only
  `/actuator/{health,info}` are unauthenticated.

---

## Common shape

`Notification`:
```json
{ "id": "ntf-...",
  "sourceDomain": "erp",
  "type": "APPROVAL_SUBMITTED|APPROVAL_APPROVED|APPROVAL_REJECTED|APPROVAL_WITHDRAWN|DELEGATION_GRANTED|DELEGATION_REVOKED",
  "title": "결재 상신 통지",
  "body": "...",
  "deepLink": "/erp/approval?request=<sourceId>  (APPROVAL) | /erp/delegation  (DELEGATION)",
  "sourceType": "APPROVAL|DELEGATION",
  "sourceId": "appr-... (APPROVAL) | grant-... (DELEGATION)",
  "read": false,
  "createdAt": "<ISO-8601 UTC>",
  "readAt": "<ISO-8601 UTC; ABSENT until the notification is read>" }
```

- `id` — the notification's own id (`ntf-...`), distinct from `sourceId`.
- `sourceDomain` — the owning-domain attribution field of the cross-domain
  envelope ([`notification-inbox-contract.md`](../../../../../platform/contracts/notification-inbox-contract.md) § 1); always the
  constant `"erp"`. The console-bff aggregator (ADR-MONO-043 D2/P3) uses it to
  label + route each merged item.
- `deepLink` — the in-app console route the shell's notification bell navigates
  to (contract § 1). **Derived** from `sourceType`/`sourceId` (TASK-ERP-BE-028):
  `APPROVAL` → `/erp/approval?request=<sourceId>` (the 결재함 route preselects the
  request — console PC-FE-230), `DELEGATION` → `/erp/delegation` (the 위임 route).
  The console owns the route SoT; these strings track the console-web route table.
  Non-null for every current source, so it is always present (NON_NULL still omits
  it should a future source derive no route).
- `type` — the approval + delegation notification types fanned out by
  [`notification-subscriptions.md`](../events/notification-subscriptions.md)
  (1:1 with the consumed `eventType`): the four `APPROVAL_*` transitions plus
  `DELEGATION_GRANTED`/`DELEGATION_REVOKED`.
- `sourceType` — `"APPROVAL"` (approval-workflow source) or `"DELEGATION"`
  (delegation grant/revoke source). A forward enum so a v2 masterdata /
  permission source can be added additively.
- `sourceId` — for `APPROVAL` the originating `approvalRequestId`, for
  `DELEGATION` the `grantId` (the consumed `aggregateId`). An APPROVAL client
  deep-links to the request via `GET /api/erp/approval/requests/{sourceId}`
  (source of record — E5).
- `read` — boolean; `false` until acknowledged.
- `readAt` — ISO-8601 UTC; **ABSENT** while `read == false`, present once read
  (NON_NULL absent convention — never `null`).

`PageMeta` (list responses):
`{ "page": 0, "size": 20, "totalElements": 7, "timestamp": "<ISO-8601>" }`.

---

## Endpoints

| Method | Path | Use case | Auth gate | Mutates |
|---|---|---|---|---|
| GET | `/api/erp/notifications` | Current recipient's inbox (paged, optional `unread` filter) | entitlement-trust + READ + recipient-scope | no |
| GET | `/api/erp/notifications/{id}` | Single notification (must belong to caller) | entitlement-trust + READ + recipient-scope | no |
| POST | `/api/erp/notifications/{id}/read` | Mark read (idempotent) | entitlement-trust + READ + recipient-scope | yes (idempotent) |

---

## GET /api/erp/notifications

The current caller's inbox — notifications where `recipient == caller.sub`,
newest first (`createdAt` desc).

**Headers**: `Authorization` (req)

**Query**:
- `unread` — optional boolean. `true` → only `read == false`; `false` → only
  `read == true`; omitted → all. (Bad value → 400 `VALIDATION_ERROR`.)
- `page` — optional, default `0`
- `size` — optional, default `20`, ≤ `100` (> 100 → 400 `VALIDATION_ERROR`)

**200**:
```json
{ "data": [ <Notification>, ... ],
  "meta": { "page": 0, "size": 20, "totalElements": 7,
            "timestamp": "<ISO-8601>" } }
```

**Errors**: 400 `VALIDATION_ERROR` (bad `unread` / `page` / `size`),
401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

---

## GET /api/erp/notifications/{id}

A single notification. The row **must belong to the caller** (`recipient ==
caller.sub`); otherwise 404 (existence-leak avoidance — a notification addressed
to another recipient is indistinguishable from a non-existent one).

**Headers**: `Authorization` (req)

**Path**: `id` — notification id (`ntf-...`).

**200**:
```json
{ "data": <Notification>,
  "meta": { "timestamp": "<ISO-8601>" } }
```

**Errors**: 404 `NOTIFICATION_NOT_FOUND` (no notification with `id` for this
caller — either unknown id, or a row owned by another recipient → 404 not 403),
401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED`, 403 `TENANT_FORBIDDEN`.

---

## POST /api/erp/notifications/{id}/read

Mark the notification read. The row **must belong to the caller**.

**Idempotent — no `Idempotency-Key` header.** Setting `read = true` is
**naturally idempotent**: the operation is a state-converging assignment, not an
accumulating side effect (unlike a create / append). Re-marking an already-read
notification is a no-op that returns **200** with the **same** state (the
original `readAt` is preserved — it is NOT advanced on the second call). Because
no replay can produce a divergent result, the transactional `Idempotency-Key`
mechanism (which guards accumulating mutations such as approval transitions in
[`approval-api.md`](approval-api.md)) does **not** apply here.

**Headers**: `Authorization` (req)

**Path**: `id` — notification id (`ntf-...`).

**Request body**: none.

**200** (first call — transitions to read):
```json
{ "data": { "id": "ntf-...", "sourceDomain": "erp", "type": "APPROVAL_SUBMITTED",
            "title": "결재 상신 통지", "body": "...",
            "sourceType": "APPROVAL", "sourceId": "appr-...",
            "read": true, "createdAt": "<ISO-8601>",
            "readAt": "<ISO-8601 UTC; now present>" },
  "meta": { "timestamp": "<ISO-8601>" } }
```

**200** (re-mark — already read): identical body, **same** `readAt` as the first
call (preserved, not advanced).

**Errors**: 404 `NOTIFICATION_NOT_FOUND` (unknown id, or owned by another
recipient → 404 not 403), 401 `UNAUTHORIZED`, 403 `PERMISSION_DENIED`,
403 `TENANT_FORBIDDEN`.

---

## Error codes

| Code | HTTP | When |
|---|---|---|
| `VALIDATION_ERROR` | 400 | Bad `unread` (non-boolean) / `page` / `size` (> 100) query param |
| `UNAUTHORIZED` | 401 | Missing / invalid / expired JWT |
| `PERMISSION_DENIED` | 403 | Caller passes entitlement-trust but lacks the READ gate (`erp.read` ∨ operator ∨ entitled) (E6) |
| `TENANT_FORBIDDEN` | 403 | Both entitlement-trust branches fail (`tenant_id ∉ {erp, *}` ∧ `entitled_domains ∌ erp`) |
| `NOTIFICATION_NOT_FOUND` | 404 | No notification with the given `id` is visible to the caller — unknown id, **or** a row whose `recipient` is another employee (returned as 404, not 403, to avoid existence leak) |

> **`NOTIFICATION_NOT_FOUND` (404) is a PROPOSED new erp code** — it is **not yet
> registered** in [`platform/error-handling.md`](../../../../../platform/error-handling.md).
> The existing `NOTIFICATION_NOT_FOUND` row in that catalog is in the
> **ecommerce** section (owned by ecommerce's own `notification-service`), a
> different domain — the erp `[domain: erp]` section has no notification family.
> **Registration requirement (error-handling.md Change Rule)**: before
> implementation, add a `NOTIFICATION_NOT_FOUND | 404 | erp notification with the
> given id does not exist for the caller (unknown, or owned by another recipient
> → 404 not 403 to avoid existence leak) (`NotificationNotFoundException`)` row
> to a new `## Notification  [domain: erp]` subsection. (Alternative considered:
> reuse the generic `NOT_FOUND` — rejected because a domain-specific
> `*_NOT_FOUND` is the platform convention where one applies, and every other erp
> aggregate has its own code, e.g. `MASTERDATA_NOT_FOUND`,
> `APPROVAL_REQUEST_NOT_FOUND`.)

No mutation here requires `IDEMPOTENCY_KEY_REQUIRED` / `IDEMPOTENCY_KEY_CONFLICT`
— mark-read is naturally idempotent (see POST `…/read` above), so the
transactional idempotency-key codes do not surface on this contract.

---

## Auth & scope notes

- **entitlement-trust dual-accept**: identical gate to
  [`read-model-api.md`](read-model-api.md) — `tenant_id ∈ {erp, *}` OR signed
  `entitled_domains ∋ erp`; both fail → 403 `TENANT_FORBIDDEN`.
- **READ gate (E6, fail-closed)**: `erp.read` ∨ `isOperator()` ∨ entitled; else
  403 `PERMISSION_DENIED`.
- **Recipient scope (E6 data-scope, fail-closed)**: every query is implicitly
  filtered to `recipient == caller.sub`; a detail/mark-read on another
  recipient's notification is 404, never 403. This is the data-scope boundary
  for this service — the inbox is **personal**, there is no all-recipients view
  in v1.
- **internal-only (E7)**: SSO/IAM-issued JWT required; no anonymous / external
  path. Network-layer `internal: true` is the primary enforcement; this contract
  is the application-layer surface.

---

## NON_NULL absent-field convention

`readAt` is **ABSENT** (the JSON key is omitted) while `read == false`, and
present once the notification is read — it is **never** serialized as `null`.
A client distinguishes "unread" by the absence of `readAt` (or `read == false`),
not by a null value. This matches the erp-wide `@JsonInclude(NON_NULL)`
convention ([`read-model-api.md`](read-model-api.md),
[`erp-approval-events.md`](../events/erp-approval-events.md)
`finalizedAt` / `reason`).

---

## v2 deferred

- **External delivery channels** (email / push / SMS / chat) — v1 is in-app
  inbox only. The inbox surface is unchanged when channels are added (channels
  are a delivery concern, not a read-API concern).
- **Masterdata-change / permission notifications** — v1 fans out the four
  approval transitions **and** the two delegation events (`DELEGATION_GRANTED`/
  `DELEGATION_REVOKED`, `sourceType=DELEGATION`)
  ([`notification-subscriptions.md`](../events/notification-subscriptions.md));
  a v2 increment adds further `sourceType` values (`MASTERDATA`, `PERMISSION`)
  additively.
- **Recipient preferences** (per-type / per-channel mute, quiet hours) — v2.
- **Digest / batching** (roll-up of N notifications into one) — v2.
- **Cross-recipient operator inbox view** (an operator querying another
  employee's inbox) — v2; v1 is strictly self-scoped (`recipient == caller.sub`).
- **Bulk mark-all-read** — v2; v1 marks one notification per call.
