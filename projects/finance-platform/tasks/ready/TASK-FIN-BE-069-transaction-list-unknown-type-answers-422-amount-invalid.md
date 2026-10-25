# Task ID

TASK-FIN-BE-069

# Title

`GET /api/finance/accounts/{id}/transactions` answers an unknown `type` / `status` filter with 422 `AMOUNT_INVALID` — make it 400 `VALIDATION_ERROR`

# Status

ready

# Owner

backend

# Task Tags

- code
- bug
- contract

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (reuse the metrics `period` mechanism; contract line + tests)
> Drafted 2026-10-25 UTC. Owner decision 2026-10-25 UTC (AskUserQuestion): «400 VALIDATION_ERROR 로 (Recommended)». Facts read on origin/main `031210ecb` (Explore, read-only).

# Dependency Markers

- 출처: `TASK-PC-FE-370` (done) — the console's type filter sent `CREDIT`/`DEBIT` and got 422; the console side was fixed there, the backend code recorded as out of scope («계약 변경 필요»).
- 선례: `TASK-MONO-812` — metrics `period` unknown → 400 `VALIDATION_ERROR` via `InvalidQueryParameterException`.

# Goal

A client that sends a filter value the service does not know is told «bad request» (400 `VALIDATION_ERROR`), not «invalid amount» (422 `AMOUNT_INVALID`).

# Scope

## In Scope

- **AC-0 facts (code reading):**
  - `TransactionController.list` (`apps/account-service/.../presentation/controller/TransactionController.java:157-173`) takes `type` / `status` as raw strings; `AccountApplicationService.listTransactions` (`.../application/AccountApplicationService.java:395-398`) parses them with `TransactionType.valueOf(...)` / `TransactionStatus.valueOf(...)` — an unknown value throws a bare `IllegalArgumentException`.
  - `GlobalExceptionHandler.java:149-153` maps every bare IAE to `AMOUNT_INVALID` → 422 (`STATUS_BY_CODE:66`). That arm is deliberate and tested (`GlobalExceptionHandlerTest.java:176-180`, `GlobalExceptionHandlerSharedTailWiringTest.java:133-137`) because `Money.of()` is its dominant source — **it must not change**.
  - The precedent: `InvalidQueryParameterException` (`presentation/advice/InvalidQueryParameterException.java:14-19`, not an IAE) → `GlobalExceptionHandler.java:160-163` → 400 `VALIDATION_ERROR`; `TransactionMetricsController.java:53-58` wraps its parse and throws it.
  - Contract: `account-api.md:220-222` (`?page=&size=&type=&status=`, no allowed values), `:232` Errors = `404 ACCOUNT_NOT_FOUND` only.
  - Console: `features/finance-ops/api/finance-api.ts:94-109` sends only the seven enum values since PC-FE-370 (`types.ts:167-183`); the proxy passes 400 and 422 through alike (`app/api/finance/_proxy.ts:24`) — no console branch on either code.
- **Fix:** parse `type` / `status` in `TransactionController.list` (as the metrics controller does) and throw `InvalidQueryParameterException` on an unknown value; leave the global IAE arm alone. `status` is the same defect on the same line, so it is fixed with `type`.
- **Contract:** `account-api.md` § transactions lists the allowed `type` and `status` values (case-insensitive, as `valueOf(trim().toUpperCase())` is today) and `400 VALIDATION_ERROR` for an unknown one.

## Out of Scope

- The global IAE → 422 mapping and genuine amount errors.

# Acceptance Criteria

- [ ] **AC-1** — contract first: allowed values + `400 VALIDATION_ERROR` in § transactions.
- [ ] **AC-2** — `type=CREDIT` → 400 `VALIDATION_ERROR`; `status=DONE` → 400; red before the change.
- [ ] **AC-3** — control: `type=topup` (lowercase) still 200; a `Money` violation on a POST still 422 `AMOUNT_INVALID` (existing tests stay green).

# Related Specs

- `projects/finance-platform/specs/contracts/http/account-api.md` § GET /api/finance/accounts/{id}/transactions · § Error codes

# Related Contracts

- `GET /api/finance/accounts/{id}/transactions`

# Edge Cases

- `loadAccount` runs before the parse today, so an unknown account with a bad `type` answered 404. Parsing in the controller moves the 400 ahead of the 404 — state the order in the contract.
- Blank `type=` stays «no filter».

# Failure Scenarios

1. The global IAE arm is changed instead — every amount error becomes 400. AC-3.
2. Only `type` is fixed and `status` keeps the 422. AC-2 names both.
