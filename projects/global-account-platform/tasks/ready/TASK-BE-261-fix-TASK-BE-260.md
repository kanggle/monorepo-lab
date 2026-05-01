# Task ID

TASK-BE-261

# Title

Fix issue found in TASK-BE-260: OutboxRelayIntegrationTest still calls deprecated AuthEventPublisher overloads that now throw IllegalArgumentException

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Fix issue found in TASK-BE-260. The fix added requireTenantId(tenantId) guards at the top of AuthEventPublisher.publishLoginAttempted (4-arg) and publishLoginFailed (6-arg). The deprecated 3-arg publishLoginAttempted and 5-arg publishLoginFailed overloads delegate to those guarded methods with tenantId set to null, so any caller of the deprecated overloads now throws IllegalArgumentException with message "tenantId required" at runtime.

projects/global-account-platform/apps/auth-service/src/test/java/com/example/auth/integration/OutboxRelayIntegrationTest.java still has two such callers:

- Line 149 in loginFailedEventRelayedToKafka: authEventPublisher.publishLoginFailed(accountId, emailHash, "CREDENTIALS_INVALID", 3, ctx) — 5-arg deprecated overload.
- Line 184 in sameAccountEventsOrderedInSamePartition: authEventPublisher.publishLoginAttempted(accountId, "hash-order", ctx) — 3-arg deprecated overload.

Both callsites now throw at runtime. The compile-time [removal] warning is visible in the build output but the unit-only :auth-service:test run passed because OutboxRelayIntegrationTest is a Testcontainers @SpringBootTest integration test guarded by DockerAvailableCondition. On any environment with Docker available (CI), these two test methods will fail with IllegalArgumentException tenantId required instead of producing the intended Kafka publish.

The TASK-BE-260 task file acknowledged this risk in its Scope (the current bodies pass null which would now throw post-Fix 3) but deferred the migration to a follow-up cleanup task once no callers remain. A caller does remain in the same module, so the follow-up must land before the deprecated bodies stay broken in the codebase.

---

# Scope

## In Scope

- Fix — migrate OutboxRelayIntegrationTest callsites to tenant-carrying overloads:
  - Line 149: change to authEventPublisher.publishLoginFailed(accountId, emailHash, "fan-platform", "CREDENTIALS_INVALID", 3, ctx) — 6-arg form, matches the in-process tenant context default established by TASK-BE-229/248.
  - Line 184: change to authEventPublisher.publishLoginAttempted(accountId, "hash-order", "fan-platform", ctx) — 4-arg form.
  - Use the literal "fan-platform" for consistency with TenantContext.DEFAULT_TENANT_ID and the AuthEventPublisherTest migration pattern adopted in TASK-BE-260 commit dc893800.
- Verify: integration tests assert payload tenantId field equals "fan-platform". Extend the existing assertion blocks at lines 165-170 and 195-198 with one assertion line each like assertThat(payload.get("tenantId").asText()).isEqualTo("fan-platform"), since the contract now requires the field on the wire.

## Out of Scope

- Removal of the deprecated 3-arg/5-arg overloads themselves. Still tracked as a separate cleanup task per TASK-BE-260 Scope; do not remove them in this fix — only migrate callers.
- Other integration test files. None of the active auth-service integration tests use these deprecated overloads beyond OutboxRelayIntegrationTest. Verify by grepping for publishLoginAttempted and publishLoginFailed under apps/auth-service/src/test.
- Production code. LoginUseCase already uses the tenant-carrying overloads after TASK-BE-260.

---

# Acceptance Criteria

- [ ] OutboxRelayIntegrationTest.loginFailedEventRelayedToKafka calls the 6-arg publishLoginFailed(..., "fan-platform", ...) overload.
- [ ] OutboxRelayIntegrationTest.sameAccountEventsOrderedInSamePartition calls the 4-arg publishLoginAttempted(..., "fan-platform", ...) overload.
- [ ] Both test methods assert the published Kafka payload contains tenantId=fan-platform.
- [ ] No [removal] deprecation warnings remain for publishLoginAttempted(String, String, SessionContext) or publishLoginFailed(String, String, String, int, SessionContext) in the auth-service test compile output.
- [ ] ./gradlew :projects:global-account-platform:apps:auth-service:test GREEN on a Docker-enabled environment when run with Testcontainers active.
- [ ] ./gradlew :projects:global-account-platform:apps:auth-service:test GREEN on a Docker-disabled environment. Testcontainers tests skipped via DockerAvailableCondition, no compile failure.

---

# Related Specs

- specs/contracts/events/auth-events.md — auth.login.attempted and auth.login.failed schema v2 (tenantId required).
- specs/features/multi-tenancy.md section "Cross-Tenant Security Rules" → "Event 발행". 모든 outbox 이벤트에 tenant_id 페이로드 필수.
- tasks/done/TASK-BE-260-fix-TASK-BE-248.md after this review move — original fix task that introduced the regression.

# Related Skills

- .claude/skills/review-checklist/SKILL.md

---

# Related Contracts

- specs/contracts/events/auth-events.md — no schema change. The test must satisfy the existing required-field contract rather than rely on the deprecated null-tenantId path.

---

# Target Service

- auth-service (integration test only)

---

# Architecture

No structural changes. Edits are confined to:

- apps/auth-service/src/test/java/com/example/auth/integration/OutboxRelayIntegrationTest.java — two callsite migrations + two payload assertions.

---

# Implementation Notes

- The literal "fan-platform" is preferred over importing TenantContext.DEFAULT_TENANT_ID because the integration test class lives outside the auth.domain.tenant package and the existing test fixtures use string literals consistent with the migration-backfill default. This matches the convention established by AuthEventPublisherTest after TASK-BE-260.
- The two new payload assertions are additive — they do not change existing assertions, only confirm the now-required tenantId field reaches Kafka.
- The deprecated overloads stay in place. A separate cleanup task should remove their bodies, or delete the methods entirely, once a grep confirms no remaining callers in any project. That scope is broader than this fix and intentionally deferred.

---

# Edge Cases

- Test runs without Docker: DockerAvailableCondition skips the entire class. The migration is silent. :auth-service:test remains GREEN with the relevant tests in the skipped bucket. This does not regress the unit-only AC of TASK-BE-260.
- Test runs with Docker: post-fix, both methods publish events carrying tenantId=fan-platform. The outbox relay forwards to Kafka, the consumer poll succeeds, and the new payload assertions pass. Pre-fix, both methods threw IllegalArgumentException tenantId required inside transactionTemplate.executeWithoutResult and bubbled out as test failures.

---

# Failure Scenarios

- If a future caller adds a new use of the deprecated overloads: their unit tests stay green because they pass null and the guard throws — visible immediately. It is still a regression of the cleanup intent. Mitigated when the deprecated overloads are eventually removed in a separate task.
- If the integration test environment lacks Docker AND the test environment lacks Testcontainers: the AC for the Docker-enabled path is unverifiable locally. Reviewer should defer to CI run output. CI is Docker-enabled.

---

# Test Requirements

- Integration tests (auth-service): the existing two test methods in OutboxRelayIntegrationTest are updated as described. No new test methods are required because the strict tenantId requirement is already covered by AuthEventPublisherTest.publishLoginAttempted_nullTenantId_throws and the related blank/null variants added in TASK-BE-260. The integration test role here is to confirm the wire-level payload includes the field after the fix.
- No unit-test changes — AuthEventPublisherTest migration was completed in TASK-BE-260.

---

# Definition of Done

- [ ] OutboxRelayIntegrationTest two callsites migrated to tenant-carrying overloads.
- [ ] OutboxRelayIntegrationTest payload assertions extended to include tenantId=fan-platform.
- [ ] ./gradlew :projects:global-account-platform:apps:auth-service:test GREEN locally. Testcontainers skipped is acceptable.
- [ ] No [removal] warnings remain for the two deprecated overloads in test compilation output.
- [ ] Ready for review.
