# Task ID

TASK-PC-FE-357

# Title

이미 받은 데이터로 그리는 막대 다섯 — 이커머스 주문 상태 · 프로모션 발급률 · WMS 입고 검수 · SCM SKU 노드별 수량 · WMS 투영 지연

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (기존 CSS 막대 `RankingBarChart` 선례를 따르는 화면 작업 · 서버·BFF 변경 없음)
> 기안 2026-10-22 UTC — 소유자 요청 «콘솔에서 그래프로 더 직관적으로» 조사(이 세션)의 1차 묶음 A. 묶음 B 의 WMS 일별 처리량은 `TASK-PC-FE-358`.

# Dependency Markers

- 선행 없음. 같은 1차 묶음: `TASK-PC-FE-358`(독립 — 파일이 겹치지 않는다).

# Goal

다섯 화면이 숫자를 글자로만 나열해 «어느 쪽이 큰가 · 얼마나 찼나» 를 읽는 데 시간이 걸린다. 화면이 **이미 받는** 데이터로 막대를 더해 한눈에 읽히게 한다. 숫자 글자는 그대로 둔다(막대는 보조 — 접근성 · 기존 시험 보존).

| # | 화면 | 지금 | 더하는 것 | 데이터 (이미 있음) |
|---|---|---|---|---|
| 1 | 이커머스 개요 «주문 상태» | 숫자 6개 `<dl>` (`features/ecommerce-ops/components/EcommerceOverview.tsx:62-81`) | 상태 6종을 흐름 순서로 잇는 **가로 누적 막대 1줄** + 범례(상태 · 건수) | `orderStatus[]` `{status,count}` — 상태마다 `size=1` 의 `totalElements`(전체 모집단) |
| 2 | 프로모션 목록 · 상세 | `{issuedCount} / {maxIssuanceCount}` 글자 (`PromotionsTable.tsx:90` · `PromotionDetailFields.tsx:63`) | 글자 옆 **발급률 진행 막대** | `issuedCount`(≥0) · `maxIssuanceCount`(>0, zod `positive()`) — 행마다 |
| 3 | WMS 입고 검수 패널 | `<dl>` 8칸 (`AsnInspectionPanel.tsx:85-134`) | 예정 수량을 **통과 · 파손 · 부족** 으로 나눈 누적 막대 | `totalQtyExpected` · `totalQtyPassed` · `totalQtyDamaged` · `totalQtyShort` (모두 nullable) |
| 4 | SCM 재고 «SKU 노드별» | 표 (`ScmSkuBreakdown.tsx:115-125`) | 노드별 `quantity` **가로 막대**(최대값 기준) | SKU 하나의 `nodes[]` 전부(전체 분포) |
| 5 | WMS 운영 «투영 상태» | 지연 숫자 칸 + «최대 지연: N초» 글자 (`WmsOperationsScreen.tsx`) | 토픽별 `lagSeconds` 가로 막대 + 반영 · 중복 무시 · 실패 누계 누적 막대(받아 놓고 안 그리던 값) | `projections[]` 의 `lagSeconds` · `lifetimeApplied` · `lifetimeIgnoredDuplicate` · `lifetimeFailed` (전부 — 페이지 아님) |

# Scope

## In Scope

- 공용 막대 원자 한 곳(`shared/ui/` — 이름은 구현자: 예 `ProportionBar` · `StackedBar`). `RankingBarChart.tsx` 와 같은 원칙: **차트 라이브러리 없음 · CSS width % · 서버 컴포넌트에서도 쓸 수 있게(`'use client'` 없음)** · 막대는 `aria-hidden`, 숫자는 글자로 남는다 · 0 나누기 방지 · 0 이 아닌 값은 최소 폭(선례 2%).
- 위 다섯 자리에 적용. `RankingBarChart` 는 바꾸지 않는다(원하면 원자를 쓰게 정리 — 출력 바이트 동일일 때만).
- 색: 상태 색은 기존 `StatusBadge` · `poStatusTone` 계열 토큰을 쓴다(새 색 체계 만들지 않음). 실패 · 부족 · 파손 = 경고 톤.
- 시험(단위): 각 자리의 막대 폭 비율 · 0 / null / 빈 목록 · 합이 예정과 다른 경우 · 기존 시험 비회귀.

## Out of Scope

- 새 API 호출 · BFF · 서버 변경 (묶음 B — 358 · 이후 티켓).
- 금액 · 환율 그래프(콘솔 규칙상 문자열 금액을 숫자로 바꾸지 않는다 — 별도 결정).
- 20건 한 페이지로 전체 분포처럼 보이는 그래프(목록 화면의 상태 분포 등).

# Acceptance Criteria

- [ ] **AC-0** — 다섯 자리의 실제 데이터 모양 확인(위 file:line 이 지금도 맞는지 · 샘플 고정값 `shared/sample/fixtures/*` 에 값이 있는지 — 익명 방문자 샘플 화면에서도 막대가 보이는가). 어긋나면 이 표를 고친다.
- [ ] **AC-1** — 구현 + **사용자에게 보이는 새 문구(범례 · 단위 · 빈 상태 · 접근성 라벨) 목록을 소유자에게 보이고 승인**받은 뒤 머지. 문구가 없는 막대(장식)는 목록에 «문구 없음» 으로.
- [ ] **AC-2** — 시험: 비율 · 0 · null · 빈 목록 · 합 불일치 · 최대값 0 · 기존 스위트 통과(`--maxWorkers=4 --minWorkers=2`), `tsc --noEmit` · `next lint` rc=0. 막대 출력이 바뀌는 화면의 e2e(`getByText` 류 · testid) 를 grep 해 깨지는 단언이 없는지 확인.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면에서 다섯 자리 확인(데모 창 불필요 — 샘플로 보인다. 샘플에 값이 없는 자리는 «로그인 운영자 · 다음 데모 창» 으로 남긴다).

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` (화면 데이터 — 변경 없음, 읽기만)

# Edge Cases

- 3: `totalQtyExpected` 가 null/0 → 막대 대신 기존 칸만. 통과+파손+부족 ≠ 예정(초과 · 미달) → 막대의 기준은 **max(예정, 합)** 로 하고 넘친 만큼을 숨기지 않는다(구현자가 표시 방식을 정하되 숫자는 그대로 보인다).
- 2: `issuedCount > maxIssuanceCount`(서버가 막아야 하지만) → 100% 로 자르고 숫자는 원문.
- 1: 주문 상태 셀이 degrade(`forbidden`/`degraded`)면 막대 없이 기존 안내. 전부 0 → 빈 막대 대신 «주문이 없습니다» 류(문구는 AC-1).
- 5: `lagSeconds` 없음(optional) → 그 행은 막대 없음. 누계 셋 모두 0 → 막대 없음.
- 4: 노드 1개 → 100% 막대 하나(정상). 응답의 S5 경고(«발주 결정용 아님») 는 막대 위에서도 계속 보인다.

# Failure Scenarios

1. 막대만 남기고 숫자를 지운다 → 스크린리더 · 기존 시험이 깨진다. 숫자 글자는 지우지 않는다.
2. 20건 페이지로 전체 분포처럼 그린다 → 범위 밖. 이 티켓의 다섯 자리는 모두 «전체» 또는 «한 건의 내역» 이다(AC-0 이 다시 확인).
3. 새 문구를 승인 없이 머지 → AC-1.
