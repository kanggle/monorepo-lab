# Runbook — AuthOutboxLagHigh

Assumptions: [`README.md`](README.md) § Environment. Sibling: `account-outbox-lag-high.md` (same
mechanism, other table).

## 1. What it means

`auth_outbox_lag_seconds > 60` for 5 minutes, severity `warning`.

Metric: gauge `auth_outbox_lag_seconds`, exported by
`auth-service/.../infrastructure/messaging/OutboxLagMetric.java`. Its value is the age in seconds of
the **oldest** row in `auth_db.auth_outbox` with `published_at IS NULL` (`LAG_SQL`, read on every
scrape), `0` when nothing is pending. Since TASK-BE-663 it reads the v2 table `auth_outbox`; before
2026-11-24 it read the empty v1 `outbox` and was `0` forever.

- Value **`-1`** means the gauge could not run its query (`DataAccessException`, logged
  `Failed to calculate auth outbox lag metric`). `-1` is below the threshold, so **the alert does not
  fire on it** — a gauge stuck at `-1` is a database problem hiding behind a quiet alert.
- A missing series means auth-service is not being scraped — `service-down.md`.

The row is published by `AuthOutboxPublisher` (`@Scheduled`, every `auth.outbox.poll-ms` = 1000 ms,
batch `auth.outbox.batch-size` = 100), a subclass of the shared
`libs/java-messaging/.../AbstractOutboxPublisher`: it sends each row to Kafka, waits up to 10 s for
the ACK, then stamps `published_at`. **On any failure it stops the batch** and backs off
1 s → 2 s → 4 s → 8 s → 30 s, so one row that cannot be published blocks every younger row behind it.

## 2. First checks

```bash
# The value now, and the companion gauge of unpublished rows
docker exec iam-prometheus wget -qO- --post-data='query=auth_outbox_lag_seconds' http://localhost:9090/api/v1/query
docker exec iam-prometheus wget -qO- --post-data='query=auth_outbox_pending_count' http://localhost:9090/api/v1/query

# The stuck rows: oldest first, by event type
docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" auth_db' <<'SQL'
SELECT event_type, COUNT(*) AS pending, MIN(created_at) AS oldest
FROM auth_outbox WHERE published_at IS NULL
GROUP BY event_type ORDER BY oldest;
SQL

# Is the broker up
docker compose -f projects/iam-platform/docker-compose.yml ps kafka
```

auth-service log (the `bootRun` terminal) — search for the relay's own lines:
`Failed to publish outbox row <id> (<event_type>): <message>` and `Outbox poll failed: <message>`.
Failure counters: `docker exec iam-prometheus wget -qO- --post-data='query={__name__=~"auth_outbox_publish.*"}' http://localhost:9090/api/v1/query`
(Micrometer series of `MicrometerOutboxMetrics`; their exact exported names were not verified).

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| Kafka down or not reachable | `ps` shows `iam-kafka` not `healthy`; log says `Kafka send failed for outbox row` / timeout |
| Wrong bootstrap address for the host app | `application.yml` defaults `KAFKA_BOOTSTRAP_SERVERS` to `localhost:9093`, while the committed overlay `docker-compose.bootrun.yml` publishes the broker on **39093**; without the overlay the broker has no host port. Log shows connection refused / metadata timeout from the first send |
| Target topic missing | `docker exec iam-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list` lacks the row's topic; `iam-kafka-init` creates them (`docker compose -f projects/iam-platform/docker-compose.yml logs kafka-init`) |
| A **poison row** blocks the queue: `event_type` that `AuthOutboxPublisher.topicFor` does not map throws `IllegalArgumentException: Unknown auth event type: …` on every attempt | The same `<id>` repeats in the `Failed to publish outbox row` log line, and that row is the oldest in the SQL above |
| MySQL slow or unavailable | gauge `-1`; `Outbox poll failed`; `iam-mysql` not `healthy` |
| auth-service is up but its scheduler is not running | Gauge climbing, **no** `Failed to publish` lines at all — **not verified how to distinguish further; escalate** |

## 4. Recover / mitigate

- Kafka / topic / address problems: fix them; the relay retries by itself within 30 s (backoff cap)
  and drains in created order. Lag falls as rows are stamped.
- Poison row: do **not** delete rows to "clear the alert" — `auth_outbox` is the only copy of that
  event and downstream consumers (security-service) will never see it. Hand the row id and
  `event_type` to the auth-service owner; the fix is code (a mapping) or a deliberate, reviewed row
  repair.
- Confirm: `auth_outbox_lag_seconds` back under 60 and the pending SQL above returns no rows older
  than a few seconds. Remember downstream: `security-consumer-lag-high.md` if lag moves there.

## 5. Escalate

- A poison row, or lag still rising after Kafka is healthy and the log shows no failures: the
  auth-service owner.
- Lag is also a delay of security detection (`auth.login.*` and `auth.token.reuse.detected` feed
  security-service) — say so if the backlog is large.
