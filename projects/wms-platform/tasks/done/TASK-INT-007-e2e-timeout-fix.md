# TASK-INT-007 — E2E job: artifact reuse + timeout increase

## Goal

Stop the `e2e-tests` CI job from being CANCELLED at the 20-minute wall-clock limit.

Root cause: the `e2e-tests` job currently only `needs: build-and-test`, so it must rebuild both
`master-service.jar` and `gateway-service.jar` from scratch on every run (~4 min). On top of
that, 5-container Testcontainers startup (Postgres + Redis + Kafka + master + gateway, with 3-min
health-check timeouts each) plus the rate-limit scenario (250 serial HTTP requests) regularly
pushes total elapsed time past 20 minutes on slower GitHub-hosted runners.

## Scope

**In scope — `.github/workflows/ci.yml` only:**

1. Change `e2e-tests.needs` from `[build-and-test]` to `[build-and-test, boot-jars]`.
2. Add an `actions/download-artifact@v4` step in `e2e-tests` that restores `wms-boot-jars`
   to the workspace root (preserving the relative paths the Testcontainers `ImageFromDockerfile`
   copies from).
3. Change the Gradle invocation to exclude the two `bootJar` tasks
   (`-x :projects:wms-platform:apps:master-service:bootJar
     -x :projects:wms-platform:apps:gateway-service:bootJar`),
   since the jars are now pre-supplied by the artifact.
4. Increase `e2e-tests.timeout-minutes` from `20` to `30`.

**Out of scope:**

- Parallelising the rate-limit scenario (separate concern).
- Adding per-test `@Timeout` annotations (no hang observed in test logic itself).

## Acceptance Criteria

1. `e2e-tests` `needs` includes `boot-jars`.
2. `e2e-tests` has a `Download boot jars` step before the Gradle run step.
3. The Gradle command excludes both `bootJar` tasks.
4. `e2e-tests.timeout-minutes` is `30`.
5. No other job is affected.

## Related Specs

- `.github/workflows/ci.yml`

## Related Contracts

None.

## Edge Cases

- If `boot-jars` is skipped (e.g., a hypothetical `if:` condition), the download step will fail
  cleanly with "artifact not found" rather than silently running with stale jars, because
  `actions/download-artifact@v4` errors on missing artifacts by default.
- `actions/download-artifact@v4` restores files to their original workspace-relative paths
  (e.g., `projects/wms-platform/apps/master-service/build/libs/master-service.jar`), which
  is exactly what `E2EBase.locateJar()` resolves at runtime.

## Failure Scenarios

- If the restored jars are somehow absent (download step skipped), `e2eTest` will fail at
  `E2EBase.locateJar()` with a clear `IllegalStateException` naming the missing path — not a
  silent timeout.
