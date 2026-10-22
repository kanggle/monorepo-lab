# Task ID

TASK-PC-FE-361

# Title

그래프 3차 묶음 — 재무 계좌 통화별 «사용 가능 + 보류» 막대 · 원장 시산표 계정별 차변 · 대변 막대 · ERP 결재 목록 행별 단계 진행 · WMS 출고 상세 진행 단계

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (357 · 359 의 원자와 F5 예외 함수를 그대로 쓴다 · 서버/BFF 변경 없음)
> 기안 2026-10-21 UTC — 콘솔 그래프 조사의 남은 후보 중 «효과 중» 넷(소유자 «진행», 2026-10-22 KST). 1차 = `TASK-PC-FE-357` · `358`, 2차 = `359` · `360`(모두 review).

# Dependency Markers

- 선행 없음. 재사용: `shared/ui/InlineBar.tsx`(357) · `shared/lib/chart-scale.ts` `chartMagnitude`(359 — F5 의 유일한 예외, `console-web/architecture.md` § Integration Rules) · 가드 `tests/unit/f5-chart-geometry-exception.test.ts`.

# Goal

| # | 화면 | 지금 | 더하는 것 | 데이터 (이미 있음) |
|---|---|---|---|---|
| 1 | `/finance/accounts` 잔액 | 통화별 `{ledger, available, held}` 문자열 표(`features/finance-ops/components/BalancesTable.tsx`) | 통화마다 **사용 가능 · 보류 누적 막대**(기준 = 원장 잔액) | 통화별 한 행 = 전체(계좌 하나의 전 통화). 금액 = 문자열 ⇒ 막대 폭만 `chartMagnitude` |
| 2 | `/ledger` 시산표 | 계정별 차변 · 대변 합계 표(`features/ledger-ops/components/TrialBalanceTable.tsx`) | 계정마다 **차변 · 대변 막대 한 쌍**(기준 = 표 안 최대값) | 계정 목록 전체(페이지 아님). 금액 = 문자열 ⇒ `chartMagnitude` |
| 3 | `/erp/approval` 목록 | 상태 배지 | 행마다 **«현재 단계 / 전체 단계» 진행 표시** | 각 행의 `currentStage` · `totalStages`(`ApprovalSummary`) |
| 4 | `/wms/outbound` 주문 상세 | `<dl>` 네 칸(`features/wms-outbound-ops/components/OutboundDrillSummary.tsx`) | 그 주문의 **진행 단계 표시**(계약의 정상 흐름 순서 위에 현재 위치) | 그 주문의 `status` · `sagaState` |

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 1·2 의 금액 필드 이름과 모양(통화별 · 기준 통화 `base*` 를 쓸지 원 통화를 쓸지 — 시산표는 계정마다 통화가 다를 수 있다 ⇒ **한 막대 묶음 안에서는 같은 통화만 비교**하는 기준을 정해 적는다) ② 음수 잔액 · 보류 > 원장 같은 모양이 계약상 가능한가 ③ 결재 `currentStage` 가 0 기반인가 1 기반인가 · 종결 상태(APPROVED · REJECTED · WITHDRAWN)에서 무엇인가 ④ 출고 상태 · 사가 상태의 **정상 흐름 순서와 곁가지**(CANCELLED · BACKORDERED 등)를 계약에서 — 화면에 쓸 순서를 계약에서 가져오고 손으로 만들지 않는다 ⑤ 샘플 고정값에 네 자리 값이 있는가.
- 구현: `InlineBar` 재사용 · 1·2 의 폭 계산은 **`chartMagnitude` 만**(그 결과를 글자 · 합계 · 비교 판정에 쓰지 않는다 — 금액 글자는 원문 그대로) · 3·4 는 숫자 필드라 예외 불필요 · 막대 · 단계 표시는 `aria-hidden`, 글자는 그대로 · 기존 testid 유지.
- 4 의 곁가지(취소 · 백오더 등)는 정상 흐름 위에 억지로 놓지 않는다 — 그 상태를 그대로 표시하고 진행 막대는 그리지 않는다(AC-0 이 정한 규칙대로).
- 시험: 비율 · 0 · 파싱 실패 금액 · 통화 섞임(묶음 분리) · 음수 · 단계 0/1 기반 경계 · 종결 상태 · 곁가지 상태 · 알 수 없는 상태 · F5 가드가 여전히 초록(새 파일에 `Number(` 없음).

## Out of Scope

- 서버 · BFF · 새 호출.
- 금액 합계 · 비교를 숫자로 하는 것(F5 예외는 기하만).

# Acceptance Criteria

- [x] **AC-0** — 위 ①~⑤. (2026-10-21 UTC, 아래 `## AC-0 측정`)
- [ ] **AC-1** — 구현 + **사용자에게 보이는 새 문구 목록을 소유자에게 보이고 승인** 받은 뒤 머지.
- [ ] **AC-2** — 시험 위 목록 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · `node scripts/check-client-graph-server-only.mjs` · `check-client-graph-backend-origins.mjs`(스테이지 뒤) rc=0. SSR 이 바뀌면 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인(클라이언트 패널은 브라우저 · 소유자).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (F5 · 그래프 기하 예외)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.7 · § 2.4.7.1 (finance · ledger) · § 2.4.5.1 (wms outbound — 이름은 AC-0 에서 확인) · ERP 결재 절
- `projects/erp-platform/specs/contracts/http/approval-api.md` · wms outbound 계약(읽기만)

# Edge Cases

- 원장 잔액 0 인 통화 → 막대 없음 · 보류 > 원장(계약상 불가여도) → 기준을 max(원장, 사용 가능 + 보류) 로.
- 시산표 차변 = 대변(균형) → 같은 길이 · 계정 하나 → 100%.
- 결재 `totalStages` 0 · null → 진행 표시 없음.
- 출고 상태가 목록에 없는 값 → 상태 글자만.

# Failure Scenarios

1. `chartMagnitude` 결과가 «균형 맞음» 같은 판정에 쓰인다 → F5 예외 위반, 시험이 막는다(판정은 기존 문자열 비교 그대로).
2. 통화가 다른 계정을 한 기준으로 그린다 → 묶음 분리 시험.
3. 출고 단계 순서를 손으로 만들어 계약과 어긋난다 → AC-0 ④ 의 계약 인용 + 시험.

---

## AC-0 측정 (2026-10-21 UTC)

모든 경로는 `projects/platform-console/apps/console-web/src/` 기준(별도 표기 없으면).

**① 금액 필드와 통화 묶음 규칙**
- 재무 잔액: `shared/api/finance-accounts-types.ts:48-59` `BalanceSchema` = `{ currency(3자), ledger, available, held }` 세 금액 모두 정수 minor-units **문자열**(`/^-?\d+$/`). 기준 통화 필드(`base*`)는 없다. 표는 통화마다 한 행(`features/finance-ops/components/BalancesTable.tsx` 렌더 루프). → 한 행 = 한 통화이므로 **행마다 자기 막대**, 서로 다른 통화는 한 눈금에서 비교하지 않는다.
- 시산표: `shared/api/ledger-types/trial-balance.ts:12-19` 계정 행 = `{ ledgerAccountCode, debitTotal, creditTotal, baseDebitTotal, baseCreditTotal }`, 모두 `Money{amount:문자열, currency}`. 원 통화 합계(`debitTotal`/`creditTotal`)와 기준 통화(KRW) 합계가 따로 있다.
- **결정한 규칙**: 막대는 **원 통화** 합계(`debitTotal`/`creditTotal`)로 그린다. 묶음 = `debitTotal.currency`. 같은 통화 계정끼리 그 통화의 «차변 · 대변 중 가장 큰 값» 한 눈금을 공유한다. 한 계정의 차변 · 대변 통화가 다르거나 비어 있으면 그 계정만 막대 없음(남의 눈금에 그리지 않음). 통화가 둘 이상 그려지면 표 아래 안내 문장이 «같은 통화 계정끼리» 라고 밝힌다. 구현: `features/ledger-ops/lib/trial-balance-bars.ts`. (`base*` 열은 모두 KRW 라 서로 비교 가능하지만 티켓이 «계정별 차변 · 대변» 이라 원 통화 열에 대응시켰다.)
- 폭 계산은 `shared/lib/chart-scale.ts:27` `chartMagnitude` 만(1 · 2). 그 결과는 막대 폭에만 쓰고 글자는 `formatMoney` 원문 그대로, 균형 판정은 `tb.inBalance`(제공자 boolean) 그대로.

**② 음수 · 보류 > 원장 가능성**
- `BalanceSchema` 정규식 `-?\d+` 는 음수를 허용한다(`finance-accounts-types.ts:54-58`), 보류 > 원장을 막는 스키마 제약은 없다. → 음수 · 파싱 실패는 **0 으로 취급**(글자는 그대로 보임), 가용 + 보류 > 원장이면 눈금을 `max(원장, 가용 + 보류)` 로(`StackedBar` 의 `stackedBase`, `data-overflow="true"` 표시). 시산표 금액 스키마도 `-?\d+` 라 같은 처리.

**③ 결재 단계**
- `projects/erp-platform/specs/contracts/http/approval-api.md:31-35`: `currentStage` = «0-based index of the pending stage; **ABSENT once finalized**», `totalStages` 별도. 콘솔 `features/erp-ops/api/approval-types.ts:171-175, 205-208`(optional int) 와 상세 `ApprovalDetail.tsx:134-135` 가 이미 `currentStage + 1` / `totalStages` 로 쓴다.
- 종결 상태에서 `currentStage` 는 없다 → 종결 3종 중 `APPROVED` 만 «모든 단계 완료» 로 그리고(글자 없음, 배지가 말함), `REJECTED` · `WITHDRAWN` 은 어느 단계에서 멈췄는지 요약에 없으므로 **그리지 않는다**. `DRAFT` 도 안 그림. `SUBMITTED` · `IN_REVIEW` 만 `n/N 단계` + 막대.
- 범위 밖 숫자(`currentStage >= totalStages`, 음수, 소수, `totalStages` 0 · 없음) → 아무것도 안 그림(1 기반으로 잘못 읽은 값이 위치처럼 보이지 않게).

**④ 출고 정상 흐름 순서와 곁가지 (계약에서)**
- `projects/wms-platform/specs/services/outbound-service/state-machines/order-status.md` § States(`:27-36`) · § Transitions(`:42-91`, mermaid `:96-111`): 정상 흐름 `RECEIVED →(같은 TX) PICKING → PICKED → PACKING → PACKED → SHIPPED`. `RECEIVED` 는 `:29` «callers never observe an Order resting in RECEIVED» 이므로 쉬는 단계가 아니다. 곁가지(터미널) = `CANCELLED`(`:35`, PICKING~PACKED 에서 cancel) · `BACKORDERED`(`:36`, `:135` RECEIVED/PICKING 에서 reserve 실패). 목록 필터 enum `PICKING|PICKED|PACKING|PACKED|SHIPPED|CANCELLED|BACKORDERED` 와 일치(`projects/wms-platform/specs/contracts/http/outbound-service-api.md:306`, 콘솔 `STATUS_FILTER_OPTIONS`).
- 사가 상태(`outbound-service-api.md:843-845`, `saga-status.md:24-35`)는 `order-status.md:19-21` 대로 **별도 기계**(주문과 lock-step 이지만 독립). 위치는 **주문 `status` 로만** 정하고 사가는 기존 글자(`outbound-drill-saga`)로 둔다. 사가 순서를 단계로 쓰면 `CANCELLATION_REQUESTED` · `STUCK_RECOVERY_FAILED` 같은 곁가지가 흐름 위에 억지로 놓인다.
- 구현 규칙: 순서는 `OUTBOUND_FLOW` 상수(위 계약 순서, 주석에 출처)이고 시험이 계약 순서와 같음을 고정. `SHIPPED` = 전 단계 완료. `CANCELLED` · `BACKORDERED` = 막대 없이 «정상 흐름 밖 상태: …» 글자. 알 수 없는 · 없는 · `RECEIVED` = 추가 표시 없음(기존 배지 글자만).

**⑤ 샘플 고정값의 네 자리 값**
- `shared/sample/fixtures/finance.ts:86` 잔액 `ledger '100000' / available '80000' / held '20000'` — 다섯 자리 이상. 샘플 원장 `fixtures/ledger.ts:146-196` 금액도 `500000` · `50000` · `10000`(USD scale 2) · `130000`. 네 자리 값은 없다. 결재 샘플 `fixtures/erp.ts:757-758` `currentStage: 1, totalStages: 2`(0 기반이라 «2/2 단계»). 출고 샘플은 기존 `status` 문자열을 그대로 쓴다.

## 새 문구 (소유자 승인 대기)

| file:line | 문구 | 언제 보이나 |
|---|---|---|
| `features/finance-ops/components/BalancesTable.tsx:64-66` | 열 머리 «가용 보류» (색 견본 둘 + 글자 «가용» · «보류») | 잔액 표가 있을 때 항상(막대 열 머리) |
| `features/finance-ops/components/BalancesTable.tsx:121` | 막대는 통화별 장부 잔액 대비 가용 · 보류 비율입니다. | 막대가 하나라도 그려질 때 표 아래 |
| `features/ledger-ops/components/TrialBalanceTable.tsx:96-98` | 열 머리 «차변 대변» (색 견본 둘 + 글자 «차변» · «대변») | 시산표 계정이 있을 때 항상(막대 열 머리) |
| `features/ledger-ops/components/TrialBalanceTable.tsx:185` | 막대는 같은 통화 계정끼리, 그 통화에서 가장 큰 차변 · 대변 합계 대비 길이입니다. | 막대가 둘 이상 통화에 걸쳐 그려질 때 표 아래 |
| `features/ledger-ops/components/TrialBalanceTable.tsx:186` | 막대는 이 시산표에서 가장 큰 차변 · 대변 합계 대비 길이입니다. | 막대가 한 통화로만 그려질 때 표 아래 |
| `features/erp-ops/lib/approval-steps.ts:38` (표시 `components/ApprovalStepProgressView.tsx`) | `{현재}/{전체} 단계` (예: 2/3 단계) | 결재 목록 행이 상신됨 · 검토중 이고 단계 숫자가 유효할 때 상태 배지 아래 |
| `features/wms-outbound-ops/components/OutboundDrillSummary.tsx:49` | 진행 단계 | 출고 상세에서 주문 상태가 정상 흐름(PICKING~SHIPPED)일 때 단계 막대 위 |
| `features/wms-outbound-ops/components/OutboundDrillSummary.tsx:61` | 정상 흐름 밖 상태: {CANCELLED 또는 BACKORDERED} | 출고 상세에서 주문 상태가 취소 · 백오더일 때(막대 없음) |

- aria-label · title · sr-only 로 새로 넣은 문구는 **없다**(막대 · 단계 막대는 `aria-hidden`, 위치는 위 글자로 남음). 단계 막대 아래 단계 이름(`PICKING` …)은 기존 상태 enum 원문 그대로(새 한국어 아님).
- 기존 문구 · testid 는 하나도 바꾸지 않았다. 새 testid: `finance-balance-bar-{i}` · `finance-balance-bar-head` · `finance-balance-bar-scope` · `ledger-tb-bar-{i}` · `ledger-tb-bar-debit-{i}` · `ledger-tb-bar-credit-{i}` · `ledger-tb-bar-head` · `ledger-tb-bar-scope` · `approval-steps-{i}` · `approval-steps-bar-{i}` · `approval-steps-text-{i}` · `outbound-drill-progress` · `outbound-drill-progress-bar` · `outbound-drill-progress-side`.

## 구현 (2026-10-21 UTC · Sonnet)

- 공유 원자: `shared/ui/InlineBar.tsx` 에 `StepState` · `stepStates()` · `StepBar` **추가만**(기존 export 변경 0 — 하위 호환).
- 1 재무: `features/finance-ops/lib/balance-bars.ts`(신규, `chartMagnitude` 만) + `BalancesTable.tsx`(5번째 열 `StackedBar` · 안내 문장).
- 2 시산표: `features/ledger-ops/lib/trial-balance-bars.ts`(신규, 통화 묶음) + `TrialBalanceTable.tsx`(6번째 열 `ProportionBar` 한 쌍 · 합계 행 빈 칸 · 안내 문장).
- 3 결재: `features/erp-ops/lib/approval-steps.ts` · `components/ApprovalStepProgressView.tsx`(신규) + `ApprovalScreen.tsx`(상태 칸). 인박스 목록은 그대로.
- 4 출고: `outbound-ops-helpers.ts`(`OUTBOUND_FLOW` · `OUTBOUND_SIDE_BRANCHES` · `outboundProgress`) + `OutboundDrillSummary.tsx`.
- 시험: `tests/unit/graph-batch-3-bars.test.tsx` 39건(단계 원자 · 비율 · 0 · 파싱 실패 · 음수 · 통화 섞임 묶음 분리 · 0/1 기반 경계 · 종결 · 곁가지 · 알 수 없는 상태 · F5 새 파일 `Number(` 없음). 기존 `f5-chart-geometry-exception.test.ts` 가 새 `lib/` 파일도 훑어 초록.
- 결과: 전체 vitest `--maxWorkers=4 --minWorkers=2` 423 파일 · 5155 통과 rc=0 · `tsc --noEmit` rc=0 · `next lint` rc=0(기존 경고 1건 `received-invitations/route.ts` 무관).
- SSR 여부: 네 화면 모두 `export const dynamic = 'force-dynamic'` 서버 페이지 안의 **클라이언트 컴포넌트**다. 재무 잔액(`/finance/accounts`, `initialBalances` 시드), 시산표(`/ledger`, `page.tsx:173` `trialBalance={state.trialBalance}` 시드), 결재 목록(`/erp/approval`, `initialApprovalRequests` 시드)은 첫 페인트 HTML 에 새 막대가 서버 렌더로 들어간다(필터 · 후속 조회는 클라이언트). 출고 상세(`/wms/outbound` 주문 드릴)는 행을 눌러 클라이언트에서 불러오는 패널이라 클라이언트 전용. → SSR 이 바뀌므로 AC-2 의 브랜치 nightly 콘솔 잡 필요(본 세션에서 안 돌림).
