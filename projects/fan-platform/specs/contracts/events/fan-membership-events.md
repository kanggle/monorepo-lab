# fan-membership-events — Kafka contract

> Spec authored by **TASK-FAN-BE-008**. Implementation = **TASK-FAN-BE-009**.
>
> Producer: `fan-platform-membership-service`. All events flow through the outbox
> table (`outbox`) and are relayed by `MembershipOutboxPollingScheduler` with
> `acks=all` + `enable.idempotence=true`.

## Common envelope

Every payload (event_type-specific schema below) is wrapped by
`libs:java-messaging`'s `BaseEventPublisher`:

```json
{
  "eventId":      "<UUID>",
  "eventType":    "fan.membership.activated",
  "source":       "fan-platform-membership-service",
  "occurredAt":   "2026-06-06T00:00:00Z",
  "schemaVersion": 1,
  "partitionKey": "<aggregate id, e.g. membershipId>",
  "payload":      { /* event-specific schema */ }
}
```

Idempotency key for consumers = `eventId` (UUID, persisted by
`libs:java-messaging`'s `processed_events` table on the consumer side).

> **Topic naming convention.** Every Kafka topic name is the envelope's
> `eventType` field plus a `.v1` suffix. Example: an envelope with
> `eventType="fan.membership.activated"` is published on the topic
> `fan.membership.activated.v1`. Consumers MUST subscribe to the suffixed topic
> name; the envelope's `eventType` stays unsuffixed for forward compatibility.

## Topics (`.v1` suffix per `platform/event-driven-policy.md`)

| Topic | Producer trigger | Partition key | Retention (recommended) | Status |
|---|---|---|---|---|
| `fan.membership.activated.v1` | subscribe → ACTIVE (PG mock approved) | `membershipId` | 14 d | **emitted** |
| `fan.membership.canceled.v1` | ACTIVE → CANCELED (cancel) | `membershipId` | 14 d | **emitted** |
| `fan.membership.expired.v1` | window end (`now > validTo`) | `membershipId` | 14 d | **forward-declared, NOT emitted in v1** |

> **`fan.membership.expired.v1` gap (honest record).** Expiry is computed at
> **read-time** (architecture.md § State Machine) — there is no stored `EXPIRED`
> transition and no scheduler in this increment to detect the window boundary, so
> NO `fan.membership.expired.v1` event is produced in v1. The topic + payload are
> declared here so a future sweeper increment (and the notification-service v2
> consumer) can be designed against a stable contract. Until that increment ships,
> consumers MUST derive "expired" from `validTo` rather than expect an event. This
> mirrors how community-events records its v2 gaps.

Consumer (planned, all topics): **notification-service v2** (not built in this
increment — producer-only forward interface).

---

## `fan.membership.activated.v1`

Triggered when `SubscribeUseCase` creates a membership in `ACTIVE` (PG mock
authorize approved).

```json
{
  "membershipId":  "<UUID>",
  "tenantId":      "fan-platform",
  "accountId":     "<UUID>",
  "tier":          "MEMBERS_ONLY | PREMIUM",
  "planMonths":    1,
  "validFrom":     "ISO-8601 UTC",
  "validTo":       "ISO-8601 UTC",
  "occurredAt":    "ISO-8601 UTC"
}
```

Consumer (planned): notification-service v2 (subscription welcome / member fanout).

## `fan.membership.canceled.v1`

Triggered when `CancelMembershipUseCase` transitions `ACTIVE → CANCELED`. A
re-cancel of an already-CANCELED membership is an idempotent no-op and emits NO
new event.

```json
{
  "membershipId":  "<UUID>",
  "tenantId":      "fan-platform",
  "accountId":     "<UUID>",
  "tier":          "MEMBERS_ONLY | PREMIUM",
  "reason":        "string | null",
  "canceledAt":    "ISO-8601 UTC",
  "occurredAt":    "ISO-8601 UTC"
}
```

Consumer (planned): notification-service v2 (cancellation notice).

## `fan.membership.expired.v1` (forward-declared — NOT emitted in v1)

Reserved payload for a future expiry sweeper increment. NOT produced in this
increment.

```json
{
  "membershipId":  "<UUID>",
  "tenantId":      "fan-platform",
  "accountId":     "<UUID>",
  "tier":          "MEMBERS_ONLY | PREMIUM",
  "validTo":       "ISO-8601 UTC",
  "occurredAt":    "ISO-8601 UTC"
}
```

Consumer (planned): notification-service v2 (expiry / renewal prompt) — pending the
sweeper increment.

---

## Failure handling

- Outbox INSERT shares the business transaction. If the business write rolls back,
  no event is enqueued.
- Kafka publish failure → row stays `status=PENDING`; retried each tick. Metric
  `membership_outbox_publish_failures_total` increments.
- DLQ (v2): rows stuck PENDING > 1h → moved to an `outbox_dead_letter` table +
  operator alert.
