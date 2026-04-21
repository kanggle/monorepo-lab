# Service Type: Batch Job

Normative requirements for any service whose `Service Type` is `batch-job`.

This document extends the Core platform specs. It does not replace them.

---

# Scope

A `batch-job` service performs scheduled or one-shot execution of long-running work: ETL, reconciliation, periodic aggregation, cleanup, report generation, outbox dispatch.

Candidate services in this monorepo: `batch-worker`.

---

# Mandatory Requirements

## Schedule Ownership
- Every job MUST declare its schedule in `specs/services/<service>/architecture.md` under "Scheduled Jobs"
- Schedule format: cron expression with timezone explicit (e.g., `0 2 * * * Asia/Seoul`)
- Manual / on-demand jobs MUST also be declared with trigger source

## Restart Safety
- Every job MUST be safely re-runnable from any state
- Use one of:
  1. **Checkpointing**: persist last-processed cursor (`Spring Batch JobRepository` or custom table)
  2. **Idempotent steps**: each step is naturally idempotent (upsert, no side effects on re-run)
  3. **Compensation**: failed steps roll back via explicit compensating actions
- Crashed jobs MUST resume from the last checkpoint, not restart from scratch (unless data volume permits it)

## Concurrency Control
- Only one instance of a given job MUST run at a time
- Use a distributed lock (Redis, ShedLock, or DB advisory lock) keyed by job name
- Lock TTL MUST exceed the job's p99 expected duration

## Execution Control Plane
- Every job MUST expose an admin endpoint or CLI to:
  - Trigger manually
  - Inspect last execution status (success/failure, duration, processed count)
  - Cancel a running execution gracefully
- Jobs MUST NOT be triggered only via cron — operators need a way to recover from missed runs

## Observability
- Per-job metrics: `job_duration_seconds`, `job_processed_total`, `job_failed_total`, `job_last_success_timestamp`
- Alert when `job_last_success_timestamp` is older than `2 * scheduled_interval`
- Logs MUST include `jobName`, `executionId`, and per-step counters

## Resource Limits
- Set explicit memory and CPU `limits` (unlike `rest-api`, batch jobs may use CPU limits)
- Long-running jobs MUST tolerate pod eviction by checkpointing frequently

---

# Allowed Patterns

- Cron-scheduled execution
- Cursor-based reads from databases or APIs
- Chunked writes with commit per chunk
- Outbox polling and dispatch (already implemented in `messaging/outbox-pattern.md`)
- Reading from event streams as bounded historical replay

---

# Forbidden Patterns

- Holding a single transaction across the whole job — break into commit chunks
- Loading entire datasets into memory — always use cursor or paged reads
- Synchronous HTTP exposure for end users — promote to a `rest-api` service if needed
- Silent skip of failures — every failure must be logged with sufficient context to investigate

---

# Testing Requirements

- Unit tests for each step with mocked I/O
- Integration tests with Testcontainers for end-to-end execution against real DB
- Restart test: kill mid-execution, restart, verify resume from checkpoint
- Idempotency test: run twice, verify identical end state

---

# Default Skill Set

`backend/scheduled-tasks`, matched architecture skill, `database/transaction-boundary`, `cross-cutting/observability-setup`, `backend/testing-backend`, `testing/testcontainers`, `service-types/batch-job-setup`

---

# Acceptance for a New Batch Job Service

- [ ] `Scheduled Jobs` table in `architecture.md` lists every job, schedule, and trigger
- [ ] Distributed lock prevents concurrent execution
- [ ] Checkpointing or idempotency strategy implemented
- [ ] Manual trigger and status inspection endpoint or CLI
- [ ] Restart-safety test passes
- [ ] Last-success-timestamp alert configured
- [ ] Resource limits set
