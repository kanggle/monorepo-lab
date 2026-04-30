---
name: cost-optimization
description: K8s right-sizing, HPA, spot/ARM, PDB, cost levers
category: infra
---

# Skill: Cost Optimization

Patterns for sizing Kubernetes resources, choosing instance types, and reducing cloud spend without hurting reliability.

Prerequisite: read `platform/deployment-policy.md` and `cross-cutting/performance-tuning/SKILL.md` before using this skill. Monitoring of saturation lives in `infra/monitoring-stack/SKILL.md`.

---

## Resource Requests vs Limits

| Field | Purpose | Default Rule |
|---|---|---|
| `requests.cpu` | Scheduler reservation, basis for HPA | Set to p95 of measured usage |
| `requests.memory` | Scheduler reservation, OOM protection | Set to p95 of measured RSS |
| `limits.cpu` | CPU throttling cap | **Omit** for latency-sensitive services (avoid throttling) |
| `limits.memory` | OOM kill threshold | Set to 1.5x request for safety |

```yaml
resources:
  requests:
    cpu: 200m
    memory: 512Mi
  limits:
    memory: 768Mi   # no cpu limit for latency-sensitive
```

CPU limits cause throttling that masquerades as latency bugs. Use only for batch workloads.

---

## Right-Sizing Workflow

1. Deploy with conservative defaults (`200m / 512Mi`)
2. Run for at least 1 week with realistic traffic
3. Query Prometheus for p95 and p99 of CPU/memory:
   ```promql
   quantile_over_time(0.95, container_cpu_usage_seconds_total{pod=~"order-service-.*"}[7d])
   quantile_over_time(0.95, container_memory_working_set_bytes{pod=~"order-service-.*"}[7d])
   ```
4. Set `requests` to p95 + 20% headroom
5. Repeat quarterly

Use Vertical Pod Autoscaler (VPA) in `recommend` mode for automation, but apply manually.

---

## Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

- Always set `minReplicas >= 2` for HA
- Target 60-70% CPU utilization (room to absorb spikes)
- For event consumers, use custom metric `kafka_consumer_lag` instead of CPU

---

## Pod Disruption Budget (Reliability + Cost)

```yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: order-service
spec:
  minAvailable: 2
  selector:
    matchLabels:
      app.kubernetes.io/name: order-service
```

Prevents cluster autoscaler from killing too many pods at once during scale-down.

---

## Node Pool Strategy

| Pool | Use For | Notes |
|---|---|---|
| On-demand | Critical services with strict SLO | Default for stateful and gateway |
| Spot / preemptible | Stateless, fault-tolerant batch | 60-90% cost saving |
| ARM (Graviton/Tau T2A) | JVM and Node services that build for ARM | 20-40% cost saving |

Mix using node selectors and tolerations:

```yaml
nodeSelector:
  workload-class: stateless
tolerations:
  - key: spot
    operator: Equal
    value: "true"
    effect: NoSchedule
```

---

## Database Cost Levers

| Lever | Impact |
|---|---|
| Right-size instance class | Largest single saving |
| Use read replicas only when needed | Replica counts to actual read load |
| Archive cold data to object storage | Move > 1 year old rows out of hot DB |
| Reserved instances / committed use | 30-60% saving vs on-demand |
| Disable Multi-AZ for non-prod | Halves dev/staging DB cost |

---

## Storage

- Use SSD (`gp3`, `pd-balanced`) by default; only use NVMe for IOPS-bound workloads
- Set retention on log/metric storage (Loki 14-30 days, Tempo 7-14 days)
- Compress backups; lifecycle to glacier after 30 days

---

## Observability

Required cost metrics:
- `kube_pod_container_resource_requests` vs `container_cpu_usage_seconds_total` → over/under-provisioning
- `kube_horizontalpodautoscaler_status_current_replicas` over time → scaling pattern
- Cloud bill exported to BigQuery / Athena, dashboarded in Grafana with `cost-per-service` label

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Setting CPU limits on latency-sensitive services | Omit `limits.cpu` |
| `requests` set to peak, not p95 | Use p95 + 20% |
| `minReplicas: 1` in production | Always >= 2 for HA |
| No PDB → cluster scale-down kills service | Add PDB |
| Spot pool for stateful services | Use on-demand |
| No quarterly right-sizing review | Schedule recurring task |

---

## Verification Checklist

- [ ] `requests` based on p95 measurements, not guesses
- [ ] `limits.cpu` omitted unless workload is batch
- [ ] HPA configured with min >= 2
- [ ] PDB defined
- [ ] Spot/ARM nodes used where workload tolerates
- [ ] Cost dashboard shows per-service spend trend
- [ ] Quarterly right-sizing review scheduled
