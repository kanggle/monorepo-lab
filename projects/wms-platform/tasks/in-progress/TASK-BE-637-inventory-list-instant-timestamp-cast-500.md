# Task ID

TASK-BE-637

# Title

WMS inventory-service list endpoints return **500** — native-query rows are cast to `java.sql.Timestamp` but the driver returns `java.time.Instant` (`ClassCastException`)

# Status

in-progress

# Owner

backend

# Task Tags

- code
- test

---

> **Analysis model:** Opus 5.5 / **Recommended implementation model:** Sonnet (mechanical conversion at four sites + one IT)
> Source: the 27th demo window (2026-10-14 UTC, AMI `ami-0775fa009a12a824d` @ `73daae263`), found while measuring iam `TASK-BE-632` AC-1. Not related to that ticket: the call fails with a full-permission token too.

# Dependency Markers

- None. Live verification of the fix needs a re-bake (the demo runs the baked jar).

# Goal

`GET /api/v1/inventory` returns `500 INTERNAL_ERROR` on the demo host, with any token. The inventory-service log says:

```
c.e.w.e.CommonGlobalExceptionHandler - Unexpected error
java.lang.ClassCastException: class java.time.Instant cannot be cast to class java.sql.Timestamp
```

The same cast appears at four places, each mapping a native-query `Object[]` row:

| File | Line(s) |
|---|---|
| `adapter/out/persistence/inventory/InventoryRepositoryImpl.java` | 245 · 247 · 248 |
| `adapter/out/persistence/transfer/StockTransferRepositoryImpl.java` | 115 |
| `adapter/out/persistence/adjustment/StockAdjustmentRepositoryImpl.java` | 97 |
| `adapter/out/persistence/movement/InventoryMovementRepositoryImpl.java` | 158 |

Only the inventory list is measured as 500. The other three lists share the cast and are presumed broken the same way. AC-0 measures them.

# Scope

## In Scope

- AC-0 (red first): an IT against the real database that calls each of the four list endpoints, and is red on the current code at the cast. Write down why the existing tests did not catch it (for example: a unit test mocking `Object[]` with `Timestamp`, or an IT driver or dialect that returns `Timestamp`).
- Convert all four sites through one helper that accepts `Instant`, `Timestamp`, `OffsetDateTime` and `LocalDateTime`. Read the value; never assume its class.
- Grep the sibling WMS services for the same pattern. It was 0 hits outside inventory-service on 2026-10-14; re-measure.

## Out of Scope

- Moving the queries off native SQL.

# Acceptance Criteria

- [ ] **AC-0**: each of the four endpoints is measured red, with the reason recorded. The ticket also records why no existing test caught it.
- [ ] **AC-1**: all four list endpoints return 200 with rows, in an IT on the same driver and dialect the service runs with.
- [ ] **AC-2**: a test pins the helper against each accepted input type, and fails on an unknown type with a message naming the column.
- [ ] **AC-3**: live, after the next re-bake. Use `GET /api/v1/inventory?size=1` with the demo operator token, and check the console «WMS 재고» screen. Without a bake, record ⚪ and the reason.

# Related Specs

- `projects/wms-platform/specs/contracts/http/inventory-service-api.md` § 1.1 (and the transfer · adjustment · movement list sections)

# Related Contracts

- Same as above. Responses do not change.

# Edge Cases

- A `NULL` timestamp column (for example `lastMovementAt` on a row with no movement). The helper must keep `null`, not throw.

# Failure Scenarios

1. Fixing only the inventory list. The three sibling lists keep failing, because the cast is copied, not shared.
2. Proving the fix with a mocked `Object[]` row. That is the same shape of test that let the defect through.

# Implementation Notes (2026-10-16 UTC)

- **AC-0 (red, measured)**: `NativeListQueryTimestampIntegrationTest` (Testcontainers PostgreSQL 16, through the four repository ports) on the unfixed code: 4 of 4 methods red with `java.lang.ClassCastException` at the repository call (inventory · transfer · adjustment · movement) — CI job `Integration (inventory + inbound + gateway-service, Testcontainers) / integration`, run 38890745632, job 116939445990, head `b166f110c`; the Gradle task reported `30 tests completed, 4 failed` (the other 26 inventory ITs green). The CI log prints no exception message, so the `Instant`→`Timestamp` wording comes from the live log in the Goal, not from this run.
- **Why no existing test caught it**: no test called any of the four list queries against a real database. The only coverage of the list paths is `@WebMvcTest` slice tests (`InventoryQueryControllerSliceTest`, `MovementQueryControllerSliceTest`, `TransferControllerSliceTest`, `AdjustmentControllerSliceTest`) that mock the use case, so the repository `mapRow` never ran; the existing ITs (`AdjustmentTransferIntegrationTest`, …) drive the mutation use cases and read via JDBC, not the native list queries.
- **Fix**: one helper `adapter/out/persistence/NativeRowValues.instant(Object, String column)` (Instant · Timestamp · OffsetDateTime · LocalDateTime as UTC · null kept · unknown type -> `IllegalStateException` naming the column and class), used at all four sites. Pinned by `NativeRowValuesTest`.
- **Sibling grep** (`java.sql.Timestamp` in `projects/*/**/src/main` + `libs/`): the 4 sites above only. iam `JdbcEventDedupeAdapter` / `SasAuthorizationRevocationAdapter` use `Timestamp.from(...)` as a bound parameter and `ConsumerPoolLegacyMoveRepositoryImpl` uses `rs.getTimestamp(...)` (JDBC API, type-safe) — not the defect. Other wms `createNativeQuery` users (masterref repositories, `AsnRepositoryImpl`) are DML / `Number` reads, no timestamp column read.
