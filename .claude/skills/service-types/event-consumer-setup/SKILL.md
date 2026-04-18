---
name: event-consumer-setup
description: Set up an `event-consumer` service end-to-end
category: service-types
---

# Skill: Event Consumer Service Setup

Implementation orchestration for an `event-consumer` service. Composes existing messaging skills into a setup workflow.

Prerequisite: read `platform/service-types/event-consumer.md` before using this skill.

---

## Orchestration Order

1. **Subscribed topics** — declare in `specs/services/<service>/architecture.md` under "Subscribed Topics"
2. **Producer contracts** — read `specs/contracts/events/<producer>-events.md` for each consumed topic
3. **Architecture style** — pick from `backend/architecture/`
4. **Consumer wiring** — `messaging/event-implementation/SKILL.md`
5. **Idempotency** — `messaging/idempotent-consumer/SKILL.md`
6. **Retry / DLQ** — `messaging/consumer-retry-dlq/SKILL.md`
7. **Schema versioning** — branch on `schemaVersion` per `cross-cutting/api-versioning/SKILL.md`
8. **Observability** — `cross-cutting/observability-setup/SKILL.md` plus consumer-specific metrics below
9. **Tests** — `backend/testing-backend/SKILL.md`, `testing/testcontainers/SKILL.md` with Kafka

---

## Consumer Group Naming

Format: `<service>-<purpose>`

```
example-consumer-order-events
example-consumer-user-events
example-indexer-product-catalog
```

One consumer group per (service, purpose) pair. Never share a group across services.

---

## Idempotency Wiring

Choose the cheapest sufficient strategy:

| Strategy | When | Skill |
|---|---|---|
| Natural (upsert by event-derived key) | Read model materialization | `messaging/idempotent-consumer/SKILL.md` §natural |
| Idempotency table by `eventId` | Side-effect-producing handlers (email, webhook) | `messaging/idempotent-consumer/SKILL.md` §idempotency-table |
| Optimistic concurrency on aggregate | Updates to versioned aggregates | `messaging/idempotent-consumer/SKILL.md` §version-check |

---

## Retry / DLQ Wiring

```java
@KafkaListener(
    topics = "order.events.v1",
    groupId = "example-consumer-order-events",
    containerFactory = "retryableKafkaListenerContainerFactory"
)
public void onOrderEvent(OrderEvent event, Acknowledgment ack) {
    handler.handle(event);
    ack.acknowledge();
}
```

The container factory wires:
- 3 in-process retries with exponential backoff + jitter
- After exhaustion: send to `<topic>.dlq` with original payload + failure cause headers
- Manual acknowledgment to avoid losing in-flight events on rebalance

See `messaging/consumer-retry-dlq/SKILL.md` for the full container factory bean.

---

## Observability Specifics

Required metrics beyond the standard set:

| Metric | Type | Labels |
|---|---|---|
| `kafka_consumer_lag` | gauge | topic, group |
| `event_handler_duration_seconds` | histogram | handler |
| `event_handler_errors_total` | counter | handler, error_type |
| `dlq_depth` | gauge | dlq_topic |

Required alerts:
- Consumer lag > 1 minute for 5 minutes
- DLQ depth > 0
- Handler error rate > 1% over 5 minutes

---

## Test Pattern

```java
@SpringBootTest
@Testcontainers
class OrderEventConsumerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Test
    void duplicateDeliveryProducesSingleSideEffect() {
        OrderPlacedEvent event = givenOrderEvent();

        producer.send(event);
        producer.send(event); // duplicate

        await().untilAsserted(() ->
            assertThat(notificationRepository.findByOrderId(event.orderId())).hasSize(1)
        );
    }

    @Test
    void persistentFailureRoutesToDlq() {
        OrderPlacedEvent event = givenEventThatHandlerRejects();

        producer.send(event);

        await().untilAsserted(() ->
            assertThat(dlqConsumer.received("order.events.v1.dlq")).hasSize(1)
        );
    }
}
```

---

## Self-Review Checklist

Verify against `platform/service-types/event-consumer.md` Acceptance section. Specifically:

- [ ] Every subscribed topic listed in architecture.md with consumer group name
- [ ] Idempotency strategy implemented and tested with duplicate delivery
- [ ] DLQ tested with persistent-failure case
- [ ] Schema-version branching present (or explicitly noted as not yet needed)
- [ ] OTel propagation verified with a producer→consumer trace span
- [ ] Lag and DLQ alerts configured
