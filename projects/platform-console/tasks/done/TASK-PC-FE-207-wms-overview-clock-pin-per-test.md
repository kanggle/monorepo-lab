# TASK-PC-FE-207 — wms-overview-state test fails every KST Monday (module-level clock pin lost under the concurrent suite)

- **Status**: done
- **Project**: platform-console
- **Service**: console-web (`tests/unit/wms-overview-state.test.ts`)
- **Type**: bug fix (flaky/date-boundary test)
- **Analysis model**: Opus 4.8 / **구현 권장**: Sonnet (single test-file lifecycle fix)

## Goal

`tests/unit/wms-overview-state.test.ts` fails on the shared **Frontend unit tests** CI job on
**every KST Monday** (and the 1st of a month) with:

```
expected { today: 2, week: 2, month: 6 }       to deeply equal { today: 2, week: 5, month: 6 }
expected { today: null, week: null, month: 6 } to deeply equal { today: null, week: 5, month: 6 }
```

This blocks the shared console-web vitest gate for **all** PRs on those dates (it surfaced while
merging an unrelated iam-platform PR, BE-484). Make the test deterministic on every calendar date.

## Root cause

The 배송 (shipments) flow reads a per-period count keyed by the exact `shippedAtFrom` **bound
string** the source passes, mapped through a `switch`/`if` on `bounds.{today,week,month}StartInstant`.
`kstPeriodBounds()` ([kst-period.ts](../../apps/console-web/src/features/wms-ops/api/kst-period.ts))
derives ISO-week from the current KST date, so on a **KST Monday** `weekStartInstant ===
todayStartInstant` — the two `case`s collide, the later one goes unreachable, and the week read is
mis-bucketed onto today's value (or today's rejection).

The test tried to dodge this by pinning the clock to a fixed mid-week instant
(`vi.useFakeTimers({ now: 2026-07-15 })`) **at module top level**. That pin does not survive into
test execution: it runs at collection, and `tests/setup.ts`'s global `afterEach(vi.restoreAllMocks)`
plus the concurrent full-suite run (sibling files resetting global `Date`) tear it down before this
file's tests run — so the source saw the REAL KST clock. It only reproduces under the concurrent
suite on a KST Monday, which is why isolated / non-Monday runs pass.

## Scope

- `tests/unit/wms-overview-state.test.ts` **only**. Move the fake-timer pin AND the `bounds`
  computation from module top level into `beforeEach` (re-established before every test, inside the
  lifecycle), add `afterEach(vi.useRealTimers)`, and make `bounds` a `let` typed `KstPeriodBounds`.
- No source change: `kstPeriodBounds()`'s Monday `weekStart === todayStart` is correct calendar
  behaviour ("this week so far" == "today so far" on Monday); only the test's clock discipline was wrong.

## Acceptance Criteria

- `wms-overview-state.test.ts` passes on a KST Monday under both the isolated run and the full
  concurrent `vitest run` suite.
- The full console-web suite stays green (no fake-`Date` leak into sibling files).
- Lint + tsc clean.

## Related Specs

- `projects/platform-console/specs/features/` — wms 운영 개요 (TASK-PC-FE-166/174) domain-landing overview

## Related Contracts

- n/a (test-only change; no producer/API contract touched)

## Edge Cases

- The 1st of a KST month (`monthStart === todayStart`) — same collision class; the mid-month pin
  (07-15) keeps all three bounds distinct.
- A sibling test file that leaves fake timers active — `beforeEach` re-pins regardless of prior state;
  `afterEach(vi.useRealTimers)` restores so this file does not leak into siblings.

## Failure Scenarios

- Re-introducing a module-top-level pin (future regression) → reverts to the KST-Monday failure.
  Mitigation: the in-file comment documents why the pin MUST live in `beforeEach`.
- Verified locally on a real KST Monday: module pin removed → the two exact CI failures reproduce
  byte-identically; per-test `beforeEach` pin → 9/9 pass; full suite 228 files / 2449 tests pass.
