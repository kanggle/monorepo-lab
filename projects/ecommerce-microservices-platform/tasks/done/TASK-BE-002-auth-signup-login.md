# Task ID

TASK-BE-002

# Title

auth-service 회원가입 + 로그인 + JWT 발급 API 구현

# Status

review

# Owner

backend

# Task Tags

- code
- api

---

# Goal

회원가입(POST /api/auth/signup)과 로그인(POST /api/auth/login) API를 구현한다.
로그인 성공 시 JWT accessToken과 opaque refreshToken을 발급한다.

이 태스크 완료 후: 신규 사용자가 회원가입하고, 이메일/비밀번호로 로그인하여 토큰을 발급받을 수 있어야 한다.

**선행 태스크:** TASK-BE-001 완료 필요

---

# Scope

## In Scope

- POST /api/auth/signup 구현
- POST /api/auth/login 구현
- JWT accessToken 생성 (HS256 또는 RS256, TTL 1시간)
- RefreshToken 생성 및 Redis 저장 (TTL 30일)
- Spring Security 기본 필터 체인 구성 (signup/login은 permitAll)
- 입력 유효성 검증 (Bean Validation)
- 에러 응답 포맷: `{"code": "...", "message": "...", "timestamp": "..."}`
- 컨트롤러 슬라이스 테스트, 애플리케이션 서비스 단위 테스트

## Out of Scope

- 토큰 갱신 API (TASK-BE-003)
- 로그아웃 API (TASK-BE-003)
- 소셜 로그인 (별도 태스크)
- 이메일 인증 (별도 태스크)

---

# Acceptance Criteria

- [ ] POST /api/auth/signup — 201 반환, userId/email/name/createdAt 포함
- [ ] POST /api/auth/signup — 중복 email이면 409 EMAL_ALREADY_EXISTS 반환
- [ ] POST /api/auth/signup — 필수 필드 누락 시 400 VALIDATION_ERROR 반환
- [ ] POST /api/auth/login — 200 반환, accessToken/refreshToken/expiresIn 포함
- [ ] POST /api/auth/login — 잘못된 자격증명이면 401 INVALID_CREDENTIALS 반환
- [ ] accessToken은 유효한 JWT 형식이며 1시간 후 만료
- [ ] refreshToken은 Redis에 저장되며 30일 TTL 적용
- [ ] 비밀번호는 BCrypt 해싱되어 DB에 저장
- [ ] 컨트롤러 슬라이스 테스트 작성
- [ ] 애플리케이션 서비스 단위 테스트 작성

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/platform/error-handling.md`
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/dto-mapping.md`
- `.claude/skills/backend/validation.md`
- `.claude/skills/backend/exception-handling.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — POST /api/auth/signup, POST /api/auth/login

---

# Target Service

- `auth-service`

---

# Architecture

`specs/services/auth-service/architecture.md` — Layered Architecture

계층별 역할:
- presentation: SignupController, LoginController, Request/Response DTO
- application: SignupService, LoginService (트랜잭션, 유스케이스 조율)
- domain: User, RefreshToken, UserRepository interface, TokenGenerator interface
- infrastructure: UserJpaRepository, RedisRefreshTokenRepository, JwtTokenGenerator

---

# Implementation Notes

- JWT 라이브러리: `io.jsonwebtoken:jjwt` 또는 `com.nimbusds:nimbus-jose-jwt`
- JWT payload 포함 필드: `sub` (userId), `email`, `iat`, `exp`
- JWT secret은 application.yml의 환경변수로 외부화 (`${JWT_SECRET}`)
- RefreshToken Redis key 형식: `refresh:{uuid}`, value: userId
- Spring Security: signup/login 경로는 `permitAll()`, 나머지는 `authenticated()`
- GlobalExceptionHandler로 에러 응답 통일

---

# Edge Cases

- 동시에 같은 email로 회원가입 요청 → DB unique 제약으로 409 처리
- 비밀번호 길이 제한: 최소 8자
- email 형식 검증: Bean Validation `@Email`
- name 길이 제한: 최대 50자

---

# Failure Scenarios

- Redis 장애 시 로그인 실패 (RefreshToken 저장 불가) → 500 반환, 로그 기록
- JWT secret 미설정 시 기동 실패 → 환경변수 확인

---

# Test Requirements

- 단위 테스트: SignupService, LoginService (mock repository)
- 컨트롤러 슬라이스 테스트: MockMvc로 요청/응답 검증
- 통합 테스트: Testcontainers(PostgreSQL + Redis)로 실제 DB/Redis 연동

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
