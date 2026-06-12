# Task ID

TASK-BE-365

# Title

ecommerce **marketplace settlement / commission slice** — ADR-MONO-030 **Step 4 facet b**. A net-new `settlement-service` that consumes order/payment events and accrues, per order-line by seller, the **platform commission** vs the **seller net** (per-seller rate in bps + platform default), with **refund reversal**. Builds on Step 2 (`tenant_id`, BE-357) + Step 3 (`seller_id`, BE-363). **Settlement-period close, payout/disbursement, seller banking, partial-refund clawback, and multi-currency are NOT in scope** (forward-declared).

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- event
- multi-tenant
- marketplace

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

ADR-MONO-030 의 **마켓플레이스 경제(commission)** 첫 조각을 구현한다. Step 2(바깥 tenant) + Step 3(안쪽 seller)가 격리·귀속 축을 깔았으니, 이 증분은 그 위에 **셀러 정산/수수료** 를 얹는다 — 신규 **`settlement-service`** 가 order/payment 이벤트를 소비해 캡처된 결제를 order-line 단위로 **플랫폼 수수료** 와 **셀러 순수익** 으로 분할·누적한다.

이 task 가 끝나면: "소비자가 결제를 완료하면(`PaymentCompleted`), 그 주문의 각 라인이 그 셀러의 수수료율(`seller_commission_rate` ?? 플랫폼 기본)로 분할되어 — `commission = round(gross × rate_bps / 10000)`, `seller_net = gross − commission` — 셀러별 accrual 로 누적되고, 환불(`PaymentRefunded`)은 그 주문 accrual 을 reversal 로 net-zero 상쇄하며, 운영자는 셀러-스코프 ABAC read 로 자기 셀러 정산만 보고, 수수료율 미설정 standalone 은 기본율 0 으로 degrade(net-zero)" 가 참이 된다.

기준 문서(SoT) = [`specs/features/marketplace-settlement.md`](../../specs/features/marketplace-settlement.md) + [`specs/services/settlement-service/architecture.md`](../../specs/services/settlement-service/architecture.md) + ADR-MONO-030 **§3.4 Step 4 facet b**.

---

# Scope

## In Scope (신규 `settlement-service` 1개)

- **신규 `settlement-service`** (`apps/settlement-service/`, Hexagonal/DDD, **`event-consumer + rest-api` 하이브리드**, terminal consumer — outbox 없음, libs `OutboxAutoConfiguration`/`OutboxMetricsAutoConfiguration` 제외; finance ledger / erp read-model 선례).
- **이벤트 소비 파이프라인** (`settlement-subscriptions.md`):
  - `order.order.placed` → **라인 스냅샷 캐시**(`order_id → [{seller_id, gross_minor = unitPrice × quantity}]` + 봉투 `tenant_id`). 멱등(order_id).
  - `payment.payment.completed` → 스냅샷 `orderId` 조인 → 라인별 **accrual** 적립. 멱등(`order_id, payment_id`).
  - `payment.payment.refunded` → 그 주문 accrual **reversal**(음수, 전체 상쇄). 멱등(`order_id, payment_id`).
- **CommissionPolicy(순수 도메인)**: `rate_bps = sellerRate ?? platform default`; `commission_minor = round(gross_minor × rate_bps / 10000)` (HALF_UP, ≥0); `seller_net_minor = gross_minor − commission_minor`(나머지 — `commission + seller_net == gross` 불변). money=`long` minor(KRW), rate=정수 bps(no float/BigDecimal).
- **수수료율 (셀러별 + 플랫폼 기본)**: `seller_commission_rate (tenant_id, seller_id) → rate_bps` 테이블; 부재 → config `settlement.commission.default-rate-bps`. 설정은 prospective(이미 적립분 불변, F3).
- **Accrual ledger (append-only, 불변)**: `commission_accrual` row 당 `(order line × event)`, `type ∈ {ACCRUAL, REVERSAL}`. 셀러 잔액 = `Σ seller_net_minor`. in-place 변이 금지(정정=reversal).
- **HTTP 표면(운영자 평면 read + rate admin)** (`settlement-api.md`): `GET /accruals`(셀러-스코프) · `GET /sellers/{id}/balance` · `GET/PUT /commission-rates/{id}`. accrual write path 없음.
- **멀티테넌시 & 셀러 스코프** (Step 2/3 패턴 재사용): 모든 row `tenant_id NOT NULL`; **tenant 는 OrderPlaced 스냅샷에서 파생**(payment 봉투엔 tenant 없음); accrual 은 라인 `seller_id` 귀속; read=셀러-스코프 ABAC `org_scope`(net-zero/fail-OPEN, `X-Seller-Scope`, tenant 필터 내부); cross-tenant/cross-seller=404(M3).
- **Flyway `V1`**(settlement_db 별도 schema): `seller_commission_rate` · `settlement_order_snapshot`(+라인) · `commission_accrual` · `processed_event`(dedupe). money=BIGINT minor, rate=INT bps, id=String(UUID).
- **deploy/CI 배선(atomic)**: settings.gradle + docker-compose 서비스(+ settlement DB) + gateway 라우트 `/api/admin/settlements/**` + CI `:settlement-service:check`(+ `-PrunIntegration` IT).
- **계약 갱신**: 신규 `settlement-api.md` + `settlement-subscriptions.md`; 기존 `order-events.md`/`payment-events.md` 의 **Consumers 목록에 settlement-service 추가**(additive — **본 spec PR 에서 이미 반영**, 페이로드 무변경).
- **PROJECT.md** Out-of-Scope "marketplace 정산/수수료 없음" 라인 정정(ADR §D7 타이밍 — 구현과 함께).

## Out of Scope (→ 후속 증분, ADR §3.4 Step 4 나머지)

- **기간마감(`settlement_period`) + payout(`seller_payout`) + 셀러 뱅킹/지급(PG payout)** — accrual + read 만. outbox + `settlement.*.v1` produced 이벤트도 이때.
- **부분/비례 환불 clawback** — v1 은 환불=주문 전체 reversal(`PaymentRefunded.amount` 비례 netting 보류).
- **multi-currency** — KRW only.
- **티어/카테고리/프로모션-조정 수수료** — flat 셀러별 율만.
- **payment-service tenant_id enrichment**(Step 2 합류) — v1 은 스냅샷 파생.
- **consumer-facing 셀러 수익 뷰** — 운영자 평면 한정.
- **셀러 온보딩 / 실 IAM seller provisioning** — Step 3 와 동일하게 토큰 claim 신뢰.

---

# Acceptance Criteria

- [ ] AC-1 — 신규 `settlement-service`(event-consumer + rest-api, terminal consumer, outbox 제외)가 존재하고 deploy/CI 배선(settings.gradle/compose/gateway/CI) 완료.
- [ ] AC-2 — `OrderPlaced` 소비 시 라인 스냅샷(`order_id → [{seller_id, gross_minor}]` + 봉투 `tenant_id`)이 멱등 적재(중복 order_id → 1개).
- [ ] AC-3 — `PaymentCompleted` 소비 시 스냅샷 조인 → 라인별 accrual: `commission = round(gross × rate_bps / 10000)` HALF_UP, `seller_net = gross − commission`, `commission + seller_net == gross`. 다중 셀러 주문 = 라인별 독립 셀러 귀속.
- [ ] AC-4 — 수수료율: 셀러 override 있으면 그 율, 없으면 플랫폼 기본. `PUT /commission-rates` 는 prospective(이미 적립분 불변); `rateBps ∉ [0,10000]` → 422 `COMMISSION_RATE_INVALID`.
- [ ] AC-5 — `PaymentRefunded` 소비 시 그 주문 accrual 이 reversal 로 **net-zero** 상쇄(셀러 잔액·플랫폼 commission 환불 전 값 복귀).
- [ ] AC-6 — 멱등: `PaymentCompleted`/`PaymentRefunded` 재전달이 중복 accrual/중복 reversal 안 만듦(`event_id` dedupe + `(order_id, payment_id)` 키).
- [ ] AC-7 — **tenant 파생**: accrual/reversal 의 `tenant_id` 가 **OrderPlaced 스냅샷에서** 옴(payment 봉투엔 tenant 없음). cross-tenant read=404(M3); **M6 cross-tenant leak 회귀 GREEN**(테넌트 A accrual 이 B 토큰에 안 보임).
- [ ] AC-8 — 셀러-스코프 ABAC read: restricted=자기 `seller_id` accrual 만; 부재/`'*'`=무필터(net-zero, fail-OPEN); 항상 tenant 필터 내부(isolate-then-attribute).
- [ ] AC-9 — net-zero degrade: 플랫폼 기본율 `0` + default-seller → commission 0, seller_net = gross(단일 스토어 경제적 동치, D8).
- [ ] AC-10 — `:settlement-service:check` BUILD SUCCESSFUL; CI Build & Test GREEN; 기존 order/payment 서비스 회귀 0(Consumers 목록 외 무변경).

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md`(`multi-tenant` trait 보유) + `rules/common.md` + `rules/traits/{multi-tenant,transactional,integration-heavy,event-consumer 해당분}.md` + `rules/traits/multi-tenant.md` M1-M7. Service Type = `event-consumer + rest-api` → `platform/service-types/event-consumer.md`(primary) + `rest-api.md`.

- [`specs/features/marketplace-settlement.md`](../../specs/features/marketplace-settlement.md) (이 증분 SoT)
- [`specs/services/settlement-service/architecture.md`](../../specs/services/settlement-service/architecture.md) (신규 서비스 아키텍처)
- [`specs/features/multi-tenancy-and-marketplace.md`](../../specs/features/multi-tenancy-and-marketplace.md) §2 바깥축 / §3 안쪽축 (tenant/seller 모델)
- [`docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md`](../../../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) §3.4 Step 4 facet b / D8 degradation
- [`docs/adr/ADR-MONO-025-abac-data-scope-generalization.md`](../../../../docs/adr/ADR-MONO-025-abac-data-scope-generalization.md) — `org_scope` ABAC(셀러-스코프 read, net-zero)
- 참조 구현: finance-platform ledger-service(accrual + 멱등 posting + 불변 ledger F3 — `SettleForeignPositionUseCase`/`FxSettlementPolicy` 형) · Step 3 BE-363(seller-scope ABAC `SellerScopeContext`/필터/repo-chokepoint) · Step 2 BE-357 B/C(`TenantContext`/필터) · erp read-model(terminal consumer, outbox 제외 패턴)

# Related Contracts

- `specs/contracts/http/settlement-api.md` (신규 — 운영자 read + rate admin)
- `specs/contracts/events/settlement-subscriptions.md` (신규 — 소비 이벤트)
- `specs/contracts/events/order-events.md` (`OrderPlaced` Consumers +settlement-service — 본 PR 반영; 페이로드 무변경)
- `specs/contracts/events/payment-events.md` (`PaymentCompleted`/`PaymentRefunded` Consumers +settlement-service — 본 PR 반영; 페이로드 무변경)

---

# Target Service

- `settlement-service` (신규)
- (계약 Consumers 목록만) `order-service` · `payment-service` — 코드 무변경

---

# Implementation Notes

- **트리거 = `PaymentCompleted`(캡처), 스냅샷 = `OrderPlaced`**: 결제 이벤트엔 라인/seller/tenant 가 없으므로 OrderPlaced 로 `(seller_id, gross_minor, tenant_id)` 를 미리 캐시해야 한다. **tenant 권위 소스 = 스냅샷**(payment 봉투엔 tenant 없음 — `payment-events.md` 확인). 이것이 핵심 설계 제약(AC-7).
- **순서/out-of-order(F2)**: PaymentCompleted 가 스냅샷보다 먼저 오면 귀속 불가 → 재시도 → DLQ. v1 은 placement 가 capture 를 선행한다고 가정(현실적). 버퍼링 = 보류.
- **bps 정수 산술**: `commission = round(gross × rate_bps / 10000)` HALF_UP, `seller_net` 은 나머지(드리프트 0). finance ledger 의 `BigDecimal` HALF_UP minor 라운딩과 동형이나 여기선 bps 라 순수 정수.
- **불변 ledger(F3)**: accrual/reversal 은 insert-only. 율 변경/환불은 새 row(정정=reversal). finance ledger immutability 미러.
- **terminal consumer**: produced 이벤트 0(v1). `OutboxAutoConfiguration` 제외. produced `settlement.*.v1` 는 payout 증분에서.
- **셀러-스코프 ABAC**: Step 3 의 `SellerScopeContext` + `X-Seller-Scope` 필터 + repo-chokepoint 단일 net-zero 분기(`:sellerRestricted = false OR seller_id = :scope`) 재사용. **tenant 필터 항상 선행**(AC-7/8).
- **마이그레이션**: settlement_db 별도 schema, Flyway `V1`(4 테이블). id=String(UUID), money=BIGINT minor, rate=INT bps.
- ⚠️ **IT 트랩(BE-357/363 동일)**: ecommerce 로컬 IT = `-PrunIntegration` + bare-`@SpringBootTest` 는 `@SpringBootTest(classes = SettlementServiceApplication.class)` 핀(multiple-`@SpringBootConfiguration` 회피); **CI 게이트 = Build & Test(unit+slice)**, ecommerce 전용 PR-gated IT 잡 부재 — Rancher npipe degraded 로컬은 compiled-only(skip 관행).
- 모델 권장 = **Opus** (신규 서비스 + 이벤트 상관 + 불변 ledger + 복합 스코프 — 도메인 심화).

---

# Edge Cases

- **다중 셀러 주문**: 한 주문 라인이 셀러 a1/a2 → 라인별 독립 accrual(헤더 tenant 단일).
- **수수료율 0(또는 기본 0)**: commission 0, seller_net = gross(net-zero degrade, AC-9).
- **라운딩**: `round(gross × bps / 10000)` HALF_UP; `seller_net = gross − commission`(나머지 → 합 항상 gross).
- **환불 후 재조회**: 셀러 잔액 = 환불 전 값 − 그 주문분(reversal net-zero).
- **재전달**: 같은 PaymentCompleted/Refunded 2회 → 1회 효과(멱등).
- **스냅샷 없는 결제(out-of-order/유실)**: 귀속 불가 → 재시도/DLQ(F2).
- **셀러-scope 부재 운영자**: 무필터 전체(fail-OPEN). cross-seller 조회=404.

---

# Failure Scenarios

- **F1 — 수수료 분할 부정확/드리프트**: commission + seller_net ≠ gross. → AC-3 `seller_net = gross − commission`(나머지) + HALF_UP, 단위 테스트(gain/zero/라운딩).
- **F2 — out-of-order accrual**: PaymentCompleted 가 OrderPlaced 스냅샷보다 먼저 → seller/tenant 미상 → 잘못 귀속 또는 누락. → 스냅샷 부재 시 raise→재시도→DLQ(placement 선행 가정), IT 로 검증.
- **F3 — 중복 적립/상쇄**: 재전달이 accrual/reversal 중복. → AC-6 `event_id` dedupe + `(order_id, payment_id)` 키.
- **F4 — cross-tenant 누설**: payment 봉투에 tenant 없다고 스냅샷 대신 다른 소스로 tenant 추정 → 격리 위반. → AC-7 tenant=스냅샷 파생 한정 + M6 회귀.
- **F5 — 환불 미상쇄**: 환불이 reversal 안 만들어 셀러가 미반환 수익 보유. → AC-5 reversal net-zero + 테스트.
- **F6 — 율 소급 오염**: `PUT /commission-rates` 가 이미 적립분 재산정. → AC-4 prospective(F3 불변).

---

# Test Requirements

- **unit**: `CommissionPolicy` 분할(gain/zero-rate/HALF_UP 라운딩/`commission+seller_net==gross`) · 율 해석(override/기본) · reversal 음수 생성.
- **slice**: `GET /accruals`·`/balance`·`/commission-rates`(셀러-스코프 필터·404) · `PUT /commission-rates`(422 범위) · accrual write path 부재 확인.
- **application**: consume OrderPlaced→스냅샷(멱등) · PaymentCompleted→accrual(스냅샷 조인, 멱등) · PaymentRefunded→reversal(net-zero) · 멱등 재전달.
- **IT**(Testcontainers, `-PrunIntegration` 로컬): V1 마이그레이션 실적용 + 이벤트 라운드트립(placed→completed→accrual→refunded→net-zero) + **tenant=스냅샷 파생** + cross-tenant 격리(M6) + cross-seller 격리. `@SpringBootTest(classes = SettlementServiceApplication.class)` 핀.

---

# Definition of Done

- [ ] `settlement-service` 부트스트랩(event-consumer + rest-api, terminal, outbox 제외) + deploy/CI 배선
- [ ] OrderPlaced 스냅샷 + PaymentCompleted accrual + PaymentRefunded reversal(멱등) 구현
- [ ] CommissionPolicy(bps 분할, net-zero degrade) + 셀러별 율/플랫폼 기본
- [ ] 셀러-스코프 ABAC read(net-zero) + tenant=스냅샷 파생(AC-7) + M6 회귀 GREEN
- [ ] HTTP read + rate admin(422 검증) + 계약 일치
- [ ] PROJECT.md Out-of-Scope 정산 라인 정정
- [ ] `:settlement-service:check` GREEN, order/payment 회귀 0
- [ ] Ready for review
