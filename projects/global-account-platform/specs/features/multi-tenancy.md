# Feature: Multi-Tenancy

## Purpose

이 플랫폼은 단일 제품(팬플랫폼) 전용 계정 시스템에서 **여러 제품이 공유하는 계정·인증 공급자(account/auth provider)**로 진화한다. 1차 소비자는 B2C 팬플랫폼(`fan-platform`)이고, 2차 소비자는 B2B 내부 시스템 WMS(`wms`)이다. 향후 ERP·SCM·MES 등 추가 엔터프라이즈 제품도 동일한 게이트웨이를 통해 위임 인증한다.

본 문서는:

- 테넌트 모델(식별자·등록 방식)
- 데이터 격리 수준과 적용 범위
- JWT 변경(tenant_id claim)
- 테넌트별 역할 집합과 cross-tenant 보안 규칙
- WMS·향후 enterprise 소비자를 위한 internal provisioning API
- 기존 단일-테넌트 데이터의 마이그레이션 정책

을 정의한다.

---

## Related Services

| Service | Role |
|---|---|
| account-service | 테넌트 메타·계정 소유. `tenant_id` 컬럼 강제. 내부 provisioning 엔드포인트 제공 |
| auth-service | 로그인 시 `tenant_id` 조회 → JWT payload에 claim 포함 |
| gateway-service | JWT `tenant_id` 검증, 다운스트림으로 `X-Tenant-Id` 헤더 전파, tenant rate limit |
| admin-service | 운영자가 다룰 수 있는 테넌트 범위 제어, 테넌트별 audit query 분리 |
| security-service | 감사·보안 이벤트에 `tenant_id` 보존, tenant 단위 비정상 탐지 |

---

## Tenant Model

### TenantId

- **타입**: 문자열 (slug). 정규식 `^[a-z][a-z0-9-]{1,31}$` (소문자·숫자·하이픈, 1~32자)
- **예시**: `fan-platform`, `wms`, `erp`, `scm`, `mes`
- **불변**: 한 번 발급된 `tenant_id`는 변경 불가. 회수·재할당 금지(감사 트레일·외부 토큰과의 정합)
- **노출 범위**: JWT claim, internal API URL path, audit log, downstream HTTP header

### Tenant 엔터티 (account-service `domain/tenant/`)

| 필드 | 설명 |
|---|---|
| `tenant_id` | 위 슬러그. PK |
| `display_name` | UI 표시용. 예: "Fan Platform", "Warehouse Management System" |
| `tenant_type` | enum: `B2C_CONSUMER`, `B2B_ENTERPRISE`. 격리 정책·기본 역할셋 결정 |
| `status` | enum: `ACTIVE`, `SUSPENDED`. SUSPENDED 테넌트는 신규 로그인·가입 차단 |
| `created_at`, `updated_at` | 감사 |

### 테넌트 등록 방식

테넌트는 **운영자(SUPER_ADMIN)에 의해서만 등록**된다. self-service 가입 없음.

> 신규 소비 도메인이 합류할 때의 단일 진입 가이드는 [consumer-integration-guide.md](consumer-integration-guide.md) 참조 — Phase 1 (테넌트 등록) ~ Phase 6 (운영 체크리스트) 단선화.

- 등록 경로: admin-service의 운영자 명령 — 4개 엔드포인트 (`POST /api/admin/tenants`, `GET /api/admin/tenants`, `GET /api/admin/tenants/{id}`, `PATCH /api/admin/tenants/{id}`). 상세 contract 는 [admin-api.md § Tenant Lifecycle](../contracts/http/admin-api.md#tenant-lifecycle-task-be-256) 참조 (TASK-BE-256).
- 등록 시 `admin_actions`에 `action_code=TENANT_CREATE` 기록 + outbox 이벤트 [tenant-events.md](../contracts/events/tenant-events.md) `tenant.created` 발행 (audit-heavy).
- 테넌트 SUSPEND/REACTIVATE 도 동일하게 admin-service 경유 — `tenant.suspended` / `tenant.reactivated` outbox 이벤트 발행. account-service 가 이 이벤트를 소비해 SUSPENDED 테넌트의 신규 로그인·가입을 차단한다.
- 예약어 (`admin`, `internal`, `system`, `null`, `default`, `public`, `gap`, `auth`, `oauth`, `me`) 는 `tenantId` 로 등록 불가 (`400 TENANT_ID_RESERVED`).
- 테넌트 삭제 미지원 — 감사 트레일·외부 토큰 정합으로 인해 SUSPEND 만 가능.

---

## Isolation Strategy

### 격리 수준

**모든 서비스에서 row-level isolation으로 시작한다.** 동일 schema에 `tenant_id` 컬럼을 추가하고, 모든 도메인 테이블의 unique index/foreign key/쿼리에 `tenant_id`를 명시적으로 포함한다.

| 테넌트 유형 | 격리 수준 | 비고 |
|---|---|---|
| B2C consumer (`fan-platform`, 향후 `ecommerce` 등) | row-level (shared schema) | 다수 테넌트가 등장해도 비용 효율을 위해 shared schema 유지 |
| B2B enterprise (`wms`, `erp`, `scm`, `mes`) | row-level (shared schema, 초기) | 규모·계약 요건에 따라 schema-level로 승격 가능. 트리거는 별도 ADR로 결정 |

schema-level 또는 DB-level 격리로 전환되는 시점은:

1. 단일 enterprise 테넌트의 데이터 볼륨이 다른 테넌트 합계의 50% 이상으로 비대해진 경우
2. 계약상 물리적 격리가 명시된 경우
3. 컴플라이언스(규제) 요구가 row-level로 충족 불가능한 경우

위 조건이 발생하면 ADR을 작성하고 `specs/services/<service>/architecture.md`를 갱신한다.

### 적용 범위 (서비스별)

- **account-service**: `accounts`, `profiles`, `account_status_history`, `outbox_events`에 `tenant_id` NOT NULL. unique index `(tenant_id, email)`로 변경(테넌트 간 동일 이메일 허용)
- **auth-service**: `credentials`, `refresh_tokens`, `social_identities`에 `tenant_id` NOT NULL. unique index `(tenant_id, provider, provider_user_id)` (소셜 식별자도 테넌트별 분리)
- **admin-service**: `admin_operators`, `admin_operator_roles`, `admin_actions`에 `tenant_id`(또는 `target_tenant_id`) NOT NULL. SUPER_ADMIN은 cross-tenant 운영을 위해 `tenant_id = '*'` 같은 와일드카드 또는 별도 platform-scope role을 가짐(상세는 admin-service 스펙 후속)
- **security-service**: 보안 이벤트(`auth.login.attempted`, `auth.login.failed`, `account.status.changed` 등) 모두 `tenant_id` 페이로드 필수. 테넌트별 비정상 탐지 임계치 운용 가능
- **gateway-service**: 라우팅·rate limit 키에 `tenant_id` 포함. `rate:login:{tenant_id}:{ip}` 형태

---

## JWT Changes

### Access Token Payload

기존 payload에 다음 claim을 추가한다.

| Claim | 타입 | 설명 |
|---|---|---|
| `tenant_id` | string | 토큰을 발급한 테넌트의 slug. 필수 |
| `tenant_type` | string | `B2C_CONSUMER` \| `B2B_ENTERPRISE`. 라우팅·권한 검사 힌트 |
| `roles` | string[] | 기존 필드. 테넌트별 역할 집합. 의미는 `tenant_id` 컨텍스트에서만 유효 |

### Refresh Token

- DB `refresh_tokens.tenant_id` NOT NULL. rotation 시 `tenant_id` 일치 검증 필수
- cross-tenant refresh(다른 테넌트의 refresh로 다른 테넌트 access 발급)는 **절대 금지** → `TOKEN_TENANT_MISMATCH` 401

### Gateway 검증

- gateway는 JWT 서명·만료뿐 아니라 `tenant_id` claim 존재를 검사한다. 누락 시 401
- 라우트 패턴에 따라 다운스트림 서비스로 `X-Tenant-Id` 헤더를 전파한다. 다운스트림은 JWT claim과 헤더 일치를 재검증한다 (defense-in-depth)

---

## Per-Tenant Roles

### 원칙

- **역할 이름은 테넌트 컨텍스트 안에서만 의미를 가진다.** 동일한 `WAREHOUSE_ADMIN` role을 다른 테넌트에 부여해도 권한 의미는 다를 수 있다
- 역할 정의(role definitions)는 `(tenant_id, role_name)` 복합키로 관리. 물리 스토리지는 `account_roles` 테이블 ([specs/services/account-service/data-model.md § account_roles](../services/account-service/data-model.md#account_roles))
- 권한 매트릭스(role → permissions)는 테넌트마다 독립
- **기본 정책 (TASK-BE-255)**: admin 이 사전 등록한 역할만 부여 가능. 등록되지 않은 역할 이름은 provisioning API 가 400 으로 거부 — `account_roles.role_name` 자체는 자유 문자열 (정규식 `^[A-Z][A-Z0-9_]*$` 강제) 이며, 테넌트별 허용 역할 카탈로그 (`tenant_role_definitions`) 도입은 별도 후속 태스크

### 기본 역할 (예시)

#### `fan-platform` (B2C_CONSUMER)
- `MEMBER` (기본 가입자)
- `VERIFIED_MEMBER` (이메일 검증 완료)
- 운영자 역할은 admin-service의 platform-level role(`SUPER_ADMIN`, `ACCOUNT_ADMIN`, `AUDITOR`)이 담당

#### `wms` (B2B_ENTERPRISE)
- `WAREHOUSE_ADMIN` — 창고 운영 전반 관리. 사용자 생성·역할 부여 가능(WMS 내부)
- `INBOUND_OPERATOR` — 입고 관련 작업
- `OUTBOUND_OPERATOR` — 출고 관련 작업
- `INVENTORY_VIEWER` — 재고 조회 전용 (read-only)

> WMS의 위 4개 역할은 본 플랫폼의 `roles` claim에 그대로 노출된다. WMS 애플리케이션은 자체 권한 매트릭스를 보유하고 본 플랫폼의 claim을 신뢰한다. 본 플랫폼은 역할 이름 등록·계정 ↔ 역할 매핑·revocation만 책임진다.

### Cross-Tenant Operator

- `SUPER_ADMIN`은 platform-scope role로 테넌트 경계를 넘어 운영 가능. JWT claim에 `tenant_id="*"` 또는 별도 `platform_scope=true` 플래그를 사용한다(구체 형식은 admin-service 스펙 갱신 시 확정)
- 모든 cross-tenant 작업은 `admin_actions`에 `tenant_id=*` + `target_tenant_id=<대상>`으로 기록 (audit-heavy 정합)

---

## Internal Provisioning API

### 목적

WMS·ERP 등 enterprise tenant는 **자체 가입 페이지를 두지 않고** 본 플랫폼의 internal API를 통해 사용자를 생성·관리한다. 일반 사용자는 셀프 가입(SSO/이메일 가입)을 사용하지 않는다.

### 엔드포인트 (account-service `presentation/internal/`)

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/internal/tenants/{tenantId}/accounts` | 신규 사용자 생성 (이메일·초기 비밀번호·역할 배열) |
| `GET` | `/internal/tenants/{tenantId}/accounts` | 테넌트 사용자 목록 조회 (페이지네이션, 필터: status, role) |
| `GET` | `/internal/tenants/{tenantId}/accounts/{accountId}` | 단일 사용자 조회 |
| `PATCH` | `/internal/tenants/{tenantId}/accounts/{accountId}/roles` | 역할 전체 교체 |
| `PATCH` | `/internal/tenants/{tenantId}/accounts/{accountId}/status` | 상태 변경 (ACTIVE↔LOCKED, DELETED) |
| `POST` | `/internal/tenants/{tenantId}/accounts/{accountId}/password-reset` | 운영자에 의한 비밀번호 재설정 토큰 발급 |

### 인증·인가

- 인증: mTLS 또는 service-to-service 토큰. **gateway 비공개 라우트** (외부 인터넷 도달 금지)
- 인가: 호출 주체가 해당 `tenantId`에 대한 provisioning 권한을 보유해야 함. 주체 유형:
  - WMS의 시스템 계정(WMS 백엔드 → 본 플랫폼) — `tenant_id=wms`로만 호출 가능
  - 본 플랫폼의 `SUPER_ADMIN` 운영자 — 모든 테넌트로 호출 가능
- URL path의 `{tenantId}`와 인증 주체의 tenant scope가 불일치하면 **403 TENANT_SCOPE_DENIED**

### 동작 규칙

- 신규 계정 생성은 internal API에서 **tenant 등록 여부를 사전 검증** (`tenants.status = ACTIVE`)
- 이메일 unique index는 `(tenant_id, email)` 기준 → 같은 이메일이 `fan-platform`과 `wms`에 동시에 존재 가능
- 모든 mutation은 `admin_actions`에 기록(`OPERATOR_PROVISIONING_*` action_code) — internal 호출이라도 감사 의무 동일

---

## Cross-Tenant Security Rules

### 토큰 검증 (필수)

| 위치 | 검사 |
|---|---|
| gateway | JWT `tenant_id` claim 존재 확인. 누락 시 401 `TOKEN_INVALID` |
| gateway | 라우트가 internal provisioning이면 path `{tenantId}` ↔ JWT `tenant_id` 또는 platform-scope 검사 |
| 다운스트림 서비스 | `X-Tenant-Id` 헤더 ↔ JWT claim 재검증 (defense-in-depth) |
| 도메인 쿼리 | 모든 read/write 쿼리는 `WHERE tenant_id = ?` 명시 — application layer에서 강제 |

### 격리 회귀 방지

- **Repository 레벨**: 모든 JPA repository 메서드는 `tenant_id` 파라미터를 첫 번째 인자로 받는다. `findById(id)`처럼 `tenant_id` 없는 조회 메서드는 금지(컴파일 또는 정적 분석으로 차단)
- **Specification/QueryDSL**: 동적 쿼리 빌더에 tenant predicate가 자동 주입되도록 base specification 제공
- **테스트**: 모든 도메인 통합 테스트에 **cross-tenant leak 회귀 테스트** 포함 — 다른 `tenant_id`로 동일 PK·이메일 조회 시 결과가 격리되는지 검증

### Rate Limit

- key 패턴에 `tenant_id` 포함: `rate:login:{tenant_id}:{ip}`, `rate:signup:{tenant_id}:{ip}`
- 테넌트별 quota를 별도로 운용 가능 (예: WMS는 더 높은 internal quota)

### Event 발행

- 모든 outbox 이벤트(`account.created`, `auth.login.succeeded` 등)에 `tenant_id` 페이로드 필수
- security-service는 tenant 단위로 비정상 탐지 임계치를 분리 적용 가능

---

## Migration of Existing Data

### 현재 상태

현재 모든 데이터(`accounts`, `profiles`, `credentials`, `refresh_tokens`, `social_identities`, `admin_operators`, `account_status_history` 등)는 단일 테넌트 가정 하에 작성되어 있다.

### 마이그레이션 단계

1. **사전 단계**: `tenants` 테이블 생성 후 `('fan-platform', 'Fan Platform', 'B2C_CONSUMER', 'ACTIVE')` row 1건 삽입
2. **스키마 변경 (Flyway)**:
   - 도메인 테이블에 `tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform'` 추가
   - DEFAULT 값으로 모든 기존 row 백필
   - 백필 완료 후 DEFAULT 제거(NOT NULL은 유지)
   - 기존 unique index 제거 후 `(tenant_id, email)` 등 복합 unique index 재생성
3. **애플리케이션 변경**: tenant-aware repository·JWT claim·gateway 헤더 전파를 단계적으로 도입
4. **검증**: 기존 사용자가 그대로 로그인되며 모든 토큰이 `tenant_id=fan-platform` claim과 함께 발급되는지 통합 테스트
5. **WMS 테넌트 등록**: 운영자 명령으로 `tenants` 테이블에 `wms` 등록. WMS 백엔드의 system credential 발급
6. **JWT key rotation 불필요**: claim 추가만이며 서명 키는 재사용

### 호환성

- 마이그레이션 진행 중 발급된 기존 access/refresh token은 `tenant_id` claim이 없을 수 있음 → gateway는 grace period(예: 30일) 동안 누락 시 `fan-platform`으로 간주하는 fallback 정책을 운영 가능. 이후 grace 만료 시 누락 토큰은 401 처리

> grace policy의 실제 적용 여부·기간은 운영 배포 시점에 별도 결정. 본 스펙은 옵션을 명시할 뿐 강제하지 않음.

---

## Edge Cases

- 동일 이메일이 `fan-platform`·`wms`에 동시에 존재 — 허용. 로그인은 항상 tenant 컨텍스트 위에서 수행
- WMS 사용자가 fan-platform 토큰으로 WMS API 호출 시도 — gateway가 path tenant 또는 다운스트림이 `tenant_id` 불일치로 403
- SUPER_ADMIN이 SUSPENDED 테넌트 사용자 강제 로그아웃 — 허용 (운영 회수 시나리오)
- WMS 테넌트 SUSPENDED 시 — 신규 로그인·refresh 모두 거부. 기존 access token은 만료까지 유효(짧은 TTL이 안전망)
- `tenant_id`가 path와 JWT 모두에 있는데 둘이 다름 — 항상 거부, 로그에 기록 (잠재적 공격 시그널)
- 마이그레이션 중 `tenant_id` 컬럼 추가 직후 NULL row 잔존 — 백필 완료 전 NOT NULL 제약 활성화 금지

---

## Related Contracts

본 feature 도입에 따라 다음 컨트랙트의 업데이트가 필요하다 (실제 갱신은 본 스펙 승인 후 별도 PR):

- HTTP: `auth-api.md` (login/refresh 응답에 tenant_id 명시), `account-api.md` (signup의 tenant 컨텍스트), `admin-api.md` (tenant CRUD)
- Internal: `auth-to-account.md` (credential lookup에 tenant 파라미터), `account-internal-provisioning.md` (신규)
- Events: 모든 이벤트 페이로드에 `tenant_id` 필드 추가, 스키마 버전 +1

---

## Out of Scope (이 스펙에서 다루지 않음)

- 테넌트별 빌링/usage metering — 본 플랫폼은 계정 인프라이며 과금은 다루지 않음
- 테넌트별 데이터 export/이전 — 향후 enterprise 계약 요건이 발생할 때 별도 스펙
- schema-level 또는 DB-level 격리로의 전환 절차 상세 — 트리거 조건 발생 시 ADR로 결정
- WMS 자체의 권한 매트릭스 — WMS 프로젝트가 소유. 본 플랫폼은 role 이름과 매핑만 관리
- B2B SSO(SAML/OIDC IdP federation) — 향후 enterprise 요구사항. 별도 feature spec
