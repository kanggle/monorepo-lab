# Docker build and E2E compose guide

Human reference for running the platform services in containers.
Covers TASK-BE-041a foundation (build + compose up + healthcheck). Scenario
tests are covered separately by TASK-BE-041c.

## Prerequisites

- Docker 24+ with BuildKit
- Docker Compose v2
- ~4 GB free RAM for all 4 services + MySQL/Redis/Kafka
- Host ports 18081/18082/18084/18085 free (mapped to container 8081/8082/8084/8085)

## 1. Build bootJars (REQUIRED before `docker compose build`)

Images copy the pre-built bootJar from `apps/<svc>/build/libs/`. Always run
this first:

```bash
./gradlew :apps:admin-service:bootJar \
          :apps:auth-service:bootJar \
          :apps:account-service:bootJar \
          :apps:security-service:bootJar
```

Rationale: building Gradle inside BuildKit is slow and brittle against
transient DNS / plugin-portal failures. Building on the host keeps the
image build under ~30s per service while preserving a multi-stage Dockerfile
(the final image ships only JRE + jar, no JDK/Gradle layers).

## 2. Build images

```bash
docker compose -f docker-compose.e2e.yml build
```

First build downloads Gradle + dependencies inside the build stage — expect
5-15 min. Subsequent builds reuse the Docker layer cache.

## 3. Start the stack

```bash
docker compose -f docker-compose.e2e.yml up -d
docker compose -f docker-compose.e2e.yml ps
```

Wait 30-60s after `up -d` for all services to report `healthy`.

## 4. Verify health

```bash
curl -fsS http://localhost:18081/actuator/health   # auth
curl -fsS http://localhost:18082/actuator/health   # account
curl -fsS http://localhost:18084/actuator/health   # security
curl -fsS http://localhost:18085/actuator/health   # admin
```

## 5. Tear down

```bash
docker compose -f docker-compose.e2e.yml down -v
```

## Notes

- `docker-compose.yml` (local dev) and `docker-compose.e2e.yml` (this file)
  are independent and do not share networks or volumes.
- admin-service loads `db/migration` + `db/migration-dev` in the e2e profile
  so the V0014 dev SUPER_ADMIN seed is present. Other services load only
  `db/migration`.
- `ADMIN_JWT_V1_PEM` and `ADMIN_TOTP_V1_KEY` embedded in
  `docker-compose.e2e.yml` are fixed TEST values. They must never be used in
  production.
- For ARM64 (Apple Silicon) hosts, images build natively. To force x86
  parity, add `platform: linux/amd64` under each service.
