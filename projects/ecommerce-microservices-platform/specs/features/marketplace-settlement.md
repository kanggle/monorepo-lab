# Feature — Marketplace Settlement & Commission (ADR-MONO-030 Step 4 facet b, source-of-truth)

> 본 문서는 멀티벤더 마켓플레이스의 **셀러 정산 / 수수료** 증분의 **스펙 기준(source of truth)** 이다 — [ADR-MONO-030](../../../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) §3.4 **Step 4 facet b**.
> 선행: Step 2(바깥 tenant 축, BE-357) + Step 3(안쪽 seller 축, BE-363). 이 증분은 그 위에 **마켓플레이스 경제(commission)** 의 첫 조각을 얹는다.
> 슬라이스 범위 = **신규 `settlement-service` 1개** (order/payment 이벤트 소비 → 셀러별 수수료 accrual). **기간마감·payout·뱅킹 = 보류**(아래 §6).

---

## 0. 한 장 요약

| 요소 | 모델 |
|---|---|
| **무엇** | 캡처된 결제를 order-line 단위로 **플랫폼 수수료(commission)** 와 **셀러 순수익(net)** 으로 분할, 셀러별 누적(accrual) |
| **언제 (트리거)** | `PaymentCompleted`(실제 캡처) — `OrderPlaced` 로 캐시한 라인 스냅샷을 조인해 산정. 환불(`PaymentRefunded`)=reversal |
| **얼마 (수수료율)** | **셀러별 율(bps) + 플랫폼 기본** fallback. `commission = round(gross × rate_bps / 10000)` HALF_UP |
| **누구 소유** | 신규 `settlement-service` (event-consumer + 운영자 read API). 자체 테이블만 — order/payment HTTP 미호출 |
| **격리/귀속** | tenant(바깥) 격리 + seller(안쪽) 귀속. accrual row = `(tenant_id, seller_id)`. read=셀러-스코프 ABAC(net-zero) |
| **불변** | ledger 형 append-only(F3) — accrual 정정 = reversal row. accrual + reversal = net-zero |

---

## 1. 왜 이벤트 기반인가 (트리거 = PaymentCompleted)

수수료는 **돈이 실제로 들어왔을 때**(결제 캡처) 발생해야 한다 — 주문 시점(placement)에 적립하면 미결제/취소가 reversal 을 강제한다. 따라서 트리거는 **`payment.payment.completed`**(캡처) 이다.

그러나 `PaymentCompleted` 페이로드(`paymentId`/`orderId`/`userId`/`amount`/`paidAt`)에는 **라인 분해도, `seller_id` 도, `tenant_id` 도 없다**. 라인별 셀러 귀속 + tenant 는 **`order.order.placed`** 에만 있다(`items[].sellerId` + `items[].unitPrice` + `items[].quantity` + 봉투 `tenant_id`). 그래서:

1. `OrderPlaced` 소비 → **라인 스냅샷 캐시**(`order_id → [{seller_id, gross_minor}]`, `tenant_id`). 아직 accrual 없음(돈 미캡처).
2. `PaymentCompleted` 소비 → 스냅샷을 `orderId` 로 조인 → 라인별 수수료 분할 → **accrual** 적립.
3. `PaymentRefunded` 소비 → 해당 주문 accrual 을 **reversal**(음수)로 상쇄.

> **★ ADR-030 통찰 — settlement 고유 제약**: payment-service 는 Step 2 (tenant) 미적용이라 결제 이벤트 봉투에 `tenant_id` 가 없다. 따라서 settlement 의 `tenant_id` **권위 소스는 `OrderPlaced` 스냅샷뿐**이다(orderId 조인). 이것이 "왜 스냅샷을 먼저 영속해야 하는가" 의 이유다. (payment-service 가 나중에 Step 2 합류하면 결제 봉투에서 직접 tenant 를 읽어 이 의존을 제거 — §6 forward.)

---

## 2. 수수료 분할 (CommissionPolicy)

money = 정수 **minor units**(`long`, KRW 암묵 — order/payment 와 동일). rate = 정수 **basis points**(bps; `1000 bps = 10%`). float/`BigDecimal` 금지 — 유일 산술은 `gross × bps / 10000`(정수, HALF_UP 라운딩).

라인별 (`gross_minor = unitPrice × quantity`):
```
rate_bps         = sellerRate(tenant_id, seller_id) ?? platform default
commission_minor = round(gross_minor × rate_bps / 10000)   (HALF_UP, ≥ 0)
seller_net_minor = gross_minor − commission_minor           (나머지 — 2차 라운딩 없음)
```
`seller_net` 을 **나머지**로 두어 항상 `commission + seller_net == gross`(드리프트 0). 한 주문이 여러 셀러에 걸치면 라인별 독립 분할(헤더 tenant 단일, 각 라인 seller 귀속 — Step 3).

### 2.1 수수료율 해석 (셀러별 + 플랫폼 기본)

- `seller_commission_rate (tenant_id, seller_id) → rate_bps` — 운영자가 설정하는 셀러별 율. 행 부재 → **플랫폼 기본** `settlement.commission.default-rate-bps`.
- 율 설정은 **prospective** — 이후 적립분에만 적용, 이미 적립된 accrual 은 불변(F3). (과거 재산정 = 보류.)
- `rateBps ∈ [0, 10000]`(0%…100%). 범위 밖 = 422 `COMMISSION_RATE_INVALID`.

---

## 3. Accrual ledger (append-only, 불변 — F3)

`(order line × event)` 당 1 row. `ACCRUAL` = 양수, `REVERSAL` = 원본의 음수. 셀러의 정산가능 잔액 = 그 셀러 row 들의 `Σ seller_net_minor`(플랫폼 수수료 = `Σ commission_minor`). read 는 집계만 — in-place 변이 없음.

**환불 reversal (v1 = 전체 상쇄)**: `PaymentRefunded` → 그 주문의 모든 accrual 을 음수로 복제해 net-zero. v1 은 환불을 **주문 전체** 로 처리(부분/비례 clawback = §6 보류; `PaymentRefunded.amount` 가 부분일 수 있으나 v1 미사용).

**멱등**: `processed_event`(event_id) dedupe + accrual/reversal 은 `(order_id, payment_id)` 키 → 재전달이 중복 적립/중복 상쇄 불가.

---

## 4. 멀티테넌시 & 셀러 스코프 (ADR-030 재사용)

- **바깥 tenant (M1-M7)**: 모든 settlement row(rate/snapshot/accrual)에 `tenant_id NOT NULL`. read=`WHERE tenant_id`(gateway `X-Tenant-Id`), cross-tenant=404(M3). tenant 는 §1 의 스냅샷에서 파생.
- **안쪽 seller**: accrual 은 라인 `seller_id`(Step 3 `items[].sellerId`)로 귀속.
- **셀러-스코프 read (ABAC `org_scope`, ADR-025, net-zero/fail-OPEN)**: OPERATOR 가 자기 셀러 accrual 만. seller-scope claim(`X-Seller-Scope`) 부재/`'*'`=무필터(테넌트 운영자 전체); restricted=`seller_id` 필터. **항상 tenant 필터 내부**(isolate-then-attribute, Step 3 AC-6). consume 경로는 seller-scope 무관(이벤트 기반).
- **degradation (D8)**: default-tenant + default-seller + 플랫폼 기본율 `0` 가능 → 단일 스토어 = commission 0, seller_net = gross(오늘 동작과 경제적 동치 = net-zero).
- **회귀 (M6)**: cross-tenant leak IT — 테넌트 A accrual 이 B 토큰으로 안 보임.

---

## 5. HTTP 표면 (운영자 평면 read + rate admin)

`specs/contracts/http/settlement-api.md` 참조. **accrual write path 없음**(이벤트 전용).
- `GET /api/admin/settlements/accruals` — 셀러-스코프 accrual 라인.
- `GET /api/admin/settlements/sellers/{sellerId}/balance` — 셀러별 누적 net + 플랫폼 commission.
- `GET/PUT /api/admin/settlements/commission-rates/{sellerId}` — 셀러 율 조회/설정(prospective).

---

## 6. 보류 (forward-declared — 후속 증분)

- **기간마감 + payout**: `settlement_period`(OPEN→CLOSED) + 기간별 셀러 net 스냅샷 + `seller_payout` 생성(finance ledger period-close 미러). outbox + `settlement.commission.accrued.v1`/`settlement.period.closed.v1` 도입.
- **셀러 뱅킹 / 지급**: 셀러 계좌 + 실 송금(PG payout) + 지급 상태.
- **부분/비례 환불 clawback**: v1 은 환불=주문 전체 reversal. `PaymentRefunded.amount` 비례 netting 보류.
- **multi-currency**: v1 KRW only.
- **티어/카테고리/프로모션-조정 수수료**: v1 = flat 셀러별 율.
- **payment-service tenant_id enrichment**: payment-service Step 2 합류 시 결제 봉투에서 직접 tenant → accrual 의 스냅샷-파생 의존 제거.

---

## 7. 영향 범위 (구현 시)

| 레이어 | 변경 |
|---|---|
| **신규 서비스** | `apps/settlement-service/`(Hexagonal/DDD, event-consumer + rest-api). Flyway `V1`(rate / snapshot / accrual / processed_event). |
| **계약** | 신규 `settlement-api.md` + `settlement-subscriptions.md`(소비). 기존 `order-events.md`/`payment-events.md` 의 **Consumers 목록에 settlement-service 추가**(additive, 페이로드 무변경). |
| **deploy/CI** | docker-compose 서비스 + gateway 라우트(`/api/admin/settlements/**`) + CI `:settlement-service:check`. |
| **PROJECT.md** | Out-of-Scope "marketplace 정산/수수료 없음" 라인 정정(ADR §D7 타이밍 — 구현 PR 과 함께). |
