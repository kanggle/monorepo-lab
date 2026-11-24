# Runbook — HighErrorRate5xx

**Alert**: `HighErrorRate5xx` (`severity: critical`) in `infra/prometheus/alert-rules.yml`.

## 1. What it means

- Expression: `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)` divided
  by `sum(rate(http_server_requests_seconds_count[5m])) by (service)`, `> 0.05`, `for: 5m`.
- `http_server_requests_seconds_*` is the Spring Boot / Micrometer HTTP server metric — exported by
  the framework in every wms service, not by a wms class. The `service` label is attached by
  `prometheus.yml` per scrape job.
- A low-traffic service can cross 5% on a handful of requests; check the request rate first.

## 2. First checks

```bash
# Ratio per service (what the alert evaluates)
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=sum(rate(http_server_requests_seconds_count%7Bstatus%3D~%225..%22%7D%5B5m%5D))%20by%20(service)%20%2F%20sum(rate(http_server_requests_seconds_count%5B5m%5D))%20by%20(service)'

# Which endpoints and statuses (replace master-service with the alerting {{service}})
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=sum(rate(http_server_requests_seconds_count%7Bservice%3D%22master-service%22%2Cstatus%3D~%225..%22%7D%5B5m%5D))%20by%20(uri%2Cstatus)'

# Dependencies
docker compose -f projects/wms-platform/docker-compose.yml ps
curl -s http://localhost:8081/actuator/health      # port of the alerting service (8080-8086)
```

The services run on the host, so their logs are the `bootRun` process output; the stack trace of
the 5xx is there. Log location for deployed processes: not verified.

## 3. Likely causes

| Cause | How to confirm |
|---|---|
| Postgres unreachable / pool exhausted | `wms-postgres` not healthy in `ps`; service log shows connection / `HikariPool` errors. |
| Kafka or Redis unreachable (Redis is used for idempotency and rate limiting) | `wms-kafka` / `wms-redis` not healthy; errors naming them in the service log. |
| Upstream service failing (gateway-service returns 5xx when a routed service is down) | Run the per-`uri` query above for gateway-service, then check [`service-down.md`](service-down.md) for the routed service. |
| Bad deploy / bug on one endpoint | The per-`uri` query shows one `uri` carrying all the 5xx; the stack trace in the log names the class. |

## 4. Recover / mitigate

- Dependency down: bring it back (`docker compose -f projects/wms-platform/docker-compose.yml up -d`),
  then watch the ratio fall; the alert clears after the 5m window.
- Bad deploy: roll the service back to the previous build and restart (`./gradlew
  :apps:<service>:bootRun` from the previous revision locally). Deployed rollback procedure: not
  verified.
- If the 5xx come from outbox/Kafka problems, see
  [`wms-outbox-publish-failing.md`](wms-outbox-publish-failing.md).

## 5. Escalate

- No dependency is down and one endpoint keeps throwing: hand the stack trace and the `uri` to the
  service owner.
- gateway-service ratio high with healthy downstreams: gateway configuration issue — escalate to the
  gateway owner.
