# TASK-MONO-331 — Serialize the WMS integration CI job to stop the resource-contention Testcontainers flake

- **Status**: ready
- **Level**: monorepo (shared `.github/workflows/`)
- **Type**: CI reliability fix
- **Analysis model**: Opus 4.8 / **구현 권장**: Sonnet (two-file CI change)

## Goal

The shared CI job **`Integration (master-service + notification-service + outbound-service, Testcontainers)`** flakes red intermittently (observed red on `main` itself 2026-07-06, and 3× consecutively on an unrelated PR that same day). It blocks the shared integration gate for every PR while it is red.

The failure is **not** a code or seed-data bug. The CI log exception chain is a DB-connection loss:

```
CannotCreateTransactionException → JDBCConnectionException → SQLTransientConnectionException
Caused by: SocketException: Closed by interrupt / Socket closed
Caused by: PSQLException: An I/O error occurred while sending to the backend
```

Every failing test fails on its **first DB access**, and the failing set is always the DB-dependent cluster of whichever module lost the race (e.g. notification-service: `FlywayMigrationIntegrationTest.seededRoutingRulesPresent`, `RoutingRulePersistenceIntegrationTest.*`, `AlertConsumerIntegrationTest.duplicateReplaySkipsSilently`).

## Root cause

- Root `gradle.properties` sets **`org.gradle.parallel=true`**.
- The `wms-integration-tests` caller in `ci.yml` runs **four** module `integrationTest` tasks in one invocation (master / notification / admin / outbound).
- **All four modules spin their own Postgres + Confluent `cp-kafka` (7.6.1) Testcontainers stack.** Under `parallel=true` Gradle boots all four stacks **at once** — 4× (Postgres + heavy Kafka) + 4 test JVMs on the 2-CPU / 7 GB GitHub runner → memory/CPU exhaustion → containers/connections become unstable → transient DB connection loss, failing whichever module loses the race as a cluster.
- The `iam` integration job runs 7 modules yet passes — a container-footprint difference, not a module-count one.

## Scope

- `.github/workflows/_integration.yml`: add an optional `gradle-args` input (default `''`), appended to the `./gradlew` invocation.
- `.github/workflows/ci.yml`: on the **WMS** caller only, pass `gradle-args: --no-parallel` (serialize → one Testcontainers stack at a time) and raise `timeout-minutes` 20 → 30 (serial run is ~2× wall-clock).
- **Out of scope**: touching the passing iam / fan / ecommerce / scm / finance / erp / console callers (surgical — they keep parallel execution); reducing per-stack container footprint (Kafka heap caps, shared containers) — a larger follow-up if serialization proves insufficient.

## Acceptance Criteria

- The WMS integration job runs the four module `integrationTest` tasks **sequentially** (one Postgres+Kafka stack live at a time).
- The job is **green across multiple CI runs** (the authoritative check — local Windows Testcontainers is not reliable per project policy).
- No other integration caller changes behaviour (only the WMS caller passes `gradle-args`; the new input defaults to empty).
- YAML is valid and the reusable-workflow contract is unchanged for existing callers.

## Related Specs / Contracts

- n/a (CI infrastructure). Reusable-workflow lineage: TASK-MONO-326 (`_integration.yml`).

## Edge Cases

- Callers that omit `gradle-args` → input defaults to `''`, the run line renders unchanged (empty append). Verified in the diff.
- A genuine notification/master/admin/outbound integration regression → still surfaces (serialization only removes the resource-contention false red; real assertion failures remain).

## Failure Scenarios

- Serialization still flakes (contention was not the whole story) → escalate to the footprint-reduction follow-up (cap Hikari `maximum-pool-size` per test profile + bound cp-kafka heap, or share a single broker/DB across the WMS modules).
- 30-min timeout too tight for the serial run → raise further; the four stacks start sequentially so worst case ≈ 4 × single-module wall-clock.
