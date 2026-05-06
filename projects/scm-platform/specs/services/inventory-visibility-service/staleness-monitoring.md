# inventory-visibility-service — Staleness Monitoring

## batch-heavy Trait First Code

This document describes the staleness detection batch — the **first `batch-heavy` trait
code in scm-platform** (TASK-SCM-BE-003, 2026-05-07).

## Purpose

Each inventory node has a `lastEventAt` timestamp updated whenever a wms event is processed.
The staleness detection batch periodically evaluates all nodes and publishes `SNAPSHOT_STALE`
or `NODE_UNREACHABLE` alerts when a node has not reported events within the threshold.

## Threshold

Default: **600 seconds (10 minutes)** — configurable via:
```yaml
inventory-visibility:
  staleness:
    threshold-seconds: 600
```

This matches `staleness-monitoring.md` and `application.yml` (Acceptance Criteria #20).

## Batch Schedule

| Property | Value |
|---|---|
| Class | `StalenessDetectionScheduler` |
| Trigger | `@Scheduled(fixedDelay = 300000ms)` (every 5 minutes) |
| Initial delay | 60 seconds (allows service to fully start) |
| ShedLock name | `staleness-detection-batch` |
| lockAtMostFor | PT10M |
| lockAtLeastFor | PT4M |

ShedLock ensures only one replica in a clustered deployment runs the batch at a time
(JDBC lock provider backed by the `shedlock` table in Postgres).

## Staleness Status Transitions

```
FRESH ─── lastEventAt > now - threshold ──► FRESH (no change)
FRESH ─── lastEventAt < now - threshold ──► STALE  (alert published)
STALE ─── lastEventAt < now - threshold ──► STALE  (no re-alert)
STALE ─── event received ──────────────────► FRESH (via recordEventReceived)
null  ─── first check ─────────────────────► UNREACHABLE (alert published)
```

Alert is published **only on status transition** (FRESH→STALE, FRESH/STALE→UNREACHABLE).
This prevents alert flooding from repeated checks.

## Alert Dedup

Dedup logic: `NodeStaleness.evaluate()` returns `true` only on status change.
`StalenessDetectionScheduler` publishes only when `statusChanged = true`.
Max one alert per node per staleness transition.

## Alert Event

Topic: `scm.inventory.alert.v1`
Published by: `KafkaAlertPublisherAdapter` (best-effort, no outbox).
Alert loss: acceptable — next batch (5 minutes later) re-detects and re-publishes.

## Edge Cases

- **Kafka source down**: all nodes eventually go STALE → alert flood.
  Mitigation: alert dedup prevents per-check flood; best-effort publishing.
- **Batch lock stuck**: lockAtMostFor=PT10M auto-releases stuck lock.
- **Race with new event**: batch reads `last_event_at` → event arrives → batch marks STALE.
  Mitigation: `evaluate()` uses the staleness record's current `last_event_at`, which
  was just updated by the consumer within the same transaction window.
  If a race does occur, the next batch (5 minutes) corrects the status to FRESH.

## Consumer Group

`scm-inventory-visibility-v1` — matches `application.yml` and
`inventory-visibility-subscriptions.md` (Acceptance Criteria #19, #20).
