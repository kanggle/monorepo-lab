# Task ID

TASK-BE-003

# Title

auth-service 토큰 갱신 + 로그아웃 API 구현

# Status

review

# Owner

backend

# Task Tags

- code
- api

---

# Goal

토큰 갱신(POST /api/auth/refresh)과 로그아웃(POST /api/auth/logout) API를 구현한다.
유효한 refreshToken으로 새 accessToken을 발급하고, 로그아웃 시 refreshToken을 즉시 폐기한다.

이 태스크 완료 후: auth-service의 전체 인증 플로우(회원가입 → 로그인 → 토큰 갱신 → 로그아웃)가 완성된다.

**선행 태스크:** TASK-BE-002 완료 필요

---

# Scope

## In Scope

- POST /api/auth/refresh 구현
- POST /api/auth/logout 구현 (Bearer JWT 필요)
- Redis에서 refreshToken 조회 및 TTL 검증
- 로그아웃 시 Redis에서 refreshToken 삭제
- JWT 검증 필터 구현 (Authorization: Bearer 헤더)

## Out of Scope

- accessToken 블랙리스트 (refreshToken 폐기로 충분)
- 다중 디바이스 로그아웃 (별도 태스크)

---

# Acceptance Criteria

- [ ] POST /api/auth/refresh — 유효한 refreshToken으로 새 accessToken 발급 (200)
- [ ] POST /api/auth/refresh — 존재하지 않는 refreshToken이면 401 INVALID_REFRESH_TOKEN
- [ ] POST /api/auth/refresh — 이미 폐기된 refreshToken이면 401 REFRESH_TOKEN_REVOKED
- [ ] POST /api/auth/logout — 204 반환, Redis에서 refreshToken 삭제됨
- [ ] POST /api/auth/logout — JWT 없이 요청 시 401 UNAUTHORIZED
- [ ] JWT 검증 필터가 모든 authenticated() 경로에 적용됨
- [ ] 컨트롤러 슬라이스 테스트 작성
- [ ] 통합 테스트 작성

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/platform/error-handling.md`
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/exception-handling.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — POST /api/auth/refresh, POST /api/auth/logout

---

# Target Service

- `auth-service`

---

# Architecture

`specs/services/auth-service/architecture.md` — Layered Architecture

추가 구성:
- infrastructure: JwtAuthenticationFilter (OncePerRequestFilter)
- application: RefreshTokenService, LogoutService
- domain: RefreshToken 폐기 정책

---

# Implementation Notes

- JwtAuthenticationFilter: `Authorization: Bearer <token>` 파싱 → SecurityContext 설정
- refresh 요청 시 Redis TTL 확인 후 새 accessToken만 발급 (refreshToken은 유지)
- logout 시 Redis에서 `refresh:{token}` 키 삭제
- 폐기된 토큰 구분: 키가 없으면 INVALID_REFRESH_TOKEN (만료 or 없음), 별도 폐기 마킹 필요 시 `revoked:{token}` 키 추가 고려

---

# Edge Cases

- 만료된 JWT로 logout 시도 → 401 (필터에서 차단)
- 동시에 같은 refreshToken으로 refresh 요청 → Redis atomic 연산으로 중복 발급 방지
- refreshToken TTL이 1분 미만으로 남은 경우 → 그대로 새 accessToken 발급 (refreshToken TTL은 건드리지 않음)

---

# Failure Scenarios

- Redis 장애 시 refresh/logout 실패 → 503 또는 500 반환
- JWT 검증 실패 (서명 오류, 만료) → 401 반환

---

# Test Requirements

- 단위 테스트: RefreshTokenService, LogoutService
- 컨트롤러 슬라이스 테스트: MockMvc (유효/무효 토큰 시나리오)
- 통합 테스트: Testcontainers(PostgreSQL + Redis) 전체 플로우

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
