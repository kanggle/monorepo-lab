---
name: event-implementation
description: Implement Kafka event publisher/consumer
category: messaging
---

# Skill: Event Implementation

Patterns for implementing domain events with Kafka in this repository.

Prerequisite: read `platform/event-driven-policy.md` and `specs/contracts/events/` for event schemas before using this skill.

---

## Reference Templates

| Purpose | File |
|---|---|
| Event envelope record | [`templates/EventEnvelope.java`](templates/EventEnvelope.java) |
| Port interface + Kafka adapter | [`templates/KafkaEventPublisher.java`](templates/KafkaEventPublisher.java) |
| Standalone no-op publisher | [`templates/NoopEventPublisher.java`](templates/NoopEventPublisher.java) |
| `@KafkaListener` consumer | [`templates/EventConsumer.java`](templates/EventConsumer.java) |

All templates use `Product*` / `Order*` as placeholders. Replace with your aggregate name and event types when copying.

---

## Architecture

- **Port** (application layer): `<Aggregate>EventPublisher` interface.
- **Adapter** (infrastructure layer): `Kafka<Aggregate>EventPublisher` — active when `!standalone`.
- **Standalone fallback**: `Noop<Aggregate>EventPublisher` — active when `standalone` profile is set.
- Both adapters implement the same port so application code is profile-agnostic.

---

## Topic Naming Convention

Pattern: `{source-service}.{aggregate}.{event-name}`

Examples:
- `product.product.created`
- `order.order.placed`
- `order.order.cancelled`

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Publishing event outside transaction | Use outbox pattern (see `messaging/outbox-pattern/SKILL.md`) |
| Missing `@Profile("!standalone")` on Kafka publisher | Standalone mode will fail without Kafka |
| Hardcoded group ID | Use `${spring.application.name}` for group ID |
| No null-payload guard in consumer | Always check `event.payload() != null` |
| Missing error metrics on publish failure | Record failure metrics for observability |
