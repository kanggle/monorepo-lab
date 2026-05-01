# auth-service — Data Model

## Design Decision

credentials(비밀)와 profile(비밀 아님)은 **물리적으로 별도 서비스·DB**에 저장한다 ([rules/domains/saas.md](../../../rules/domains/saas.md) S1). auth-service는 credentials 전담, profile은 account-service가 소유.

---

## Tables

### `credentials`

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT | internal | — |
| `account_id` | VARCHAR(36) | UNIQUE, NOT NULL | internal | account-service의 account.id 참조 (FK 없음 — 서비스 간 FK 금지) |
| `credential_hash` | VARCHAR(255) | NOT NULL | **restricted** | argon2id 해시. 평문 저장 금지 (R2) |
| `hash_algorithm` | VARCHAR(30) | NOT NULL, DEFAULT 'argon2id' | internal | 향후 알고리즘 업그레이드 시 구분 |
| `created_at` | DATETIME(6) | NOT NULL | internal | — |
| `updated_at` | DATETIME(6) | NOT NULL | internal | 패스워드 변경 시 갱신 |
| `version` | INT | NOT NULL, DEFAULT 0 | internal | 낙관적 락 (T5) |

**인덱스**: `idx_credentials_account_id` (UNIQUE)

### `refresh_tokens`

| 컬럼 | 타입 | 제약 | 분류 등급 | 설명 |
|---|---|---|---|---|
| `id` | BIGINT | PK | internal | — |
| `jti` | VARCHAR(36) | UNIQUE, NOT NULL | confidential | JWT ID (UUID v7 권장) |
| `account_id` | VARCHAR(36) | NOT NULL, INDEX | internal | — |
| `issued_at` | DATETIME(6) | NOT NULL | internal | — |
| `expires_at` | DATETIME(6) | NOT NULL | internal | — |
| `rotated_from` | VARCHAR(36) | NULL, INDEX | confidential | 이전 토큰의 jti. NULL이면 최초 발급 |
| `revoked` | BOOLEAN | NOT NULL, DEFAULT FALSE | internal | 명시적 revoke 여부 |
| `device_id` | VARCHAR(36) | NULL, INDEX | internal | `device_sessions.device_id` 참조 (FK 없음). NULL 허용 (D5 백필 전 기존 row). [device-session.md](./device-session.md) D5 canonical |
| `device_fingerprint` | VARCHAR(128) | NULL | confidential | (deprecated, superseded by `device_id`) 디바이스 식별 (해시) |

**인덱스**: `idx_rt_jti` (UNIQUE), `idx_rt_account_id`, `idx_rt_rotated_from`, `idx_rt_device_id`

**토큰 재사용 탐지 로직**: `POST /api/auth/refresh`가 `jti=A`로 rotation을 요청했을 때, A에 이미 `rotated_from`을 참조하는 자식이 존재하면 → 재사용 탐지. 해당 `account_id`의 모든 refresh_token을 `revoked=TRUE`로 일괄 처리 + `auth.token.reuse.detected` 이벤트 발행.

### `outbox`

[libs/java-messaging](../../../libs/java-messaging) 표준 스키마 사용. 테이블 이름은 라이브러리의 `OutboxJpaEntity`가 `@Table(name = "outbox")`으로 선언하므로 `outbox`를 사용한다.

| 컬럼 | 타입 | 설명 |
|---|---|---|
| `id` | BIGINT | PK, AUTO_INCREMENT |
| `aggregate_type` | VARCHAR(100) | `auth` |
| `aggregate_id` | VARCHAR(255) | `account_id` |
| `event_type` | VARCHAR(100) | `auth.login.succeeded` 등 |
| `payload` | TEXT (JSON) | 이벤트 envelope |
| `created_at` | TIMESTAMP | — |
| `published_at` | TIMESTAMP | NULL이면 미발행 |
| `status` | VARCHAR(20) | `PENDING` / `PUBLISHED` |

---

## Migration Strategy

- **Flyway**: `V{nnnn}__{description}.sql` 네이밍
- 첫 마이그레이션: `V0001__create_credentials_and_refresh_tokens.sql`
- Outbox 테이블: `V0002__create_outbox_events.sql` (libs/java-messaging DDL 재사용, 테이블 이름은 `outbox`)
- PII 마스킹 컬럼 (`credential_hash`, `device_fingerprint`) 변경 시 down migration 금지 — 단방향만 허용

---

## Data Classification Summary

| 등급 | 컬럼 |
|---|---|
| **restricted** | `credentials.credential_hash` |
| **confidential** | `refresh_tokens.jti`, `refresh_tokens.rotated_from`, `refresh_tokens.device_fingerprint`, `device_sessions.device_fingerprint`, `device_sessions.ip_last` |
| **internal** | `refresh_tokens.device_id`, 그리고 위에 명시되지 않은 `credentials`, `refresh_tokens`, `device_sessions`, `outbox`의 모든 컬럼 (예: `device_sessions.device_id`, `account_id`, `user_agent`, `geo_last`, `issued_at`, `last_seen_at`, `revoked_at`, `revoke_reason`) |
| **public** | 없음 |

[rules/traits/regulated.md](../../../rules/traits/regulated.md) R1 준수.

> `device_sessions` DDL/인덱스/제약은 본 문서가 아닌 [specs/services/auth-service/device-session.md](./device-session.md) "Data Model" 절에서 선언된다. 본 Classification Summary는 해당 테이블의 컬럼 분류를 canonical 위치에서 재확인하는 역할만 수행한다.

---

## OAuth 2.0 / OIDC Tables (TASK-BE-252)

Spring Authorization Server 영속 레이어. Flyway V0008에서 생성.

### `oauth_clients`

OAuth 2.0 등록 클라이언트. `client_id` 는 글로벌 unique — lookup은 항상 `client_id` 기준.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | VARCHAR(100) | PK | UUID (entity 기본 키) |
| `client_id` | VARCHAR(100) | UNIQUE NOT NULL | OAuth 2.0 `client_id` |
| `tenant_id` | VARCHAR(32) | NOT NULL | 소유 테넌트 식별자 |
| `tenant_type` | VARCHAR(32) | NOT NULL | `B2C` / `B2B` 등 |
| `client_secret_hash` | VARCHAR(200) | NULL | BCrypt 해시; public PKCE 클라이언트는 NULL |
| `client_name` | VARCHAR(200) | NOT NULL | 표시 이름 |
| `client_authentication_methods` | JSON | NOT NULL | e.g. `["client_secret_basic"]` |
| `authorization_grant_types` | JSON | NOT NULL | e.g. `["authorization_code","refresh_token"]` |
| `redirect_uris` | JSON | NOT NULL | e.g. `["http://localhost:3000/callback"]` |
| `scopes` | JSON | NOT NULL | 허용 scope 목록 |
| `client_settings` | JSON | NOT NULL | SAS `ClientSettings` 직렬화; `custom.tenant_id`, `custom.tenant_type` 커스텀 키 포함 |
| `token_settings` | JSON | NOT NULL | SAS `TokenSettings` 직렬화 |
| `created_at` | TIMESTAMP | NOT NULL | — |
| `updated_at` | TIMESTAMP | NOT NULL | ON UPDATE CURRENT_TIMESTAMP |

**인덱스**: `uk_oauth_clients_client_id` (UNIQUE), `idx_oauth_clients_tenant_client (tenant_id, client_id)`

**Tenant 캐리어 전략 (Option B)**: `client_settings` 내 `custom.tenant_id` / `custom.tenant_type` 키에 테넌트 정보를 저장한다. `TenantClaimTokenCustomizer`와 `SasRefreshTokenAuthenticationProvider`가 이 값을 읽어 JWT claim을 주입한다. `clientName = "tenantId|tenantType"` 패턴(TASK-BE-251 in-memory 방식)은 더 이상 사용하지 않는다.

**Secret 정책**: `client_secret_hash`는 BCrypt(cost=10) 해시만 저장. 평문 저장 금지. `JpaRegisteredClientRepository.save()`가 `{noop}` prefix 탐지 시 즉시 BCrypt 인코딩.

### `oauth_scopes`

Scope 카탈로그. 시스템 scope(`tenant_id = NULL`)는 모든 테넌트가 공유. 테넌트 정의 scope는 `tenant_id`에 소유 테넌트를 기록.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | BIGINT | PK AUTO_INCREMENT | — |
| `scope_name` | VARCHAR(100) | NOT NULL | e.g. `openid`, `account.read` |
| `tenant_id` | VARCHAR(32) | NULL | NULL = 시스템 scope |
| `description` | VARCHAR(500) | NULL | — |
| `is_system` | BOOLEAN | NOT NULL | TRUE이면 시스템 정의 |
| `created_at` | TIMESTAMP | NOT NULL | — |
| `tenant_scope_key` | VARCHAR(132) | GENERATED STORED | `CONCAT(COALESCE(tenant_id,'__system__'),':',scope_name)` |

**Unique constraint**: `uk_oauth_scopes_tenant_scope (tenant_scope_key)`

**시드 데이터** (is_system=true, tenant_id=NULL): `openid`, `profile`, `email`, `offline_access`

### `oauth_consent`

사용자가 클라이언트에 부여한 scope 동의 기록. `revoked_at = NULL`이면 활성 동의.

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `principal_id` | VARCHAR(100) | PK | 사용자 식별자 |
| `client_id` | VARCHAR(100) | PK | OAuth `client_id` |
| `tenant_id` | VARCHAR(32) | NOT NULL | — |
| `granted_scopes` | JSON | NOT NULL | 동의한 scope 목록 |
| `granted_at` | TIMESTAMP | NOT NULL | — |
| `revoked_at` | TIMESTAMP | NULL | 철회 시각; NULL = 활성 |

**인덱스**: PK `(principal_id, client_id)`, `idx_oauth_consent_tenant_principal (tenant_id, principal_id)`

**Soft-delete**: 동의 철회 시 행을 삭제하지 않고 `revoked_at`을 기록 → 감사 이력 보존.

### `oauth2_authorization`

Spring Authorization Server 공식 스키마. `JdbcOAuth2AuthorizationService`가 직접 읽고 쓰므로 컬럼 이름을 임의로 변경하면 안 된다. 전체 DDL은 [Flyway V0008](../../../../apps/auth-service/src/main/resources/db/migration/V0008__create_oauth_tables.sql) 참조.

주요 컬럼:

| 컬럼 | 설명 |
|---|---|
| `id` | PK (SAS authorization id) |
| `registered_client_id` | `oauth_clients.id` 참조 (FK 없음 — 서비스 간 FK 금지) |
| `principal_name` | 인가된 주체 (account_id 또는 client_id) |
| `authorization_grant_type` | `client_credentials`, `authorization_code`, `refresh_token` |
| `access_token_value` | BLOB (SAS 직렬화) |
| `refresh_token_value` | BLOB (SAS 직렬화) |
| `oidc_id_token_value` | BLOB (OIDC ID token) |

**참조**: Spring Authorization Server 공식 스키마 — `oauth2-authorization-schema.sql` (SAS 1.x 소스 기준).
