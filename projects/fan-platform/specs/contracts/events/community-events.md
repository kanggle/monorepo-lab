# community-events — Kafka contract

> Producer: `fan-platform-community-service`. All events flow through the
> outbox table (`outbox`) and are relayed by `CommunityOutboxPollingScheduler`
> with `acks=all` + `enable.idempotence=true`.

## Common envelope

Every payload (event_type-specific schema below) is wrapped by
`libs:java-messaging`'s `BaseEventPublisher`:

```json
{
  "eventId":      "<UUID>",
  "eventType":    "community.post.published",
  "source":       "fan-platform-community-service",
  "occurredAt":   "2026-05-03T00:00:00Z",
  "schemaVersion": 1,
  "partitionKey": "<aggregate id, e.g. postId>",
  "payload":      { /* event-specific schema */ }
}
```

Idempotency key for consumers = `eventId` (UUID, persisted by
`libs:java-messaging`'s `processed_events` table on consumer side).

> **Topic naming convention.** Every Kafka topic name is the envelope's
> `eventType` field plus a `.v1` suffix. Example: an envelope with
> `eventType="community.post.published"` is published on the topic
> `community.post.published.v1`. Consumers MUST subscribe to the suffixed
> topic name; the envelope's `eventType` stays unsuffixed for forward
> compatibility — a future v2 schema can be published on a new topic
> (`community.post.published.v2`) without re-emitting events under a
> different envelope value.

## Topics (`.v1` suffix per `platform/event-driven-policy.md`)

| Topic | Producer trigger | Partition key | Retention (recommended) |
|---|---|---|---|
| `community.post.published.v1` | DRAFT → PUBLISHED transition | `postId` | 14 d |
| `community.post.status_changed.v1` | any status transition | `postId` | 14 d |
| `community.comment.added.v1` | new comment INSERT | `postId` | 14 d |
| `community.reaction.added.v1` | new or upserted reaction | `postId` | 7 d |

---

## `community.post.published.v1`

Triggered when `PublishPostUseCase` flips a post from DRAFT to PUBLISHED.

```json
{
  "postId":          "<UUID>",
  "tenantId":        "fan-platform",
  "authorAccountId": "<UUID>",
  "postType":        "ARTIST_POST | FAN_POST",
  "visibility":      "PUBLIC | MEMBERS_ONLY | PREMIUM",
  "publishedAt":     "ISO-8601 UTC"
}
```

Consumers (planned):
- notification-service (push fanout to followers)
- search-service (index post body + metadata)

## `community.post.status_changed.v1`

Triggered on every status transition (PUBLISH / HIDE / DELETE / un-HIDE).

```json
{
  "postId":          "<UUID>",
  "tenantId":        "fan-platform",
  "from":            "DRAFT | PUBLISHED | HIDDEN",
  "to":              "PUBLISHED | HIDDEN | DELETED",
  "actorAccountId":  "<UUID>",
  "occurredAt":      "ISO-8601 UTC"
}
```

Consumers (planned): search-service (re-index / remove on HIDDEN/DELETED), audit pipeline.

## `community.comment.added.v1`

Triggered when `AddCommentUseCase` succeeds.

```json
{
  "postId":          "<UUID>",
  "tenantId":        "fan-platform",
  "commentId":       "<UUID>",
  "authorAccountId": "<UUID>",
  "occurredAt":      "ISO-8601 UTC"
}
```

Consumers (planned): notification-service (mention/reply alerts).

## `community.reaction.added.v1`

Triggered on each `AddReactionUseCase.execute(...)` call — both new reactions
and type-changes (re-emits with updated type). Consumers MUST treat duplicates
as no-ops keyed by `eventId`.

```json
{
  "postId":            "<UUID>",
  "tenantId":          "fan-platform",
  "reactorAccountId":  "<UUID>",
  "reactionType":      "LIKE | LOVE | FIRE | SAD",
  "occurredAt":        "ISO-8601 UTC"
}
```

Consumers (planned): notification-service (interaction badges), analytics.

---

## Failure handling

- Outbox INSERT shares the business transaction. If the business write rolls back, no event is enqueued.
- Kafka publish failure → row stays `status=PENDING`; retried each tick. Metric `community_outbox_publish_failures_total` increments.
- DLQ (v2): rows stuck PENDING > 1h → moved to a `outbox_dead_letter` table + operator alert.
