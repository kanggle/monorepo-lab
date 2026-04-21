---
name: consumer-retry-dlq
description: Implement consumer retry and dead-letter queue
category: messaging
---

# Skill: Consumer Retry & DLQ

Patterns for Kafka consumer retry and dead-letter queue handling.

Prerequisite: read `specs/platform/event-driven-policy.md` before using this skill.

---

## Retry Configuration

Use Spring Kafka's `DefaultErrorHandler` with `ExponentialBackOff`, matching `specs/platform/event-driven-policy.md` Retry Policy (Base 1s, multiplier 2.0, max 30s, max 3 attempts).

```java
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@Profile("!standalone")
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        // Custom destination resolver: append ".dlq" instead of Spring's default ".DLT"
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, ex) -> new TopicPartition(record.topic() + ".dlq", record.partition())
        );

        // Exponential backoff per event-driven-policy.md Retry Policy
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxAttempts(3);

        return new DefaultErrorHandler(recoverer, backOff);
    }
}
```

---

## DLQ Topic Naming

Pattern: `{original-topic}.dlq`

Per `specs/platform/event-driven-policy.md`, DLQ topics use the `.dlq` suffix — **not** Spring Kafka's default `.DLT`. A custom destination resolver must be passed to `DeadLetterPublishingRecoverer` (see the configuration example above).

Examples:
- `order.order.placed` → `order.order.placed.dlq`
- `product.product.stock-changed` → `product.product.stock-changed.dlq`

---

## Consumer Error Handling

Guard against null payloads and deserialization errors.

```java
@KafkaListener(topics = "order.order.placed", groupId = "${spring.application.name}")
public void onMessage(@Payload String payload) {
    try {
        OrderPlacedEvent event = objectMapper.readValue(payload, OrderPlacedEvent.class);
        if (event.payload() == null) {
            log.warn("Null payload, skipping. eventId={}", event.eventId());
            return;
        }
        processEvent(event);
    } catch (JsonProcessingException e) {
        log.error("Failed to deserialize event, sending to DLQ", e);
        throw new RuntimeException("Deserialization failed", e);
    }
}
```

---

## Retry vs Skip vs DLQ

| Scenario | Behavior |
|---|---|
| Transient error (DB timeout, network) | Retry (up to max attempts) |
| Deserialization failure | Throw → DLQ after retries exhausted |
| Null payload | Log and skip (return without processing) |
| Business rule violation | Throw → DLQ after retries exhausted |
| Duplicate event (idempotent) | Skip (return without processing) |

---

## Testing DLQ Behavior

```java
@Test
@DisplayName("Malformed event is routed to DLQ after retries")
void malformedEvent_routedToDlq() {
    kafkaTemplate.send(topic, "invalid-json");

    await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
        ConsumerRecords<String, String> dlqRecords = pollDlqTopic();
        assertThat(dlqRecords.count()).isGreaterThanOrEqualTo(1);
    });
}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Catching all exceptions silently in consumer | Let retriable errors propagate to trigger retry |
| No DLQ configured | Always configure `DeadLetterPublishingRecoverer` |
| Infinite retries | Use bounded `ExponentialBackOff` with `setMaxAttempts(3)` |
| Retrying non-retriable errors | Skip or throw to DLQ for deserialization/validation errors |
