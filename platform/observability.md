# Observability

Defines platform-wide standards for logging, metrics, and tracing.

---

# Pillars

1. **Logging** — structured logs for debugging and audit
2. **Metrics** — quantitative indicators of service health
3. **Tracing** — distributed request tracing across services

---

# Logging

## Format

- All logs must be structured JSON in production environments.
- Local development may use human-readable format.

## Required Fields

| Field | Description |
|---|---|
| `timestamp` | ISO 8601 UTC |
| `level` | INFO / WARN / ERROR |
| `service` | Service name as declared in `PROJECT.md` |
| `traceId` | Distributed trace ID (from MDC) |
| `message` | Log message |

## Rules

- Log `INFO` for significant business events (e.g., resource created, state transition committed, external integration call succeeded).
- Log `WARN` for degraded conditions: retry, fallback, slow query.
- Log `ERROR` with full stack trace for unexpected failures.
- Never log: passwords, tokens, card numbers, PII.
- Use MDC (Mapped Diagnostic Context) to propagate `traceId` per request.

---

# Metrics

## Tool

- Spring Boot Actuator + Micrometer → Prometheus → Grafana

## Required Metrics (all services)

Names below are the **Prometheus series names actually exported** (verify by scrape, not by
guessing the Micrometer → Prometheus conversion).

| Metric | Type | Description | Exported by |
|---|---|---|---|
| `http_server_requests_seconds_count` | Counter (Timer) | Total HTTP requests by `method`, `uri`, `status`, `outcome` | Spring Boot (servlet and WebFlux server observation) |
| `http_server_requests_seconds_bucket` | Histogram (Timer) | Request latency buckets — what `histogram_quantile` (p99) needs | Spring Boot; buckets are enabled for `http.server.requests` by `libs/java-observability` (TASK-MONO-836) — a service not using that library must set `management.metrics.distribution.percentiles-histogram.http.server.requests: true` |
| `jvm_memory_used_bytes` | Gauge | JVM heap/non-heap usage | Micrometer JVM binders |
| `hikaricp_connections_active` | Gauge | Active DB connections | HikariCP Micrometer binder (JDBC services) |

## Business Metrics (per service)

Each service must define its own business metrics in `specs/services/<service>/observability.md`.

---

# Tracing

## Tool

- OpenTelemetry (OTLP export) → Jaeger or a compatible backend (the local stack uses
  VictoriaTraces — `docs/adr/ADR-MONO-007a-trace-layer.md`).
- A service exports traces in one of two ways: Micrometer Tracing with the OTel bridge
  (`micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`), or the OpenTelemetry
  Java agent attached in its container image. No repository code creates spans by hand.

## Rules

These bind a service that exports traces. A service with neither mechanism exports none, so
there is nothing for it to propagate.

- Every HTTP request it sends must carry the W3C trace headers (`traceparent`, `tracestate`).
  With Micrometer Tracing that only happens when the client is **observed**: build it from
  Spring Boot's injected `RestClient.Builder` / `WebClient.Builder`, or pass the
  application's `ObservationRegistry` (`.observationRegistry(registry)`, or
  `ResilienceClientFactory.buildRestClient(…, registry)` — the registry is a required
  argument). A client built with `RestClient.builder()` / `RestClient.create()` alone uses
  `ObservationRegistry.NOOP`: no client span, no header, and the receiving service starts a
  new trace. Guard: `scripts/check-outbound-client-observation.mjs`.
- When a trace context exists, its trace ID goes into the log MDC under `traceId`.
- Required spans: **HTTP server** (every handled request) and **HTTP client** (every outbound
  call). Spring Boot creates both from observations; nothing else is required.
- **Not required — and not produced by Micrometer Tracing here:** DB query, Redis operation
  and messaging (Kafka) spans. Spring Boot has no JDBC or Lettuce observation
  auto-configuration, and Kafka observation is off unless `observation-enabled` is set. The
  Java agent instruments these too, so services using it do emit them. Adding them elsewhere
  is a new tracing requirement — document it here first (§ Change Rule).

---

# Health Checks

- All services must expose `GET /actuator/health` returning 200 when healthy.
- Liveness: reports if the process is alive.
- Readiness: reports if the service can handle traffic (DB + cache connectivity).

---

# Alerting Rules (baseline)

| Condition | Severity |
|---|---|
| Error rate > 5% over 5 minutes | CRITICAL |
| P99 latency > 2 seconds | WARNING |
| Service health check failing | CRITICAL |
| DB connection pool exhausted | CRITICAL |

---

# Change Rule

New metrics or tracing requirements must be documented here before implementation.

---

# Spec ↔ Code ↔ Consumers

A metric name lives in three places — the service spec, the code that registers it, and the
alert rules / dashboards that query it. Each direction is guarded. The two directions that ask
"does the code export this name?" share **one** predicate — the one in
`scripts/check-alert-rule-metrics.sh`; the spec guard calls it (`--unexported <project>`) instead
of keeping a second copy:

| Direction | Guard | Fails when |
|---|---|---|
| code → spec | `scripts/check-metric-name-registry.sh` | a meter registered under a literal name is documented in no spec of that project |
| alerts · dashboards → code | `scripts/check-alert-rule-metrics.sh` | an alert `expr` or a Grafana panel `expr` names a metric nothing exports |
| spec → code | `scripts/check-spec-metrics-implemented.mjs` | a spec metric/span table row names something the code does not export, and the row is not marked planned |

Plus `scripts/check-grafana-datasource-uid.mjs`: every datasource a dashboard references must be
one its own project provisions (`infra/grafana/provisioning/datasources/*.yml`).

## Declaring a metric that is not built yet

A spec table may list a metric (or span) before it exists — mark the row by ending its **last
cell** with exactly:

```
 — 🔵 *계획 · 미구현 (TASK-<id>)*
```

and put this legend line once above the table:

```
> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(<UTC date> 전수, TASK-<id>). 알림 · 대시보드가 필요해질 때 구현한다.
```

When it is implemented, delete the mark in the same PR. A **derived** value (a ratio computed
by PromQL from counters, not an instrument of its own) says ``파생 — PromQL: `<expr>` `` in its
last cell instead. Span rows can only pass marked: no repository code creates spans by hand, so
a span name cannot be judged "exported".

## A dashboard panel on a metric that is not built yet

`projects/<p>/infra/grafana/pending-metrics.txt` — one Prometheus name per line, `# <spec
location> — <reason>`. Only names the project's **spec** declares may be listed. It exempts
dashboards only, never an alert rule (an alert on a missing metric can never fire), and the
guard fails once an entry starts being exported (delete it) or once no dashboard queries it
(stale). Delete the file when it empties.

## Naming

Do not guess the Prometheus name from the Micrometer name — register the meter on a real
`PrometheusMeterRegistry` in a test and read the scrape. Measured (TASK-BE-666): Counter
`x.count` → `x_count_total`; Gauge `x.count` → `x_count`; Timer `x.seconds` →
`x_seconds_{count,sum,max}` (no doubled `_seconds`, and `_bucket` only with histogram buckets
on); `-` becomes `_`. A gauge must hold its value through a strong reference —
`registry.gauge(name, 0.0d)` reads `NaN` after the first GC (TASK-BE-667); use
`Gauge.builder(name, supplier)`.

---

# Deprecated Metrics

Project-specific deprecated metrics (single-value lag gauges, obsolete business counters, etc.)
belong in the owning project's `specs/services/<service>/observability.md` or a project-level
changelog — not in this platform document.

As a platform rule, prefer **per-dimension** metrics (e.g., `kafka_consumer_lag{topic,group,partition}`)
over **pre-aggregated** single-value gauges, and let the query layer aggregate as needed.
