# Task ID

TASK-BE-366

# Title

ecommerce product/order **admin 컨트롤러 operator-plane authorization 전환** — `validateAdminRole(X-User-Role==ADMIN)` 제거하고 gateway 의 operator-plane 게이트(`account_type=OPERATOR` for `/api/admin/**` + `tenant_id` 격리)에 위임. platform-console operator 가 ecommerce product/order 운영 CRUD 를 호출할 수 있게 하는 **ADR-MONO-031 Phase 1a 백엔드 선행** (계약 § 2.4.10). AdminProductController(write) + AdminProductImageController(전체) + AdminOrderController(read+write). **AdminSellerController 제외**(seller facet, Phase 1 밖).

# Status

ready

# Owner

backend

# Task Tags

- ecommerce
- multi-tenant
- adr-mono-031
- adr-mono-030
- console-integration
- authorization

---

# Dependency Markers

- **선행 (prerequisite, done)**:
  - `TASK-MONO-243` — AdminProductController **list** read leg 에서 이미 `validateAdminRole` 제거 + operator-plane 근거 javadoc(#1-3) 확립. 본 태스크는 그 패턴을 product **write/image + order read/write** 로 확장한다.
  - `TASK-MONO-252` — ADR-MONO-031 ACCEPTED + 계약 § 2.4.10 (product/order CRUD 바인딩). 본 태스크가 그 계약의 producer-side authorization 게이트(#1)를 해소.
- **근거 (ADR)**: [ADR-MONO-031](../../../../docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md) Phase 1a; [ADR-MONO-030](../../../../docs/adr/ADR-MONO-030-ecommerce-multivendor-marketplace-saas.md) Step 4 facet a-후속-2; `console-integration-contract.md` § 2.4.10 (Phase-1 producer-verify gate #1 = operator role mapping).
- **진단 (2026-06-13)**: gateway `AccountTypeEnforcementFilter`(order -2) 가 `/api/admin/**` 에 `account_type=OPERATOR` 를 **이미 강제**(CONSUMER 403); `JwtHeaderEnrichmentFilter`(order -1) 는 `X-User-Role` 을 JWT `roles`/`role` claim 에서만 채움(없으면 `""`). 콘솔 OPERATOR 토큰엔 ecommerce-local `ADMIN` role claim 이 없어 `validateAdminRole` 에서 403. 즉 컨트롤러 RBAC 은 gateway operator-plane 게이트와 **중복·모순**.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (multi-tenant authorization 경계 변경 + 광범위 테스트 영향 + M6 cross-tenant leak 회귀 보존).

---

# Goal

platform-console operator(IAM OIDC, `account_type=OPERATOR`, ecommerce-local `ADMIN` role claim 없음)가 ecommerce product/order 운영 CRUD 를 호출할 수 있도록, product/order admin 컨트롤러의 **ecommerce-local `validateAdminRole(X-User-Role=="ADMIN")` 게이트를 제거**하고, 이미 존재하는 gateway operator-plane 게이트(`AccountTypeEnforcementFilter` = `/api/admin/**` → `account_type=OPERATOR` + `TenantClaimValidator` non-blank `tenant_id` + 리포지토리 `WHERE tenant_id` 격리)에 authorization 을 위임한다. TASK-MONO-243 이 product **list** read leg 에 적용한 패턴(javadoc #1-3)을 write/image/order 로 확장하는 것. **net 효과**: operator-plane 은 더 열리고, multi-tenant 격리(M1-M7)는 불변, admin-dashboard(OPERATOR 토큰)는 더 관대해질 뿐 깨지지 않음.

# Scope

ecommerce **product-service + order-service presentation 계층만**. gateway/도메인/persistence 불변. 멀티테넌트 격리 불변.

## In scope

1. **`AdminProductController`** (`product-service`): write 엔드포인트(register, update, delete, addVariant, updateVariant, deleteVariant, adjustStock)에서 `validateAdminRole(userRole)` 호출 제거 + `@RequestHeader("X-User-Role") String userRole` 파라미터 제거. `validateAdminRole` private 메서드 + `ROLE_ADMIN` 상수 + `AccessDeniedException` import 가 미사용이면 제거. **list 는 이미 MONO-243 처리됨**. 각 write 메서드에 operator-plane 근거 javadoc(list 의 #1-3 패턴 요약 인용 — "authorization at gateway: AccountTypeEnforcementFilter operator + TenantClaimValidator + WHERE tenant_id; write-plane ecommerce-local RBAC intentionally not applied").
2. **`AdminProductImageController`** (`product-service`): 전체 5 엔드포인트(listImages, generateUploadUrl, registerImage, updateImage, deleteImage)에서 동일 제거. controller-level javadoc 으로 근거 1회.
3. **`AdminOrderController`** (`order-service`): getOrders, getOrder, changeStatus 3 엔드포인트에서 동일 제거. read(getOrders/getOrder)도 operator 에게 열림(product list 와 동형). controller-level javadoc.
4. **테스트 수정**: `AdminProductControllerTest`, `AdminProductImageControllerTest`, `AdminOrderControllerTest` 의 `X-User-Role==ADMIN` 통과 / 누락→403(`AccessDeniedException`) 가정을 **operator-plane 으로 갱신** — X-User-Role 헤더 없이 호출 성공을 단언. 기존 "403 when role missing/non-admin" 케이스는 **삭제**(더 이상 컨트롤러 책임 아님 — gateway 책임). 기존 happy-path 가 X-User-Role:ADMIN 을 보내면 그 헤더 제거.
5. **멀티테넌트 격리 회귀(M6)**: product/order 가 `tenant_id` 격리(ADR-030 Step 2/3)를 유지하는지 — 기존 cross-tenant leak IT 가 있으면 GREEN 유지 확인; 본 변경은 tenant 격리 코드(TenantContext/WHERE tenant_id)를 건드리지 않음을 단언.

## Out of scope

- **AdminSellerController** — seller facet, 계약 § 2.4.10 "out of this binding". validateAdminRole 유지.
- promotions/shippings/notifications/users 컨트롤러 — Phase 2~5 (각자 tenant_id 마이그레이션과 함께).
- console-web 흡수 (Phase 1b, TASK-PC-FE-078/079).
- gateway 필터 변경 — 이미 operator-plane 게이트 존재, 불변.

# Acceptance Criteria

- **AC-1**: AdminProductController write(7) + AdminProductImageController(5) + AdminOrderController(3) 에서 `validateAdminRole` 호출·메서드·`X-User-Role` 파라미터·미사용 import 제거. AdminSellerController 불변.
- **AC-2**: operator-plane 근거 javadoc 추가(MONO-243 #1-3 패턴, write-plane RBAC intentionally-not-applied 명시).
- **AC-3**: 각 ControllerTest 가 X-User-Role 헤더 **없이** write/read 성공을 단언; "role missing → 403" 케이스 제거.
- **AC-4**: 멀티테넌트 격리 불변 — tenant 격리 IT(있으면) GREEN; tenant scoping 코드 0-change.
- **AC-5 (빌드 GREEN)**: `:projects:ecommerce-microservices-platform:apps:product-service:check` + `:order-service:check` GREEN. Docker-free 빌드/단위 GREEN 후, Testcontainers IT 는 CI(Linux)에 위임(로컬 Docker IT 선택).
- **AC-6 (HARDSTOP)**: HARDSTOP-03 N/A(shared 미변경). 멀티테넌트 trait M1-M7 불변(M2 gate 는 gateway, 본 변경은 컨트롤러 중복 RBAC 만 제거).

# Related Specs / Code

- ADR: `docs/adr/ADR-MONO-031-...md` Phase 1a, `ADR-MONO-030-...md` Step 4.
- 계약: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.10 (gate #1).
- 코드:
  - `apps/product-service/.../presentation/controller/AdminProductController.java` (list = MONO-243 선례)
  - `apps/product-service/.../presentation/controller/AdminProductImageController.java`
  - `apps/order-service/.../presentation/AdminOrderController.java`
  - gateway(불변, 근거): `apps/gateway-service/.../filter/AccountTypeEnforcementFilter.java` + `JwtHeaderEnrichmentFilter.java`
- 테스트: `AdminProductControllerTest`, `AdminProductImageControllerTest`, `AdminOrderControllerTest`.

# Related Contracts

- `console-integration-contract.md` § 2.4.10 (consumer-side; 본 태스크는 producer-side authorization 게이트 해소). 신규 API/이벤트 계약 0(엔드포인트 시그니처 불변 — 헤더 게이트만 제거).

# Edge Cases / Failure Scenarios

- **admin-dashboard 회귀**: admin-dashboard 도 동일 엔드포인트 호출(OPERATOR 토큰). validateAdminRole 제거 시 더 관대해질 뿐(통과) → 회귀 없음. (admin-dashboard 가 X-User-Role:ADMIN 을 보내왔다면 그 헤더는 이제 무시됨 — 무해.)
- **tenant 격리 누출(M6, 가장 중요)**: authorization 게이트 제거가 tenant 격리를 건드리면 cross-tenant leak. → tenant scoping(WHERE tenant_id / TenantContext)은 **절대 미변경**; 격리 IT GREEN 확인.
- **미사용 import/메서드 잔존**: validateAdminRole/ROLE_ADMIN/AccessDeniedException 제거 후 `-Xlint`/컴파일 경고 — 전부 정리.
- **테스트 잔존 403 단언**: "role missing → 403" 케이스를 안 지우면 RED. 전수 제거.
- **order read 도 여는 것**: product list 와 동형(operator read). getOrders/getOrder 도 validateAdminRole 제거 — read 차별 없음.

# Notes

- 격리 worktree `monorepo-lab-ecom-be366` (브랜치 `task/be-366-ecommerce-operator-plane-admin-write`, base=origin/main `707afd236`). 메인 체크아웃 main 파킹 유지.
- 머지 후 close-chore: review→done Status 만, narrative 는 커밋. 3-dim 검증.
- 다음(Phase 1b): console-web `features/ecommerce-ops` 흡수(TASK-PC-FE-078 products / -079 orders) — 본 태스크 머지 후.
