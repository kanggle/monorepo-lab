---
name: observability-query
description: Query the worktree-isolated ephemeral observability stack (Vector + VictoriaLogs + VictoriaMetrics + VictoriaTraces) via LogQL, PromQL, and trace_id lookup. Wraps curl boilerplate with 4-block remediation on failure.
category: cross-cutting
---

# Skill: Observability Query

# No single spec — operates across every service's telemetry surface

This skill is **cross-cutting**: it has no single source-of-truth spec because it queries telemetry emitted by every service. The authoritative references are the ADR that pins the stack architecture and the operator README that documents the runtime:

- [docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md](../../../../docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md) — stack architecture (§ 2.4 D4 pins the skill DX contract, § 2.5 D5 Phase 2 = this skill)
- [docs/adr/ADR-MONO-007a-trace-layer.md](../../../../docs/adr/ADR-MONO-007a-trace-layer.md) — trace layer (§ 2 D6 = the `/observe trace` full impl this skill now carries)
- [infra/observability/README.md](../../../../infra/observability/README.md) — operator guide for the running stack
- [platform/lint-remediation-message-standard.md](../../../../platform/lint-remediation-message-standard.md) — 4-block remediation format the failure modes use

Each service's own observability spec (e.g. `projects/<project>/specs/services/<service>/observability.md` when present) describes what that service emits; this skill queries whatever it does, agnostic to the per-service contract.

Agent-facing query interface for the per-worktree observability stack defined by ADR-MONO-007 and scaffolded by [TASK-MONO-065](../../../../tasks/done/TASK-MONO-065-observability-stack-scaffolding.md).

This skill is the **counterpart of `observability-setup`** in the same `cross-cutting/` category — `observability-setup` defines what to instrument; this skill queries what was emitted.

---

## When to invoke

Use this skill when:

- An e2e or integration test fails and the agent needs to inspect what the service actually logged or what metrics moved.
- An interactive Claude / Codex session wants to verify a code change end-to-end by emitting a request to a bootRun stack and checking the resulting telemetry.
- A doc-gardening or refactor session needs to verify "the new code path emits the expected metric / log line" without bringing up a full unit test harness.
- A cross-product request (e.g. a console dashboard fan-out) needs to be followed across services as one trace tree (`/observe trace <trace_id>`).

Do **NOT** use this skill for:

- Production monitoring (use admin-service `OperationsController` + Grafana dashboards instead).
- Historical incident review (the stack is ephemeral, 1-day retention max).
- Pre-implementation instrumentation choice (consult `observability-setup` instead).

---

## Prerequisites

The Phase 1 stack must be running. Two invocation paths:

1. **Manual bootRun mode** — operator ran `./scripts/observability/up.sh` against a wms-platform docker-compose.bootrun session. `$REPO_ROOT/.observability/ports.env` exists.
2. **Gradle e2eTest mode** — `./gradlew :projects:wms-platform:apps:gateway-service:e2eTest -Pobservability=on` is in progress (the build wraps `up.sh` / `down.sh` automatically).

If neither holds, this skill's query scripts fail with `OBSERVE-QUERY-01` (stack not up) and the remediation instructs the operator to bring the stack up first.

Trace queries additionally require the VictoriaTraces service (ADR-MONO-007a / TASK-MONO-143) — a stack brought up before the trace layer landed will report `OBSERVE-QUERY-02` (no `VICTORIATRACES_PORT`).

---

## Invocation surface

The skill exposes three shell scripts. Agents invoke them via the `Bash` tool.

```
.claude/skills/cross-cutting/observability-query/scripts/query-logs.sh '<LogQL>' [--limit N]
.claude/skills/cross-cutting/observability-query/scripts/query-metrics.sh '<PromQL>' [--range <duration>] [--step <duration>]
.claude/skills/cross-cutting/observability-query/scripts/query-traces.sh '<trace_id>'
```

All three scripts:

- Source `$REPO_ROOT/.observability/ports.env` to resolve dynamic ports.
- URL-encode / pass the query argument.
- Issue a `curl` request to the VictoriaLogs / VictoriaMetrics / VictoriaTraces endpoint.
- Emit ndjson (logs) or JSON (metrics / traces) on stdout, or a 4-block `OBSERVE-QUERY-NN` error on stderr.

When the user types `/observe logs <query>`, `/observe metrics <query>`, or `/observe trace <trace_id>` in a slash-command surface that maps to this skill, the agent translates it directly to a `Bash` tool call against the corresponding script.

---

## LogQL primer (VictoriaLogs)

Fields available on every event (set by `vector.toml` parse_logback + add_worktree_label transforms):

| Field | Source | Example value |
|---|---|---|
| `service` | docker compose label | `<service-name>` (per project's `<project>/specs/services/<service>/architecture.md` Service Type) |
| `level` | Logback JSON `level` field | `INFO`, `WARN`, `ERROR` |
| `traceId` | Logback MDC `traceId` | `a1b2c3d4-...` |
| `spanId` | Logback MDC `spanId` | `e5f6...` |
| `worktree` | `WORKTREE_HASH` env var | `1c184f58` |
| `message` | Logback JSON `message` field | (free text) |
| `logger` | Logback JSON `logger` field | `<project-package>.<module>.<Class>` (e.g. `com.example.app.application.service.SomeService`) |
| `mdc` | Logback MDC bag | (object) |

Common patterns:

```
{service="<service-name>"} |= "<EventName>"             # services emitting a phrase — substitute project's service per specs/services/
{level="ERROR"}                                          # all errors across all services
{traceId="abc"}                                         # follow one request across services
{service="<service-name>",level=~"WARN|ERROR"}          # filter by enum-like field
```

Refer to the [VictoriaLogs LogQL reference](https://docs.victoriametrics.com/victorialogs/logsql/) for the full grammar.

---

## PromQL primer (VictoriaMetrics)

VictoriaMetrics is a drop-in Prometheus replacement; standard PromQL applies. Series available from each project's services' `/actuator/prometheus` (substitute `<service-name>` per `<project>/specs/services/`):

| Series prefix | Source | Example |
|---|---|---|
| `jvm_memory_used_bytes` | Spring Boot Micrometer JVM binder | `jvm_memory_used_bytes{area="heap",service="<service-name>"}` |
| `http_server_requests_seconds_count` | Spring Boot Micrometer web binder | `rate(http_server_requests_seconds_count[1m])` |
| `system_cpu_usage` | Micrometer system binder | `system_cpu_usage{service="<service-name>"}` |
| `bff_fanout_latency` / `bff_fanout_errors_total` / `bff_aggregation_degrade_count` | console-bff D7 per-domain fan-out attribution (ADR-MONO-017 D7) | `bff_fanout_errors_total{domain="finance"}` |
| `<custom>_count_total` | service-specific Micrometer counter | per-service business metric (see project's observability section) |
| `up` | Vector prometheus_scrape source | scrape target health |

Range queries: `--range 5m` selects `now - 5m` through `now`, step `15s` by default; override step with `--step 30s`.

Refer to the [PromQL reference](https://prometheus.io/docs/prometheus/latest/querying/basics/) for the full grammar.

---

## Trace queries (VictoriaTraces)

`/observe trace <trace_id>` returns the full span tree for one `trace_id` from VictoriaTraces (Jaeger-compatible query API). The trace layer is pinned by [ADR-MONO-007a](../../../../docs/adr/ADR-MONO-007a-trace-layer.md) and ingests OTLP via Vector (producers + console-bff + console-web export to the Vector `:4318` OTLP source, which forwards to VictoriaTraces).

The headline use case is **cross-product fan-out tracing**: a console dashboard request (Operator Overview / Domain Health) assembles as one trace tree —

```
console-web SSR span                    (root — Next.js instrumentation.ts, ADR-MONO-007a D3)
└─ console-bff aggregation span         (RestClient ObservationRegistry adopts the inbound traceparent)
   ├─ gap producer span
   ├─ wms producer span
   ├─ scm producer span
   ├─ finance producer span
   └─ erp producer span
```

= 7 spans sharing one `trace_id`. A degraded card resolves to the specific producer span that errored — the trace complements the `bff_fanout_*` D7 metrics with causal ordering.

```
$ ./.claude/skills/cross-cutting/observability-query/scripts/query-traces.sh 0af7651916cd43dd8448eb211c80319c
```

Find the `trace_id` first via a LogQL query on the correlated `X-Request-Id` / `traceId` MDC field, then pivot to the trace tree. Refer to the [VictoriaTraces docs](https://docs.victoriametrics.com/victoriatraces/) for the query API.

---

## Failure modes (`OBSERVE-QUERY-NN`)

Every script failure emits a 4-block remediation message on stderr matching [`platform/lint-remediation-message-standard.md`](../../../../platform/lint-remediation-message-standard.md). Rule-ID namespace:

| ID | Trigger | Remediation hint |
|---|---|---|
| `OBSERVE-QUERY-01` | Stack not up (`.observability/ports.env` missing or stack containers dead) | Run `./scripts/observability/up.sh` (manual mode) or pass `-Pobservability=on` to the Gradle e2eTest task |
| `OBSERVE-QUERY-02` | Port file present but a stack container is unreachable (mid-tear-down race, OOM kill) or the trace layer port is absent (stack predates ADR-MONO-007a) | Run `down.sh` then `up.sh` to re-cycle |
| `OBSERVE-QUERY-03` | Query syntax error — backend returned 400 with parser message | Consult the LogQL / PromQL primer above; re-check field names and quoting; for traces, the `trace_id` must be hex (no dashes) |
| `OBSERVE-QUERY-04` | No results within the query window (logs / metrics) | Widen the time range (PromQL: `--range 5m` → `--range 1h`) or relax the matcher; the stack works but the data isn't there |
| `OBSERVE-QUERY-05` | Pagination overflow — result exceeded the limit (default 100 lines) | Refine the query with a narrower matcher or pass `--limit 500` to raise the cap |
| `OBSERVE-QUERY-06` | Trace not found for the given `trace_id` (empty span set / 404) | Confirm the `trace_id`; allow trace-export flush latency and retry; verify `OTEL_EXPORTER_OTLP_ENDPOINT` is set so services export |
| `OBSERVE-QUERY-07` | Trace found but incomplete — fewer than the expected fan-out spans (possible broken span chain: a layer dropped / regenerated `trace_id`) | If the dashboard invoked a subset of domains, fewer spans is expected; otherwise check each layer propagates W3C `traceparent` (console-web → console-bff → producers) |

Each remediation block ends with a `[REFERENCE]` line citing this skill's body.

Exit codes mirror the rule IDs: `0` success (incl. `OBSERVE-QUERY-07` which still returns the data), `1` for QUERY-01, `2` for QUERY-02/03, `3` for QUERY-04/06, `4` for QUERY-05. The agent on the next turn reads the structured stderr and follows the remediation text — the same loop closure that gap A delivered for Hard Stops.

---

## Worked examples

### "Why did the inbound test fail?"

```
$ ./.claude/skills/cross-cutting/observability-query/scripts/query-logs.sh \
    '{service="inbound-service",level=~"WARN|ERROR"} |= "ASN"'
```

Expected outcomes:

- ndjson with the failing service's error lines and their `traceId` — agent then follows the trace across services with `{traceId="<id>"}` or pivots to the span tree via `query-traces.sh`.
- `OBSERVE-QUERY-04` if no errors logged — the test failure is not server-side; check the test assertion itself.

### "Did the new feature emit the expected metric?"

```
$ ./.claude/skills/cross-cutting/observability-query/scripts/query-metrics.sh \
    'outbound_tms_request_count_total{service="outbound-service"}'
```

Expected outcomes:

- A series with monotonic increase across recent scrapes — the metric is being emitted.
- Empty result + `OBSERVE-QUERY-04` — either the counter has not been incremented yet, or the registration name diverges from the query. Check the Micrometer counter declaration site.

### "Which domain degraded the Operator Overview?"

```
$ ./.claude/skills/cross-cutting/observability-query/scripts/query-traces.sh \
    0af7651916cd43dd8448eb211c80319c
```

Expected outcomes:

- The 7-span tree with one producer span carrying an error status — that domain caused the degrade.
- `OBSERVE-QUERY-07` if fewer spans than expected — a broken propagation chain (a layer dropped `traceparent`) or the dashboard only invoked a subset of domains.

---

## Limitations

- **No streaming.** All scripts are request-response. For tail-style follow-along, use the VictoriaLogs / VictoriaMetrics / VictoriaTraces web UIs at `http://127.0.0.1:${VICTORIALOGS_PORT}/select/vmui` / `http://127.0.0.1:${VICTORIAMETRICS_PORT}/vmui` / `http://127.0.0.1:${VICTORIATRACES_PORT}/select/vmui` (humans only — agents should prefer scripted queries).
- **1-day retention.** VictoriaLogs / VictoriaMetrics / VictoriaTraces are configured with 1-day retention (see `infra/observability/docker-compose.yml` command args). Queries beyond that window return empty results matching `OBSERVE-QUERY-04` / `-06`.
- **No data across `up.sh` cycles.** Stack restart with `down.sh` + `up.sh` wipes all data (tmpfs storage). This is intentional — ephemeral by design.
- **Trace-export flush latency.** Producers batch-export spans; a trace queried immediately after a request may be incomplete. Allow a few seconds + retry (the `query-traces.sh` `OBSERVE-QUERY-07` hint covers this).
- **Cross-product trace-tree assertion (federation e2e).** Asserting the 7-span tree in CI is `TASK-MONO-144` (federation-hardening-e2e workflow_dispatch); this skill is the interactive query surface.

---

## Cross-references

- [docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md](../../../../docs/adr/ADR-MONO-007-worktree-ephemeral-observability-stack.md) § 2.4 D4 (DX target) and § 2.5 D5 (Phase 2 = this skill)
- [docs/adr/ADR-MONO-007a-trace-layer.md](../../../../docs/adr/ADR-MONO-007a-trace-layer.md) § 2 D6 (trace query DX) — the trace layer this skill queries
- [infra/observability/README.md](../../../../infra/observability/README.md) (operator guide)
- [tasks/done/TASK-MONO-065-observability-stack-scaffolding.md](../../../../tasks/done/TASK-MONO-065-observability-stack-scaffolding.md) (Phase 1 stack)
- [platform/lint-remediation-message-standard.md](../../../../platform/lint-remediation-message-standard.md) (4-block format)
- [.claude/skills/cross-cutting/observability-setup/SKILL.md](../observability-setup/SKILL.md) (sibling skill — instrumentation side)
