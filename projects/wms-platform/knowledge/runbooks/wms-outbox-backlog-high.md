# Runbook — WmsOutboxBacklogHigh

**Alert**: `WmsOutboxBacklogHigh` (`severity: warning`) in `infra/prometheus/alert-rules.yml`.

## 1. What it means

- Expression: `max by (service) (<svc>_outbox_pending_count) > 100`, OR-ed over six services,
  `for: 5m`. More than 100 outbox rows with `published_at IS NULL` for 5 minutes — the relay is
  stuck, slow, or Kafka is unreachable. 100 is the threshold the wms specs state
  (e.g. `specs/services/notification-service/external-integrations.md`).
- The gauge `<svc>.outbox.pending.count` counts `countByPublishedAtIsNull()` on the service's own
  outbox table. Exporters and tables:

| `service` label | Gauge registered in | Table (database) |
|---|---|---|
| master-service | `MasterOutboxPublisher` | `master_outbox` (`master_db`) |
| inbound-service | `inbound ... messaging.OutboxPublisher` | `inbound_outbox` (`inbound_db`) |
| inventory-service | `inventory ... messaging.OutboxPublisher` | `inventory_outbox` (`inventory_db`) |
| outbound-service | `outbound ... event.publisher.OutboxPublisher` | `outbound_outbox` (`outbound_db`) |
| notification-service | `OutboxPollingScheduler` | `notification_outbox` (`notification_db`) |
| admin-service | `AdminOutboxPublisher` | `admin_outbox` (`admin_db`) |

Publishers poll on a short fixed delay (admin / notification 500ms, master 1000ms by default; the
others not checked) and publish in batches (`batch-size` default 100); on a Kafka failure the row
stays unpublished and the publisher backs off 1s → 30s (admin / notification; shared
`AbstractOutboxPublisher` for the rest).

## 2. First checks

```bash
# Which service, how many
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=%7B__name__%3D~%22.%2B_outbox_pending_count%22%7D'

# Is Kafka up?
docker exec wms-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092 | head -1
docker compose -f projects/wms-platform/docker-compose.yml ps

# Confirm the backlog in the DB (swap database and table from the table above; this is admin)
docker exec wms-postgres psql -U postgres -d admin_db -c \
  "SELECT count(*) AS pending, min(created_at) AS oldest FROM admin_outbox WHERE published_at IS NULL;"
```

Column for "oldest": `created_at` for admin / inbound / inventory / outbound / notification
outbox tables, `occurred_at` for `master_outbox` (their Flyway migrations).

The publisher's own log lines (host process output) are `Outbox poll failed: ...`,
`Failed to publish outbox row ... : ...` and `Outbox publisher backing off <n>ms after <k>
consecutive failures` (`AdminOutboxPublisher` / `OutboxPollingScheduler` / `AbstractOutboxPublisher`).

## 3. Likely causes

| Cause | How to confirm |
|---|---|
| Kafka down or unreachable | `kafka-broker-api-versions.sh` above fails; `wms-kafka` not healthy; publisher log shows `Kafka send failed`. |
| Service process down, publisher not running | Gauge missing / `up == 0` — see [`service-down.md`](service-down.md). A down service stops the gauge, so this alert may not fire at all; check `ServiceDown`. |
| Burst of events faster than the relay | Pending count falls on its own over successive queries; no failure log lines. |
| Poison row (e.g. unsupported aggregate/event type) blocking the batch | Same `Failed to publish outbox row <id> (<event_type>)` repeating for the same id; publisher returns on the first failure, so later rows wait behind it. |
| Postgres slow/unreachable | `Outbox poll failed` in the log; `wms-postgres` health. |
| Service running with the `standalone` profile (no Kafka) | master / notification publishers are `@Profile("!standalone")`: rows are written but never drained by design. |

## 4. Recover / mitigate

- Kafka down: `docker compose -f projects/wms-platform/docker-compose.yml up -d kafka`. The publisher
  resumes by itself after backoff (≤ 30s); pending count should fall within a minute. Do not edit
  rows by hand.
- Poison row: fix the cause named in the log (e.g. add the topic mapping) and redeploy; the row is
  then published on the next poll. Marking rows `published_at` manually loses the event — do not do
  it without the event owner's agreement.
- notification-service rows that fail downstream delivery are a different path: see
  [`../../specs/services/notification-service/runbooks/dlt-replay.md`](../../specs/services/notification-service/runbooks/dlt-replay.md).
- Verify: re-run the pending query; the count must reach 0 (or its idle level) and the alert clears
  after the next evaluation.

## 5. Escalate

- Count keeps growing with Kafka healthy and no log errors: the service owner needs a thread dump of
  the publisher; capture the pending count and `oldest` first.
- Topic mapping / event contract questions: owner of the contract in `specs/contracts/events/`.
- Related alert: [`wms-outbox-publish-failing.md`](wms-outbox-publish-failing.md) (admin /
  notification only).
