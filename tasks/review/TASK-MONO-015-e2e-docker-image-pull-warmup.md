# TASK-MONO-015 — E2E CI: pre-pull eclipse-temurin base image to fix 30-min timeout

> **Prerequisite of TASK-INT-008 fix.** TASK-INT-008 (PR #92) parallelised
> Scenario 3 requests but the job still times out because `startInfrastructure()`
> (the `@BeforeAll`) itself consumes all 30 minutes building Docker images via
> `ImageFromDockerfile`. The root cause: CI runners have an empty Docker layer
> cache, so `docker build` has to pull `eclipse-temurin:21-jre-alpine` (~150 MB
> compressed) for both `master-service` and `gateway-service` before the first
> Testcontainers container is running. This pull alone accounts for 20–25 minutes.

## Goal

Add a `docker pull eclipse-temurin:21-jre-alpine` step to the
`e2e-tests` CI job so the Docker layer cache is warm before `ImageFromDockerfile`
calls `docker build` for master-service and gateway-service. With the base
layers already local, each `docker build` completes in seconds instead of
10–15 minutes.

## Scope

**Single file: `.github/workflows/ci.yml`**

Insert a new step **between** "Verify Docker" and "Run gateway-master e2e suite":

```yaml
- name: Pre-pull base image (warm Docker layer cache)
  run: docker pull eclipse-temurin:21-jre-alpine
```

No other changes. Java code (`E2EBase.java`, `GatewayMasterE2ETest.java`) is
unchanged. Only the `e2e-tests` job is affected.

**Not in scope:**
- Other CI jobs.
- Docker build caching via Buildx / GitHub cache (more complex; not needed).
- Changing the Dockerfile base image.
- Timeout increase (existing 30 min should be sufficient after the fix).

## Acceptance Criteria

1. The `e2e-tests` job in `.github/workflows/ci.yml` has a new step
   `Pre-pull base image (warm Docker layer cache)` that runs
   `docker pull eclipse-temurin:21-jre-alpine` immediately before the
   Gradle e2eTest step.
2. No other jobs or steps are modified.
3. CI `E2E (gateway-master live-pair, Testcontainers)` completes within
   30 minutes (previously consumed 29+ min just in `@BeforeAll`).

## Related Specs

- `.github/workflows/ci.yml`
- `projects/wms-platform/apps/master-service/Dockerfile` (FROM eclipse-temurin:21-jre-alpine)
- `projects/wms-platform/apps/gateway-service/Dockerfile` (FROM eclipse-temurin:21-jre-alpine)

## Related Contracts

None.

## Edge Cases

- **Both Dockerfiles use the same base image**: a single `docker pull` warms
  the cache for both `master-service` and `gateway-service` builds.
- **Base image already cached** (e.g. runner reuse): `docker pull` is a no-op
  (image up to date); no harm done.
- **`docker pull` fails** (Docker Hub rate limit or network error): the step
  fails fast and the job fails before wasting 30 minutes — better than silently
  timing out.

## Failure Scenarios

- **Job still times out after pull warmup**: one of the Dockerfiles was changed
  to use a different base image. Check that both Dockerfiles still `FROM
  eclipse-temurin:21-jre-alpine`.
- **Docker Hub rate limit on `docker pull`**: authenticated pull or switch to
  ECR/GHCR mirror (out of scope for this task — file a follow-up).
