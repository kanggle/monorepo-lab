# Runbooks

On-call procedures for the Prometheus alerts defined in
[`infra/prometheus/alert-rules.yml`](../../infra/prometheus/alert-rules.yml). Each alert's
`runbook:` annotation points at one file here.

Created by TASK-MONO-835 — before it, every `runbook:` link pointed at a `docs/runbooks/*.md`
that did not exist.

---

# Contents

| Runbook | Triggered by | Covers |
|---|---|---|
| [`service-down.md`](service-down.md) | `ServiceDown` | A scrape target (`up == 0`) unreachable for 2 minutes: which service, process vs. scrape-path problem |
| [`high-error-rate-5xx.md`](high-error-rate-5xx.md) | `HighErrorRate5xx` | One service answering > 5% of requests with 5xx |
| [`high-latency-p99.md`](high-latency-p99.md) | `HighLatencyP99` | One service's p99 request latency above 1s |
| [`wms-outbox-backlog-high.md`](wms-outbox-backlog-high.md) | `WmsOutboxBacklogHigh` | `<svc>_outbox_pending_count > 100` for master / inbound / inventory / outbound / notification / admin |
| [`wms-outbox-publish-failing.md`](wms-outbox-publish-failing.md) | `WmsOutboxPublishFailing` | admin / notification outbox publisher failing continuously for 10 minutes |

Service-specific procedures that already exist live next to the service spec and are **linked, not
copied**: [`specs/services/*/runbooks/`](../../specs/services/) (currently admin-service
`db-role-grants.md` and `read-model-rebuild.md`, notification-service `dlt-replay.md`).

---

# Writing rule

A runbook is read at 3am by someone who did not write it. Every command must be
**copy-pasteable as written** — no placeholder the reader is expected to know how to fill, no
"connect to the DB" without saying which container and which user, no step whose success criterion
is left implicit. If a step needs a value the reader must supply, the runbook says where to get it.

State the environment each command assumes. The commands here assume the local
`docker-compose.yml` stack (containers `wms-postgres`, `wms-kafka`, `wms-prometheus`, ...). **The
wms application services are not containers in that file** — they run as host JVM processes
(`./gradlew :apps:<service>:bootRun`), so their ports are on the host (`localhost:8080`–`8086`).
Anything not confirmed from the code, compose file or specs is marked "not verified" instead of
guessed.

---

# Priority

`knowledge/` is lower priority than all `specs/` documents (see [`../README.md`](../README.md)).
A runbook describes how to recover; it does not define policy. If a runbook conflicts with a spec,
the spec wins and the runbook is the thing that needs fixing.
