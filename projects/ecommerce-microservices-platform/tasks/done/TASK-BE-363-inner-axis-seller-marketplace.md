# Task ID

TASK-BE-363

# Title

ecommerce **inner-axis seller (marketplace) slice** — ADR-MONO-030 **Step 3** (the multi-vendor code: `seller` aggregate + `seller_id` ownership/attribution + seller-scoped ABAC read, product + order). Builds on Step 2's row-level `tenant_id` (BE-357 A/B/C). Adds the inner **seller axis** nested under `tenant_id`: a net-new ecommerce-local `seller` aggregate keyed `(tenant_id, seller_id)`, product ownership, **order-line** attribution, seller-scoped reads in the **ABAC `org_scope` shape** (ADR-025, net-zero), and a **default-seller** seed for standalone single-seller degradation. **Settlement/commission, console integration, seller onboarding flow, and the remaining 11 services are NOT in scope** (Step 4).

# Status

done

# Owner

backend

# Task Tags

- code
- test
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

ADR-MONO-030 의 **안쪽(seller) 축** 을 구현한다. Step 2(BE-357 A/B/C)가 product-service + order-service 에 row-level `tenant_id`(바깥 격리 축)를 심었으니, Step 3 은 **한 테넌트 내부의 멀티벤더(쿠팡식)** 를 추가한다 — `tenant_id` 가 **격리**하고 그 안에서 `seller_id` 가 **귀속/필터**하는 *isolate-then-attribute* 복합 스코프.

이 task 가 끝나면: "한 테넌트의 마켓플레이스 안에서 여러 셀러가 공유 카탈로그에 상품을 등록하고(상품 소유 = `(tenant_id, seller_id)`), 한 소비자 주문이 여러 셀러 상품을 포함하면 **주문 항목(order-line) 단위로** 각 셀러에 귀속되며, 셀러(OPERATOR)는 자기 데이터만(ABAC 셀러-스코프 read, net-zero) 보고, 셀러 정보가 없는 standalone 배포는 **단일 셀러로 degrade**(오늘 동작 byte-identical)" 가 참이 된다.

기준 문서(Step 1 source-of-truth) = [`specs/features/multi-tenancy-and-marketplace.md`](../../specs/features/multi-tenancy-and-marketplace.md) **§3 (안쪽 축 — 셀러)** + ADR-MONO-030 **D3 / D6 Step 3**.

---

# Scope

## In Scope (product-service + order-service only — the ADR §D5 slice)

- **`seller` 애그리거트 (product-service, net-new)** — ecommerce-local 엔티티, 키 `(tenant_id, seller_id)`. v1 **최소 라이프사이클**(등록 + 활성 상태). 셀러는 **한 테넌트 내부의 참여자**(격리 경계 아님 — ADR-030 D3-B 기각). Flyway 마이그레이션(product 다음 버전).
- **상품 소유권** — product-service 의 상품(소유 단위 엔티티)에 `seller_id NOT NULL` 추가. `(tenant_id, seller_id)` 가 소유 키. 기존 상품 → **default-seller 시드** 백필 후 NOT NULL(2-step, Step 2 의 `tenant_id` V13 패턴 재사용).
- **주문 항목 귀속** — order-service 의 `order_items`(주문 항목)에 `seller_id` 추가. **order 헤더는 `tenant_id` 만, 각 항목은 그 상품의 `seller_id`**(주문 시점에 상품 참조에서 캡처, 라인에 불변 적재). 한 주문이 여러 셀러 상품을 포함 가능(공유 카탈로그/장바구니). Flyway 마이그레이션(order 다음 버전).
- **셀러-스코프 read (ABAC `org_scope` 형태, ADR-025 재사용)** — OPERATOR 가 자기 셀러 데이터만 보는 필터. `seller_id` 를 데이터-스코프 축으로. **net-zero**(ADR-025 핵심): 스코프 claim 부재 / `'*'` = **무필터**(테넌트 운영자 전체 조망, fail-OPEN), restricted = `seller_id` 필터. **항상 `tenant_id` 필터 *다음*에 적용**(복합 = isolate-then-attribute).
- **default-seller 시드 + standalone degradation (D8)** — default tenant 당 단일 default seller, 기존 상품 전부 귀속 → 오늘 단일-셀러 동작 불변. 플랫폼 IAM 부재(standalone) = seller 스코프 없음 → 무필터 단일-셀러로 degrade.
- **계약 갱신**(§5 영향 범위 구현): `seller_id` 를 OPERATOR 표면(상품 등록/조회·주문 항목 응답)에 노출, CONSUMER 표면은 **읽기 전용 표시**. 이벤트 봉투(product/order 항목 페이로드)에 `seller_id`.
- **검증**: 셀러-스코프 ABAC read 단위 + 크로스-셀러 귀속/격리 테스트(셀러 a1 은 자기 상품/라인만, a2 것 안 보임; 단 공유 카탈로그 소비자뷰는 전부). **Step 2 의 M6 cross-tenant leak IT 회귀 GREEN 유지.**

## Out of Scope (→ ADR §3.4 Step 4)

- **셀러 정산/수수료(settlement/commission)** — 마켓플레이스 경제(payout) 일체. (ADR D5/D7 명시 보류 — `marketplace` 스코프는 "in-tenant seller 축만, 정산 보류".)
- **콘솔 통합** — `tenant_domain_subscription` `domain_key='ecommerce'` 시드 + 카탈로그 렌더(원래 "웹스토어 어드민을 콘솔에서" 요청). ecommerce 는 entitlement 행을 **의존성으로 참조만**, 시드/렌더는 Step 4.
- **셀러 온보딩 흐름 / 실 IAM `seller_id` provisioning** — 슬라이스는 OPERATOR 토큰의 `seller_id`/seller-scope claim 을 **신뢰**(ADR-025 org_scope claim plumbing 형태). 실제 IAM 셀러 계정 발급·온보딩 UI 는 보류.
- **나머지 11개 서비스**(cart/payment/promotion/shipping/review/search/notification/…) `seller_id`/`tenant_id` 전파.
- **ADR-022 이행 이벤트(`ecommerce.fulfillment.requested.v1` 등) `tenant_id`/`seller_id` 스레딩** — 슬라이스 범위 밖.
- **M7 per-tenant quota.** 바깥 축 `tenant_id`(Step 2 완료) 재작업.

---

# Acceptance Criteria

- [ ] AC-1 — product-service 에 `seller` 애그리거트(키 `(tenant_id, seller_id)`, 최소 라이프사이클)가 존재.
- [ ] AC-2 — 상품이 `seller_id NOT NULL`(테넌트 내부 소유)을 가지며, **default-seller 시드**가 기존 상품을 단일 default seller 로 백필 → 기존 동작 byte-identical.
- [ ] AC-3 — `order_items` 가 `seller_id` 를 가지며, **주문 시점에 주문된 상품의 셀러에서 캡처**된다. **다중 셀러 주문**에서 각 라인이 올바른 셀러에 독립 귀속(헤더는 `tenant_id` 만).
- [ ] AC-4 — 셀러-스코프 read: seller-scope claim 보유 OPERATOR 는 자기 `seller_id` row 만; **claim 부재 / `'*'` = 무필터(net-zero, fail-OPEN)**; CONSUMER 표면 불변(공유 카탈로그 전체, `seller_id` 읽기 전용).
- [ ] AC-5 — default-seller net-zero: standalone / seller-scope 부재 = 단일-셀러로 degrade, 오늘 동작 불변(D8, default-tenant 와 대칭).
- [ ] AC-6 — 복합 스코프 정합: `seller_id` 가 **테넌트 경계를 절대 안 넘음**(isolate-then-attribute — seller 필터는 항상 `tenant_id` 필터 내부). 크로스-셀러 테스트로 입증.
- [ ] AC-7 — 계약/이벤트 갱신: `seller_id` 가 OPERATOR HTTP 표면 + product/order 항목 이벤트 페이로드에. CONSUMER 계약은 읽기 전용.
- [ ] AC-8 — `:product-service:check` + `:order-service:check` BUILD SUCCESSFUL; CI Build & Test GREEN; **Step 2 M6 cross-tenant 회귀 GREEN 유지**; 기본 설정에서 기존 동작 회귀 0.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md`(이제 `multi-tenant` trait 보유, Step 2 적용) + `rules/common.md` + `rules/traits/{multi-tenant,transactional,integration-heavy}.md` + `rules/traits/multi-tenant.md` M1-M7.

- [`specs/features/multi-tenancy-and-marketplace.md`](../../specs/features/multi-tenancy-and-marketplace.md) **§3 안쪽 축 / §4 평면 / §6 슬라이스 검증** (Step 1 source-of-truth)
- [`docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md`](../../../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) **D3(seller 애그리거트) / D4(평면 정정본) / D6 Step 3 / D8(degradation)**
- [`docs/adr/ADR-MONO-025-abac-data-scope-generalization.md`](../../../../docs/adr/ADR-MONO-025-abac-data-scope-generalization.md) — `org_scope` ABAC 데이터-스코프(셀러-스코프 read 형태 + **net-zero** 불변)
- [`specs/services/product-service/architecture.md`](../../specs/services/product-service/architecture.md) · [`specs/services/order-service/architecture.md`](../../specs/services/order-service/architecture.md) — 서비스별 테넌시/셀러 섹션
- [`specs/integration/iam-integration.md`](../../specs/integration/iam-integration.md) — `account_type`(CONSUMER\|OPERATOR) 평면(셀러 축 = OPERATOR 평면 내부)
- 참조 구현: ADR-025 wms 3-엔티티 `org_scope` enforcement(ABAC 형태) · Step 2 BE-357 B/C 의 `TenantContext`/repo-chokepoint/필터 패턴

# Related Skills

- `.claude/skills/INDEX.md` — multi-tenant / ABAC / 마이그레이션 관련 확인

---

# Related Contracts

- `specs/contracts/events/product-events.md` — product 항목 페이로드에 `seller_id`(M5: `tenant_id` 는 Step 2 가 이미)
- `specs/contracts/events/order-events.md` — `order_items` 페이로드에 `seller_id`
- product-service / order-service HTTP 계약 — `seller_id` 를 OPERATOR 표면(상품 등록/조회·주문 항목 응답)에 노출; CONSUMER 표면 읽기 전용
- (의존성 참조, 미편집) `tenant_domain_subscription` `domain_key='ecommerce'` — 플랫폼 IAM 소유, 시드는 Step 4

---

# Target Service

- `product-service` (seller 애그리거트 + 상품 소유권 + 셀러-스코프 상품 read)
- `order-service` (order-line `seller_id` 귀속 + 셀러-스코프 주문 항목 read)

---

# Implementation Notes

- **Step 2 패턴 재사용(BE-357 B/C)**: product/order 양쪽에 이미 `TenantContext` + per-request 컨텍스트 필터(`TenantContextFilter`) + repo-chokepoint(`WHERE tenant_id = ?`)가 있다. Step 3 은 그 위에 **seller-scope 컨텍스트**(OPERATOR 토큰의 seller-scope claim → gateway 헤더, 예: `X-Seller-Scope`)를 **ADR-025 `org_scope` claim plumbing 형태**로 더하고, repo-chokepoint 의 read 필터를 `tenant_id` *그리고* (restricted 일 때) `seller_id` 로 확장한다. **seller 필터는 절대 단독 아님 — 항상 tenant 필터 내부**(AC-6).
- **ABAC net-zero(ADR-025 핵심, fail-OPEN)**: seller-scope claim 부재/`'*'` = 무필터. **fail-closed(빈 결과) 금지** — 테넌트 운영자(셀러 제약 없음)는 전체를 봐야 한다(F1).
- **order-line `seller_id` 캡처**: 주문 생성(placement) 시 기존 상품 참조 경로(가격/변형 조회 또는 상품 스냅샷)에서 그 상품의 `seller_id` 를 읽어 라인에 **denormalize, 불변 적재**. order-service 는 `seller` 애그리거트를 **소유하지 않음**(귀속 속성만 보유).
- **default-seller 마이그레이션(2-step, Step 2 미러)**: ① default tenant 당 default seller row INSERT + 상품/항목 `seller_id` 백필 → ② NOT NULL. 단일 트랜잭션 대량 백필 주의(F4).
- **마이그레이션 버전**: product = Step 2 가 V13 사용 → **V14**; order = Step 2 가 V8 사용 → **V9**(구현 시 최신 확인 — 동시 세션 충돌 주의).
- **평면(D4 정정본)**: `seller_id` 는 **OPERATOR 평면(`/api/admin/**`) 한정**. CONSUMER 는 셀러 권위 없음(상품을 *볼* 뿐) — CONSUMER 표면에 seller-scope 필터 미적용, `seller_id` 는 읽기 전용 표시.
- ⚠️ **IT 트랩(BE-293/294/357 동일)**: ecommerce 로컬 IT = `-PrunIntegration` 플래그 필요; product/order 의 bare-`@SpringBootTest` IT 는 multiple-`@SpringBootConfiguration` 잠복-red(이 task 범위 밖, 미수정); **CI 게이트 = Build & Test(unit+slice)**, ecommerce 전용 PR-gated IT 잡 없음.
- 모델 권장 = **Opus** (ADR D6 Step 3: 마켓플레이스 애그리거트 + ABAC 데이터-스코프 + 복합 스코프 정합 — isolation 인접 설계).

---

# Edge Cases

- **다중 셀러 주문**: 한 주문의 라인이 셀러 a1/a2 에 걸침 → 각 라인 독립 귀속(헤더 `tenant_id` 단일).
- **seller-scope 부재 OPERATOR**(테넌트 운영자): 무필터 전체 조망(net-zero, fail-OPEN — F1).
- **seller-scope = `'*'`**: 무필터(restricted 아님).
- **standalone(IAM 부재)**: seller-scope 없음 → default seller 단일-셀러 degrade.
- **레거시 상품(셀러 미지정)**: default-seller 시드로 귀속(NOT NULL 충족).
- **셀러 ⊂ 테넌트**: 테넌트 A 의 `seller_id` 가 테넌트 B 컨텍스트에서 절대 매칭/귀속 안 됨(AC-6).
- **경계값**: seller 필터 적용/미적용 분기(claim 형태 enum — ADR-025 닫힌 조건 형태 일관).

---

# Failure Scenarios

- **F1 — 셀러-스코프 over-filter**: 셀러 제약 없는 테넌트 운영자가 빈 결과로 잘못 필터됨(fail-closed) → 전체 조망 상실. → AC-4/AC-5 **net-zero(부재=무필터, fail-OPEN)**. ADR-025 불변 미러.
- **F2 — 크로스-테넌트 셀러 누설**: 테넌트 A 의 `seller_id` 가 테넌트 B 컨텍스트에 적용 → 격리 위반. → AC-6 복합(테넌트 필터 항상 선행) + 크로스-테넌트/셀러 테스트.
- **F3 — order-line 오귀속**: 라인이 잘못된 셀러(예: default)로 귀속 → 정산(Step 4) 기반 오염. → AC-3 주문 시점 상품 셀러에서 캡처 + 다중 셀러 테스트.
- **F4 — default-seller 마이그레이션 회귀**: 기존 상품 `seller_id` NOT NULL 위반 / 대량 백필 락. → 2-step(시드/백필 → NOT NULL), Step 2 패턴.
- **F5 — CONSUMER 평면 오염**: seller-scope 필터가 CONSUMER 표면에 새어 공유 카탈로그가 셀러로 잘림. → AC-4 CONSUMER 불변(OPERATOR 평면 한정).

---

# Test Requirements

- **unit**: 셀러-스코프 ABAC 필터(restricted=자기 `seller_id` 만 / 부재·`'*'`=무필터 net-zero) · order-line 셀러 캡처(단일·다중 셀러) · default-seller resolve(부재→default).
- **slice**: product 등록/조회 OPERATOR 표면 `seller_id` 노출 + CONSUMER 읽기 전용 · order 항목 응답 `seller_id`.
- **(선택) IT**(Testcontainers, `-PrunIntegration` 로컬): default-seller 마이그레이션 실적용 + 크로스-셀러 격리(셀러 a1 ≠ a2) + **Step 2 M6 cross-tenant 회귀 동반 GREEN**(테넌트 A ≠ B). ABAC 필터는 read-path 결정적이라 unit 우선, IT 는 마이그레이션/복합 스코프 검증용.

---

# Definition of Done

- [ ] `seller` 애그리거트 + 상품 소유권 + order-line 귀속 구현
- [ ] 셀러-스코프 ABAC read(net-zero) 구현
- [ ] default-seller 시드 + standalone degrade 확인(AC-5)
- [ ] 복합 스코프(seller ⊂ tenant) 입증(AC-6) + M6 회귀 GREEN
- [ ] 계약/이벤트 `seller_id` 갱신(AC-7)
- [ ] `:product-service:check` + `:order-service:check` GREEN, 회귀 0
- [ ] Ready for review
