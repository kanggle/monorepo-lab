# Event Subscriptions — notification-service

Kafka topics **consumed** by `erp-platform/apps/notification-service` to fan out
approval-state notifications into an in-app inbox (first increment). This service
is a **terminal consumer** — it **publishes no events** (E5-style boundary:
`rules/domains/erp.md` § Internal Event Catalog declares **no** `erp.notification.*`
producer topic; notification-service notifies only, it does not re-emit).

Authoritative architecture:
[`notification-service/architecture.md`](../../services/notification-service/architecture.md).
Producer contract (consumed unchanged):
[`erp-approval-events.md`](erp-approval-events.md).
HTTP (inbox) contract: [`notification-api.md`](../http/notification-api.md).
Domain rules: [`rules/domains/erp.md`](../../../../../rules/domains/erp.md)
§ Internal Event Catalog + E5 (책임 경계 — 재발행 없음) / E6 (인가).

This contract closes the `notification-service` **forward consumer** that
[`erp-approval-events.md`](erp-approval-events.md) § First-increment publication
decision named ("`notification-service` **v2** — fan-out approval-state
notifications (상신/승인/반려 통지)"). The producer interface is consumed
**unchanged**.

---

## Consumed topics (first increment)

The four approval-transition topics (producer = `approval-service`). One inbound
event → one in-app `Notification` row per **resolved recipient**.

| Topic | Producer | Recipient resolution | Notification type |
|---|---|---|---|
| `erp.approval.submitted.v1` | approval-service | `payload.approverId` (the approver who must act on the newly-submitted request) | `APPROVAL_SUBMITTED` |
| `erp.approval.approved.v1` | approval-service | `payload.submitterId` (the submitter is told their request was approved) | `APPROVAL_APPROVED` |
| `erp.approval.rejected.v1` | approval-service | `payload.submitterId` (the submitter is told their request was rejected; `reason` always present) | `APPROVAL_REJECTED` |
| `erp.approval.withdrawn.v1` | approval-service | `payload.approverId` (the approver is told the submitter withdrew the pending request) | `APPROVAL_WITHDRAWN` |

Consumer group: `erp-notification-v1`.

**Recipient = an employee master id** (`emp-...`), written verbatim to the
`Notification.recipient` column. The inbox HTTP contract scopes reads to
`recipient == caller.sub` ([`notification-api.md`](../http/notification-api.md)).
No display-name enrichment is placed in the notification at consume time — the
event carries only ids; `title` / `body` are composed from the **type + the ids
already on the payload** (a v2 increment may resolve master display names at
read time, same E5 read-time-resolution rule as the read-model). The notification
is **never** fabricated from a master lookup that has not occurred.

### Consumed topic — delegation (TASK-ERP-BE-014, additive)

The fifth consumed topic. Producer = `approval-service`
([`erp-approval-events.md`](erp-approval-events.md) § v2.1 amendment). A
`DelegationGrant`-create event → one in-app `Notification` to the **delegate**.
This topic has a **different aggregate + payload shape** from the four transition
topics (`aggregateType = "DelegationGrant"`, partition key = `grantId`, NO
`approverId`/`submitterId`/`subjectId`), so it is consumed by a parallel mapper
path — the four transition consumers are **byte-unchanged**.

| Topic | Producer | Recipient resolution | Notification type |
|---|---|---|---|
| `erp.approval.delegated.v1` | approval-service | `payload.delegateId` (the employee who **received** the delegation authority is told they may now act on the delegator's behalf) | `DELEGATION_GRANTED` |
| `erp.approval.delegation.revoked.v1` | approval-service | `payload.delegateId` (the employee who **loses** the delegated authority is told it was revoked — TASK-ERP-BE-016) | `DELEGATION_REVOKED` |

- `aggregateId` (= `grantId`) → partition key; written to `Notification.sourceId`
  with `sourceType = DELEGATION` (both delegation events).
- `payload.delegateId` — the resolved recipient (both events); null/blank →
  invalid → immediate DLT (cannot deliver to an absent recipient).
- **granted** (`erp.approval.delegated.v1`): `payload.delegatorId` /
  `payload.validFrom` always present; `payload.validTo` ABSENT = open-ended
  ("무기한"); `payload.reason` ABSENT when none. Rendered via `DelegationEvent`.
- **revoked** (`erp.approval.delegation.revoked.v1`, TASK-ERP-BE-016): the revoke
  payload has **no validity window** (`grantId` / `delegatorId` / `delegateId` /
  `reason?` — `erp-approval-events.md` § v2.2), so it is consumed by a separate
  parallel mapper path (`DelegationRevokedEvent`); the granted path + the four
  transition consumers are **byte-unchanged**. The producer emits it ONLY on an
  ACTIVE→REVOKED transition (ERP-BE-015), so a notification is created once per
  real revoke.

> **`erp.masterdata.*.changed.v1` (masterdata-change notifications) are NOT
> consumed** in this increment — master-change / permission-change / data-scope
> notifications (`rules/domains/erp.md` § Integration Boundaries "알림 채널":
> 마스터 변경 통지 / 권한 변경 알림) are **v2**. Until then this is a deliberate,
> recorded gap (no silent drop — the topics are simply not in the subscription
> list). See § v2 deferred below.

---

## Envelope (consumed — libs/java-messaging standard)

Consumed **identically** to the producer shape in
[`erp-approval-events.md`](erp-approval-events.md) § Envelope — **do not invent a
new shape**. This consumer reads:

```json
{
  "eventId": "<uuid>",
  "eventType": "erp.approval.submitted",
  "occurredAt": "<ISO-8601 UTC>",
  "tenantId": "erp",
  "source": "erp-platform-approval-service",
  "aggregateType": "ApprovalRequest",
  "aggregateId": "<approvalRequestId>",
  "traceId": "<w3c-traceparent trace-id>",
  "payload": { ... }
}
```

- `eventId` — dedupe key (`processed_events` PK). Null → invalid → immediate DLT.
- `eventType` — selects the recipient-resolution + `Notification.type` row above.
- `aggregateId` (= `approvalRequestId`) — partition key (per-request ordering);
  written to `Notification.sourceId`.
- `aggregateType` — always `"ApprovalRequest"` on every approval event.
- `payload.approverId` / `payload.submitterId` — recipient resolution per the
  table above. Null on the resolved-recipient field for the event's type →
  invalid → immediate DLT (cannot deliver to an absent recipient).
- `payload.subjectType` / `payload.subjectId` — included in `body` composition.
- `payload.reason` — included in `body` when present (always present on
  `rejected` / `withdrawn`; ABSENT-or-present on `approved`; ABSENT on
  `submitted` — the `@JsonInclude(NON_NULL)` absent-field convention).
- `payload.finalizedAt` — the terminal timestamp (ABSENT on `submitted`).
- `traceId` — propagated into the consume→deliver span (observability
  continuity: approval transition → notification creation is one trace).

---

## Consumption rules

- **Idempotency (T8)**: dedupe on `eventId` via `processed_events`; a duplicate
  is skipped without mutation (re-delivery creates **no** second inbox row — the
  inbox is byte-identical after a redelivery). This is the at-least-once
  contract the producer mandates ([`erp-approval-events.md`](erp-approval-events.md)
  § Consumer rules: "Dedupe on `eventId`").
- **Ordering**: per-`approvalRequestId` only (producer partitions by
  `aggregateId` = `approvalRequestId`). A request's `submitted` precedes its
  terminal in publish order; events for **different** requests may interleave.
  Notifications are independent rows, so ordering matters only for the dedupe
  guarantee, not for cross-request inbox layout (the inbox sorts by
  `createdAt`).
- **Terminal-once (E3) tolerance**: a request emits at most one terminal event
  after `submitted`. If a terminal event arrives without a prior-seen
  `submitted` (compaction / replay-from-middle), the terminal notification is
  still created (the notification is the terminal fact). A later duplicate /
  out-of-contract transition for an already-handled `aggregateId` + `eventId` is
  dropped by the dedupe gate above.
- **`RETIRED`/recipient resolution**: notification-service does **not** consume
  master `*.changed` topics in this increment, so it performs **no** master
  state interpretation — the recipient is the opaque `emp-...` id carried on the
  approval payload (no `RETIRED` semantics apply here; that is a read-model
  concern).
- **Delivery resilience (ADR-MONO-005 Category C)** — the consume→deliver step
  is a **single-step retry+DLT** class (NOT a saga): manual ACK; on a transient
  deliver failure (in-app write contention / DB blip), retry with **exponential
  backoff + jitter**, up to the Category-C retry budget (**5 attempts**), then
  route the event to `<topic>.DLT` as a **terminal outcome** (no compensation,
  no escalation chain — the inbox write is the only step). An **invalid
  envelope** (null `eventId`, or null on the type's resolved-recipient field) →
  **immediate DLT, no retry** (it can never succeed). See
  [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md)
  § Category C.
- **No re-emission (E5 boundary)**: notification-service publishes **NO** events
  and writes back to **no** other service. `rules/domains/erp.md` § Internal
  Event Catalog declares no `erp.notification.*` producer topic — this service is
  a **terminal consumer**. It owns only its own inbox store; the authoritative
  approval state + history stays with `approval-service`
  (`GET /api/erp/approval/requests/{id}` is the source of record). The
  notification is a derived convenience fact, never an authoritative one.

---

## v2 deferred (recorded gaps — no silent drop)

The following are **out of scope** for this increment and are recorded here so a
later increment wires against a known gap rather than discovering a silent drop:

- **External delivery channels** (email / push / SMS / chat — `rules/domains/erp.md`
  § Integration Boundaries "알림 채널"). v1 is **in-app inbox only**.
- **Masterdata-change notifications** (`erp.masterdata.*.changed.v1`) — 마스터
  변경 통지. Not subscribed in v1.
- **Permission / data-scope notifications** (`erp.permission.*`) —
  forward-declared in the catalog, owned by the v2 permission increment; not
  emitted, so not consumed. (Delegation `erp.approval.delegated.v1` **is** now
  consumed — § Consumed topic — delegation, TASK-ERP-BE-014.)
- ~~**Delegation revoke notification**~~ → **DONE (TASK-ERP-BE-016)** — once
  ERP-BE-015 added the `erp.approval.delegation.revoked.v1` producer event, the
  delegate is now notified on revoke (§ Consumed topic — delegation, row 2).
- **Recipient preferences / digest / batching** — per-recipient channel
  preferences and digest roll-up are v2; v1 delivers one inbox row per event.
- **Display-name enrichment** at consume time — v1 stores the ids; read-time
  master resolution (same E5 rule as the read-model) is a v2 read-side concern.

---

## Relationship to platform / rules

- Idempotent consumption + dedupe = `rules/traits/transactional.md` T8 +
  `libs/java-messaging` standard (`processed_events`).
- Terminal-consumer / no-re-emission boundary = `rules/domains/erp.md` E5
  (notification-service holds no approval business logic; the single source of
  record for every notified fact is `approval-service`).
- Consumer resilience (single-step retry + DLT, no saga) =
  [ADR-MONO-005](../../../../../docs/adr/ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md)
  Category C — same shape as `read-model-service`
  [`read-model-subscriptions.md`](read-model-subscriptions.md) and scm
  `inventory-visibility-subscriptions.md`.
- Inbox read authorization (recipient-scoped READ gate) =
  [`notification-api.md`](../http/notification-api.md) (E6).
