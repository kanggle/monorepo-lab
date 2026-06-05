# membership-service — Observability

> Spec authored by **TASK-FAN-BE-008**. Implementation = **TASK-FAN-BE-009**.

## Metrics (Micrometer / Prometheus)

| Name | Type | Tags | Purpose |
|---|---|---|---|
| `membership_outbox_publish_failures_total` | Counter | (none) | outbox → Kafka send failures (drives alerting on broker outages) |
| `membership_payment_declined_total` | Counter | (none) | PG mock declines (funnel + fraud-signal placeholder) |
| `membership_access_check_total` | Counter | `result` (`allow`/`deny`) | internal access-check volume + grant/deny ratio |
| `membership_access_check_fail_closed_total` | Counter | (none) | access-checks that denied due to infra error (fail-closed path) — alert when > 0 |
| `http.server.requests` | Timer (Spring Boot default) | `method`, `uri`, `status` | per-endpoint latency + traffic |
| `jvm.*`, `process.*`, `system.*` | gauges | (default) | runtime telemetry |

Future v2 additions (declared here so downstream alert rules can be authored ahead of time):

- `membership_outbox_lag_seconds` — gauge of `now - oldest PENDING.created_at`. Alert when > 60s.
- `membership_active_total` — gauge tagged by `tier`. Drives subscription dashboards.
- `membership_expired_total` — only meaningful once a future expiry sweeper exists (read-time expiry has no event in v1).

Endpoint: `GET /actuator/prometheus` (unauthenticated, scraped on the internal
docker network only — NOT gateway-routed, matching community-service /
TASK-FAN-BE-004 network isolation).

## Tracing (OpenTelemetry)

- Bridge: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`.
- Sampling: 100 % in dev, 10 % default in prod (override via `management.tracing.sampling.probability`).
- Trace context propagation: `traceparent` / `tracestate` headers honored for inbound requests (including the community → membership internal access-check call); outbound HTTP carries the same headers automatically.
- Spans: every controller method, every JPA query, every Kafka send, every PG mock authorize call.

## Logging (Logback)

- Pattern includes MDC keys: `traceId`, `requestId`, `tenantId`, `accountId`.
- `prod` profile uses `LogstashEncoder` (JSON lines).
- `!prod` profiles use the human-readable pattern (matches gateway/community-service convention).
- Levels:
  - `root` — INFO
  - `com.example.fanplatform.membership` — INFO (DEBUG in dev for state machine + outbox + PG mock tracing)
  - `org.hibernate.SQL` — WARN (set to DEBUG when investigating query issues)
- **PII / payment hygiene**: NEVER log the raw `paymentToken` or `Idempotency-Key`
  value; log only the derived `paymentRef` and a key fingerprint.

## Health checks

- `/actuator/health` (public) — Spring Boot composite (DB, Kafka, Disk).
- `/actuator/health/liveness`, `/actuator/health/readiness` — K8s probes.
- `/actuator/info` (public) — git commit, build version, profile.

## Audit

- Subscription lifecycle is auditable via the Kafka stream — `fan.membership.activated.v1`
  / `fan.membership.canceled.v1` flow through Kafka, so a downstream auditor can
  replay them at any time.
- The `memberships` row itself records `created_at` / `canceled_at` for point-in-time
  reconstruction; `paymentRef` ties a row to its PG mock authorization.
- (Note: expiry has no audit event in v1 because it is read-time; it is derivable
  from `valid_to`.)

## Dashboards (planned)

When alerting infra arrives:

- **Outbox lag** — `membership_outbox_publish_failures_total` rate + lag gauge.
- **Access-check fail-closed** — `membership_access_check_fail_closed_total` rate (anomaly when > 0 — signals membership-service degradation affecting community gating).
- **Cross-tenant attempts** — count of 403 `TENANT_FORBIDDEN` responses per minute (anomaly when > 0).
- **Payment decline rate** — `membership_payment_declined_total` rate (funnel health).

These dashboards live with the platform observability stack rather than per-service.
