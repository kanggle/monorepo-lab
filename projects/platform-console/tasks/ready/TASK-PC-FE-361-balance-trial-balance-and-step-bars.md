# Task ID

TASK-PC-FE-361

# Title

그래프 3차 묶음 — 재무 계좌 통화별 «사용 가능 + 보류» 막대 · 원장 시산표 계정별 차변 · 대변 막대 · ERP 결재 목록 행별 단계 진행 · WMS 출고 상세 진행 단계

# Status

ready

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

- [ ] **AC-0** — 위 ①~⑤.
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
