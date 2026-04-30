---
name: monitoring-stack
description: Prometheus/Grafana/Loki/Alertmanager stack as code
category: infra
---

# Skill: Monitoring Stack

Patterns for deploying Prometheus, Grafana, Loki, and alert routing for the monorepo's services.

Prerequisite: read `platform/observability.md` and `cross-cutting/observability-setup/SKILL.md` before using this skill. Service-side instrumentation lives in `backend/observability-metrics/SKILL.md`.

---

## Stack Components

| Component | Purpose | Source of Truth |
|---|---|---|
| Prometheus | Metric scraping, recording rules, alerting | `infra/prometheus/` |
| Grafana | Dashboards, ad-hoc queries | `infra/grafana/dashboards/` |
| Loki | Log aggregation | `infra/loki/` |
| Tempo (or Jaeger) | Distributed traces | `infra/tempo/` |
| Alertmanager | Alert routing, deduplication, silencing | `infra/alertmanager/` |
| OTel Collector | Receive OTLP, fan out to Prom/Loki/Tempo | `infra/otel-collector/` |

All configuration lives **as code** under `infra/` and is deployed via the same CI pipeline as services.

---

## Prometheus ServiceMonitor

```yaml
# infra/prometheus/service-monitors/order-service.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: order-service
  namespace: monitoring
  labels:
    release: kube-prometheus-stack
spec:
  selector:
    matchLabels:
      app.kubernetes.io/name: order-service
  endpoints:
    - port: actuator
      path: /actuator/prometheus
      interval: 30s
      scrapeTimeout: 10s
  namespaceSelector:
    matchNames:
      - default
```

Every service Helm chart must expose an `actuator` port and have a matching ServiceMonitor.

---

## Recording Rules

Pre-aggregate hot queries to reduce dashboard load:

```yaml
# infra/prometheus/rules/order-service.yaml
groups:
  - name: order-service-recording
    interval: 30s
    rules:
      - record: order_service:http_request_rate:5m
        expr: sum(rate(http_server_requests_seconds_count{service="order-service"}[5m])) by (uri, status)
      - record: order_service:http_error_ratio:5m
        expr: |
          sum(rate(http_server_requests_seconds_count{service="order-service",status=~"5.."}[5m]))
          /
          sum(rate(http_server_requests_seconds_count{service="order-service"}[5m]))
```

---

## Alert Rules

```yaml
# infra/prometheus/rules/order-service-alerts.yaml
groups:
  - name: order-service-alerts
    rules:
      - alert: OrderServiceHighErrorRate
        expr: order_service:http_error_ratio:5m > 0.05
        for: 5m
        labels:
          severity: critical
          team: orders
        annotations:
          summary: order-service 5xx ratio above 5%
          runbook: https://runbooks.example.com/order-service/high-error-rate
      - alert: OrderServiceHighLatencyP99
        expr: histogram_quantile(0.99, sum by (le) (rate(http_server_requests_seconds_bucket{service="order-service"}[5m]))) > 1.2
        for: 10m
        labels:
          severity: warning
          team: orders
```

Every alert must have a `runbook` annotation and an owning `team` label.

---

## Alertmanager Routing

```yaml
# infra/alertmanager/config.yaml
route:
  receiver: default
  group_by: [alertname, service]
  routes:
    - match: { severity: critical }
      receiver: pagerduty
      continue: true
    - match: { team: orders }
      receiver: slack-orders
receivers:
  - name: default
    slack_configs:
      - channel: '#alerts'
  - name: pagerduty
    pagerduty_configs:
      - routing_key: '${PAGERDUTY_KEY}'
  - name: slack-orders
    slack_configs:
      - channel: '#alerts-orders'
```

---

## Grafana Dashboards as Code

- All dashboards exported as JSON under `infra/grafana/dashboards/`
- Provisioned via ConfigMap in K8s — no UI-only edits in production
- Naming: `<service>-overview.json`, `<service>-deep-dive.json`
- Each service dashboard must include: request rate, error rate, latency p50/p95/p99, saturation (CPU/mem), JVM/event-loop, dependencies (DB, cache, downstream)

---

## Loki Log Querying

```logql
# All errors for order-service in last 1h
{service="order-service"} |= "ERROR" | json | level="ERROR"

# Trace correlation
{service="order-service"} | json | traceId="abc123def456"
```

Logs must include `service`, `traceId`, `level` labels for query.

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Dashboards edited only in UI | Export to JSON, commit |
| Alerts without runbook | Required field |
| Cardinality explosion (label per user) | Whitelist label values |
| Missing scrape interval tuning | 30s default, 15s only for hot services |
| No alert silencing during deploys | Use `amtool silence` in deploy script |
| Single Slack channel for all alerts | Route by team label |

---

## Verification Checklist

- [ ] ServiceMonitor exists for the service
- [ ] Recording rules defined for hot queries
- [ ] Alert rules with `runbook` and `team`
- [ ] Dashboard JSON committed under `infra/grafana/dashboards/`
- [ ] Loki labels include `service`, `traceId`, `level`
- [ ] Alertmanager route configured for the team
- [ ] End-to-end test: trigger a synthetic 5xx and confirm alert fires within 5 min
