# Task ID

TASK-BE-229

# Title

auth-service — JWT `tenant_id` claim 추가 + tenant-aware 로그인/회전 흐름

# Status

ready

# Owner

backend

# Task Tags

- code
- api

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

`specs/features/multi-tenancy.md` 와 `specs/services/auth-service/architecture.md` 에 정의된 대로:

- access token payload와 refresh token DB row에 `tenant_id` 를 영속화한다.
- 로그인 시 account-service `lookupCredential` 응답으로부터 `tenant_id` 를 받아 JWT claim과 `refresh_tokens.tenant_id` 컬럼에 동시에 기록한다.
- refresh rotation 시 제출된 token의 `tenant_id` 와 새로 발급할 token의 `tenant_id` 일치 여부를 검증한다 (`TOKEN_TENANT_MISMATCH` 401).
- 같은 이메일이 두 테넌트에 등록 가능하므로, tenant 명시 없는 로그인이 다중 매칭되면 `LOGIN_TENANT_AMBIGUOUS` 400 으로 명시적 선택을 요구한다.
- Redis 키 패턴(`login:fail:{tenant_id}:{email}`, `refresh:blacklist:{tenant_id}:{jti}`)에 `tenant_id` 를 포함시킨다.

# Scope

## In Scope

- **Flyway 마이그레이션 (auth-service)**:
  - `credentials`, `refresh_tokens`, `social_identities` 테이블에 `tenant_id VARCHAR(32) NOT NULL DEFAULT 'fan-platform'` 컬럼 추가
  - 백필 후 DEFAULT 제거 (NOT NULL 유지)
  - `credentials.email` 단독 unique → `(tenant_id, email)` 복합 unique 로 변경
  - `social_identities` 의 unique key를 `(tenant_id, provider, provider_user_id)` 로 재구성
- **`AccountServiceClient.lookupCredential` 시그니처 / 응답 변경**:
  - 입력: `(email, tenantId?)` 두 파라미터 — `tenantId` 지정 시 단일 row 반환, 미지정 시 다중 매칭 가능 응답 형태
  - 응답 DTO에 `tenant_id`, `tenant_type`, `accountId` 포함
  - 다중 매칭 시 `LOGIN_TENANT_AMBIGUOUS` 400으로 변환 (presentation layer)
- **`JwtSigner`**:
  - access token claim에 `tenant_id`, `tenant_type` 추가 (필수 — 누락 시 fail-closed)
  - claim 누락 시 발급 실패 + 보안 로그 기록
- **`LoginUseCase`**:
  - 입력 DTO에 `tenant_id` (선택) 필드 수용
  - lookup 결과 단일 row → 비밀번호 검증 → JWT 발급 (tenant claim 포함) → `refresh_tokens` row에 `tenant_id` 영속
  - 다중 매칭 시 `LOGIN_TENANT_AMBIGUOUS` 400
  - `auth.login.attempted/succeeded/failed` 이벤트 페이로드에 `tenant_id` 포함
- **`RefreshTokenUseCase`**:
  - rotation 시 제출된 refresh token에서 `tenant_id`(JWT claim 또는 DB row)를 추출
  - 새 access/refresh token의 `tenant_id` 와 비교 → 불일치 시 `TOKEN_TENANT_MISMATCH` 401
  - 보안 이벤트(`auth.token.tenant.mismatch` 또는 동등 이름) 발행 — reuse-detection 과 동일 수준의 audit
- **`LogoutUseCase`** / **`OAuthLoginUseCase`**:
  - refresh token 무효화 시 `tenant_id` 를 키에 포함하여 처리
  - OAuth callback 흐름의 tenant 컨텍스트 결정도 동일 규칙 적용 (account-service `socialSignup` 응답에서 `tenant_id` 수신)
- **Redis 키 패턴**:
  - `login:fail:{tenant_id}:{email}`
  - `refresh:blacklist:{tenant_id}:{jti}`
  - 기존 키와의 호환성: 마이그레이션 기간엔 두 패턴을 모두 읽을 수 있도록 fallback 검토(옵션). 기본은 새 패턴으로 즉시 전환
- **테스트**:
  - 발급된 access token JWT에 `tenant_id`, `tenant_type` claim이 존재하는지 검증
  - 다른 테넌트 refresh token으로 rotation 시도 → `TOKEN_TENANT_MISMATCH` 401
  - 같은 이메일이 두 테넌트에 존재할 때 `tenant_id` 미지정 로그인 → `LOGIN_TENANT_AMBIGUOUS` 400
  - cross-tenant leak 회귀: `(tenant_id, email)` 복합 unique 가 동작하는지

## Out of Scope

- gateway-service의 `tenant_id` claim 검증 + `X-Tenant-Id` 전파 (→ TASK-BE-230)
- account-service의 internal provisioning 컨트롤러 (→ TASK-BE-231)
- admin-service / security-service 의 tenant 인지 (별도 태스크)
- B2B SSO/SAML federation (별도 feature)
- Tenant 엔터티 자체 신규 도입 (→ TASK-BE-228에서 완료)
- JWT 서명 키 rotation (claim 추가만이며 키는 재사용)
- Grace period legacy 토큰 fallback 정책 (gateway 단에서 처리 — TASK-BE-230)

# Acceptance Criteria

- [ ] Flyway 마이그레이션이 적용되어 `credentials`/`refresh_tokens`/`social_identities` 모두 `tenant_id` NOT NULL 컬럼을 보유하며 unique key가 `(tenant_id, email)` / `(tenant_id, provider, provider_user_id)` 로 변경된다
- [ ] `AccountServiceClient.lookupCredential` 응답에 `tenant_id`, `tenant_type`, `accountId` 가 포함된다
- [ ] `JwtSigner` 가 발급한 access token의 payload에 `tenant_id`, `tenant_type` claim이 항상 존재한다 (누락 시 발급 실패)
- [ ] `LoginUseCase` 는 lookup 응답이 다중 매칭일 때 `LOGIN_TENANT_AMBIGUOUS` 400을 반환한다
- [ ] `LoginUseCase` 는 `tenant_id` 가 명시된 경우 해당 테넌트의 credential만 사용한다
- [ ] `RefreshTokenUseCase` 는 제출된 refresh token의 `tenant_id` 와 새 token의 `tenant_id` 가 다르면 `TOKEN_TENANT_MISMATCH` 401을 반환한다
- [ ] Redis 키 패턴이 `login:fail:{tenant_id}:{email}`, `refresh:blacklist:{tenant_id}:{jti}` 로 동작한다
- [ ] `auth.login.attempted/succeeded/failed`, `auth.token.refreshed`, `auth.token.reuse.detected` 이벤트 페이로드에 `tenant_id` 필드가 포함된다
- [ ] cross-tenant rotation 시도 시 보안 이벤트가 발행된다 (reuse-detection 과 유사 수준)
- [ ] 기존 단일 테넌트 통합 테스트는 `tenant_id='fan-platform'` 고정으로 모두 통과한다

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `specs/features/multi-tenancy.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/account-service/architecture.md` (lookupCredential 응답 컨텍스트)
- `rules/traits/multi-tenant.md`
- `rules/traits/transactional.md` (token rotation 일관성)
- `rules/traits/audit-heavy.md` (보안 이벤트)
- `rules/traits/regulated.md` (PII 보존)
- `platform/security-rules.md`
- `platform/error-handling.md`
- `platform/service-types/rest-api.md`

# Related Skills

- `.claude/skills/INDEX.md` (해당 도메인/서비스 스킬 매칭 결과 적용)

# Related Contracts

- `specs/contracts/http/auth-api.md` — login/refresh 응답에 `tenant_id` 노출, 새 에러 코드 `LOGIN_TENANT_AMBIGUOUS`, `TOKEN_TENANT_MISMATCH`
- `specs/contracts/http/internal/auth-to-account.md` — `lookupCredential` 입력에 `tenant_id?`, 응답에 `tenant_id`/`tenant_type`/`accountId`
- `specs/contracts/events/auth-events.md` — 모든 페이로드에 `tenant_id` 필드 추가 (스키마 버전 +1)

# Target Service

- auth-service

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

상위 원칙:

- Layered (`presentation / application / domain / infrastructure`)
- `tenant_id` 검증은 application use-case + infrastructure(JwtSigner) 양쪽에서 fail-closed
- `domain/token` 의 `RefreshToken`/`TokenRotationService` 는 `tenant_id` 를 값으로 보유하며 회전 시 비교 책임을 진다
- HTTP 호출은 `@Transactional` 외부에서 수행 (TASK-BE-069/072 정책 — auth-service 기존 가이드 준수)

# Implementation Notes

- **순서 의존성**: TASK-BE-228 (account-service `tenant_id` 스키마 + `domain/tenant/`) **선행 필수**. account-service의 lookup 응답에서 `tenant_id` 를 받을 수 있어야 본 태스크가 의미를 가진다.
- **후속 태스크**: 본 태스크 완료 후 TASK-BE-230 에서 gateway-service가 발급된 토큰의 `tenant_id` claim을 검증하고 다운스트림으로 전파한다.
- **fail-closed 원칙**: `JwtSigner` 는 `tenant_id` 가 null/빈 문자열이면 발급 자체를 거부한다. 누락 가능성을 코드 경로에서 차단.
- **Redis 키 마이그레이션**: 새 패턴 즉시 전환을 권장. 운영 환경에서 기존 카운터/블랙리스트의 짧은 TTL(분 단위) 덕에 재기록 대기로 자연 만료. 필요 시 듀얼 read 옵션을 추가.
- **다중 매칭 lookup 응답 형태**: 단일 row 응답 강제 대신 응답 schema가 list가 될 수 있도록 internal contract 갱신. presentation에서 list 길이 0 → 401 `INVALID_CREDENTIALS`, length 1 → 정상, length > 1 → 400 `LOGIN_TENANT_AMBIGUOUS`.
- **OAuth callback**: provider userinfo로부터는 tenant 컨텍스트가 직접 도출되지 않음 → 기본값 `fan-platform` 또는 OAuth 진입 URL/state에 tenant context를 실어 전달하는 방식 검토 (구체 결정은 본 태스크 내에서 합의 후 contract 갱신).
- 본 태스크는 account-service contract `auth-to-account.md` 변경에 의존하므로, 컨트랙트 갱신 PR과 동기화하거나 같은 PR에 포함시킨다 ("Contract Rule" 준수).

# Edge Cases

- 같은 이메일이 두 테넌트(`fan-platform`, `wms`)에 존재 + 사용자가 `tenant_id` 미명시 로그인 → `LOGIN_TENANT_AMBIGUOUS` 400
- 사용자가 `tenant_id=wms` 명시 + 해당 테넌트에 credential 없음 → `INVALID_CREDENTIALS` 401 (정보 누설 방지를 위해 ambiguous 와 별도 코드 유지)
- refresh token rotation 시 JWT claim과 DB row의 `tenant_id` 불일치 (조작/버그) → `TOKEN_TENANT_MISMATCH` 401 + 보안 이벤트
- `tenant_id` claim이 누락된 legacy access token 검증 → 본 태스크 범위에선 auth-service가 발급하지 않으므로 발생 불가. gateway-side fallback은 TASK-BE-230 책임
- SUSPENDED 테넌트의 사용자가 로그인 시도 → 401 + 명확한 에러 코드 (예: `TENANT_SUSPENDED`). 페이로드 누설 없도록 일반화 가능
- OAuth 신규 가입 흐름에서 account-service `socialSignup` 응답이 다른 `tenant_id` 를 반환 (코드 결함) → 검증 후 거부 + 알람
- Redis 키 패턴 변경 직후 잠시 동안 `login:fail:{email}`(레거시) row 존재 → 운영 영향 미미. 회귀 테스트로 신규 패턴만 사용됨을 검증

# Failure Scenarios

- account-service lookup 응답에 `tenant_id` 누락 (스펙 위반) → `LoginUseCase` 가 명시적 예외(`AUTH_TENANT_RESOLUTION_FAILED` 등)로 거부. 5xx 또는 401 결정은 운영 정책에 따름
- `JwtSigner` 가 `tenant_id` null로 호출 → `IllegalStateException` (fail-closed)
- DB 마이그레이션 도중 `(tenant_id, email)` 복합 unique 위반 — 단일 테넌트 환경에서 중복 행이 있을 가능성 (현재 production은 single tenant이므로 발생 가능성 낮음). 발생 시 데이터 정합성 점검 + 수동 정리
- Redis 장애로 `login:fail` 카운터 증가 실패 → 기존 fail-open/fail-closed 정책 유지 (변경 없음)
- refresh rotation 시도가 `tenant_id` 비교 단계에서 거부되었으나 보안 이벤트 발행에 실패 → outbox 기록만 유지하고 후속 relay 가 처리 (`rules/traits/transactional.md` T3 일관성)
- account-service contract 갱신이 없어 lookup DTO에 `tenant_id` 가 도착하지 않음 → 본 태스크 컨트랙트 갱신과 동기화 (Hard Stop 사유)

# Test Requirements

- **Unit**:
  - `JwtSigner` 가 `tenant_id` 누락 시 fail-closed
  - `TokenRotationService` 의 tenant 일치 검증
  - `RefreshToken` 값 객체에 `tenant_id` 보존
- **Repository slice (`@DataJpaTest` + Testcontainers)**:
  - `CredentialJpaRepositoryTest`: `(tenant_id, email)` 복합 unique 동작
  - `RefreshTokenJpaRepositoryTest`: `tenant_id` 컬럼 영속/조회
  - `SocialIdentityJpaRepositoryTest`: `(tenant_id, provider, provider_user_id)` 복합 unique
- **Application integration (Testcontainers MySQL+Redis+Kafka)**:
  - 로그인 → access token JWT에 `tenant_id`, `tenant_type` claim 존재
  - 같은 이메일이 두 테넌트에 등록된 상태에서 `tenant_id` 미명시 로그인 → `LOGIN_TENANT_AMBIGUOUS` 400
  - 다른 테넌트 refresh token 으로 rotation 시도 → `TOKEN_TENANT_MISMATCH` 401 + 보안 이벤트 발행
  - Redis 키 `login:fail:fan-platform:user@example.com` 패턴으로 카운트되는지 검증
  - `auth.login.succeeded` 이벤트 페이로드에 `tenant_id` 포함
- **Contract test**:
  - `lookupCredential` 응답 schema 가 `auth-to-account.md` 와 일치

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added (tenant 격리 회귀 + JWT claim 검증 포함)
- [ ] Tests passing (Testcontainers 포함 전체 그린)
- [ ] Contracts updated (`auth-api.md`, `auth-to-account.md`, `auth-events.md`)
- [ ] Specs updated first if required (변경 필요 시 specs 선행 갱신 후 본 태스크 진행)
- [ ] Ready for review
