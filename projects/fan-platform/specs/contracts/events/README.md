# Event Contracts — fan-platform

Index of this project's event contracts and the project's declared choices for the five decisions [`platform/event-driven-policy.md`](../../../../../platform/event-driven-policy.md) delegates to `specs/contracts/events/README.md`. This file states what the code does today; it does not restate the platform rule body.

**Source of this census**: live code (outbox publisher adapters, `TOPIC_*`/`EVENT_*` constants, `@KafkaListener` consumers), read alongside the 3 contract files below, TASK-MONO-415 (2026-07-15).

---

## 1. Topic Naming

**Consistent** across all three event families: `<eventType>.v1` — the eventType string with a `.v1` suffix appended.

Examples: `artist.registered.v1`, `artist.published.v1`, `community.post.published.v1`, `community.comment.added.v1`, `fan.membership.activated.v1`, `fan.membership.canceled.v1`, `fan.membership.expired.v1`.

## 2. `eventType` Naming

**Consistent.** Dot-separated lowercase, family-prefixed: `artist.registered`, `artist.group_member_changed`, `community.post.status_changed`, `fan.membership.activated`. Values are byte-for-byte verified against `EVENT_*` string constants (`ArtistEventPublisherAdapter`, `CommunityEventPublisher`, `MembershipEventPublisher`), not just spec prose.

## 3. Serialization

**JSON**, via Jackson — each producer adapter builds a `LinkedHashMap` envelope and calls `objectMapper.writeValueAsString(envelope)`. Consumer side (`notification-service`) uses `StringDeserializer` + manual JSON parsing, not `JsonDeserializer<T>` or Avro. No `.avsc`/`.proto` files anywhere in the project.

## 4. Schema Registry

**Not used.** Zero hits for `schema.registry`, `SchemaRegistry`, `apicurio`, `Avro`, `.avsc`, `proto3`, `io.confluent` across the entire project.

## 5. Contract Index

| File | Producer / Type |
|---|---|
| `artist-events.md` | artist-service — producer. **No live consumers today** — every event in this family is marked "(planned)" in the spec, and no `@KafkaListener` for `artist.*` topics exists anywhere in `apps/`. |
| `community-events.md` | community-service — producer. **No live consumers today**, same as above ("(planned)" per spec, no listener in code). |
| `fan-membership-events.md` | membership-service — producer. **Consumed** — `MembershipEventConsumer` in notification-service (`@KafkaListener`) subscribes to all three `fan.membership.*` topics. |

---

## Notes

- **Produced-but-unconsumed events are real, not a gap in this README.** `artist.*` and `community.*` topics are actively published (outbox → Kafka) but have zero subscribers in the current codebase. The specs are honest about this ("planned"); flagging it here so a reader of this index doesn't assume a consumer exists.
- `artist-events.md`'s envelope example documents `partitionKey` as populated directly from the aggregate id. In the actual outbox row, `partition_key` is persisted as `null`, and the Kafka key is set via the relay's fallback to `aggregateId` — same wire behavior, different code path. Not a divergence in outcome, just a subtlety the spec text doesn't surface; noted here for anyone reading the code against the spec.

## Follow-up (not in scope for this README)

- None identified beyond the notes above — this project's conventions are internally consistent; no unification work is needed.
