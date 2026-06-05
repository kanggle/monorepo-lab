# 2026-05-07 — Docker Desktop 4.36+ CLI proxy regression: Testcontainers blocked

**Status**: **RESOLVED** (2026-05-07 same day) via Rancher Desktop + DOCKER_API_VERSION env. See § Resolution at the bottom.

**Affected tasks** (all 4 blocked from local Docker reproduction):

- [TASK-MONO-046-7](../../tasks/ready/TASK-MONO-046-7-auth-service-sas-deferred-8.md) — auth-service SAS deferred 8
- [TASK-MONO-046-8](../../tasks/ready/TASK-MONO-046-8-consumer-pipeline-deeper-investigation.md) — consumer pipeline deeper investigation
- [TASK-SCM-BE-002d](../../projects/scm-platform/tasks/ready/TASK-SCM-BE-002d-procurement-testcontainers-it.md) — procurement Testcontainers IT
- [TASK-SCM-INT-001](../../projects/scm-platform/tasks/ready/TASK-SCM-INT-001-procurement-inventory-visibility-e2e.md) — cross-service E2E

**Impact on Phase 5 readiness**: blocks Template extraction trigger. Per `scripts/verify-template-readiness.sh` Check 2, Phase 5 cannot enter until both SCM tasks land in `done/`. CI (Linux runner) remains the only reproduction path.

---

## Symptom

`@Testcontainers(disabledWithoutDocker = true)` reports Docker as unavailable inside WSL2 Ubuntu, despite Docker Desktop showing healthy and `docker run --rm hello-world` succeeding. Tests `SKIPPED` rather than `FAILED`:

```xml
<testsuite name="...SecurityServiceIntegrationTest" tests="1" skipped="1" failures="0" errors="0">
  <testcase name="...">
    <skipped/>
  </testcase>
</testsuite>
```

Verbose Testcontainers logs show three strategies attempted and failed:

```
ERROR org.testcontainers.dockerclient.DockerClientProviderStrategy --
  Could not find a valid Docker environment. Attempted configurations:
    EnvironmentAndSystemPropertyClientProviderStrategy: failed with exception
      BadRequestException (Status 400: {"ID":"","Containers":0,"ContainersRunning":0,
      "ContainersPaused":0,"ContainersStopped":0,"Images":0,"Driver":"","DriverStatus":null,
      ...,"Labels":["com.docker.desktop.address=unix:///var/run/docker-cli.sock"], ...})
    UnixSocketClientProviderStrategy: failed with same BadRequestException
    DockerDesktopClientProviderStrategy: NullPointerException
      (getSocketPath() returned null)
```

The empty-skeleton JSON with the `com.docker.desktop.address` label is the diagnostic signature.

---

## Root cause

Docker Desktop 4.36+ replaced the previous direct-socket WSL Integration with a **CLI proxy**:

1. `/var/run/docker.sock` in WSL2 is now a proxy socket, not the Docker daemon directly.
2. The proxy responds to `GET /_ping` and `GET /info` from `curl` and the `docker` CLI with normal data — these clients negotiate using methods/headers the proxy recognizes.
3. The proxy responds to the same requests from `docker-java` (the Java client embedded in Testcontainers 1.20.x and 1.21.0) with **HTTP 400 + an empty JSON skeleton** carrying only one usable field: a `Labels` entry pointing at `unix:///var/run/docker-cli.sock`.
4. The proxy intends `docker-cli.sock` to be the redirect target. Testing it directly:

```
$ curl -s --unix-socket /var/run/docker-cli.sock http://localhost/_ping
{"message":"Not Found"}
$ curl -s --unix-socket /var/run/docker-cli.sock http://localhost/info
{"message":"Not Found"}
```

That socket only accepts a different (CLI-specific) endpoint set, so `docker-java` cannot use it as a fallback either.

In effect: Docker Desktop's CLI proxy applies an HTTP-method/header inspection that `docker-java` does not pass, and the documented redirect target (`docker-cli.sock`) is not a general-purpose Docker API socket.

### Direct verification

```
$ curl -s --unix-socket /var/run/docker.sock http://localhost/_ping
OK

$ curl -s --unix-socket /var/run/docker.sock http://localhost/info
{"ID":"46377c6e-3331-4072-a39d-ac2f8b5782c3","Containers":45,
 "ContainersRunning":0,"ContainersPaused":0,"ContainersStopped":45,
 "Images":50,"Driver":"overlayfs", ...}

$ docker version --format 'Server Version: {{.Server.Version}} | API: {{.Server.APIVersion}}'
Server Version: 29.4.2 | API: 1.54

$ docker context ls
NAME            DESCRIPTION                               DOCKER ENDPOINT
default *       Current DOCKER_HOST based configuration   unix:///var/run/docker.sock
desktop-linux   Docker Desktop                            npipe:////./pipe/dockerDesktopLinuxEngine
```

Daemon is healthy from the CLI's perspective. The regression is purely in the CLI-proxy ↔ `docker-java` compatibility layer.

---

## Attempted fixes (all unsuccessful)

| Attempt | Result |
|---|---|
| `~/.testcontainers.properties` with `docker.host=unix:///var/run/docker.sock` + `ryuk.disabled=true` | No effect — probe still HTTP 400 |
| `DOCKER_HOST=unix:///var/run/docker.sock` env + `-Ddocker.host=...` system property propagated to test JVM | Verified propagated; same HTTP 400 |
| `TESTCONTAINERS_HOST_OVERRIDE=localhost` | No effect — probe runs before override |
| `TESTCONTAINERS_RYUK_DISABLED=true` | No effect — probe failure precedes Ryuk |
| Testcontainers 1.21.0 BOM override | Same HTTP 400 (docker-java dependency unchanged) |
| `wsl --shutdown` and restart | Sockets recreated; same regression |
| Direct probe of `/var/run/docker-cli.sock` | Returns `{"message":"Not Found"}` for `/_ping` and `/info` |

All client-side mitigations have been exhausted. The regression is server-side (Docker Desktop's CLI proxy implementation).

---

## Remediation paths (user-side action required)

Listed in order of recommended trial:

1. **Docker Desktop > Settings > Resources > WSL Integration toggle** — Ubuntu-22.04 OFF / Apply / ON / Apply. Forces socket re-binding; in some 4.36.x point releases this restores the legacy direct-socket path. (Tried in this session, no effect — but worth retrying if Docker Desktop has updated since.)
2. **Docker Desktop downgrade ≤ 4.35** — most reliable. The CLI proxy was introduced in 4.36; prior versions provide the legacy direct socket. Settings > Updates may not allow downgrade through GUI; download installer from `docs.docker.com/desktop/release-notes/` archive.
3. **Rancher Desktop or native `dockerd` in WSL** — fully bypasses Docker Desktop. Rancher Desktop ships its own dockerd that binds `/var/run/docker.sock` directly (no proxy). Caveat: distinct image cache from Docker Desktop, container migration not transparent.
4. **Wait for upstream fix** — track:
   - `docker-java` issue tracker for Docker Desktop 4.36+ CLI proxy compatibility
   - Testcontainers releases > 1.21.0 for CLI-proxy-aware probe strategy
   - Docker Desktop release notes for proxy-spec change

---

## Related references

- Memory: `project_testcontainers_docker_desktop_blocker.md` — operational notes (point-in-time)
- Verify script: `scripts/verify-template-readiness.sh` Check 2 — gates Phase 5 readiness on the 2 SCM Docker-blocked tasks
- TASK-MONO-046 series done summary in `tasks/INDEX.md` — CI Linux runner reproduction precedent
- ADR-MONO-002 — Phase 4 catalyst trigger (Phase 5 timing depends on this incident's resolution)

---

## Why this report

Memory entries decay; codebase docs persist. The root cause analysis here (CLI proxy / `docker-cli.sock` Not Found / 6 attempted fixes catalog) was directly verified during the 2026-05-07 session and represents non-trivial diagnostic effort. Future sessions encountering identical symptoms should consult this file before retrying client-side mitigations.

When the regression is resolved, append a "Resolution" section to this file with:

- Date resolved
- Path that worked (downgrade / Rancher / WSL toggle / upstream fix)
- Docker Desktop version after resolution
- Verification: SecurityServiceIntegrationTest single-test run output (expect `tests=1 failures=0 errors=0 skipped=0`)

Do not delete this incident — keep the trail intact for future regression diagnosis.

---

## Resolution (2026-05-07, same day)

**Path that worked**: Rancher Desktop coexistence + `DOCKER_API_VERSION` / `api.version` env forwarding. **No** Docker Desktop downgrade. **No** testcontainers version upgrade (1.20.4 retained).

### Steps taken

1. **Rancher Desktop install** (https://rancherdesktop.io/): Container Engine = `dockerd (moby)` (NOT `containerd` — Testcontainers requires moby for socket compatibility). Kubernetes optional (OFF saves memory). PATH integration auto.
2. **Rancher Desktop GUI** > Preferences > **WSL** > **Integrations** > Ubuntu-22.04 toggle ON > Apply.
3. **Docker Desktop** GUI > Settings > Resources > **WSL Integration** > Ubuntu-22.04 toggle OFF > Apply (CLI proxy disabled in WSL).
4. **Docker Desktop quit** entirely (system tray right-click > Quit Docker Desktop). This step is required: leaving Docker Desktop running kept its dockerd binding `/var/run/docker.sock` first, blocking Rancher's takeover.
5. **First Rancher GUI Apply** after Docker Desktop quit recreated socket with correct ownership: `srwxrwxrwx 1 root root /var/run/docker.sock` (Rancher dockerd, ID `46f64629-...`, ServerVersion `29.1.3`, no `com.docker.desktop` label).
6. **Code change** ([PR #255](https://github.com/kanggle/monorepo-lab/pull/255), root `build.gradle`): forward `DOCKER_API_VERSION=1.45` env + `-Dapi.version=1.45` system property to all `Test` tasks. This forces docker-java to skip its stale default API version (1.32) which Rancher's dockerd rejects (minimum API 1.41).

### What did NOT need to change

| Attempted during diagnosis | Result |
|---|---|
| Testcontainers 1.20.4 → 1.21.3 BOM upgrade (`ext['testcontainers.version']`) | Resolved 1.21.3 successfully, but caused regression in `AuthIntegrationTest > Refresh token reuse → 401 TOKEN_REUSE_DETECTED` (1.21.x lifecycle / state-handling differences). **Reverted to 1.20.4.** |
| docker-java direct version override | Not needed — system property `api.version=1.45` works on testcontainers 1.20.4's embedded docker-java |
| Docker Desktop downgrade ≤ 4.35 | Not attempted; Rancher path resolved first |

### Verification (WSL2 + Rancher Desktop dockerd 29.1.3)

```
$ DOCKER_API_VERSION=1.45 ./gradlew :projects:iam-platform:apps:security-service:integrationTest \
    --tests "com.example.security.integration.SecurityServiceIntegrationTest.consumeLoginSucceededEvent"
BUILD SUCCESSFUL in 1m 25s
# XML: tests="1" skipped="0" failures="0" errors="0" time="5.633"
```

[PR #255](https://github.com/kanggle/monorepo-lab/pull/255) CI all green: 12/12 jobs PASS including `Integration (iam-platform, Testcontainers)` (60 tests / 1 failed transient / 9 skipped @Disabled — fail rate equivalent to main baseline).

### Trade-offs of the chosen path

- **Rancher Desktop coexistence**: 다른 dockerd, 별도 image cache. Docker Desktop 의 image / volumes 와 분리. dev 시 active context 골라 사용. `docker context use desktop-linux` 로 Docker Desktop 복귀 가능.
- **Auto-update gotcha**: Rancher Desktop / Docker Desktop 둘 다 minor patch 받을 때 socket binding 우선순위 변경 가능성. 매번 Rancher 의 WSL Integration toggle 점검 권장.
- **CI 영향 0**: GitHub Actions Linux runner 는 native dockerd, CLI proxy 무관. PR #255 의 env forwarding 은 CI 에서 no-op.

### Affected tasks unblocked

- TASK-MONO-046-7 (auth-service SAS 8 deferred) — Opus 권장
- TASK-MONO-046-8 (consumer pipeline burst 3) — Opus 권장
- TASK-SCM-BE-002d (procurement Testcontainers IT ≥ 7) — Sonnet 권장
- TASK-SCM-INT-001 (cross-service E2E ≥ 6 + docker-compose.scm-e2e.yml) — Opus 권장

이 4 task 는 다음 세션 (또는 fresh agent) 에서 dispatch.

### Future regression prevention

본 fix 는 `api.version` system property 만으로도 동작 — Spring Boot BOM 의 testcontainers 버전 의존도 없음. 향후 docker-java 가 default API version 갱신하면 본 env 가 no-op 되며 자연 롤백 가능 (kit dirt 0).
