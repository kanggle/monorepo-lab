# Runbook — AccountOutboxLagHigh

Assumptions: [`README.md`](README.md) § Environment. Sibling: `auth-outbox-lag-high.md` (same
mechanism, other table) — read its § 1 for how the relay and its backoff work; only the differences
are written here.

## 1. What it means

`account_outbox_lag_seconds > 60` for 5 minutes, severity `warning`.

Metric: gauge `account_outbox_lag_seconds`, exported by
`account-service/.../infrastructure/outbox/OutboxLagMetric.java` (TASK-BE-662). Value = age in
seconds of the oldest row in `account_db.account_outbox` with `published_at IS NULL` (`LAG_SQL`), `0`
when nothing is pending, **`-1` when the query fails** (below the threshold, so no alert — a gauge
stuck at `-1` is a database problem; log line `Failed to calculate account outbox lag metric`).
The v1 table `outbox` still exists in `account_db` but is unused (`V0026__account_outbox_v2.sql`) —
do not read it.

Relay: `AccountOutboxPublisher` (every `account.outbox.poll-ms` = 1000 ms, batch
`account.outbox.batch-size` = 100; env `ACCOUNT_OUTBOX_POLL_MS`, `ACCOUNT_OUTBOX_BATCH_SIZE`),
same shared `AbstractOutboxPublisher`: stops the batch on the first failure, so one unpublishable
row blocks every younger row. Companion gauge `account_outbox_pending_count`.

Consumers of what it publishes (`account.created`, `account.status.changed`, `account.locked`,
`account.unlocked`, `account.roles.changed`, `account.deleted`, `tenant.subscription.changed`, …):
security-service and auth-service consume `account.locked` — a stuck outbox means a **locked
account keeps its sessions** until the event gets through. ecommerce `account.*` consumers also parse
these messages (comment in `V0026__account_outbox_v2.sql`).

## 2. First checks

```bash
docker exec iam-prometheus wget -qO- --post-data='query=account_outbox_lag_seconds' http://localhost:9090/api/v1/query
docker exec iam-prometheus wget -qO- --post-data='query=account_outbox_pending_count' http://localhost:9090/api/v1/query

docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" account_db' <<'SQL'
SELECT event_type, COUNT(*) AS pending, MIN(created_at) AS oldest
FROM account_outbox WHERE published_at IS NULL
GROUP BY event_type ORDER BY oldest;
SQL

docker compose -f projects/iam-platform/docker-compose.yml ps kafka
```

Log (the `bootRun` terminal of account-service): `Failed to publish outbox row <id> (<event_type>)`,
`Outbox poll failed`. If the series is absent entirely, account-service is not being scraped:
`service-down.md`. account-service only exposes `/actuator/prometheus` since TASK-BE-664
(2026-11-24); on an older build it answers 404 and this alert can never fire, so check
`curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8082/actuator/prometheus` (expect `200`)
before trusting any silence from it.

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| Kafka down / unreachable | `ps` shows `iam-kafka` not `healthy`; log has `Kafka send failed for outbox row` |
| Wrong bootstrap address | default `KAFKA_BOOTSTRAP_SERVERS` is `localhost:9093` (`application.yml`); the committed overlay publishes the broker on **39093** (`docker-compose.bootrun.yml`) |
| Topic missing | `docker exec iam-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` lacks the row's topic |
| Poison row: `AccountOutboxPublisher.topicFor` throws `IllegalArgumentException: Unknown account event type: …` | The same `<id>` repeats in the log and is the oldest row in the SQL above |
| MySQL unavailable | gauge `-1`; `iam-mysql` not `healthy` |

## 4. Recover / mitigate

Same as `auth-outbox-lag-high.md` § 4: fix Kafka / address / topic and the relay drains by itself
within its 30 s backoff cap; **never delete outbox rows to clear the alert** — `account_outbox` is
the only copy of the event; a poison row goes to the account-service owner with its `id` and
`event_type`. Confirm: `account_outbox_lag_seconds` under 60 and the SQL above returns nothing old.

## 5. Escalate

- Poison row, or rising lag with a healthy broker and no failure lines: the account-service owner.
- If `account.locked` is among the stuck event types, tell security: locks are not reaching
  auth-service / security-service yet.
