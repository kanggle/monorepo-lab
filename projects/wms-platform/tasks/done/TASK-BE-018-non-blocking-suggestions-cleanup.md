# Task ID

TASK-BE-018

# Title

Batch cleanup — 10 non-blocking suggestions accumulated across reviews

# Status

ready

# Owner

backend

# Task Tags

- refactor
- cleanup
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

Apply the 10 non-blocking suggestions accumulated across the BE-008
through BE-016 reviews. None of these block anything individually; the
intent is a single batched PR that knocks them all out so the
codebase matches reviewer-approved polish level.

---

# Scope

## In Scope — 10 items

### Production code

1. **`LotService.java:95`** — `Sku.isActive()` helper to insulate from
   `WarehouseStatus` enum leak. Currently `parent.getStatus() != WarehouseStatus.ACTIVE`
   compares an Sku's status via the Warehouse enum (works because Sku
   reuses it, but brittle). Add `boolean Sku.isActive()` and use it.

2. **`LotService.java:201`** — `expireBatch` per-row `REQUIRES_NEW`
   transaction. Currently all rows share one big transaction; a
   constraint-violation mid-loop would roll back everything. Change to
   per-row `REQUIRES_NEW` so one failing Lot doesn't abort the batch.

3. **`GatewayErrorHandler.java:43-44`** — fallback JSON string
   concatenates `envelope.code()` + `envelope.message()` without escaping.
   Replace with either a minimal-escape helper or a dedicated fallback
   `ObjectMapper`.

4. **`ApiErrorEnvelope.java:31-38`** — `ApiError` record should enforce
   `Objects.requireNonNull(timestamp)` via compact canonical constructor
   so future callers bypassing the factory still can't create a null
   timestamp.

### Tests

5. **`LotServiceAuthorizationTest.java:89-95`** — replace bare
   `try/catch(Exception ignored)` with
   `assertThatThrownBy(...).isNotInstanceOf(AccessDeniedException.class)`
   (matches `SkuServiceAuthorizationTest` pattern).

6. **`LotExpirationSchedulerTest.java:80-93`** — failure-isolation test
   currently asserts `result.failed() == 0` after exception; should
   assert `result.failed() >= 1` to verify count reflects actual failure.

7. **`LotControllerTest.java:56`** — `ISO_TIMESTAMP_REGEX` constant
   duplicated verbatim from `SkuControllerTest`. Extract to a shared
   `TestConstants` (under `src/test/java/com/wms/master/testsupport/`).

8. **Korean `@DisplayName` consistency** — `LotExpirationSchedulerTest`
   (line 47) uses Korean display names; rest of the project uses English.
   Convert to English for consistency.

9. **`GatewayMasterE2ETest.java:180`** (pre-existing noted in INT-006
   review) — `UUID.fromString(...)` called bare; parse failure produces
   `IllegalArgumentException` not a descriptive AssertJ message. Wrap:
   `assertThatNoException().isThrownBy(() -> UUID.fromString(eventId))`.

10. **`EventContractTest.lotEvent_envelopeValidates`** (noted in BE-016
    review) — currently asserts only against generic
    `event-envelope.schema.json`. Add an assertion against
    `master-lot-created.schema.json` too for full envelope-depth parity
    (other aggregates already do both).

## Out of Scope

- Anything not in the list above
- New behaviour changes
- Integration test flakiness fixes (see TASK-BE-017)

---

# Acceptance Criteria

- [ ] All 10 items addressed in a single PR titled "refactor(wms): TASK-BE-018 — batch cleanup"
- [ ] `./gradlew :projects:wms-platform:apps:master-service:check` passes
- [ ] No existing test regressed
- [ ] Each commit (or section of the single commit) references the item number (1–10) so reviewers can trace

---

# Related Specs

- `platform/error-handling.md` (for ApiErrorEnvelope changes)
- `rules/domains/wms.md` (Sku.isActive semantic correctness)

# Related Contracts

- None (no contract changes)

---

# Target Service

- `master-service` (items 1, 2, 4, 5, 6, 7, 8, 10)
- `gateway-service` (items 3, 9)

---

# Implementation Notes

- **Item 2 (REQUIRES_NEW)**: inject the use-case into itself via `@Autowired LotService self` or extract a helper bean. Spring's `@Transactional` AOP requires the call to go through a proxy boundary; internal self-calls don't trigger new transactions. Simplest clean approach: extract a `LotExpirationBatchProcessor` bean with `@Transactional(propagation = REQUIRES_NEW)` on its `expireOne(Lot)` method, and have `LotService.expireBatch` loop + call it.

- **Item 7 (TestConstants)**: simple `public final class TestConstants { public static final String ISO_TIMESTAMP_REGEX = "..."; }`. Update both Sku and Lot controller tests to import.

- **Item 10 (Lot schema)**: `LotCreated_SCHEMA` field might already exist in `EventContractTest`. If so, just add the second assertion to `lotEvent_envelopeValidates`. If not, add the schema load using the existing pattern (see `SkuCreated_SCHEMA`).

- Each fix is small. Do them in sequence with a fresh run of the relevant test class after each to catch regressions early.

---

# Edge Cases

- Item 2: if `expireOne` throws a domain exception (e.g., `InvalidStateTransitionException`), the per-row tx still rolls back cleanly; only the outer loop continues
- Item 4: any existing factory that doesn't pass a timestamp currently would start failing at construction — check all `ApiError.of(...)` callers

---

# Failure Scenarios

- Item 2 change could subtly alter metric emission timing — observe the batch integration test (if any) for regression

---

# Test Requirements

- No new tests required (items are cleanup / test-quality improvements)
- Existing tests still green

---

# Definition of Done

- [ ] All 10 items in one PR
- [ ] `./gradlew check` passes
- [ ] Ready for review
