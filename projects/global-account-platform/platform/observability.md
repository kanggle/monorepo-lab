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
| `service` | Service name (e.g. `auth-service`) |
| `traceId` | Distributed trace ID (from MDC) |
| `message` | Log message |

## Rules

- Log `INFO` for business events: user registered, order placed, payment confirmed.
- Log `WARN` for degraded conditions: retry, fallback, slow query.
- Log `ERROR` with full stack trace for unexpected failures.
- Never log: passwords, tokens, card numbers, PII.
- Use MDC (Mapped Diagnostic Context) to propagate `traceId` per request.

---

# Metrics

## Tool

- Spring Boot Actuator + Micrometer → Prometheus → Grafana

## Required Metrics (all services)

| Metric | Type | Description |
|---|---|---|
| `http_requests_total` | Counter | Total HTTP requests by method, path, status |
| `http_request_duration_seconds` | Histogram | Request latency |
| `jvm_memory_used_bytes` | Gauge | JVM heap/non-heap usage |
| `db_connection_pool_active` | Gauge | Active DB connections |

## Business Metrics (per service)

Each service must define its own business metrics in `specs/services/<service>/observability.md`.

---

# Tracing

## Tool

- OpenTelemetry → Jaeger (or compatible backend)

## Rules

- All HTTP requests must propagate trace headers (`traceparent`, `tracestate`).
- Services must inject trace IDs into log MDC for correlation.
- Spans must be created for: HTTP handler, DB query, Redis operation, external service call.

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

# Deprecated Metrics

- `security_consumer_lag` (aggregate, single-value): removed in TASK-BE-031-fix.
  Use the per-partition gauge `kafka_consumer_lag{topic,group,partition}` and
  aggregate in the query layer, e.g.
  `sum(kafka_consumer_lag{service="security-service"})`.
