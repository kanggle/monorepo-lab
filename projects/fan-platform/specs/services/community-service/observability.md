# community-service — Observability

## Metrics (Micrometer / Prometheus)

| Name | Type | Tags | Purpose |
|---|---|---|---|
| `community_outbox_publish_failures_total` | Counter | (none) | outbox → Kafka send failures (drives alerting on broker outages) |
| `community_feed_cache_unavailable_total` | Counter | (none) | Redis cache R/W errors (drives alerting on cache outages) |
| `community_feed_cache_hits_total` | Counter | (none) | feed cache read hits (page query skipped; live counts are still read). Registered in `FeedCacheRepository` |
| `community_feed_cache_misses_total` | Counter | (none) | feed cache read misses (empty entry, or malformed entry treated as a miss) that fall through to the DB; hit ratio = hits / (hits + misses) |
| `http.server.requests` | Timer (Spring Boot default) | `method`, `uri`, `status` | per-endpoint latency + traffic |
| `jvm.*`, `process.*`, `system.*` | gauges | (default) | runtime telemetry |

Future v2 additions (declared here so downstream alert rules can be authored ahead of time):

- `community_outbox_lag_seconds` — gauge of `now - oldest PENDING.created_at`. Alert when > 60s.
- `community_post_published_total` — counter tagged by `visibility`/`postType`. Drives funnel dashboards.

Endpoint: `GET /actuator/prometheus` (unauthenticated, rate-limited at the gateway).

## Tracing (OpenTelemetry)

- Bridge: `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`.
- Sampling: 100 % in dev, 10 % default in prod (override via `management.tracing.sampling.probability`).
- Trace context propagation: `traceparent` / `tracestate` headers honored for inbound requests; outbound HTTP carries the same headers automatically.
- Spans: every controller method, every JPA query, every Redis op, every Kafka send.

## Logging (Logback)

- Pattern includes MDC keys: `traceId`, `requestId`, `tenantId`, `accountId`.
- `prod` profile uses `LogstashEncoder` (JSON lines).
- `!prod` profiles use the human-readable pattern (matches gateway-service convention).
- Levels:
  - `root` — INFO
  - `com.example.fanplatform.community` — INFO (DEBUG in dev for state machine + outbox tracing)
  - `org.hibernate.SQL` — WARN (set to DEBUG when investigating query issues)

## Health checks

- `/actuator/health` (public) — Spring Boot composite (DB, Redis, Kafka, Disk).
- `/actuator/health/liveness`, `/actuator/health/readiness` — K8s probes.
- `/actuator/info` (public) — git commit, build version, profile.

## Audit

- `post_status_history` is the in-database audit trail. INSERT-only (enforced at the application layer; v2 may add DB grants).
- All events flow through Kafka, so a downstream auditor can replay them at any time.

## Dashboards (planned)

When alerting infra arrives:

- **Outbox lag** — `community_outbox_publish_failures_total` rate + lag gauge.
- **Cross-tenant attempts** — count of 403 `TENANT_FORBIDDEN` responses per minute (anomaly when > 0).
- **State machine violations** — count of 422 `POST_STATUS_TRANSITION_INVALID` per minute (drives review when client integrations regress).

These dashboards live with the platform observability stack rather than per-service.
