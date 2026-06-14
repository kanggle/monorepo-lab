# Task ID

TASK-BE-368

# Title

ecommerce **promotion-service tenant isolation** — ADR-MONO-030 **Step 4** / ADR-MONO-031 **Phase 3a** (the backend prerequisite that gates console promotions-area absorption). Add **row-level `tenant_id`** to promotion-service (`promotions`, `coupons`), propagate the request tenant context (`X-Tenant-Id`), thread `tenant_id` onto the **outbox** events (M5), seed the **default tenant** for net-zero/standalone degradation, and prove isolation with an **M6 cross-tenant-leak Testcontainers IT**. Replicates the proven product/order/user pattern (TASK-BE-357 order-service outbox + TASK-BE-367 user-service). **Gateway gate + `multi-tenant` trait already landed in BE-357 — not re-done here.**

# Status

done

# Owner

backend

# Task Tags

- code
- multi-tenant
- tenancy
- isolation
- ecommerce
- adr-030
- adr-031
- migration

---

# Dependency Markers

- **선행 (prerequisite)**: TASK-BE-356 (Step 1 spec — `specs/features/multi-tenancy-and-marketplace.md` §2 SoT) + **TASK-BE-357** (gateway `TenantClaimValidator` entitlement-trust + `X-Tenant-Id` injection + `multi-tenant` trait — DONE, reused unchanged) + **TASK-BE-367** (user-service — the most recent same-pattern reference). ADR-MONO-031 D4 Phase 3 ("promotions absorption gated on promotion-service `tenant_id` migration").
- **mirror / reference**: **TASK-BE-357 order-service** (the **outbox**-based M5 pattern — `tenant_id` on the outbox event envelope; system/saga paths tenant-agnostic by unique id) + **TASK-BE-367 user-service** (`TenantContext`/`TenantContextFilter`, entity `tenant_id`, repo isolation, M6 IT). promotion-service is **outbox-based** (V3 outbox + OutboxWriter), so order-service is the closer event reference (not user-service's direct-Kafka publish).
- **blocks / 후속**: **ADR-031 Phase 3b** — console promotions-area absorption (`features/ecommerce-ops` promotions — **full CRUD**: create/update/delete + coupon-issue, mirrors products/orders slice, NOT read-only like users).

# Goal

promotion-service 를 단일 스토어 → 멀티테넌트로 진화 — gateway 가 주입하는 `X-Tenant-Id`(BE-357) 를 요청 컨텍스트로 보관하고, `promotions`/`coupons` 가 row 로 격리되며, 기존 동작은 default-tenant 시드로 byte-identical 유지(net-zero), cross-tenant 누설은 M6 IT 로 증명. **핵심 동기**: ADR-031 Phase 3 콘솔 promotions 흡수의 백엔드 선행 — operator-plane 조회(`/api/promotions`)가 테넌트로 격리되어야 콘솔이 테넌트-안전한 promotions CRUD 표면을 렌더할 수 있다. **SoT = `specs/features/multi-tenancy-and-marketplace.md` §1·§2 + `specs/services/promotion-service/architecture.md` Multi-Tenancy 섹션(본 task 가 추가).**

# Scope

## In Scope

### A. promotion-service tenant_id (V6 migration)
- `V6__add_tenant_id.sql`: `promotions`, `coupons` 에 `tenant_id VARCHAR(64) NOT NULL` — **zero-downtime 3-step**(ADD nullable → 백필 `'ecommerce'` → SET NOT NULL) + 복합 인덱스(`(tenant_id, status)` promotion 목록, `(tenant_id, user_id)` coupon 조회 패턴). `outbox`/`processed_events` 는 메시징 인프라 — tenant 는 봉투 payload 로 전파(컬럼 불요, order-service V8 와 동일).
- 두 JPA 엔티티(`PromotionJpaEntity`/`CouponJpaEntity`, `infrastructure/persistence/entity/`)에 `@Column(name="tenant_id", nullable=false, updatable=false, length=64)` + `fromDomain(domain, tenantId)`; 매퍼/save 경로가 `TenantContext.currentTenant()` 주입. clean 도메인 모델(`Promotion`/`Coupon`)은 불변.

### B. 요청 컨텍스트 (M2 layer 2/3)
- `domain/tenant/TenantContext`(framework-free ThreadLocal, `DEFAULT_TENANT_ID="ecommerce"`) + `interfaces/.../filter/TenantContextFilter`(`@Order(HIGHEST_PRECEDENCE)` OncePerRequestFilter, `X-Tenant-Id` → context, finally clear). user-service 와 동일 형태(DDD 패키지명에 맞춤).
- **모든 read `WHERE tenant_id = <context>`**:
  - **admin/operator-plane**(콘솔이 소비): `PromotionJpaRepository` `findActive`/`findScheduled`/`findEnded`/`findAll`(목록) + `findById`(단건) → tenant 필터. cross-tenant 단건 = **404**(M3). `findByIdForUpdate`(update/delete/issue 전 락) → tenant 필터(cross-tenant write 도달 불가). `existsByPromotionId` → tenant 필터.
  - **consumer-plane**: `CouponJpaRepository` `findByUserId`/`findByUserIdAndStatus`(GET /api/coupons/me) + `findByIdForUpdate`(apply) → tenant 필터; cross-tenant = 404.
  - **system/batch path = tenant-agnostic 유지**(order `findByIdAcrossTenants` 선례): `findExpiredIssuedCoupons`(만료 배치 sweep — 전역 운영성) + `findByOrderIdAndStatus`(OrderCancelled 복구 — orderId 전역 unique). 단, 변이하는 row 의 `tenant_id` 보존 + 발행 이벤트에 그 row 의 tenant 사용.
- write 는 컨텍스트의 `tenant_id` 주입(엔티티 `fromDomain` 경유); coupon issue(`saveAll`)는 부모 promotion 의 tenant 비정규화.

### C. 이벤트(M5 — outbox)
- `CouponUsedEvent`/`CouponExpiredEvent` **봉투에 `tenant_id`** 추가. publish 시점 요청/row 테넌트를 봉투에 실음(outbox payload). order-service outbox 봉투 `tenant_id` 패턴.
- **소비 이벤트**(`order.order.cancelled` → `OrderCancelledEventConsumer`): 봉투의 `tenant_id`(optional — order 마이그 됨/안 됨 무관하게 방어) 를 `TenantContext` 에 바인딩한 뒤 `restoreCouponsByOrderId` 처리, finally clear. 부재 시 default. (orderId 로 찾으므로 system path = tenant-agnostic, 복구 coupon 의 tenant 보존.)
- 배치 만료 job: 만료시키는 coupon 의 `tenant_id` 를 `CouponExpiredEvent` 봉투에 실음(row 에서 읽음).

### D. degradation + 회귀
- **default-tenant 시드/백필**(A) → 기존 동작 byte-identical(net-zero). **standalone**: `X-Tenant-Id` 부재 → `currentTenant()` = default resolve(degrade).
- **M6 cross-tenant-leak Testcontainers IT**(`MultiTenantIsolationIntegrationTest`): 테넌트 A promotion/coupon 이 B 컨텍스트로 **안 보임**(목록 제외 + 단건 404); net-zero(헤더 부재=default); 발행 outbox 이벤트 봉투에 요청/row 테넌트 `tenant_id`. (Docker-free `:check` 미포착 → Testcontainers IT 권위 — `feedback_spring_boot_diagnostic_patterns` §14-17.)

## Out of Scope

- **gateway `TenantClaimValidator`/`JwtHeaderEnrichmentFilter` 진화** — BE-357 완료(주입된 `X-Tenant-Id` 소비만).
- **`PROJECT.md` `multi-tenant` trait** — BE-357 완료.
- **`seller_id` / seller 축** — promotion 은 테넌트-스코프 운영 엔티티(셀러 귀속 아님). 해당 없음.
- 나머지 미마이그 서비스(cart/payment/shipping/review/search/notification) `tenant_id` — 각자 Step 4 task.
- **order/auth `tenant_id` 봉투 스레딩**(소비 측은 optional 수용) — 각 서비스 마이그레이션 task.
- **콘솔 promotions 흡수**(ADR-031 Phase 3b) — 후속 platform-console task.

# Acceptance Criteria

- **AC-1** `promotions`/`coupons` 에 `tenant_id VARCHAR(64) NOT NULL`(V6, 백필 `'ecommerce'`); 3-step; `(tenant_id, …)` 인덱스. `outbox`/`processed_events` 미변경.
- **AC-2** 두 엔티티에 `tenant_id`(`updatable=false`); write 가 `TenantContext.currentTenant()` 주입; 도메인 모델 불변.
- **AC-3** **admin operator-plane** 조회(`/api/promotions` 목록·단건·`findByIdForUpdate`)가 `tenant_id` 격리 — 타 테넌트 promotion 목록 제외 + 단건/cross-tenant write **404**(M3). consumer coupon 조회도 `tenant_id` 필터. 배치/OrderCancelled-복구 system path 는 tenant-agnostic 유지(unique id) but row tenant 보존.
- **AC-4** `CouponUsed`/`CouponExpired` outbox 봉투에 `tenant_id`(M5); 소비 `OrderCancelled` 봉투 `tenant_id`(부재=default) 를 컨텍스트로 바인딩.
- **AC-5** **M6**: cross-tenant-leak Testcontainers IT GREEN — A promotion/coupon 이 B 컨텍스트로 안 보임(목록/단건/404).
- **AC-6** **net-zero**: default-tenant 백필 + 부재=default resolve 로 기존 동작 불변(기존 IT/단위 회귀 0); standalone=default.
- **AC-7** gateway/`PROJECT.md` trait 미변경(BE-357); `seller_id` 미포함; 콘솔 흡수 미포함(Phase 3b).

# Related Specs

- `specs/features/multi-tenancy-and-marketplace.md` §1·§2 (SoT) + `specs/services/promotion-service/architecture.md` 「Multi-Tenancy」 섹션(본 task 가 추가)
- `specs/integration/iam-integration.md`(게이트 — BE-357 완료, 참조만) + `rules/traits/multi-tenant.md` M1-M7
- 참조: TASK-BE-357 order-service(outbox 봉투 `tenant_id`·system path tenant-agnostic) + TASK-BE-367 user-service(`TenantContext`/M6 IT)

# Related Contracts

- `specs/contracts/events/promotion-events.md`(또는 해당 이벤트 계약) — `CouponUsed`/`CouponExpired` 공통 봉투에 `tenant_id` 추가(M5). 본 task 가 계약 먼저 갱신 후 구현(Source-of-Truth-first).
- `specs/contracts/http/promotion-api.md` — `tenant_id` 는 요청/응답 필드 아님(`X-Tenant-Id` claim 파생, 표면 불변). HTTP 계약 변경 없음.

# Edge Cases

- **net-zero / standalone**: `X-Tenant-Id` 부재 → `currentTenant()` = `'ecommerce'` → 단일 스토어 동작 불변. fail-closed 금지.
- **PESSIMISTIC_WRITE 락**: `findByIdForUpdate`(promotion update/delete/issue, coupon apply)는 락 전에 tenant 필터 → cross-tenant 락/write 도달 불가.
- **outbox 봉투 tenant**: order-service 처럼 봉투에 `tenant_id` — 소비자가 payload 파싱 없이 라우팅/스코프. 배치 만료는 row 의 tenant, 사용은 요청 tenant.
- **OrderCancelled 소비**: order 미마이그 → 봉투 `tenant_id` 부재 가능 → optional 수용, 부재=default. orderId 전역 unique 라 system path tenant-agnostic 안전.
- **coupon 부모 promotion tenant**: issue 시 coupon.tenant_id = 부모 promotion.tenant_id(요청 컨텍스트와 일치).
- **Docker-free `:check` miss**: Testcontainers IT 만 적발(feedback §14-17) → 로컬 IT 완주 후 CI.

# Failure Scenarios

- `X-Tenant-Id` 컨텍스트를 admin read/`findByIdForUpdate` 필터에 안 걸면 cross-tenant 누설/탈취(M2 layer3 위반) → M6 IT 적발(AC-3/AC-5).
- default-tenant 백필 누락하고 NOT NULL → 기존 row 마이그 실패 → 3-step 필수.
- fail-closed 로 빈 컨텍스트 거부 → standalone·기존 단위 테스트 파손 → 빈=default resolve.
- 배치/OrderCancelled system path 를 tenant-scoped 로 바꾸면 전역 만료/복구가 default-tenant 만 처리 → tenant-agnostic 유지(unique id) + row tenant 보존.

# Notes

- 분석=Opus 4.8 / **구현 권장=Opus** (멀티테넌트 격리 + DDD + outbox 이벤트 스레딩 + PESSIMISTIC 락 + 동시성 + Testcontainers IT — 복잡 도메인; BE-357 order-service(outbox)+BE-367 패턴 복제). Testcontainers IT = Rancher Desktop + `DOCKER_API_VERSION=1.44`(`project_testcontainers_docker_desktop_blocker` — **2026-06-14 호스트 블로커 재발**, 로컬 IT 실행 불가 가능 → 작성·컴파일 GREEN 으로 두고 CI Linux 가 권위). ecommerce `:check`=Docker-free(`@Tag(integration)` 제외).
- ADR-031 Phase 3 = (a) 본 task(백엔드 tenant_id 선행) **then** (b) 콘솔 promotions **CRUD** 흡수(products/orders 슬라이스 미러). 본 task 가 (a).
