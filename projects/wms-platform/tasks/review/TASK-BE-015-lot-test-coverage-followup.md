# Task ID

TASK-BE-015

# Title

Lot aggregate — missing test classes (follow-up from TASK-BE-006 review)

# Status

ready

# Owner

backend

# Task Tags

- code
- test

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

Fix issues found in TASK-BE-006. The Lot aggregate implementation (BE-006)
landed all production code correctly but is missing five test classes required
by the task's Definition of Done and by platform/testing-strategy.md.

Missing classes:

1. LotControllerTest — @WebMvcTest covering all 7 endpoints, ETag format, and at
   least one 422 case asserting the ISO 8601 timestamp regex (post-BE-008 envelope).
2. LotServiceAuthorizationTest — @PreAuthorize role matrix.
3. LotPersistenceAdapterH2Test — ORM paths via H2 in-memory DB.
4. LotPersistenceAdapterTest (Testcontainers, Postgres 16 alpine) — real constraint
   detection, raw-SQL ck_lots_date_pair bypass, partial index correctness, cross-SKU
   same lot_no uniqueness.
5. LotExpirationSchedulerTest — mocked use case; batch-failure isolation; disabled
   property.

Additionally, the five Lot event JSON schemas and lot-response.schema.json exist but
are never loaded by EventContractTest or HttpContractTest — add Lot cases to each.

---

# Scope

## In Scope

- Create the five missing test classes listed in the Goal.
- Extend EventContractTest: lotEvent_envelopeValidates triggers a Lot create via HTTP
  and validates the received wms.master.lot.v1 envelope against event-envelope.schema.json.
- Extend HttpContractTest: Lot create + get-by-id round-trip validating
  lot-response.schema.json.
- LotControllerTest must include at least one 422 case asserting the ISO 8601
  timestamp regex pattern on the error envelope (per BE-008 SkuControllerTest pattern).

## Out of Scope

- Changes to production code (all production code passed BE-006 review).
- New features beyond what BE-006 specified.

---

# Acceptance Criteria

- [ ] LotControllerTest exists with 20+ test methods covering all 7 endpoints
- [ ] At least one LotControllerTest 422 case asserts the ISO 8601 timestamp regex
- [ ] LotServiceAuthorizationTest covers the @PreAuthorize role matrix
- [ ] LotPersistenceAdapterH2Test covers ORM paths (insert, update, version-bump, lists)
- [ ] LotPersistenceAdapterTest (Testcontainers, disabledWithoutDocker=true) covers:
      unique constraint detection, raw-SQL CHECK bypass, partial index correctness,
      cross-SKU same lot_no allowed
- [ ] LotExpirationSchedulerTest covers: happy path, disabled property, one-failing-lot
      isolation (does not abort the batch)
- [ ] EventContractTest has a Lot event case validating the envelope schema
- [ ] HttpContractTest has a Lot HTTP case validating lot-response.schema.json
- [ ] ./gradlew :projects:wms-platform:apps:master-service:check passes

---

# Related Specs

- platform/testing-strategy.md
- specs/contracts/http/master-service-api.md Section 6
- specs/contracts/events/master-events.md Section 6
- specs/services/master-service/architecture.md
- projects/wms-platform/tasks/done/TASK-BE-006-lot-aggregate.md (original, see Test Requirements)

# Related Contracts

- specs/contracts/http/master-service-api.md Section 6
- specs/contracts/events/master-events.md Section 6

---

# Edge Cases

- LotPersistenceAdapterTest must use @Testcontainers(disabledWithoutDocker = true)
  so Windows-native test runs skip it (Windows + Docker 29 Testcontainers blocker).
- The raw-SQL bypass for ck_lots_date_pair must insert via JdbcTemplate or native
  query — not the domain factory — to confirm the DB-level guard fires independently.
- LotExpirationSchedulerTest.disabled case: set wms.scheduler.lot-expiration.enabled=false
  via @TestPropertySource and assert that runScheduled() does NOT delegate to the use case.

---

# Failure Scenarios

- If a LotControllerTest mock setup is incorrect and 422 paths pass for the wrong
  reason, the ISO 8601 timestamp regex check acts as a canary.
- If the Testcontainers test cannot connect to Docker, disabledWithoutDocker=true
  skips gracefully rather than failing the build.
