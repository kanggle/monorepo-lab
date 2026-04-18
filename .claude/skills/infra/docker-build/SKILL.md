---
name: docker-build
description: Docker image build patterns
category: infra
---

# Skill: Docker Build

Patterns for building Docker images in this repository.

Prerequisite: read `platform/deployment-policy.md` before using this skill.

---

## Reference Templates

| Target | File |
|---|---|
| Spring Boot service (multi-stage + OTel agent + layer extraction) | [`templates/backend.Dockerfile`](templates/backend.Dockerfile) |
| Next.js app (standalone output, pnpm workspace) | [`templates/frontend.Dockerfile`](templates/frontend.Dockerfile) |

Both templates use `{service}` / `{app}` as placeholders for the app module name. Replace when copying into a new service.

---

## Key Patterns

### Spring Boot Layer Extraction

Layer extraction enables efficient Docker layer caching:

```
dependencies/          ← rarely changes (cached)
spring-boot-loader/    ← rarely changes (cached)
snapshot-dependencies/ ← changes occasionally
application/           ← changes every build
```

### Non-Root User

Always run as non-root:

```dockerfile
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

### Health Check

```dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8081/actuator/health || exit 1
```

### JVM Memory

```
-XX:MaxRAMPercentage=75.0
```

Uses 75% of container memory limit. Set memory limits in Docker Compose or Kubernetes.

---

## Docker Compose Build

```yaml
services:
  example-service:
    build:
      context: .
      dockerfile: apps/example-service/Dockerfile
    deploy:
      resources:
        limits:
          memory: 512M
```

---

## Common Pitfalls

| Pitfall | Fix |
|---|---|
| Running as root | Always use `USER appuser` |
| No health check | Add `HEALTHCHECK` for orchestrator readiness |
| COPY entire project in one layer | Use multi-stage + layer extraction for caching |
| No memory limit | Set `-XX:MaxRAMPercentage` + container memory limit |
| Missing `.dockerignore` | Exclude `build/`, `node_modules/`, `.git/` |
