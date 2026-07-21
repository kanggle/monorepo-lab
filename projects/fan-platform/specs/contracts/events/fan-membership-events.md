# fan-membership-events — Kafka contract

> Spec authored by **TASK-FAN-BE-008**. Implementation = **TASK-FAN-BE-009**.
>
> Producer: `fan-platform-membership-service`. All events flow through the outbox
> table (`outbox`) and are relayed by `MembershipOutboxPublisher` with
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
| `fan.membership.expired.v1` | window end (`now > validTo`), detected by the expiry sweeper | `membershipId` | 14 d | **emitted (TASK-FAN-BE-014)** |

> **`fan.membership.expired.v1` — emitted by the expiry sweeper (TASK-FAN-BE-014).**
> Expiry is still **read-time** for the stored model (architecture.md § State
> Machine) — there is **no stored `EXPIRED` status**. A scheduled sweeper
> (`MembershipExpirySweepScheduler`) detects memberships whose window has just
> ended (`status=ACTIVE AND now > validTo AND expiry_notified_at IS NULL`),
> sets a one-time `expiry_notified_at` marker, and emits `fan.membership.expired.v1`
> **exactly once** per membership (the marker + outbox append share one
> transaction). The stored `status` stays `ACTIVE` (the event is a notification
> trigger, not a lifecycle transition — Option B, architecture.md § Expiry
> Sweeper). Read-time `active` and event-time are intentionally decoupled: a
> membership reads `active=false` the instant `now > validTo`, but the event
> follows on the next sweep tick. Consumers MUST treat the event as at-least-once
> (dedupe on `eventId`).

Consumer: **notification-service** (`EXPIRY_REMINDER`, TASK-FAN-BE-014) — all three
topics now consumed.

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

## `fan.membership.expired.v1` (emitted by the expiry sweeper — TASK-FAN-BE-014)

Emitted once per membership when the sweeper first observes a passed window
(`status=ACTIVE AND now > validTo AND expiry_notified_at IS NULL`). The membership
keeps `status=ACTIVE` (read-time expiry; no stored EXPIRED).

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

Consumer: notification-service → `EXPIRY_REMINDER` in-app notification (expiry /
renewal prompt).

---

## Failure handling

- Outbox INSERT shares the business transaction. If the business write rolls back,
  no event is enqueued.
- Kafka publish failure → row stays `status=PENDING`; retried each tick. Metric
  `membership_outbox_publish_failures_total` increments.
- DLQ (v2): rows stuck PENDING > 1h → moved to an `outbox_dead_letter` table +
  operator alert.
