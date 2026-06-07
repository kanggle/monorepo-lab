# Task ID

TASK-BE-342

# Title

Make the outbound-service integration-test suite CI-runnable, then wire it into the wms integration CI job — surfaced by TASK-BE-340 (ADR-MONO-022): wiring `outbound-service:integrationTest` revealed **13/19 ITs fail in the CI runner**, a pre-existing dev-only-IT maintenance gap.

# Status

ready

# Owner

claude (Opus 4.8) — wms outbound-service test-infra repair. NOT a feature change; restores the integration safety net for a service whose ITs have never run in CI.

# Task Tags

- test

---

# Dependency Markers

- **선행/맥락**: TASK-BE-340 (added `FulfillmentRequestedConsumer`; its IT `FulfillmentRequestedConsumerIT` is included in this suite). TASK-BE-043/045 (the existing wms integration CI job that runs master/notification/admin).
- **근거**: PR #1177 CI run 27102786125 — `:outbound-service:integrationTest` = **19 tests, 13 failed** when first invoked in CI.

# Goal

`./gradlew :projects:wms-platform:apps:outbound-service:integrationTest` passes in the CI runner, and the task is added to the wms integration CI job (`.github/workflows/ci.yml`, the `integration-tests` job) alongside master/notification/admin — so outbound-service (incl. the cross-project fulfillment path) is Docker-verified on every wms change, not unit-only.

# Background (observed failures, CI run 27102786125)

`outbound-service:integrationTest` has **never been invoked in CI** (the wms integration job ran only master/notification/admin). Invoking it surfaced:

| Suite | Failing | Symptom |
|---|---|---|
| `SagaSweeperIT` | 5 | `BadSqlGrammarException` / `PSQLException` at the test's own JDBC (`SagaSweeperIT.java:187`) — stale helper SQL vs current schema (suspected). |
| `TmsClientAdapterIT` | 6 | all 6 scenarios fail — WireMock/resilience4j timing in the CI runner (suspected). |
| `IdempotencyFilterRedisIT` | 1 | assertion at `:199` — Redis-backed idempotency filter. |
| `FulfillmentRequestedConsumerIT` | 1 | order not created within await window (`:99`) — investigate real Kafka-consumption path (consumer + seed + `integration` profile all verified correct by inspection; unit tests for the consumer pass). |

6/19 passed, so the base infra (Postgres/Kafka/Redis/WireMock/context) boots — these are per-suite issues, not a total-environment failure.

# Scope

## In Scope
- Diagnose + fix each failing outbound IT so the suite is green in the CI runner (postgres:16-alpine, Confluent Kafka 7.6.1, Redis 7, WireMock — per `OutboundServiceIntegrationBase`).
- For `FulfillmentRequestedConsumerIT`: confirm the Kafka listener consumes the published event under `@ActiveProfiles("integration")`; fix the test (await/offset/topic) or the consumer if a real bug exists.
- Add `:projects:wms-platform:apps:outbound-service:integrationTest` to the `integration-tests` job run step + artifact paths; update the job name.

## Out of Scope
- Any production/feature behavior change (ADR-MONO-022 is already merged). Pure test-infra + CI.

# Acceptance Criteria

- AC-1: `:outbound-service:integrationTest` = 0 failures in the CI runner.
- AC-2: The task is wired into the wms `integration-tests` CI job; a wms-touching PR shows the outbound integration suite running + passing (pre-merge `gh pr checks`).
- AC-3: `FulfillmentRequestedConsumerIT` specifically passes — the ADR-MONO-022 Kafka→order path is Docker-verified end to end.
- AC-4: No production code regression (the 212 unit tests stay green).

# Related Specs

- `specs/services/outbound-service/architecture.md` (test-requirement section); ADR-MONO-022; `platform/testing-strategy.md`.

# Related Contracts

- None (test-infra). Exercises `ecommerce.fulfillment.requested.v1` consumption + outbound saga events.

# Edge Cases

- These ITs pass on dev machines (where they were authored) but fail in the CI runner — focus on CI-env-specific differences (image versions, timing, container networking), not just local repro.
- `disabledWithoutDocker = true` means they silently skip without Docker — ensure the CI job's `docker info` gate keeps them from being a false-green skip.

# Failure Scenarios

- Marking flaky ITs `@Disabled` to force green = green-wash → forbidden. Fix the root cause or, if a test is genuinely obsolete, delete it with justification.
- Wiring the suite into CI before it's green → re-introduces the PR #1177 RED. Fix first (AC-1), then wire (AC-2).
