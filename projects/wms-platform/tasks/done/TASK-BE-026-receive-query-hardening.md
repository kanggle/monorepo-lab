# Task ID

TASK-BE-026

# Title

inventory-service receive/query — remove dead code, decide restore-invariant policy, harden increment unit test

# Status

review

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

Close three correctness/cleanliness issues found in the TASK-BE-022 review
(verdict 2026-04-28 FIX NEEDED):

1. **Dead `isFreshlyCreated` method + stale comment block in
   `ReceiveStockService`.** The method is unreferenced and would fire a
   spurious `inventoryRepository.findById` query if invoked. The comment
   block above it describes a "thread-local flag" mechanism that was never
   implemented and is now misleading.
2. **`InventoryMovement.restore` bypasses the W2 structural invariant.** The
   `create` factory enforces `qtyAfter == qtyBefore + delta` and
   `qtyAfter >= 0`. The `restore` path used by the JPA mapper invokes the
   constructor directly, so a corrupted DB row would load silently. The
   policy must be made explicit: either add a soft assertion (logging or
   throwing on the JPA load path), or add a class-level Javadoc note that
   `restore` deliberately trusts persisted data and accepts the trade-off.
3. **`existingInventoryRowGetsIncremented` does not actually distinguish
   insert vs update.** The current `FakeInventoryRepo` substitutes either
   path identically; a service that always called `insert` would still pass
   the assertion. The test must be tightened so that AC-3 ("if a row exists,
   `available_qty += qty`") has a reliable guard.

---

# Scope

## In Scope

- Remove `ReceiveStockService.isFreshlyCreated` and the preceding
  comment block referencing the unimplemented "thread-local flag".
- Pick **one** of the two policies for `InventoryMovement.restore` and
  apply it consistently:
  - **Policy A — soft-assert**: replicate the structural guard inside
    `restore` and throw `IllegalStateException` on load. Add a unit test
    covering an inconsistent DB row.
  - **Policy B — documented trust**: add a class-level Javadoc paragraph
    explaining that `restore` is the JPA-load entry point and trusts
    persisted data; cross-reference the `create` factory as the only place
    where the W2 structural invariant is enforced.
  Pick the policy that matches the project's existing `restore`-pattern
  conventions (check `Inventory.restore`, `Reservation.restore`,
  `StockAdjustment.restore`, `StockTransfer.restore` — the choice should be
  uniform across aggregates).
- Tighten `existingInventoryRowGetsIncremented` so that:
  - the test seeds `FakeInventoryRepo` with an existing row at qty 20 *before*
    the service call (not via `existing.receive(20, …)` mutation chained into
    the same in-memory object the service will mutate again);
  - the fake repo distinguishes which method was called (track an `insertCount`
    and `updateCount`) and the assertion verifies the **update** path was
    taken, not insert;
  - the final qty assertion (50) reflects the actual increment branch.

## Out of Scope

- Any change to the `Inventory` aggregate's domain methods.
- Any change to `OutboxWriter`, `OutboxPublisher`, or the contract envelope.
- Any change to `PutawayCompletedConsumer` (covered by separate fix tasks if
  needed).
- Adding new acceptance criteria beyond the three review findings.

---

# Acceptance Criteria

- [ ] `ReceiveStockService` no longer contains `isFreshlyCreated` or its
      preceding stale comment block.
- [ ] `InventoryMovement.restore` policy is consistent with the rest of the
      codebase and either (a) enforces the W2 structural guard with a test,
      or (b) documents trust in persisted data via class-level Javadoc.
- [ ] `existingInventoryRowGetsIncremented` distinguishes insert vs update
      via fake-repo counters and asserts the update path was taken.
- [ ] `./gradlew :apps:inventory-service:test` is green.
- [ ] No behavioural change in production — only test + cleanup +
      documentation/assertion changes.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0.

- `rules/domains/wms.md` — W2 invariant
- `specs/services/inventory-service/domain-model.md` — Inventory + InventoryMovement

# Related Skills

- `.claude/skills/backend/architecture/hexagonal`
- `.claude/skills/backend/testing/fakes-and-test-doubles`

---

# Related Contracts

- `specs/contracts/events/inventory-events.md` (no changes; reference only)

---

# Target Service

- `inventory-service`

---

# Architecture

Follow:

- `specs/services/inventory-service/architecture.md`

---

# Implementation Notes

- `Inventory.java`, `Reservation.java`, `StockAdjustment.java`,
  `StockTransfer.java` — check each one's `restore` factory before deciding
  the policy. Uniformity matters more than the specific choice; whichever
  the existing aggregates do, mirror in `InventoryMovement`.
- The `FakeInventoryRepo` lives inside `ReceiveStockServiceTest` (or a
  shared test fixture). Add `int insertCalls` / `int updateCalls` fields and
  expose them as package-private getters; assert in the test.
- Do not change the public signatures of `InventoryRepository` /
  `OutboxWriter` / `MasterReadModelPort`.

---

# Edge Cases

- A row that already exists with non-zero `version` increments correctly
  with `updateWithVersionCheck`; the test must seed a row with version > 0
  so a buggy "always-insert" implementation would surface a primary-key
  collision in the fake.
- `restore` invariant test (Policy A only): a DB row with `qty_before=10`,
  `delta=5`, `qty_after=20` should fail to load.

---

# Failure Scenarios

- Wrong restore policy chosen vs the rest of the codebase → reviewer flags
  inconsistency. Mitigation: explicit grep across all `restore(` factories
  before committing.
- Tightened test reveals a real bug in service path → fix it as part of
  this task; widen scope under the same task ID.

---

# Test Requirements

- Unit test (existing) updated as described above.
- One additional unit test only if Policy A is chosen for
  `InventoryMovement.restore`.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests updated/added per AC
- [ ] Tests passing
- [ ] PR description references TASK-BE-022 review verdict
- [ ] Ready for review
