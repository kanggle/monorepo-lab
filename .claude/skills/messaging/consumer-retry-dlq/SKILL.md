---
name: consumer-retry-dlq
description: Implement consumer retry and dead-letter queue
category: messaging
---

# Skill: Consumer Retry & DLQ

Patterns for Kafka consumer retry and dead-letter queue handling.

Prerequisite: read `platform/event-driven-policy.md` before using this skill. Concrete per-event retry overrides are declared in `specs/services/<service>/architecture.md` § Subscribed Topics.

---

## Retry Configuration

Use Spring Kafka's `DefaultErrorHandler` with `ExponentialBackOff`, matching `platform/event-driven-policy.md` § Retry Policy (Base 1s, multiplier 2.0, max 30s, max 3 attempts, **exponential with jitter**).

Spring's `ExponentialBackOff` does not add jitter on its own — the spec's "jitter" requirement is not satisfied by the class name alone. Wrap it with a small random component, and register the non-retryable exception types (deserialization failures, business-rule violations — see § Retry vs Skip vs DLQ below) so they skip the backoff loop entirely instead of consuming retry attempts before reaching the DLQ:

```java
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;
import org.springframework.util.backoff.ExponentialBackOff;
import java.util.concurrent.ThreadLocalRandom;

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

        // Exponential backoff per event-driven-policy.md Retry Policy, with jitter (±0-250ms)
        // layered on top — plain ExponentialBackOff has no jitter of its own.
        ExponentialBackOff base = new ExponentialBackOff(1000L, 2.0);
        base.setMaxInterval(30000L);
        base.setMaxAttempts(3);
        BackOff backOff = () -> {
            BackOffExecution delegate = base.start();
            return () -> {
                long next = delegate.nextBackOff();
                return next == BackOffExecution.STOP
                    ? next
                    : next + ThreadLocalRandom.current().nextLong(0, 250);
            };
        };

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Deserialization failures and business-rule violations are not transient — retrying
        // them replays the same failure. event-driven-policy.md § Error Classification requires
        // both go to the DLQ immediately, with zero retries consumed.
        handler.addNotRetryableExceptions(EventDeserializationException.class, BusinessRuleViolationException.class);
        return handler;
    }
}
```

---

## DLQ Topic Naming

Pattern: `{original-topic}.dlq`

Per `platform/event-driven-policy.md`, DLQ topics use the `.dlq` suffix — **not** Spring Kafka's default `.DLT`. A custom destination resolver must be passed to `DeadLetterPublishingRecoverer` (see the configuration example above).

The source topic's own naming is a project decision, not this skill's: `platform/event-driven-policy.md` § Broker states the canonical RULE (`{service|domain}.{aggregate}.{version}`, versioned); check the project's `specs/contracts/events/README.md` for the shape actually in use before wiring a listener — do not assume the unversioned examples below are correct for your project.

Examples (illustrative placeholders, not a real project's topic names):
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
        // EventDeserializationException is registered as non-retryable on the error handler
        // (see § Retry Configuration) — this goes straight to the DLQ, no retry attempts spent.
        log.error("Failed to deserialize event, sending to DLQ", e);
        throw new EventDeserializationException("Deserialization failed", e);
    }
}
```

`processEvent(event)` throws `BusinessRuleViolationException` (also registered non-retryable) when the handler rejects the event on business grounds — see § Retry vs Skip vs DLQ.

---

## Retry vs Skip vs DLQ

Per `platform/event-driven-policy.md` § Error Classification — retrying a deserialization
failure or a business-rule violation replays the exact same payload against the exact
same rejection, so both skip the backoff loop entirely (registered via
`addNotRetryableExceptions`, § Retry Configuration). Only genuinely transient and
unclassified failures spend retry budget.

| Scenario | Behavior |
|---|---|
| Transient error (DB timeout, network) | Retry (up to max attempts), then DLQ |
| Deserialization failure (unknown schema, malformed JSON) | **Immediate DLQ — zero retries** |
| Null payload | Log and skip (return without processing) |
| Business rule violation (handler rejects the event) | **Immediate DLQ — zero retries** |
| Unknown / unhandled exception | Retry, then DLQ after max retries |
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
| Retrying non-retriable errors | Register `EventDeserializationException` / `BusinessRuleViolationException` via `addNotRetryableExceptions` so they route straight to DLQ (`platform/event-driven-policy.md` § Error Classification) |
| Jitter-less exponential backoff | Plain `ExponentialBackOff` has none — wrap it (§ Retry Configuration) or the spec's "exponential with jitter" requirement is unmet |
