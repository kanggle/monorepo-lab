# Service Architecture — auth-service

## Service

`auth-service`

## Service Type

`rest-api` — 인증 전담 서비스. 로그인/로그아웃, JWT 발급, refresh token 회전, 토큰 재사용 탐지, Redis 기반 로그인 실패 카운팅.

적용되는 규칙: [platform/service-types/rest-api.md](../../../platform/service-types/rest-api.md)

## Architecture Style

**Layered Architecture** — `presentation / application / domain / infrastructure` 4계층. 의존성 방향은 **일방향**: 위에서 아래로만.

## Why This Architecture

- **중간 복잡도 도메인**: credentials, sessions, refresh token rotation state — DDD/Hexagonal이 제공하는 수준의 추상화를 필요로 하지 않음. 동시에 filter-only 구조(게이트웨이)로는 담을 수 없는 **명확한 도메인 규칙**이 있음 (비밀번호 정책, 토큰 재사용 탐지, 실패 카운터 결정 경로).
- **프레임워크 친화성**: Spring Boot의 @Transactional 경계가 layered에서 가장 자연스러움. 토큰 회전 같은 원자적 연산이 트랜잭션으로 깔끔히 표현됨.
- **이벤트 발행 경로**: application layer가 도메인 연산 후 outbox로 이벤트를 기록하는 패턴이 layered에서 가장 명확함 ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T3).
- **테스트 가능성**: 도메인 로직을 infrastructure에서 격리하여 순수 단위 테스트 가능. `presentation` slice 테스트와 `@DataJpaTest` repository 테스트를 표준 패턴으로 사용.
- **테넌트 컨텍스트의 운반자**: 본 서비스는 JWT 발급 책임을 지므로 [specs/features/multi-tenancy.md](../../features/multi-tenancy.md)에서 정의한 `tenant_id` claim을 access/refresh token에 실어 전체 플랫폼에 전파한다. 로그인 입력의 tenant 컨텍스트는 account-service의 credential lookup 응답에서 받은 `tenant_id`로 결정한다.

## Internal Structure Rule

```
apps/auth-service/src/main/java/com/example/auth/
├── AuthApplication.java
├── presentation/             ← HTTP 컨트롤러, 요청/응답 DTO, 예외 처리
│   ├── LoginController.java             ← DEPRECATED (ADR-001 D2-b, 2026-08-01 제거 목표)
│   │                                      응답에 Deprecation + Sunset 헤더 포함 (RFC 8594, RFC 9745)
│   ├── LogoutController.java
│   ├── RefreshController.java
│   ├── JwksController.java              ← gateway 대상 JWKS 엔드포인트
│   ├── dto/
│   │   ├── LoginRequest.java
│   │   ├── LoginResponse.java
│   │   └── RefreshRequest.java
│   └── exception/
│       └── AuthExceptionHandler.java
├── application/              ← use-case 서비스, 트랜잭션 경계
│   ├── LoginUseCase.java
│   ├── LogoutUseCase.java
│   ├── RefreshTokenUseCase.java
│   ├── CredentialVerificationService.java
│   └── event/
│       └── AuthEventPublisher.java      ← outbox 경유
├── domain/                   ← 엔터티, 값 객체, 도메인 서비스, 포트 인터페이스
│   ├── credentials/
│   │   ├── CredentialHash.java          ← 값 객체 (argon2/bcrypt)
│   │   └── PasswordPolicy.java
│   ├── token/
│   │   ├── AccessToken.java
│   │   ├── RefreshToken.java
│   │   ├── TokenPair.java
│   │   ├── TokenRotationService.java
│   │   └── TokenReuseDetector.java
│   ├── session/
│   │   └── SessionContext.java          ← 값 객체 (ip, user_agent, device_id)
│   └── repository/                      ← 포트 인터페이스
│       ├── CredentialRepository.java
│       ├── RefreshTokenRepository.java
│       └── LoginAttemptCounter.java     ← Redis 인터페이스
└── infrastructure/           ← JPA, Redis, Kafka, JWT 서명 구현체
    ├── persistence/
    │   ├── CredentialJpaEntity.java
    │   ├── CredentialJpaRepository.java
    │   ├── RefreshTokenJpaEntity.java
    │   └── RefreshTokenJpaRepository.java
    ├── redis/
    │   └── RedisLoginAttemptCounter.java
    ├── kafka/
    │   └── AuthKafkaProducer.java       ← outbox relay
    ├── jwt/
    │   ├── JwtSigner.java               ← RSA 서명
    │   └── JwksProvider.java
    ├── oauth2/                          ← TASK-BE-251: Spring Authorization Server 설정
    │   ├── AuthorizationServerConfig.java   ← SAS SecurityFilterChain @Order(1)
    │   │                                       revocation + introspection 엔드포인트 활성화 (Phase 2c)
    │   ├── TenantClaimTokenCustomizer.java  ← access/id token에 tenant_id, tenant_type 주입
    │   ├── TenantIntrospectionCustomizer.java  ← introspect 응답에 tenant_id, tenant_type 추가
    │   │                                          (RFC 7662 extension, Phase 2c)
    │   ├── OidcUserInfoMapper.java          ← /oauth2/userinfo 응답 구성 (account-service 조회)
    │   ├── SasRefreshTokenAuthenticationProvider.java  ← refresh_token grant + reuse detection
    │   └── DomainSyncOAuth2AuthorizationService.java   ← SAS ↔ JPA RefreshTokenRepository 동기화
    │                                                      revoke 시 JPA 도메인 스토어도 갱신 (Phase 2c)
    ├── client/
    │   └── AccountServiceClient.java    ← 내부 HTTP, credential lookup
    └── config/
        ├── PersistenceConfig.java
        ├── RedisConfig.java
        ├── KafkaConfig.java
        └── SecurityConfig.java          ← @Order(2) 기존 /api/auth/** 체인
```

### SAS 도입에 따른 공존 정책 (ADR-001 D2-b)

| 구분 | 엔드포인트 | 상태 | 제거 일정 |
|---|---|---|---|
| **표준 OIDC (SAS)** | `/oauth2/**`, `/.well-known/openid-configuration` | 활성 | — |
| **레거시** | `POST /api/auth/login` | **DEPRECATED 2026-05-01** | 2026-08-01 |
| **레거시** | `POST /api/auth/refresh` | 유지 (TASK-BE-259 이후 결정) | 미정 |
| **레거시** | `POST /api/auth/logout` | 유지 | 미정 |

**SAS 필터 체인 우선순위**:
- `@Order(1)` — `AuthorizationServerConfig.authorizationServerSecurityFilterChain`: `/oauth2/**`, `/.well-known/**` 전담
- `@Order(2)` — `SecurityConfig.filterChain`: `/api/auth/**`, `/api/accounts/**`, `/actuator/**`, `/internal/**`
- 두 체인은 request matcher로 완전 분리 — 상호 간섭 없음

**revocation 동작 흐름 (Phase 2c)**:
1. `POST /oauth2/revoke` → SAS `OAuth2TokenRevocationEndpointFilter` 수신
2. SAS built-in `OAuth2TokenRevocationAuthenticationProvider` → `oAuth2AuthorizationService.remove(authorization)`
3. `DomainSyncOAuth2AuthorizationService.remove()` → JPA `RefreshTokenRepository`에서 해당 refresh token `revoked = TRUE`
4. 이후 `POST /oauth2/introspect` → `active = false`

## Allowed Dependencies

```
presentation → application → domain
                     ↓
              infrastructure → domain (포트 구현)
```

- `presentation` → `application` (use-case 호출)
- `presentation` → [libs/java-web](../../../libs/java-web) (에러 핸들러, DTO 베이스)
- `application` → `domain` (엔터티, 포트 인터페이스)
- `application` → [libs/java-messaging](../../../libs/java-messaging) (outbox writer)
- `infrastructure` → `domain` (포트 구현 방향)
- `infrastructure` → [libs/java-common](../../../libs/java-common), [libs/java-security](../../../libs/java-security), [libs/java-observability](../../../libs/java-observability)

## Forbidden Dependencies

- ❌ `domain` → `infrastructure`, `application`, `presentation` — 도메인은 순수 자바 (의존성 역전)
- ❌ `domain` → Spring 프레임워크 (JPA 애노테이션, @Service 등) — 도메인 엔터티는 POJO
- ❌ `presentation`에서 직접 `repository` 포트 호출 — 반드시 `application`을 경유
- ❌ `application`에서 JPA 엔터티·Redis 키 직접 사용 — 반드시 `domain`의 포트 인터페이스 경유
- ❌ 다른 서비스의 도메인 모델 import — 내부 HTTP `AccountServiceClient`로만 통신
- ❌ 도메인 로직을 `infrastructure`에 두기 (예: 토큰 회전 결정을 `RefreshTokenJpaRepository` 안에서 수행)
- ❌ refresh token rotation·logout·force-logout에서 `tenant_id` 검증 누락 — 다른 테넌트의 token으로 rotation 시도는 401 `TOKEN_TENANT_MISMATCH`로 거부 ([specs/features/multi-tenancy.md](../../features/multi-tenancy.md))
- ❌ `tenant_id` claim 없이 access token 발급 (서명 단계에서 fail-closed)

## Boundary Rules

### presentation/
- 요청 검증(format)만 수행 — 비즈니스 규칙은 application으로 위임
- 응답 DTO로 도메인 엔터티를 **절대 노출하지 않음**
- 예외는 `AuthExceptionHandler`가 [platform/error-handling.md](../../../platform/error-handling.md)의 표준 포맷으로 변환

### application/
- `@Transactional` 경계 소유. 단일 use-case = 단일 트랜잭션
- 여러 도메인 서비스를 조합하여 사용 사례 흐름을 조율
- 이벤트 발행은 **반드시 outbox 경유** ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T3)
- use-case 결과는 도메인 결과를 presentation-friendly DTO로 매핑
- **로그인 흐름의 tenant 컨텍스트 결정**: `LoginUseCase`는 (1) account-service `AccountServiceClient.lookupCredential(email)`을 호출하여 응답에서 `tenant_id`·`tenant_type`·`accountId`를 받음, (2) 비밀번호 검증 통과 시 `tenant_id`·`tenant_type`을 access token claim과 `refresh_tokens.tenant_id` 컬럼에 동시에 영속, (3) 발행 이벤트(`auth.login.succeeded` 등)에도 `tenant_id` 포함. 같은 이메일이 두 테넌트에 등록될 수 있으므로 lookup 응답이 다중 row가 가능 → 프로토콜은 `(email, tenant_id?)` 입력으로 단일 row 응답을 강제하거나, 다중 매칭 시 `LOGIN_TENANT_AMBIGUOUS` 400으로 명시적 tenant 선택을 요구한다(상세는 [specs/contracts/http/internal/auth-to-account.md](../../contracts/http/internal/) 갱신 시 확정)
- **Refresh rotation의 tenant 무결성**: `RefreshTokenUseCase`는 제출된 refresh token의 `tenant_id`(JWT claim 또는 DB row)와 새로 발급할 token의 `tenant_id`가 일치해야 한다. 불일치 시 `TOKEN_TENANT_MISMATCH` 401 + reuse-detection과 동일 수준의 보안 이벤트 발행
- **모든 HTTP는 `@Transactional` 밖에서 수행** (TASK-BE-069 + TASK-BE-072): OAuth 콜백처럼 외부 provider HTTP, 내부 account-service HTTP, DB 쓰기가 섞인 경로는 use-case(`OAuthLoginUseCase#callback`)가 먼저 (1) provider token+userinfo exchange, (2) 로컬 `SocialIdentityJpaRepository` 비-트랜잭션 조회, (3) `accountServicePort.socialSignup`(신규 identity 경로에만), (4) `accountServicePort.getAccountStatus`를 모두 수행한 뒤, 결과를 extended `OAuthCallbackTxnCommand` (accountId, isNewAccount, accountStatus)로 `@Transactional` 빈(`OAuthLoginTransactionalStep#persistLogin`)에 전달한다. 트랜잭션 내부에는 **어떠한 HTTP 호출도 없다** — Hikari connection pinning 제거가 목적(TASK-BE-062 #18 CI에서 관측). 보상 노트: provider HTTP + account-service HTTP가 성공한 뒤 DB 커밋이 실패하면 사용자는 로그인 실패로 인식하고 outbox 롤백으로 downstream 이벤트는 발행되지 않는다. account-service `socialSignup`은 (email, provider) 기준으로 멱등이므로 retry 시에도 중복 계정을 만들지 않는다. provider-side revoke 보상은 수행하지 않는다. TOCTOU: identity 존재 체크가 비-트랜잭션으로 이동했지만 트랜잭션 내 upsert와 DB unique key `(provider, provider_user_id)`가 동시 삽입을 막는다.

### domain/
- 순수 비즈니스 규칙. 프레임워크 의존 없음
- `TokenRotationService`, `TokenReuseDetector`, `PasswordPolicy`는 이 레이어에 존재
- 리포지토리 인터페이스(포트)만 선언 — 구현은 `infrastructure`

### infrastructure/
- 기술 상세의 어댑터. JPA, Redis, Kafka, WebClient, JWT 라이브러리 모두 여기
- `client/AccountServiceClient`는 account-service를 내부 HTTP로 호출 ([specs/contracts/http/internal/auth-to-account.md](../../contracts/http/internal/)) — 응답은 내부 DTO로 번역 후 `domain`으로 전달. **credential lookup 응답에 `tenant_id` 포함**, 로그인 use-case가 이를 토큰 발급에 사용
- `jwt/JwtSigner`는 access token payload에 `tenant_id`, `tenant_type` claim을 포함하여 서명. claim 누락 시 발급 실패(fail-closed)

## Integration Rules

- **HTTP 컨트랙트 (외부)**: [specs/contracts/http/auth-api.md](../../contracts/http/) — `/api/auth/login`, `/api/auth/logout`, `/api/auth/refresh`, `/api/auth/jwks`. 로그인 응답·refresh 응답에 `tenant_id` 노출
- **HTTP 컨트랙트 (내부)**: [specs/contracts/http/internal/auth-to-account.md](../../contracts/http/internal/) — credential lookup(응답에 `tenant_id`·`tenant_type` 포함), 계정 상태 조회
- **이벤트 발행**: [specs/contracts/events/auth-events.md](../../contracts/events/) — `auth.login.attempted`, `auth.login.failed`, `auth.login.succeeded`, `auth.token.refreshed`, `auth.token.reuse.detected`. 모두 **outbox 경유**, 페이로드에 `tenant_id` 포함
- **퍼시스턴스**: MySQL — `credentials`, `refresh_tokens`, `social_identities`, `outbox_events`, `processed_events` (idempotency). 모두 `tenant_id` NOT NULL. `credentials.email`은 `(tenant_id, email)` unique
- **Redis**: `login:fail:{tenant_id}:{email}` 카운터 (`rules/traits/transactional.md` T1), `refresh:blacklist:{tenant_id}:{jti}`, `jwks:cache` (서명 키는 테넌트 공통)

## Testing Expectations

| 레이어 | 목적 | 도구 |
|---|---|---|
| Unit | 토큰 회전·재사용 탐지·패스워드 정책 로직 | JUnit 5 |
| Repository slice | JPA 쿼리 · 낙관적 락 | `@DataJpaTest` + Testcontainers (MySQL) |
| Application integration | use-case 트랜잭션 · outbox 기록 · Redis 카운터 | Testcontainers (MySQL+Redis+Kafka) |
| Controller slice | DTO validation · 에러 포맷 | `@WebMvcTest` |
| Contract | 응답이 [specs/contracts/http/auth-api.md](../../contracts/http/)와 일치 | 계약 테스트 |

**필수 시나리오**: 5회 실패 → 429 → Redis 카운터 증가 / 만료된 access token → 401 / refresh rotation 성공 / 이미 사용된 refresh token → `token.reuse.detected` 이벤트 + 해당 세션 전체 invalidate ([rules/traits/transactional.md](../../../rules/traits/transactional.md) T8과 [rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A1 교차) / **tenant 격리 회귀**: 발급된 access token의 `tenant_id` claim 존재 검증, 다른 테넌트의 refresh로 rotation 시도 시 `TOKEN_TENANT_MISMATCH` 401 / 같은 이메일이 두 테넌트에 등록된 상태에서 tenant 명시 없는 로그인 → `LOGIN_TENANT_AMBIGUOUS` 400.

## Change Rule

1. 도메인 로직 변경(토큰 회전 규칙, 실패 카운트 임계치, 패스워드 정책)은 [specs/features/authentication.md](../../features/) 업데이트 선행
2. API 경로·응답 스키마 변경은 [specs/contracts/http/auth-api.md](../../contracts/http/) 업데이트 선행
3. 이벤트 페이로드 변경은 [specs/contracts/events/auth-events.md](../../contracts/events/) + 스키마 버전 증가
4. credentials 테이블 스키마 변경은 Flyway migration + [specs/services/auth-service/data-model.md](./data-model.md) 업데이트
5. 모든 변경은 테스트 추가와 함께 이루어져야 함 ([rules/traits/audit-heavy.md](../../../rules/traits/audit-heavy.md) A7 fail-closed 원칙)
6. JWT claim 스키마(`tenant_id`, `tenant_type` 등) 또는 tenant 컨텍스트 확정 프로토콜 변경은 [specs/features/multi-tenancy.md](../../features/multi-tenancy.md) 업데이트 선행
