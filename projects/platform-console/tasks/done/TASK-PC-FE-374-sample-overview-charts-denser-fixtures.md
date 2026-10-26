# Task ID

TASK-PC-FE-374

# Title

익명 샘플 모드의 도메인 개요 그래프가 거의 비어 있다 — 표본 픽스처에 9/1~15 분포 행을 더해 «실적» 막대·랭킹·환율 이력이 읽히게 한다

# Status

done

# Owner

frontend

# Task Tags

- code
- console
- sample

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (픽스처 행 추가 + 고정 테스트 갱신; 코드 경로 불변)
> Drafted 2026-10-26 UTC. 출처: 소유자 요청 «각 도메인 개요의 그래프에 더 많은 데이터를 보이게». 대상 = 샘플 모드(소유자 선택). 라이브 데모 스택은 범위 밖.

# Dependency Markers

- 선행 없음. `TASK-PC-FE-373`(review, #4565 머지됨)이 `fixtures/wms.ts` 경보 행을 바꿨다 — 그 위에서 작업한다(origin/main 기준).
- 관련: `ADR-MONO-074`(익명 방문자 = 합성 샘플), `TASK-MONO-807`~`812`(도메인별 «실적» 스트립), `TASK-PC-FE-164`·`172`·`174`.

# Goal

로그인 없이 보는 각 도메인 개요(`/ecommerce` · `/wms` · `/scm` · `/erp` · `/finance` · `/iam` · `/ledger`)의 그래프가 월간 창(9/1~15)에서 여러 날에 걸친 막대를, 주간 창(9/14~15)에서 0 이 아닌 막대를 보여 준다. 픽스처 행을 늘리고, 지표는 지금처럼 행에서 파생한다 — 목록·KPI 카드·시계열이 한 행 집합에서 나오는 구조를 지킨다.

# Scope

## In Scope

- **AC-0 사실 (기안 중 확인):** 시각은 `shared/sample/codes.ts` 의 `SAMPLE_AS_OF = 2026-09-15T11:00:00Z` 로 고정. 창은 `shared/sample/fixtures/metrics-windows.ts` 의 고정 리터럴(주=9/14~15, 월=9/1~15). 현재 표본: ecommerce 주문 6(월간 매출 막대 1개, 주간 0) · scm PO 4(주간 0, `receivedCount` 상수 "0") · erp 결재 5(주간 0) · finance 거래 3(월 1~2일) · iam 감사 5(주간 0, `newAccount` 상수 "0") · ledger 환율 이력 쌍당 2점 · wms «실적» 주간 입고 0. wms 처리량 차트는 이미 합성 14일치라 제외.
- **1.** `fixtures/ecommerce.ts` 주문 · `scm.ts` PO · `erp.ts` 결재 요청 · `finance.ts` 거래 · `iam.ts` 감사 행 · `wms.ts` ASN/출고(«실적» 스트립용) · `ledger.ts` `FX_HISTORY` 에 행을 추가한다. 날짜는 9/1~15 에 퍼뜨리고 주간 창(9/14~15)에도 반드시 일부를 둔다. 상태 분포는 기존 상태 막대(주문·배송·PO·결재)가 여러 칸을 갖게 고른다.
- **2.** ecommerce 랭킹(상품·판매자 top-5)이 월간 창에서 3개 이상 항목을 갖게 기존 표본 상품·판매자에 주문을 분배한다.
- **3.** 상수로 박힌 "0" 지표(scm `receivedCount`, iam `newAccount`)는 같은 파일의 행에서 파생할 근거 행이 있으면 파생으로 바꾸고, 없으면 그대로 둔다(근거 없는 숫자를 만들지 않는다).
- **4.** 이 변화로 깨지는 고정 테스트를 새 행 집합에 맞게 갱신한다 — `tests/unit/sample-ecommerce-metrics.test.ts` · `sample-wms-metrics.test.ts` · `sample-scm-metrics.test.ts` · `sample-erp-metrics.test.ts` · `sample-iam-metrics.test.ts` · `sample-finance-metrics.test.ts`. 기대값은 손으로 다시 셈한 상수로 고정한다(파생 함수 재호출로 기대값을 만들지 않는다 — 그러면 테스트가 아무것도 안 문다).

## Out of Scope

- 라이브 데모 스택 시드(`infra/demo/seed/*`) — API 로는 과거 시각을 못 넣는다. 필요하면 별도 task.
- `SAMPLE_AS_OF` · 창 경계 변경(주간 창 확대) — 파급이 넓다.
- 차트 컴포넌트·지표 파생 로직 변경.

# Acceptance Criteria

- **AC-1** ecommerce · scm · erp · finance · iam · wms 의 월간 «실적» 시계열에서 0 이 아닌 날이 각 도메인 대표 지표 기준 **5일 이상**.
- **AC-2** 같은 도메인들의 주간 창 시계열에서 0 이 아닌 날이 **1일 이상**.
- **AC-3** ecommerce 월간 상품 랭킹·판매자 랭킹이 각각 **3항목 이상**.
- **AC-4** ledger 환율 이력이 표본의 각 통화쌍마다 **7점 이상**.
- **AC-5** 모든 추가 행이 기존 스키마 테스트(`sample-fixtures-schema-*.test.ts`)와 라벨 규칙(`sample-label-rule.test.ts`, 사람이 읽는 문자열은 «(샘플)» 로 끝남)을 통과하고, `sample-overview-cards-match-lists.test.ts`(개요 합계 = 목록 합계)가 초록.
- **AC-6** console-web `lint` · `tsc` · vitest 전체 초록.

# Related Specs

- `projects/platform-console/specs/` 의 샘플 모드 관련 서술(ADR-MONO-074 실행 시리즈).

# Related Contracts

- 없음(픽스처는 기존 스키마를 따른다. 계약 변경 없음).

# Edge Cases

- 주문의 «실현 매출» 은 CONFIRMED/SHIPPED/DELIVERED 만 센다 — 매출 막대를 늘리려면 그 상태로 넣는다.
- KST 날짜 경계: 시각을 UTC 15:00 근처에 두면 KST 다음날로 넘어간다. 날짜를 정할 때 KST 로 셈한다.
- 목록 화면 페이지 크기를 넘으면 페이지네이션 표본이 생긴다 — 넘더라도 목록·개요 합계가 일치해야 한다.

# Failure Scenarios

- 기대값을 파생 함수로 재계산하는 테스트 → 픽스처가 틀려도 초록. 금지(Scope 4).
- 새 행의 id 가 기존 상세 화면 링크·교차 참조(주문→배송, ASN→검수)와 충돌 → 상세 표본이 깨짐. 기존 id 체계를 이어 쓴다.

# Implementation Notes (2026-10-26 UTC)

- 추가 행: ecommerce 주문 11(`order-sample-0007~0017`) + 배송 4 + 상품 `prod-sample-0004`·셀러 `seller-sample-0004` · scm PO 6 · erp 결재 7 · finance 거래 6(TOPUP·TRANSFER 각 3, 합이 상쇄돼 잔액 불변) · iam 로그인 10·잠금 2 · wms SHIPPED 출고 6(saga·피킹·배송 동반) + CLOSED ASN 5(검수 동반) · ledger 환율 이력 쌍당 7·8점.
- AC-1 월 비영 일수(대표 지표): ecommerce 매출 10 · scm 발주 8 · erp 상신 10 · finance 7 · iam 로그인 성공 8 · wms 출고 7/입고 6. AC-2 주 비영: 각 1~2일(wms 출고는 기존 «주간에 0 인 날이 남는다» 대조군 때문에 09-14 하루). AC-3 월 랭킹: 상품 4 · 셀러 3. AC-4: 7·8점.
- AC-6: `tsc` rc=0 · `lint` rc=0 · `vitest run tests/unit/sample-` 23 파일 459건 rc=0. 전체 vitest 는 rc=1 — 실패는 `TenantsScreen`·`TenantPlacement`·`TenantDetail`·`OperatorProfileEditDialog` 등 샘플 픽스처를 안 쓰는 상호작용 테스트였고, **변경 없는 main 체크아웃에서 같은 4개 파일을 돌려도 3건이 실패**(매 실행 다른 테스트) → 기존 부하 의존 flake, 이 변경과 무관.
- 범위 판단: scm `receivedCount` 는 근거 행(RECEIVED 이력)이 없어 "0" 유지. iam `newAccount` 는 이미 `IAM_ACCOUNTS.createdAt` 파생이었고 신규 계정 행이 없어 0 유지. `ORDERS_SUMMARY`·`SHIPPINGS_SUMMARY` 를 손 상수에서 행 파생으로 바꿨다(행 수와 어긋나지 않게). DELIVERED 주문은 기존 «DELIVERED 필터 = 빈 결과» 테스트 보존을 위해 안 넣었다. 새 실현 주문의 정산 accrual 은 없다(9월 정산 기간은 의도적으로 OPEN·빈 상태).
- 손 고친 테스트(기대값=손으로 센 상수): `sample-{ecommerce,scm,erp,finance,iam,wms}-metrics.test.ts` + 목록에 없던 `sample-fixtures-schema-{ecommerce,scm,wms}.test.ts`.
