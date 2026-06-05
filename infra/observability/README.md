# Worktree-isolated Ephemeral Observability Stack

Operator guide for the Vector + VictoriaLogs + VictoriaMetrics + VictoriaTraces stack defined by ADR-MONO-007 (logs + metrics) and ADR-MONO-007a (trace layer).

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

`up.sh` prints the URLs (VictoriaLogs / VictoriaMetrics / VictoriaTraces / Vector admin) at the end with the dynamically assigned ports. The same values are in `$(git rev-parse --show-toplevel)/.observability/ports.env` if you need them programmatically.

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

# Trace tree by trace_id (VictoriaTraces, Jaeger-compat — ADR-MONO-007a)
curl -s "http://127.0.0.1:${VICTORIATRACES_PORT}/select/jaeger/api/traces/0af7651916cd43dd8448eb211c80319c" | jq .
```

Phase 2 (TASK-MONO-066) will wrap these into a `/observe` slash command via a `.claude/skills/cross-cutting/observability-query/` skill — no `curl` boilerplate needed once that lands.

---

## Web UIs (humans only)

If you want a UI for ad-hoc exploration:

- VictoriaLogs UI: `http://127.0.0.1:${VICTORIALOGS_PORT}/select/vmui`
- VictoriaMetrics UI (vmui): `http://127.0.0.1:${VICTORIAMETRICS_PORT}/vmui`
- VictoriaTraces UI (vmui): `http://127.0.0.1:${VICTORIATRACES_PORT}/select/vmui` (trace layer — ADR-MONO-007a)

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

## Phase 2: Gradle e2eTest mode

Phase 2 (TASK-MONO-066) adds Gradle e2eTest integration via the `-Pobservability=on` flag. When passed, the e2eTest task creates a named docker network beforehand, runs `up.sh --network <name>`, injects the network name into the Testcontainers JVM, runs `down.sh` and removes the network in `doLast`.

```sh
./gradlew :projects:wms-platform:apps:gateway-service:e2eTest -Pobservability=on
```

What happens behind the scenes:

1. `doFirst` — `docker network create wms-observability-e2e-${worktreeHash}` (idempotent — `|| true`)
2. `doFirst` — `./scripts/observability/up.sh --network wms-observability-e2e-${worktreeHash}` (waits ≤ 30 s for stack healthy)
3. Test JVM starts with `wms.e2e.observabilityNetwork=<name>` system property; `E2EBase` resolves the named network via `Network.builder().createNetworkCmdModifier(...)`.
4. e2eTest scenarios run normally; service container stdout flows to VictoriaLogs, `/actuator/prometheus` scrapes flow to VictoriaMetrics.
5. `doLast` — `./scripts/observability/down.sh` then `docker network rm wms-observability-e2e-${worktreeHash}` (both with `|| true`).

Without `-Pobservability=on`, the e2eTest task is byte-identical to the pre-Phase-2 behaviour (anonymous Testcontainers `Network.newNetwork()`).

### Querying during a long-running e2eTest

While the e2eTest is running (or after, before `doLast` tears down — useful during failure post-mortems), open a second terminal and use the Phase 2 skill scripts:

```sh
./.claude/skills/cross-cutting/observability-query/scripts/query-logs.sh \
    '{service="gateway-service"} |= "trace"'

./.claude/skills/cross-cutting/observability-query/scripts/query-metrics.sh \
    'rate(http_server_requests_seconds_count[1m])'
```

The scripts read `.observability/ports.env` (still populated until `down.sh` runs) and emit ndjson / JSON results, with 4-block `OBSERVE-QUERY-NN` errors on stderr.

`OBSERVE-QUERY-NN` rule-ID quick-reference (full table in `.claude/skills/cross-cutting/observability-query/SKILL.md` § Failure modes):

| ID | Trigger | Remediation hint |
|---|---|---|
| `01` | Stack not up | run `up.sh` or pass `-Pobservability=on` |
| `02` | Stack mid-tear-down / container crashed | `down.sh` then `up.sh` |
| `03` | Query syntax error | consult LogQL / PromQL primer |
| `04` | No results within window | widen range or relax matcher |
| `05` | Pagination overflow | `--limit 500` or narrow the matcher |

CI does NOT pass `-Pobservability=on` — the existing e2e-tests job remains the default (Docker-free Testcontainers anonymous network) for fast feedback. Phase 3 (TASK-MONO-067) will decide whether any CI lane wants the stack active.

---

## Docker engine compatibility

The stack uses docker-compose only — no Testcontainers, no docker-java client. The Phase 1 measurement protocol (TASK-MONO-065) validated against:

| Engine | Version | Host OS | Status | Source |
|---|---|---|---|---|
| Rancher Desktop | dockerd v29.1.3 | Windows 11 | ✅ validated — 11.1s cold start, **26.88 MiB resident** | TASK-MONO-065 Implementation Notes |
| Docker bundled with GitHub Actions `ubuntu-latest` | varies | Linux | ✅ validated — **62.84 MiB resident** (Vector 44 + VictoriaLogs 5.9 + VictoriaMetrics 12.9), see `observability-footprint` CI job (Phase 3 / TASK-MONO-067) | `.github/workflows/ci.yml` |
| Docker Desktop | — | macOS / Windows | ⚠️ not yet validated | Phase 3 explicit non-deliverable |

**Why the two baselines differ**: Vector's resident memory differs by ~3× between Rancher Desktop (Windows, ~14 MiB) and Linux runner (~44 MiB). The difference is driven by Vector's native build flavour — the alpine image bundles a musl-libc binary, but the underlying memory allocator's reservation strategy + cgroup accounting on Linux push the resident set considerably higher than on Windows where dockerd runs under WSL2 with a VM-level memory layer. VictoriaLogs and VictoriaMetrics show smaller divergence (~2× each).

The CI regression cap is therefore pegged against the **larger** baseline (Linux 62.84 MiB × ~1.6 safety margin = 100 MiB). Future image-version upgrades that push Linux footprint past 100 MiB fail the CI job; Windows/Rancher operators tracking the same artefact would see room for growth, which is acceptable because their footprint stays well below the cap.

**Important — Rancher Desktop docker-java regression does NOT affect this stack.** Memory `project_testcontainers_docker_desktop_blocker.md` documents a Rancher dockerd v29.1.3 + docker-java zerodep npipe transport regression that affects **Testcontainers** workflows (every test JVM hits `MalformedChunkCodingException` after the first cycle). This observability stack uses pure docker-compose against the dockerd HTTP API, bypassing docker-java entirely — the regression does not surface here.

The Phase 2 Gradle e2eTest integration (`-Pobservability=on`) does pull in Testcontainers via `Network.builder().createNetworkCmdModifier(...)`, but only to **attach** to an existing named network — no image pulls or container management via docker-java. The named-network handoff was chosen specifically to keep the high-friction docker-java surface minimal (see ADR-MONO-007 § 4.2 and TASK-MONO-066 § Implementation Notes for the trade-off analysis).

If you operate against Docker Desktop and observe a regression, file a follow-up against TASK-MONO-067 follow-up — the stack itself does nothing engine-specific, so any divergence is most likely in the network attach or `docker stats` parsing paths.

---

## Limitations

What works (gap #3 fully closed as of 2026-05-13):

- Manual bootRun mode against `projects/wms-platform/docker-compose.bootrun.yml`.
- Logs from any wms container on the bootRun network flow into VictoriaLogs.
- Metrics from any wms container's `/actuator/prometheus` flow into VictoriaMetrics (15 s scrape interval).
- Per-worktree isolation: two worktrees → two independent stacks, no cross-contamination.
- **Gradle e2eTest integration** for all 4 e2e suites — `gradlew :…:e2eTest -Pobservability=on` wires the stack via named docker network. Phase 2 (TASK-MONO-066) added gateway-master live-pair; Phase 3 (TASK-MONO-067) added fan-platform live-trio + scm-platform cross-service + iam-platform. Per-project network prefixes (`wms-` / `fan-` / `scm-` / `iam-`) prevent cross-project collisions.
- **`/observe` skill** — `.claude/skills/cross-cutting/observability-query/` queries LogQL + PromQL via skill-mediated bash scripts with 4-block `OBSERVE-QUERY-NN` failure remediation. Phase 2 (TASK-MONO-066).
- **CI footprint regression** — `observability-footprint` job runs on `infra/observability/**` or `scripts/observability/**` path changes; fails if resident > 40 MiB or cold start > 30 s. Phase 3 (TASK-MONO-067).

What does **not** work (intentional, out of scope for gap #3):

- **Idle teardown daemon** — manual `down.sh` is required to teardown after `up.sh`. Not a Phase 3 deliverable per ADR-MONO-007 § 2.5 D5; file a follow-up if manual teardown becomes a recurring burden.
- **Trace ingestion** — deferred to ADR-MONO-007a per ADR-MONO-007 § 2.1 D1. Will gain `OBSERVE-QUERY-06+` rule IDs + a `query-traces.sh` script when that ADR ACCEPTED.
- **Docker Desktop validation** — Rancher Desktop + GitHub Actions Linux are the validated baselines; Docker Desktop is documented but not yet measured. See § Docker engine compatibility above.

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
