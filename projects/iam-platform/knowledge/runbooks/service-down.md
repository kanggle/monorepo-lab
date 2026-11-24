# Runbook — ServiceDown

Assumptions (apps run on the host, Prometheus has no host port): [`README.md`](README.md) § Environment.

## 1. What it means

`up == 0` for 2 minutes, severity `critical` (`alert-rules.yml`, group `platform.availability`).
`up` is written by Prometheus itself for every scrape target, not by any class in this repo. It is `0`
when the scrape did not get an HTTP 200 from `<target><metrics_path>`. Targets
(`infra/prometheus/prometheus.yml`): `gateway-service` :8080, `auth-service` :8081,
`account-service` :8082, `security-service` :8084, `admin-service` :8085, all at
`host.docker.internal<port>/actuator/prometheus`, plus `prometheus` itself on `localhost:9090`.

"Down" therefore means one of: the process is not running, it is running but not answering, or it
answers but `/actuator/prometheus` is not a 200.

## 2. First checks

```bash
# Which target, and why Prometheus says it failed (read "job", "health", "lastError")
docker exec iam-prometheus wget -qO- http://localhost:9090/api/v1/targets

# Is the series 0 right now, and for which job
docker exec iam-prometheus wget -qO- --post-data='query=up == 0' http://localhost:9090/api/v1/query
```

Then ask the service directly from the host (the port is the one in § 1):

```bash
curl -s -o /dev/null -w "health=%{http_code}\n"     http://localhost:8081/actuator/health
curl -s -o /dev/null -w "prometheus=%{http_code}\n" http://localhost:8081/actuator/prometheus
```

Backing containers (a service that cannot reach them fails at boot or fails its health):

```bash
docker compose -f projects/iam-platform/docker-compose.yml ps
```

## 3. Likely causes and how to confirm each

| Cause | Confirm |
|---|---|
| The `bootRun` process exited or was never started | `health=000` above; the terminal that ran `./gradlew :apps:<service>:bootRun` shows the stack trace |
| Boot failed because MySQL / Redis / Kafka is down or unhealthy | `docker compose ... ps` shows `iam-mysql` / `iam-redis` / `iam-kafka` not `healthy`; app log has a connection error |
| `health=200` but `prometheus=404` | `/actuator/prometheus` is not exposed. gateway, security and admin set `management.endpoints.web.exposure.include` with `prometheus` (`apps/*/src/main/resources/application.yml`); **auth-service and account-service set no exposure in the repository — not verified whether another source supplies it** |
| `/api/v1/targets` `lastError` mentions DNS / `host.docker.internal` | The Prometheus container cannot resolve the host. The compose file declares no `extra_hosts` for `iam-prometheus` (`docker-compose.yml:289-310`); Docker Desktop provides the name by itself, a plain Linux engine may not — not verified |
| Prometheus itself is the target (`job="prometheus"`) | `docker compose -f projects/iam-platform/docker-compose.yml ps prometheus` |

## 4. Recover / mitigate

- Process dead: start it again with `./gradlew :apps:<service>:bootRun` from the repository root
  (`<service>` = `gateway-service`, `auth-service`, `account-service`, `security-service` or
  `admin-service`). Read the first error before restarting — a crash loop on a missing dependency
  needs the dependency, not another restart.
- Dependency down: `docker compose -f projects/iam-platform/docker-compose.yml up -d mysql redis kafka`,
  wait until `ps` shows `healthy`, then restart the app.
- Check recovery: the same `up == 0` query returns an empty result and the alert leaves
  `/api/v1/alerts` after the next evaluation (rule groups evaluate every 30 s).

## 5. Escalate

- Several targets down at once: the common cause is the host or Docker, not the apps. Escalate to
  whoever owns the machine.
- `auth-service` down also stops token issuance and `GET /internal/auth/jwks` (the gateway fetches
  its keys from there — `gateway.jwt.jwks-url`); the gateway keeps serving cached keys for the
  configured grace period (`gateway.jwt.grace-period-seconds: 300`); `JwksCache` logs
  `In-memory JWKS grace period expired` when it runs out (`JwksCache.java:164`) — what the filter
  does after that was not verified. Treat it as the most urgent target.
- Boot failure with a Flyway or schema error: stop and hand to the service owner; do not edit tables
  by hand.
