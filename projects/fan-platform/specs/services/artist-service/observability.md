# artist-service — Observability

Follows `platform/observability.md`. Metrics / logs / traces emitted by every
endpoint via the standard Spring Boot Actuator + Micrometer wiring; this file
documents the artist-service-specific additions.

---

## Custom metrics

| Metric | Type | Labels | Purpose |
|---|---|---|---|
| `artist_registered_total` | Counter | (none — tenant inferred via tag `application`) | Number of `artist.registered` events appended to the outbox. Tracks new artist throughput; alert if zero for >24h on a live-traffic environment. |
| `artist_outbox_publish_failures_total` | Counter | none | Number of outbox events that failed to publish to Kafka after the polling tick caught them. Alert when rate > 0 for 5 min — indicates broker outage or topic mis-config. |
| `artist_directory_cache_unavailable_total` | Counter | none | Number of directory cache operations that fell back to the DB because Redis was unavailable. Steady non-zero means Redis is unhealthy; spikes correlate with directory latency regressions. |

Outbox lag and pending row counts are inherited from
`libs:java-messaging`'s `OutboxMetricsAutoConfiguration` (gauges
`outbox_pending_count`, `outbox_lag_seconds`).

---

## Logging MDC

Every request decorates the SLF4J MDC with:

- `traceId` — OTel trace id (auto-injected by `micrometer-tracing-bridge-otel`)
- `requestId` — request correlation id (gateway-injected via
  `X-Request-Id`; service back-fills if missing)
- `tenantId` — JWT `tenant_id` claim
- `accountId` — JWT `sub` claim

Pattern (see `logback-spring.xml`):

```
%d{HH:mm:ss.SSS} %-5level [traceId=%X{traceId:-} requestId=%X{requestId:-} tenantId=%X{tenantId:-} accountId=%X{accountId:-}] %logger{36} - %msg%n
```

Production profile (`prod`) emits structured JSON via `LogstashEncoder`.

---

## Traces

OTel spans propagate across:
- HTTP requests (Spring MVC servlet auto-instrumentation)
- Postgres calls (`micrometer-tracing-bridge-otel` JDBC instrumentation)
- Redis calls (Spring Data Redis instrumentation)
- Kafka producer (`spring-kafka` instrumentation; `traceparent` header on
  outbox-relayed messages)

Sampling: 100% in `default`/`local`/`test`. Production tail-based sampling
configured at the OTel Collector (out of scope here).

---

## Health checks

| Endpoint | Probes |
|---|---|
| `/actuator/health` | aggregate (DB ping, Redis ping). Public. |
| `/actuator/health/liveness` | container is up. Public. |
| `/actuator/health/readiness` | DB + Redis + Kafka ready. Public. |
| `/actuator/info` | build / git-sha. Public. |
| `/actuator/prometheus` | metrics scrape endpoint. Public (Prometheus pulls). |

Authenticated endpoints (`/actuator/{env,beans,...}`) are NOT exposed — only
`health,info,prometheus` (see `application.yml`
`management.endpoints.web.exposure.include`).

---

## Alerts (recommended)

| Condition | Severity | Action |
|---|---|---|
| `artist_outbox_publish_failures_total` rate > 0 for 5 min | page | Investigate Kafka health; outbox will drain when broker recovers, but alert immediately. |
| `outbox_pending_count` > 1000 | warn | Outbox backlog growing — Kafka or the relay is throttled. |
| `outbox_lag_seconds` > 60 | warn | Events older than 60s still pending — downstream consumers will lag. |
| `artist_directory_cache_unavailable_total` rate > 0 for 15 min | warn | Redis unhealthy; directory queries hitting DB directly. |
| HTTP 5xx rate > 1% over 5 min | page | Application-level failure. |
| `/actuator/health/readiness` flapping | warn | Dependent service issue (DB/Redis/Kafka). |

---

## Dashboards (recommended)

- Request rate, error rate, p99 latency per endpoint (Spring MVC defaults).
- Outbox lag + pending count (libs metrics).
- artist registration / publish / archive rate.
- Directory cache hit ratio (= 1 - `artist_directory_cache_unavailable_total / total search rate`; or instrument cache hit/miss counters in v2).

v2 may add cache hit/miss counters explicitly; v1 only counts unavailability
(the most actionable signal for fail-open behaviour).
