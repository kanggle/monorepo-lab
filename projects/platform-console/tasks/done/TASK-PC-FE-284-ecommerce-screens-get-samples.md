# Task ID

TASK-PC-FE-284

# Title

E-Commerce 화면이 샘플로 선다 — 상품·주문·프로모션·판매자·정산·배송·회원·알림 템플릿 (`ADR-MONO-074` 실행 3/8)

# Status

done

# Owner

platform-console

# Task Tags

- code
- test
- demo

---

# Goal

`TASK-PC-FE-282` 샘플 모드 위에서 **ecommerce 도메인 GET 을 전부 `ready`** 로 만든다.

⏳ **`TASK-PC-FE-282` 머지 전 착수 금지.** 🔴 283~288 은 등록부·원장을 공유 ⇒ **직렬 머지**.

화면: `/ecommerce` · `/ecommerce/guide`(정적) · `/ecommerce/products` · `/products/new` · `/products/[id]` · `/products/[id]/edit` ·
`/ecommerce/orders` · `/orders/[id]` · `/ecommerce/promotions` · `/promotions/new` · `/promotions/[id]` · `/promotions/[id]/edit` ·
`/ecommerce/sellers` · `/sellers/new` · `/sellers/[id]` · `/ecommerce/settlements` · `/settlements/periods/[id]` ·
`/ecommerce/shippings` · `/ecommerce/users` · `/users/[id]` · `/ecommerce/notifications/templates` · `/templates/new` · `/templates/[id]/edit`

코어: `callEcommerceGateway`. ADR 인벤토리: GET **19** · 쓰기 **28**.

---

# Scope

## In Scope

- 위 화면의 ecommerce GET 픽스처 + 원장 `ready`
- 편집 화면(`…/edit`)의 **초기값 로드**(GET)까지 — 폼이 채워져 보여야 «무엇을 하는 화면인가» 가 보인다

## Out of Scope

- 저장·삭제·상태 전이 — `SAMPLE_READ_ONLY`
- 상품 이미지 **바이너리** 프록시(`/api/ecommerce/products/[id]/images/[imageId]`) — AC-7 참조

---

# Acceptance Criteria

- [x] **AC-0** 282 AC-0 표·원장에서 ecommerce `pending` GET 목록을 이 파일에 적는다. — § Implementation notes "AC-0 — 재인벤토리" (9 surface; ADR 19 는 다른 단위).
- [x] **AC-1** 전부 `ready` + 픽스처별 실제 파서 통과 테스트. — `tests/unit/sample-fixtures-schema-ecommerce.test.ts` (9 surface 전부, 실제 프로덕션 zod 스키마로 파싱).
- [x] **AC-2** «(샘플)» 규칙 테스트 초록(상품명·판매자명·회원명·템플릿 제목 끝). 🔴 금액·수량·주문번호·상태 enum 에는 없음. — `sample-label-rule.test.ts` 의 `it.each(SAMPLE_FIXTURE_DOCUMENTS)` 가 ecommerce 문서 9개를 순회, 초록. 새 키 분류는 `label-rule.ts` TASK-PC-FE-284 절, bite B14.
- [x] **AC-3** 목록 id ↔ 상세 일관(상품·주문·프로모션·판매자·정산 기간·회원). 주문 상세의 상품·회원 참조가 **같은 픽스처 세계** 안의 것. — world-consistency 테스트(black-box, `sampleResponse` 만 사용) + 404 테스트 7종, bite B15/B19.
- [x] **AC-4** 노출된 필터·검색·페이지가 픽스처 위에서 적용되고 총계가 실제 행 수와 같다. — products(status/categoryId) · orders(status) · users(email/status) · promotions(status) · shippings(status) · settlements accruals(sellerId/orderId) 각각 "부분집합·비지 않음·조건 만족" 3중 단언, bite B16.
- [x] **AC-5** 대표 쓰기 1개(예: 환불 승인) → «샘플 화면에서는 실행되지 않습니다». — `sample-fixtures-schema-ecommerce.test.ts` "AC-5" 절: POST 주문 상태변경 → 403 `SAMPLE_READ_ONLY` → `messageForCode` 매핑 문구 정확히 일치.
- [x] **AC-6** `e2e-smoke` 익명 `/ecommerce/orders` 렌더 1칸 + 주문 1건 상세 진입 1칸. — `e2e-smoke/sample-visitor-ecommerce.spec.ts`, `pnpm e2e:smoke` rc=0 (21 passed, 신규 2건 포함).
- [x] **AC-7** 상품 이미지: 🔴 MinIO 주소를 픽스처에 넣지 않는다(EC2 가 꺼지면 깨진 이미지 = 고장으로 보인다). 선택지 — 이미지 없음 표시 / 이미 공개인 CDN 주소 — 를 **골라서 이유와 함께** 적는다. — **선택: 이미지 없음.** § Implementation notes "AC-7 — 내 선택" 참조.

# Related Specs

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md`
- `TASK-PC-FE-282` (기반)
- `projects/platform-console/specs/services/console-web/architecture.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 (ecommerce)

# Edge Cases

- 편집 화면이 초기 GET 후 저장 → `SAMPLE_READ_ONLY`, 폼 값은 유지.
- 정산 기간 상세의 합계 = 행 합 (합성이어도 산술은 맞아야 한다 — 틀리면 «고장» 으로 읽힌다).

# Failure Scenarios

- 주문 상세가 없는 상품 id 를 참조 → 링크 404 ⇒ AC-3.
- 이미지 주소가 꺼진 오리진 → 깨진 이미지 ⇒ AC-7.

# Test Requirements

- `pnpm lint` · `npx tsc --noEmit` · `pnpm test` · `pnpm test:e2e:smoke`(각각 독립 + `rc=$?`)

# Definition of Done

- [x] 원장의 ecommerce `pending` 0 — `coverage.ts` `SURFACE_COVERAGE`/`SCREEN_COVERAGE` 의 ecommerce 행 전부 `ready` (9 surface + 22 screen).
- [x] 로그인 운영자 경로 무수정 초록 — 대조군은 이 티켓이 건드리지 않은 인증 경로 테스트 전부(무변화). `pnpm test` BEFORE(이 워크트리, `git stash`로 손대지 않은 상태) 304/304 files · 3261/3261 tests → AFTER(같은 워크트리, 최종) 305/305 · 3316/3316, 실패 0.

분석=Opus 5 / 구현 권장=Sonnet 5.

---

# Implementation notes (구현 에이전트, 2026-09-16 UTC)

## AC-0 — 재인벤토리

`TASK-PC-FE-282`/`283` 은 원장 granularity 를 엔드포인트가 아니라 **surface**(게이트웨이 프로필 `logPrefix`)로
잡았다(282 D1) — 경로가 래퍼 여러 겹을 거쳐 조립되어 엔드포인트 추출기가 추출기 자신을 재는 문제가 이
티켓에도 그대로 적용된다. `coverage.ts` 의 ecommerce `pending` 9 surface를 그대로 인용한다:

| surface | 화면 | GET (`method:'GET'` 리터럴 스윕) |
|---|---|---:|
| `ecommerce` (products, bare logPrefix) | `/ecommerce/products[/…]` | list · summary · detail(public) = 3 |
| `ecommerce_image` | product 상세에 embed | list = 1 |
| `ecommerce_order` | `/ecommerce/orders[/…]` | list · detail · summary · insights = 4 |
| `ecommerce_user` | `/ecommerce/users[/…]` | list · detail · summary = 3 |
| `ecommerce_promotion` | `/ecommerce/promotions[/…]` | list · detail · summary = 3 |
| `ecommerce_shipping` | `/ecommerce/shippings` | list · summary = 2 (detail-by-id 없음 — producer 미정의) |
| `ecommerce_notification` | `/ecommerce/notifications/templates[/…]` | list · detail · summary = 3 |
| `ecommerce_seller` | `/ecommerce/sellers[/…]` | list · detail · summary = 3 |
| `ecommerce_settlement` | `/ecommerce/settlements[/…]` | accruals · seller-balance · commission-rate · periods · period-payouts = 5 |

🔴 ADR 의 **19** 는 코드 읽기 스윕(호출 함수 이름 단위, `/ecommerce` 카운트 6종 read 포함)이고, 위 27 은 메서드
리터럴 스윕(282/283 실측과 같은 단위) — 단위가 다르다(282 § AC-0 가 이미 그렇게 적어 뒀다). 이 티켓의 **실제
범위는 원장의 ecommerce `pending` 9 surface**이고 ADR 의 19 와 직접 비교하지 않는다. 원장 `SCREEN_COVERAGE`
의 ecommerce `pending` 화면도 22개였다(작업지시서 나열과 대체로 일치 — `/settlements/periods/[id]` 는 실제
코드 경로가 `/ecommerce/settlements/periods/[id]`).

## 스크린 ↔ 서페이스 매핑 (구현 중 실측)

- `/ecommerce` (landing) → `products`+`orders`+`shippings`+`promotions`+`users`+`sellers`+`notifications` 의
  7개 `/summary` fan-out (`getEcommerceOverviewState`, TASK-PC-FE-164/172) + `orders` 상태분포 + `orders/insights`
  + `sellers` 목록(최근활동 + 이름맵) — 콘솔이 직접 fan-out 하는 구조(§ 2.4.10.6, console-bff 없음)라 새 픽스처가
  아니라 **7개 도메인 픽스처의 조합**으로 이미 `ready`.
- `/ecommerce/products`, `/products/new`, `/products/[id]`, `/products/[id]/edit` → `ecommerce`(products) +
  `ecommerce_image`(상세에 embed된 이미지 관리자).
- `/ecommerce/orders`, `/orders/[id]` → `ecommerce_order`.
- `/ecommerce/promotions`, `/promotions/new`, `/promotions/[id]`, `/promotions/[id]/edit` → `ecommerce_promotion`.
- `/ecommerce/sellers`, `/sellers/new`, `/sellers/[id]` → `ecommerce_seller`.
- `/ecommerce/settlements`, `/settlements/periods/[id]` → `ecommerce_settlement`.
- `/ecommerce/shippings` → `ecommerce_shipping`.
- `/ecommerce/users`, `/users/[id]` → `ecommerce_user`.
- `/ecommerce/notifications/templates`, `/templates/new`, `/templates/[id]/edit` → `ecommerce_notification`.
- `/ecommerce/guide` → 이미 302 소유(static, 변경 없음).

## 픽스처 세계 (하나로 엮은 데이터)

셀러 3(ACTIVE·PENDING_PROVISIONING·SUSPENDED) · 상품 3(ON_SALE·SOLD_OUT·HIDDEN, 각각 셀러 참조) · 회원
3(ACTIVE·SUSPENDED·WITHDRAWN — WITHDRAWN 은 email/name/nickname 전부 `null`, TOLERANCE 회귀 방지) · 주문
4(PENDING·CONFIRMED·SHIPPED·CANCELLED, 각 라인이 실제 상품/셀러를 참조하고 각 주문이 실제 회원을 참조) ·
프로모션 3(ACTIVE·ENDED·SCHEDULED) · 배송 3(PREPARING·SHIPPED·DELIVERED, 각각 실제 주문을 참조) · 알림
템플릿 3(ORDER_PLACED/EMAIL·SHIPPING_STATUS_CHANGED/SMS·WELCOME/PUSH) · 정산: 커미션율 3 · 셀러잔액 3 ·
적립라인 5(REVERSAL 클로백 1건 포함, 실제 두 셀러의 잔액과 정확히 합산 일치) · 기간 2(CLOSED 1 · OPEN 1) ·
지급 2(CLOSED 기간에만, `sellerCount` = 지급 행 수와 일치).

## AC-7 — 내 선택 (소유자가 한 줄로 뒤집을 수 있다)

**선택: 이미지 없음.** `thumbnailUrl`/`profileImageUrl` = `null` 고정, `listImages()` = `{ images: [] }` 고정
(모든 상품). **이유**: 이미 공개된 CDN 주소를 하나 고르더라도 그 주소가 나중에 만료·차단되면 "깨진 이미지 =
고장"으로 보이는 것은 똑같다(과제 Failure Scenario 가 명시) — 반면 "이미지 없음"은 화면에 이미 있는 정상
placeholder 상태이고 외부 의존이 전혀 없다. 코드 확인: `ProductsTable`은 `thumbnailUrl`을 아예 읽지 않고,
`ProductDetail`도 마찬가지이며, `ImageManager`는 빈 `images:[]`를 "표시할 이미지가 없다"는 기존 empty 상태로
그린다 — 추가 코드 변경 0. 뒤집으려면: `fixtures/ecommerce.ts` 의 `thumbnailUrl`/`profileImageUrl` 값과
`imagesFixture()` 의 반환값만 바꾸면 된다(다른 코드 변경 없음).

## 편차 / 구현자 선택

- **D1 정산 도메인의 "합계 = 행 합" 해석.** 이 티켓의 Edge Case("정산 기간 상세의 합계 = 행 합")를 문자 그대로
  적용할 화면 내 합계 표시가 없다(`PeriodPayoutsScreen`/`PeriodPayoutsTable`은 개별 지급 행만 보여주고 합계
  줄이 없다). 그래서 **이 도메인에서 교차검증 가능한 유일한 "합계"** — CLOSED 기간의 `sellerCount` = 그 기간의
  실제 지급 행 수 — 로 해석했다(테스트 + bite B18). 별도로 셀러 정산잔액(`SellerBalanceLookup`)은 그 셀러의
  적립 라인(`AccrualsSection`) 합계와 정확히 일치하도록 만들고 교차검증 테스트를 추가했다(commission rate
  bps 적산까지 포함, bite B17) — 이것이 "money 는 반드시 맞아야 한다"의 더 강한 실제 적용이다.
- **D2 주문 인사이트(§ 2.4.10 #21) 셀러 라벨.** 계약은 "seller label = raw seller_id"(콘솔이 클라이언트에서
  `overview-state.ts`의 `overlaySellers()`로 실제 이름을 덮어씀)라고 적지만, `label` 키는 R2ⓐ 라벨 가드에서
  전역적으로 "사람이 읽는 문자열"로 분류되어 있어(상품명 랭킹도 같은 키를 쓴다) raw id 를 그대로 넣으면
  `missing-suffix` 위반이 된다. 이 픽스처는 전부 합성(ADR-MONO-074 A4 — 실제 producer 를 바이트 단위로
  모사할 필요가 없다)이고 `overlaySellers()`는 `id` 로 찾아 **무조건 덮어쓰므로**, 이미 해석된 셀러
  `displayName`(접미 포함)을 미리 넣어 두는 것이 모든 소비자에게 **행동상 동일**(덮어써도 같은 값)하면서
  원장 문서 자체도 R2ⓐ를 만족한다. `ecommerce.ts`의 `computeInsights()` 문서 주석에 근거를 남겼다.
- **D3 users 이메일 검색 = SUBSTRING 매치(IAM accounts 의 EXACT 매치와 다름).** IAM `iam:accounts`(TASK-PC-FE-283
  D5)는 시드 이메일이 전부 `sample`/`.example` 어휘를 공유해 부분일치가 "좁혀지지 않음"을 만드는 함정이 있었다.
  ecommerce users 화면은 "이메일 검색" 자유 텍스트 박스(단건 조회가 아니라 목록 필터)라 실제 producer 동작도
  narrowing 이지 identity match 가 아니다 — 그래서 SUBSTRING(대소문자 무시, 접미 제거) 을 택했고, "hana" 로
  1건만 좁혀지는 것으로 AC-4 를 만족시켰다(전체 3건이 전부 "sample" 을 공유하는 IAM 과 달리 users 의 시드
  이메일은 이름별로 구분되어 "sample" 전체매치 위험이 없다).
- **D4 라벨 규칙 신규 키.** `optionName`·`productName`·`firstItemName`·`nickname`·`subject`·`recipient`·
  `address1`·`address2` 를 human-readable 로, `discountType`·`channel`·`trackingNumber`·`carrier`·
  `payoutReference`·`phone`·`zipCode`·`startDate`·`endDate`·`from`·`to`·`objectKey`·`url`·`thumbnailUrl` 를
  machine 으로 분류했다(`label-rule.ts` TASK-PC-FE-284 절에 각각 이유 주석). `phone`/`zipCode`는 값 자체가
  합성이라 AC-7 을 만족하지만 「(샘플)」 접미를 붙이면 파싱 대상은 아니어도 사람이 읽는 형식(전화번호·우편번호)이
  깨져 보여서 machine 으로 분류했다(구현자 선택, 소유자가 뒤집을 수 있다).
- **D5 `updatedAt` 은 `.optional()`이지 `.nullable()`이 아니다 (`UserDetailSchema`).** `email`/`name`/`nickname`/
  `phone`/`profileImageUrl` 과 달리 `updatedAt`은 producer 스키마상 nullable 이 아니어서 시드에서 `null`을 쓰면
  파싱이 깨진다(구현 중 첫 `pnpm test`에서 잡힘 — 아래 § 측정 참고). `undefined`(JSON 직렬화 시 필드 자체가
  사라짐)로 고쳤다.
- **D6 shippings 는 detail-by-id GET 이 없다** — producer 가 정의하지 않음(§ 2.4.10.3, `shippings-api.ts` 에
  `getShipping()` 이 없다). 그래서 이 surface 만 유일하게 "AC-3 목록 id ↔ 상세" 테스트가 없다 — 대신 list 필터
  (status) 테스트로 AC-4 를 만족시켰다.
- **D7 settlements 의 `orderId`는 orders 픽스처 세계와 독립.** 적립 라인 4건 중 1건(`accrual-sample-0004`)은
  `order-settlement-0001` 이라는, `ecommerce_order` 픽스처에는 없는 id 를 쓴다 — AC-3 의 "주문 상세의 상품·회원
  참조" 요구는 **주문 → 상품/셀러/회원** 방향이지 **정산 적립 라인 → 주문** 방향이 아니며(정산 화면은 주문
  상세로 드릴스루하지 않는다, `AccrualsTable`이 orderId 를 텍스트로만 보여준다), 이 결정을 § 문서 주석에
  남겼다 — 뒤집으려면 그 한 줄만 실제 orders 픽스처의 id 로 바꾸면 된다.

## 기존 테스트 파일 4개 수정 — 2종

**(a) 실제로 빨간 상태에서 잡은 것 (수정 전 `pnpm test` 1회, 2 파일 실패):**

| # | 파일 | 원인 | 고침 |
|---|---|---|---|
| 1 | `sample-fixtures-schema-ecommerce.test.ts` › users AC-3 | `UserDetailSchema.updatedAt` 이 `.nullable()` 아님(내가 쓴 새 테스트가 내가 쓴 새 픽스처의 버그를 잡음) | D5 — `null` → `undefined` |
| 2 | `sample-label-rule.test.ts` › 예시 self-check | `nickname` 을 이 티켓이 human-readable 로 새로 분류해 self-check 의 "미분류 키" 예시가 더 이상 미분류가 아니게 됨 | 예시 키를 `mysteryField` 로 교체(단언 불변, 282/283 D8 과 같은 종류의 재발 — 헤더에 이 티켓 인용) |

**(b) 282/283 D8 선례를 따라 구현 전에 미리 고친 것** (ecommerce 를 `ready` 로 바꾸면 반드시 깨질 것이 코드
읽기로 예측되어, 실제로 빨개지는 것을 기다리지 않고 먼저 고쳤다 — 두 파일 모두 고친 뒤 실행에서 그 셀만 놓고
보면 초록이었다는 것은 확인했으나, "먼저 고쳤다"는 사실 자체는 여기 정직하게 구분해 남긴다):

| # | 파일 | 원인 | 고침 |
|---|---|---|---|
| 3 | `sample-mode-cores.test.ts` › callEcommerceGateway pending GET | `ecommerce_order` 가 이 티켓에서 ready 로 바뀜 | 282/283 D8 과 동일 패턴 — `logPrefix: 'no-such-surface'` 로 교체(단언 불변) |
| 4 | `sample-coverage-ledger.test.ts` › 동적 세그먼트 해석 | `/ecommerce/orders/[id]` 가 ready 로 바뀌어 `screenStatusFor('/ecommerce/orders/ord-1')` 기댓값이 `pending`→`ready` | 이 칸의 주제(동적 `[id]` 세그먼트가 자기 원장 행으로 해석됨)는 안 바뀜, 값만 바뀜 — ADR/티켓 인용 주석 추가(유일하게 `expect(` 리터럴을 바꾼 곳; 282/283 의 "빈 병=샘플" 재분류와 달리 이 칸은 **메커니즘이 아니라 데이터가 바뀐** 경우라 리터럴 교체가 맞는 처방이라고 판단했다) |

## ⚪ 측정하지 못한 것 / 실제로 실행되지 않는 것

- **`/ecommerce/products/new` · `/promotions/new` · `/sellers/new` · `/templates/new`** — 순수 폼 화면(초기 GET
  없음, 과제 In Scope 는 `…/edit` 만 요구)이라 새 픽스처 없이 이미 화면 원장만 `ready` 로 뒤집으면 된다(등록부
  eligibility 는 282 부터 이미 통과). 별도 검증 불필요.
- **operator-groups/org-hierarchy 류의 클라이언트 상호작용 하위 자원**에 대응하는 ecommerce 쪽 항목은 없다
  (이 도메인은 그런 하위 자원이 없음 — 셀러 lifecycle 액션은 쓰기라 AC-5 로 충분히 덮인다).
- **실제 Vercel 배포에서 샘플 방문자** — 로컬 production build + smoke 까지만 쟀다(283 과 동일 한계).

## 측정 (이 워크트리 · Windows 호스트 · 각 게이트 독립 실행 + 명시 rc)

BEFORE 는 **이 워크트리를 `git stash`로 되돌린 상태**(분기점과 동일 HEAD `23f416e64`)에서 쟀다 — 코디네이터
지시(§ 5)대로 "이 워크트리에서, 다른 체크아웃이 아니라" 쟀다.

| 게이트 | 트리 | 결과 |
|---|---|---|
| `pnpm test` | BEFORE(`git stash`로 되돌린 이 워크트리) | rc=0 · **304 files / 3261 tests passed**, 실패 0 |
| `pnpm lint` | AFTER(최종) | rc=0 · «No ESLint warnings or errors» |
| `npx tsc --noEmit` | AFTER(최종) | rc=0 |
| `pnpm test` (1차 — #3·#4 를 미리 고친 뒤 첫 실행) | AFTER | rc=1 · 2 files / 2 failed — 위 § "(a) 실제로 빨간 상태에서 잡은 것" #1·#2 |
| `pnpm test` (최종) | AFTER | rc=0 · **305 files / 3316 tests passed**, 실패 0 |
| `pnpm build` | AFTER | rc=0 |
| `pnpm e2e:smoke` | AFTER(프로덕션 빌드, 백엔드 전부 loopback 127.0.0.1:1) | rc=0 · **21 passed** — 기존 19개 + 신규 `sample-visitor-ecommerce.spec.ts` 2개(AC-6) |

🔵 알려진 Windows flake(`LedgerOpsScreen.test.tsx`/`OperatorsScreen.test.tsx`)는 BEFORE·AFTER 두 실행 모두
재현되지 않았다(둘 다 실패 0).

### Bites (금지/결함 코드를 넣어 빨강 → 되돌려 복원 트리에서 초록)

| # | 가드 | 주입 | 빨강 | 복원 후 |
|---|---|---|---|---|
| B13 | `sample-fixtures-schema-ecommerce.test.ts` AC-1 (products 스키마) | `price` 를 문자열로 | rc=1 · 7 files 단언 실패(파싱 연쇄 실패 — totalElements/필터/summary/detail/AC-7/world-consistency) | rc=0 · 46/46 |
| B14 | `sample-label-rule.test.ts` (D4 — 셀러 displayName 접미 제거) | `SUFFIX` 제거 | rc=1 · `ecommerce:ecommerce_seller`+`ecommerce:ecommerce_order`(insights 라벨 재사용) 양쪽 `missing-suffix` | rc=0 · 30/30 |
| B15 | `sample-fixtures-schema-ecommerce.test.ts` AC-3 world-consistency | order-1 의 `productId` 를 존재하지 않는 id 로 | rc=1 · world-consistency + insights(라벨 조회 실패로 연쇄) 2건 | rc=0 · 46/46 |
| B16 | `sample-fixtures-schema-ecommerce.test.ts` AC-4 (products status 필터) | status 필터 줄 주석 처리 | rc=1 · `expected 3 to be less than 3`(narrowing 실패) | rc=0 · 46/46 |
| B17 | `sample-fixtures-schema-ecommerce.test.ts` money (정산 잔액 합산) | seller-0001 `grossMinor` 를 999999 로 | rc=1 · `expected 999999 to be 380000` | rc=0 · 46/46 |
| B18 | `sample-fixtures-schema-ecommerce.test.ts` "합계=행 합" (D1) | period-1 `sellerCount` 를 999 로 | rc=1 · `expected 999 to be 2` | rc=0 · 46/46 |
| B19 | `sample-fixtures-schema-ecommerce.test.ts` AC-3 404 (orders) | `fixtureNotFound(...)` 대신 `undefined` 반환 | rc=1 · `expected 503 to be 404` | rc=0 · 46/46 |
| B20 | `sample-coverage-ledger.test.ts` (283 이 만든 기존 가드 — 우리 새 행도 잡는지 독립 확인) | `SURFACE_SAMPLE_PATH['ecommerce:ecommerce']` 항목 삭제 | rc=1 · `ecommerce:ecommerce (ready)` 200 기대가 503 + "explicit SURFACE_SAMPLE_PATH entry" 가드 위반 | rc=0 · 45/45 |

B13–B20 모두 단독 주입 → 실행 → 복원 순으로 개별 확인했다. `grep -rn "BITE-" src tests e2e-smoke` = 0건
(복원 확인). 전체 스위트 기준 최종 복원 확인은 § 측정의 "최종" 행.

## CORRECTION — 코디네이터 리뷰: 정산 라인 금액이 주문 화면과 어긋남 (2026-09-16 UTC)

🔴 **결함**: `settlement-types.ts`의 `minorToWon`은 minor unit 을 그대로 원(₩)으로 그린다(KRW 는 scale 0).
그런데 최초 구현의 적립 라인 `grossMinor` 는 참조하는 주문의 실제 합계와 **무관한 별개 숫자**였다 —
`accrual-sample-0001`(order-sample-0001 참조)의 gross 는 200,000 인데 그 주문의 `/ecommerce/orders` 합계는
38,000; `accrual-sample-0004`는 아예 픽스처 세계에 없는 `order-settlement-0001`을 참조했다. 이 티켓 자신의
Edge Case("합성이어도 산술은 맞아야 한다 — 틀리면 «고장» 으로 읽힌다")가 정확히 지목하는 결함이고, 방문자가
주문을 열어 본 뒤 그 정산 라인을 열면 같은 돈이 다른 숫자로 보인다.

### 고침

1. **`order-settlement-0001` 제거** — 새 주문 `order-sample-0005`(멀티셀러: item 1 = seller-sample-0001 몫
   19,000, item 2 = seller-sample-0002 몫 24,000, 합계 43,000)를 `ECOMMERCE_ORDERS`에 추가하고 그 주문을
   참조하도록 했다(추가 쪽을 선택 — 기존 4개 주문은 이미 각자의 적립 라인과 1:1로 대응되어 있어 재사용하면
   "한 주문에 같은 셀러의 적립이 두 번" 이라는 새 결함을 만들었을 것이다). 이 주문은 동시에 "주문의 아이템이
   여러 셀러에 걸칠 수 있고, 그러면 적립은 셀러별" 이라는 코디네이터 요청 문장의 실증 사례가 됐다.
2. **모든 ACCRUAL 의 `grossMinor` = 그 주문(또는 멀티셀러 주문에서는 그 셀러 몫)의 실제 합계**로 재계산:
   accrual-1→38,000(order-1 전체) · accrual-2→45,000(order-2 전체) · accrual-3(REVERSAL)→-10,000(order-2
   누적 gross 45,000 이하의 부분 환불) · accrual-4→19,000(order-5 의 seller-1 몫) · accrual-5→36,000(order-3
   전체) · accrual-6(신규)→24,000(order-5 의 seller-2 몫). CANCELLED 인 `order-sample-0004` 를 참조하는 적립은
   0건(취소된 주문은 커미션이 발생하지 않는다).
3. **커미션/셀러정산액/잔액/지급 전부 재계산**(정수 bps 적산, 반올림 없이 나누어떨어짐):
   - seller-sample-0001(10%): gross 38,000+45,000-10,000+19,000=**92,000**, commission **9,200**,
     net **82,800**, accrualCount **4**.
   - seller-sample-0002(12%): gross 36,000+24,000=**60,000**, commission **7,200**, net **52,800**,
     accrualCount **2**.
   - seller-sample-0003: 활동 없음, 0/0/0/0 (무변화).
   - `period-sample-0001` 지급 2건도 위 net/commission/accrualCount 로 갱신, `sellerCount`(2) = 지급 행
     수(2) 불변 확인.
4. **새 테스트** `sample-fixtures-schema-ecommerce.test.ts`의 "CORRECTION (coordinator review) — every
   accrual's orderId exists in the order fixtures, and each ACCRUAL's grossMinor equals that SELLER's
   subtotal on that order" — `sampleResponse` 만으로 적립→주문을 순회해 ⓐ `orderId` 가 실제 주문 목록에
   존재 ⓑ CANCELLED 주문을 참조하지 않음 ⓒ ACCRUAL gross = 그 셀러의 그 주문 라인 합 ⓓ REVERSAL 절대값 ≤
   그 셀러가 그 주문에서 이미 누적한 gross, 4가지를 단언한다. **bite B21**: `accrual-sample-0001`의 gross
   를 200,000 으로 주입 → 이 새 테스트 + 기존 "money adds up" 테스트 둘 다 빨강(rc=1, 2 files 단언 실패) →
   복원 → rc=0 47/47. `grep -rn "BITE-" src tests e2e-smoke` = 0건.

### 전/후 표 — 적립 → 주문 금액

| accrual | orderId | 전: grossMinor | 그 주문(또는 셀러 몫) 합계 | 후: grossMinor |
|---|---|---:|---:|---:|
| accrual-sample-0001 | order-sample-0001 | 200,000 | 38,000 | **38,000** |
| accrual-sample-0002 | order-sample-0002 | 150,000 | 45,000 | **45,000** |
| accrual-sample-0003 (REVERSAL) | order-sample-0002 | -20,000 | (≤45,000 누적) | **-10,000** |
| accrual-sample-0004 | ~~order-settlement-0001~~ → **order-sample-0005** | 50,000 | 19,000(seller-1 몫) | **19,000** |
| accrual-sample-0005 | order-sample-0003 | 120,000 | 36,000 | **36,000** |
| accrual-sample-0006(신규) | order-sample-0005 | — | 24,000(seller-2 몫) | **24,000** |

### 부수 효과 — insights 랭킹 숫자도 갱신

`order-sample-0005` 추가로 `/ecommerce` 개요의 랭킹 차트 숫자가 바뀌었다(테스트 갱신, 단언 로직은 불변):
`topProductsByRevenue`의 `prod-sample-0001` 38,000→**57,000**(order-1 38,000 + order-5 item-1 19,000),
`topSellersByRevenue`의 `seller-sample-0001` 83,000→**102,000**(order-1 38,000 + order-2 45,000 + order-5
item-1 19,000). `ORDERS_SUMMARY.total`/`.month` 도 4→**5**.

### 게이트 (이 워크트리, 각각 독립 실행 + 명시 rc)

| 게이트 | 결과 |
|---|---|
| `pnpm lint` | rc=0 · «No ESLint warnings or errors» |
| `npx tsc --noEmit` | rc=0 |
| `pnpm test` | rc=0 · **305 files / 3317 tests passed**(교정 전 305/3316 대비 +1 신규 테스트), 실패 0 |

`pnpm build`/`pnpm e2e:smoke` 는 재실행하지 않았다 — 화면 렌더링 코드나 e2e 스펙을 건드리지 않았고(픽스처
데이터 + 테스트 파일만 변경), 코디네이터 지시 § 6 이 명시적으로 재실행을 요구하지 않는 경우로 판단했다.

## CORRECTION — 닫기 판정 (조정자, 2026-09-16 UTC)

🔴 위 AC 절의 체크박스는 `[ ]` 로 남아 있다 — `review/` 파일은 동결이다. **체크박스가 아니라 아래 표가 판정이다.**
🔴 **본문 § 편차 D7(«정산 적립의 `orderId` 는 주문 세계와 독립») 은 바로 위 CORRECTION 으로 뒤집혔다** — 지금 참인 것은
«모든 적립이 실제 주문을 가리키고, 적립 금액 = 그 주문의 그 판매자 몫» 이다. D7 만 읽고 되돌리지 말 것.

머지 검증 4차원: (a) PR [#3872](https://github.com/kanggle/monorepo-lab/pull/3872) `state=MERGED` 2026-09-16T16:19:46Z ·
(b) squash `66a994df1` 가 `origin/main` 조상(머지 시점의 끝) · (c) 머지 전 롤업 **61 체크 · FAILURE 0**, 필수 4 + 프런트 unit ·
E2E smoke · console-bff IT **실제 실행** · (d) 아래 표.
🔵 (c) 의 전사: 첫 푸시 뒤 CI 가 **0 건** 이었다(`mergeable=UNKNOWN` — main 이 두 번 움직여 INDEX 충돌). 두 번 `main` 을 병합해
충돌(`tasks/INDEX.md` 만)을 풀었고, 병합 트리에서 조정자가 lint · tsc · vitest(308 files 통과) · 필수 가드 3종을 다시 돌렸다.
머지 판정에 쓴 61 체크는 **마지막 병합 커밋**의 롤업이다.

| AC | 닫힘 | 증거 |
|---|---|---|
| AC-0 | ✅ | 표면 9 · 화면 22 · GET 메서드 리터럴 27. ADR 의 19 는 단위가 달라 직접 비교하지 않음 |
| AC-1 | ✅ | `sample-fixtures-schema-ecommerce.test.ts` — 9 표면을 화면이 쓰는 실제 zod 스키마로 파싱(router 경유) · bite B13 |
| AC-2 | ✅ | 라벨 가드 초록 · 새 키 22개 분류와 사유(D4) · bite B14 |
| AC-3 | ✅ | 주문→상품·판매자·회원 참조가 픽스처 세계 안에서 해석(B15) · 없는 id → 실제 404(B19). **조정자 실측**: 주문 전부 목록 합계 = 상세 합계 = Σ수량×단가 |
| AC-4 | ✅ | 필터 6종이 픽스처 위에서 좁혀짐(B16) · 총계 = 필터 후 행 수 |
| AC-5 | ✅ | 주문 상태 변경 POST → 403 `SAMPLE_READ_ONLY` → `messageForCode` 문구 |
| AC-6 | ✅ | `e2e-smoke/sample-visitor-ecommerce.spec.ts` 2칸 · 로컬 21 passed · PR CI E2E smoke SUCCESS(주문 5건 수정 반영 트리). 스펙은 건수를 세지 않는다(조정자 확인) |
| AC-7 | ✅ | «이미지 없음» 선택과 이유 · 픽스처에 MinIO/EC2/외부 오리진 0건(조정자 grep) |
| Edge «합계 = 행 합» | ✅ | 정산 기간 상세엔 합계 줄이 없어 D1 로 해석 · **실제 산술은 조정자가 따로 검산**: 판매자1 38,000+45,000−10,000+19,000 = 92,000 / 수수료 10% 9,200 / 순액 82,800 / 4건, 판매자2 36,000+24,000 = 60,000 / 12% 7,200 / 52,800 / 2건 — 잔액·지급 행과 일치 |
| DoD | ✅ | 원장 ecommerce `pending` 0 · 인증 운영자 칸 무수정. 기존 테스트 4개 변경 중 `expect(` 리터럴이 바뀐 곳 2줄(원장 동적 세그먼트 `pending→ready` · 라벨 «미분류 키» 예시 `nickname→mysteryField`) — 둘 다 주제 불변, 데이터/분류가 바뀐 결과(조정자 diff 실측) |

🔴 **조정자 리뷰가 잡은 결함 1건** — 같은 주문이 주문 화면 ₩38,000 · 정산 화면 ₩200,000 으로 보였다(위 CORRECTION).
에이전트 최초 보고의 AC 표는 이것을 «✅» 로 두었다: **에이전트가 세운 테스트는 각 화면 안의 산술만 쟀고, 화면 사이는 재지 않았다.**
지금은 적립→주문을 router 로 순회하는 테스트(B21)가 이 간극을 문다.

🔵 **남는 것**: 신규 `…/new` 폼 4종은 초기 GET 이 없어 픽스처가 필요 없다 · 실제 Vercel 배포의 익명 화면은 미측정(로컬 production build + smoke 까지).
넘길 의무 **0건**.
