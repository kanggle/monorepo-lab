# Workflows

## ci.yml — Build & Test

Triggered on push to `main` and every PR targeting `main`.

### Jobs

| Job | Purpose |
|---|---|
| `build-and-test` | `./gradlew check` on Ubuntu + JDK 21 (Temurin). Testcontainers-based tests execute here because Linux runners expose a real Docker socket at `/var/run/docker.sock` — unlike Windows developer machines where Testcontainers gracefully skips via `@Testcontainers(disabledWithoutDocker = true)`. |
| `boot-jars` | Packages `master-service.jar` and `gateway-service.jar` as workflow artifacts. Runs only after tests pass. |

### Concurrency

Older runs for the same branch/PR are cancelled on new commits to avoid queue pile-up.

### Runner Node runtime

Workflow sets `FORCE_JAVASCRIPT_ACTIONS_TO_NODE24=true` to opt into the Node 24 runtime ahead of GitHub's June 2, 2026 default switch (Node 20 is deprecated and scheduled for removal on September 16, 2026 — see [GitHub changelog](https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/)). Once every action pinned below publishes a `@v5` that defaults to Node 24, this env var can be removed.

### Artifacts

- **test-reports** (on failure only, 7d) — HTML + XML test output for post-mortem.
- **wms-boot-jars** (always, 7d) — executable jars for ad-hoc manual verification.

### Local parity

```bash
./gradlew check                      # what the CI job runs
./gradlew :projects:wms-platform:apps:master-service:bootJar
./gradlew :projects:wms-platform:apps:gateway-service:bootJar
```

Differences between local (Windows) and CI (Linux):

- Testcontainers tests (e.g. `WarehousePersistenceAdapterTest`) skip locally on Windows + Docker Desktop, run on CI.
- Local H2 equivalent (`WarehousePersistenceAdapterH2Test`) runs in both environments for fast feedback.
