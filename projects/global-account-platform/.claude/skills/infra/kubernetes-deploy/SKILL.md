---
name: kubernetes-deploy
description: Kubernetes deployment manifests
category: infra
---

# Skill: Kubernetes Deployment

Patterns for Kubernetes manifests in this repository.

Prerequisite: read `platform/deployment-policy.md` before using this skill.

---

## Service Manifest Structure

Each service has manifests in `k8s/services/{service}/`:

```
k8s/services/auth-service/
├── deployment.yaml
├── service.yaml
└── configmap.yaml
```

---

## Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  labels:
    app: auth-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: auth-service
  template:
    metadata:
      labels:
        app: auth-service
    spec:
      containers:
        - name: auth-service
          image: auth-service:latest
          ports:
            - containerPort: 8081
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
          envFrom:
            - configMapRef:
                name: auth-service-config
          resources:
            requests:
              cpu: 250m
              memory: 384Mi
            limits:
              cpu: 500m
              memory: 512Mi
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: 8081
            initialDelaySeconds: 30
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: 8081
            initialDelaySeconds: 60
            periodSeconds: 15
```

---

## Service

```yaml
apiVersion: v1
kind: Service
metadata:
  name: auth-service
spec:
  selector:
    app: auth-service
  ports:
    - port: 8081
      targetPort: 8081
  type: ClusterIP
```

---

## ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: auth-service-config
data:
  SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres:5432/auth_db"
  SPRING_DATA_REDIS_HOST: "redis"
  SPRING_KAFKA_BOOTSTRAP_SERVERS: "kafka:9092"
```

Secrets (passwords, JWT keys) use `Secret` resources — not ConfigMaps.

---

## Key Patterns

### Health Probes

| Probe | Path | Purpose |
|---|---|---|
| Readiness | `/actuator/health/readiness` | Ready to receive traffic |
| Liveness | `/actuator/health/liveness` | Process is alive |

### Resource Limits

| Service Type | CPU Request | CPU Limit | Memory Request | Memory Limit |
|---|---|---|---|---|
| Backend service | 250m | 500m | 384Mi | 512Mi |
| Gateway | 250m | 500m | 384Mi | 512Mi |
| Frontend | 100m | 250m | 128Mi | 256Mi |

### Rolling Update Strategy

```yaml
spec:
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| No resource limits | Always set requests and limits |
| Missing readiness probe | Traffic routed to unready pods |
| Secrets in ConfigMap | Use `kind: Secret` for sensitive values |
| `latest` tag in production | Use versioned image tags |
| No `maxUnavailable: 0` | Allows downtime during rollout |
