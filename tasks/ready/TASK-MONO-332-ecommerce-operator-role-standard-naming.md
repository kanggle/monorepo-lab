# Task ID

TASK-MONO-332

# Title

이커머스 도메인 운영자 롤 `ADMIN` → `ECOMMERCE_OPERATOR` 표준 네이밍 정렬 — 플랫폼 표준 `<DOMAIN>_OPERATOR`(WMS/SCM/ERP/FINANCE/FAN_OPERATOR)에 맞춰 이커머스만 남아있던 레거시 `ADMIN` 롤명을 리네임. IAM 파생·위임 카탈로그·이커머스 게이트웨이·12개 서비스·시드·스펙·콘솔을 1 원자 cross-project PR로.

# Status

ready

# Task Tags

- refactor
- cross-project
- iam
- ecommerce
- rbac

---

# Dependency Markers

- **amends**: ADR-MONO-035(operator-auth-unification-model) — `ecommerce → ADMIN` 파생 테이블 항목을 `ecommerce → ECOMMERCE_OPERATOR`로 개정(모델 불변, 롤명 표준화만). ADR 본문 테이블 + dated amendment note 갱신.
- **builds on**: BE-376(assume-tenant 도메인 롤 파생), BE-380(이커머스 게이트웨이 operator 라우팅), BE-393(멀티값 X-User-Role 게이트), BE-479(파트너십 위임 cap — DelegatableRoleCatalog 동기 불변식).
- **note (동기)**: 이커머스는 외부 standalone 프로토타입에서 이관돼 자체 RBAC를 `ADMIN` 롤명으로 구현한 상태로 합류. 다른 도메인은 monorepo 표준 `<DOMAIN>_OPERATOR`를 따르는데 이커머스만 `ADMIN`이라 튐. 의미는 동급(이커머스 오퍼레이터 롤), 이름만 레거시.

# Goal

이커머스 도메인 운영자 롤 문자열 `ADMIN`을 `ECOMMERCE_OPERATOR`로 **전면 리네임**(hard rename, back-compat/dual-accept 없음 — 사용자 확정). 리네임은 all-or-nothing: 부분 롤아웃 시 게이트웨이·서비스 간 롤명 불일치로 조용한 403. **1 원자 cross-project PR**로 아래 전부를 동시에 바꾼다.

**주의 — 무관한 것(건드리지 말 것)**: 콘솔 RBAC `SUPER_ADMIN`/`TENANT_ADMIN`/`TENANT_BILLING_ADMIN`(별개 축), wms `WMS_ADMIN`/`OUTBOUND_ADMIN`(의도적 미부여 admin-tier), `ADMIN_ADJUSTMENT`(재고 사유 enum), 이커머스 `RoleSeedPolicy`의 `CUSTOMER`(소비자 seed), `AdminAccountSeeder`의 `ADMIN_EMAIL/PASSWORD`(dev 로그인 상수).

# Scope

## In Scope (1 atomic PR)

**A. IAM 파생 (auth-service)**
- `projects/iam-platform/apps/auth-service/.../infrastructure/oauth2/OperatorRoleDerivation.java` L81 `case "ecommerce" -> List.of("ADMIN")` → `List.of("ECOMMERCE_OPERATOR")`.
- `.../OperatorRoleDerivationTest.java` (ecommerce→ECOMMERCE_OPERATOR 기대).
- `.../AssumeTenantExchangeIntegrationTest.java`, `.../TenantClaimTokenCustomizerTest.java` — ecommerce roles 기대값.

**B. 위임 카탈로그 (admin-service) — A와 원자 동기**
- `.../admin/domain/rbac/DelegatableRoleCatalog.java` L41 javadoc·L55 `"ADMIN"` → `"ECOMMERCE_OPERATOR"`.
- `.../DelegatableRoleCatalogTest.java`.

**C. 이커머스 서비스 내부 RBAC (12 서비스, functional 상수 + hasAdminRole)**
- product-service `AdminSellerController` `ROLE_ADMIN`, shipping-service(Query/Command/RefreshTracking), promotion-service(Query/Command/Coupon), notification-service `TemplateController` `ADMIN_ROLE`, settlement-service(Period/SettlementController), search-service `SearchAdminController`, user-service `AdminUserController` `ROLE_ADMIN`.
- 각 서비스의 `ROLE_ADMIN="ADMIN"`/`ADMIN_ROLE="ADMIN"` 상수 + `X-User-Role == ADMIN` 비교 → `ECOMMERCE_OPERATOR`.
- 관련 테스트 전부(~38 파일, X-User-Role/roles 리터럴 `"ADMIN"`).
- doc-only 컨트롤러(AdminProductController/AdminProductImageController/AdminOrderController) javadoc `{@code ADMIN}` → `ECOMMERCE_OPERATOR`.

**D. 이커머스 게이트웨이 (gateway-service)**
- `AccountTypeEnforcementFilter.java` L77·L85 `hasRole(roles, "ADMIN")` → `"ECOMMERCE_OPERATOR"`, javadoc(L24·37·80-83) 갱신. `JwtHeaderEnrichmentFilter`는 롤명-불문(변경 없음).
- `AccountTypeEnforcementFilterTest.java` + gateway 테스트의 `"ADMIN"` fixture.

**E. 시드/데모 SQL (fed-e2e fixtures)**
- `tests/federation-hardening-e2e/fixtures/seed-ecommerce-operator.sql` L89 `INSERT ... account_roles ... 'ADMIN'` → `'ECOMMERCE_OPERATOR'` + 주석.
- `seed-omni-ecommerce.sql`, `seed-omni-tenant.sql` 주석.

**F. 스펙/계약/ADR (producer-authoritative)**
- 이커머스 specs: `product-api.md`, `shipping-api.md`, `settlement-api.md`, `search-api.md`, `notification-api.md`, `integration/iam-integration.md`, `features/multi-tenancy-and-marketplace.md`, `services/web-store/architecture.md`.
- `projects/platform-console/specs/contracts/console-integration-contract.md`(이커머스 게이트웨이 roles∋ADMIN 구간만).
- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md`(파생 테이블 + amendment note).
- `projects/iam-platform/docs/guides/operator-auth-token-model.md`(도메인 롤 나열).

**G. 콘솔 (platform-console console-web)**
- `features/iam-guide/data.ts`(DOMAIN_ROLE_MAP `ecommerce` 항목 + 내레이션), `features/ecommerce-guide/data.ts`(`ECOMMERCE_ROLE_NOTE` + 파생 주석).
- `tests/unit/EcommerceGuideScreen.test.tsx` (single-role note 텍스트).

## Out of Scope

- dual-accept/전환 창(사용자가 hard rename 확정).
- 콘솔 RBAC·wms admin-tier·CUSTOMER seed 등 무관 롤(위 주의).
- 히스토리 `tasks/done/*.md` 기록(과거 스냅샷, 리라이트 금지).
- 이커머스 서비스의 공유 RBAC 라이브러리화(각 서비스 상수 하드코딩은 별건 리팩터 여지).
- promotion-api.md의 기존 ADMIN 문서 누락(pre-existing gap, 별건).

# Acceptance Criteria

- [ ] **AC-1 (A+B 원자)** auth-service `OperatorRoleDerivation` ecommerce→`ECOMMERCE_OPERATOR` + admin-service `DelegatableRoleCatalog` 동일 리네임(둘의 javadoc "keep in sync" 유지). 어느 한쪽만 바뀐 상태 없음.
- [ ] **AC-2 (게이트웨이)** 이커머스 `AccountTypeEnforcementFilter`의 `/api/admin/**` admission + operator-on-public 트리가 `ECOMMERCE_OPERATOR`로 게이트. CUSTOMER 분기 불변.
- [ ] **AC-3 (서비스)** 12 서비스 내부 `ROLE_ADMIN`/`ADMIN_ROLE` 상수 + `X-User-Role` 비교가 `ECOMMERCE_OPERATOR`. 남은 이커머스-operator 의미의 리터럴 `"ADMIN"` 0.
- [ ] **AC-4 (시드)** `account_roles.role_name`·주석이 `ECOMMERCE_OPERATOR`. fed-e2e 데모 운영자가 이커머스 화면 진입(회귀 없음).
- [ ] **AC-5 (스펙/ADR)** 이커머스 producer 스펙·console-integration-contract·ADR-035·iam 가이드의 이커머스 롤 표기가 `ECOMMERCE_OPERATOR`. ADR-035 amendment note 추가.
- [ ] **AC-6 (콘솔)** DOMAIN_ROLE_MAP ecommerce=`ECOMMERCE_OPERATOR`, ecommerce-guide note 갱신, 콘솔 테스트 green.
- [ ] **AC-7 (무관 불변)** SUPER_ADMIN/TENANT_ADMIN/WMS_ADMIN/ADMIN_ADJUSTMENT/CUSTOMER seed/AdminAccountSeeder 상수 무변경.
- [ ] **AC-8 (green)** ecommerce·iam·gateway 컴파일 + 콘솔 lint/tsc/vitest green. CI(Testcontainers Linux)가 IT 권위.

# Related Specs

- `docs/adr/ADR-MONO-035-operator-auth-unification-model.md` (파생 테이블 SoT — 개정 대상).
- `projects/ecommerce-microservices-platform/specs/contracts/http/integration/iam-integration.md` (X-User-Role 멀티값 게이트 계약).
- `projects/iam-platform/apps/admin-service/.../rbac/DelegatableRoleCatalog.java` javadoc (A↔B sync 불변식).

# Related Contracts

- 이커머스 `*-api.md`(product/shipping/settlement/search/notification) X-User-Role 헤더 계약.
- `projects/platform-console/specs/contracts/console-integration-contract.md` (이커머스 게이트웨이 roles).

# Edge Cases

- **A↔B 드리프트**: 한쪽만 리네임 시 파트너십 위임이 `422 PARTNERSHIP_SCOPE_INVALID` fail-closed. 반드시 동시.
- **부분 서비스 롤아웃**: 게이트웨이가 새 롤 통과시켜도 서비스가 옛 `ADMIN` 기대 시 조용한 403 — 12 서비스 전부 동시.
- **operator-on-public 트리**(promotion/shipping/notification): 게이트웨이 admit + 서비스 self-gate 둘 다 리네임.
- **false-positive 회피**: WMS_ADMIN/OUTBOUND_ADMIN/ADMIN_ADJUSTMENT/SUPER_ADMIN/CUSTOMER/ADMIN_EMAIL 미접촉(AC-7).

# Failure Scenarios

- 리네임 누락 파일 → CI RED(단위/IT의 리터럴 `"ADMIN"` 기대 불일치) 또는 런타임 403. 전 참조 grep 0 확인으로 가드.
- 시드 미마이그레이션 → fed-e2e 데모 이커머스 운영자 403(AC-4).
- ADR/스펙 미갱신 → producer-authoritative 계약 드리프트(다른 에이전트/팀이 옛 ADMIN을 SoT로 소비).

# Definition of Done

- [ ] A~G 전 영역 `ADMIN`(이커머스 operator 의미) → `ECOMMERCE_OPERATOR`, 무관 롤 불변
- [ ] ecommerce·iam·gateway 컴파일 green + 콘솔 lint/tsc/vitest green
- [ ] ADR-035 개정 + amendment note, producer 스펙 정합
- [ ] 1 원자 cross-project PR
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
