# Task ID

TASK-MONO-807

# Title

`ADR-MONO-084` P2 — E-Commerce 실적 지표: 매출 · 판매 수량 · 주문 수(`CONFIRMED` 이후 · `paidAt` 귀속 · 환불 제외) + `/insights` 순위 기준 교정 + 콘솔 개요 실적 줄

# Status

review

# Owner

monorepo

# Task Tags

- code
- api
- ecommerce
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (매출 인식 기준 · 집계 쿼리 · 기존 순위 숫자 변경)
> 🔴 **cross-project 원자 PR**: order-service 계약 · 엔드포인트와 콘솔 화면을 한 PR 에 싣는다.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1 공용 DTO · 경계 · 콘솔 컴포넌트) `main` 머지.
- 형제: `TASK-MONO-808`~`812`.

# Goal

E-Commerce 개요(`/ecommerce`) 맨 위에 이번 주 · 이번 달 **매출 · 판매 수량 · 주문 수**와 직전 대비 · 일별 추이를 보여 준다. 같은 기준으로 기존 상위 5 순위도 고친다(지금은 기간 없는 누적 + `PENDING` 포함 — `OrderJpaRepository.java:160-176`).

# Scope

## In Scope

- 계약 먼저: `order-api.md` 에 `GET /api/admin/orders/metrics?period=week|month` (P1 공용 모양) · 지표 `revenue` · `units` · `orders` 각각의 **포함 상태 · 귀속 시각 · 제외** 한 줄씩(ADR-084 D5).
  - 포함: `CONFIRMED` · `SHIPPED` · `DELIVERED`. 귀속: `paidAt`. 제외: `PENDING` · `CANCELLED` · 환불(`refundedAt` 있음).
  - OD-1 기본값: `BACKORDERED` 는 넣지 않는다. `CONFIRMED` 가 되면 그 주문의 `paidAt` 날짜로 들어간다 → **지난 날의 값이 나중에 바뀔 수 있다**고 계약에 적는다.
  - OD-2 기본값: 할인 후 결제 금액.
- order-service: 집계 쿼리(테넌트 범위 · 판매자 범위 규칙이 있으면 그대로) · 컨트롤러 · `ADMIN` 인가(기존 admin 엔드포인트와 같은 게이트).
- `/insights` 교정: 같은 포함/제외 기준 · `period` 파라미터(기본값은 AC-0 에서 정한다 — 지금 화면이 기간 없이 부른다).
- 콘솔: `features/ecommerce-ops` 개요에 P1 «실적 줄» 붙이기 · 순위 차트에 기간 표시.

## Out of Scope

- 다른 영역(상품 · 배송 · 프로모션 …)의 `/summary` 건수 — 그대로 둔다.
- 정산 · 결제 서비스의 금액 집계.

# Acceptance Criteria

- [x] **AC-0** — 재측정: `totalPrice` · `discountAmount` · `unitPrice * quantity` 의 실제 의미(할인 전/후 · 배송비 포함 여부)와 셋의 관계 · `paidAt` 이 채워지는 경로(모든 `CONFIRMED` 주문에 있는가) · 환불 시 상태 전이(`refundedAt` 과 `CANCELLED` 의 관계) · 판매자 범위 규칙 · `/insights` 소비자 전수. 결과 → 이 파일 + 계약 문구. — ✅ § AC-0 기록 · `order-api.md` § `/metrics` · `/insights`.
- [x] **AC-1** — 🔴 대조군(제외 쪽 먼저): `PENDING` · `CANCELLED` · 환불 · `BACKORDERED` 주문을 넣어도 `revenue` · `units` · `orders` 가 안 움직인다. `CONFIRMED` 하나를 넣으면 움직인다. — ✅ `OrderMetricsQueryIntegrationTest.only_realised_sales_count`(실 PostgreSQL): 상태 8종을 서로 다른 금액으로 한 테넌트에 넣고 `CONFIRMED` · `SHIPPED` · `DELIVERED` 의 금액만 나오는지(새 금액이 끼면 어느 상태가 샜는지 금액으로 보인다) · 순위도 같은 셋. 🔴 Docker 미기동 — **판정 = CI `Integration (ecommerce …)` 잡**(§ 검증).
- [x] **AC-2** — 귀속 시험: `createdAt` 은 지난주, `paidAt` 은 이번 주인 주문 → 이번 주에 잡힌다. KST 00:00 경계 직전/직후(`Clock` 주입). — ✅ IT `attributed_to_paid_at_half_open`(생성 지난주 · 결제 안 → 잡힘 / 생성 안 · 결제 전 → 안 잡힘 / `from` 정각 포함 · `to` 정각 제외 / `paidAt` 없는 `CONFIRMED` 제외) + 단위 `OrderQueryServiceMetricsTest`(KST 00:10 결제 → UTC 날짜가 아니라 KST 날짜 행 · `Clock` 고정 01:30 KST). 🔴 bite: 날짜 자르기를 UTC 로 바꾸면 `[nothing on the UTC date] expected "0" but was "1"` → 복원 후 초록.
- [x] **AC-3** — 다른 테넌트의 주문은 안 잡힌다. — ✅ IT `tenant_isolation`(지표 · 순위 둘 다).
- [x] **AC-4** — `/insights` 교정 전후 값을 같은 데이터로 기록한다(줄어든 숫자 = 기준 교정, ADR-084 Consequences). — ✅ 샘플 시드(콘솔 `shared/sample/fixtures/ecommerce.ts`, 주문 6건)로: `prod-sample-0001` 매출 **57000 → 19000**(미결제 `PENDING` order-1 38000 빠짐) · `seller-sample-0001` 매출 **102000 → 64000**(같은 이유) · `BACKORDERED` order-6 은 전에도 지금도 빠짐. `sample-fixtures-schema-ecommerce.test.ts` 기대값을 바꾸고 이유를 주석에 적었다. ⚪ 라이브 데이터 전후 값은 데모 창이 필요(AC-6).
- [x] **AC-5** — 콘솔: 실적 줄 렌더 · 칸 실패 시 현재 상태 블록이 선다 · 401 재로그인. 익명 샘플 모드(ADR-MONO-074)가 이 새 호출에 샘플 응답을 준다. — ✅ `ecommerce-overview.test.tsx`(실적 줄이 맨 위 · 라벨 · 기간 링크 · 실패해도 영역 카드 · 순위 기간 표시) · `ecommerce-overview-state.test.ts`(기간이 두 다리에 · 503 은 그 칸만 · 403 · 401 → 재로그인) · `ecommerce-page.test.tsx`(`?period=week` 전달 · 모르는 값 → 기본) · `sample-ecommerce-metrics.test.ts`(샘플 창 = `kstMetricsWindows(SAMPLE_AS_OF)` · 계약 모양 · 확정 주문만 · `/metrics` 가 주문 id 로 오인되지 않음 · 기간 순위).
- [ ] **AC-6** — 배포 상태(`main` · AMI · 데모 창 라이브)를 따로 적는다. 라이브가 없으면 ⚪ 와 이유. — § 배포 상태. ⏳ `main` = 이 PR 머지 · AMI ⚪(다음 굽기 — order-service 이미지와 `V13` 인덱스가 실려야 한다) · 라이브 ⚪(데모 창).

## AC-0 기록 (2026-10-23 UTC, origin/main `bc88fe5a0`)

- **금액 셋의 관계**: `OrderJpaEntity.totalPrice` = **쿠폰 할인이 이미 빠진** 결제 금액(`discount_amount` javadoc: «`total_price` is already net of it») · `discount_amount` = 주문 단위 쿠폰 할인 · 줄의 `unit_price × quantity` = 할인 전 상품 금액. 배송비 칸은 없다. ⇒ 매출(OD-2 «할인 후») = `total_price`.
- **`paidAt`**: `Order.markPaymentCompleted` 하나만 쓴다(`PaymentCompleted` 이벤트). 🔴 `Order.confirm` 은 결제 완료를 **검사하지 않는다** — 확정은 재고 예약 사가가, 결제 기록은 다른 소비자가 하므로 순서가 뒤집힐 수 있다 ⇒ 잠깐 동안 `CONFIRMED` · `paidAt` 없음이 가능하고, 그 주문은 `paidAt` 이 들어오는 순간 그날로 잡힌다(계약에 적음). 데모 시드는 실제 결제 경로(`POST /api/payments/confirm`)를 탄다(`infra/demo/seed/seed-ecommerce.sh:61-69`) — 직접 INSERT 가 아니다.
- **환불**: `Order.markRefunded` 는 `CANCELLED` 에서만 허용 ⇒ 환불된 주문은 언제나 `CANCELLED` 라 상태 조건만으로 빠진다. `refunded_at IS NULL` 은 두 번째 방어선(계약에 «다른 모집단이 아니다» 로 적음).
- **판매자 범위**: `X-Seller-Scope` → `SellerScopeContext`. 게이트웨이가 그 헤더를 **벗기고**(`GatewayIdentityConfig.java:65`) 아직 주입하는 곳이 없다 ⇒ 오늘 라이브에서는 늘 «제한 없음». 🔴 그래도 기존 `/insights` 는 **판매자 범위를 무시**하고 있었다(다른 admin 읽기는 다 적용). 이 티켓에서 지표 · 순위 둘 다 적용 — 범위가 걸리면 그 셀러의 줄만, 매출은 그 줄 금액(주문 단위 쿠폰은 셀러에게 배분하지 않는다).
- **`/insights` 소비자**: 콘솔 `overview-state.ts` 하나 + 샘플 픽스처 + 시험. 콘솔이 기간 없이 부르던 호출은 그대로 동작한다(`period` 선택 · 없으면 처음부터 지금까지).
- **인덱스**: `(tenant_id, paid_at)` 으로 시작하는 인덱스가 없었다 → `V13__add_orders_tenant_paid_at_index.sql`. ⚪ 쿼리 계획은 재지 않았다(Docker 없음).
- 🔴 **계약 문구 하나를 시험이 고쳤다**: `period` 를 `@RequestParam` 필수로 두니 빠졌을 때 Spring 의 누락 경로가 `VALIDATION_ERROR` 를 냈다(이 서비스의 `handleMissingParam`) — 모르는 값은 `INVALID_ORDER_REQUEST`. 한 규칙에 코드 둘 ⇒ `required = false` 로 두고 `MetricsPeriod.parse` 하나가 둘 다 400 `INVALID_ORDER_REQUEST` 로.
- **콘솔 공용 파서 보강**: 건수 지표의 `currency` 가 **필드째 없어도** `null` 로 읽는다(`shared/api/performance-metrics.ts`). order-service 는 null 을 보내지만(JSON null 포함 기본값) 다른 도메인이 null 을 빼면 P1 파서가 실적 줄 전체를 «불러올 수 없음» 으로 만들었다.
- **샘플 모드**: `shared/sample/**` 는 `shared/lib` 를 import 할 수 없다(격리 가드) ⇒ 샘플 창을 상수로 적고 `kstMetricsWindows(SAMPLE_AS_OF)` 와 같은지 시험으로 묶었다. 🔴 `/orders/{id}` 상세 분기가 `/orders/metrics` 를 주문 id `metrics` 로 읽어 404 를 낼 뻔했다 → 상세 분기 앞에 둔다(시험).
- **기본 기간 = 이번 달**(소유자 첫 예시가 «이번 달 매출»). 모르는 `?period=` 는 400 페이지가 아니라 기본값.
- 🔴 **샘플 라벨 규칙이 새 키를 막았다**(`sample-label-rule.test.ts` — 분류 안 된 문자열 키 68개). `period` · `unit` · `current` · `previous` · `revenue` · `units` · `orders` 를 기계 판독 키로 분류했다(`shared/sample/label-rule.ts`) — 화면 이름은 콘솔 라벨 맵에서 오고 응답에서 오지 않는다. 🔵 P3~P7 도 같은 키를 쓰므로 다시 분류할 일이 없다(도메인 키만 새로 생긴다).

## 검증 (2026-10-23 UTC, 로컬 · Windows 호스트)

| 무엇 | 명령 | 결과 |
|---|---|---|
| Java 공용 | `:libs:java-common:test` | rc=0 (`dateOf` 시험 포함) |
| order-service 단위 · 슬라이스 | `:order-service:test --tests '*AdminOrder*' --tests '*OrderQueryService*'` | rc=0 · 9 스위트 73 / 실패 0 (권한 게이트에 `/metrics` 추가 · 새 `/metrics` 슬라이스 3 · `getMetrics` 3 · `getInsights` 5) |
| order-service IT (AC-1~3) | `OrderMetricsQueryIntegrationTest` (Testcontainers) | ⚪ **로컬 미실행 — Docker 없음.** 컴파일은 통과(`compileTestJava`). 판정 = CI `Integration (ecommerce …)` |
| 콘솔 타입 · 린트 | `tsc --noEmit` · `next lint` | rc=0 · rc=0 |
| 콘솔 전체 | `TZ=UTC vitest run --maxWorkers=2 --minWorkers=1` | 5276 / 5277 → 빨강 1 = 샘플 라벨 규칙(위) → 고친 뒤 그 파일 포함 4 파일 97 / 97 |
| bite | 일별 버킷을 UTC 날짜로 | `OrderQueryServiceMetricsTest` 빨강 → 복원 후 초록 |

## 배포 상태

- `main` ⏳ 이 PR · AMI ⚪ 다음 굽기(order-service 새 이미지 + `V13` 인덱스) · 콘솔은 Vercel(머지 뒤 배포 — 백엔드가 굽기 전이면 데모에서 실적 줄은 «불러올 수 없음», 다른 블록은 그대로) · 라이브 ⚪ 데모 창.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D2 · D4 · D5 · OD-1 · OD-2
- `projects/ecommerce-microservices-platform/specs/services/order-service/architecture.md`
- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` (샘플 응답)

# Related Contracts

- `projects/ecommerce-microservices-platform/specs/contracts/http/order-api.md` § Admin Endpoints (`/summary` · `/insights` · 새 `/metrics`)
- P1 공용 실적 응답 모양

# Edge Cases

- 한 주문에 여러 판매자의 줄 → 주문 수는 1, 매출 · 수량은 줄의 합.
- 부분 환불이 있는가 → AC-0 에서 잰다. 있으면 계약에 처리를 적는다.
- 결제는 지난달, 확정은 이번 달(`BACKORDERED` → `CONFIRMED`) → `paidAt` 기준이라 지난달 값이 늘어난다.

# Failure Scenarios

- `createdAt` 으로 귀속 → 결제 안 된 날에 매출이 잡힌다.
- 콘솔 샘플 모드가 새 호출을 모른다 → 익명 방문자 화면에서 실적 줄이 항상 실패로 보인다.
- `/insights` 숫자가 줄어든 것을 결함으로 되돌린다 → AC-4 기록이 막는다.
