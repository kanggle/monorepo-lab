# Task ID

TASK-BE-004

# Title

auth-service 보안 버그 수정 — DUMMY_HASH, JWT secret 검증

# Status

review

# Owner

backend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

auth-service에 존재하는 보안 버그 2건을 수정한다.

1. `LoginService.DUMMY_HASH`가 유효하지 않은 BCrypt 형식(52자)으로 되어 있어, 존재하지 않는 이메일 로그인 시 `BCryptPasswordEncoder.matches()`가 예외를 발생시킬 수 있다. 이는 타이밍 어택 방지 목적을 무력화한다.
2. `JwtProperties.secret`에 `@NotEmpty`만 있고 최소 길이 검증이 없어, HMAC-SHA256에 부적합한 짧은 secret으로 기동이 가능하다.

이 태스크 완료 후: 존재하지 않는 이메일과 존재하는 이메일의 로그인 응답 시간 차이가 제거되고, 32바이트 미만의 JWT secret으로는 애플리케이션이 기동되지 않는다.

---

# Scope

## In Scope

- `LoginService.DUMMY_HASH`를 유효한 60자 BCrypt 해시로 교체
- `JwtProperties.secret`에 최소 길이(32자) 검증 추가
- 수정된 항목에 대한 단위 테스트

## Out of Scope

- 로그인 rate limiting (gateway 레벨 책임, `specs/platform/security-rules.md`)
- JWT 알고리즘 변경 (RS256 전환 등)
- 비밀번호 정책 변경

---

# Acceptance Criteria

- [ ] `DUMMY_HASH`가 정확히 60자의 유효한 BCrypt 해시 형식(`$2a$10$` + 22자 salt + 31자 hash)이다
- [ ] 존재하지 않는 이메일로 로그인 시 `BCryptPasswordEncoder.matches()`가 예외 없이 false를 반환한다
- [ ] 존재하지 않는 이메일과 존재하는 이메일(틀린 비밀번호)의 로그인 응답이 동일한 401 INVALID_CREDENTIALS이다
- [ ] `jwt.secret`이 32자 미만이면 애플리케이션 기동 시 validation 에러로 실패한다
- [ ] `jwt.secret`이 32자 이상이면 정상 기동된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — POST /api/auth/login 응답 변경 없음

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

변경 대상 레이어: application (LoginService), infrastructure (JwtProperties)

---

# Implementation Notes

### DUMMY_HASH 수정

현재 값: `$2a$10$dummyhashfordummyhashfordummyhashfordummyhashfordummy` (52자)

BCrypt 형식: `$2a$10$` (7자) + salt 22자 + hash 31자 = 60자 필수.
실제 BCrypt 출력값을 사용하거나, BCrypt base-64 알파벳(`./A-Za-z0-9`)으로 정확히 53자(salt+hash)를 구성해야 한다.

가장 안전한 방법: 실제로 `BCryptPasswordEncoder.encode("dummy-never-match")`의 결과를 상수로 사용.

### JWT Secret 길이 검증

`JwtProperties`에 `@Size(min = 32)` 추가. HMAC-SHA256 최소 키 길이는 256bit(32바이트).

---

# Edge Cases

- DUMMY_HASH로 실제 비밀번호가 우연히 매칭되지 않아야 함 → BCrypt 특성상 확률적으로 불가능
- JWT secret이 정확히 32자일 때 기동 성공
- JWT secret이 31자일 때 기동 실패

---

# Failure Scenarios

- DUMMY_HASH가 여전히 유효하지 않은 형식이면 Spring Security 내부에서 예외 발생 → 500 반환 (타이밍 어택 가능)
- JWT secret 검증이 기동 시점이 아닌 런타임에 발생하면 장애 감지 지연

---

# Test Requirements

- 단위 테스트: LoginService — 존재하지 않는 이메일로 로그인 시 예외 없이 401 반환 확인
- 단위 테스트: JwtProperties — 짧은 secret으로 validation 실패 확인
- 기존 LoginServiceTest, 통합 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
