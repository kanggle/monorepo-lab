# Task ID

TASK-PC-FE-357

# Title

이미 받은 데이터로 그리는 막대 다섯 — 이커머스 주문 상태 · 프로모션 발급률 · WMS 입고 검수 · SCM SKU 노드별 수량 · WMS 투영 지연

# Status

done

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
| 5 | WMS 운영 «투영 상태» | 지연 숫자 칸 + «최대 지연: N초» 글자 (`WmsOperationsScreen.tsx` `ProjectionSection`, 구현 전 162-258) | 토픽별 `lagSeconds` 가로 막대 + 반영 · 중복 무시 · 실패 누계 누적 막대(받아 놓고 안 그리던 값) | `projections[]` 의 `lagSeconds` · `lifetimeApplied` · `lifetimeIgnoredDuplicate` · `lifetimeFailed` (전부 — 페이지 아님) |

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

- [x] **AC-0** — 다섯 자리의 실제 데이터 모양 확인(위 file:line 이 지금도 맞는지 · 샘플 고정값 `shared/sample/fixtures/*` 에 값이 있는지 — 익명 방문자 샘플 화면에서도 막대가 보이는가). 어긋나면 이 표를 고친다.
- [x] **AC-1** — 구현 + **사용자에게 보이는 새 문구(범례 · 단위 · 빈 상태 · 접근성 라벨) 목록을 소유자에게 보이고 승인**받은 뒤 머지. 문구가 없는 막대(장식)는 목록에 «문구 없음» 으로.
- [x] **AC-2** — 시험: 비율 · 0 · null · 빈 목록 · 합 불일치 · 최대값 0 · 기존 스위트 통과(`--maxWorkers=4 --minWorkers=2`), `tsc --noEmit` · `next lint` rc=0. 막대 출력이 바뀌는 화면의 e2e(`getByText` 류 · testid) 를 grep 해 깨지는 단언이 없는지 확인.
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

## AC-0 측정 (2026-10-22 UTC)

다섯 자리의 file:line · 데이터 모양을 `origin/main` 기준으로 다시 읽었다. **표는 어긋나지 않았다**(5번에 함수 위치만 보충).

| # | 위치 | 지금도 맞나 | 데이터 모양 | 익명 샘플 고정값(`shared/sample/fixtures/*`) |
|---|---|---|---|---|
| 1 | `EcommerceOverview.tsx:62-81` | 맞음 | `state.orderStatus: {status, count: number\|null, cellStatus}[]`, `ORDER_STATUS_VALUES` 순서(대기 → 확정 → 배송중 → 배송완료 → 취소 → 복구실패)로 상태마다 `listOrders({status,size:1}).totalElements` | 있음 — `ecommerce.ts` 주문 5건(대기 1 · 확정 2 · 배송중 1 · 취소 1 · 배송완료 0 · 복구실패 0). `paginateContent` 의 `totalElements` 는 슬라이스 전 필터 행 수라 `size=1` 이어도 정확 |
| 2 | `PromotionsTable.tsx:90` · `PromotionDetailFields.tsx:63` | 맞음 | `issuedCount`(number) · `maxIssuanceCount`(number, zod `positive()`) | 있음 — `ecommerce.ts:705-736` 3건: 42/100 · 100/100(가득) · 0/50(빈 막대) |
| 3 | `AsnInspectionPanel.tsx:85-134` | 맞음 | `Inspection.totalQtyExpected/Passed/Damaged/Short` 전부 nullable number | 있음 — `wms.ts:717-737` `asn-sample-0002` 만 500/500/0/0(전량 통과 · 막대 가득 초록). 다른 ASN 은 «검수 내역 없음»(404 모사) — 정상 경로. 손상·부족 색은 샘플에서 보이지 않음(단위 시험으로 덮음) |
| 4 | `ScmSkuBreakdown.tsx:115-125` | 맞음 | `nodes[]: {nodeId, quantity?: number, staleness?: string}` (SKU 하나의 전체) | 있음 — `scm.ts:507-526` `SKU-SAMPLE-001` 은 노드 2개(120 FRESH · 75 STALE). SKU-SAMPLE-002/003 은 노드 1개(단일 노드 경로) |
| 5 | `WmsOperationsScreen.tsx` `ProjectionSection` (162-258) | 맞음 | `projections[]` 의 `lagSeconds?` · `lifetimeApplied?` · `lifetimeIgnoredDuplicate?` · `lifetimeFailed?` — zod 스키마(`wms-ops/api/types.ts:287-306`)엔 이미 있었으나 **화면 props 타입(`ProjectionSection`)이 좁아** 안 보이던 값 | 있음 — `wms.ts:884-912` 토픽 2개(lag 1.4 / 0.2 · 반영 12048 / 500 · 중복 17 / 2 · 실패 0 / 0) |

- 다섯 자리 모두 «전체» 또는 «한 건의 내역» 이다 — 20건 페이지로 그리는 자리 없음(Failure Scenario 2 비해당).
- e2e: `tests/e2e/` · `e2e-smoke/` 에서 `order-status` · `promotion-*` · `wms-asn-inspection` · `scm-sku` · `wms-operations` · `projection` grep **0건** → 깨질 단언 없음.
- 기존 단위 시험의 단언(`order-status-PENDING` 텍스트 · 입고 검수 · SKU 표 · 투영 표)은 막대가 숫자 글자를 건드리지 않으므로 그대로 통과(전체 스위트로 확인).

## 새 문구 (소유자 승인 2026-10-22 UTC)

| 위치 | 문구 | 언제 보이나 |
|---|---|---|
| `features/ecommerce-ops/components/OrderStatusBar.tsx:43` | 주문이 없습니다. | 이커머스 개요 «주문 상태» 여섯 건수가 **전부 0** 일 때(빈 막대 대신). 한 칸이라도 점검 필요/권한 없음이면 안 보임 |
| `features/wms-ops/components/WmsOperationsScreen.tsx:202` | 반영 {n} · 중복 무시 {n} · 실패 {n} (예: «반영 12,048 · 중복 무시 17 · 실패 0») | WMS 운영 «프로젝션 상태» 표의 토픽 칸 아래. 세 누계 중 하나라도 응답에 있을 때(셋 다 0 이어도 글자는 보임, 막대만 생략). 막대 색을 읽는 범례를 겸함 |
| 주문 상태 누적 막대 · 상태 색 견본(`OrderStatusBar.tsx` · `EcommerceOverview.tsx` `BarSwatch`) | 문구 없음 | 장식(`aria-hidden`). 범례는 기존 «대기 · 확정 …» 글자 + 색 견본 |
| 프로모션 발급률 막대(`IssuanceBar.tsx` → `PromotionsTable.tsx`, `PromotionDetailFields.tsx`) | 문구 없음 | 장식. 숫자는 기존 «42 / 100» 글자 |
| 입고 검수 누적 막대 + 합격/손상/부족 색 견본(`AsnInspectionPanel.tsx`) | 문구 없음 | 장식. 범례는 기존 «합격 수량 · 손상 수량 · 부족 수량» 글자 + 색 견본 |
| SCM SKU 노드별 수량 막대(`ScmSkuBreakdown.tsx`) | 문구 없음 | 장식. 숫자는 기존 수량 칸 글자 |
| WMS 토픽별 지연 막대(`WmsOperationsScreen.tsx`) | 문구 없음 | 장식. 숫자는 기존 «Lag (초)» 칸 |
| 누계 누적 막대(`WmsOperationsScreen.tsx` `LifetimeTotals`) | 문구 없음 | 장식. 글자는 위 «반영 · 중복 무시 · 실패» 줄 |

`aria-label` · `title` 은 새로 붙이지 않았다(막대는 전부 `aria-hidden`, 숫자는 글자로 남음).

## 구현 (2026-10-22 UTC · Sonnet)

**변경 파일** (`projects/platform-console/apps/console-web/`)

- 신규 `src/shared/ui/InlineBar.tsx` — 공용 막대 원자: `ProportionBar` · `StackedBar` · `BarSwatch` + 순수 계산 `barPercent` · `stackedBase` · `stackedPercents`. 라이브러리 없음 · `'use client'` 없음 · `aria-hidden` · 최소 2% 조각 · 0 나누기 방지. 색은 `StatusTone`(success/progress/warning/danger/neutral)의 솔리드 변형.
- 신규 `src/features/ecommerce-ops/components/OrderStatusBar.tsx` · `IssuanceBar.tsx`.
- 수정 `EcommerceOverview.tsx`(막대 + 색 견본) · `PromotionsTable.tsx` · `PromotionDetailFields.tsx`(발급률 막대).
- 수정 `src/features/wms-ops/components/AsnInspectionPanel.tsx`(누적 막대 + 색 견본, `inspectionSegments` 내보냄) · `WmsOperationsScreen.tsx`(지연 막대 · 누계 막대/글자, props 타입 `ProjectionRow` 로 넓힘).
- 수정 `src/features/scm-ops/components/ScmSkuBreakdown.tsx`(노드별 막대, `maxNodeQuantity`).
- 신규 시험 `tests/unit/inline-bars.test.tsx` (31개: 비율 · 0 · null · 빈 목록 · 합 불일치(미달/초과) · max 0 · degraded · 단일 노드 · lag 없음 · 누계 전부 0).
- `RankingBarChart.tsx` 는 건드리지 않음. 기존 숫자 글자 · testid 전부 유지(막대는 추가만).

**결정**
- 입고 검수: 통과=success · 손상=danger · 부족=warning (인접 두 칸을 구분하려고 손상은 danger). 합 > 예정이면 기준을 합으로 늘리고 `data-overflow="true"`, 합 < 예정이면 트랙 끝이 빈 채로 남음.
- 주문 상태: 한 칸이라도 degraded/forbidden/null 이면 **막대를 그리지 않음**(일부만 그린 분포는 전체처럼 읽힘). 전부 0 이면 «주문이 없습니다.».
- 프로모션: 발급 ≥ 한도면 warning 톤, 한도 ≤ 0 이면 막대 없음, 발급 > 한도는 100% 로 자름.
- SCM: STALE/UNREACHABLE 노드 막대는 warning 톤. S5 경고 위치 불변.
- WMS 지연: 가장 큰 lag = 100%, `lagSeconds` 없는 토픽은 막대 없음.

**검증** (로그: scratchpad `357-*.log`)

- 신규 시험 파일: 31/31 통과.
- 전체 스위트 `npx vitest run --maxWorkers=4 --minWorkers=2`: **rc=0 · 413 파일 · 4993/4993 통과**.
- `npx tsc --noEmit`: rc=0.
- `npx next lint`: rc=0 (기존 경고 1건 `received-invitations/route.ts` `_req` — 이번 변경 아님).
- e2e grep: 위 AC-0 참고, 0건.

## 소유자 승인 · 후속 수정 (2026-10-22 UTC · 분석=Opus 5.5)

- 위 새 문구 두 개 · 색 선택 = **소유자 승인**(선택창, 머지 전).
- 후속 커밋: WMS 토픽별 지연 막대 색 `warning` → `neutral`(`WmsOperationsScreen.tsx`) — 값과 무관하게 경고색이면 정상 지연(1.4 s)도 문제로 읽힌다. `inline-bars.test.tsx` 31/31 · `tsc --noEmit` rc=0.
- AC-1 닫힘(머지 전 승인). 열림: **AC-3**(Vercel Production 기록 · 익명 샘플 화면 다섯 자리).

## CORRECTION (2026-10-22 UTC · close chore · 4차원 확인)

- **AC-3 닫힘** — 위 AC 목록의 AC-3 칸은 review 시점 기록이라 빈 채로 둔다. Vercel Production `7231346845` success. 익명 샘플 화면: 다섯 자리 전부 확인 — 주문 상태 막대 `ecommerce-order-status-bar` · 발급률 막대 `promotion-row-issue-bar-0`(42% 파랑) · `-1`(100% 노랑) · WMS 투영 지연/누계 막대(`wms-operations-projection-lag-bar-0/1` · `-lifetime-bar-0/1`) = 2026-10-21 UTC 서버 렌더 HTML; 입고 검수 막대(`wms-asn-inspection-bar`, 패널 `wms-asn-inspection-1` 을 연 뒤 · 100%) · SKU 노드별 막대(`SKU-SAMPLE-001` → `scm-sku-node-bar-0` 100% · `-1` 62.5%) = 2026-10-22 UTC 헤드리스 브라우저(Playwright · 메인 세션, 익명 샘플 `https://console.hubwang.com`).
- 4차원: (a) #4450 `state=MERGED` · (b) 스쿼시 `d10f95e74` 가 `origin/main` 조상(tip `e35c77765`) · (c) 머지 시점 `statusCheckRollup` 67 · 실패 0 · 대기 0 · (d) AC 원문을 열어 남은 칸 = AC-3 하나 — 위 기록으로 그 동사(«배포 · 확인»)대로 닫힘.
