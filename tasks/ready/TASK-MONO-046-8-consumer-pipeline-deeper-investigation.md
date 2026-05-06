# Task ID

TASK-MONO-046-8

# Title

GAP security-service IT — consumer-pipeline deeper investigation (3 tests deferred from 046-6)

# Status

ready

# Owner

backend / qa

# Task Tags

- code
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

TASK-MONO-046-6 partial recovery (PR #236) attempted a Phase 1 fix: extend awaitility timeout from
30s to 60s for two burst tests (`CrossTenantVelocityIntegrationTest`,
`DetectionE2EIntegrationTest`) and re-enable the byte[] DLPR path in `DlqRoutingIntegrationTest`.

**Phase 1 disproved simple timing**: CI run 25413789119 showed all 3 tests still failing at 60s.
Root cause is NOT cold-start jitter. 046-6 re-disabled them with `TASK-MONO-046-8` tags.

This task requires Docker-level reproduction to inspect Kafka offsets, Redis counter state, and
DLPR header preservation at runtime. The 3 deferred tests must be re-enabled and pass.

## Deferred Tests

| Test | Disabled Reason |
|------|----------------|
| `CrossTenantVelocityIntegrationTest.tenantABurst_doesNotTriggerTenantBDetection` | Class-level @Disabled |
| `DetectionE2EIntegrationTest.velocityTriggersAutoLockE2E` | Class-level @Disabled |
| `DlqRoutingIntegrationTest.invalidBytesRoutedToDlq` | Method-level @Disabled (Order=2) |

---

# Hypotheses

## CrossTenantVelocityIntegrationTest / DetectionE2EIntegrationTest (burst tests)

1. **Offset race**: Consumer commits offset before VelocityRule completes. When the next test
   class starts with a shared Kafka container, `auto-offset-reset=latest` means events produced
   before listener assignment are skipped. `ContainerTestUtils.waitForAssignment` confirms
   partition assignment but does NOT guarantee the consumer group offset is below the produced
   offset.
2. **Redis counter reset between Spring contexts**: `@DirtiesContext` creates a new application
   context. The new context connects to the shared Redis container but may not share the
   counter key state — or may see stale TTL from a previous test run.
3. **Consumer group collision**: Multiple test classes using the same consumer group ID will
   rebalance between contexts, causing event loss if the new context commits an offset ahead of
   the messages produced in this test.
4. **VelocityRule threshold race**: With threshold=3 and 50/10 events, there is a window where
   the counter increments but the decision has not been persisted by the time the awaitility
   assertion fires.

## DlqRoutingIntegrationTest.invalidBytesRoutedToDlq

1. **Header preservation broken**: When `ErrorHandlingDeserializer` receives raw `byte[]`,
   `VALUE_DESERIALIZER_EXCEPTION_HEADER` may carry null data (no deserialization actually
   fails — the bytes are valid at the Kafka layer; the application-level deserializer is what
   fails). `DLPR.accept()` calls `vDeserEx.getData()` which may return null, producing a null
   payload in the DLQ record.
2. **Topic auto-create missing for .dlq**: The raw `byte[]` producer sends to
   `auth.login.failed`, but `auth.login.failed.dlq` topic may not be auto-created when
   `EmbeddedKafkaBroker` / Testcontainers Kafka uses strict topic config. The DLQ consumer
   subscribes but finds no topic.
3. **DelegatingByTypeSerializer dispatch**: If the DLPR creates a `ProducerRecord<String, byte[]>`
   but the DelegatingByTypeSerializer is not registered for `byte[].class`, the serializer
   falls through to `StringSerializer`, corrupting the bytes.

---

# Scope

## In Scope

- Docker-level reproduction: run the 3 failing tests in isolation with `docker compose up` +
  `./gradlew :projects:global-account-platform:apps:security-service:test --tests <class>` to
  capture live Kafka offset/consumer group state and Redis counter values.
- Diagnose and fix the root cause for each test.
- Re-enable all 3 tests (`@Disabled` removal).
- Ensure the 3 other `DlqRoutingIntegrationTest` methods (Order=1,3,4) remain PASS.

## Out of Scope

- TASK-MONO-046-7 (auth-service SAS 8 IT) — separate task
- TASK-MONO-046-5 (PiiMasking) — separate task
- Production code changes unless required by a confirmed bug (consumer group ID or serializer
  configuration changes are acceptable if they fix the root cause)

---

# Acceptance Criteria

1. `CrossTenantVelocityIntegrationTest.tenantABurst_doesNotTriggerTenantBDetection` PASS
2. `DetectionE2EIntegrationTest.velocityTriggersAutoLockE2E` PASS
3. `DlqRoutingIntegrationTest.invalidBytesRoutedToDlq` PASS
4. `DlqRoutingIntegrationTest` Orders 1, 3, 4 remain PASS (no regression)
5. main CI `Integration (GAP)` Job: security-service 20 tests — 20 PASS / 0 FAIL / 0 DISABLED
6. PR description includes root cause diagnosis for each of the 3 tests

---

# Related Specs

- [TASK-MONO-046-6](../in-progress/) — **직접 선행** (046-6 PR #236 머지 후 착수)
- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/`

---

# Related Contracts

- 없음 (test-only investigation — 외부 contract 변경 없음)

---

# Target Service / Component

- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/CrossTenantVelocityIntegrationTest.java`
- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/DetectionE2EIntegrationTest.java`
- `projects/global-account-platform/apps/security-service/src/test/java/com/example/security/integration/DlqRoutingIntegrationTest.java`
- `projects/global-account-platform/apps/security-service/src/main/java/com/example/security/infrastructure/kafka/KafkaConsumerConfig.java` (if consumer group ID fix needed)

---

# Edge Cases

1. **Consumer group ID uniqueness**: Each test class and test run must use a unique consumer
   group ID to prevent offset reuse across context refreshes (`@DirtiesContext`).
2. **Redis TTL on velocity counter**: If the Redis key has a TTL shorter than the test duration,
   the counter may expire mid-test. The `window-seconds` property must exceed total test
   duration including warm-up.
3. **Byte[] null data path**: `ErrorHandlingDeserializer` wraps the raw bytes even when the
   value deserializer is `StringDeserializer` — if it receives `byte[]` directly (not via
   `ByteArrayDeserializer`), the exception header data may be null.
4. **Topic partition count**: If `auth.login.failed.dlq` is auto-created with 1 partition but
   `ContainerTestUtils.waitForAssignment` only checks the main topic assignment, the DLQ
   consumer may block waiting for an assignment that never fires.

---

# Failure Scenarios

## A. Offset race not fixable without consumer group isolation

If the Testcontainers Kafka container shares state across all test classes (same broker), unique
consumer group IDs per test class are required. Add `UUID.randomUUID()` suffix to
`group.id` in the consumer container config or override via `@DynamicPropertySource`.

## B. byte[] DLPR path requires configuration change

If `DelegatingByTypeSerializer` does not dispatch `byte[]` correctly, add explicit
`byte[].class → ByteArraySerializer` mapping in `KafkaConsumerConfig` DLPR producer factory.

## C. DLQ topic not auto-created for byte[] producer

If the `.dlq` topic requires explicit pre-creation for the Testcontainers broker, add topic
creation in `AbstractIntegrationTest` or the test `@BeforeEach` setup using
`AdminClient.createTopics()`.

---

# Test Requirements

- 3 IT methods `@Disabled` removal + PASS
- Remaining `DlqRoutingIntegrationTest` methods 회귀 0
- main CI `Integration (GAP)` Job security-service: 20 PASS / 0 FAIL / 0 DISABLED

---

# Definition of Done

- [ ] Docker reproduce completed for each of the 3 tests
- [ ] Root cause documented for each test (offset race / Redis reset / serializer path)
- [ ] Fix applied (consumer group isolation / serializer config / topic pre-create as needed)
- [ ] 3 `@Disabled` annotations removed
- [ ] local integration test PASS confirmed
- [ ] main CI `Integration (GAP)` Job SUCCESS confirmed
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Opus** — requires Kafka offset/consumer group introspection,
  Redis counter state debugging, and Spring Kafka DLPR header chain analysis.
- **Docker required**: Must run tests locally with Docker Desktop to reproduce failures and
  inspect broker state. CI-only iteration is insufficient for diagnosing offset races.
- **dependency**:
  - `선행`: TASK-MONO-046-6 (본 task 의 `@Disabled` 가 046-6 partial-recovery PR #236 에서 추가됨 — 046-6 머지 후 착수).
  - `병렬`: TASK-MONO-046-7 (auth-service SAS), TASK-MONO-046-5 (PiiMasking).
  - `후속`: 본 task + 046-7 + 046-5 모두 머지 시 main `Integration (GAP)` Job 전체 GREEN milestone.
