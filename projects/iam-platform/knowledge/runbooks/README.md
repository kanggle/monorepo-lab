# Runbooks

On-call procedures for the alerts in
[`infra/prometheus/alert-rules.yml`](../../infra/prometheus/alert-rules.yml). Every alert's
`runbook:` annotation points at one file here.

Created by TASK-MONO-835: until then every `runbook:` link in this project pointed at a
`docs/runbooks/*.md` that did not exist.

---

# Contents

| Runbook | Triggered by | Covers |
|---|---|---|
| [`service-down.md`](service-down.md) | `ServiceDown` | A scrape target (`up == 0`) that has been unreachable for 2 minutes |
| [`high-error-rate-5xx.md`](high-error-rate-5xx.md) | `HighErrorRate5xx` | One service answering more than 5% of its requests with 5xx |
| [`high-latency-p99.md`](high-latency-p99.md) | `HighLatencyP99` | One service whose p99 request latency stays above 1 second |
| [`gateway-jwt-failure-spike.md`](gateway-jwt-failure-spike.md) | `GatewayJwtFailureSpike` | The edge rejecting more than 5 access tokens per second |
| [`gateway-rate-limit-exceeded.md`](gateway-rate-limit-exceeded.md) | `GatewayRateLimitExceeded` | The edge answering 429 more than 10 times per second |
| [`auth-login-failure-spike.md`](auth-login-failure-spike.md) | `AuthLoginFailureSpike` | More than 20 failed logins per second (infrastructure faults excluded) |
| [`auth-refresh-token-reuse.md`](auth-refresh-token-reuse.md) | `AuthRefreshTokenReuseDetected` | A rotated refresh token presented again — possible theft |
| [`auth-outbox-lag-high.md`](auth-outbox-lag-high.md) | `AuthOutboxLagHigh` | `auth_outbox` rows not reaching Kafka |
| [`account-outbox-lag-high.md`](account-outbox-lag-high.md) | `AccountOutboxLagHigh` | `account_outbox` rows not reaching Kafka |
| [`security-consumer-lag-high.md`](security-consumer-lag-high.md) | `SecurityConsumerLagHigh` | security-service falling behind its Kafka topics |
| [`security-dlq-depth-high.md`](security-dlq-depth-high.md) | `SecurityDlqDepthHigh` | Poison messages parked on security-service `.DLT` topics |
| [`security-auto-lock-spike.md`](security-auto-lock-spike.md) | `SecurityAutoLockSpike` | Accounts being auto-locked faster than 1 per second |
| [`admin-action-spike.md`](admin-action-spike.md) | `AdminActionSpike` | Operator commands above 5 per second for 10 minutes |
| [`admin-command-failure-high.md`](admin-command-failure-high.md) | `AdminCommandFailureHigh` | More than 20% of admin commands finishing as `failure` |

---

# Environment these runbooks assume

Read this once; the runbooks do not repeat it.

- **Backing services are containers, the five apps are not.** `docker-compose.yml` starts only
  `iam-mysql`, `iam-redis`, `iam-kafka`, `iam-kafka-init`, `iam-kafka-ui`, `iam-prometheus`,
  `iam-alertmanager`, `iam-loki`, `iam-promtail`, `iam-grafana`, `iam-mailpit` (its header, lines
  18-33, says apps run via `./gradlew :apps:<service>:bootRun`). Prometheus scrapes them on the host:
  `host.docker.internal:8080` gateway, `:8081` auth, `:8082` account, `:8084` security, `:8085` admin
  (`infra/prometheus/prometheus.yml:25-57`). **App logs are the terminal that runs `bootRun`** —
  there is no app container to `docker logs`. A containerised full stack exists only in
  `docker-compose.e2e.yml` (project `iam-e2e`, host ports 18080/18081/18082/18084/18085, no fixed
  container names); its container names were not verified here.
- **Run compose commands from the repository root**, with
  `-f projects/iam-platform/docker-compose.yml`.
- **Prometheus has no published port** (`expose: 9090` only). Every query below runs inside its
  container with the form
  `docker exec iam-prometheus wget -qO- --post-data='query=<PromQL>' http://localhost:9090/api/v1/query`.
  The form was derived from the compose file (the container's own healthcheck uses `wget`,
  `docker-compose.yml:304`) and **was not executed while writing these runbooks** (no stack was
  running). If `wget` refuses `--post-data`, use Grafana Explore at `http://grafana.iam.local/`
  (Traefik host rule, `docker-compose.yml:384`; needs the hosts entries from the compose header).
- **Which alerts are firing now:**
  `docker exec iam-prometheus wget -qO- http://localhost:9090/api/v1/alerts`
- **MySQL:** `iam-mysql`, root password from the container's own `MYSQL_ROOT_PASSWORD`
  (`docker-compose.yml:64`, default `rootpass`). Pattern used throughout, so the password is never
  typed: `docker exec -i iam-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <database>' <<'SQL' … SQL`.
  Databases: `auth_db`, `account_db`, `security_db`, `admin_db` (`docker/mysql/init.sh`).
- **Kafka:** `docker exec iam-kafka /opt/kafka/bin/<tool>.sh --bootstrap-server localhost:9092 …`
  (the broker's own healthcheck, `docker-compose.yml:150`, uses that path and listener).
- **Redis:** `docker exec iam-redis redis-cli -a redispass …` — `redispass` is the compose default
  (`docker-compose.yml:90`); use `REDIS_PASSWORD` instead if it was overridden.
- **Actuator on the apps:** all five declare `management.endpoints.web.exposure.include` with
  `prometheus` in their `application.yml` (auth's and account's `SecurityConfig` `permitAll`
  `/actuator/**`; the other three were not re-checked here).
  🔴 auth-service and account-service only since TASK-BE-664 (2026-11-24) — before that their
  `/actuator/prometheus` answered 404 and **none** of their metrics (outbox lag included) reached
  Prometheus. An old checkout or image without that change shows the same symptom; `service-down.md`
  § 3 says how to test it.
- **Demo host (EC2):** same container names; reach them through `ssm` instead of a local shell.
  How the apps are run there is not verified.

---

# Writing rule

A runbook is read at 3am by someone who did not write it. Every command must be
**copy-pasteable as written** — no placeholder the reader is expected to already know how to fill,
no "connect to the DB" without saying which container and which credentials, no step whose success
criterion is left implicit. If a step needs a value the reader must supply, the runbook says exactly
where to get it.

Write only what was confirmed in code, config or specs, and cite where. Anything that could not be
confirmed says **"not verified"** — never a plausible guess.

---

# Priority

`knowledge/` is lower priority than all `specs/` documents (see [`../README.md`](../README.md)).
A runbook describes how to recover; it does not define policy. If a runbook conflicts with a spec,
the spec wins and the runbook is the thing that needs fixing.
