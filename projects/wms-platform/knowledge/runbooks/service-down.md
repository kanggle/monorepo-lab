# Runbook — ServiceDown

**Alert**: `ServiceDown` (`severity: critical`) in `infra/prometheus/alert-rules.yml`.

## 1. What it means

- Expression: `up == 0`, `for: 2m`. `up` is Prometheus's own per-target series: 0 means the last
  scrape of that target failed (connection refused, timeout, non-2xx).
- The `job` label names the target. Jobs and host ports come from `infra/prometheus/prometheus.yml`:
  gateway-service 8080, master-service 8081, inbound-service 8082, inventory-service 8083,
  outbound-service 8084, notification-service 8085, admin-service 8086 (all
  `host.docker.internal:<port>/actuator/prometheus`), plus the `prometheus` job itself.
- `up` is produced by Prometheus, not by a wms class. It only says "the scrape failed" — the
  process may be dead, or alive but not answering `/actuator/prometheus`.

## 2. First checks

The wms services run as host JVM processes, not containers (`docker-compose.yml` header).

```bash
# Which targets are down, and the scrape error
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/targets?state=active'

# The alert's own series
docker exec wms-prometheus wget -qO- 'http://localhost:9090/api/v1/query?query=up%3D%3D0'

# Is the service itself answering? (replace 8081 with the port of the failing job)
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/actuator/health
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8081/actuator/prometheus

# Is the infrastructure up?
docker compose -f projects/wms-platform/docker-compose.yml ps
```

`/actuator/health` and `/actuator/prometheus` are exposed (`management.endpoints.web.exposure.include:
health,info,prometheus` in each service's `application.yml`; gateway-service and master-service
confirmed, the others not individually checked).

## 3. Likely causes

| Cause | How to confirm |
|---|---|
| Service process not running / crashed | `curl` above gets connection refused; the `bootRun` terminal (or wherever the process's stdout goes) shows the stack trace. Log location for a deployed process: not verified. |
| Backing container down (Postgres, Kafka, Redis) so the service failed to start | `docker compose -f projects/wms-platform/docker-compose.yml ps` shows `wms-postgres` / `wms-kafka` / `wms-redis` not healthy; service log shows the connection error. |
| Scrape path broken, service fine | `curl http://localhost:<port>/actuator/prometheus` works but `/api/v1/targets` shows an error for the job — usually `host.docker.internal` not resolving from `wms-prometheus` (Linux hosts). Not verified on this stack. |
| Prometheus config drifted from the service port | The port in `prometheus.yml` differs from the service's `SERVER_PORT` (default in each `application.yml`). |

## 4. Recover / mitigate

```bash
# Restart infrastructure that is down
docker compose -f projects/wms-platform/docker-compose.yml up -d

# Restart the service (host process)
./gradlew :apps:<service>:bootRun      # e.g. :apps:master-service:bootRun
```

Confirm with `up` returning 1 for the job (query `up` instead of `up%3D%3D0`) and the alert
resolving after the next evaluation. A service that came back after a Kafka outage may also trip the
outbox alerts — see [`wms-outbox-backlog-high.md`](wms-outbox-backlog-high.md).

## 5. Escalate

- Service restarts but dies again in under a minute: capture the stack trace and hand it to the
  service owner; do not loop restarts.
- gateway-service down means every external request fails — treat as the highest priority.
- Deployed (non-local) environments: the process supervisor and log location are not documented in
  this repo — not verified.
