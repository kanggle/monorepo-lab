# Task ID

TASK-BE-496

# Title

`PutawayLifecycleIntegrationTest` drops co-batched Kafka records — restore main GREEN (`Integration (inventory + inbound-service)` lane)

# Status

ready

# Owner

backend

# Task Tags

- test
- ci
- bugfix

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-BE-489` (#2358) — the task that restored this dormant suite and CI-wired it. The defect is in the restored test's Kafka assertion helper, not in production code.
- **후속 (blocks)**: every open PR. `main` is RED on this lane, so no PR can satisfy the CLAUDE.md merge rule (pre-merge 0 failing required checks) until this lands.

---

# Goal

Restore `main` to GREEN on the `Integration (inventory + inbound-service, Testcontainers)` CI lane by fixing the **test-side** record-loss bug in `PutawayLifecycleIntegrationTest`.

The production inbound flow is correct — all three events *are* published. The test throws two of them away.

## Root cause

`pollEventOnTopic(consumer, topic)` is called three times in sequence against **one** `KafkaConsumer` subscribed to **all three** topics:

```java
JsonNode instructed = pollEventOnTopic(consumer, TOPIC_INSTRUCTED);
JsonNode completed  = pollEventOnTopic(consumer, TOPIC_COMPLETED);   // ← may already be gone
JsonNode closed     = pollEventOnTopic(consumer, TOPIC_CLOSED);      // ← may already be gone
```

Each call runs `consumer.poll(...)`, scans the returned batch for **its own** topic, returns that record — and **silently discards every other record in the batch**. `poll()` has already advanced the consumer's position past them, so a later call can never see them again.

Whether the test passes is therefore decided by how the broker batches the three records:

- three separate `poll()` batches → passes (this is why it passed when first restored);
- one batch containing all three → the 2nd and 3rd calls find nothing and time out after 45s.

The outbox publisher flushes the three events in quick succession, so co-batching is the *normal* case on a fast runner. Hence "flaky" that is now effectively deterministic: it reproduces on `main` tip (`85c8e732d`, `07a2e019e`) and on an unrelated feature branch, on the first run **and on `gh run rerun --failed`**.

This is a latent-forever defect: it is not a timeout that a longer `atMost(...)` can fix, because the records are already consumed and dropped.

---

# Scope

## In Scope

- `projects/wms-platform/apps/inbound-service/src/test/java/com/wms/inbound/integration/PutawayLifecycleIntegrationTest.java`
  - Replace the per-topic sequential poll with a **single accumulating drain**: poll in a loop, put every received record into a `topic → JsonNode` map (first record per topic wins), and finish when all expected topics are present. Then assert against the map.
  - Keep the 45s `atMost` budget and the 500ms poll interval; keep `auto.offset.reset=earliest` and the per-run random `group.id`.
  - Fail with a message that names the *missing* topics, so a genuine publisher regression is distinguishable from a consumer-side drop.

## Out of Scope

- Any production/main-source change under `apps/*/src/main`. The publisher is not at fault; do not "fix" it.
- The other suites that use a raw `KafkaConsumer` (inventory `PickingFlowIntegrationTest`, `AdjustmentTransferIntegrationTest`, `PutawayCompletedConsumerIntegrationTest`, `MasterLocationDltRoutingIntegrationTest`, erp `ReadModelDltIntegrationTest`, wms-admin `KafkaTestSupport`). **Verified they do not call `pollEventOnTopic`** — the helper is unique to this file. Auditing them for the same shape is a separate follow-up if desired.
- The unrelated `main` red on other lanes (`Build & Test (JDK 21, Linux)` on `954ecfef3`, `Integration (iam)` on `89219cb87`, `master-service + notification-service + outbound-service` on `85c8e732d`) — those vary per run and look like runner contention. This task fixes only the one lane that reproduces deterministically.

---

# Acceptance Criteria

- [ ] **AC-1**: `pollEventOnTopic` is gone; the test drains **all** records from each `poll()` batch into a map and never discards a record it has consumed.
- [ ] **AC-2**: The three assertions (`instructed` / `completed` / `closed`) read from the drained map, and all still assert the same envelope fields (`eventType`, `payload.asnId`, `lines[0].qtyReceived`, `summary.putawayConfirmedTotal`).
- [ ] **AC-3**: A timeout failure message names which topics were **not** seen — a publisher regression must not be reported as an anonymous `TimeoutException`.
- [ ] **AC-4**: `./gradlew :projects:wms-platform:apps:inbound-service:integrationTest` GREEN (2/2) — and GREEN when re-run, since the fix removes the batching dependence rather than widening a timeout.
- [ ] **AC-5**: Diff is test-only (`git diff --stat` shows no file under `src/main`).
- [ ] **AC-6**: CI Linux `Integration (inventory + inbound-service, Testcontainers)` lane GREEN on the PR (authoritative — local Testcontainers on Windows is not).

---

# Related Specs

> `platform/entrypoint.md` Step 0 first — read `projects/wms-platform/PROJECT.md` and the declared rule layers.

- `projects/wms-platform/specs/services/inbound-service/architecture.md` (test-requirement section)
- `projects/wms-platform/tasks/done/TASK-BE-489-inbound-integration-suite-restore-ci.md` (the suite this defect rode in on)
- `platform/testing-strategy.md`

# Related Contracts

- `projects/wms-platform/specs/contracts/events/` — `wms.inbound.putaway.instructed.v1`, `wms.inbound.putaway.completed.v1`, `wms.inbound.asn.closed.v1` (the envelopes asserted; unchanged by this task)

---

# Target Service

- `inbound-service` (test source set only)

---

# Edge Cases

- **A record for an expected topic arrives twice** (redelivery / rebalance) — first one wins (`putIfAbsent`); do not overwrite, or a later duplicate could mask an ordering assertion.
- **All three arrive in the very first batch** — the drain must complete without a second `poll()` and must not block for the full 45s.
- **Only two of three ever arrive** (a real publisher regression) — the test must fail *fast enough to be readable* and name the missing topic, rather than surfacing a bare `ConditionTimeoutException`.
- **`readTree` throws on a malformed payload** — must surface as a test failure, not be swallowed into "record not found" (which would time out and hide the real cause).
- The consumer subscribes before the use-cases run? **No** — it subscribes *after*, which is why `auto.offset.reset=earliest` + a fresh random `group.id` are load-bearing. Do not "tidy" either away.

---

# Failure Scenarios

- The fix widens `atMost(...)` instead of draining → the records are still consumed and dropped; the test just fails slower. Guard: AC-1 forbids the per-topic poll helper.
- The drain returns on the first batch without checking that all topics are present → passes spuriously when the publisher regresses. Guard: AC-2 + AC-3.
- A production change is made to "make the events arrive separately" → couples the publisher's batching to a test artifact. Guard: AC-5 test-only diff.
- Local Windows Testcontainers is treated as authoritative → this host cannot even start `cp-kafka` under load. Guard: AC-6 CI Linux.
