# Runbook — HighLatencyP99

**Alert**: `HighLatencyP99` (`severity: warning`) in `infra/prometheus/alert-rules.yml`.

## 1. What it means

- Expression: `histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le,
  service)) > 1`, `for: 5m` — p99 HTTP server latency above 1 second.
- `http_server_requests_seconds_bucket` comes from the Spring Boot / Micrometer HTTP server
  instrumentation (framework, not a wms class). Spring Boot publishes its buckets only when
  histogram buckets are turned on (TASK-MONO-836; before that this alert was permanently no-data):
  gateway · master · inbound · inventory · outbound get them from `libs/java-observability`;
  admin-service and notification-service do not use that library and set
  `management.metrics.distribution.percentiles-histogram.http.server.requests: true` in their
  `application.yml`. If the query below returns nothing for a service, check that first.
- p99 over a low request rate is noisy; check the rate before acting.

## 2. First checks

```bash
# p99 per service (what the alert evaluates)
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=histogram_quantile(0.99%2Csum(rate(http_server_requests_seconds_bucket%5B5m%5D))%20by%20(le%2Cservice))'

# Slowest endpoints of one service (replace master-service with the alerting {{service}})
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=histogram_quantile(0.99%2Csum(rate(http_server_requests_seconds_bucket%7Bservice%3D%22master-service%22%7D%5B5m%5D))%20by%20(le%2Curi))'

# Dependencies
docker compose -f projects/wms-platform/docker-compose.yml ps
```

## 3. Likely causes

| Cause | How to confirm |
|---|---|
| Slow or blocked Postgres | Per-`uri` query shows only DB-touching endpoints slow; check for locks: `docker exec wms-postgres psql -U postgres -d master_db -c "SELECT pid, state, wait_event_type, now()-query_start AS age, left(query,80) FROM pg_stat_activity WHERE state <> 'idle' ORDER BY age DESC LIMIT 10;"` (use the service's own database: `master_db`, `inbound_db`, `inventory_db`, `outbound_db`, `admin_db`, `notification_db`). |
| Kafka/Redis dependency slow (synchronous call on the request path) | `docker compose ... ps` health; service log shows timeouts. Which endpoints call them synchronously: not verified. |
| Host resource pressure (JVM + all containers on one machine) | Host CPU / memory; not covered by any wms alert. |
| One expensive endpoint | Per-`uri` query isolates a single `uri`. |

## 4. Recover / mitigate

- Kill/terminate a runaway query found above: `docker exec wms-postgres psql -U postgres -d
  master_db -c "SELECT pg_terminate_backend(<pid>);"` with the `pid` from the previous output.
- Restart a degraded dependency container (`docker compose -f projects/wms-platform/docker-compose.yml
  restart postgres`) only if it is clearly unhealthy — it interrupts every service using it.
- The alert is `warning`; if p99 recovers by itself within the window no action beyond noting the
  `uri` is needed.

## 5. Escalate

- Persistent slow single endpoint: hand the `uri` and the p99 query result to the service owner.
- Latency accompanied by a rising 5xx ratio: switch to [`high-error-rate-5xx.md`](high-error-rate-5xx.md).
