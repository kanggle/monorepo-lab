---
name: outbox-pattern
description: Implement transactional outbox for reliable events
category: messaging
---

# Skill: Outbox Pattern

Patterns for reliable event publishing using the transactional outbox.

Prerequisite: read `platform/event-driven-policy.md` before using this skill.

---

## Outbox Table

```sql
CREATE TABLE outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(100)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         TEXT            NOT NULL,
    created_at      TIMESTAMP       NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMP,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_status_created ON outbox (status, created_at);
```

---

## Writing to Outbox

Write to the outbox table within the same transaction as the business operation.

```java
@Transactional
public PlaceOrderResult placeOrder(PlaceOrderCommand command) {
    Order order = Order.create(command);
    orderRepository.save(order);
    outboxPublisher.publish("Order", order.getOrderId(), "OrderPlaced", toPayload(order));
    return PlaceOrderResult.from(order);
}
```

```java
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
        String json = objectMapper.writeValueAsString(payload);
        OutboxEntry entry = new OutboxEntry(aggregateType, aggregateId, eventType, json);
        outboxRepository.save(entry);
    }
}
```

---

## Outbox Polling Scheduler

Polls pending entries and publishes to Kafka. Each service extends the base scheduler.

```java
@Slf4j
@Component
@Profile("!standalone")
public class OrderOutboxPollingScheduler extends OutboxPollingScheduler {

    public OrderOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                       KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "OrderPlaced"    -> "order.order.placed";
            case "OrderCancelled" -> "order.order.cancelled";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        // record metrics
    }
}
```

---

## Flow

```
1. Business operation + outbox write (same transaction) → COMMIT
2. Scheduler polls outbox (status = PENDING)
3. Scheduler sends to Kafka
4. On success: update status → PUBLISHED, set published_at
5. On failure: leave as PENDING, retry on next poll
```

---

## Rules

- Outbox write must be in the same transaction as the business operation.
- Scheduler runs outside transactions — Kafka send is not transactional.
- Use `@Scheduled(fixedDelayString = "...")` for polling interval.
- The base `OutboxPollingScheduler` lives in `libs/java-messaging`.
- Each service extends it and implements `resolveTopic()`.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Outbox write in a separate transaction | Must share the same `@Transactional` as the business op |
| Publishing directly to Kafka without outbox | Events can be lost if the app crashes after commit |
| No index on `(status, created_at)` | Polling query will be slow |
| Not handling serialization errors | Wrap `objectMapper.writeValueAsString` with proper error handling |
