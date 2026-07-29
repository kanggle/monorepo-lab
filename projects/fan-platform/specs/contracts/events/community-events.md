# community-events — Kafka contract

> Producer: `fan-platform-community-service`. All events flow through the
> outbox table (`outbox`) and are relayed by `CommunityOutboxPublisher`
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
  "postId":               "<UUID>",
  "tenantId":             "fan-platform",
  "commentId":            "<UUID>",
  "authorAccountId":      "<UUID>",
  "postAuthorAccountId":  "<UUID>",
  "mentionedAccountIds":  ["<UUID>", "..."],
  "occurredAt":           "ISO-8601 UTC"
}
```

### Recipient-routing fields (TASK-FAN-BE-026, additive)

`postAuthorAccountId` and `mentionedAccountIds` were **added to the same `.v1`
topic** so a consumer can address a reply/mention alert **without a synchronous
call back into community-service** (the no-sync-coupling invariant — see
`specs/services/notification-service/architecture.md` and TASK-FAN-BE-026 §
Design decision). The addition is backward-compatible per this directory's
additive-compatibility rule: no field was renamed or removed, the topic and
`schemaVersion` are unchanged, and consumers tolerate unknown fields.

| Field | Type | Semantics |
|---|---|---|
| `postAuthorAccountId` | string (UUID) | the **post**'s author = the reply-alert recipient. Equal to `authorAccountId` when a user comments on their own post (consumers suppress the self-notify). |
| `mentionedAccountIds` | array of string (UUID) | mention-alert recipients. **May be empty** — and is empty for every event the current producer emits: community-service has no `@`-mention syntax and no username→accountId directory, so nothing populates it yet. The field is on the wire so a future mention-resolution increment is a producer-only change. |

**Rollout tolerance (consumer requirement).** Events emitted *before* this
enrichment carry neither field. A consumer MUST treat an absent
`postAuthorAccountId` as "no addressable recipient → skip + log + dedupe" and an
absent `mentionedAccountIds` as an empty list — **never** as a malformed event
(no DLQ routing for this case).

Consumers:
- notification-service — `CommunityEventConsumer` → REPLY alert to
  `postAuthorAccountId` + MENTION alert per `mentionedAccountIds` entry
  (consumer group `notification-service-community-events`, TASK-FAN-BE-026).

## `community.reaction.added.v1`

Triggered only when a reaction is **created** (first PUT for a `(post,
reactor)` pair) or its **type changes** (e.g. `LIKE` → `LOVE`). A repeat
PUT with the same `(post, reactor, reactionType)` is a true no-op — neither
the DB row nor the outbox is touched, so consumers do not see a stream of
distinct `eventId`s for what is the same logical interaction. Consumers
SHOULD additionally dedupe by `eventId` (idempotency safety net) but with
this trigger semantics the duplicate volume is bounded by genuine
type-change activity.

```json
{
  "postId":               "<UUID>",
  "tenantId":             "fan-platform",
  "reactorAccountId":     "<UUID>",
  "postAuthorAccountId":  "<UUID>",
  "reactionType":         "LIKE | LOVE | FIRE | SAD",
  "occurredAt":           "ISO-8601 UTC"
}
```

### Recipient-routing field (TASK-FAN-BE-026, additive)

| Field | Type | Semantics |
|---|---|---|
| `postAuthorAccountId` | string (UUID) | the **post**'s author = the interaction-badge recipient. Equal to `reactorAccountId` when a user reacts to their own post (consumers suppress the self-notify). |

Same additive-compatibility and rollout-tolerance rules as
`community.comment.added.v1` above: same topic, same `schemaVersion`, nothing
renamed or removed; a consumer reading a pre-enrichment in-flight event MUST skip
(log + dedupe), not DLQ.

Consumers:
- notification-service — `CommunityEventConsumer` → REACTION_BADGE alert to
  `postAuthorAccountId` (consumer group `notification-service-community-events`,
  TASK-FAN-BE-026).
- analytics (planned).

---

## Failure handling

- Outbox INSERT shares the business transaction. If the business write rolls back, no event is enqueued.
- Kafka publish failure → row stays `status=PENDING`; retried each tick. Metric `community_outbox_publish_failures_total` increments.
- DLQ (v2): rows stuck PENDING > 1h → moved to a `outbox_dead_letter` table + operator alert.
