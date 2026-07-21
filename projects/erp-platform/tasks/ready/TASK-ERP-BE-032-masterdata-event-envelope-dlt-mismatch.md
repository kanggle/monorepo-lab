# TASK-ERP-BE-032 — masterdata→read-model event envelope mismatch: every masterdata event routes to DLT (producer wire omits top-level `aggregateId`)

Status: ready

`(분석=Opus 4.8 / 구현 권장=Opus — cross-service event contract 정합 + regression 커버리지 설계, 실프로덕션 결함)`

---

## Goal

Close a **confirmed cross-service contract defect** found in the 2026-07-21 reconciliation audit and re-measured against `main` (`dd93fc420`): the `masterdata-service` outbox producer and the `read-model-service` consumer disagree on the event **envelope shape**. The consumer requires a **top-level `aggregateId`** field that the producer **never emits** (the producer puts the aggregate id in `partitionKey` and inside `payload`, but not as a top-level envelope field). Result: **every real masterdata change event is rejected by read-model-service as an invalid envelope and routed straight to the DLT** (no retry), so the integrated employee org-view never populates in production.

This is a genuine 3-way divergence (spec ↔ producer ↔ consumer) and a textbook *test-fixture-proves-nothing* trap: the end-to-end integration test is green because it **hand-builds a spec-shaped envelope**, not the producer's actual output.

### The three faces (re-measured — treat line numbers as a hypothesis, re-measure at start)

**1. Spec** — [`specs/contracts/events/erp-masterdata-events.md:77-89`](../../specs/contracts/events/erp-masterdata-events.md#L77-L89) declares a top-level `aggregateId`:

```json
{ "eventId": "<uuid>", "eventType": "erp.masterdata.department.changed",
  "occurredAt": "...", "tenantId": "erp", "source": "erp-platform-masterdata-service",
  "aggregateType": "department|...", "aggregateId": "<id>", "traceId": "...", "payload": { ... } }
```

**2. Producer (real wire)** — [`OutboxMasterdataEventPublisher.writeEvent`](../../apps/masterdata-service/src/main/java/com/example/erp/masterdata/infrastructure/outbox/OutboxMasterdataEventPublisher.java#L129-L154) builds a **different 7-field envelope** — no top-level `aggregateId` (nor `tenantId`/`aggregateType`/`traceId`); instead `schemaVersion` + `partitionKey`:

```java
envelope.put("eventId",       eventId.toString());
envelope.put("eventType",     eventType);
envelope.put("source",        SOURCE);
envelope.put("occurredAt",    occurredAt.toString());
envelope.put("schemaVersion", SCHEMA_VERSION);   // not in spec
envelope.put("partitionKey",  aggregateId);      // aggregate id lives HERE, not top-level "aggregateId"
envelope.put("payload",       payload);          // payload.aggregateId also carries it
```

Its Javadoc calls this "the EXACT 7-field shape the previous `BaseEventPublisher` path emitted" and the producer unit test [`OutboxMasterdataEventPublisherTest`](../../apps/masterdata-service/src/test/java/com/example/erp/masterdata/infrastructure/outbox/OutboxMasterdataEventPublisherTest.java#L75-L84) **locks that shape from both sides** — it asserts `eventId/eventType/source/occurredAt/schemaVersion/partitionKey` at the envelope top level and `aggregateId` only inside `payload`. So the producer wire is intentionally, verifiably missing a top-level `aggregateId`.

**3. Consumer** — [`MasterEventEnvelope.isValid()`](../../apps/read-model-service/src/main/java/com/example/erp/readmodel/adapter/inbound/messaging/MasterEventEnvelope.java#L38-L42) **requires** a non-blank top-level `aggregateId`:

```java
public boolean isValid() {
    return eventId != null && !eventId.isBlank()
            && aggregateId != null && !aggregateId.isBlank()   // ← bound to TOP-LEVEL @JsonProperty("aggregateId")
            && payload != null;
}
```

`aggregateId` is bound to the top-level `@JsonProperty("aggregateId")`, not `payload.aggregateId`, and the mapper reads only `record.value()` (the JSON body) — never the Kafka key/headers. So for a real producer event `aggregateId == null` → `isValid() == false` → [`EnvelopeToCommandMapper`](../../apps/read-model-service/src/main/java/com/example/erp/readmodel/adapter/inbound/messaging/EnvelopeToCommandMapper.java#L34-L37) throws `InvalidEnvelopeException` → the consumer routes to `.DLT` **with no retry** (`@RetryableTopic(exclude = InvalidEnvelopeException.class)`, [`DepartmentChangedConsumer`](../../apps/read-model-service/src/main/java/com/example/erp/readmodel/adapter/inbound/messaging/DepartmentChangedConsumer.java#L38-L57) and its 3 siblings — employee/jobgrade/costcenter).

### Why nothing caught it (the coverage gap this task must close)

The producer unit test asserts the **producer** shape; the consumer/E2E tests assert the **consumer** shape — and **no test bridges the two**. [`AbstractReadModelIntegrationTest.envelope()`](../../apps/read-model-service/src/test/java/com/example/erp/readmodel/integration/AbstractReadModelIntegrationTest.java#L246-L266) hand-builds a **spec-shaped** envelope (top-level `aggregateId`) and publishes *that* to Kafka, so `ReadModelEndToEndIntegrationTest` (and the DLT test) exercise a payload the real producer never sends. The org-view populates in the test and would silently never populate in production. A contract test that feeds the **producer's actual `OutboxMasterdataEventPublisher` output** through the **consumer's `MasterEventEnvelope`** would have failed immediately.

## Live repro (AC-0 gate — reproduce, do not inherit)

No live stack was stood up for this finding (audit was static + code re-measurement). AC-0 must confirm it dynamically before the fix:

- **Preferred (fast, Docker-free at the seam):** a new test that serialises a real `OutboxMasterdataEventPublisher` envelope (capture the `masterdata_outbox` row `payload` JSON — as `OutboxMasterdataEventPublisherTest` already does) and runs it through `EnvelopeToCommandMapper.map(json, topic)` → assert it currently throws `InvalidEnvelopeException` ("missing eventId/aggregateId/payload"). This RED test *is* the repro and becomes the regression guard (goes GREEN after the fix).
- **Optional (full-stack):** bring up masterdata-service + read-model-service + Kafka + MySQL, create a department via `POST /api/erp/masterdata/departments`, and observe the `erp.masterdata.department.changed.v1.DLT` topic receiving the event while `department_proj` stays empty and the consumer logs `Invalid envelope … routing to DLT`.

## Scope

**In:**
- Reconcile the masterdata event **envelope shape** across producer, consumer, and the `erp-masterdata-events.md` contract so a real producer event deserialises into a valid envelope and projects (AC-1 decides the direction).
- The equivalent fix for **all 5 aggregate topics** (department/employee/jobgrade/costcenter/businesspartner) — they share `OutboxMasterdataEventPublisher.writeEvent` and `MasterEventEnvelope`, so one seam fixes all; the 4 wired consumers (businesspartner has no read-model consumer yet — per the BE-007 amendment) all benefit.
- A **cross-service contract regression test** that feeds the producer's actual envelope output through the consumer's deserialisation + `isValid()` (the missing bridge). This is the AC that prevents recurrence.
- Update `OutboxMasterdataEventPublisherTest` and `AbstractReadModelIntegrationTest.envelope()` so the test fixtures match the reconciled wire (the integration `envelope()` helper must build the **producer's** shape, not a hand-authored spec shape).

**Out:**
- The approval / delegation event envelopes (`ApprovalEventEnvelope`, `DelegationEventEnvelope`) — verify in passing that they do NOT share this defect (the approval/delegation test helpers also hand-build top-level `aggregateId`; confirm the approval/delegation **producers** actually emit it before declaring them clean — if they don't, file a sibling task, don't silently widen this one).
- Dedupe / projection / org-view resolution logic (correct; unaffected once the envelope validates).
- The retry/DLT wiring itself (correct — the point is that valid events should never reach it).

## Acceptance Criteria

- **AC-0 (repro gate):** on current `main`, demonstrate a real `OutboxMasterdataEventPublisher` envelope is rejected by `EnvelopeToCommandMapper` / `MasterEventEnvelope.isValid()` (RED). Re-measure the file:line references above — code wins over this document.
- **AC-1 (direction — contract decision, do not skip):** choose and record one:
  - **Option A — producer conforms to the spec (recommended).** `Specs are the source of truth` (CLAUDE.md § Source of Truth Priority: contracts = layer 6) and the consumer already matches the spec. Add the top-level `aggregateId` to `OutboxMasterdataEventPublisher.writeEvent` (minimum), and reconcile the remaining spec-vs-wire deltas explicitly: emit top-level `tenantId`/`aggregateType`/`traceId` per the spec **or** amend the spec if they are genuinely optional; and resolve `schemaVersion`/`partitionKey` (present in the wire, absent from the spec §Envelope) by either documenting them in the contract or dropping them. The reconciled envelope must be a **single agreed shape** with no silent extra/missing fields. **No live consumer depends on the old 7-field shape** (`read-model-service` is the first and only consumer — `erp-masterdata-events.md` "v1 consumers = none" + BE-007 amendment), so there is no backward-compat barrier to conforming the producer.
  - **Option B — relax the consumer + rewrite the spec to the producer wire.** Make `MasterEventEnvelope` read `aggregateId` from `payload.aggregateId` (or `partitionKey`) as a fallback, and rewrite `erp-masterdata-events.md §Envelope` to the actual 7-field wire (add `schemaVersion`/`partitionKey`, drop top-level `aggregateId`/`tenantId`/`aggregateType`/`traceId`). **Disfavoured** — it makes the wire the SoT against the spec, and fragments consistency with the approval/delegation envelopes (which do carry top-level `aggregateId`).
  - **Recommendation: Option A.** The spec and consumer already agree; only the producer diverges, and it diverges to preserve a dead legacy shape with zero live consumers.
- **AC-2:** after the fix, a real producer envelope for **each of the 5 aggregates** deserialises to a valid `MasterEventEnvelope` (`isValid() == true`) and projects — no event reaches `.DLT` on the happy path.
- **AC-3 (the coverage gap):** a cross-service contract test asserts the **producer's actual output** (from `OutboxMasterdataEventPublisher`) is accepted by the **consumer's `MasterEventEnvelope` + `EnvelopeToCommandMapper`**. This test must fail on pre-fix `main` and pass after. The two existing shape-locking tests (`OutboxMasterdataEventPublisherTest`, `AbstractReadModelIntegrationTest.envelope()`) are updated to the reconciled shape so they can no longer drift apart.
- **AC-4:** the `erp-masterdata-events.md §Envelope` example is byte-consistent with what the producer emits (whichever direction AC-1 picks) — a reader copying the example gets an envelope the consumer accepts.
- **AC-5:** `./gradlew :projects:erp-platform:apps:masterdata-service:check :projects:erp-platform:apps:read-model-service:check` green; the read-model integration suite (Testcontainers) green on CI Linux (authority — local Windows Docker is host-dependent, `project_testcontainers_docker_desktop_blocker`).

## Related Specs

- `projects/erp-platform/specs/contracts/events/erp-masterdata-events.md` — §Envelope (the contract to reconcile), §Topics, §Payload schemas.
- `projects/erp-platform/specs/services/read-model-service/architecture.md` — Failure Mode 2 (invalid envelope → DLT) referenced by `MasterEventEnvelope`.
- `projects/erp-platform/specs/services/masterdata-service/architecture.md` — outbox / event publication.
- `rules/traits/transactional.md` T2/T3 (outbox + dedupe), `rules/domains/erp.md` § Internal Event Catalog.

## Related Contracts

- `projects/erp-platform/specs/contracts/events/erp-masterdata-events.md` (primary — this IS the contract in dispute).
- `projects/erp-platform/specs/contracts/events/read-model-subscriptions.md` — consumer-side rules for the 4 subscribed topics (BE-007).

## Edge Cases

- **businesspartner** has no read-model consumer in this increment (BE-007 amendment) — the fix still corrects its producer wire for the future consumer, but AC-2 can only assert projection for the 4 wired aggregates. Do not add a businesspartner consumer here (out of scope).
- **`payload.aggregateId` already present** — the producer's payload carries `aggregateId` today. If Option B is chosen, ensure the fallback reads it robustly (non-blank) and does not mask a genuinely malformed event (a truly aggregateId-less event must still DLT).
- **Approval/delegation envelopes** use the same `envelope()`-style hand-built fixtures — confirm their producers emit top-level `aggregateId` before assuming they are clean; the same trap may hide there.
- **Dedupe key** — `MasterChangeCommand` keys dedupe on `eventId` (present in the wire), so dedupe is unaffected by the fix; verify the regression test still exercises the duplicate-eventId idempotency path.

## Failure Scenarios

- **Silent DLT in production (current state):** operators create master data, the API returns 201, but the read-model never updates and the only signal is DLT depth + an error log — the org-view silently serves stale/empty data. The fix + AC-3 make this a compile/test-time failure instead of a production data-integrity gap.
- **Fix the code, leave the test hand-building the spec shape:** if `AbstractReadModelIntegrationTest.envelope()` keeps hand-authoring the envelope, the E2E test stays green regardless of the producer, and the seam can drift again. AC-3 forces the test to consume the producer's real output.
- **Half-reconcile (Option A but only add `aggregateId`):** adding the top-level `aggregateId` alone restores function but leaves `schemaVersion`/`partitionKey`/`tenantId`/`aggregateType`/`traceId` still mismatched vs the spec — a future strict consumer or contract test reopens the drift. AC-1/AC-4 require the envelope to be a single agreed shape, not a minimal patch.
