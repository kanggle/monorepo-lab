# Task ID

TASK-INT-006

# Title

Fix destructive drain() inside untilAsserted — outbox smoke assertion loses record on retry

# Status

ready

# Owner

integration

# Task Tags

- test
- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

Fix a Critical defect found during the TASK-INT-004 review.

In `GatewayMasterE2ETest.createWarehouseThroughGateway()` (scenario 1), the
outbox→Kafka smoke assertion calls `consumer.drain()` inside an
`await().untilAsserted(...)` lambda. `drain()` is destructive — it removes
records from the underlying `ConcurrentLinkedQueue` via `poll()`. Awaitility
retries the entire lambda body on each polling cycle. If the matching
`ConsumerRecord` arrives on the first call to `drain()` but any subsequent
assertion inside the same lambda body fails (e.g. the envelope has an
unexpected `eventType` value), the record is already gone. On the next retry
`drain()` returns an empty list, `match` is null, and the test fails with a
misleading "no record received" message instead of the real field mismatch.

This is a **reliability and diagnosability defect**:
- In the happy path it is unlikely to surface (correctly formatted events will
  pass all assertions in one pass), but it is a latent trap for future
  contributors who add an envelope-field assertion while the outbox impl is
  still evolving.
- When the defect fires, the failure message ("outbox publisher should deliver
  … within 10s") hides the real cause (a field mismatch on a record that was
  received).

Fix: accumulate `drain()` output across retries into a list that persists
across Awaitility polling cycles. The simplest correct pattern is to lift the
accumulation list outside the lambda and append inside, then filter on it.

---

# Scope

## In Scope

- `gateway-service/src/e2eTest/java/com/wms/gateway/e2e/GatewayMasterE2ETest.java`
  — fix the `createWarehouseThroughGateway` test method so `drain()` output
  is accumulated across Awaitility retries, not replaced on each cycle.
  The canonical approach: declare a `List<ConsumerRecord<String, String>>`
  before the `await()` block, append inside the lambda (`records.addAll(consumer.drain())`),
  and filter the accumulated list for the match.
- Update the Awaitility lambda comment to explain the accumulation pattern.

## Out of Scope

- Changing `KafkaTestConsumer.drain()` semantics (it is correct for single-shot use).
- Adding a non-destructive `peek()` method to `KafkaTestConsumer` (unnecessary
  given the fix above; would expand scope).
- Any changes to E2EBase, application-integration.yml, or other scenarios.

---

# Acceptance Criteria

- [ ] The accumulated-records list is declared outside the `await()` lambda and
      passed by reference (effectively final capture) into the lambda body.
- [ ] Each Awaitility retry appends newly drained records to the accumulated
      list before filtering for the match — no record is lost across retries.
- [ ] The failure message on a genuine field mismatch names the mismatched
      field (already provided by AssertJ's `.as(...)` chain — this AC verifies
      the message is reachable, i.e. the match is found and the bad field is
      the assertion that throws, not the null-match guard).
- [ ] All existing envelope field assertions remain unchanged (eventType,
      aggregateType, aggregateId, producer, eventVersion, eventId UUID
      parseability, payload.warehouse.warehouseCode).
- [ ] The test compiles and the e2e suite runs green (CI `e2e-tests` job passes).

---

# Related Specs

- `platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/events/master-events.md` — envelope field assertions remain aligned.

---

# Target Service

- `gateway-service` (e2e test only)

---

# Implementation Notes

Replace the lambda body pattern from:

```java
List<ConsumerRecord<String, String>> records = consumer.drain();  // WRONG — new list each retry
ConsumerRecord<String, String> match = records.stream()...findFirst().orElse(null);
assertThat(match).isNotNull();
```

with:

```java
// Declared BEFORE await() — captured by the lambda, persists across retries.
List<ConsumerRecord<String, String>> accumulated = new ArrayList<>();

await().atMost(Duration.ofSeconds(10))
       .pollInterval(Duration.ofMillis(500))
       .untilAsserted(() -> {
           accumulated.addAll(consumer.drain());   // accumulate, never discard
           ConsumerRecord<String, String> match = accumulated.stream()
               .filter(r -> warehouseId.equals(r.key()))
               .findFirst()
               .orElse(null);
           assertThat(match).as("...").isNotNull();
           // ... remaining field assertions unchanged
       });
```

This pattern is safe because:
- `accumulated` is an `ArrayList` on the test thread; the lambda runs on the
  same thread (Awaitility default). No concurrency concern.
- `consumer.drain()` itself is thread-safe (ConcurrentLinkedQueue).
- At-most-once event delivery from master-service means the record appears
  exactly once in the Kafka topic; accumulating does not produce duplicates.

---

# Edge Cases

- The event arrives on the very first `drain()` poll and all assertions pass
  in the same retry — no change in behavior vs. the previous code.
- A spurious record with a different key lands in the topic (from a concurrent
  test or a retry) — the accumulated list holds it but the `warehouseId` key
  filter skips it correctly.

---

# Failure Scenarios

- If `accumulated` is declared inside the lambda (wrong fix), the bug
  reappears. Verify by placing a deliberate typo in the `eventType` assertion
  string, running the test, and confirming the failure message names the field
  mismatch rather than reporting "no record received."

---

# Test Requirements

- Manual verification step: temporarily change
  `isEqualTo("master.warehouse.created")` to `isEqualTo("WRONG")` in the
  lambda; confirm the test fails with "eventType expected: WRONG but was:
  master.warehouse.created" within 10s (not with "no record received after 10s").
- Revert the deliberate typo before committing.
- No additional test file needed — the fix is inside the existing e2e test.

---

# Definition of Done

- [ ] Accumulated-records pattern in place
- [ ] Manual verification step executed and reverted
- [ ] CI `e2e-tests` job green
- [ ] Ready for review
