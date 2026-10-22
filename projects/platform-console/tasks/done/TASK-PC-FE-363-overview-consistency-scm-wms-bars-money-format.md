# Task ID

TASK-PC-FE-363

# Title

도메인 개요 모양 맞추기 — SCM 개요 발주 상태 · WMS 개요 알림 상태를 숫자 나열에서 분포 막대로 · SCM · Finance 개요의 금액 서식을 이커머스와 같게

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (360 의 상태 분포 막대와 같은 모양 · 서버 변경 없음)
> 기안 2026-10-22 UTC — 소유자 질문 «전체개요 · 도메인 개요가 대시보드 느낌인가» 에 대한 캡처 판정(이 세션)의 1단계(소유자 «추천대로 진행»). 2단계(전체 개요를 업무 지표로) 는 측정 뒤 별도 티켓.

# Dependency Markers

- 선행 없음. 선례: `TASK-PC-FE-360`(배송 · 결재 상태 분포 막대 — `ShippingStatusBar` · `ApprovalStatusBar`) · `TASK-PC-FE-357` `shared/ui/InlineBar.tsx`.

# Goal

운영 익명 샘플 화면 캡처(2026-10-22 UTC)에서 도메인 개요끼리 모양이 갈렸다:

| # | 화면 | 지금 | 바꿀 것 |
|---|---|---|---|
| 1 | SCM 개요 «발주 상태» | 9종(초안 · 제출 · 접수 · 확정 · 부분입고 · 입고 · 정산 · 마감 · 취소) 숫자만 나열 | 360 과 같은 **분포 막대** + 색 견본(숫자는 그대로) |
| 2 | WMS 개요 «알림 상태» | 미확인 · 확인 숫자 둘 | 같은 막대(미확인 = 경고 톤) |
| 3 | SCM 개요 «최근 발주» 금액 | `125000.00 KRW` | 이커머스 개요와 같은 서식(예 `₩125,000`) — **글자 서식만** |
| 4 | Finance 개요 «기본계좌 스냅샷» 잔액 | `KRW: 80000 KRW (가용)` | 같은 서식 |

# Scope

## In Scope

- **AC-0 측정** (file:line): ① 1 · 2 의 숫자가 **전체 모집단**인가(상태 필터 `size:1` 의 `totalElements` 인가, 첫 페이지에서 센 것인가 — 첫 페이지면 막대를 그리지 않고 이유를 적는다) · 그 값을 만드는 읽기가 상태를 **실제로 저장**하는가(🔴 선례: WMS admin 읽기 모델은 출고 상태를 셋으로 접었다 — `TASK-PC-FE-362`) ② 상태의 순서 · 색을 어디서 가져올지(기존 배지 톤 · 라벨) ③ 3 · 4 의 금액이 어떤 모양으로 오는가(문자열 · 소수 자리 · 통화) 와 콘솔의 기존 서식 함수(`formatMoney` · `minorToWon` 등)가 F5 규칙(문자열 금액을 `Number` 로 바꾸지 않는다 — `console-web/architecture.md` § Integration Rules)을 지키며 쓸 수 있는가 ④ 샘플 고정값.
- 구현: 360 규칙 그대로(칸 하나라도 비ok → 막대 없음 · 전부 0 → 빈 문구 · 숫자 글자 · 기존 testid 유지) · 금액은 **기존 서식 함수만** — 새 `Number(` 없음(F5 가드 `tests/unit/f5-chart-geometry-exception.test.ts` 초록 유지).
- 시험: 비율 · 하나 저하 · 전부 0 · 금액 서식(소수 · 큰 수 · 통화 다름 · 빈 값).

## Out of Scope

- 전체 개요(`/dashboards/overview`) — 2단계.
- 새 호출 · 서버 변경.

# Acceptance Criteria

- [x] **AC-0** — 위 ①~④. ① 이 «첫 페이지에서 셈» 이거나 상태를 접는다면 그 칸은 막대에서 빼고 이유를 적는다.
- [x] **AC-1** — 구현 + **사용자에게 보이는 새 문구 목록과 금액 서식 변경 전후를 소유자에게 보이고 승인** 받은 뒤 머지.
- [x] **AC-2** — 시험 + 기존 스위트(`--maxWorkers=4 --minWorkers=2`) · `tsc --noEmit` · `next lint` · Client graph 가드 둘(스테이지 뒤) rc=0. SSR 이 바뀌므로 머지 전 브랜치 nightly 콘솔 잡.
- [ ] **AC-3** — 배포(Vercel Production 기록) · 익명 샘플 화면 확인.

# Related Specs

- `projects/platform-console/specs/services/console-web/architecture.md` (F5)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — SCM 개요 · WMS 개요(§ 2.4.5.2) · Finance 개요 절(이름은 AC-0 에서)

# Edge Cases

- 통화가 KRW 가 아닌 금액 → 그 통화의 기호 · 자리수(기존 서식 함수의 규칙).
- 금액 문자열이 파싱 불가 → 원문 그대로(지금처럼).
- 알림 0 건 → 막대 없음 · 빈 문구.

# Failure Scenarios

1. 금액 서식을 위해 `Number(` 를 새로 쓴다 → F5 위반, 가드가 막는다.
2. 상태를 접는 읽기 위에 분포 막대를 그린다 → AC-0 ① 이 막는다.

---

## AC-0 측정 (2026-10-22 UTC)

**① 전체 모집단인가 · 생산자가 상태를 실제로 저장하는가**

- SCM 발주 상태 9칸: `scm-ops/api/overview-state.ts:134-136` — 상태마다 `listPurchaseOrders({ status: s, page: 0, size: 1 })` 를 따로 부르고 `statusCells[i].value?.data.totalElements`(`:168`) 를 쓴다. **첫 페이지에서 센 값이 아니다**(상태 필터 `size:1` 의 `totalElements`).
- 생산자 저장: `PurchaseOrder.java:73-75` 가 `@Enumerated(EnumType.STRING) PoStatus status` 를 aggregate 컬럼으로 저장하고, 조회는 `PurchaseOrderJpaRepository.java:24` `p.status = :status` — **접히지 않는다**(WMS 출고 읽기 모델 같은 3값 접기 없음). DB 제약 `V1__init.sql:62` 도 9값 전부.
- 🔴 **단, `SETTLED` · `CLOSED` 는 지금 쓸 수 있는 경로가 없다**: `PurchaseOrder.java` 의 상태 변경은 `submit/acknowledge/confirm/cancel`(`:277-289`) + 입고 반영(`:325,330`) 뿐이고 `SETTLED`/`CLOSED` 로 가는 메서드가 없다(`PoStatusMachine.java:60-61` 전이표에만 있음). 스펙도 «settlement-service 가 몰아주며 아직 부트스트랩 안 됨»(`scm-platform/specs/services/procurement-service/architecture.md:331-333,389-390`, `scm-procurement-events.md:29`). 결과: 이 두 칸은 **항상 0**. 접힌 것이 아니라 «아직 생길 수 없는 상태» 이므로 막대에 **남긴다**(0 은 조각을 그리지 않아 화면은 같고, 정산 서비스가 붙으면 합이 자동으로 맞는다). 숫자 목록에는 이미 0 으로 보인다.
- WMS 알림 2칸: `wms-ops/api/overview-state.ts:192-193,236,242` — `listAlerts({ acknowledged: false|true, page: 0, size: 1 })` 의 `data.page.totalElements`. 생산자 `AlertLogRepository.java:26-28` 이 `acknowledgedAt IS NULL` / `IS NOT NULL` 로 거르는 **서로 겹치지 않는 완전 분할**(`AlertLogEntity.java:51` 컬럼 저장). 접힘 없음 → 둘 다 막대에 넣는다.
- 결과: **제외하는 칸 없음.**

**② 순서 · 색**: SCM — `PO_STATUS_BUCKETS`(`overview-state.ts:67-77`) 순서 그대로, 색은 기존 `poStatusTone`(`scm-ops-helpers.ts:40-54`: 초안 neutral · 제출 warning · 접수/확정/부분입고 progress · 입고/정산 success · 마감 neutral · 취소 danger), 라벨은 `ScmOverview.tsx` 의 `PO_STATUS_LABELS` 그대로. WMS — 미확인 `warning`(소유자 지시) · 확인 `neutral`(소유자가 정하지 않은 값 — 새 톤은 아니며 바꾸기 쉽다). 색 견본은 360 처럼 `BarSwatch`.

**③ 금액 모양 · 서식 함수**

- SCM 최근 발주: `totalAmount`/`currency` 는 **소수 문자열**(예 `"125000.00"` · `"KRW"` — `scm-ops/api/types.ts:99-100`, 샘플 `shared/sample/fixtures/scm.ts:179`). 지금 화면: `{po.totalAmount}{ currency }` 원문(`ScmOverview.tsx:184-187`).
- Finance 기본계좌 잔액: 정수 **최소단위 문자열** `{amount,currency}` → `formatMoney` → `100000 KRW`(`FinanceOverviewScreen.tsx:256`).
- 이커머스 개요 서식: 인라인 `₩{o.totalPrice.toLocaleString('ko-KR')}`(`EcommerceRecentPanels.tsx:72`) — **`number` 에서만** 쓸 수 있고 문자열에 쓰면 `Number(` 강제 변환이 필요해 F5 위반. 기존 `formatMoney`(`shared/lib/money.ts`)는 F5 를 지키지만 «천 단위 구분 · ₩ 기호» 가 없다.
- 판정: **기존 함수 그대로는 목표 모양을 못 만든다 → 새 `Number(` 없이 문자열 처리만 하는 서식 함수를 `shared/lib/money.ts`(F5 가드 대상 파일) 에 추가**: `formatDecimalDisplay`(소수 문자열) · `formatMoneyDisplay`(최소단위 Money). 천 단위 구분은 정규식 치환(`\B(?=(\d{3})+(?!\d))`), 소수 자리는 문자열 그대로, 0 자리 통화(KRW·JPY)의 **전부 0 인 소수부만** 버린다(반올림 없음). 기존 `formatMoney` 는 호출처 38 파일이 쓰므로 **출력 불변**(공통 분모 `minorUnitsToDecimal` 만 추출). F5 가드 `f5-chart-geometry-exception.test.ts` 는 `shared/lib/money.ts` 를 스캔하고 새 `Number(`/`parseFloat(`/`parseInt(` 0 건 — 초록.
- 통화 규칙(소유자 확인 필요): KRW → `₩` 접두 · 그 밖 통화 → 코드 접미(`1,234.50 USD`) — 이커머스에 ₩ 말고 기준이 없어 다른 기호(`$` 등)는 만들지 않았다.

**④ 샘플 고정값**: SCM `totalAmount` `125000.00`/`100000.00`/`62500.00`/`24000.00`(KRW — `fixtures/scm.ts:179,206,234,259`) → `₩125,000` 등. Finance 샘플 잔액은 `fixtures/finance.ts`(KRW). 샘플 데이터는 바꾸지 않는다(표시만 바뀜).

## 새 문구 (소유자 승인 2026-10-22 KST)

| 파일:줄 | 문구 | 언제 보이나 |
|---|---|---|
| `src/features/scm-ops/components/PoStatusBar.tsx:48` | `발주가 없습니다.` | SCM 개요 «발주 상태» 9칸이 전부 ok 이고 합이 0 일 때(막대 대신) |
| `src/features/wms-ops/components/AlertStatusBar.tsx:56` | `알림이 없습니다.` | WMS 개요 «알림 상태» 두 칸이 전부 ok 이고 합이 0 일 때(막대 대신) |

그 밖의 새 사용자 문구 · aria-label 은 **없다**(막대 · 색 견본은 `aria-hidden`, 숫자 · 라벨 · testid 는 기존 그대로). 정책 질문 2건: ① WMS «확인» 색을 `neutral` 로 한 것 ② 비 KRW 통화의 표기(`1,234.50 USD`).

## 금액 서식 전후

| 화면 | 전 | 후 |
|---|---|---|
| SCM 개요 «최근 발주» (샘플 `125000.00` KRW) | `125000.00 KRW` | `₩125,000` |
| SCM 개요 «최근 발주» (`62500.00` KRW) | `62500.00 KRW` | `₩62,500` |
| SCM 개요 «최근 발주» (소수 있음 `125000.50` KRW) | `125000.50 KRW` | `₩125,000.50` |
| SCM 개요 «최근 발주» (`1234.50` USD) | `1234.50 USD` | `1,234.50 USD` |
| SCM 개요 «최근 발주» (통화 없음 `125000.00`) | `125000.00` | `125,000.00` |
| SCM 개요 «최근 발주» (파싱 불가 `abc`) | `abc` | `abc` (원문) |
| Finance 개요 «기본계좌 스냅샷» (`100000` KRW) | `KRW: 100000 KRW (가용)` | `KRW: ₩100,000 (가용)` |
| Finance 개요 (`123456` USD 최소단위) | `USD: 1234.56 USD (가용)` | `USD: 1,234.56 USD (가용)` |

## 구현 (2026-10-22 UTC · Sonnet)

- 새 파일: `src/features/scm-ops/components/PoStatusBar.tsx` · `src/features/wms-ops/components/AlertStatusBar.tsx`(360 의 `ShippingStatusBar` 쌍둥이 — 칸 하나라도 비ok → 막대 없음 · 전부 0 → 빈 문구) · `tests/unit/overview-consistency-bars-money.test.tsx`(16 건: 비율 · 하나 저하/403 · 전부 0 · 빈 목록 · 톤 순서 · 개요 통합 · 서식 소수/큰 수/통화 다름/빈 값/파싱 불가/음수).
- 수정: `ScmOverview.tsx`(막대 + `BarSwatch` + 금액 서식) · `WmsOverview.tsx`(막대 + `BarSwatch`) · `FinanceOverviewScreen.tsx`(`formatMoneyDisplay`) · `src/shared/lib/money.ts`(함수 2 추가 · `formatMoney` 출력 불변) · `tests/unit/FinanceOverviewScreen.test.tsx`(기대값 `100000 KRW` → `KRW: ₩100,000 (가용)` — 의도된 변경).
- SSR vs 클라이언트: 새 컴포넌트 · 함수 모두 `'use client'` 없음 — 기존처럼 **SSR 서버 컴포넌트** 에서 그려진다. 새 호출 · 서버 변경 없음, 기존 숫자 · testid 유지.
- 결과(최종 코드): vitest 전체 `--maxWorkers=4 --minWorkers=2` 426 파일 / 5192 테스트 통과 rc=0 · `tsc --noEmit` rc=0 · `next lint` rc=0(기존 경고 1) · Client graph 가드 둘 rc=0(console-web hits=0) · INDEX 큐 드리프트 · Task ID 충돌 · Walkthrough 원장 드리프트 rc=0(스테이지 뒤).
- 남음: AC-1(소유자 승인) · AC-2 의 브랜치 nightly 콘솔 잡 · AC-3.

## 소유자 승인 · 머지 전 확인 (2026-10-22 UTC · 분석=Opus 5.5)

- **소유자 승인**(선택창, 2026-10-22 KST, 머지 전): 새 문구 2개 · 금액 서식 전후 표 · 구현자가 정한 셋(비원화 = 코드 접미 `1,234.50 USD` · 알림 막대 미확인 노랑 / 확인 회색 · SCM `SETTLED` · `CLOSED` 칸 유지 — 늘 0 이지만 접힌 상태가 아니라 정산 서비스가 붙기 전이라 생길 수 없는 상태) → AC-1 닫힘.
- 메인 세션이 `money.ts` 의 추가 줄에 `Number(` · `parseFloat` · `parseInt` 0건을 확인.
- `TASK-PC-FE-364` 티켓 머지(#4466) 뒤 rebase — `INDEX.md` ready 칸 충돌 1곳(364 행만 남김). 기준 변화는 티켓 문서뿐(코드 동일).
- 브랜치 nightly run `39760579750`(head `60fd20c1d`) **success** — `Package platform-console e2e boot jars (nightly)` · `Platform Console E2E full-stack (Playwright + docker compose)` 둘 다 success. PR #4467 같은 head 체크 pending 0 · fail 0 → AC-2 닫힘.
- 열림: **AC-3**(Vercel Production 기록 · 익명 샘플 `/scm` · `/wms` · `/finance` 개요).

## CORRECTION (2026-10-22 UTC · close chore · 4차원 확인)

- **AC-3 닫힘** — 위 AC 목록의 AC-3 칸은 review 시점 기록이라 빈 채로 둔다. Vercel Production `7249961645` success. 익명 샘플 화면(`https://console.hubwang.com`, 서버 렌더 HTML, 2026-10-22 UTC): `/scm` — `scm-po-status-bar` · 최근 발주 `₩125,000` · `₩100,000` · `₩62,500` · `₩24,000`; `/wms` — `wms-alert-status-bar`; `/finance` — 기본계좌 `₩80,000`.
- 4차원: (a) #4467 `state=MERGED` · (b) 스쿼시 `96bb5798e` 가 `origin/main` 조상 · (c) 머지 시점 `statusCheckRollup` 실패 0 · 대기 0 · (d) AC 원문의 남은 칸 = AC-3 하나 — 위 기록으로 «배포 · 확인» 대로 닫힘.
