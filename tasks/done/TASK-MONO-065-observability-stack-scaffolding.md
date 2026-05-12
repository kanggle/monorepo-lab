# Task ID

TASK-MONO-065

# Title

Scaffold ephemeral observability stack — Vector + VictoriaLogs + VictoriaMetrics under `infra/observability/` (OpenAI Harness gap #3 Phase 1)

# Status

done

# Owner

monorepo

# Task Tags

- code
- infra
- harness

---

# Required Sections

- Goal
- Scope (In / Out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Convert ADR-MONO-007 § 2.5 D5 **Phase 1 (stack scaffolding)** from policy into running code: a working `infra/observability/docker-compose.yml` + `vector.toml` + `scripts/observability/{up,down}.sh` that an operator (human or agent) can invoke against the current worktree's `docker-compose.bootrun.yml` workflow to capture gateway-service + master-service telemetry into VictoriaLogs (LogQL) and VictoriaMetrics (PromQL).

This is the second of four phases in the gap #3 closure series after Phase 0 (TASK-MONO-064 → ADR-MONO-007 ACCEPTED on PR #400). Phase 2 (skill + `/observe` slash command) and Phase 3 (coverage expansion + Rancher validation + CI footprint regression) follow as separate tasks (MONO-066 / 067) once this one merges.

The verification target is **manual bootRun mode** as described in ADR-MONO-007 § 2.3 D3 ("Manual override. scripts/observability/up.sh / down.sh can be invoked directly without Gradle"). Full Testcontainers / e2eTest Gradle integration is deliberately pushed to Phase 2 (TASK-MONO-066) — the testcontainers-network coupling problem is best solved alongside the skill that consumes the stack, not before.

---

# Scope

## In Scope

### A. `infra/observability/docker-compose.yml`

A standalone compose project (not extending the wms-platform compose) that brings up three containers:

- `vector` (`timberio/vector:0.45.0-alpine`) — collector. Sources: `docker_logs` (collects stdout/stderr of every container on the worktree's docker network) + `prometheus_scrape` (scrapes `<service>:8080/actuator/prometheus` for the wms services). Sinks: `victorialogs_http` + `victoriametrics_remote_write` (both via Vector's official integrations).
- `victorialogs` (`victoriametrics/victoria-logs:v0.40.0-victorialogs`) — LogQL store. tmpfs `/victoria-logs-data`. Exposed on `127.0.0.1:<dyn>` mapped to internal `9428`.
- `victoriametrics` (`victoriametrics/victoria-metrics:v1.105.0`) — PromQL store. tmpfs `/victoria-metrics-data`. Exposed on `127.0.0.1:<dyn>` mapped to internal `8428`.

Compose properties:

- `name: wms-observability-${WORKTREE_HASH}` (compose project name derived from `git rev-parse --show-toplevel | sha256sum | head -c 8`)
- `networks: { default: { name: wms-observability-${WORKTREE_HASH} } }` — joins the wms-platform's `docker-compose.bootrun.yml` network via external attachment when invoked with the appropriate flag (see `up.sh` below)
- All three services bind only on `127.0.0.1:0` (OS-assigned ephemeral port). Port assignments captured in `.observability/ports.env` after `up.sh` returns.
- No named volumes; all storage is `tmpfs` so teardown is total.

### B. `infra/observability/vector.toml`

Vector pipeline config (TOML — Vector's stable config format) with:

- 2 sources:
  - `docker_logs` — collect from the worktree's docker network only (`include_labels = ["com.docker.compose.project=wms-platform-*"]`). JSON-formatted Logback line shape is parsed via a `remap` transform.
  - `prometheus_scrape` — endpoints list driven by `WMS_SCRAPE_TARGETS` env var (newline-separated `host:port` pairs). 15 s scrape interval.
- 2 transforms:
  - `parse_logback` (VRL `remap`) — Logback JSON → typed fields (`level`, `traceId`, `service`, `message`, …). Default Logback console pattern fallback if JSON parse fails.
  - `add_worktree_label` — annotates every event with `worktree=${WORKTREE_HASH}`.
- 2 sinks:
  - `victorialogs` (`http` sink with VictoriaLogs's JSON-stream ingest endpoint `http://victorialogs:9428/insert/jsonline`)
  - `victoriametrics` (`prometheus_remote_write` sink targeting `http://victoriametrics:8428/api/v1/write`)

### C. `scripts/observability/up.sh` + `scripts/observability/down.sh`

POSIX shell scripts (bash) for cross-OS compatibility (memory `feedback_*.md` records Windows + macOS team mix). Behaviour:

`up.sh`:

1. Derive `WORKTREE_HASH` from `git rev-parse --show-toplevel | sha256sum | head -c 8`.
2. Pre-flight: docker daemon reachable (`docker info`), the worktree's primary compose network already up (detected via `docker network ls | grep wms-platform`) — exit with the 4-block format if missing.
3. `docker compose -f infra/observability/docker-compose.yml -p wms-observability-${WORKTREE_HASH} up -d`.
4. Wait up to 30 s for all three containers' health endpoints (Vector `:8686/health`, VictoriaLogs `:9428/health`, VictoriaMetrics `:8428/health`) — fail with 4-block format on timeout.
5. Read mapped ports via `docker compose port` and write `.observability/ports.env` (gitignored) inside the worktree:
   ```
   VICTORIALOGS_PORT=<port>
   VICTORIAMETRICS_PORT=<port>
   VECTOR_PORT=<port>
   STARTED_AT=<ISO8601>
   ```
6. Print final readiness banner with the three URLs.

`down.sh`:

1. Derive `WORKTREE_HASH` the same way.
2. `docker compose -p wms-observability-${WORKTREE_HASH} down -v --remove-orphans`.
3. Remove `.observability/ports.env`.

Both scripts emit failures in the 4-block format (`OBSERVE-SCAFFOLD-NN` rule-id namespace — distinct from Phase 2's `OBSERVE-QUERY-NN`):

- `OBSERVE-SCAFFOLD-01` — docker daemon unreachable
- `OBSERVE-SCAFFOLD-02` — wms-platform compose network missing (instruct user to `docker compose -f projects/wms-platform/docker-compose.bootrun.yml up -d` first)
- `OBSERVE-SCAFFOLD-03` — stack health check timeout
- `OBSERVE-SCAFFOLD-04` — port file write failure

### D. `infra/observability/README.md`

Operator-facing documentation:

- What the stack provides (one paragraph per layer).
- How to invoke `up.sh` / `down.sh` (3-line cheat sheet).
- Sample LogQL + PromQL queries against the running stack (curl shape — Phase 2's skill will wrap this).
- Footprint + start-time targets (200 MB resident / 30 s cold start per ADR § 2.1 D1).
- Cross-references to ADR-MONO-007 + the Phase 2/3 follow-up tasks.

### E. `.gitignore` extension

Add `.observability/` (the per-worktree port file directory) to root `.gitignore`.

### F. Footprint + start-time measurement

Run `up.sh` against a local docker daemon (Rancher Desktop or Docker Desktop). Capture:

- `docker stats --no-stream --format "{{.Name}} {{.MemUsage}}"` snapshot 10 s after `up.sh` returns.
- Wall-clock duration of `up.sh` from invocation to readiness banner.
- Record both numbers in this task's Implementation Notes section before merging.

If the local docker environment is unavailable (Rancher Desktop blocker per memory), skip the live measurement and note the deferral in Implementation Notes; CI does not run this stack so there's no automated alternative.

## Out of Scope

- **Gradle e2eTest integration** (passing `-Pobservability=on` from the e2e suite). Deferred to TASK-MONO-066 — the testcontainers-network coupling is best solved alongside the skill that consumes the stack.
- **The `.claude/skills/cross-cutting/observability-query/` skill**. That is TASK-MONO-066 Phase 2's whole deliverable.
- **Trace layer**. Stays deferred to ADR-MONO-007a per ADR § 2.1 D1.
- **Production observability stack**. admin-service Operations endpoint is the production story; this Phase 1 is for ephemeral agent loops only.
- **Service-side telemetry changes** (new Logback appenders, new Micrometer counters, OTel instrumentation). The stack ingests whatever the services already emit; service code MUST NOT be modified by this task.
- **CI activation**. ADR § 2.3 D3 excludes CI explicitly.
- **Dashboards / UI** (Grafana, vmui, VictoriaLogs UI). Phase 2's skill is the agent's query surface; humans can reach `vmui` directly via the printed ports if they want a UI, but no Grafana provisioning in this task.
- **Auto-start on service container boot**. ADR § 2.3 D3 specifies opt-in only.

---

# Acceptance Criteria

- [ ] `infra/observability/docker-compose.yml` defines Vector + VictoriaLogs + VictoriaMetrics with project name `wms-observability-${WORKTREE_HASH}`, all ports bound on `127.0.0.1:0`, tmpfs storage only.
- [ ] `infra/observability/vector.toml` defines the 2-source / 2-transform / 2-sink pipeline (`docker_logs` + `prometheus_scrape` → `parse_logback` + `add_worktree_label` → `victorialogs` + `victoriametrics`).
- [ ] `scripts/observability/up.sh` derives WORKTREE_HASH, performs pre-flight checks, brings the stack up, waits for health, writes `.observability/ports.env`, and prints the readiness banner. Failures emit 4-block `OBSERVE-SCAFFOLD-NN` messages.
- [ ] `scripts/observability/down.sh` tears the stack down and removes the port file.
- [ ] `infra/observability/README.md` documents operator workflow + sample LogQL/PromQL queries + footprint targets + cross-references.
- [ ] Root `.gitignore` includes `.observability/`.
- [ ] Implementation Notes section in this task records the measured footprint (`docker stats` snapshot) and start-time (wall clock). If the local docker is unavailable, the deferral reason is named.
- [ ] No file under `libs/`, `apps/`, `projects/<name>/`, `.claude/` modified — `infra/` + `scripts/` + root `.gitignore` + this task file only.
- [ ] CI green (path-filter — `infra/**` + `scripts/**` matched, full pipeline subset only).

---

# Related Specs

- `docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md` § 2.1 D1 (stack components + footprint targets) / § 2.2 D2 (topology) / § 2.3 D3 (lifecycle) / § 2.5 D5 (phasing — this is Phase 1)
- `platform/lint-remediation-message-standard.md` (4-block format for `OBSERVE-SCAFFOLD-NN`)
- Memory `reference_openai_harness_engineering.md` § "다이어그램 3개" — Vector + VictoriaLogs/Metrics provenance
- Memory `project_testcontainers_docker_desktop_blocker.md` (local docker environment caveat — relevant for footprint measurement)

# Related Skills

None — Phase 2 will create `.claude/skills/cross-cutting/observability-query/`. This task is infrastructure-only.

---

# Related Contracts

None — no HTTP / event contract surface. Internal config files and shell scripts only.

---

# Target Service

N/A — monorepo-level infrastructure scaffolding. Targets:

- `infra/observability/` (new directory + 3 files)
- `scripts/observability/` (new directory + 2 files)
- Root `.gitignore` (one-line extension)

---

# Architecture

Single-process stack: Vector pipes `docker_logs` + `prometheus_scrape` → 2 storage backends. No service-side changes — the existing Logback console output + Micrometer `/actuator/prometheus` endpoint of every wms service are the data sources, picked up at the dockerd layer (Vector's `docker_logs` source) and via HTTP scrape (Vector's `prometheus_scrape` source) respectively.

Lifecycle gate: opt-in `up.sh` / `down.sh` invocation. Not auto-started by anything in this task — the user (or Phase 2's skill, once it lands) decides when the stack is needed. Idle teardown (ADR § 2.3 D3, 5-min) is also Phase 2's responsibility — Phase 1 only provides manual `down.sh`.

---

# Implementation Notes

## Container image version pinning

- Vector 0.45 (latest stable as of 2026-05). Alpine variant for smaller footprint.
- VictoriaLogs v0.40 (LTS series, LogQL support stable since v0.30).
- VictoriaMetrics v1.105 (LTS series, drop-in Prometheus replacement).

All three pinned to specific tags (not `latest`) so Phase 1's footprint baseline is reproducible — a Phase 3 footprint regression test will compare against the numbers measured here.

## Network coupling with wms-platform's `docker-compose.bootrun.yml`

The simplest path for Phase 1 (manual bootRun mode):

- wms `docker-compose.bootrun.yml` already creates a docker network named `wms-platform-bootrun_default` (compose default naming).
- `infra/observability/docker-compose.yml` declares that network as **external** via the compose top-level `networks:` block, scoped to the same network name. Vector's `docker_logs` source filters by label `com.docker.compose.project=wms-platform-bootrun` to ignore unrelated containers on the same host.

This means the operator workflow is:

```
docker compose -f projects/wms-platform/docker-compose.bootrun.yml up -d   # 1. start wms services
./scripts/observability/up.sh                                              # 2. start observability stack (joins wms network)
# (run e2e, query logs, etc.)
./scripts/observability/down.sh                                            # 3. tear observability down
docker compose -f projects/wms-platform/docker-compose.bootrun.yml down    # 4. (optional) tear wms down
```

Phase 2 will replace step 2 with `gradlew :…:e2eTest -Pobservability=on` once the testcontainers-network coupling is solved.

## D4 churn-clock interaction

This task is scope-extended under the OpenAI Harness gap series (ADR-MONO-003a § D1.3 — Harness gap series scope, user-acknowledged 2026-05-12). All file additions are under `infra/observability/` (new directory, no rule-relaxation) + `scripts/observability/` (new directory) + root `.gitignore` (additive). Minimal last_churn impact — same shape as MONO-062's `.claude/workflows/doc-gardening.md` new-file authoring.

## Commit shape

Single commit / single PR pattern. Conventional commit prefix:

```
feat(infra)+task(mono-065): observability stack scaffolding — Vector + VictoriaLogs/Metrics (OpenAI Harness gap #3 Phase 1)
```

The closure chore (`ready` → `done`) lands in a separate small PR after this one merges, matching the MONO-059/060/061/062/063/064 lifecycle precedent.

## Footprint + start-time measurement protocol

```
$ time ./scripts/observability/up.sh
$ docker stats --no-stream --format "table {{.Name}}\t{{.MemUsage}}" $(docker compose -p wms-observability-${HASH} ps -q)
```

Record both outputs in the Implementation Notes addendum below before merging. Target: total resident ≤ 200 MB, wall-clock cold start ≤ 30 s (per ADR § 2.1 D1).

## Phase 1 measurement results (2026-05-12)

Environment: Rancher Desktop, dockerd v29.1.3, Windows 11 host, Git Bash. wms-platform compose project not running (dummy `wms-platform-bootrun_default` network only) — measurement reflects stack-only footprint without service-side traffic.

**Cold start time** (`time bash scripts/observability/up.sh`, images cached): **11.1 s** wall-clock from invocation to readiness banner. Well under the 30 s target.

**Resident memory** (`docker stats --no-stream`, ~5 s after readiness):

| Container | Memory | CPU |
|---|---|---|
| vector | 14.39 MiB | 0.06 % |
| victorialogs | 2.84 MiB | 0.11 % |
| victoriametrics | 9.65 MiB | 0.55 % |
| **Total** | **26.88 MiB** | **0.72 %** |

Total resident at idle is **13 % of the 200 MB target** — substantial headroom for the Phase 2 skill + Phase 3 coverage expansion. The 200 MB cap in ADR-MONO-007 § 2.1 D1 anticipated VictoriaTraces being included; without it, Phase 1 sits at <30 MB.

**Endpoint verification**:

- VictoriaMetrics `GET /api/v1/query?query=up` → `{"status":"success","data":{"resultType":"vector","result":[]}}` (200, empty result expected — no scrape targets configured in Phase 1).
- Vector admin `GET /health` → `{"ok":true}` (200).
- VictoriaLogs healthcheck container-level reports `(healthy)` within 5 s.

## Configuration gotchas discovered during Phase 1

1. **Vector env var interpolation runs against the entire config file including comments.** Any literal `${IDENTIFIER}` token in a comment causes Vector to look up `IDENTIFIER` in env vars at config load. Phase 1 hit this with a comment referencing `${VAR}` syntax — Vector fails with `Missing environment variable in config. name = "VAR"`. Fix: avoid the literal pattern in comments (this task's vector.toml describes the syntax in prose instead). Worth a Phase 2 follow-up: a `vector validate` pre-commit hook would catch this class of error before push.
2. **Vector 0.45 dropped `ndjson` as a sink codec.** Use `encoding.codec = "json"` + `framing.method = "newline_delimited"` together to produce the same wire format. Earlier Vector versions accepted `ndjson` as a single-name codec; the 0.45 schema only enumerates 11 codecs (avro/cef/csv/gelf/json/logfmt/native/native_json/protobuf/raw_message/text).
3. **Vector image's default config emits demo / fake syslog if `--config` flag is absent.** Easy to miss because the container starts "healthy" by some lenient measure but the pipeline is wrong. Phase 1's `docker-compose.yml` explicitly sets `command: ["--config-toml", "/etc/vector/vector.toml"]` to avoid this.
4. **`docker compose ps --format json` health field parsing on Git Bash / Windows.** The script's awk-based extraction works but is fragile against Compose v2 minor JSON shape changes. Phase 3 may want to migrate to `docker compose ps --format '{{.Health}}'` (template form) if Compose template support stabilises across the supported daemon versions.

Each item above is a small DX/robustness gain candidate but not load-bearing for Phase 1 closure.

---

# Edge Cases

- **`up.sh` invoked outside a git worktree.** `git rev-parse --show-toplevel` returns non-zero; the script emits an `OBSERVE-SCAFFOLD-01` variant (new sub-code `01a` — outside git worktree) and exits. Documented in `up.sh` comments.
- **Stale `.observability/ports.env` from a crashed prior run.** `up.sh` always overwrites; the operator who reads stale ports gets a connection refused, which is self-correcting. Acceptable first-iteration behaviour; harden in Phase 3 if it becomes noisy.
- **Port collision against another running observability stack.** Compose project name includes the worktree hash, so two worktrees produce different project names → independent network namespaces → no collision. `127.0.0.1:0` binding leaves port selection to the OS.
- **docker compose v1 vs v2 syntax differences.** The scripts assume v2 (`docker compose`, space form) which is the default on Docker Desktop ≥ 20.10 and Rancher Desktop ≥ 1.0. v1 (`docker-compose`, hyphen form) is unsupported per the team's environment baseline.

---

# Failure Scenarios

- **VictoriaLogs / VictoriaMetrics container fails to start.** Vector's sinks back off and retry; the operator sees no data flowing despite no script-level error. Mitigation: `up.sh` waits on each container's `/health` endpoint with a 30 s timeout — failure surfaces as `OBSERVE-SCAFFOLD-03` rather than silent data loss.
- **Vector pipeline misconfigured (TOML parse error).** Container fails to start with a visible error in `docker logs <vector>`. `up.sh` health-check timeout catches this. Mitigation: vector config is small (~50 lines) and verifiable by `vector validate /etc/vector/vector.toml` before commit.
- **wms-platform compose network not running.** `up.sh` emits `OBSERVE-SCAFFOLD-02` with the remediation instructing the user to start it first. No silent attach to a non-existent network.
- **Footprint exceeds 200 MB target.** Implementation Notes records the actual number. If it exceeds by >50%, this task itself does not regress the change — it documents the gap and opens a follow-up note in ADR-MONO-007 § 4.2 (negative consequences); the policy stands while Phase 3 plans a regression test.

---

# Test Requirements

N/A — infrastructure scaffolding without executable test surface in this task. Verification:

1. `up.sh` brings the stack up against a wms-platform `docker-compose.bootrun.yml` session.
2. `curl http://127.0.0.1:${VICTORIALOGS_PORT}/select/logsql/query?query='*'` returns at least one log line within 60 s of any wms service receiving traffic.
3. `curl http://127.0.0.1:${VICTORIAMETRICS_PORT}/api/v1/query?query=up` returns the scrape targets with `value=1`.
4. `down.sh` removes all three containers and the port file.
5. Footprint + start-time numbers recorded in Implementation Notes.

These steps belong to the **operator** (or a future Phase 2 skill that can chain them). This task's CI is just path-filtered linting + markdown validation.

---

# Definition of Done

- [ ] All Acceptance Criteria pass.
- [ ] Footprint + start-time measured (or deferral reason recorded).
- [ ] CI green.
- [ ] Closure chore PR (`ready` → `done`) opens after this PR merges.
- [ ] memory `reference_openai_harness_engineering.md` gap #3 row updated to reflect Phase 1 DELIVERED on closure chore merge (separate PR — same shape as TASK-MONO-064 closure).

---

# Provenance

Memory `reference_openai_harness_engineering.md` (2026-05-07 receipt) § "monorepo-lab 갭 매핑" — gap #3, Phase 1 of the four-phase closure plan defined in ADR-MONO-007 § 2.5 D5.

ADR-MONO-007 ACCEPTED 2026-05-12 (PR #400) gates this task per ADR § 6 outstanding follow-up item #1 — Phase 1 task ready/ when ADR-MONO-007 ACCEPTED. **Satisfied** by PR #400 merge.

D4 OVERRIDE applies per ADR-MONO-003a § D1.3 — Harness gap series scope.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (compose + script + TOML authoring, routine — per ADR-MONO-007 § 2.5 D5 phase model note). Direct authoring chosen for this PR (single-PR scope, manageable).
