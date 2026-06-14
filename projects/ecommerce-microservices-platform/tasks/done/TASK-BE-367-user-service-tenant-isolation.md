# Task ID

TASK-BE-367

# Title

ecommerce **user-service tenant isolation** — ADR-MONO-030 **Step 4** (per-area backend `tenant_id` migration) / ADR-MONO-031 **Phase 2a** (the backend prerequisite that gates console users-area absorption). Add **row-level `tenant_id`** to user-service (`user_profiles`, `user_addresses`, `wishlist_items`), propagate the request tenant context (`X-Tenant-Id`), thread `tenant_id` onto the published events (M5), seed the **default tenant** for net-zero/standalone degradation, and prove isolation with an **M6 cross-tenant-leak Testcontainers IT**. Replicates the proven product/order pattern (TASK-BE-357). **Gateway gate + `multi-tenant` trait already landed in BE-357 — not re-done here.**

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

- **선행 (prerequisite)**: TASK-BE-356 (Step 1 spec foundation — `specs/features/multi-tenancy-and-marketplace.md` §2 바깥 축 is the SoT) + **TASK-BE-357** (Step 2 — gateway `TenantClaimValidator` entitlement-trust + `JwtHeaderEnrichmentFilter` `X-Tenant-Id` injection + `multi-tenant` trait on `PROJECT.md` — all already DONE; this task reuses them wholesale, edits neither). ADR-MONO-030 (D6 Step 4) + ADR-MONO-031 (D4 Phase 2, "users absorption gated on user-service `tenant_id` migration").
- **mirror / reference**: **TASK-BE-357** product-service / order-service — `TenantContext`/`TenantContextFilter`, `tenant_id` entity columns, repository `WHERE tenant_id` isolation, M5 event-envelope threading, default-tenant backfill migration (V13/V8), M6 `MultiTenantIsolationIntegrationTest`.
- **blocks / 후속**: **ADR-031 Phase 2b** — console (platform-console) users-area absorption (`features/ecommerce-ops` users list/detail, `app/api/ecommerce/users/**` route handlers). The console operator-plane read (`GET /api/admin/users`) is now tenant-scoped, so the console can safely render a tenant-isolated users surface.

# Goal

user-service 를 단일 스토어 → 멀티테넌트로 진화 — gateway 가 주입하는 `X-Tenant-Id`(BE-357) 를 요청 컨텍스트로 보관하고, 세 테이블이 row 로 격리되며, 기존 단일 스토어 동작은 default-tenant 시드로 byte-identical 유지(net-zero), cross-tenant 누설은 M6 IT 로 증명. **핵심 동기**: ADR-031 Phase 2 콘솔 users 흡수의 백엔드 선행 — operator-plane 조회(`GET /api/admin/users`)가 테넌트로 격리되어야 콘솔이 테넌트-안전한 users 표면을 렌더할 수 있다. **Source-of-Truth = `specs/features/multi-tenancy-and-marketplace.md` §1·§2 + `specs/services/user-service/architecture.md` Multi-Tenancy 섹션.**

# Scope

## In Scope

### A. user-service tenant_id (V4 migration)
- `V4__add_tenant_id.sql`: `user_profiles`, `user_addresses`, `wishlist_items` 에 `tenant_id VARCHAR(64) NOT NULL` — **zero-downtime 3-step**(ADD nullable → 백필 `'ecommerce'`(default-tenant) → SET NOT NULL) + 복합 인덱스(`(tenant_id, status)` admin 목록 조회 패턴, `(tenant_id, user_id)` 자식 테이블). 자식 테이블(`user_addresses`/`wishlist_items`)의 `tenant_id` 는 부모(`user_profiles`)에서 비정규화 — 기존 FK·unique 유지(default-tenant 단일값이라 충돌 없음).
- 세 JPA 엔티티(`UserProfileJpaEntity`/`AddressJpaEntity`/`WishlistItemJpaEntity`)에 `@Column(name="tenant_id", nullable=false, updatable=false, length=64)` 필드 + `fromDomain(domain, tenantId)`; 매퍼가 `TenantContext.currentTenant()` 주입. `tenant_id` 는 **persistence + event 레이어에만** — clean 도메인 모델(`UserProfile`/`Address`/`WishlistItem`)은 불변(product/order 와 동일).

### B. 요청 컨텍스트 (M2 layer 2/3)
- `domain/tenant/TenantContext`(framework-free ThreadLocal, `DEFAULT_TENANT_ID="ecommerce"`, `set/currentTenant/clear`) + `presentation/filter/TenantContextFilter`(`@Order(HIGHEST_PRECEDENCE)` `OncePerRequestFilter`, `X-Tenant-Id` 헤더 → `TenantContext`, finally clear). product-service 와 동일 형태.
- **모든 read 가 `WHERE tenant_id = <context>`**:
  - **admin/operator-plane**(Phase 2 콘솔이 소비): `findByStatus`/`findByEmailContaining`/`findByStatusAndEmailContaining`/`findAll` → `tenant_id` 필터. `AdminUserController#getUser`(단건) → cross-tenant = **404**(M3).
  - **consumer-plane**(이미 user 스코프, 방어 심층): `findByUserId`(/me), address `findByIdAndUserId`/`findAllByUserId`/`countByUserId`/`unmarkDefaultByUserId`/delete, wishlist `existsByUserIdAndProductId`/`findByUserIdAndProductId`/`findAllByUserId`/`deleteAllByUserId`/delete → `tenant_id` 필터.
- write 는 컨텍스트의 `tenant_id` 주입(엔티티 `fromDomain` 경유).

### C. 이벤트(M5)
- `UserProfileUpdatedEvent`/`UserWithdrawnEvent` **봉투에 `tenant_id`**(`@JsonProperty("tenant_id")`). publish 시점 요청 테넌트를 Spring 이벤트(`UserProfileUpdatedSpringEvent`/`UserWithdrawnSpringEvent`)에 담아 전달 → Kafka publisher 가 봉투에 실음(AFTER_COMMIT ThreadLocal 타이밍 회피). 표준 봉투 = `event_id`/`event_type`/`occurred_at`/`source`/**`tenant_id`**/`payload`.
- **소비 이벤트**(`auth.user.signed-up` → `UserSignedUpConsumer`/`UserSignedUpHandler`): 봉투의 `tenant_id`(optional — auth 미마이그) 를 `TenantContext` 에 바인딩한 뒤 프로필 생성, finally clear. 부재 시 default-tenant resolve(net-zero; auth `tenant_id` 스레딩은 Step 4).

### D. degradation + 회귀
- **default-tenant 시드/백필**(A) → 기존 동작 byte-identical(net-zero). **standalone**: `X-Tenant-Id` 부재 → `currentTenant()` = default tenant resolve(degrade).
- **M6 cross-tenant-leak Testcontainers IT**(`MultiTenantIsolationIntegrationTest`): 테넌트 A 사용자/프로필이 B 컨텍스트로 **안 보임**(목록 제외 + 단건 404), A 컨텍스트로만 보임; net-zero(헤더 부재 = default); 발행 이벤트 봉투에 요청 테넌트 `tenant_id`. (ecommerce Docker-free `:check` 미포착 → `@SpringBootTest` Testcontainers IT 가 권위 — `feedback_spring_boot_diagnostic_patterns` §14-17.)

## Out of Scope

- **gateway `TenantClaimValidator` / `JwtHeaderEnrichmentFilter` 진화** — BE-357 에서 이미 완료(이 task 는 주입된 `X-Tenant-Id` 소비만).
- **`PROJECT.md` `multi-tenant` trait 추가** — BE-357 에서 이미 완료.
- **`seller_id` / seller 축** — user 는 셀러 귀속 대상 아님(소비자/프로필 도메인). 해당 없음.
- 나머지 미마이그 서비스(cart/payment/promotion/shipping/review/search/notification/auth/admin-dashboard/web-store) `tenant_id` — 각자의 Step 4 task.
- **auth-service `auth.user.signed-up` 봉투 `tenant_id` 스레딩**(소비 측은 optional 수용) — auth 마이그레이션 task.
- **콘솔 users-area 흡수**(ADR-031 Phase 2b) — 후속 platform-console task.

# Acceptance Criteria

- **AC-1** `user_profiles`/`user_addresses`/`wishlist_items` 에 `tenant_id VARCHAR(64) NOT NULL`(V4, 백필 `'ecommerce'`); 3-step 무중단 마이그레이션; `(tenant_id, …)` 인덱스.
- **AC-2** 세 엔티티에 `tenant_id`(`updatable=false`); write 가 `TenantContext.currentTenant()` 주입; 도메인 모델 불변.
- **AC-3** **admin operator-plane** 조회(`/api/admin/users` 목록·단건)가 `tenant_id` 로 격리 — 타 테넌트 사용자 목록 제외 + 단건 **404**(M3). consumer-plane 조회도 `tenant_id` 필터(방어 심층).
- **AC-4** `UserProfileUpdated`/`UserWithdrawn` 봉투에 `tenant_id`(M5); 소비 `UserSignedUp` 봉투 `tenant_id`(부재=default) 를 컨텍스트로 바인딩.
- **AC-5** **M6**: cross-tenant-leak Testcontainers IT GREEN — A 데이터가 B 컨텍스트로 안 보임(목록/단건/404).
- **AC-6** **net-zero**: default-tenant 백필 + 부재=default resolve 로 기존 단일 스토어 동작 불변(기존 IT/단위 회귀 0); standalone(헤더 부재)=default tenant.
- **AC-7** gateway/`PROJECT.md` trait 미변경(BE-357); `seller_id` 미포함(user 도메인 무관); 콘솔 흡수 미포함(Phase 2b).

# Related Specs

- `specs/features/multi-tenancy-and-marketplace.md` §1·§2 (SoT) + `specs/services/user-service/architecture.md` 「Multi-Tenancy」 섹션(본 task 가 추가)
- `specs/integration/iam-integration.md`(게이트 — BE-357 에서 진화 완료, 참조만) + `rules/traits/multi-tenant.md` M1-M7
- 참조: TASK-BE-357 product-service/order-service `tenant_id` 마이그레이션/엔티티/레포/`TenantContext`/M6 IT

# Related Contracts

- `specs/contracts/events/user-events.md` — 공통 봉투에 `tenant_id` 추가(M5). 본 task 가 계약 먼저 갱신 후 구현(Source-of-Truth-first).
- `specs/contracts/http/user-api.md` — `tenant_id` 는 요청/응답 필드 아님(`X-Tenant-Id` claim 파생, 표면 불변). HTTP 계약 변경 없음.

# Edge Cases

- **net-zero / standalone**: `X-Tenant-Id` 부재(standalone, 백그라운드 컨슈머, 단위 테스트) → `currentTenant()` = `'ecommerce'`(default) → 단일 스토어 동작 불변. fail-closed(빈 컨텍스트 거부) 금지.
- **자식 테이블 비정규화**: `user_addresses`/`wishlist_items` 의 `tenant_id` 는 부모 프로필에서 비정규화 — 기존 FK(`user_id` → `user_profiles`)·unique 유지. 백필이 단일 default-tenant 라 위젯 충돌 없음.
- **AFTER_COMMIT publisher**: `@TransactionalEventListener(AFTER_COMMIT)` 가 ThreadLocal 클리어 이후 실행될 위험 → 요청 처리 중 Spring 이벤트에 `tenant_id` 를 담아 전달(서비스 레이어에서 `currentTenant()` 캡처).
- **소비 `UserSignedUp`**: auth 미마이그 → 봉투 `tenant_id` 부재 가능 → optional 수용, 부재 시 default-tenant(net-zero). auth 스레딩은 Step 4.
- **user_id 전역 unique**: 한 사용자(IAM userId)는 한 테넌트 소속 — `uq_user_profiles_user_id` 전역 unique 유지(테넌트 범위로 좁힐 필요 없음; `tenant_id` 는 격리 컬럼).
- **Docker-free `:check` miss**: null/CHECK/`@JsonInclude` 함정은 Testcontainers IT 만 적발(feedback §14-17) → 로컬 IT 완주 후 CI.

# Failure Scenarios

- `X-Tenant-Id` 컨텍스트 전파를 admin read 필터에 안 걸면 cross-tenant 누설(M2 layer3 위반, 콘솔이 타 테넌트 사용자 조망) → M6 IT 가 적발해야 함(AC-3/AC-5).
- default-tenant 백필을 빠뜨리고 `NOT NULL` 을 걸면 기존 row 마이그레이션 실패 → 3-step(ADD→백필→NOT NULL) 필수.
- fail-closed 로 빈/누락 `tenant_id` 컨텍스트를 거부하면 standalone(헤더 부재) golden path·기존 단위 테스트가 깨짐 → 빈=default-tenant(net-zero) resolve.
- AFTER_COMMIT publisher 가 클리어된 ThreadLocal 을 읽어 default-tenant 로 잘못 라벨 → Spring 이벤트로 `tenant_id` 캡처-전달.

# Notes

- 분석=Opus 4.8 / **구현 권장=Opus** (멀티테넌트 격리 + 마이그레이션 + 이벤트 스레딩 + Testcontainers IT — 복잡 도메인, 단 BE-357 의 검증된 패턴 복제라 기계적 비중 큼). ⚠️**호스트 제약**: 큰 세션 + 무거운 Java 빌드 = JDT.LS OOM 캐스케이드(`env_jdtls_oom_cascade`). Testcontainers IT = Rancher Desktop + `DOCKER_API_VERSION=1.44`(`project_testcontainers_docker_desktop_blocker`).
- ADR-031 Phase 2 = (a) 본 task(백엔드 tenant_id 선행) **then** (b) 콘솔 users 흡수. 본 task 가 (a).
