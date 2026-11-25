# Runbook — HighLatencyP99

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

`histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service)) > 1`
for 5 minutes, severity `warning`. The series is Spring Boot's built-in HTTP server timer, not a
class in this repo.

🔴 **The alert can only fire if `_bucket` series exist.** Spring Boot does not publish histogram
buckets for `http.server.requests` by default. `libs/java-observability`
(`ObservabilityAutoConfig.HttpServerRequestsHistogramConfig`, TASK-MONO-836) turns them on, and all
five iam services depend on that library — before 836 nothing did and this alert was permanently
no-data. A service that sets `management.metrics.distribution.percentiles-histogram.http.server.requests=false`
(or `...percentiles-histogram.all=false`) turns them back off. Check that the series exist:

```bash
docker exec iam-prometheus wget -qO- --post-data='query=count by (service) (http_server_requests_seconds_bucket)' http://localhost:9090/api/v1/query
```

An empty result means the alert is blind, not that latency is fine; use the mean instead (§ 2).

## 2. First checks

```bash
# Mean latency per service and route (works without buckets)
docker exec iam-prometheus wget -qO- --post-data='query=sum by (service,uri) (rate(http_server_requests_seconds_sum[5m])) / sum by (service,uri) (rate(http_server_requests_seconds_count[5m]))' http://localhost:9090/api/v1/query

# p99 per route, if buckets exist
docker exec iam-prometheus wget -qO- --post-data='query=histogram_quantile(0.99, sum by (le,service,uri) (rate(http_server_requests_seconds_bucket[5m])))' http://localhost:9090/api/v1/query
```

Dependencies: `docker compose -f projects/iam-platform/docker-compose.yml ps`. Logs are the `bootRun`
terminal of the slow service.

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| One slow route | `uri` in the queries above is a single value; the same route is slow in the log |
| Database slow or connections exhausted | Hikari metrics are framework metrics (`hikaricp_connections_pending`, `hikaricp_connections_active`): `docker exec iam-prometheus wget -qO- --post-data='query=hikaricp_connections_pending > 0' http://localhost:9090/api/v1/query` — presence of these series was not verified |
| A downstream HTTP call is slow (auth ↔ account, admin → auth/account/security, security → account) | Timeouts are configured per caller: admin `admin.downstream.read-timeout-ms` default 10000, security `security.detection.auto-lock.read-timeout-ms` default 10000, account → auth 15000 (`application.yml` of each service). A route whose latency sits at one of those numbers is waiting on a timeout |
| Password hashing under CPU pressure (auth-service login) | spec metric `auth_credential_verify_duration_seconds` (auth `observability.md`) — **no code exports it today**; check host CPU instead |
| Redis slow | gateway Redis timeout is 100 ms (`gateway-service application.yml:80`), admin 1 s; `docker exec iam-redis redis-cli -a redispass --latency-history -i 1` (Ctrl-C to stop) |

## 4. Recover / mitigate

- Remove the cause you confirmed: restore the slow dependency, or roll back the change that made one
  route slow.
- If the host is CPU- or memory-starved, stop unrelated containers/processes before restarting apps.
- Confirm: the p99 query (or the mean query when there are no buckets) is under 1 s for 5 minutes.

## 5. Escalate

- Latency high with no confirmed cause after the checks: hand to the service owner with the
  `service`, `uri`, the two query results, and whether `_bucket` series exist at all.
- If the buckets do not exist, file that gap against the alert (it is configuration work, not an
  incident) — do not silence the alert as "noisy".
