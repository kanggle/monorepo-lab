# Feature — Multi-Tenancy & Marketplace (ADR-MONO-030, Step 1 source-of-truth)

> 본 문서는 ecommerce 를 **멀티벤더 마켓플레이스 SaaS** 로 승격하는 두 직교 축의 **스펙 기준(source of truth)** 이다 — [ADR-MONO-030](../../../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) §3.4 **Step 1**.
> **Step 1 = 스펙만** (Source-of-Truth-first). 컬럼/마이그레이션/게이트 코드/셀러 애그리거트 구현은 **Step 2(바깥 축)·Step 3(안쪽 축)** 의 별도 태스크가 본 문서를 기준으로 구현한다.
> 슬라이스 범위 = **product-service + order-service** 2개. 나머지 서비스·정산/수수료·콘솔 통합 = 보류(ADR §3.4 Step 4). [ADR-MONO-022](../../../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) 이행 이벤트 `tenant_id` 스레딩(facet d) = 실현됨(TASK-MONO-296 — §7).

## 진행 현황 (as-of 2026-06-15)

Step 2(product/order row-level `tenant_id` M1), Step 3(seller 축 TASK-BE-363), Step 4 facet b (settlement-service TASK-BE-365) 및 추가 tenant_id 증분이 완료됨:

| 서비스 | tenant_id 마이그레이션 | 비고 |
|---|---|---|
| product-service | Step 2 (M1) | TASK-BE-357 |
| order-service | Step 2 (M1) | TASK-BE-357 |
| user-service | V4 | TASK-BE-367 |
| promotion-service | V6 | TASK-BE-368 |
| shipping-service | V7 | TASK-BE-369 |
| notification-service | V5 | TASK-BE-370 |
| settlement-service | 구현 완료 | TASK-BE-365 (신규 서비스) |
| payment-service | V5 | TASK-BE-400 |
| review-service | V5 | TASK-BE-403 |
| search-service | ES index-filter (TASK-BE-404) | tenantId 필드 + mandatory query filter; Flyway/SQL 없음 |
| cart | **N/A — 서비스 미존재** | 클라이언트/order-service 미분리; 추후 cart-service 추출 시 |
| auth-service | **N/A — 폐기됨** | TASK-BE-132, 빌드 제외; iam-platform 대체 |
| web-store | **N/A — 프런트(Next.js)** | relational 영속 없음; `X-Tenant-Id` 헤더 전파만 (gateway→BFF, 이미 라이브) — 마이그레이션 대상 아님 |

`admin-dashboard` (frontend-app): ADR-MONO-031 Phase 6 / TASK-MONO-259 로 **폐기 완료** — platform-console 일원화.

---

## 0. 한 장 요약

| 축 | 모델 | 키 | 성격 | 상태 |
|---|---|---|---|---|
| **바깥 — tenant** | Shopify식 멀티테넌트 SaaS | `tenant_id` | 격리(isolation) | **재사용** — 플랫폼 federation 의 6번째 entitlement-trust 도메인 ([ADR-019](../../../../docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md) D5 진화를 ecommerce 에 적용; scm/erp 가 선례) |
| **안쪽 — seller** | 쿠팡식 마켓플레이스(멀티벤더) | `seller_id` | 공유 + 참여자 귀속 | **net-new** — ecommerce-local `seller` 애그리거트 (`marketplace` 스코프, PROJECT.md 가 드롭했던 것) |
| **평면 — roles** | 소비자/운영자 분리 | `roles` (소비자 `CUSTOMER` / 운영자 `ADMIN`) | 이미 존재 | **재사용** — IAM 발급 + gateway `AccountTypeEnforcementFilter` roles-only 강제 (TASK-BE-131; ADR-MONO-032/035 가 레거시 `account_type` 평면을 roles 로 대체) |

**복합 신원 = `tenant_id`(어느 스토어) × `roles`(소비자 `CUSTOMER` / 운영자 `ADMIN`) × `seller_id`(어느 셀러; 운영자 평면 한정).**
중첩: `platform → tenant A(격리) → marketplace A → {seller a1, a2, …}`. 바깥축=격리, 안쪽축=한 테넌트 내부에서 공유 카탈로그/고객풀에 셀러 귀속.

---

## 1. 현재 상태 (Step 1 이 닫는 갭)

| 요소 | Before Step 2 (single-tenant, 역사적 기준) | 목표 (슬라이스) |
|---|---|---|
| gateway `TenantClaimValidator` | **고정 슬러그** `tenant_id == 'ecommerce'` 만 통과 (ADR-019 *"before"* 상태) — [iam-integration.md](../integration/iam-integration.md) §Token 검증 §5 | **entitlement-trust** — JWKS 검증된 GAP/IAM 토큰의 임의 `tenant_id` 수용, row 로 격리 (ADR-019 D5) |
| product/order 데이터 | `tenant_id` **없음** (모든 row = 암묵적 단일 스토어) | 모든 영속 row 에 `tenant_id` (M1) + `seller_id`(소유/귀속) |
| 평면 | `roles` (소비자 `CUSTOMER` / 운영자 `ADMIN`) **이미 존재** (IAM 발급, gateway 강제) | 변경 없음 (재사용). 셀러 = 운영자(`ADMIN`) 평면 내부 `seller_id` |
| 셀러 | 단일 판매자 (암묵) | `seller` 애그리거트 + `seller_id` (Step 3) |
| `tenant_domain_subscription` | `domain_key='ecommerce'` **행 없음** | 행 추가 (콘솔 카탈로그가 ecommerce 를 구독 가능 도메인으로 렌더 — Step 4) |

> 핵심: 토큰 게이트(`TenantClaimValidator`)·평면(`roles`)·IAM 통합은 **이미 있다**. 닫을 갭은 **(a) 게이트를 고정슬러그→entitlement-trust 로 진화** + **(b) product/order 데이터의 row-level `tenant_id`** + **(c) `seller_id` 축** 이다.

---

## 2. 바깥 축 — 테넌트 격리 (M1-M7 채택)

ecommerce 는 [`rules/traits/multi-tenant.md`](../../../../rules/traits/multi-tenant.md) M1-M7 을 채택한다 (PROJECT.md `multi-tenant` trait 추가는 **Step 2** 에서 코드와 함께 — ADR §D7 타이밍).

### 2.1 M1 — row-level `tenant_id`
- product-service / order-service 의 **모든 영속 aggregate/entity** 에 `tenant_id VARCHAR NOT NULL`.
- 슬라이스 대상 테이블 (구현 시 확정): product (`products`, `product_variants`, `inventory`, …), order (`orders`, `order_items`, …).
- 참조 구현 = **scm `inventory-visibility`/`procurement`, erp `approval`** 의 `tenant_id` 컬럼 패턴.

### 2.2 M2 — 3-layer 격리
1. **게이트(토큰)**: gateway `TenantClaimValidator` 가 고정슬러그 → **entitlement-trust** 로 진화 (Step 2). JWKS 검증된 토큰의 `tenant_id` 수용.
2. **컨텍스트 전파**: gateway `JwtHeaderEnrichmentFilter` 에 `X-Tenant-Id` 추가 → 서비스가 요청 컨텍스트로 보관 (per-request).
3. **row 필터**: 모든 read 가 `WHERE tenant_id = <context>`; write 는 컨텍스트의 `tenant_id` 주입.

### 2.3 M3-M7
- **M3 (404-over-403)**: cross-tenant 리소스 조회 = 404 (존재 누설 방지). **M4 (enumeration 방지)**: id 순회로 타 테넌트 존재 추론 불가. **M5 (async 전파)**: order saga 이벤트(`order.*`)·consumer(`product.product.stock-changed` 등) 봉투에 `tenant_id` 전파 (ADR-022 이행 이벤트 스레딩은 Step 4). **M6 (cross-tenant leak 회귀 IT)**: 슬라이스 필수 — 테넌트 A 데이터가 B 토큰으로 안 보임 증명. **M7 (per-tenant rate limit / unbounded-query cap)**: **realized (TASK-BE-405, ADR-MONO-030 Step 4 facet e)** — §2.6 참조.

### 2.4 entitlement-trust 게이트 진화 (ADR-019 D5)
- gateway `TenantClaimValidator`: `tenant_id == 'ecommerce'` (고정) → **JWKS 검증된 GAP/IAM 토큰의 임의 `tenant_id` 수용**. entitlement 결정은 **IAM 발급 시점**(구독 + operator 배정이 있을 때만 ecommerce-스코프 토큰 발급) — ecommerce 는 row 격리만 집행 (두 권위, 무중첩).
- **Dual-accept 윈도우** (M6/ADR-019 D6): 진화 단계에서 게이트는 **레거시 고정슬러그 `'ecommerce'` ∪ entitlement-trust** 양쪽 수용 → default-tenant 시드(아래 2.5)와 함께 net-zero.

### 2.5 default-tenant + standalone degradation (D8)
- **default-tenant 시드**: 기존 단일 스토어 데이터 = 단일 default tenant(예: `tenant_id='ecommerce'`)로 매핑 → 오늘 동작 byte-identical.
- **standalone**(플랫폼 IAM 부재): `tenant_id` claim 부재 → default tenant 로 resolve → 단일 스토어로 degrade. 멀티테넌트 = **additive**, 하드 의존성 아님.

### 2.6 M7 — per-tenant rate limit + unbounded-query cap (realized, TASK-BE-405 / ADR-MONO-030 Step 4 facet e)

M7 의 두 facet 을 **gateway-edge** (M2 layer-1) 에서 실현:

- **per-tenant rate limit** (`gateway-service`): route 의 `RequestRateLimiter` 필터가 **`(tenant_id, route_id)` 튜플 키**로 동작 (`tenantRouteKeyResolver`, 키 = `rate:ecommerce-gw:<routeId>:t:<tenantId>`). 한 테넌트의 burst 가 다른 테넌트의 버킷을 소모하지 않음 (M7 line 84). tenant 는 JWKS-검증된 JWT 의 `tenant_id` claim 에서 **reactive security context** 로 추출 (ThreadLocal 아님, non-blocking).
  - **pre-auth vs post-auth 키잉**: 인증 요청 = `<tenant>:<route>`; **익명/pre-auth 요청**(`/api/search/**`, 공개 `GET /api/products/**`, carrier-webhook) = security context 부재 → **default tenant `'ecommerce'` + client IP** 로 키잉(`...:t:ecommerce:ip:<ip>`) → 레거시 IP-바운딩(brute-force/DoS) 보존. 키는 **절대 null 아님**(default-tenant 가드).
  - **limit source**: route 당 config 기본값(`replenishRate`/`burstCapacity`, application.yml) + 향후 옵션 per-tenant override(entitlement 평면과 decouple — `tenant_domain_subscription` 무결합). 위반 → **429 `TOO_MANY_REQUESTS`**.
  - **degrade (D8 net-zero)**: `tenant_id` 부재(standalone) → default tenant 단일 버킷(오늘 동작 동치). Redis 미가용 → **fail-open**(`FailOpenRateLimiter` — Redis-class 에러만 통과 허용, sentinel `X-RateLimit-Remaining: -1` + 메트릭; 비-Redis 에러는 전파). rate-limit 은 additive, 하드 의존성 아님.
- **unbounded-query cap** (모든 tenant-scoped list endpoint): max page size = **100**(`LIMIT`-less / 과도 list 도달 불가, M7 line 86). 정상 page size 는 backward-compatible(clamp only). **audit 결과 (2026-06-18)**: ecommerce 전 list endpoint 가 이미 100 으로 clamp — `product-service`(`ProductController`/`AdminProductController`/`AdminSellerController` `MAX_PAGE_SIZE=100`), `order-service`(`OrderControllerUtils.MAX_PAGE_SIZE=100`), 공유 `PageQuery.of`(MAX_SIZE=100, settlement/promotion/review 사용), `user-service`(WishlistService/UserProfileService 서비스-레이어 `Math.min(size,100)`). TASK-BE-405 는 product/order 에 회귀 테스트를 추가해 캡을 고정.
- **cross-tenant isolation IT** (AC-7): `CrossTenantRateLimitIsolationIntegrationTest` — tenant A 의 burst(429)가 동일 route·동일 client 의 tenant B 버킷을 소모하지 않음 증명(`@Tag("integration")`, CI 가 실 Redis 로 실행).

---

## 3. 안쪽 축 — 셀러 (마켓플레이스, net-new)

### 3.1 seller 애그리거트
- ecommerce-local `seller` 엔티티, 키 `(tenant_id, seller_id)` — 셀러는 **한 테넌트 내부의 참여자**(격리 경계 아님; ADR-030 D3-B 기각).
- **라이프사이클 + 실 IAM provisioning (ADR-MONO-042, Step 4 facet f — 실현됨)**: 셀러는 실 운영자 principal. 온보딩이 account-service 내부 EP 를 호출해 **실 IAM seller-operator 계정 + born-unified identity**(ADR-036 `resolveOrCreate` 재사용)를 발급한다. 상태기계 `PENDING_PROVISIONING → ACTIVE`(provisioning 성공) / `ACTIVE → SUSPENDED·CLOSED`(운영자 비활성화 시 백킹 계정 lock/deactivate). **fail-soft(D3)**: IAM 미가용 시 셀러는 PENDING_PROVISIONING 유지, 온보딩은 막히지 않음(재-provision 가능). **authz net-zero(D6)**: 런타임 seller-scope 경로 불변(신뢰 claim 이 실 계정으로 backing 될 뿐). 정산/수수료 = 여전히 **보류**(Step 4 facet b 는 별도 settlement-service). 계약 = [contracts/http/internal/product-to-account.md](../contracts/http/internal/product-to-account.md).

### 3.2 소유/귀속
- **product**: 소유권 = `(tenant_id, seller_id)`. 셀러가 등록한 상품.
- **order**: **order-line(주문 항목) 단위 셀러 귀속** — 한 주문이 여러 셀러 상품을 포함할 수 있음(공유 카탈로그/장바구니). order 헤더는 `tenant_id`, 각 `order_item` 은 그 상품의 `seller_id`.

### 3.3 셀러-스코프 read (ABAC 재사용)
- 셀러(OPERATOR)가 자기 데이터만 보는 필터 = [ADR-MONO-025](../../../../docs/adr/ADR-MONO-025-abac-data-scope-generalization.md) `org_scope` ABAC 데이터-스코프 형태 재사용 (`seller_id` 를 데이터-스코프 축으로).
- **net-zero 원칙**(ADR-025): 스코프 claim 부재/`'*'` = 무필터(테넌트 운영자 전체 조망); restricted = `seller_id` 필터.
- **default-seller 시드**: 기존 상품 = 단일 default seller → 오늘 동작 불변(D8 대칭).

---

## 4. 평면 — `roles` (재사용; ADR-030 D4 의 `account_type` 평면을 ADR-032/035 가 roles 로 대체)

[iam-integration.md](../integration/iam-integration.md) 의 기존 메커니즘을 사용한다. ADR-030 D4 는 `account_type` 평면 재사용을 명시했으나, **ADR-MONO-032 D5 + ADR-MONO-035 4b 가 `account_type` 을 제거**하고 `roles` 를 유일 인가 축으로 삼았다 — 평면 구분은 이제 토큰의 `roles` 가 담당한다:

| 평면 | roles | 경로 | 신원 권위 |
|---|---|---|---|
| 소비자(shopper) | `roles ∋ CUSTOMER` | `/api/**` | IAM (self-service signup) |
| 운영자/셀러-어드민 | `roles ∋ ADMIN` | `/api/admin/**` | IAM (assume-tenant 도메인 롤 파생, ADR-035 4a) |

- **단일 IdP(IAM), 평면 2개 = `roles` (`CUSTOMER` / `ADMIN`)**. 별도 ecommerce auth-service 아님 (auth-service 는 제거됨, TASK-BE-132).
- **셀러 축은 운영자(`ADMIN`) 평면 내부**: `seller_id` 는 운영자 토큰/스코프에만 의미 있음. 소비자(`CUSTOMER`)는 `seller_id` 권위 없음(상품을 *볼* 뿐, 셀러로서 행위 불가).
- 소비자 주문은 여러 셀러 상품에 걸칠 수 있으나, 소비자 자신은 셀러가 아님 — `seller_id` 는 상품/주문항목의 **귀속 속성**이지 소비자 신원이 아니다.

---

## 5. 계약 영향 (Step 2/3 가 구현)

> Step 1 은 **모델**을 확정한다. 아래는 Step 2/3 이 갱신할 계약의 **영향 범위** — 필드 단위 계약 편집은 해당 구현 태스크가 본 문서를 기준으로 수행.

- **HTTP 계약**: `tenant_id` 는 **요청 본문 필드가 아니라 토큰 claim 파생**(헤더 `X-Tenant-Id`) — API 표면에 노출 안 함. `seller_id` 는 product 등록/조회·order 항목 응답에 노출(OPERATOR 표면). 소비자 표면은 `seller_id` 를 읽기 전용 표시로만.
- **이벤트 계약**: `order.*`·product 소비 이벤트 봉투에 `tenant_id` 추가(M5). `seller_id` 는 product/order 항목 페이로드에. **ADR-022 이행 이벤트(`ecommerce.fulfillment.requested.v1` 정방향 + `wms.outbound.shipping.confirmed.v1`/`wms.outbound.order.cancelled.v1` 귀환) `tenant_id` 스레딩 = 실현됨** (facet d / TASK-MONO-296 — §7 참조; wms 는 불투명 correlation 으로만 carry, 단일 테넌트 유지).
- **`tenant_domain_subscription` `domain_key='ecommerce'`**: 플랫폼 IAM(account-service) 소유 — ecommerce 가 구독 가능 도메인이 되는 entitlement 행. ecommerce 는 이를 **의존성**으로 참조(읽지 않음; 게이트는 토큰 claim 만 신뢰). 실제 시드/콘솔 렌더 = Step 4.

---

## 6. 슬라이스 검증 (Step 2/3 의 AC 기준)

> 슬라이스 하나로 **두 축(중첩 테넌시) end-to-end** 증명:
> 셀러 a1(테넌트 A)가 상품 등록 → 소비자가 그 상품 주문 → 주문 항목이 `(tenant A, seller a1)`로 귀속 → **테넌트 B 는 그 무엇도 못 봄**(M6 cross-tenant leak 회귀 IT).

- 바깥축 AC: cross-tenant 격리 IT(M6) GREEN; default-tenant net-zero(기존 동작 불변); entitlement-trust 게이트 dual-accept.
- 안쪽축 AC: `seller_id` 소유/귀속; 셀러-스코프 read(ABAC); default-seller net-zero.

---

## 7. 보류 (ADR §3.4 Step 4)

셀러 정산/수수료 · 콘솔 통합(원래 "웹스토어 어드민을 콘솔에서" 질문 — `domain_key='ecommerce'` 시드 + 카탈로그 렌더) · 나머지 11개 서비스(cart/payment/promotion/shipping/review/search/notification/…) `tenant_id` 전파. (M7 per-tenant rate limit / unbounded-query cap 은 **realized** — §2.6 / TASK-BE-405. 향후 보류 facet = resource-count quotas(max products/sellers per tenant) · subscription-tier-derived limits — 마켓플레이스 경제 기능, M7 아님.)

> **ADR-022 이행 이벤트 `tenant_id` 스레딩 (facet d) = 실현됨** ([ADR-MONO-022](../../../../docs/adr/ADR-MONO-022-ecommerce-wms-fulfillment-integration.md) §6 facet-d row / TASK-MONO-296) — M5 비동기 전파 불변식이 ecommerce↔wms 이행 루프까지 도달. 정방향(`OrderConfirmed.tenant_id` → `FulfillmentRequestedMessage.tenantId`)에 이어 **귀환 레그**도 스레딩: wms 가 인바운드 봉투의 `tenantId` 를 **불투명 correlation** 으로 캡처(아웃바운드 order 의 nullable 컬럼 — 격리 키 아님; wms 단일 테넌트 유지, NOT NULL/row 필터/게이트 변경 없음)하고 `wms.outbound.shipping.confirmed.v1`·`wms.outbound.order.cancelled.v1` 봉투에 echo. ecommerce 귀환 컨슈머(shipping `WmsShippingConfirmedConsumer`/`WmsOutboundCancelledConsumer`, order `WmsOutboundCancelledConsumer`)가 `TenantContext` 바인딩(봉투 부재 시 `orderNo` 로 로컬 행 폴백, D8) + `finally` clear. D5 correlation-round-trip 패턴의 *실현*(신규 결정 아님).

> **셀러 온보딩 흐름 + 실 IAM provisioning (facet f) = 실현됨** ([ADR-MONO-042](../../../../docs/adr/ADR-MONO-042-ecommerce-seller-onboarding-iam-provisioning.md) / TASK-BE-402) — §3.1 참조. 잔여 facet f 후속(연기): IAM→셀러 역방향 `account.status.changed` 투영, 셀러 self-service 온보딩 표면.

---

## 참조

- [ADR-MONO-030](../../../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) — 결정 (D1-D8, D4 정정본)
- [ADR-MONO-019](../../../../docs/adr/ADR-MONO-019-platform-console-customer-tenant-model.md) D5 — entitlement-trust 게이트 진화 (재사용)
- [ADR-MONO-025](../../../../docs/adr/ADR-MONO-025-abac-data-scope-generalization.md) — `org_scope` ABAC (셀러-스코프 read 형태)
- [ADR-MONO-032](../../../../docs/adr/ADR-MONO-032-unified-identity-roles-model.md) · [ADR-MONO-035](../../../../docs/adr/ADR-MONO-035-operator-auth-unification-model.md) — 통합 identity (`roles` 유일 축; 레거시 `account_type` 평면 대체)
- [iam-integration.md](../integration/iam-integration.md) — 기존 IAM 통합(고정슬러그 게이트·`roles` 평면·auth-service 제거)
- [`rules/traits/multi-tenant.md`](../../../../rules/traits/multi-tenant.md) — M1-M7
- 참조 구현: scm `inventory-visibility`/`procurement` · erp `approval` (row-level `tenant_id`)
- [product-service architecture.md](../services/product-service/architecture.md) · [order-service architecture.md](../services/order-service/architecture.md) — 서비스별 테넌시 섹션
