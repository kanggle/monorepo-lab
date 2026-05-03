# Prometheus Scrape Configuration — fan-platform

## Design

fan-platform services expose `/actuator/prometheus` on the **internal docker
network only**. The gateway does not route this path externally (see
`specs/services/gateway-service/architecture.md` § "Prometheus scrape endpoint —
network isolation" and `specs/contracts/http/community-api.md` § "Health /
metrics").

This is the network-isolation approach (TASK-FAN-BE-004 option c). Prometheus
must run inside `fan-platform-net` (or on a network that can reach it) to scrape.

---

## docker-compose scrape targets

Add a `prometheus` service to `docker-compose.yml` (or a dev overlay) on
`fan-platform-net`:

```yaml
prometheus:
  image: prom/prometheus:v2.52.0
  container_name: fan-platform-prometheus
  volumes:
    - ./infra/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
    - prometheus-data:/prometheus
  command:
    - --config.file=/etc/prometheus/prometheus.yml
    - --storage.tsdb.path=/prometheus
    - --storage.tsdb.retention.time=7d
  expose:
    - "9090"
  networks:
    - fan-platform-net
```

---

## prometheus.yml

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: gateway-service
    static_configs:
      - targets: ["gateway-service:8080"]
    metrics_path: /actuator/prometheus

  - job_name: community-service
    static_configs:
      - targets: ["community-service:8080"]
    metrics_path: /actuator/prometheus

  - job_name: artist-service
    static_configs:
      - targets: ["artist-service:8080"]
    metrics_path: /actuator/prometheus
```

---

## Scrape interval guidance

| Parameter | Recommended value | Rationale |
|---|---|---|
| `scrape_interval` | 15s | Matches Spring Boot Actuator default; gives 4 data points/min |
| `scrape_timeout` | 10s | Must be < `scrape_interval`; 10s gives room for a slow JVM GC pause |
| Prometheus retry on target failure | 3 retries (Prometheus default) | Avoid false gaps on transient hiccup |

Avoid `scrape_interval < 5s` — the token bucket for Prometheus's own
retries could briefly deplete under high scrape load, and the endpoint does not
need sub-5s resolution for portfolio observability.

---

## IP allowlist (production hardening)

In production (K8s or a hardened docker host), restrict `/actuator/prometheus`
at the network layer or via Spring Security's `management.server.port` isolation:

```yaml
management:
  server:
    port: 9090          # Bind actuator to a dedicated port
```

Then expose port 9090 only within the cluster/internal network, keeping port
8080 as the application port (routed through the gateway). This ensures the
prometheus endpoint is physically unreachable from the application port even if
a misconfiguration exposes the application port externally.

---

## Kubernetes scrape annotations (future)

When deploying to Kubernetes, add pod annotations so a Prometheus Operator
`PodMonitor` or the default scrape discovery picks up the targets:

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: /actuator/prometheus
```

Pair this with a `NetworkPolicy` that allows ingress on port 8080 only from the
Prometheus namespace — equivalent to the docker network isolation used locally.
