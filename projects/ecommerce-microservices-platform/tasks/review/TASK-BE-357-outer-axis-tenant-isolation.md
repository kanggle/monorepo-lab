# Task ID

TASK-BE-357

# Title

ecommerce **outer-axis tenant isolation** — ADR-MONO-030 **Step 2** (the multi-tenant code, product + order slice). Evolve the gateway gate from fixed-slug to **entitlement-trust** (ADR-019 D5; mirror wms/scm), add **row-level `tenant_id`** to product-service + order-service, propagate the tenant context, thread `tenant_id` onto domain events (M5), seed a **default tenant** for net-zero/standalone degradation, prove isolation with an **M6 cross-tenant-leak Testcontainers IT**, and add the **`multi-tenant` trait** to `PROJECT.md` (ADR §D7 timing — with the code). **`seller_id` is NOT in scope** (Step 3 / BE-358).

# Status

review

# Owner

backend

# Task Tags

- code
- multi-tenant
- tenancy
- isolation
- ecommerce
- adr-030
- migration

---

# Dependency Markers

- **선행 (prerequisite)**: TASK-BE-356 (Step 1 spec foundation — `specs/features/multi-tenancy-and-marketplace.md` §2 바깥 축 is the SoT) + ADR-MONO-030 ACCEPTED/corrected (MONO-231/232).
- **mirror / reference**: wms/scm `TenantClaimValidator` entitlement-trust (post-ADR-019 D5) + **scm `inventory-visibility`/`procurement` + erp `approval` row-level `tenant_id`** migrations/entities/repos.
- **blocks / 후속**: TASK-BE-358 (Step 3 inner seller axis — `seller` aggregate + `seller_id` ownership/attribution + ABAC seller-scoped read) builds on this.

# Goal

product+order 슬라이스를 단일 스토어 → 멀티테넌트로 진화 — gateway 가 entitlement-trust 로 임의 `tenant_id` 를 신뢰하고, 두 서비스가 row 로 격리하며, 기존 단일 스토어 동작은 default-tenant 시드로 byte-identical 유지(net-zero), cross-tenant 누설은 M6 IT 로 증명. **Source-of-Truth = `specs/features/multi-tenancy-and-marketplace.md` §1·§2.**

# Scope

## In Scope

### A. Gateway gate evolution (entitlement-trust + dual-accept)
- `gateway-service/.../security/TenantClaimValidator.java`: `expectedTenantId.equals(tenantId)` (고정 슬러그) → **entitlement-trust** — JWKS-검증된 토큰의 **임의 well-formed `tenant_id` 수용**(blank/missing 만 거부). **Dual-accept**: 진화 중 레거시 `'ecommerce'` ∪ 임의 entitled `tenant_id` 양쪽 통과(ADR-019 D6 dual-accept 윈도우; default-tenant 시드와 함께 net-zero). 패턴 = wms/scm 의 entitlement-trust validator.
- `JwtHeaderEnrichmentFilter`: downstream 으로 **`X-Tenant-Id`** 헤더 주입(기존 `X-User-Id`/`X-Account-Type` 옆에).

### B. product-service tenant_id (V10 migration)
- `V10__add_tenant_id.sql`: `products`, `product_variants`, `inventory`, `categories` 에 `tenant_id VARCHAR NOT NULL` + **기존 row 백필 `'ecommerce'`**(default-tenant) + 복합 인덱스(`(tenant_id, …)` 조회 패턴). FK/unique 제약을 tenant 범위로 조정(예: SKU unique → `(tenant_id, sku)`).
- 엔티티(`domain/model` + `infrastructure/persistence` JPA 엔티티)에 `tenant_id` 필드.
- **요청 컨텍스트**: `X-Tenant-Id` 헤더 → request-scoped TenantContext; 모든 read 가 `WHERE tenant_id = <context>`, write 가 컨텍스트 `tenant_id` 주입. cross-tenant 단건 조회 = **404**(M3).
- **이벤트(M5)**: `ProductCreated`/`ProductUpdated`/`StockChanged` 등 outbox 봉투에 `tenant_id`.

### C. order-service tenant_id (V8 migration)
- `V8__add_tenant_id.sql`: `orders`, `order_items` 에 `tenant_id VARCHAR NOT NULL` + 백필 `'ecommerce'` + 인덱스. (`outbox`/`processed_events` 는 메시징 표준 — tenant 는 봉투 payload 로 전파, 컬럼 불요.)
- `Order`/`OrderItem` 도메인 + `OrderJpaEntity`/`OrderItemJpaEntity` 에 `tenant_id`.
- 요청 컨텍스트(`X-Tenant-Id`) + read 필터 + 404(M3).
- **이벤트(M5)**: `order.order.placed`/`order.order.cancelled` outbox 봉투에 `tenant_id`. **소비 이벤트**(`product.product.stock-changed`/`payment.payment.*`/`user.user.withdrawn`)의 saga 상태전이가 테넌트 경계를 안 넘도록 봉투의 tenant 컨텍스트 사용. **stuck-detector sweep 은 전역 유지 가능**(시스템 운영성), 복구 시 주문의 `tenant_id` 보존.

### D. degradation + 회귀
- **default-tenant 시드/백필**(B/C) → 기존 동작 byte-identical(net-zero). **standalone**: `tenant_id` claim 부재 → default tenant resolve(degrade).
- **M6 cross-tenant-leak Testcontainers IT** (product + order 각): 테넌트 A 데이터가 B 토큰/컨텍스트로 **안 보임(404)**, A 컨텍스트로만 보임. (ecommerce Docker-free `:check` 미포착 → `@SpringBootTest` Testcontainers IT 가 권위 — `feedback_spring_boot_diagnostic_patterns` §14-17.)

### E. PROJECT.md trait (ADR §D7 — 여기서)
- `projects/ecommerce-microservices-platform/PROJECT.md`: `traits` 에 **`multi-tenant`** 추가 + `## Out of Scope` 의 `multi-tenant` 줄 제거/조정. (이 시점에 추가 — 슬라이스 코드가 M1 을 만족시키므로. 나머지 11 서비스는 named migration backlog = §Out of Scope 에 "in-migration" 명시.)

## Out of Scope

- **`seller_id` / seller 애그리거트** — Step 3 (BE-358).
- 나머지 11개 서비스(cart/payment/promotion/shipping/review/search/notification/user/auth/admin-dashboard/web-store) `tenant_id` — Step 4.
- `tenant_domain_subscription` `domain_key='ecommerce'` 시드 · 콘솔 통합 — Step 4.
- **ADR-022 이행 이벤트**(`ecommerce.fulfillment.requested.v1` 등) `tenant_id` 스레딩 — Step 4 (cross-project).
- M7 per-tenant quota/rate-limit.

# Acceptance Criteria

- **AC-1** gateway `TenantClaimValidator` = entitlement-trust(임의 well-formed `tenant_id` 통과, blank/missing 거부) + dual-accept(레거시 `'ecommerce'` 포함); `X-Tenant-Id` 가 downstream 에 전파.
- **AC-2** product-service: `tenant_id NOT NULL` (V10, 백필 `'ecommerce'`) on products/variants/inventory/categories; 모든 read `WHERE tenant_id`; cross-tenant 단건=404; SKU 등 unique 가 tenant 범위.
- **AC-3** order-service: `tenant_id NOT NULL` (V8, 백필) on orders/order_items; read 필터; 404; outbox 이벤트 봉투에 `tenant_id`.
- **AC-4** **M6**: product + order 각 cross-tenant-leak Testcontainers IT GREEN — A 데이터가 B 컨텍스트로 안 보임.
- **AC-5** **net-zero**: default-tenant 백필 + dual-accept 로 기존 단일 스토어 동작 불변(기존 IT/단위 회귀 0); standalone(claim 부재)=default tenant.
- **AC-6** `PROJECT.md` 에 `multi-tenant` trait 추가; `## Out of Scope` 조정(11 서비스 in-migration 명시).
- **AC-7** `seller_id` 미포함(Step 3); 11 서비스/ADR-022 스레딩 미포함(Step 4).

# Related Specs

- `specs/features/multi-tenancy-and-marketplace.md` §1·§2 (SoT) + `specs/services/{product,order}-service/architecture.md` 「Multi-Tenancy & Marketplace」 섹션
- `specs/integration/iam-integration.md` (게이트 진화 대상) + `rules/traits/multi-tenant.md` M1-M7
- 참조: wms/scm `TenantClaimValidator` (entitlement-trust) · scm/erp row-level `tenant_id` 마이그레이션/엔티티/레포

# Related Contracts

- product/order **이벤트 계약**(봉투 `tenant_id` 추가) + HTTP 계약(요청 필드 아님, `X-Tenant-Id` claim 파생 — 표면 불변). 필드 추가는 본 task 가 계약 먼저 갱신 후 구현(Source-of-Truth-first).

# Edge Cases

- **dual-accept 윈도우**: 진화 직후 레거시 `'ecommerce'` 토큰과 신규 entitled `tenant_id` 토큰이 공존 → 게이트가 양쪽 통과, default-tenant 백필로 데이터 정합. (cleanup = 후속.)
- **unique 제약**: 기존 전역 unique(SKU 등)를 `(tenant_id, …)` 로 — 백필 시 충돌 없도록 default-tenant 단일값이라 안전.
- **소비 이벤트 tenant 컨텍스트**: saga 가 타 테넌트 주문/상품을 교차 처리하지 않도록 봉투 `tenant_id` 사용; 기존 이벤트(스레딩 전)는 default-tenant 로 해석.
- **stuck-detector**: 전역 sweep 유지(운영성) but 복구가 변이하는 주문의 `tenant_id` 보존.
- **Docker-free `:check` miss**: `@JsonInclude`/null/CHECK 제약 함정은 Testcontainers IT 만 적발(feedback §14-17) → 로컬 전수 IT 완주 후 CI.

# Failure Scenarios

- 게이트를 entitlement-trust 로 바꾸며 dual-accept/default-tenant 백필을 빠뜨리면 기존 `'ecommerce'` 토큰/데이터가 깨짐(golden path) → AC-5 net-zero 필수.
- `tenant_id` 컨텍스트 전파를 read 필터에 안 걸면 cross-tenant 누설(M2 layer3 위반) → M6 IT 가 적발해야 함(AC-4).
- `PROJECT.md` trait 를 Step 1(스펙) 단계에 넣었으면 11 미마이그 서비스 M1 미스분류 → 본 task(코드와 함께)에서 추가(ADR §D7).
- fail-closed 로 빈/누락 `tenant_id` 컨텍스트를 처리하면 standalone(claim 부재) golden path 가 깨짐 → 빈=default-tenant(net-zero) resolve.

# Notes

- 분석=Opus 4.8 / **구현 권장=Opus** (멀티테넌트 격리 + 게이트 진화 + 마이그레이션 + Testcontainers IT — 복잡 도메인). ⚠️**호스트 제약**: 큰 세션 + 무거운 Java 빌드 = JDT.LS OOM 캐스케이드(`env_jdtls_oom_cascade`) → **새 세션에서 구현 권장**(작업 유실 방지). Testcontainers IT = Rancher Desktop + `DOCKER_API_VERSION=1.44`(`project_testcontainers_docker_desktop_blocker`).
- **구현 분할 가능**(트랙터빌리티): A(게이트) → B(product) → C(order) 증분 PR. 각 net-zero/회귀-GREEN.
