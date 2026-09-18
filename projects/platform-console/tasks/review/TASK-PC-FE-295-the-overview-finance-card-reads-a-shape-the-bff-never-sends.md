# Task ID

TASK-PC-FE-295

# Title

운영자 개요의 finance 카드가 console-bff 가 보내지 않는 모양을 읽는다 — 잔액이 있어도 «잔액 정보 없음»
(🔴 범위 확장 2026-09-17: **WMS · SCM 카드도 같은 결함** — § Goal 끝 «범위 확장» 절)

# Status

review (2026-09-18 UTC — AC-0~AC-7 닫힘 · AC-0 ② 라이브 원문만 ⚪ 창 예산)

# Owner

platform-console

# Task Tags

- code
- test
- contract

---

# Goal

`TASK-PC-FE-286` 조정자 리뷰에서 **코드 판독으로** 발견(샘플 모드와 무관한, 로그인 운영자 경로의 결함 후보).

- console-bff `OperatorOverviewCompositionUseCase.callFinance` → `FinanceBalanceReadAdapter.readBalances` 는
  `GET /api/finance/accounts/{id}/balances` 응답 **본문을 그대로** finance 레그의 `data` 로 싣는다.
  그 응답 모양은 `projects/finance-platform/specs/contracts/http/account-api.md` § `GET /api/finance/accounts/{id}/balances`:
  `{ "data": [ { "currency", "ledger", "available", "held" } ], ... }`.
- console-web `features/operator-overview/api/operator-overview-types.ts` `FinanceDataSchema` 는
  `{ balance?: { amount?, currency? }, accountId? }` 를 기대하고(전 필드 optional + passthrough ⇒ **파싱은 통과**),
  `DomainCardSummaries.tsx` 는 `parsed.data.balance !== undefined` 일 때만 «잔액 조회 가능» 을 그린다.
- ⇒ 기본 계좌가 설정된 운영자에게 백엔드가 잔액을 정상으로 돌려줘도 **첫 화면 finance 카드는 «잔액 정보 없음»** 일 것이다.

🔴 **계약의 공백이 원인이다**: `console-integration-contract.md` § 2.4.9.1 은 finance 레그가 무엇을 부르는지는 적지만 카드 `data`
의 **모양은 정의하지 않는다** — producer(bff)와 consumer(web)가 각자 다른 가정을 했고 둘을 함께 재는 테스트가 없다
(bff 슬라이스 테스트는 `Map.of("balance", 0)` 을, web 테스트는 `{ balance: {...} }` 를 각자 심는다).

⚪ **라이브 미확인** — 코드 판독이다. AC-0 이 먼저 실측한다.

## 범위 확장 — WMS · SCM 카드 (조정자, 2026-09-17 UTC · `TASK-PC-FE-287` 리뷰)

같은 원인(계약 § 2.4.9.1 이 카드 `data` 모양을 정의하지 않음 · bff 는 producer 본문을 그대로 싣는다)으로 **두 카드가 더** 어긋난다.
조정자가 코드와 계약을 대조했다:

| 카드 | bff 가 싣는 것 (어댑터 → producer 계약) | web 스키마가 읽는 것 | 로그인 운영자에게 보일 것 |
|---|---|---|---|
| finance «잔액 정보» | `FinanceBalanceReadAdapter` → `GET /api/finance/accounts/{id}/balances` = `{ data: [ {currency, ledger, available, held} ] }` (`finance-platform/.../account-api.md`) | `FinanceDataSchema` `{ balance, accountId }` | «잔액 정보 없음» |
| **WMS «총 재고» · «알림»** | `WmsInventoryReadAdapter` → `GET /api/v1/admin/dashboard/inventory` = `{ content: [ {… availableQty, onHandQty, lowStockFlag …} ], page, sort }` (`wms-platform/specs/contracts/http/admin-service-api.md` § 1.1) | `WmsDataSchema` `{ inventorySnapshot: { totalStockUnits, alertCount } }` | 둘 다 `—` |
| **SCM «스냅샷 노드 수»** | `ScmInventoryReadAdapter` → `GET /api/inventory-visibility/snapshot` = `{ data: { content: [ {nodeId, sku, quantity …} ], … }, meta: { warning } }` (`scm-platform/specs/contracts/http/inventory-visibility-api.md`) | `ScmDataSchema` `{ meta.warning, nodes[] }` | 노드 수 `—` (경고 문구는 보임) |

🔵 나머지 셋은 **맞는다**(조정자 대조): IAM `totalElements` · ERP `meta.totalElements` · E-Commerce `totalElements` — producer 응답 최상위와 스키마가 같은 키.

🔴 WMS 는 집계 의미도 정해야 한다 — producer 는 **행 목록(첫 페이지)** 을 주지 «총 재고 합» 이나 «알림 수» 를 주지 않는다. 한 페이지 합은 총계가 아니다.
그래서 AC-1 의 갈래 판단에 **«카드가 무엇을 보여 줄 수 있는가»** 가 들어간다(예: 총계 대신 `page.totalElements` = 재고 행 수, 알림은 `lowStockOnly=true` 의 `totalElements` —
그 경우 bff 레그 경로가 바뀐다). 숫자를 지어내는 쪽(한 페이지 합을 «총 재고» 로)은 택하지 않는다.

---

# Scope

## In Scope

- AC-0 실측(결함이 실제로 나타나는가)
- 계약 § 2.4.9.1 에 finance 레그 `data` 모양을 **먼저** 적는다(계약 → 구현 순서)
- 적은 모양에 맞춰 한쪽을 고친다 + 양쪽을 한 모양으로 묶는 테스트

## Out of Scope

- 샘플 모드(`ADR-MONO-074`) — `TASK-PC-FE-286` 의 샘플 finance 카드는 **지금의** `FinanceDataSchema` 모양을 따른다.
  🔴 이 티켓이 모양을 바꾸면 `shared/sample/fixtures/dashboards.ts` 의 finance 카드와
  `tests/unit/sample-overview-cards-match-lists.test.ts` 의 finance 칸도 **같은 PR 에서** 새 모양으로 옮긴다.
- 카드에 금액 숫자를 새로 그리는 것 — F5 money discipline(«잔액 조회 가능» + 통화만) 은 그대로.

---

# Acceptance Criteria

- [x] **AC-0 실측 먼저** — ① red-first 단위 테스트: 계약 모양(`{ data: [ { currency: 'KRW', ledger: '1000', available: '1000', held: '0' } ], meta: {} }`)을
      finance 레그 `data` 로 `DomainCardSummaries` 에 넣으면 지금 «잔액 정보 없음» 이 그려진다(rc=1 로 실패하는 칸). ② 가능하면 로컬/라이브
      `GET /api/console/dashboards/operator-overview` 의 finance 카드 `data` 원문을 한 번 기록한다(못 재면 ⚪ + 이유).
      🔴 ①이 초록이면(= 결함 없음) **구현하지 않고** 이 파일에 근거를 적고 닫는다.
- [x] **AC-1 계약 먼저** — `console-integration-contract.md` § 2.4.9.1 에 finance 레그 `data` 모양을 적는다. 갈래와 추천:
      ⓐ **(추천)** bff 는 producer 본문을 그대로 싣고(다른 레그와 같은 원칙), web 스키마가 `data[]` 에서 기본 계좌 통화의 행을 읽는다 — bff 변경 0.
      ⓑ bff 가 `{ balance: { amount, currency }, accountId }` 로 가공해 싣는다 — web 변경 0, 대신 bff 가 finance 모양을 안다.
      갈래 선택은 이 파일에 이유와 함께 적는다(소유자가 한 줄로 뒤집을 수 있게).
- [x] **AC-2 고침** — 적은 모양대로 한쪽만 고친다. AC-0 ① 칸이 초록이 된다.
- [x] **AC-3 양쪽을 묶는 테스트** — producer 계약 모양 한 벌을 **양쪽 테스트가 같이 쓰는** 형태로 둔다(bff 슬라이스/IT 가 싣는 것 = web 이 파싱하는 것).
      지금처럼 양쪽이 각자 다른 가짜 모양을 심으면 이 결함이 다시 초록으로 숨는다.
- [x] **AC-4 샘플 동반 이동** — 모양이 바뀌면 샘플 finance 카드 + `sample-overview-cards-match-lists.test.ts` finance 칸을 같은 PR 에서 옮기고 초록.
- [x] **AC-5 대조군** — 기본 계좌 **없는** 운영자(레그 short-circuit) 경로는 무변화.
- [x] **AC-6 (범위 확장) WMS · SCM 카드** — 위 AC-0 ~ AC-4 를 WMS · SCM 카드에도 똑같이 적용한다: 계약 모양으로 red-first 칸 → 계약 § 2.4.9.1 에
      두 레그의 `data` 모양(과 WMS 의 **집계 의미**) → 한쪽 고침 → 양쪽이 같은 모양 한 벌을 쓰는 테스트 → 샘플 카드(`TASK-PC-FE-287` · `288` 이
      지금 스키마 모양으로 파생해 둔 것) + `sample-overview-cards-match-lists.test.ts` 칸 동반 이동. 셋을 한 PR 로 할지 카드별로 나눌지는 구현자가 정해 적는다.
- [x] **AC-7 여섯 카드 전수 가드** — IAM · ERP · E-Commerce 까지 포함해, **레그마다 producer 계약 모양의 표본 한 벌**을 web 카드 파서에 넣었을 때
      카드가 값을 그린다(`—` 나 «정보 없음» 이 아니다)는 칸 6개. 새 레그가 생기면 이 표에 칸이 없으면 빨개지게 한다. 이번 결함은 셋이 같은 원인으로
      따로따로 숨어 있었다 — 카드별 수정만으로는 네 번째를 못 막는다.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.9.1 (operator overview) · § 2.4.7 (finance)
- `projects/finance-platform/specs/contracts/http/account-api.md` § `GET /api/finance/accounts/{id}/balances`
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.1 `GET /api/v1/admin/dashboard/inventory` (범위 확장)
- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` § `GET /api/inventory-visibility/snapshot` (범위 확장)
- `projects/platform-console/specs/services/console-web/architecture.md` · `projects/platform-console/specs/services/console-bff/architecture.md`
- `projects/platform-console/tasks/done/TASK-PC-FE-014-*` (finance Option (a) 활성화 — 이 레그를 켠 티켓)

# Related Contracts

- `console-integration-contract.md` § 2.4.9.1 — 🔴 이 티켓이 finance 레그 `data` 모양을 **추가**한다
- `account-api.md` balances 응답(불변)

# Edge Cases

- 계좌가 여러 통화의 잔액을 가진다 — ⓐ 에서 어느 행을 카드의 통화로 보일지 정한다(계좌 통화 = 기본).
- 잔액 배열이 비어 있다 — «잔액 정보 없음» 이 **정직한** 값이 되는 유일한 경우.
- finance 레그가 `forbidden`/`degraded` — 카드 상태 분기는 기존 그대로.

# Failure Scenarios

- AC-0 없이 고친다 → 결함이 없던 경우 멀쩡한 경로를 바꾼다.
- 계약을 안 적고 고친다 → 다음 변경에서 다시 한쪽만 움직인다(이번 결함의 원인 그대로).
- 샘플 픽스처를 안 옮긴다 → `main` 의 샘플 개요 카드가 degrade 로 떨어진다(`sample-overview-cards-match-lists` 가 빨강).

# Test Requirements

- console-web: `pnpm lint` · `npx tsc --noEmit` · `pnpm test` (각각 독립 + `rc=$?`)
- console-bff(ⓑ 선택 시): `./gradlew :projects:platform-console:apps:console-bff:test`

# Definition of Done

- [ ] 계약에 finance 레그 `data` 모양이 있다
- [ ] 계약 모양으로 넣은 red-first 칸이 초록
- [ ] 샘플 개요 카드 가드 초록

분석=Opus 5 / 구현 권장=Sonnet 5 (갈래 ⓐ 기준 — web 스키마·렌더 한 곳 + 테스트).

---

# 🟢 AC-0 실측 (2026-09-18 UTC · 분석=Opus 5 / 구현=Opus 5)

## ① red-first 칸 — **결함이 실재한다**

`tests/unit/features/operator-overview/leg-body-contract.test.tsx` 를 먼저 세우고 돌렸다:

| | 결과 |
|---|---|
| 실행 | **9칸** |
| 실패 | **4** — `wms` · `scm` · `finance` 전수 칸 + finance F5 칸 |
| 통과 | **5** — `iam` · `erp` · `ecommerce` 전수 칸 + 픽스처 커버리지 칸 + 「빈 잔액이 정직한 «없음»」 칸 |

🔵 **대조군이 초록인 것이 이 측정의 힘이다.** 술어가 «전부 빨강» 이면 모집단이 아니라 술어를 의심해야 하는데, 여기선 **깨진 셋만** 물었다.

## ② 라이브 — ⚪ **재지 않았다**

`GET /api/console/dashboards/operator-overview` 의 원문을 뜨려면 데모 창(+ 운영자 로그인 + 기본 계좌가 설정된 운영자)이 필요하고, 이번 세션에 창을 열 예산이 없다(`TASK-MONO-672` § AC-0 이 잰 잔여 **30분** · 이번 달 마지막). 🔵 **그러나 이 ⚪ 는 판정을 약화시키지 않는다** — 아래 § 출처가 producer 의 **컨트롤러·DTO 코드**에서 모양을 떴고, 그것이 라이브 응답을 만드는 바로 그 코드다.

## 출처 — 계약서 산문이 아니라 **producer 코드**에서 떴다

| 레그 | producer 코드 | 실제 본문 |
|---|---|---|
| iam | `AccountAdminController#search` → `AccountSearchResponse(content, totalElements, page, size, totalPages)` | 최상위 `totalElements` |
| wms | `InventoryDashboardController#list` → `PageResponse<InventorySnapshotResponse>(content, page{…}, sort)` | `page.totalElements` |
| scm | `InventoryVisibilityController#getSnapshot` → `ApiEnvelope.of(PageResponse<…>, meta)` | `{ data: {…, totalElements}, meta: {timestamp, warning, staleness} }` |
| finance | `AccountController#balances` → `ApiEnvelope.of(List<BalanceResponse(currency, ledger, available, held)>)` | `{ data: [ … ], meta }` |
| erp | `DepartmentController#list` → `ApiEnvelope.ofList(...)` | `meta.totalElements` |
| ecommerce | `AdminProductController#list` → `ProductListResponse(content, page, size, totalElements, totalPages)` | 최상위 `totalElements` |

그리고 bff 는 **가공하지 않는다** — 어댑터 셋이 `RestClientHelper.authenticatedGet` 결과를 그대로 돌려주고 `OperatorOverviewCompositionUseCase` 가 그것을 레그에 그대로 싣는다. ⇒ **producer 본문 = 카드의 `data`**.

## 🔴🔴 티켓이 「계약의 공백」이라고 부른 것은 공백이 아니라 **틀린 기재**였다

§ 2.4.9.1 *Response schema* 의 예시 JSON 이 **없는 모양 셋을 가르치고 있었다**:

| 예시가 적은 것 | producer 가 보내는 것 |
|---|---|
| `data: { "accountCount": 12345 }` (iam) | `totalElements` |
| `data: { "inventorySnapshot": { … } }` (wms) | `content` + `page` |
| `data: { "activeDepartmentCount": 87 }` (erp) | `meta.totalElements` |

🔴 **`WmsDataSchema.inventorySnapshot` 의 출처가 바로 이 예시다.** consumer 는 계약을 **충실히 구현했고** 계약이 틀렸다. 그래서 AC-1 은 «빠진 것을 적는» 일이 아니라 **«적힌 것을 고치는»** 일이 됐고, 예시 옆에 *"이 파일의 예시 본문은 모양에 대해 규범이다 — producer 의 컨트롤러/DTO 에서 떠라"* 를 못박았다.

🔵 iam·erp 는 예시가 틀렸는데도 **카드는 맞았다** — 스키마를 쓴 사람이 그 둘만 producer 를 확인했기 때문이다. 즉 이 결함은 «계약이 틀리면 반드시 깨진다» 가 아니라 **«계약이 틀리면 확인 안 한 쪽이 깨진다»** 이고, 그래서 AC-7 전수 가드가 필요하다.

---

# 🟢 AC-1 — 갈래 **ⓐ** (bff 무변경, web 이 producer 모양을 읽는다)

소유자가 한 줄로 뒤집을 수 있도록 이유를 적는다:

1. **bff 는 이미 정직한 쪽이다.** 여섯 레그 전부 producer 본문을 그대로 싣는다. ⓑ(bff 가 가공)를 고르면 **한 레그만 특별해지고**, 그 특례가 다음 레그의 선례가 된다.
2. **가공은 계약을 늘린다.** producer 가 이미 문서화한 모양 위에 bff 전용 모양이 하나 더 생기고, 그 둘이 갈라지는 날 아무도 모른다 — 이번 결함이 정확히 그 모양이다.
3. **비용이 web 쪽이 싸다.** 스키마 셋 + 렌더러 셋, 코드 40줄. ⓑ 는 어댑터·포트·유스케이스·슬라이스 테스트를 건드린다.

## 🔴 WMS 집계 의미 — 「총 재고」는 **불가능**하다

producer 는 **한 페이지**의 행을 준다. 그 위에서:

- **「총 재고」** = Σ `onHandQty` 는 **페이지 지역 합을 전체 총계로** 내놓는 것 ⇒ AC-6 이 금지한 «지어낸 숫자». (🔴 샘플 픽스처 `TASK-PC-FE-287` 이 정확히 이 계산을 하고 있었다.)
- **「알림」** = `lowStockFlag` 개수도 같은 문제. 정직하게 세려면 `lowStockOnly=true&size=1` **두 번째 질의**가 필요하고, 그건 이 라우트에 **레그 하나를 더 다는 비용 결정**이지 렌더링 수정이 아니다.

⇒ **「재고 행 수」 = `page.totalElements`** 로 바꿨다(그 수는 페이지 총계가 **정직하게 답할 수 있는** 유일한 수다). **알림 타일은 제거**했다 — 🔵 운영에서 그 타일은 지금껏 **`—` 만** 보여 줬으므로 제거로 잃는 것이 없고, 영구히 빈 타일은 «알림 0건» 으로 읽혀 «묻지 않았다» 를 숨긴다.

🔵 **뒤집는 방법**: 계약 § 2.4.9.1 producer 표에 행을 하나 더하고 두 번째 레그 호출을 배선하면 된다(계약서에 그 순서를 적어 뒀다).

## 🔴 SCM — 「노드 수」도 같은 이유로 **「스냅샷 행 수」**

행은 node × sku 스냅샷이지 노드가 아니고, 페이지에서 **서로 다른 노드 수**를 셀 수도 없다(페이지는 전체 집합이 아니다).

## 🔵 finance 다중 통화

본문에 «계좌 통화» 필드가 없다. 그래서 행이 **정확히 하나일 때만** 통화 칩을 보인다 — 먼저 온 행을 고르는 것은 **추측을 사실로** 내놓는 것이다. 빈 `data[]` 는 «잔액 정보 없음» 이 **정직한** 유일한 경우다(그 칸이 따로 있다).

---

# 🟢 AC-2 · AC-3 · AC-4 · AC-5 · AC-6 · AC-7

- **AC-2 고침** — 스키마 셋 + 렌더러 셋. AC-0 ① 칸 **9/9 초록**.
- **AC-3 양쪽을 묶었다** — `specs/contracts/fixtures/operator-overview-leg-bodies.json` **한 벌**을 **두 스위트가 같이 읽는다**: console-web 은 «카드가 이 본문으로 값을 그리는가», console-bff 는 «이 본문이 카드까지 **그대로** 가는가». 🔴 둘 중 하나만으로는 아무것도 못 막는다 — 렌더러만 재면 «아무도 안 보내는 모양» 이어도 초록이고, 통과만 재면 «렌더러가 읽는 것» 과 대조되지 않는다. **이번 결함이 정확히 그 둘 사이로 빠져나갔다.**
- **AC-4 샘플 동반 이동** — 샘플 카드 셋이 이제 producer 본문을 **그대로** 싣는다. 🔵 파생 계산이 사라졌고(`totalStockUnits` 합·`nodes[]` 조립), *"일부러 틀린 모양을 재현한다"* 던 주석 셋도 사라졌다. 🔴 `sample-overview-cards-match-lists` 는 **내 변경 직후에도 초록이었다** — 카드가 아니라 픽스처 상수를 읽기 때문이다. 그 테스트를 **동일성 비교**(`card.data == 그 질의의 본문`)로 바꿨다: 이제 카드는 질의가 바뀌지 않는 한 어긋날 수 없다.
- **AC-5 대조군** — 기본 계좌 **없는** 운영자(레그 short-circuit → `forbidden / MISSING_PREREQUISITE`)는 **무변화**. `finance-option-a-render.test.tsx` 의 그 칸과 degraded 칸을 **건드리지 않았고** 초록이다.
- **AC-6 WMS · SCM** — 위와 같이 처리. 🔵 **셋을 한 PR 로** 했다: 원인이 하나고(계약 예시), 전수 가드(AC-7)가 셋을 동시에 물어야 의미가 있어서다.
- **AC-7 전수 가드** — `CARD_ORDER` 를 돌며 **여섯 카드 전부**를 문다. 🔴 **새 레그가 픽스처에 칸을 안 만들면 빨개진다**(양쪽 스위트 모두 «선언된 여섯과 정확히 일치» 를 단언). 카드별 수정만으로는 네 번째를 못 막는다는 것이 이 AC 의 요지다.

## bite — 가드가 실제로 무는가

| 주입 | 결과 |
|---|---|
| bff 유스케이스에서 finance 본문을 `Map.of("balance", …)` 로 **가공** | 🔴 `OperatorOverviewLegBodyContractTest` **3칸 중 정확히 1칸** 빨강 (대조군 2칸 초록) |
| (원복 확인) | `git diff` 산출물 **0줄** |

## 게이트 (각각 독립 실행 · `rc` 명시 · 파이프 없음)

| 게이트 | rc | 실행 칸 |
|---|---|---|
| `npx vitest run` (console-web 전체) | **0** | **3525** (파일 315, skipped 0) |
| `npx tsc --noEmit` | **0** | — |
| `npx next lint` | **0** | 경고·오류 0 |
| `:console-bff:test` | **0** | **87** (skipped 0) |

🔴 **`rc=0` 을 증거로 쓰지 않았다** — 네 줄 모두 **실행된 칸 수를 따로 셌다**(gradle 은 결과 XML 을 파싱). 이 저장소가 `rc=0` 인데 43칸이 전부 `skipped` 인 경우를 이미 겪었다.

## ⚪ 남기는 것

- **라이브 원문 미확인**(AC-0 ②) — 창 예산. 🔵 다음 창에서 한 줄이면 된다: 운영자 로그인 → `/dashboards/overview` → finance·wms·scm 카드에 값이 있는가.
- **wms 알림 타일** — 위 갈래대로 제거했고, 되살리려면 두 번째 레그 호출이라는 **비용 결정**이 선행이다. 후속 티켓 기안은 **별도 spec PR** 로 낸다(PR Separation Rule — spec 과 impl 을 한 PR 에 묶지 않는다).
