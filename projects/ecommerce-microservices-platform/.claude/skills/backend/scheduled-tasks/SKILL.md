---
name: scheduled-tasks
description: Scheduled tasks, outbox polling, batch jobs
category: backend
---

# Skill: Scheduled Tasks

Patterns for recurring scheduled tasks and batch processing.

Prerequisite: read `specs/platform/event-driven-policy.md` before using this skill.

---

## Outbox Polling (Template Method)

Base class in `libs/java-messaging`. Each service extends and implements `resolveTopic()`.

```java
// libs/java-messaging
public abstract class OutboxPollingScheduler {

    private final OutboxPublisher outboxPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.polling.interval-ms:1000}")
    public void pollAndPublish() {
        List<OutboxEntry> entries = outboxPublisher.findPending();
        for (OutboxEntry entry : entries) {
            String topic = resolveTopic(entry.getEventType());
            try {
                kafkaTemplate.send(topic, entry.getAggregateId(), entry.getPayload());
                outboxPublisher.markPublished(entry.getId());
            } catch (Exception e) {
                onKafkaSendFailure(entry.getEventType(), entry.getAggregateId(), e);
            }
        }
    }

    protected abstract String resolveTopic(String eventType);

    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        // override for metrics
    }
}
```

### Service Implementation

```java
@Component
@Profile("!standalone")
public class OrderOutboxPollingScheduler extends OutboxPollingScheduler {

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "OrderPlaced"    -> "order.order.placed";
            case "OrderCancelled" -> "order.order.cancelled";
            default -> throw new IllegalArgumentException("Unknown: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        orderMetrics.recordEventPublishFailure(eventType);
    }
}
```

---

## Cleanup Scheduler

Periodic cleanup of processed event records.

```java
@Slf4j
@Component
@Profile("!standalone")
public class ProcessedEventCleanupScheduler {

    private final ProcessedEventRepository processedEventRepository;

    @Scheduled(cron = "0 0 3 * * *") // daily at 3 AM
    @Transactional
    public void cleanupOldEvents() {
        Instant cutoff = Instant.now().minus(Duration.ofDays(30));
        int deleted = processedEventRepository.deleteByProcessedAtBefore(cutoff);
        log.info("Cleaned up {} processed events older than {}", deleted, cutoff);
    }
}
```

---

## Domain Scheduler

Business-logic scheduled tasks (e.g., coupon expiration).

```java
@Slf4j
@Component
@Profile("!standalone")
public class CouponExpirationScheduler {

    @Scheduled(fixedDelay = 60000) // every 60 seconds
    @Transactional
    public void expireOverdueCoupons() {
        List<Coupon> expired = couponRepository.findExpiredButActive(Instant.now());
        for (Coupon coupon : expired) {
            coupon.expire();
            couponRepository.save(coupon);
            outboxPublisher.publish("Coupon", coupon.getId(), "CouponExpired", payload);
        }
    }
}
```

---

## Batch Job Execution Model

```java
public class BatchJobExecution {
    private UUID id;
    private String jobName;
    private BatchJobStatus status; // RUNNING, COMPLETED, FAILED
    private Instant startedAt;
    private Instant finishedAt;
    private String errorMessage;

    public static BatchJobExecution start(String jobName) { ... }
    public void complete() { this.status = COMPLETED; this.finishedAt = Instant.now(); }
    public void fail(String error) { this.status = FAILED; this.errorMessage = error; this.finishedAt = Instant.now(); }
}
```

---

## Scheduling Annotations

| Annotation | Use Case |
|---|---|
| `@Scheduled(fixedDelay = N)` | Run N ms after previous execution completes |
| `@Scheduled(fixedDelayString = "${prop}")` | Configurable delay from properties |
| `@Scheduled(cron = "...")` | Time-based scheduling (cleanup, batch) |

---

## Rules

- Always `@Profile("!standalone")` on schedulers that depend on Kafka/external services.
- Use `fixedDelay` (not `fixedRate`) to prevent overlap.
- Record metrics for failures in `onKafkaSendFailure()`.
- Batch operations should track execution status for observability.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| `fixedRate` causes overlapping executions | Use `fixedDelay` — waits for completion |
| Missing `@Profile("!standalone")` | Scheduler tries to connect to Kafka in standalone mode |
| No failure tracking | Record metrics or log errors for monitoring |
| Scheduler running in tests | Use `@ActiveProfiles("test")` or `@MockBean` for scheduler |
