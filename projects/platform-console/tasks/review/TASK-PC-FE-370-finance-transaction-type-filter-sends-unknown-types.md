# Task ID

TASK-PC-FE-370

# Title

The Finance transactions type filter offers `CREDIT` / `DEBIT`, which the backend does not know — picking one answers 422 `AMOUNT_INVALID`

# Status

review

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

- [x] **AC-0** — the above, file:line.
  - → **닫힘 2026-10-24 UTC** (origin/main `540dccd88`).
  - **결함 확인:** 필터 `<select>`(`TransactionsTable.tsx:115`)의 옵션은 `KNOWN_TXN_TYPES` 9개였다. 백엔드 `TransactionType` 은 7개(`TOPUP · WITHDRAW · TRANSFER · HOLD · CAPTURE · RELEASE · REVERSAL`)다.
  - **`CREDIT` / `DEBIT` 을 쓰는 곳:** 콘솔 코드 · 테스트에서 거래 유형으로 쓰는 곳은 `types.ts` 목록뿐이다. 저장소의 다른 `DEBIT`/`CREDIT` 은 ledger 분개 **방향**이라 다른 개념이다. → 표시용으로도 쓰이지 않는 값이다.
  - **`WITHDRAW` · `REVERSAL`:** 필터에 넣었다. v1 에 쓰기 경로는 없지만 백엔드가 받는 값이라 무해하다.
  - 백엔드의 400 vs 422 문제는 하지 않았다. finance 계약 변경이 필요하므로 기록만 한다.
- [x] **AC-1** — every filter option, sent to the backend contract, is accepted. A test enumerates the options against the producer enum, so a future drift turns red.
  - → 필터용 목록 `TXN_TYPE_FILTER_OPTIONS`(`types.ts`)를 새로 만들었다. 생산자 enum 7개, 선언 순서 그대로다. 드롭다운은 이 목록을 쓴다.
  - `finance-txn-type-filter.test.tsx`:
    - **Java 원본(`TransactionType.java`)을 읽어** enum 을 꺼내고, 목록과 렌더된 `<option>` 이 그것과 정확히 같은지 단언한다. enum 이 바뀌면 빨강이 된다.
    - 비어 있으면 실패하는 하한(≥5 · `TOPUP` 포함)을 둬서 «0개끼리 같음» 을 막는다.
  - 🔴 **무는가:** 드롭다운을 옛 `KNOWN_TXN_TYPES` 로 잠깐 되돌리면 그 시험 하나가 빨강이다(rc=1, 1 failed / 3 passed). 복구 뒤 4/4 이다.
- [x] **AC-2** — rows whose type is outside the producer enum still render with the generic «unknown» label. Control group: the table's rendering is unchanged.
  - → 대조군: 표시용 목록 `KNOWN_TXN_TYPES` 는 바이트 그대로다(시험이 9개를 고정한다). 아는 유형은 그대로 그 이름으로 그려지고, 모르는 값은 여전히 generic 라벨이다.
  - **검증:** console-web `tsc` rc=0 · `pnpm lint` rc=0 · `pnpm test` 447 파일 5,540 통과.

# Related Specs

- `projects/finance-platform/specs/contracts/http/account-api.md` § transactions list (`type` filter)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § Finance

# Related Contracts

- `GET /api/finance/accounts/{id}/transactions?type=`

# Edge Cases

- The backend upper-cases the value. A lower-case option would also pass; keep options upper-case like the enum.

# Failure Scenarios

1. `CREDIT` / `DEBIT` are removed from the shared list and a fixture still renders them → «CREDIT (unknown)» shows up. AC-2 guards this.
