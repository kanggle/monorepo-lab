---
name: outbox-pattern
description: Implement transactional outbox for reliable events
category: messaging
---

# Skill: Outbox Pattern

Patterns for reliable event publishing using the transactional outbox.

Prerequisite: read `platform/event-driven-policy.md` and
[`platform/shared-library-policy.md`](../../../../platform/shared-library-policy.md)
before using this skill.

The shared scaffolding lives in `libs/java-messaging` per
[ADR-MONO-004](../../../../docs/adr/ADR-MONO-004-shared-messaging-scaffolding.md) —
services import the generic publisher loop, envelope record, parser, dedupe
port, and MDC helpers. Service-specific code is limited to the row writer,
topic mapping, and `@Scheduled` wiring. See also `specs/contracts/events/`
for each project's per-event-family schema.

---

## Outbox Table

**V2 only — this is the only outbox shape in the repository today.** `TASK-MONO-312`
deleted the v1 classes (`OutboxPublisher`, `OutboxPollingScheduler`, `OutboxWriter`,
`OutboxJpaEntity`, `OutboxJpaRepository`) from `libs/java-messaging` after every
platform finished its v1→v2 migration (2026-06-27, ADR-MONO-004 §6 capstone). If you
see a service still extending `OutboxPollingScheduler`, that class no longer exists
in the shared library — treat it as leftover project-local code, not something this
skill teaches.

Each service owns its own outbox table; the column shape implements the shared
`com.example.messaging.outbox.OutboxRow` contract. Reference schema (the
`OutboxRowEntity` mapped superclass — extend it, or implement `OutboxRow` directly
on a richer entity if your schema needs extra columns):

```sql
CREATE TABLE <service>_outbox (
    event_id        UUID          PRIMARY KEY,
    event_type      VARCHAR(100)  NOT NULL,
    aggregate_type  VARCHAR(60)   NOT NULL,
    aggregate_id    VARCHAR(60)   NOT NULL,
    partition_key   VARCHAR(60),
    payload         TEXT          NOT NULL,
    occurred_at     TIMESTAMP     NOT NULL,
    published_at    TIMESTAMP,
    retries         INT           NOT NULL DEFAULT 0,
    last_error      TEXT
);
CREATE INDEX idx_<service>_outbox_pending
    ON <service>_outbox (occurred_at) WHERE published_at IS NULL;
```

---

## Writing to Outbox

Write to the outbox table within the same transaction as the business operation,
using your own row-writer against the concrete entity (there is no shared
`OutboxPublisher.publish(...)` convenience method — the shared class is the
*relay*, not the writer):

```java
@Transactional
public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
    Order order = Order.create(command);
    orderRepository.save(order);
    orderOutboxRepository.save(OrderOutboxEntity.create(
        order.getOrderId(), "Order", "OrderPlaced", toPayloadJson(order), clock.instant()));
    return PlaceOrderResult.from(order);
}
```

---

## Outbox Relay — `AbstractOutboxPublisher`

The shared `com.example.messaging.outbox.AbstractOutboxPublisher<R extends OutboxRow>`
(in `libs/java-messaging`) is the relay: it polls pending rows, publishes each to
Kafka, and marks it published after the broker ACK. Each service supplies its own
`OutboxRowRepository<R>`, `TopicResolver`, and `OutboxMetrics`, then schedules the
subclass:

```java
@Component
@Profile("!standalone")
public class OrderOutboxPublisher extends AbstractOutboxPublisher<OrderOutboxEntity> {

    public OrderOutboxPublisher(OrderOutboxRepository repository,
                                 KafkaTemplate<String, String> kafkaTemplate,
                                 TransactionTemplate transactionTemplate,
                                 TopicResolver topicResolver,
                                 OutboxMetrics metrics,
                                 Clock clock) {
        super(repository, kafkaTemplate, transactionTemplate, topicResolver, metrics, clock, /* batchSize */ 50);
    }
}

@Component
@Profile("!standalone")
public class OrderTopicResolver implements TopicResolver {
    @Override
    public String resolveTopic(String eventType) {
        return switch (eventType) {
            case "OrderPlaced"    -> orderPlacedTopic;    // resolve from config / project convention — see § Topic Naming below
            case "OrderCancelled" -> orderCancelledTopic;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}

@Component
@Profile("!standalone")
public class OrderOutboxScheduler {

    private final OrderOutboxPublisher publisher;

    @Scheduled(fixedDelayString = "${outbox.polling.interval-ms:1000}", scheduler = "outboxTaskScheduler")
    public void poll() {
        publisher.publishPending();
    }
}
```

`OutboxSchedulerConfig` (also in `libs/java-messaging`) provides the dedicated
`outboxTaskScheduler` bean — do not schedule outbox polling on Spring's default
`@Scheduled` pool (see the class Javadoc for why: orphaned threads across
Testcontainers context restarts, TASK-BE-077).

---

## Flow

```
1. Business operation + outbox write (same transaction) → COMMIT
2. AbstractOutboxPublisher.publishPending() polls rows where published_at IS NULL
3. Publisher sends each row to Kafka sequentially, in the row's resolved topic
4. On broker ACK: row is marked published (published_at = now) in a fresh transaction — NOT deleted
5. On failure: row stays unpublished; the publisher backs off exponentially
   (1s → 2s → 4s → 8s → ... capped at 30s) across ticks before the next attempt
```

**Rows are retained, not deleted, after a successful publish** — this is what the
shared `AbstractOutboxPublisher`/`OutboxRow.markPublished()` actually does, and it is
the only outbox implementation in this repository. `platform/event-driven-policy.md`
§ Producer Rules reads "rows are deleted from outbox only after broker
acknowledgment" — that sentence describes a different retention policy than the
shared library implements. This skill teaches what the library does, not the
literal spec sentence; the spec wording is a candidate for a follow-up correction
(out of scope here — see `TASK-MONO-413` Implementation Notes).

---

## Rules

- Outbox write must be in the same transaction as the business operation.
- The relay (`AbstractOutboxPublisher.publishPending()`) runs outside transactions for the Kafka send — only the row-mark-published step after ACK is transactional.
- Schedule polling on the dedicated `outboxTaskScheduler` bean (`OutboxSchedulerConfig`), not the default `@Scheduled` pool.
- Exponential backoff across failed ticks is built into `AbstractOutboxPublisher` — you do not need to implement your own; per `platform/event-driven-policy.md` § Producer Rules ("Publisher MUST retry broker failures with exponential backoff").
- Each service supplies `OutboxRowRepository`, `TopicResolver`, `OutboxMetrics` and extends `AbstractOutboxPublisher`.

---

## Topic Naming

The topic each `TopicResolver` resolves to is a project decision, not this skill's:
`platform/event-driven-policy.md` § Broker states the canonical RULE
(`{service|domain}.{aggregate}.{version}`, versioned). Check the project's own
`specs/contracts/events/README.md` for the shape actually declared and in use —
projects genuinely diverge here (confirmed by `TASK-MONO-415`'s cross-project
census), so do not assume any single example topic string is your project's
convention.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Outbox write in a separate transaction | Must share the same `@Transactional` as the business op |
| Publishing directly to Kafka without outbox | Events can be lost if the app crashes after commit |
| No index on `(occurred_at) WHERE published_at IS NULL` | Polling query will be slow |
| Not handling serialization errors | Wrap the row-writer's `objectMapper.writeValueAsString` with proper error handling |
| Scheduling on the default `@Scheduled` pool | Use the dedicated `outboxTaskScheduler` bean (`OutboxSchedulerConfig`) |
| Assuming rows are deleted after publish | They are marked `published_at`, never deleted — plan retention/archival separately if the table needs to stay small |
