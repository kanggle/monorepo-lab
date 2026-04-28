# TASK-MONO-016 — Fix TASK-MONO-015: Update task spec to match actual implementation

> Fix issue found in TASK-MONO-015 (review 2026-04-29).
>
> TASK-MONO-015 was implemented with a materially different approach than its
> spec described. The spec called for a single `docker pull eclipse-temurin:21-jre-alpine`
> warmup step, but the actual implementation replaced `ImageFromDockerfile`
> entirely with pre-built images via the Docker CLI (to work around a Docker 28
> / Docker Java client incompatibility). The CI workflow is correct and green;
> the task spec is stale and does not match what was delivered.

## Goal

Update the TASK-MONO-015 spec in `tasks/done/` and the `tasks/INDEX.md` done-list
entry to accurately describe what was actually implemented — switching the e2e-tests
job from `ImageFromDockerfile` to pre-built images via `docker build` CLI, and
adding the corresponding boot-jar download/restore steps.

This is a documentation-only fix. No changes to `.github/workflows/ci.yml` or
any Dockerfile are required (the implementation is correct).

## Scope

**Files to update:**

1. `tasks/done/TASK-MONO-015-e2e-docker-image-pull-warmup.md`
   — Rewrite Goal, Scope, and Acceptance Criteria to reflect the `docker build`
   CLI approach actually implemented. The title may remain as-is for traceability
   but a brief note should explain the pivot from `docker pull` warmup to
   pre-built image injection.

2. `tasks/INDEX.md` (done-list entry for TASK-MONO-015)
   — Update the one-line summary to accurately describe the Docker 28 workaround
   approach rather than the original `docker pull` warmup.

**Not in scope:**
- Any change to `.github/workflows/ci.yml`.
- Any change to Dockerfiles.
- Any change to Java E2E test code.
- Renaming the task file (keep existing filename for traceability).

## Acceptance Criteria

1. `tasks/done/TASK-MONO-015-e2e-docker-image-pull-warmup.md` Acceptance Criteria
   section accurately describes the actual CI step added:
   - "Build service images for e2e" step using `docker build -t wms-master-service:e2e`
     and `docker build -t wms-gateway-service:e2e` immediately before the Gradle
     e2eTest step.
   - "Download boot jars" and "Restore boot jar paths" steps that precede the docker build.
   - `-Dwms.e2e.masterImage` and `-Dwms.e2e.gatewayImage` system properties passed
     to the Gradle e2eTest invocation.
   - `e2e-tests` job `needs: [build-and-test, boot-jars]` dependency.
2. `tasks/done/TASK-MONO-015-e2e-docker-image-pull-warmup.md` Scope section is
   updated to remove the "single file: ci.yml, insert one step, no other changes"
   claim and instead list all files/jobs actually modified.
3. `tasks/INDEX.md` done-list entry for TASK-MONO-015 accurately summarises
   the Docker 28 workaround (pre-built images via `docker build` CLI) rather
   than the original `docker pull` warmup approach.
4. No implementation files (ci.yml, Dockerfiles, Java sources) are modified.

## Related Specs

- `tasks/done/TASK-MONO-015-e2e-docker-image-pull-warmup.md` (stale spec to update)
- `tasks/INDEX.md` (done-list entry to update)
- `.github/workflows/ci.yml` (reference only — describes the actual implementation)

## Related Contracts

None.

## Edge Cases

- The task file is already in `done/` — CLAUDE.md prohibits modifying task files
  after they reach `done/`. This fix task is therefore the correct vehicle: it
  creates a new record of what was actually done while leaving the original
  TASK-MONO-015 file as an audit trail. If the project convention strictly
  forbids touching `done/` files, update only `tasks/INDEX.md` and note the
  discrepancy in this fix task's completion comment.

## Failure Scenarios

- **`tasks/INDEX.md` entry is already accurate**: if someone already updated the
  entry after the initial review finding, skip that file and close this task as
  no-op with a comment.
- **Conflicting task-file-immutability rule**: if `done/` files must not be
  touched, scope this fix to `tasks/INDEX.md` only and add a note here.
