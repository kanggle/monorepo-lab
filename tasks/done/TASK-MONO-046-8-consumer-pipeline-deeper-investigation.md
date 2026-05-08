# Task ID

TASK-MONO-046-8

# Title

GAP security-service IT — consumer-pipeline deeper investigation (3 tests deferred from 046-6)

# Status

done — PR #272 머지 (2026-05-08). Phase 0 (3 production diagnostic + 1 build-side) 정착: KafkaConsumerConfig containsCause walker + dlqTemplate ProducerListener.onError + destinationResolver valueClass logging + security-service testcontainers BOM 1.21.3 override. Cycle3 1 cold-start window 에서 byte[] DLPR test 단독 reproduce 로 concrete signal 확보 (`Sending to DLQ` log → 60s zero-record on `.dlq` → `else` branch). Burst tests (CrossTenantVelocity / DetectionE2E) 은 env blocker (Rancher dockerd v29.1.3 + docker-java zerodep npipe transport: cold-start 1 cycle 만 정상, 후속 JVM `MalformedChunkCodingException`) 로 미reproduce. 3 deferred test root cause 확정 + fix 는 TASK-MONO-046-8a 분리.

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

# Phase 0 — partial diagnostics (2026-05-08)

## What landed (kept on `fix/mono-046-8-consumer-pipeline-deeper`)

1. **`KafkaConsumerConfig` instrumentation** — destinationResolver now logs
   `valueClass` of the in-flight record and uses a `containsCause(...)` walker
   (replaces the old shallow `ex.getCause() instanceof DeserializationException`
   check, which missed nested wrappers). `dlqTemplate.setProducerListener(...)`
   surfaces previously-silent DLQ publish failures (the suspected fail mode for
   the byte[] path). Production behaviour unchanged for happy paths.
2. **`security-service/build.gradle` testcontainers BOM 1.21.3 override** —
   matches the SCM services. The Spring Boot 3.4.1 default (1.20.4) ships a
   docker-java whose npipe transport chokes on chunked responses from modern
   dockerd. Effective for any dev who reaches a working dockerd.
3. **`projects/global-account-platform/build.gradle` `:integrationTest`
   testLogging** — `events / exceptionFormat / showStandardStreams = true`
   so future cycles can read SKIP / FAIL reasons without `--info` digging.

## What was diagnosed (cycle 3 — single cold-start window)

| Test | Outcome | Concrete signal |
|---|---|---|
| `DlqRoutingIntegrationTest` Order 1, 3, 4 (control) | PASS | DLPR → `.dlq` happy path works for String values |
| `DlqRoutingIntegrationTest.invalidBytesRoutedToDlq` (Order 2, byte[]) | **FAIL** | `Sending to DLQ: topic=auth.login.failed, eventKey=acc-dlq-002, error=Listener method ... threw exception` logged, then **60s Awaitility timeout** with zero records on `auth.login.failed.dlq`. Error classified into the `else` branch (not `DeserializationException`, not `MissingTenantIdException`), implying the deserialize chain did NOT surface a `DeserializationException` — `StrictJsonStringDeserializer.deserialize(byte[])` throws `org.apache.kafka.common.errors.SerializationException` which `ErrorHandlingDeserializer` may or may not have wrapped on the cause chain |
| `CrossTenantVelocityIntegrationTest` | not reached | env-blocked before reproduce window |
| `DetectionE2EIntegrationTest` | not reached | env-blocked before reproduce window |

## Updated working hypotheses (post-cycle-3)

- **byte[] DLPR**: most-likely the DLPR call site IS being reached (log shows
  `Sending to DLQ`) but the actual publish to `.dlq` is silently lost — either
  (a) `DelegatingByTypeSerializer` cannot dispatch the record value (which is
  `null` after EHD swallow + raw bytes only present on the header) and the
  send future fails, or (b) DLPR's byte[] restoration from header lands on a
  type that the serializer rejects. The new `setProducerListener.onError(...)`
  hook will print the exact failure once a future cycle survives env-readiness.
- **Burst tests** (CrossTenantVelocity / DetectionE2E): not yet reproduced.
  Original 046-8 hypotheses (offset race, Redis TTL, consumer-group collision,
  threshold race) remain open.

## Environment blocker (the actual reason burn stopped)

Local Windows + Rancher Desktop dockerd v29.1.3 + the
`NpipeSocketClientProviderStrategy` from `~/.testcontainers.properties`
exhibits a **transient regression**: the very first Test JVM after a
`rdctl shutdown && rdctl start` connects fine
(`Found Docker environment with local Npipe socket`), every subsequent JVM in
the same dockerd lifetime fails immediately with
`com.github.dockerjava.zerodep...MalformedChunkCodingException: Bad chunk header`.
`docker run hello-world` and `docker version` both keep returning 200s, so the
issue is specific to docker-java's chunked-response parser against Rancher's
dockerd post-first-handshake.

This caps the local burn rate at **one IT class per Rancher restart** — too
expensive for a multi-cycle diagnostic. Commenting out the strategy line in
`~/.testcontainers.properties` did not help (testcontainers still picks
NpipeSocket as the only viable Windows strategy on auto-detect). CI Linux
runners are unaffected.

The PR-#255 incident report
(`knowledge/incidents/2026-05-07-docker-cli-proxy-regression.md`) covers the
adjacent Docker Desktop CLI-proxy regression, but the symptom here
(`MalformedChunkCodingException` on chunked transfer-encoding) is a different
docker-java vs dockerd interaction and may warrant its own incident note once
fully isolated.

## Next-cycle entry plan (when env stabilises)

1. Confirm Rancher Desktop yields a stable connection — preferably switch to a
   dockerd / Docker Desktop combination where docker-java doesn't see the
   chunk-header regression, OR document a workaround.
2. Re-enable the three `@Disabled` markers (annotations carry the
   `TASK-MONO-046-8` tag and a one-line breadcrumb).
3. Run `:integrationTest --tests DlqRoutingIntegrationTest.invalidBytesRoutedToDlq`
   under the new `setProducerListener.onError` hook — read the `valueClass=...`
   line + any `DLQ publish FAILED` callback to confirm whether the byte[]
   path is (i) silent producer-side serializer mismatch or (ii) a different
   cause-chain wrapper.
4. Apply the smallest possible fix: most likely a `byte[].class →
   ByteArraySerializer` registration tweak in the DLPR producer factory, or a
   `record.value()` null-guard in `AbstractAuthEventConsumer.processEvent()`
   so the deserialization-failure path stays NotRetryable.
5. Move the burst-test diagnostic on top: introduce per-class consumer-group
   isolation (the production `@KafkaListener` groupId is hardcoded
   `security-service` whereas SCM's recently-shipped pattern overrides via
   `${...random}` → much more reproducible offset semantics under
   `@DirtiesContext(AFTER_CLASS)`).

# Notes

- **Recommended impl model**: **Opus** — requires Kafka offset/consumer group introspection,
  Redis counter state debugging, and Spring Kafka DLPR header chain analysis.
- **Docker required**: Must run tests locally with Docker Desktop to reproduce failures and
  inspect broker state. CI-only iteration is insufficient for diagnosing offset races.
- **dependency**:
  - `선행`: TASK-MONO-046-6 (본 task 의 `@Disabled` 가 046-6 partial-recovery PR #236 에서 추가됨 — 046-6 머지 후 착수).
  - `병렬`: TASK-MONO-046-7 (auth-service SAS), TASK-MONO-046-5 (PiiMasking).
  - `후속`: 본 task + 046-7 + 046-5 모두 머지 시 main `Integration (GAP)` Job 전체 GREEN milestone.
