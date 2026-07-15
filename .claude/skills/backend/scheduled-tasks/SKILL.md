---
name: scheduled-tasks
description: Scheduled tasks, outbox polling, batch jobs
category: backend
---

# Skill: Scheduled Tasks

Patterns for recurring scheduled tasks and batch processing.

Prerequisite: read `platform/event-driven-policy.md` before using this skill. Concrete outbox schema per service lives alongside `specs/services/<service>/architecture.md`; the shared V2 pattern is documented in `messaging/outbox-pattern/SKILL.md`.

---

## Outbox Polling

**This skill does not teach the outbox relay** — that base class
(`AbstractOutboxPublisher`, `libs/java-messaging`) and its scheduling wiring
(`OutboxSchedulerConfig`'s dedicated `outboxTaskScheduler` bean) are documented in
full in `messaging/outbox-pattern/SKILL.md` § Outbox Relay. Read that skill for the
current pattern; do not re-derive an outbox scheduler from `@Scheduled` primitives
here — the shared library already implements polling, exponential backoff, and
publish-then-mark-published, and a hand-rolled scheduler duplicates it without the
backoff.

(Historical note: an older `OutboxPollingScheduler` base class existed under
`libs/java-messaging` for the v1 outbox schema. `TASK-MONO-312` deleted it after
every project's v1→v2 migration completed — it is not part of the shared library
and does not exist to extend.)

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
            // Write the outbox row directly against your own row-writer/repository —
            // see messaging/outbox-pattern/SKILL.md § Writing to Outbox. There is no
            // shared `outboxPublisher.publish(...)` convenience method in the v2 API.
            couponOutboxRepository.save(CouponOutboxEntity.create(
                coupon.getId(), "Coupon", "CouponExpired", toPayloadJson(coupon), clock.instant()));
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
- Record metrics for failures (`OutboxMetrics` for the outbox relay — see `messaging/outbox-pattern/SKILL.md`; your own meter for domain schedulers).
- Batch operations should track execution status for observability.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| `fixedRate` causes overlapping executions | Use `fixedDelay` — waits for completion |
| Missing `@Profile("!standalone")` | Scheduler tries to connect to Kafka in standalone mode |
| No failure tracking | Record metrics or log errors for monitoring |
| Re-implementing outbox polling with a hand-rolled `@Scheduled` method | Extend `AbstractOutboxPublisher` (`messaging/outbox-pattern/SKILL.md`) — it already has backoff and the mark-published transaction |
| Scheduler running in tests | Use `@ActiveProfiles("test")` or `@MockBean` for scheduler |
