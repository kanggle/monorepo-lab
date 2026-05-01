# TASK-MONO-015 — E2E CI: pre-pull eclipse-temurin base image to fix 30-min timeout

> **Note (2026-04-29 spec amendment via TASK-MONO-016):** the original goal of
> this task was a single `docker pull eclipse-temurin:21-jre-alpine` warmup step
> in the `e2e-tests` job. During implementation we discovered that the layer
> cache was not the root cause: Docker 28's BuildKit-routed REST `/build`
> endpoint hangs indefinitely when invoked through the Docker Java client
> bundled in Testcontainers 1.21.3, so `ImageFromDockerfile` itself never makes
> progress (verified by 58-min and 60-min diagnostic CI runs). The implementation
> therefore pivoted: instead of warming the layer cache, we replaced
> `ImageFromDockerfile` entirely with pre-built images produced by the Docker
> CLI (BuildKit) and consumed by `E2EBase.java` via `-Dwms.e2e.masterImage` /
> `-Dwms.e2e.gatewayImage` system properties. The original filename is kept for
> traceability; the sections below describe what was actually shipped. The
> follow-up audit task is TASK-MONO-016.
>
> **Prerequisite of TASK-INT-008 fix.** TASK-INT-008 (PR #92) parallelised
> Scenario 3 requests but the job still timed out because `startInfrastructure()`
> (the `@BeforeAll`) itself consumed all 30 minutes — not on layer pulls, as
> originally suspected, but on `ImageFromDockerfile` blocking on the Docker 28
> BuildKit handshake.

## Goal

Stop using `ImageFromDockerfile` in the gateway-master E2E suite and switch the
`e2e-tests` CI job to **pre-built service images** produced by the Docker CLI
before Gradle is invoked. The job builds `wms-master-service:e2e` and
`wms-gateway-service:e2e` via `docker build` and passes the image references
to `E2EBase.java` through `-Dwms.e2e.masterImage` and `-Dwms.e2e.gatewayImage`
system properties so the harness instantiates `GenericContainer<>(imageName)`
directly. This works around the Docker 28 / Docker Java client incompatibility
described in the note above and brings `@BeforeAll` from a hard timeout
(60 min) down to seconds.

The boot jars produced by the existing `boot-jars` job are reused as the build
context: each Dockerfile's final stage `COPY`s `apps/<service>/build/libs/<service>.jar`,
so the e2e job downloads the artifact, restores the canonical paths, and runs
`docker build` against the populated workspace.

## Scope

**File: `.github/workflows/ci.yml` — `e2e-tests` job only.**

Modifications to the `e2e-tests` job:

1. `needs:` changed from `[build-and-test]` to `[build-and-test, boot-jars]`
   so the e2e job consumes the `wms-boot-jars` artifact.
2. New step **"Download boot jars"** (`actions/download-artifact@v4`) pulling
   the `wms-boot-jars` artifact into `artifact-staging/`.
3. New step **"Restore boot jar paths"** that moves
   `artifact-staging/master-service/build/libs/master-service.jar` and
   `artifact-staging/gateway-service/build/libs/gateway-service.jar` back to
   the canonical
   `projects/wms-platform/apps/<service>/build/libs/<service>.jar` layout each
   Dockerfile expects (the upload-artifact@v4 common-prefix strip removed the
   `projects/wms-platform/apps/` prefix).
4. New step **"Build service images for e2e"** invoking
   `docker build -t wms-master-service:e2e -f projects/wms-platform/apps/master-service/Dockerfile projects/wms-platform/apps/master-service/`
   and the equivalent for `gateway-service`. The Docker CLI uses BuildKit
   directly, bypassing the Docker Java client REST path that hangs on Docker 28.
5. The existing **"Run gateway-master e2e suite"** Gradle step gains
   `-Dwms.e2e.masterImage=wms-master-service:e2e` and
   `-Dwms.e2e.gatewayImage=wms-gateway-service:e2e` system properties so
   `E2EBase.java` skips `ImageFromDockerfile` and uses the pre-built tags.

No changes to:

- Java sources (`E2EBase.java` already supports the `-Dwms.e2e.*Image`
  override path; this task only wires the CI step to pass them).
- Dockerfiles (`master-service/Dockerfile`, `gateway-service/Dockerfile`).
- Other CI jobs (`build-and-test`, `boot-jars`, `integration-tests`,
  frontend-* jobs are untouched).

**Not in scope:**

- Docker build caching via Buildx / GitHub cache (orthogonal optimisation).
- Changing the Dockerfile base image.
- Authenticated Docker Hub pulls.
- Reverting if Docker 29+ fixes the upstream Java-client hang (a follow-up
  task can re-evaluate `ImageFromDockerfile`).

## Acceptance Criteria

1. The `e2e-tests` job in `.github/workflows/ci.yml` has a
   **"Build service images for e2e"** step that runs
   `docker build -t wms-master-service:e2e -f projects/wms-platform/apps/master-service/Dockerfile projects/wms-platform/apps/master-service/`
   and `docker build -t wms-gateway-service:e2e -f projects/wms-platform/apps/gateway-service/Dockerfile projects/wms-platform/apps/gateway-service/`
   before the Gradle e2eTest step.
2. The `e2e-tests` job has **"Download boot jars"** (downloading the
   `wms-boot-jars` artifact) and **"Restore boot jar paths"** steps that
   precede "Build service images for e2e", so the docker build context contains
   `apps/<service>/build/libs/<service>.jar` as each Dockerfile expects.
3. The Gradle e2eTest invocation in the `e2e-tests` job passes
   `-Dwms.e2e.masterImage=wms-master-service:e2e` and
   `-Dwms.e2e.gatewayImage=wms-gateway-service:e2e` system properties, so
   `E2EBase.java` consumes the pre-built images via `GenericContainer` and
   skips `ImageFromDockerfile`.
4. The `e2e-tests` job declares `needs: [build-and-test, boot-jars]` so the
   `wms-boot-jars` artifact is available.

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
