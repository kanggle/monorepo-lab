---
name: observability-setup
description: End-to-end observability setup (logs, metrics, traces, alerts)
category: cross-cutting
---

# Skill: Observability Setup

Cross-cutting policy for instrumenting a service end-to-end (logs, metrics, traces, alerts).

Prerequisite: read `platform/observability.md` before using this skill. Concrete Micrometer/OTel patterns live in `backend/observability-metrics/SKILL.md`. Stack deployment lives in `infra/monitoring-stack/SKILL.md`.

---

## Three Pillars Mapping

| Pillar | Tool | What to Capture | Where |
|---|---|---|---|
| Logs | structured JSON (Logback) | Events, errors, audit trail | stdout → Loki |
| Metrics | Micrometer + Prometheus | Counters, gauges, histograms | `/actuator/prometheus` |
| Traces | OpenTelemetry SDK | Cross-service request flow | OTLP → Tempo/Jaeger |

Every service must enable all three before reaching production.

---

## Log Format

JSON only. Required fields:

```json
{
  "timestamp": "2026-04-12T08:30:00.000Z",
  "level": "INFO",
  "service": "order-service",
  "traceId": "...",
  "spanId": "...",
  "userId": "...",
  "message": "Order placed",
  "orderId": "..."
}
```

- Never log secrets, tokens, or full PII (mask emails to `u***@d***.com`)
- Use MDC (`org.slf4j.MDC`) to attach `traceId`, `userId`, `requestId` to every log within a request
- Log level guidelines: ERROR for failures requiring investigation, WARN for recoverable, INFO for state transitions, DEBUG for development only

---

## MDC Propagation

```java
@Component
public class MdcFilter extends OncePerRequestFilter {
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) {
        try {
            MDC.put("requestId", Optional.ofNullable(req.getHeader("X-Request-Id")).orElse(UUID.randomUUID().toString()));
            MDC.put("userId", extractUserId(req));
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

For event consumers, propagate trace context via Kafka headers using OTel `KafkaPropagator`.

---

## Metrics Naming

Format: `<domain>_<entity>_<measurement>_<unit>`

```
order_placed_total              # counter
order_processing_duration_seconds  # histogram
payment_pending_count           # gauge
cache_hit_total{cache="product"} # counter with label
```

Always include unit suffix (`_seconds`, `_bytes`, `_total`). Use labels sparingly (cardinality < 100 per metric).

---

## OpenTelemetry Setup

```yaml
# application.yml
otel:
  service:
    name: order-service
  exporter:
    otlp:
      endpoint: http://otel-collector:4317
  traces:
    sampler: parentbased_traceidratio
    sampler.arg: 0.1   # 10% sampling in production
```

- Always-on for errors (override sampler for spans with errors)
- Inject trace context into outbound HTTP, Kafka, and database calls

---

## Dashboard Locations

| Type | Location |
|---|---|
| Service overview dashboard | `infra/grafana/dashboards/<service>-overview.json` |
| Alerting rules | `infra/prometheus/rules/<service>.yml` |
| SLO definitions | `specs/services/<service>/slo.md` |

Every service must have at minimum: request rate, error rate, p50/p95/p99 latency, and saturation panel.

---

## Alert Rules (RED/USE Method)

| Signal | Threshold (default) | Severity |
|---|---|---|
| Error rate > 1% over 5 min | warn | P3 |
| Error rate > 5% over 5 min | crit | P1 |
| p99 latency > SLO * 1.5 over 10 min | warn | P3 |
| Saturation (CPU > 80%, mem > 85%) over 5 min | warn | P3 |
| Service down (no scrape) over 2 min | crit | P1 |

Tune per service in `infra/prometheus/rules/`.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Logging PII in plain text | Mask at filter level, audit logs separately |
| Unbounded label cardinality | Whitelist allowed label values |
| Missing trace context across async boundary | Use OTel context propagation explicitly |
| Sampling 100% in production | Use head-based sampling 1-10%, always-on for errors |
| Dashboards not in git | All dashboards as code under `infra/grafana/` |

---

## Verification Checklist

- [ ] JSON log format with required fields
- [ ] MDC filter installed and tested
- [ ] `/actuator/prometheus` exposes service metrics
- [ ] OTel traces visible end-to-end across at least 2 service hops
- [ ] Grafana dashboard committed under `infra/grafana/dashboards/`
- [ ] Alert rules committed under `infra/prometheus/rules/`
- [ ] SLO defined in service spec
