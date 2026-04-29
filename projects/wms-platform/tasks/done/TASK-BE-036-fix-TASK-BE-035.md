# TASK-BE-036 — Fix issues found in TASK-BE-035 (outbound-service schema alignment)

| Field | Value |
|---|---|
| **Task ID** | TASK-BE-036 |
| **Title** | outbound-service: Populate `outcome` column in EventDedupePersistenceAdapter |
| **Status** | ready |
| **Owner** | backend |
| **Tags** | outbound, fix, dedupe, schema |

---

## Goal

Fix the issue found in the TASK-BE-035 review:

**[Warning] `EventDedupePersistenceAdapter` does not populate the `outcome` column**
— TASK-BE-035 added the `outcome` column (VARCHAR 30) to `outbound_event_dedupe` via V9
migration and extended `OutboundEventDedupe` with a full constructor that accepts `outcome`.
However, `EventDedupePersistenceAdapter.process()` still uses the legacy two-argument
constructor (`eventId, eventType, clock.instant()`), leaving `outcome = NULL` on every new
row. The entity Javadoc and `domain-model.md` §8 both state that new rows must never be null
for `outcome`. The adapter must use the full constructor and pass the resolved `Outcome` enum
value to the DB column.

---

## Scope

### In Scope

- `EventDedupePersistenceAdapter.process()` — use `OutboundEventDedupe(eventId, eventType, clock.instant(), outcome.name())` instead of the legacy three-arg constructor on every write path:
  - First insert (before `entityManager.flush()`): write with `outcome = "APPLIED"` tentatively, then overwrite to `"FAILED"` if `work.run()` throws; alternatively, set outcome after work completes and update the row (simpler: write outcome at the end of the method, or use a two-step approach).
  - Recommended approach: insert with `outcome = null` is NOT acceptable. Persist the row after outcome is determined. Since outcome is only known after `work.run()`, the cleanest implementation is:
    1. Insert a dedupe row with a placeholder `outcome = "APPLIED"` before `flush()` (to detect duplicate quickly via PK violation).
    2. After `work.run()` completes without exception, the `outcome = "APPLIED"` row is correct.
    3. If `work.run()` throws, the row is already written — the caller's outer TX will roll back the entire unit anyway (Propagation.MANDATORY), so no stale row persists.
  - On duplicate detection (`DataIntegrityViolationException`): the row was NOT written by this call; no update needed, just return `IGNORED_DUPLICATE`.
- `EventDedupePersistenceAdapterTest` — add assertions that verify the `outcome` field on the saved `OutboundEventDedupe` entity:
  - `firstOccurrenceRunsWorkAndReturnsApplied`: capture the saved entity (via `ArgumentCaptor`) and assert `outcome == "APPLIED"`.
  - Add a test `workExceptionRollsBackEntireTransaction`: verify that when `work.run()` throws, `Propagation.MANDATORY` rolls back the outer TX; the dedupe row is not persisted outside the TX. (Note: this is largely a contractual assertion, not easily unit-tested without TX support — document in Javadoc if a new test cannot be added without Testcontainers.)
- The legacy no-arg and two-arg constructors in `OutboundEventDedupe` may be kept (they are used by pre-V9 deserialization paths), but no new production code should call the constructor without an `outcome` argument.

### Out of Scope

- Any other columns or entities beyond `outcome` in `EventDedupePersistenceAdapter`.
- `OutboundOutboxEntity` `aggregateType`/`eventVersion`/`partitionKey` population — that belongs to TASK-BE-037+ (outbox publisher).
- New domain logic.

---

## Acceptance Criteria

1. `EventDedupePersistenceAdapter.process()` inserts a row with `outcome = "APPLIED"` when the work lambda completes successfully.
2. `EventDedupePersistenceAdapter.process()` does **not** insert a row (duplicate case) and returns `IGNORED_DUPLICATE` without writing an `outcome = NULL` row.
3. `EventDedupePersistenceAdapterTest.firstOccurrenceRunsWorkAndReturnsApplied` asserts `outcome == "APPLIED"` on the saved entity via `ArgumentCaptor<OutboundEventDedupe>`.
4. `./gradlew :projects:wms-platform:apps:outbound-service:test` passes (zero failures).
5. No production code path calls `new OutboundEventDedupe(eventId, eventType, instant)` (the legacy outcome-less constructor) after this fix.

---

## Related Specs

- `specs/services/outbound-service/domain-model.md` — §8 EventDedupe (`outcome` column definition)

---

## Related Contracts

- None (internal infrastructure table only; no external contract surface)

---

## Edge Cases

1. `work.run()` throws a `RuntimeException` — the outer TX (Propagation.MANDATORY) rolls back, so neither the dedupe row nor any domain writes persist. The `outcome` field is irrelevant in this path because the row does not survive the rollback.
2. Duplicate event with `outcome = NULL` in the DB (written before V9 migration) — the adapter must still return `IGNORED_DUPLICATE` correctly; no update to the existing row is needed or allowed (W2 append-only invariant).
3. Calling `process()` outside a transaction — `Propagation.MANDATORY` already throws `IllegalTransactionStateException`; this is pre-existing and must not be changed.

---

## Failure Scenarios

1. If `ArgumentCaptor`-based assertion requires Mockito `capture()` on `save()` but the test currently uses `verify(repository, times(1)).save(any(...))` — update to capture and extract the saved entity for field assertion.
2. The `outcome` field on `OutboundEventDedupe` is `String`, not an enum type — the adapter must pass `EventDedupePort.Outcome.APPLIED.name()` (i.e. the string `"APPLIED"`), matching the VARCHAR(30) column.
