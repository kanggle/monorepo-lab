# artist-events — Kafka contract

> Event family produced by `fan-platform/apps/artist-service`. All events
> follow the platform envelope (`platform/event-driven-policy.md`) and inherit
> the shared shape from `libs:java-messaging`'s `BaseEventPublisher`.

---

## Common envelope

Each Kafka message body is the JSON envelope below. The Kafka topic name is
`<eventType>.v1` (the `.v1` suffix lives on the topic only — the envelope's
`eventType` field stays unsuffixed for forward compatibility).

```json
{
  "eventId":       "string (UUID v7 per [TASK-MONO-025](../../../../../tasks/done/TASK-MONO-025-base-event-publisher-uuidv7.md) — 머지 완료, libs/java-messaging BaseEventPublisher 이 UuidV7.randomString() 발급)",
  "eventType":     "string (e.g. artist.published)",
  "source":        "fan-platform-artist-service",
  "occurredAt":    "string (ISO-8601 UTC)",
  "schemaVersion": 1,
  "partitionKey":  "string (= aggregate id, used as Kafka key)",
  "payload":       { ... }
}
```

Consumers:
- MUST dedupe on `eventId` (consumer-side processed-events table or
  natural-key upsert).
- MUST treat unknown payload fields as additive (forward-compatible).
- SHOULD process per-partition serially — partition key is the aggregate id,
  guaranteeing per-aggregate ordering.

Topic versioning: a breaking payload change publishes on `<eventType>.v2`
with a coexistence period. v1 is the only published version today.

DLQ: per `platform/event-driven-policy.md`, each topic has a `<topic>.dlq`
companion. The artist-service producer does NOT write to DLQs (it retries
indefinitely with exponential backoff and surfaces failures via
`artist_outbox_publish_failures_total`); DLQs apply on the consumer side.

---

## Topics

### `artist.registered.v1`

Emitted when an admin registers a new artist (DRAFT state).

| `eventType` | `aggregateType` | `partitionKey` |
|---|---|---|
| `artist.registered` | `artist` | artist id |

Payload:
```json
{
  "aggregateId":  "0190f3e2-...",
  "tenantId":     "fan-platform",
  "artistType":   "SOLO | GROUP_MEMBER",
  "stageName":    "STAR-A",
  "registeredBy": "<admin account id>",
  "occurredAt":   "2026-05-03T00:00:00Z"
}
```

Consumers (planned):
- search-service (v2): index in DRAFT shard for admin search.
- audit pipeline: append to immutable log.

### `artist.published.v1`

Emitted on DRAFT → PUBLISHED transition. After this event the artist appears
in the public directory.

```json
{
  "aggregateId": "0190f3e2-...",
  "tenantId":    "fan-platform",
  "publishedAt": "2026-05-03T00:00:00Z"
}
```

Consumers (planned):
- search-service: re-index into the public directory shard.
- notification-service (v2): broadcast push to existing followers (when
  community-service emits `community.follow.added`).

### `artist.updated.v1`

Emitted when an admin updates one or more profile fields. The event lists
which fields actually changed (callers passing `null` for "no change" are
filtered out at the application service).

```json
{
  "aggregateId":   "0190f3e2-...",
  "tenantId":      "fan-platform",
  "changedFields": ["stageName", "agency"],
  "updatedBy":     "<admin account id>",
  "occurredAt":    "2026-05-03T00:00:00Z"
}
```

If no fields actually change (caller sent only nulls), no event is emitted.

Consumers (planned):
- search-service: re-index.

### `artist.archived.v1`

Emitted on `* → ARCHIVED` transition.

```json
{
  "aggregateId": "0190f3e2-...",
  "tenantId":    "fan-platform",
  "archivedAt":  "2026-05-03T00:00:00Z",
  "archivedBy":  "<admin account id>",
  "reason":      "string (optional, may be omitted)"
}
```

Consumers (planned):
- community-service: mark posts and follows referencing this artist as
  pointing to an archived target (display-side handles "no longer active").
- search-service: remove from the public directory shard.

### `artist.group_created.v1`

Emitted on group creation. Membership rows are NOT included — listen on
`artist.group_member_changed.v1` for those.

```json
{
  "aggregateId": "<group id>",
  "tenantId":    "fan-platform",
  "name":        "Group X",
  "debutDate":   "2020-01-01"
}
```

Consumers (planned):
- search-service: index group entity.

### `artist.group_member_changed.v1`

Emitted when a member is added or removed from a group.

```json
{
  "aggregateId": "<group id>",
  "tenantId":    "fan-platform",
  "artistId":    "<member artist id>",
  "role":        "LEADER | MEMBER | FORMER_MEMBER",
  "action":      "ADDED | REMOVED",
  "occurredAt":  "2026-05-03T00:00:00Z"
}
```

For `action=REMOVED`, `role=FORMER_MEMBER`. The event preserves the role at
removal time so consumers can render history without an extra lookup.

---

## Producer guarantees

- **Transactional outbox** — business state change and outbox INSERT share
  one DB transaction (matches `transactional.md` T3).
- **At-least-once** — broker failures cause `artist_outbox_publish_failures_total`
  to increment; the row stays PENDING and is retried on the next polling
  tick.
- **Per-aggregate ordering** — Kafka partition key = aggregate id (the
  artist or group id), so all events for one aggregate land in the same
  partition and arrive in commit order.

## Consumer guidance

- **Idempotency** — store `eventId` after successful processing; skip duplicates.
- **Schema evolution** — new payload fields are additive; consumers MUST not
  reject unknown fields.
- **Cross-aggregate ordering** — Kafka does NOT guarantee global order. If a
  consumer needs to relate two aggregates' events (e.g. group member change
  vs. artist archive), use `occurredAt` to resolve.
- **DLQ** — consumers handle their own retry + DLQ policy per
  `platform/event-driven-policy.md`. Recommended: 3 retries with exponential
  backoff, then DLQ to `<topic>.dlq`.
