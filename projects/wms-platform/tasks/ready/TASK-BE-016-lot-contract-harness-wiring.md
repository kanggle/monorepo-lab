# Task ID

TASK-BE-016

# Title

Lot aggregate — contract harness wiring (follow-up from TASK-BE-015)

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- event
- api

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

Fix the remaining gap from TASK-BE-015. All 5 Lot test classes landed correctly
(LotControllerTest, LotServiceAuthorizationTest, LotPersistenceAdapterH2Test,
LotPersistenceAdapterTest, LotExpirationSchedulerTest). The single outstanding
item is that the contract harness is not wired to any Lot endpoints or event
schemas, even though all 5 Lot event JSON schemas and lot-response.schema.json
already exist under src/test/resources/contracts/.

Missing wiring:
1. EventContractTest — add a Lot event case: trigger a Lot create via HTTP and
   validate the received wms.master.lot.v1 envelope against
   /contracts/events/event-envelope.schema.json.
2. HttpContractTest — add a Lot create + get-by-id round-trip validating
   /contracts/http/lot-response.schema.json.

---

# Scope

## In Scope

- Extend EventContractTest with a lotEvent_envelopeValidates test method that:
  - Seeds a parent SKU (ACTIVE, LOT-tracked) via HTTP.
  - Creates a Lot on that SKU via POST /api/v1/master/skus/{skuId}/lots.
  - Consumes one record from topic wms.master.lot.v1 (poll timeout >= 15 s).
  - Validates the envelope against /contracts/events/event-envelope.schema.json.
- Extend HttpContractTest with:
  - createLot_matchesSchema — POST /api/v1/master/skus/{skuId}/lots 201, validate
    against /contracts/http/lot-response.schema.json.
  - getLot_matchesSchema — GET /api/v1/master/lots/{id} 200, validate against
    /contracts/http/lot-response.schema.json.
- No new production code. No schema file changes (schemas already exist).

## Out of Scope

- Changes to any of the 5 Lot test classes (all pass review as-is).
- New endpoints, domain logic, or persistence changes.
- Wiring the other 4 Lot event schema files (master-lot-updated, deactivated,
  reactivated, expired) — those require additional integration scaffolding and
  are deferred.

---

# Target Service

master-service

---

# Acceptance Criteria

- [ ] EventContractTest.lotEvent_envelopeValidates passes: creates a Lot over HTTP,
      consumes from wms.master.lot.v1, validates envelope against event-envelope.schema.json
- [ ] HttpContractTest.createLot_matchesSchema passes: POST 201 response validates
      against lot-response.schema.json
- [ ] HttpContractTest.getLot_matchesSchema passes: GET 200 response validates
      against lot-response.schema.json
- [ ] ./gradlew :projects:wms-platform:apps:master-service:check passes
- [ ] No production code is modified

---

# Related Specs

- platform/testing-strategy.md
- specs/contracts/http/master-service-api.md Section 6 (Lot endpoints)
- specs/contracts/events/master-events.md Section 6 (wms.master.lot.v1)
- specs/services/master-service/architecture.md

# Related Contracts

- specs/contracts/http/master-service-api.md Section 6
- specs/contracts/events/master-events.md Section 6
- src/test/resources/contracts/http/lot-response.schema.json
- src/test/resources/contracts/events/event-envelope.schema.json
- src/test/resources/contracts/events/master-lot-created.schema.json (reference only)

---

# Edge Cases

- The parent SKU must be ACTIVE and LOT-tracked; creating a Lot on a non-LOT-tracked
  SKU returns 422 STATE_TRANSITION_INVALID — the seeding step must specify
  trackingType=LOT.
- KafkaTestConsumer must be created before the HTTP POST to avoid a race where the
  event is produced before the consumer subscribes to the topic.
- lot-response.schema.json nullable fields (manufacturedDate, expiryDate) must use
  type: ["string","null"] or be marked optional — verify schema matches actual response
  before asserting.

---

# Failure Scenarios

- If the Kafka event is not received within 15 s, pollOne times out and the test
  fails with a clear timeout message — this indicates the outbox poller or Kafka
  broker setup has regressed, not a test issue.
- If lot-response.schema.json does not match the actual response shape, assertValid
  throws with a JSON Schema validation error listing the failing path — fix the
  schema file, not production code.
- If the parent SKU seed step returns a non-201 status, the test must fail fast with
  an assertThat status check before attempting the Lot create.

---

# Implementation Notes

- Follow the existing EventContractTest pattern exactly (warehouseEvent_envelopeValidates):
  KafkaTestConsumer in try-with-resources, pollOne(Duration.ofSeconds(15)), then
  ENVELOPE.assertValid(record.value()).
- The Lot create endpoint is nested under /api/v1/master/skus/{skuId}/lots — seed a
  parent SKU first. Use MasterServiceIntegrationBase helper methods or inline the HTTP
  call following the zone/location pattern already present.
- For HttpContractTest, load the schema with ContractSchema.load(...) as a static field,
  following the warehouse/zone/location pattern already in the class.
- Idempotency-Key header is required for POST requests (IdempotencyFilter is active
  in integration context). Use UUID.randomUUID().toString().

---

# Test Requirements

- No @SpringBootTest required — extend existing MasterServiceIntegrationBase.
- EventContractTest and HttpContractTest already inherit the full Postgres + Kafka +
  Redis stack from MasterServiceIntegrationBase; no additional containers needed.
- Tests must be added to the existing classes, not new classes.

---

# Definition of Done

- All 3 Acceptance Criteria pass green.
- No production code modified.
- ./gradlew :projects:wms-platform:apps:master-service:check passes.
- Task moved to done/ with review verdict APPROVED (or FIX NEEDED if issues found).
