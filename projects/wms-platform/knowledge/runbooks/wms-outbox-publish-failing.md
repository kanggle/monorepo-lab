# Runbook — WmsOutboxPublishFailing

**Alert**: `WmsOutboxPublishFailing` (`severity: warning`) in `infra/prometheus/alert-rules.yml`.

## 1. What it means

- Expression: `sum by (service) (rate(admin_outbox_publish_failure_total[5m])) > 0` OR the same for
  `notification_outbox_publish_failure_total`, `for: 10m` — failures in every minute of a 10-minute
  window. A single failure is retried with backoff (1s → 30s) and is not news.
- Counters (Micrometer `<prefix>.outbox.publish.failure.total`):
  - `admin.outbox.publish.failure.total` — `AdminOutboxPublisher` (admin-service)
  - `notification.outbox.publish.failure.total` — `OutboxPollingScheduler` (notification-service)
- The counter increments in `registerFailure()` on any of: Kafka send failed/timed out (10s ACK
  wait), the poll query failed, or another `RuntimeException` while publishing one row. It is **not**
  the count of unpublished rows — that is `WmsOutboxBacklogHigh`.
- The other four services (master, inbound, inventory, outbound) have a failure counter via the
  shared `AbstractOutboxPublisher` but this alert does not cover them: not verified that their
  metrics are scraped under the names this alert would need.

## 2. First checks

```bash
# Failure rate per service
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=rate(admin_outbox_publish_failure_total%5B5m%5D)'
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=rate(notification_outbox_publish_failure_total%5B5m%5D)'

# Kafka health
docker exec wms-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 | head -1
docker compose -f projects/wms-platform/docker-compose.yml ps

# Unpublished rows (admin shown; notification: -d notification_db, table notification_outbox)
docker exec wms-postgres psql -U postgres -d admin_db -c \
  "SELECT id, event_type, aggregate_type, created_at FROM admin_outbox WHERE published_at IS NULL ORDER BY created_at LIMIT 10;"
```

The failing reason is in the service's log (host process output):
`Failed to publish outbox row <id> (<event_type>): ...` or `Outbox poll failed: ...`, followed by
`Outbox publisher backing off <n>ms after <k> consecutive failures`.

## 3. Likely causes

| Cause | How to confirm |
|---|---|
| Kafka unreachable / broker unhealthy | Broker check above fails; log says `Kafka send failed for outbox row`. |
| Same row failing every time (poison row; admin: `Unsupported admin aggregate type`) | Same row `id` repeats in the log and is the oldest in the SQL above. The publisher stops at the first failing row, so rows behind it wait. |
| Postgres unreachable / slow | `Outbox poll failed:` in the log; `wms-postgres` health. |
| Topic missing or ACK timeout | `wms-kafka` topic list: `docker exec wms-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list | grep -E 'wms\.(admin|notification)'`. Local topics are created by `wms-kafka-init`; whether auto-create is on: not verified. |

## 4. Recover / mitigate

- Kafka: `docker compose -f projects/wms-platform/docker-compose.yml up -d kafka kafka-init`; the
  publisher recovers on its own after the next backoff, no restart needed.
- Poison row: fix the cause the log names and redeploy; do not mark the row `published_at` by hand
  (the event would be lost).
- Verify: failure rate query returns 0 and the unpublished-rows SQL returns no (or only new) rows.
- Notification delivery failures to Slack are not this alert — see
  [`../../specs/services/notification-service/runbooks/dlt-replay.md`](../../specs/services/notification-service/runbooks/dlt-replay.md).

## 5. Escalate

- Kafka healthy yet failures continue for the same row: service owner (admin-service or
  notification-service) with the row id, `event_type` and the full log line.
- Pair with [`wms-outbox-backlog-high.md`](wms-outbox-backlog-high.md) — a long-failing publisher
  usually trips it too.
