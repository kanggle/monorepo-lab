# Task ID

TASK-PC-FE-380

# Title

익명 샘플의 «지난 주» 구간(9/7~8)이 비어 있다 — 운영자 개요 카드 막대 3개(WMS · 재무 · 이커머스)의 «지난 주» 가 늘 0

# Status

review

# Owner

frontend

# Task Tags

- code
- console
- sample

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (픽스처 행 + 손으로 다시 센 고정 테스트 상수 — 코드 경로 불변)
> Drafted 2026-10-27 UTC. 출처: `TASK-PC-FE-379` 운영 실측 관찰(«샘플 지난 주 대부분 0») → 소유자 결정 2026-10-27: 해결안대로 진행(아래 Scope 3).

# Dependency Markers

- 선행: `TASK-PC-FE-379`(done, #4585) — 막대가 생겨 빈 «지난 주» 가 눈에 띄게 됐다. `TASK-PC-FE-374`·`378`(샘플 행 밀도) 의 규칙(손으로 센 상수, KST 경계, 잔액 상쇄)을 그대로 따른다.

# Goal

로그인 없이 보는 운영자 통합 개요(`/dashboards/overview`)의 WMS(출고 수량) · Finance(이체 금액) · ecommerce(매출) 카드가 «지난 주» 막대에 0 아닌 값을 보인다. 각 도메인 개요의 «실적» 스트립 «직전» 값도 같은 행에서 나온다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 확인, 2026-10-27 UTC):**
  - 창: `shared/sample/fixtures/metrics-windows.ts:29` 주간 이전 구간 = `2026-09-07T00:00+09:00 ~ 2026-09-08T20:00+09:00`, `:34` 월간 이전 구간 = `2026-08-01 ~ 08-15 20:00 KST`. 🔴 주간 이전 구간은 **이번 달 구간(9/1~15) 안**이라 행을 넣으면 그 도메인의 **월간 합계·시계열·랭킹 상수도 바뀐다**.
  - 샘플 카드 headline 은 도메인 픽스처의 `…/metrics?period=week` 응답에서 나온다(`shared/sample/fixtures/dashboards.ts:123-170` `SAMPLE_HEADLINES`) — 도메인 픽스처만 바꾸면 카드가 따라온다.
  - 운영 실측(379 AC-6): wms `qtyShipped` 이번 주 100% / 지난 주 0% · finance `transferAmount:KRW` 100/0 · ecommerce `revenue` 100/0. iam · scm · erp 는 이미 지난 주 값이 있다.
  - 충돌 테스트: `tests/unit/sample-wms-metrics.test.ts:146` «the previous window (same length, a period earlier) holds no sample movement → "0", not missing» — 주간·월간 둘 다 `['0','0','0']` 고정. 지키려는 성질 = «이전 구간 값이 없으면 키가 빠지지 않고 `"0"`». ecommerce `sample-ecommerce-metrics.test.ts:51-53,68-70` 의 `previous: '0'` 은 정확 일치 단언의 부수값.
- **1.** 9/7~8 KST(주간 이전 구간 안, 9/8 20:00 KST 이전)에 행을 더한다: wms — SHIPPED 출고 1건 이상(출고 수량이 `qtyShipped` 로 잡히는 기존 표본 모양 그대로, saga·피킹·배송 등 교차 참조 동반) · finance — 정산 완료 TRANSFER 1건 이상(잔액 불변: 같은 합 TOPUP 짝 또는 근거 있는 다른 방식) · ecommerce — 실현 주문(CONFIRMED/SHIPPED) 1건 이상(기존 상품·셀러).
- **2.** 그 도메인들의 월간·주간 고정 테스트 상수를 **손으로 다시 센 값**으로 갱신(파생 함수 재호출 금지). 운영자 개요 headline 테스트가 샘플 값을 고정한다면 함께.
- **3. 충돌 해결(소유자 결정):** wms «이전 구간 = `"0"`» 성질은 **월간 이전 구간(8/1~15)을 비워 두어** 지킨다. 그 테스트를 «주간 이전 구간은 값이 있다(손으로 센 값)» 와 «월간 이전 구간은 `['0','0','0']` — 키가 빠지지 않는다» 로 나눈다. 월간 이전 구간에는 어떤 도메인도 행을 넣지 않는다.

## Out of Scope

- iam · scm · erp(이미 지난 주 값 있음), 창 경계 · `SAMPLE_AS_OF` 변경, 차트·카드 코드.

# Acceptance Criteria

- **AC-1** 샘플 wms `qtyShipped` · finance `transferAmount:KRW` · ecommerce `revenue` 의 주간 `previous` 가 0 이 아니다 — 테스트가 손으로 센 값으로 단언.
- **AC-2** wms 월간 `previous` 는 여전히 `['0','0','0']`(키 존재) — 3 의 분리된 단언.
- **AC-3** 바뀐 월간·주간 상수(합계 · 비영 일수 · 랭킹 · 재무 `topupCount` 날짜 목록 등)가 손으로 다시 센 값이고, 374 · 378 의 하한(월 비영 ≥ 5 · 재무 그리는 지표 ≥ 6 · 랭킹 ≥ 3)이 유지된다.
- **AC-4** `sample-overview-cards-match-lists` · `sample-fixtures-schema-*` · `sample-label-rule` · `headline-metric` 초록, console-web `lint` · `tsc` · vitest `tests/unit/sample*` 초록, CI 프런트 단위 초록.
- **AC-5** 배포 뒤 운영 `https://console.hubwang.com/dashboards/overview` 샘플 방문자 화면에서 wms · finance · ecommerce 카드의 «지난 주» 막대 폭 > 0(Playwright 실측).

# Related Specs

- 없음(샘플 픽스처만).

# Related Contracts

- 없음.

# Edge Cases

- 9/8 20:00 KST(= 11:00Z) 이후는 이전 구간 밖 — 시각을 그 전으로.
- KST 날짜 경계(UTC 15:00 이후 = 다음 날).
- 사람이 읽는 문자열은 «(샘플)» 로 끝난다.

# Failure Scenarios

- 월간 이전 구간(8월)에 행을 넣는다 → «키가 빠지지 않고 `"0"`» 를 지키는 표본이 사라진다(AC-2).
- wms 출고 행만 넣고 saga·배송 교차 참조를 빠뜨린다 → 상세 화면 표본이 깨진다(374 가 쓴 시드 목록 패턴을 따른다).

# Implementation Notes (2026-10-27 UTC)

- 추가 행(전부 KST 9/7~8, 20:00 이전 · 8월 0건): wms `ord-sample-0011` SHIPPED 20(출고 KST 09-08 12:00 — `EXTRA_SHIPPED_SEEDS` 한 줄, saga · 피킹 · 배송 `ship-sample-0008` · `dispatch-sample-0008` 은 같은 시드 목록에서 파생) · finance `txn-sample-0013` TRANSFER 30,000(KST 09-08 13:02) + `txn-sample-0014` TOPUP 30,000(KST 09-07 17:03, 이미 충전이 있는 날 — `topupCount` 날짜 목록 불변) 로 **잔액 상쇄** · ecommerce `order-sample-0018` CONFIRMED `ECOBAG ×3` = 36,000(seller-sample-0002, 결제 KST 09-08 11:00).
- **AC-1** 주간 previous: wms `qtyShipped` **20**(shipmentCount 1) · finance `transferAmount:KRW` **30,000** · ecommerce `revenue` **36,000**(units 3 · orders 1) — 손으로 센 값으로 단언.
- **AC-2** `sample-wms-metrics.test.ts` 를 둘로: 주간 previous `[['qtyReceived','0'],['qtyShipped','20'],['shipmentCount','1']]` / 월간 previous `['0','0','0']`(키 존재). finance · ecommerce 에도 월간 previous 0 단언 추가.
- **AC-3** 바뀐 월간 상수(손 합산): wms `qtyShipped` 175 → 195 · `shipmentCount` 7 → 8 · 출고 날 7 → 8 / finance 월 `['6','205000','4','125000',…]` → `['7','235000','5','155000',…]`, `topupCount` 날 6 그대로(≥ 6 유지) / ecommerce 월 매출 343,000 → 379,000 · units 14 → 17 · orders 10 → 11, 상품 랭킹 prod-3 36,000 → 72,000 · 주문 수 순위 prod-3 ↔ prod-2, 셀러 seller-2 36,000 → 72,000 · 3건(랭킹 4 · 3항목 유지). `sample-fixtures-schema-wms` 주문 10 → 11 · 배송 7 → 8.
- **AC-4 (로컬)** vitest `tests/unit/sample` · `features/operator-overview` · `performance-format` 32 파일 604건 rc=0(메인 세션 재실행) · 구현 에이전트 전체 vitest 451 파일 5,585건 rc=0 · `tsc` rc=0 · `lint` rc=0.
- **AC-5 열림** — 배포 뒤 운영 실측.
