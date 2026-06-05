# Task ID

TASK-BE-252

# Title

OAuth client/scope/consent 스키마 + 마이그레이션 + JPA `RegisteredClientRepository`

# Status

ready

# Owner

backend

# Task Tags

- code
- adr

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

ADR-001 ACCEPTED (D1=A) 에 따른 OIDC Authorization Server의 영속 데이터 모델을 정의하고 마이그레이션한다. Spring Authorization Server의 `RegisteredClientRepository`/`OAuth2AuthorizationConsentService`/`OAuth2AuthorizationService` 를 in-memory에서 JPA 영속 모델로 대체한다.

완료 시점:

1. `oauth_clients`, `oauth_scopes`, `oauth_consent`, `oauth2_authorization` 테이블 정의 + Flyway 마이그레이션.
2. JPA 기반 `RegisteredClientRepository` 구현 — in-memory placeholder (TASK-BE-251) 대체.
3. 시스템 정의 scope (`openid`, `profile`, `email`, `offline_access`) 시드 데이터 삽입.
4. multi-tenant: 모든 client는 `tenant_id` 컬럼을 가지며, client는 단일 tenant에 귀속됨.
5. client secret은 BCrypt 해시로만 저장.
6. client 등록·수정·삭제는 admin-service 경유 (`POST /api/admin/oauth-clients`, audit trail 필수) — 본 태스크 범위는 스키마 + repository 어댑터까지, admin API는 별도 태스크에서 다룸.

---

# Scope

## In Scope

- Flyway 마이그레이션 (auth-service):
  - `oauth_clients` (PK `client_id`, `tenant_id` NOT NULL, `client_secret_hash`, `client_name`, `redirect_uris` JSON, `allowed_grants` JSON, `allowed_scopes` JSON, `client_authentication_methods` JSON, `require_proof_key` BOOLEAN, `access_token_ttl_seconds`, `refresh_token_ttl_seconds`, `created_at`, `updated_at`)
  - `oauth_scopes` (PK `scope_name`, `tenant_id` nullable for system scopes, `description`, `is_system` BOOLEAN, `created_at`)
  - `oauth_consent` (PK `(principal_id, client_id)`, `tenant_id`, `granted_scopes` JSON, `granted_at`, `revoked_at` nullable)
  - `oauth2_authorization` — SAS 표준 schema 그대로 차용 (Spring 공식 마이그레이션 SQL 사용)
- 인덱스:
  - `oauth_clients (tenant_id, client_id)`
  - `oauth_consent (tenant_id, principal_id)`
- JPA 엔티티 + Repository:
  - `OAuthClient`, `OAuthScope`, `OAuthConsent`
  - `JpaRegisteredClientRepository implements RegisteredClientRepository`
  - `JpaOAuth2AuthorizationConsentService implements OAuth2AuthorizationConsentService`
  - `JpaOAuth2AuthorizationService implements OAuth2AuthorizationService`
- 시스템 시드 데이터 (Flyway):
  - scopes: `openid`, `profile`, `email`, `offline_access` (모두 `is_system=true`, `tenant_id=NULL`)
- TASK-BE-251의 in-memory `RegisteredClientRepository` 빈을 본 태스크 머지 시 제거 (JPA 빈으로 대체).
- `specs/services/auth-service/data-model.md` 갱신: 신규 4개 테이블 명세.

## Out of Scope

- admin-service의 `POST /api/admin/oauth-clients` API — 별도 태스크 (admin 운영 명령은 후속).
- consent 화면 UI — fan-platform 연동 시점에 결정.
- `oauth_clients` 의 secret rotation 자동화 — 후속 ADR.
- bulk client 등록 — 현 시점 불필요.
- B2C 사용자별 consent 조회 API — 후속 (UI 도입 시).

---

# Acceptance Criteria

- [ ] Flyway 마이그레이션 V0NNN+ 4개 테이블 + 인덱스 + 시드 데이터 적용.
- [ ] `JpaRegisteredClientRepository.findByClientId(...)` 가 DB에서 client 조회 후 SAS `RegisteredClient` 빌더로 매핑.
- [ ] `client_secret_hash` 비교는 `PasswordEncoder` (`BCryptPasswordEncoder` 권장) 사용; 평문 저장 금지.
- [ ] TASK-BE-251의 in-memory `RegisteredClientRepository` 빈이 제거되고 JPA 빈으로 대체됨.
- [ ] 통합 테스트: in-memory 등록한 client → DB 마이그레이션 후 동일 `client_credentials` flow가 JPA repository로 동작.
- [ ] `oauth_clients.tenant_id` NOT NULL — 검증 회귀 테스트.
- [ ] `oauth_consent` 의 unique index `(principal_id, client_id)`.
- [ ] `oauth2_authorization` 테이블에서 발급된 token의 라이프사이클 (저장 → revoke → introspect) 동작.
- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:check` + `:integrationTest` PASS.

---

# Related Specs

> Step 0: read `PROJECT.md`, rules layers per classification.

- `docs/adr/ADR-001-oidc-adoption.md` § 3 Option A
- `specs/services/auth-service/data-model.md` (확장 대상)
- `specs/services/auth-service/architecture.md`
- `specs/features/multi-tenancy.md` § "Tenant Isolation"

# Related Skills

- `.claude/skills/backend/` 데이터 모델·마이그레이션 관련

---

# Related Contracts

- 본 태스크에서 외부 노출 contract 변경 없음 (admin API는 후속 태스크에서 추가).

---

# Target Service

- `auth-service`

---

# Architecture

- `infrastructure/persistence/oauth/`:
  - `OAuthClientEntity`, `OAuthScopeEntity`, `OAuthConsentEntity`
  - `JpaRegisteredClientRepository`
  - `JpaOAuth2AuthorizationConsentService`
  - `JpaOAuth2AuthorizationService`
- 도메인 레이어에 OAuth client 도메인 객체 분리: `domain/oauth/RegisteredOAuthClient` — SAS의 `RegisteredClient`는 인프라 표현으로 간주, 도메인은 별도 타입.
- 어댑터 변환: `OAuthClientMapper.toRegisteredClient(entity) -> RegisteredClient`.

---

# Implementation Notes

- **SAS 공식 schema 활용**: Spring Authorization Server는 공식 SQL schema (`oauth2-authorization-schema.sql`, `oauth2-authorization-consent-schema.sql`, `oauth2-registered-client-schema.sql`) 를 제공. 이를 베이스로 우리의 컬럼(`tenant_id`, `created_at`, `updated_at`)을 추가.
- **Multi-tenant client lookup**: SAS의 `RegisteredClientRepository.findByClientId(String)` 시그니처는 단일 인자. tenant 정보는 client_id 자체에 포함되지 않으므로, `tenant_id`는 client 등록 시점에 결정되고 lookup은 client_id로만 수행 (client_id는 글로벌 unique).
- **JSON 컬럼 vs 정규화**: `redirect_uris`, `allowed_grants`, `allowed_scopes` 는 JSON으로 저장 (MySQL 8 JSON column). 단순 client는 N+1 join 회피.
- **Tenant deletion**: `oauth_clients.tenant_id` 의 ON DELETE CASCADE 검토 필요 — 일단 RESTRICT로 시작하고 tenant 삭제 시 client 명시적 정리 요구.
- **In-memory placeholder 제거 순서**: 본 태스크가 머지된 후에야 251의 in-memory 빈 제거. 두 빈이 동시 존재하면 Spring 빈 충돌. PR 스코프에서 251 코드의 placeholder 빈도 함께 제거.

---

# Edge Cases

- **Client 미존재**: `findByClientId(unknown)` → `null` 반환 (SAS 계약), 호출자가 `OAuth2AuthenticationException` 던짐 — DB 예외로 변환하지 말 것.
- **Tenant SUSPENDED 상태의 client**: 본 태스크 범위에서는 별도 차단 없음. SUSPENDED tenant의 client로 발급된 token 거부는 별도 cross-cutting 정책 (후속 태스크).
- **시스템 scope (`openid`)에 `tenant_id=NULL`**: 모든 tenant가 공유. tenant 정의 scope는 `tenant_id` 채워짐. unique constraint: `(scope_name, COALESCE(tenant_id, '__system__'))`.
- **Refresh token 회전 시 `oauth2_authorization` 업데이트**: SAS 기본 동작 사용. 기존 `AuthRefreshTokenStore`와의 데이터 정합은 251에서 결정한 통합 전략에 의존.
- **Schema migration 실패 — 기존 데이터 보존**: 신규 테이블만 추가하므로 기존 데이터 영향 없음. 그러나 `oauth2_authorization` 의 발급된 토큰 데이터는 시스템 가동 후 빠르게 누적 — TTL 경과 토큰 정리 cron 필요 (후속 태스크).

---

# Failure Scenarios

- **JSON 컬럼 파싱 오류**: 운영자가 직접 SQL로 잘못된 JSON 삽입 시 client lookup 실패 — JSON validation은 application 단에서 수행, DB CHECK constraint는 MySQL 한정 미지원.
- **BCrypt 해시 cost 변경**: cost factor를 미래 변경하면 기존 client 재등록 필요 — 현재 default 10 사용.
- **Index 누락으로 인한 lookup 지연**: `oauth_clients (client_id)` PK 외에 `(tenant_id, client_id)` index 추가로 tenant-scoped 조회 성능 보장.

---

# Test Requirements

- 단위 테스트:
  - `OAuthClientMapper`: entity ↔ RegisteredClient 양방향 변환.
  - `JpaRegisteredClientRepository`: client 미존재 시 null, 존재 시 매핑 정확.
  - secret 검증: 잘못된 secret → 인증 실패.
- 통합 테스트 (`@Tag("integration")`):
  - Flyway 마이그레이션 적용 후 schema 검증.
  - 시스템 시드 scope 4개 존재.
  - JPA repository로 `client_credentials` E2E.
  - `oauth2_authorization` 테이블에 발급 토큰 row 저장 확인.
  - revocation 후 introspection `active=false`.

---

# Definition of Done

- [ ] Flyway migration applied
- [ ] JPA repository 구현 완료
- [ ] In-memory placeholder 제거
- [ ] Unit + integration tests added and passing
- [ ] `specs/services/auth-service/data-model.md` 갱신
- [ ] CI green
- [ ] Ready for review
