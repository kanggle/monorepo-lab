# Task ID

TASK-PC-FE-378

# Title

Fix issue found in TASK-PC-FE-374 — 재무 개요 «실적» 그래프(충전 건수)가 월간 4일뿐 — 374 AC-1 «5일 이상» 을 그리는 지표가 아닌 합집합으로 셌다

# Status

review

# Owner

frontend

# Task Tags

- code
- console
- sample
- bug

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (픽스처 행 추가 + 고정 테스트 기대값 + 셈 단언을 그리는 지표로)
> Drafted 2026-10-26 UTC. 출처: 운영 `https://console.hubwang.com/finance?period=month` 샘플 방문자 실측(2026-10-26 UTC, Playwright) — 월간 15열 중 0 아닌 막대 **4개**.

# Dependency Markers

- 선행: `TASK-PC-FE-374`(done, #4570) — 이 티켓이 고치는 결함의 출처. 그 티켓 끝 `## CORRECTION` 이 이 티켓을 가리킨다.

# Goal

로그인 없이 보는 재무 개요(`/finance`)의 «실적» 일별 그래프가 월간 창(9/1~15)에서 0 아닌 막대 **5일 이상**을 보인다 — 374 의 원래 목표를 재무 칸에서 실제로 충족한다. 그리고 «그래프가 그리는 지표» 로 일수를 세는 단언을 남겨 같은 오판이 재발하지 않게 한다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 확인, 2026-10-26 UTC):**
  - 그래프 지표: `features/finance-overview/components/FinanceOverviewScreen.tsx:321` `chartKey="topupCount"` — 재무 그래프는 **충전 건수만** 그린다.
  - 픽스처 `shared/sample/fixtures/finance.ts` 의 TOPUP 행은 4건(`txn-sample-0001` 9/1 + 374 가 더한 0004 · 0006 · 0009) ⇒ 월간 0 아닌 날 4. 374 구현 보고의 «재무 7일» 은 **TOPUP 또는 TRANSFER 가 있는 날의 합집합**이었다(`tests/unit/sample-finance-metrics.test.ts:129` «days with a settled TOPUP/TRANSFER»).
  - 운영 실측(위 출처): `/finance?period=month` 0 아닌 막대 4 · `week` 1. 같은 실측에서 다른 다섯 도메인은 그리는 지표 기준 ecommerce 10 · scm 8 · erp 10 · iam 8 · wms 7 — 374 AC-1 충족.
  - 374 가 잔액을 지킨 방식: 추가 TOPUP 합 = 추가 TRANSFER 합(`finance.ts:189-190` «net to zero, so the typed balance above is left untouched»).
- **1.** `finance.ts` 에 TOPUP 행을 더해 9/1~15(KST) 중 **TOPUP 이 있는 날이 6일 이상**이 되게 한다(주간 9/14~15 에 이미 있는 1일은 유지). 잔액 불변식은 374 와 같은 방식(같은 합의 TRANSFER 를 짝으로) 또는 다른 근거 있는 방식으로 지키고, 방식을 PR 에 적는다.
- **2.** `sample-finance-metrics.test.ts` 의 월·주 기대값을 손으로 다시 센 상수로 갱신한다(파생 함수 재호출 금지 — 374 규칙 그대로).
- **3. 재발 방지 단언:** 그 테스트에 «`chartKey` 지표(`topupCount`) 의 0 아닌 날» 을 별도로 세는 단언을 넣는다(합집합 단언과 분리). 기대값은 손으로 센 날짜 목록.

## Out of Scope

- `chartKey` 변경(그래프가 무엇을 그릴지는 제품 결정 — 바꾸려면 별도 결정).
- 다른 다섯 도메인(실측상 충족).
- 라이브 데모 스택 시드.

# Acceptance Criteria

- **AC-1** 샘플 재무 월간 시계열의 `topupCount` 0 아닌 날 ≥ 6, 주간 ≥ 1 — 테스트가 손으로 센 날짜 목록으로 단언한다.
- **AC-2** 잔액·요약 불변식(`ledger = available + held`, 개요 합계 = 목록 합계)이 유지된다 — `sample-overview-cards-match-lists.test.ts` · `sample-fixtures-schema-*` · `sample-label-rule.test.ts` 초록.
- **AC-3** console-web `lint` · `tsc` · vitest(`tests/unit/sample-*`) 초록, CI 프런트 단위 초록.
- **AC-4** 배포 뒤 운영 `https://console.hubwang.com/finance?period=month` 샘플 방문자 화면에서 0 아닌 막대 ≥ 6 (Playwright 실측, 데모 창 불필요).

# Related Specs

- 없음(샘플 픽스처만).

# Related Contracts

- 없음.

# Edge Cases

- KST 날짜 경계(UTC 15:00 이후 = 다음 날) — 374 와 같다.
- 추가 행은 사람이 읽는 문자열이 «(샘플)» 로 끝나야 한다(`sample-label-rule`).

# Failure Scenarios

- 합집합(TOPUP ∪ TRANSFER)으로 다시 세면 이번 결함이 그대로 초록 — AC-1 은 **그리는 지표**로 센다.
- TOPUP 만 더하고 잔액 짝을 안 맞추면 계좌 화면의 잔액·요약이 어긋난다 → AC-2.

# Implementation Notes (2026-10-27 UTC)

- 추가 행(`finance.ts`): `txn-sample-0010` TOPUP 10,000 (KST 09-05) · `0011` TOPUP 15,000 (KST 09-12) · `0012` TRANSFER 25,000 → `sample-account-0002` (KST 09-12). **잔액 불변**: 추가 충전 합 25,000 = 추가 이체 25,000 — 374 와 같은 상쇄 방식(`finance.ts:189-190` 주석).
- **AC-1** `topupCount` 0 아닌 날(손 목록): 09-01 · 03 · 05 · 07 · 12 · 15 = **6**, 주간 09-15 = 1. `sample-finance-metrics.test.ts` 에 합집합 단언과 **분리된** «그리는 지표» 단언 추가(날짜 배열 + `>= 6`) — 옛 픽스처(4일)에서는 날짜 배열이 달라 빨강.
- 기대값(손 합산): 월 `['4','180000','3','100000','0','0']` → `['6','205000','4','125000','0','0']`(충전 80,000+50,000+30,000+20,000+10,000+15,000 · 이체 40,000+25,000+35,000+25,000), 주 불변. 합집합 날짜 7 → 9.
- **AC-2 · AC-3**: `vitest run tests/unit/sample` 24 파일 464건 rc=0(`sample-overview-cards-match-lists` · `sample-fixtures-schema-*` · `sample-label-rule` 포함) · `tsc` rc=0 · `lint` rc=0.
- **AC-4 열림** — 배포 뒤 운영 샘플 실측.
- 기록: 구현 에이전트가 사용 한도로 중단돼 남긴 diff 를 메인 세션이 읽고(날짜·합·상대 계좌 id 대조) 검증해 이어받았다.
