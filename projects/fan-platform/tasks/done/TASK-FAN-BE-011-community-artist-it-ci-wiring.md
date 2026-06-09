# TASK-FAN-BE-011 — wire community + artist integrationTest into the fan CI job

Status: done
Type: backend (TASK-FAN-BE) — CI / test-infra
Project: fan-platform
Apps: community-service, artist-service (test infra) + `.github/workflows/ci.yml`

---

## Goal

Add `community-service:integrationTest` and `artist-service:integrationTest` to
the `fan-integration-tests` CI job so their per-service `@SpringBootTest`
Testcontainers suites run on CI for the first time, and fix the latent IT-infra
bugs that first run surfaces (memory §19/§20). Direct follow-up of FAN-BE-010,
whose `MembershipGateIntegrationTest` edit was only locally provable because
community's integrationTest ran nowhere on CI.

**Why these never ran on CI:** the `fan-platform-e2e` job runs the *separate*
`:projects:fan-platform:tests:e2e:e2eSmokeTest` module (FAN-INT-001), NOT the
per-service integrationTest tasks. `:check` is Docker-free (unit + slice). With
`@Testcontainers(disabledWithoutDocker = true)` they were silently skipped on
Docker-less dev hosts → latent.

## Scope

**In scope:**

1. `.github/workflows/ci.yml` — `fan-integration-tests` run step now invokes
   community + artist + membership integrationTest (one gradle invocation);
   comment corrected (the e2e job does NOT run these suites).
2. **community-service** §19c — `CommunityServiceIntegrationBase` gains a
   `JdbcTemplate truncateAll()` (TRUNCATE all tables incl. `outbox` /
   `processed_events`); `CommunityServiceIntegrationTest` +
   `OutboxRelayIntegrationTest` cleanup switched from
   `outboxJpaRepository.deleteAll()` (no default tx → `TransactionRequiredException`)
   to `truncateAll()`. (§19a static-block start already landed in FAN-BE-010.)
3. **artist-service** §19a — `ArtistServiceIntegrationBase` container start
   `@BeforeAll`/`@AfterAll` → `static {}` block (the "Mapped port" fix). Artist
   ITs need no cleanup helper (naturally isolated by unique data), so none added.
4. **artist-service** YAML — `application-test.yml`
   `artist.cache.directory.namespace` value (contains colons + trailing colon)
   quoted; an unquoted plain scalar throws `ScannerException: mapping values are
   not allowed here` at context load. (Latent: this test-profile-only file was
   never parsed until the IT ran; the main `application.yml` wraps the same value
   in `${...}` so it parsed there.)

**Out of scope:** new test cases (this is test-infra only — no production code
change); membership-service (already CI-gated, FAN-BE-009); standalone-repo CI.

## Acceptance Criteria

- **AC-1** The `fan-integration-tests` CI job runs community + artist + membership
  integrationTest and is GREEN.
- **AC-2** community: `CommunityServiceIntegrationTest` + `OutboxRelayIntegrationTest`
  no longer throw `TransactionRequiredException` on cleanup; all 7 community IT
  classes pass.
- **AC-3** artist: all 8 IT classes load context (no `ScannerException`) and pass;
  containers start via `static {}` (no "Mapped port" error).
- **AC-4** No production code changed (only test sources, the artist test-profile
  YAML, and the workflow file).
- **AC-5** Local proof with Docker: all three suites BUILD SUCCESSFUL.

## Related Specs

- memory §19 (3 latent IT-infra bugs) + §20 (disabledWithoutDocker never-ran).
- `platform/testing-strategy.md` (Testcontainers gate authority).

## Related Contracts

- None (test-infra only).

## Edge Cases

- artist ITs share singleton containers (after the static-block change) with no
  cleanup — they self-isolate via unique identifiers (`nanoTime` stage names,
  scoped queries); verified green locally. If a future artist IT writes the outbox
  and needs cleanup, apply the §19c `JdbcTemplate` TRUNCATE pattern (documented in
  the community/membership bases).

## Failure Scenarios

- **Workflow YAML break** — a malformed `ci.yml` fails the entire CI run.
  Mitigation: edit kept within the existing comment block + `>-` block scalar at
  the established indent; the three suites validated locally in one invocation.
- **First-CI flake** — KRaft Kafka cold start can be slow; the job already has a
  5m per-test timeout (artist/community build.gradle) and 30m job timeout.
