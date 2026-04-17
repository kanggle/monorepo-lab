---
name: batch-job-setup
description: Set up a `batch-job` service end-to-end
category: service-types
---

# Skill: Batch Job Service Setup

Implementation orchestration for a `batch-job` service. Composes existing skills into a setup workflow.

Prerequisite: read `platform/service-types/batch-job.md` before using this skill.

---

## Orchestration Order

1. **Job catalog** — declare every job in `architecture.md` under "Scheduled Jobs"
2. **Architecture style** — typically `layered` for batch
3. **Scheduler** — `backend/scheduled-tasks/SKILL.md`
4. **Concurrency lock** — see "Distributed Lock" below
5. **Cursor / checkpoint** — see "Restart Safety" below
6. **Transaction boundaries** — `database/transaction-boundary/SKILL.md` (commit per chunk, not per job)
7. **Admin endpoint or CLI** — see "Operator Control" below
8. **Observability** — `cross-cutting/observability-setup/SKILL.md` plus job-specific metrics
9. **Tests** — restart-safety test, idempotency test, integration test with Testcontainers

---

## Job Declaration

In `specs/services/<service>/architecture.md`:

```markdown
## Scheduled Jobs

| Job | Schedule | Trigger Source | Owner | Idempotent |
|---|---|---|---|---|
| outbox-dispatch | every 1m | cron | platform | yes (event id) |
| order-reconciliation | 0 2 * * * Asia/Seoul | cron + manual | orders | yes (date cursor) |
| stale-cart-cleanup | 0 3 * * * Asia/Seoul | cron + manual | growth | yes (upsert delete) |
```

---

## Distributed Lock

Use ShedLock or a Redis-based lock:

```java
@Scheduled(cron = "0 2 * * * Asia/Seoul")
@SchedulerLock(
    name = "orderReconciliation",
    lockAtMostFor = "PT2H",
    lockAtLeastFor = "PT1M"
)
public void runOrderReconciliation() {
    orderReconciliationJob.run();
}
```

`lockAtMostFor` MUST exceed the job's p99 expected duration. `lockAtLeastFor` prevents rapid re-trigger after a fast-failing run.

---

## Restart Safety Pattern

### Checkpoint table

```sql
CREATE TABLE batch_job_checkpoint (
    job_name VARCHAR(128) PRIMARY KEY,
    last_processed_cursor VARCHAR(255) NOT NULL,
    last_run_at TIMESTAMPTZ NOT NULL,
    last_run_status VARCHAR(32) NOT NULL
);
```

### Cursor-based read + chunked commit

```java
@Transactional   // per chunk, not per job
public void processChunk() {
    String cursor = checkpointRepository.findCursor("orderReconciliation");
    List<Order> chunk = orderRepository.findAfterCursor(cursor, CHUNK_SIZE);
    if (chunk.isEmpty()) return;

    chunk.forEach(this::reconcile);
    checkpointRepository.save("orderReconciliation", chunk.last().id(), Instant.now(), "RUNNING");
}
```

The job loop calls `processChunk()` until it returns no rows. Crashes resume from the last persisted cursor.

---

## Operator Control

Expose admin endpoints (gated by admin role) for every job:

```java
@RestController
@RequestMapping("/admin/jobs")
@PreAuthorize("hasRole('ADMIN')")
class JobAdminController {

    @PostMapping("/{jobName}/trigger")
    public JobExecutionResponse trigger(@PathVariable String jobName) {
        return jobLauncher.triggerManually(jobName);
    }

    @GetMapping("/{jobName}/status")
    public JobStatusResponse status(@PathVariable String jobName) {
        return jobStatusReader.read(jobName);
    }

    @PostMapping("/{jobName}/cancel")
    public void cancel(@PathVariable String jobName) {
        jobLauncher.requestCancel(jobName);
    }
}
```

---

## Observability Specifics

Required metrics:

| Metric | Type | Labels |
|---|---|---|
| `job_duration_seconds` | histogram | job_name |
| `job_processed_total` | counter | job_name |
| `job_failed_total` | counter | job_name, error_type |
| `job_last_success_timestamp_seconds` | gauge | job_name |

Alert: `time() - job_last_success_timestamp_seconds > 2 * scheduled_interval`.

---

## Self-Review Checklist

Verify against `platform/service-types/batch-job.md` Acceptance section. Specifically:

- [ ] Every job listed in `architecture.md` "Scheduled Jobs" table
- [ ] Distributed lock prevents concurrent execution (tested with two instances)
- [ ] Restart-safety verified by killing mid-execution
- [ ] Idempotency verified by running the job twice on the same data
- [ ] Admin trigger and status endpoints tested
- [ ] Last-success-timestamp alert configured
- [ ] Resource limits set (CPU + memory)
