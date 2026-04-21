---
name: idempotent-consumer
description: Implement idempotent event consumer
category: messaging
---

# Skill: Idempotent Consumer

Patterns for ensuring Kafka consumers process each event exactly once.

Prerequisite: read `specs/platform/event-driven-policy.md` before using this skill.

---

## Business Key Check (Preferred)

If the business operation naturally creates a unique record, check for its existence.

```java
@Transactional
public void processPayment(String orderId, String userId, long amount) {
    if (paymentRepository.findByOrderId(orderId).isPresent()) {
        log.info("Payment already exists for orderId={}, skipping", orderId);
        return;
    }
    Payment payment = Payment.create(orderId, userId, amount);
    paymentRepository.save(payment);
}
```

Best when: the business entity has a natural unique key tied to the event.

---

## Processed Event Table

Track processed event IDs when no natural unique key exists.

```sql
CREATE TABLE processed_events (
    event_id     UUID        PRIMARY KEY,
    processed_at TIMESTAMP   NOT NULL DEFAULT NOW()
);
```

```java
@Transactional
public void handleEvent(UUID eventId, Runnable action) {
    if (processedEventRepository.existsById(eventId)) {
        log.info("Event already processed: {}", eventId);
        return;
    }
    action.run();
    processedEventRepository.save(new ProcessedEvent(eventId));
}
```

Best when: multiple operations could result from one event.

---

## Database Constraints as Safety Net

Add unique constraints to prevent duplicates even if the application-level check fails.

```sql
ALTER TABLE payments ADD CONSTRAINT uq_payments_order_id UNIQUE (order_id);
```

```java
try {
    paymentRepository.save(payment);
} catch (DataIntegrityViolationException e) {
    log.info("Duplicate payment for orderId={}, ignoring", orderId);
}
```

---

## Testing Idempotency

```java
@Test
@DisplayName("Same event consumed twice produces only one result")
void duplicateEvent_processedOnce() {
    consumer.onMessage(eventPayload);
    assertThat(paymentRepository.findByOrderId(orderId)).isPresent();

    consumer.onMessage(eventPayload); // second consumption
    assertThat(paymentRepository.findAllByOrderId(orderId)).hasSize(1);
}
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| No idempotency check at all | Always check before processing — Kafka guarantees at-least-once |
| Check outside transaction | The check and the write must be in the same `@Transactional` |
| Using in-memory set for dedup | Use database — in-memory state is lost on restart |
| No unique constraint as safety net | Add DB constraint even if application checks exist |
