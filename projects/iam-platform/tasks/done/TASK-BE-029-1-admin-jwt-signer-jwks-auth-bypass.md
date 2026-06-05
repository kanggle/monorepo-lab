# Task ID

TASK-BE-029-1-admin-jwt-signer-jwks-auth-bypass

# Title

admin-service — 자체 JWT 발급 인프라: JwtSigner + JWKS 엔드포인트 + auth-sub-tree bypass

# Status

ready

# Owner

backend

# Task Tags

- code
- api

# depends_on

- TASK-BE-029a-admin-idp-boundary-and-totp-kms

---

# Goal

Admin IdP 자체 JWT 발급 기반을 갖춘다. 후속 태스크(029-2 enrollment, 029-3 login)가 이 인프라 위에서 동작하도록, JwtSigner·JWKS·unauthenticated sub-tree bypass·property 스키마를 먼저 확정한다. 2FA/로그인 로직 자체는 포함하지 않는다.

---

# Scope

## In Scope

### 1) JwtSigner bean
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/security/JwtSigner.java`
- RSA256, kid 포함 JWS 발급. 호출부는 claims만 전달.
- property 로딩:
  - `admin.jwt.active-signing-kid: v1` (필수)
  - `admin.jwt.signing-keys`: `Map<String, String>` — kid → PEM 개인키 (현 단계 property placeholder, 프로덕션 KMS는 후속)
- `@Validated` + `@NotBlank` fail-fast. `@ConfigurationProperties("admin.jwt")` 도입.

### 2) JwtVerifier 중복 정리
- 기존 `JwtConfig`와 새 JwtSigner 구성 사이에 `@Primary JwtVerifier` 충돌 방지. 단일 `operatorJwtVerifier` 빈이 `iss=admin-service` 강제하도록 정합화.
- 기존 검증 경로는 회귀 없음.

### 3) JWKS 엔드포인트
- `GET /.well-known/admin/jwks.json` — 현재 활성 kid 및 알려진 public key를 JSON으로 반환 (`{keys: [...]}` 표준 JWKS 포맷)
- Spring Security `SecurityFilterChain`에서 `permitAll()`
- 존재하는 모든 kid의 public key를 포함 (rotation 시 구/신 공존 허용)

### 4) OperatorAuthenticationFilter bypass
- 다음 경로를 `shouldNotFilter`에서 true:
  - `POST /api/admin/auth/login`
  - `POST /api/admin/auth/2fa/enroll`
  - `POST /api/admin/auth/2fa/verify`
  - `GET /.well-known/admin/jwks.json`
- 해당 경로가 아직 엔드포인트로 존재하지 않아도 현 단계에서 bypass만 선언 (404는 Spring 기본 처리)
- `X-Operator-Reason` 헤더 요구사항도 이 경로에서 skip되도록 관련 필터/인터셉터 조정.

### 5) RequiresPermissionAspect 예외 처리
- unauth sub-tree(`/api/admin/auth/**`, `/.well-known/admin/**`)는 aspect deny-by-default 미적용. 즉 aspect pointcut이 이 패키지/URL에 매치되지 않도록 제한. mutation 감지 로직은 그대로.

### 6) Smoke test
- `JwtSignerTest` — 생성된 JWS 서명 검증, claims 포함 확인, kid 헤더 확인
- `JwksControllerTest` (@WebMvcTest) — 200 + 표준 JWKS 포맷 + 활성 kid의 modulus/exponent 존재 검증 + 무인증 접근
- `OperatorAuthenticationFilterBypassTest` — auth sub-tree 요청 시 필터 skip
- 기존 `AdminIntegrationTest` 회귀 없음

### 7) property placeholder
- `application.yml`: `admin.jwt.active-signing-kid: v1`, `admin.jwt.signing-keys.v1: ${ADMIN_JWT_V1_PEM:dev-only-key-base64url}`
- `application-test.yml`: 테스트 키 고정

## Out of Scope

- `POST /auth/login`, `POST /auth/2fa/*` 실제 엔드포인트 (029-3/029-2)
- TOTP / recovery codes / bootstrap token (029-2)
- AES-GCM secret encryption (029-2)
- Flyway `V0013` 마이그레이션 (029-2)

---

# Acceptance Criteria

- [ ] `JwtSigner` bean이 claims 입력으로 올바른 JWS(kid 포함) 반환
- [ ] `@Primary JwtVerifier` 빈 충돌 없음, 어플리케이션 부팅 성공
- [ ] `GET /.well-known/admin/jwks.json`이 무인증으로 200 + 유효 JWKS 반환
- [ ] `/api/admin/auth/**` 경로가 OperatorAuthenticationFilter에서 bypass
- [ ] `RequiresPermissionAspect`가 unauth sub-tree를 가드하지 않음
- [ ] `./gradlew :apps:admin-service:test` 통과

---

# Related Specs

- `specs/services/admin-service/architecture.md` (Admin IdP Boundary)
- `specs/services/admin-service/rbac.md` (D4 issuer)
- `specs/services/admin-service/security.md` (Bootstrap Token 초안 — 참고만)
- `specs/contracts/http/admin-api.md` (Authentication Exceptions)

# Related Contracts

- `specs/contracts/http/admin-api.md`

---

# Target Service

- `apps/admin-service`

---

# Edge Cases

- 활성 kid와 signing-keys 맵 미일치 → `@PostConstruct` 시점 fail-fast
- rotation 중 동시 두 kid 공존 → JWKS가 두 key 모두 반환
- dev-only 기본 키 사용 시 WARN 로그

---

# Failure Scenarios

- JWKS JSON 생성 실패 → 500, application 부팅은 성공 유지 (lazy fail 아님)

---

# Test Requirements

- 위 4개 테스트 클래스
- 기존 회귀 없음

---

# Definition of Done

- [ ] 구현 + 테스트 완료
- [ ] Ready for review
