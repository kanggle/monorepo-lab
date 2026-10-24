# Task ID

TASK-PC-FE-370

# Title

The Finance transactions type filter offers `CREDIT` / `DEBIT`, which the backend does not know — picking one answers 422 `AMOUNT_INVALID`

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- bug
- finance

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (one option list split into «what we can render» vs «what we may filter by»)
> Drafted 2026-10-24 UTC. Found by `TASK-MONO-812`'s implementation and confirmed by reading the code; not reproduced live.

# Dependency Markers

- 출처: `TASK-MONO-812` (Finance overview metrics), § AC-5 «발견, 미수정».

# Goal

The type filter offers only values the backend accepts, so no choice in the dropdown produces an error.

# Scope

## In Scope

- **AC-0 re-measure:**
  - `features/finance-ops/components/TransactionsTable.tsx:115` builds the filter options from `KNOWN_TXN_TYPES`. Since TASK-MONO-812, `features/finance-ops/api/types.ts:154-164` has 9 values, including `CREDIT` and `DEBIT`. Those two are not account-service `TransactionType` values (`TOPUP · WITHDRAW · TRANSFER · HOLD · CAPTURE · RELEASE · REVERSAL`).
  - Backend: `AccountApplicationService.java:396` calls `TransactionType.valueOf(type.trim().toUpperCase())`. The resulting `IllegalArgumentException` is answered **422 `AMOUNT_INVALID`** (`GlobalExceptionHandler.java:66`). The status and the code are both wrong for a filter value.
  - Does any producer or fixture still emit `CREDIT` / `DEBIT`? `types.ts:152` says they stay «so nothing that already shows them changes».
- **Fix:** split the list into the values the table can label and the values the filter offers, which are the producer enum only. Decide in AC-0 whether `WITHDRAW` / `REVERSAL` belong in the filter. They have no v1 write path (TASK-MONO-812 AC-0), but filtering by them is harmless.
- Optional, if small: the backend answering 400 rather than 422 for an unknown `type`. That belongs to finance and needs a contract line. Record it rather than doing it if it grows.

## Out of Scope

- Renaming or adding transaction types.

# Acceptance Criteria

- [ ] **AC-0** — the above, file:line.
- [ ] **AC-1** — every filter option, sent to the backend contract, is accepted. A test enumerates the options against the producer enum, so a future drift turns red.
- [ ] **AC-2** — rows whose type is outside the producer enum still render with the generic «unknown» label. Control group: the table's rendering is unchanged.

# Related Specs

- `projects/finance-platform/specs/contracts/http/account-api.md` § transactions list (`type` filter)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § Finance

# Related Contracts

- `GET /api/finance/accounts/{id}/transactions?type=`

# Edge Cases

- The backend upper-cases the value. A lower-case option would also pass; keep options upper-case like the enum.

# Failure Scenarios

1. `CREDIT` / `DEBIT` are removed from the shared list and a fixture still renders them → «CREDIT (unknown)» shows up. AC-2 guards this.
