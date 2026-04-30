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

Polls pending entries and publishes to Kafka. The base scheduler in
`libs/java-messaging` is **configuration-driven** — services do not subclass it.
Topic mapping is loaded from `outbox.topic-mapping` via `OutboxProperties`
(`@ConfigurationProperties(prefix = "outbox")`).

```java
// libs/java-messaging — final, not extended by services
public class OutboxPollingScheduler {

    private final OutboxPublisher outboxPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<String, String> topicMapping;
    @Nullable
    private final OutboxFailureHandler failureHandler;

    @Value("${outbox.polling.interval-ms:1000}")
    private long intervalMs;

    public OutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ThreadPoolTaskScheduler outboxTaskScheduler,
                                  OutboxProperties outboxProperties,
                                  @Nullable OutboxFailureHandler failureHandler) { ... }

    @PostConstruct
    public void start() {
        scheduledFuture = taskScheduler.scheduleWithFixedDelay(
                this::pollAndPublish, Duration.ofMillis(intervalMs));
    }

    @PreDestroy
    public void stop() { /* cancel scheduledFuture */ }

    public void pollAndPublish() { /* publish PENDING via OutboxPublisher */ }

    // private — services do not override topic resolution.
    private String resolveTopic(String eventType) { ... }
}
```

### Service Configuration

Each service declares its `event_type → kafka_topic` mapping in
`application.yml` (no Java code required):

```yaml
outbox:
  polling:
    interval-ms: ${OUTBOX_POLLING_INTERVAL_MS:1000}
    batch-size:  ${OUTBOX_POLLING_BATCH_SIZE:50}
    enabled:     true   # default; set false to disable polling (e.g. tests)
  topic-mapping:
    OrderPlaced:    order.order.placed
    OrderCancelled: order.order.cancelled
```

`OutboxProperties` validates `topic-mapping` is `@NotEmpty` at startup so a
missing mapping fails fast.

### Failure Handler (Per-Service Metrics)

To record service-specific Micrometer metrics on Kafka send failures,
register an `OutboxFailureHandler` bean. The shared scheduler picks it up
via `ObjectProvider` (no compile-time Micrometer dep in `libs/java-messaging`):

```java
// apps/<service>-service/src/main/java/.../infrastructure/messaging/<Service>OutboxFailureHandlerConfig.java
@Configuration
class OrderOutboxFailureHandlerConfig {

    @Bean
    OutboxFailureHandler outboxFailureHandler(MeterRegistry meterRegistry) {
        return (eventType, aggregateId, e) ->
                meterRegistry.counter("order_outbox_publish_failures", "event_type", eventType).increment();
    }
}
```

The bean is optional. If absent, the scheduler simply logs the failure.

---

## Flow

```
1. Business operation + outbox write (same transaction) → COMMIT
2. Scheduler polls outbox (status = PENDING)
3. Scheduler resolves topic via outbox.topic-mapping
4. Scheduler sends to Kafka
5. On success: update status → PUBLISHED, set published_at
6. On failure: leave as PENDING, retry on next poll; OutboxFailureHandler invoked (if registered)
```

---

## Rules

- Outbox write must be in the same transaction as the business operation.
- Scheduler runs outside transactions — Kafka send is not transactional.
- Polling cadence is driven by `@PostConstruct` + `ThreadPoolTaskScheduler.scheduleWithFixedDelay` on the dedicated `outboxTaskScheduler` bean. Do not add `@Scheduled` annotations.
- The base `OutboxPollingScheduler` lives in `libs/java-messaging` and is **not subclassed** by services. Per-service behaviour is supplied via configuration (`outbox.topic-mapping`) and an optional `OutboxFailureHandler` bean.
- Topic mapping comes from `OutboxProperties` (`outbox.topic-mapping` in `application.yml`). Add the entry alongside any new event type.
- Set `outbox.polling.enabled=false` in tests/profiles that should not run the relay.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Outbox write in a separate transaction | Must share the same `@Transactional` as the business op |
| Publishing directly to Kafka without outbox | Events can be lost if the app crashes after commit |
| No index on `(status, created_at)` | Polling query will be slow |
| Not handling serialization errors | Wrap `objectMapper.writeValueAsString` with proper error handling |
| Subclassing `OutboxPollingScheduler` to add a topic | Add the mapping under `outbox.topic-mapping` in `application.yml` instead |
| Using `@Scheduled(fixedDelayString = ...)` to drive polling | Rely on the built-in `@PostConstruct` + `ThreadPoolTaskScheduler.scheduleWithFixedDelay` lifecycle |
| Missing `event_type` entry in `outbox.topic-mapping` | `OutboxProperties` `@NotEmpty` validation fails at startup; add the mapping before deploying the new event |
