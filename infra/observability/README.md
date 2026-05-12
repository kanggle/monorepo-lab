# Worktree-isolated Ephemeral Observability Stack

Operator guide for the Vector + VictoriaLogs + VictoriaMetrics stack defined by ADR-MONO-007.

This stack is **opt-in and per-worktree**. It is not started automatically by any Gradle task, dev-setup script, or CI job. The agent or developer brings it up when they want to observe `gateway-service` / `master-service` / other wms services running under `projects/wms-platform/docker-compose.bootrun.yml`, queries the data via LogQL / PromQL, and tears it down when done.

See [docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md](../../docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md) for the rationale.

---

## What the stack provides

| Layer | Image | Role | Internal port |
|---|---|---|---|
| Collector | `timberio/vector:0.45.0-alpine` | Tails `docker_logs` of wms containers + scrapes their `/actuator/prometheus` endpoints | `8686` (admin) |
| Log store | `victoriametrics/victoria-logs:v0.40.0-victorialogs` | LogQL-queryable log ingest, tmpfs storage, 1-day retention | `9428` |
| Metric store | `victoriametrics/victoria-metrics:v1.105.0` | Prometheus-compatible metric ingest + PromQL, tmpfs storage, 1-day retention | `8428` |

All three bind on `127.0.0.1:0` (OS-assigned ephemeral host port) so multiple concurrent worktrees do not collide on host ports. The assigned ports are written to `.observability/ports.env` inside the worktree after `up.sh` succeeds.

---

## 3-line cheat sheet

```sh
docker compose -f projects/wms-platform/docker-compose.bootrun.yml up -d   # 1. start wms services
./scripts/observability/up.sh                                              # 2. start observability stack
./scripts/observability/down.sh                                            # 3. tear stack down (wms services unaffected)
```

`up.sh` prints the three URLs (VictoriaLogs / VictoriaMetrics / Vector admin) at the end with the dynamically assigned ports. The same values are in `$(git rev-parse --show-toplevel)/.observability/ports.env` if you need them programmatically.

---

## Sample queries

After the stack is up and any wms service has emitted at least one log line:

```sh
# Source the ports
source "$(git rev-parse --show-toplevel)/.observability/ports.env"

# LogQL — recent INFO lines from gateway-service
curl -s "http://127.0.0.1:${VICTORIALOGS_PORT}/select/logsql/query?query={service=\"gateway-service\",level=\"INFO\"}&limit=10" | jq .

# LogQL — anything mentioning a specific traceId across all services
curl -s "http://127.0.0.1:${VICTORIALOGS_PORT}/select/logsql/query?query=traceId:\"a1b2c3d4\"&limit=100" | jq .

# PromQL — scrape target health
curl -s "http://127.0.0.1:${VICTORIAMETRICS_PORT}/api/v1/query?query=up" | jq .

# PromQL — JVM heap usage across all wms services
curl -s "http://127.0.0.1:${VICTORIAMETRICS_PORT}/api/v1/query?query=jvm_memory_used_bytes" | jq .

# PromQL — request rate by status code
curl -s "http://127.0.0.1:${VICTORIAMETRICS_PORT}/api/v1/query_range?query=rate(http_server_requests_seconds_count[1m])&start=$(date -d '5 min ago' +%s)&end=$(date +%s)&step=15" | jq .
```

Phase 2 (TASK-MONO-066) will wrap these into a `/observe` slash command via a `.claude/skills/cross-cutting/observability-query/` skill — no `curl` boilerplate needed once that lands.

---

## Web UIs (humans only)

If you want a UI for ad-hoc exploration:

- VictoriaLogs UI: `http://127.0.0.1:${VICTORIALOGS_PORT}/select/vmui`
- VictoriaMetrics UI (vmui): `http://127.0.0.1:${VICTORIAMETRICS_PORT}/vmui`

Both UIs ship as static assets inside the official images — no extra config or container needed. Agents should prefer the HTTP API + Phase 2 skill; the UIs exist only for human investigations.

---

## Footprint + start-time targets

Per ADR-MONO-007 § 2.1 D1:

| Metric | Target | Phase 1 measurement |
|---|---|---|
| Total resident memory (idle) | ≤ 200 MB | See `tasks/done/TASK-MONO-065-...md` § Implementation Notes |
| Cold start time (`up.sh` invocation to readiness banner) | ≤ 30 s | See `tasks/done/TASK-MONO-065-...md` § Implementation Notes |

If the live measurement was deferred at Phase 1 (Rancher Desktop blocker per memory `project_testcontainers_docker_desktop_blocker.md`), the task file's Implementation Notes will name the deferral and Phase 3 will re-measure under controlled conditions.

---

## Limitations of Phase 1

What works:

- Manual bootRun mode against `projects/wms-platform/docker-compose.bootrun.yml`.
- Logs from any wms container on the bootRun network flow into VictoriaLogs.
- Metrics from any wms container's `/actuator/prometheus` flow into VictoriaMetrics (15 s scrape interval).
- Per-worktree isolation: two worktrees → two independent stacks, no cross-contamination.

What does **not** work yet (deferred to Phase 2 / 3):

- **Testcontainers / e2eTest integration.** `gradlew :…:e2eTest -Pobservability=on` does not yet wire this stack into the e2e network. Phase 2 (TASK-MONO-066) adds the wiring alongside the `/observe` skill.
- **Idle teardown.** `up.sh` does not currently install a 5-min idle watchdog; you must run `down.sh` manually. Phase 2 adds this.
- **CI footprint regression test.** Phase 3 (TASK-MONO-067) adds the CI check that catches footprint creep.
- **Trace ingestion.** Out of scope for this stack (deferred to ADR-MONO-007a per ADR-MONO-007 § 2.1 D1).

---

## Troubleshooting

| Error | Meaning | Fix |
|---|---|---|
| `OBSERVE-SCAFFOLD-01` | Docker daemon unreachable | Start Docker Desktop / Rancher Desktop |
| `OBSERVE-SCAFFOLD-01a` | Not inside a git worktree | `cd` into the monorepo-lab root |
| `OBSERVE-SCAFFOLD-02` | wms-platform network missing | Run `docker compose -f projects/wms-platform/docker-compose.bootrun.yml up -d` first |
| `OBSERVE-SCAFFOLD-03` | Stack health check timeout | `docker compose -p $PROJECT logs` to inspect; re-pull images if version mismatch |
| `OBSERVE-SCAFFOLD-04` | Port file write failure | Check write permission on the worktree root; remove any stale `.observability` file blocking the path |

---

## Cross-references

- [docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md](../../docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md) — policy ADR
- [tasks/done/TASK-MONO-064-adr-mono-007-observability-stack.md](../../tasks/done/TASK-MONO-064-adr-mono-007-observability-stack.md) — Phase 0 closure
- TASK-MONO-065 — Phase 1 (this scaffolding)
- TASK-MONO-066 — Phase 2 (skill + slash command + Gradle e2eTest wiring)
- TASK-MONO-067 — Phase 3 (coverage expansion + Rancher validation + CI footprint regression)
