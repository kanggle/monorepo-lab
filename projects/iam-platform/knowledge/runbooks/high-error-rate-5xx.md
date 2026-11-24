# Runbook — HighErrorRate5xx

Assumptions: [`README.md`](README.md) § Environment.

## 1. What it means

For one `service` label: 5xx responses / all responses, both `rate(...[5m])`, above 0.05 for 5
minutes, severity `critical`. Metric `http_server_requests_seconds_count` (labels `status`, `uri`,
`method`, `outcome`, …) is Spring Boot's built-in HTTP server metric, not a class in this repo. The
`service` label comes from the scrape job (`prometheus.yml`: `service: gateway-service` …).

The expression has **no minimum-traffic guard**: a service taking 3 requests per minute, one of
them a 500, crosses 5%. Look at the absolute rate before reacting (§ 2, second query).

## 2. First checks

```bash
# Which service, which route, which status
docker exec iam-prometheus wget -qO- --post-data='query=sum by (service,uri,status) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))' http://localhost:9090/api/v1/query

# Total request rate of the same services (is this a handful of requests?)
docker exec iam-prometheus wget -qO- --post-data='query=sum by (service) (rate(http_server_requests_seconds_count[5m]))' http://localhost:9090/api/v1/query

# Health of every service (ports: gateway 8080, auth 8081, account 8082, security 8084, admin 8085)
curl -s http://localhost:8080/actuator/health
```

Logs: the terminal running that service's `bootRun` (apps are not containers — README). Search the
window of the alert for `ERROR`. Backing services:

```bash
docker compose -f projects/iam-platform/docker-compose.yml ps
```

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| A downstream service is down or slow and the **gateway** is the one answering 5xx | `service="gateway-service"` in the first query; `curl -s http://localhost:8080/actuator/health` — `UpstreamHealthIndicator` reports `WARNING` plus per-route `UP`/`DOWN` (`gateway-service/.../route/UpstreamHealthIndicator.java`; details are hidden when `show-details: never`, so otherwise read each service's own `/actuator/health`). Connection failures to a route surface as 503 via `GatewayErrorConfig` (comment in `JwtAuthenticationFilter.java:152`) |
| MySQL unavailable or exhausted | `iam-mysql` not `healthy` in `ps`; app log has Hikari / `CannotGetJdbcConnection` |
| Kafka unavailable and the service writes synchronously to it | `iam-kafka` not `healthy`; for outbox-backed services this shows as outbox lag instead — see the two outbox runbooks |
| **admin-service** calling a failing downstream | `curl -s http://localhost:8085/actuator/circuitbreakers` lists the circuit breakers (exposed, `admin-service/.../application.yml:54`); an open circuit answers `503 CIRCUIT_OPEN` (comment at `application.yml:263-266`) |
| A bad deploy / one broken endpoint | `uri` label of the first query is a single route; the log has the same exception repeating |
| Redis down | `iam-redis` not `healthy`; gateway rate-limit and token-invalidation checks fail **open** (`RATE_LIMIT_FAIL_OPEN` defaults to `true`, `application.yml:127`; `JwtAuthenticationFilter.java:229`), so Redis alone does not make the gateway answer 5xx |

## 4. Recover / mitigate

- Restore the failing dependency first (`docker compose -f projects/iam-platform/docker-compose.yml up -d mysql redis kafka`), then
  restart the affected app only if it does not recover by itself.
- A single bad endpoint after a change: roll the change back; the runbook does not know your branch.
- Confirm: re-run the first query; the ratio expression
  `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service) / sum(rate(http_server_requests_seconds_count[5m])) by (service)`
  falls under 0.05, and the alert leaves `/api/v1/alerts`.

## 5. Escalate

- Cause is a dependency you cannot restart, or the same exception persists after a restart: hand to
  the owner of that service with the `uri`, `status` and one stack trace.
- `service="auth-service"` or `"gateway-service"` at 5xx stops every sign-in and every API call —
  say so when you escalate.
