# Runbook — SecurityConsumerLagHigh

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`kafka_consumer_fetch_manager_records_lag_max{service="security-service"} > 10000` for 10 minutes,
severity `critical` — on some partition, security-service is more than 10 000 records behind.

Metric: the Kafka client's own `records-lag-max`, which Micrometer's Kafka binder publishes as
`kafka_consumer_fetch_manager_records_lag_max`. **No class in this repo exports it**; whether the
binder is attached to security-service's consumers was **not verified**. Check it exists first:

```bash
docker exec iam-prometheus wget -qO- --post-data='query=kafka_consumer_fetch_manager_records_lag_max{service="security-service"}' http://localhost:9090/api/v1/query
```

Empty result: the alert is blind. Use the gauge security-service does export itself:
`kafka_consumer_lag{topic,group,partition}` (`SecurityMetricsConfig.refreshConsumerLag`, refreshed
every 10 s from the client's `records-lag` metric, group `security-service`).

What security-service consumes (`@KafkaListener` in `security-service/.../consumer/`), all in group
`security-service` (`application.yml:37`): `auth.login.attempted`, `auth.login.failed`,
`auth.login.succeeded`, `auth.token.refreshed`, `auth.token.reuse.detected`, `account.locked`, and
the account-deleted topic (`AccountDeletedAnonymizedConsumer`). Offsets are committed manually
(`enable-auto-commit: false`); first start reads from `earliest`. Main topics keep 7 days
(`docker-compose.yml:186`).

## 2. First checks

```bash
# Lag per topic/partition and which consumer owns it (CURRENT-OFFSET, LOG-END-OFFSET, LAG, CONSUMER-ID)
docker exec iam-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group security-service

# security-service's own per-partition gauge
docker exec iam-prometheus wget -qO- --post-data='query=sum by (topic) (kafka_consumer_lag{group="security-service"})' http://localhost:9090/api/v1/query

# Is it running, and is it processing or erroring
curl -s http://localhost:8084/actuator/health
```

`--describe` printing "has no active members" or empty `CONSUMER-ID` means no consumer is attached —
`service-down.md`. Logs: the `bootRun` terminal of security-service; look for `Sending to DLQ`
(`KafkaConsumerConfig`), `DLQ publish FAILED`, and rebalance messages.

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| security-service was down / restarting and is catching up | `--describe` shows members present, `LAG` falling between two runs a minute apart |
| Consumption stalled | `LAG` flat or rising, `CURRENT-OFFSET` not moving between runs; no log output |
| A producer burst (login storm / attack) | `LOG-END-OFFSET` growing fast on `auth.login.*`; see `auth-login-failure-spike.md` |
| Slow processing: Redis dedup (`security-service.dedup.redis-ttl-seconds`), MySQL writes, or the auto-lock HTTP call to account-service (up to `max-attempts` = 3, read timeout 10 s, backoff 200 ms doubling — `security.detection.auto-lock.*`). Whether that call runs on the consumer thread was not verified | `iam-redis` / `iam-mysql` not `healthy`; `security_auto_lock_failures_total` rising (`security-auto-lock-spike.md`); security log shows `Auto-lock attempt N failed` |
| Repeated failing records retried then parked | Many `Sending to DLQ` lines; the error handler retries 3 times with 1 s → 30 s backoff before the `.DLT` publish — see `security-dlq-depth-high.md` |
| `max.poll.interval.ms` (300 000) exceeded → group rebalances in a loop | Rebalance lines in the log and `CONSUMER-ID` changing between `--describe` runs |

## 4. Recover / mitigate

- Dependency slow or down: restore it (`docker compose -f projects/iam-platform/docker-compose.yml up -d mysql redis kafka`); the consumer
  resumes from its committed offset without losing records.
- Stalled consumer with healthy dependencies: restart security-service (the offsets are committed,
  nothing is replayed beyond the uncommitted batch; handlers are idempotent on event id —
  `RecordAccountLockHistoryUseCase`, unique `event_id` in `login_history`).
- Do **not** reset offsets to the end to "clear" lag: it skips detection of the skipped events
  (`platform/service-types/event-consumer.md` forbids resetting to latest).
- Confirm: `kafka_consumer_lag` summed per topic trends to 0 and `--describe` shows small `LAG`.

## 5. Escalate

- Lag still growing after a restart with healthy dependencies, or the same record failing repeatedly:
  the security-service owner, with the `--describe` output and one stack trace.
- While it lags, security detection (velocity, device, token-reuse locks) is behind — tell security.
