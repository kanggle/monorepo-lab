# Task ID

TASK-BE-009

# Title

auth-service refresh API 계약 수정 — 토큰 로테이션 응답 명시

# Status

review

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`auth-api.md` 계약과 실제 구현이 불일치한다.

계약은 `POST /api/auth/refresh` 응답을 `{ accessToken, expiresIn }` 으로 명시하지만, 구현은 `{ accessToken, refreshToken, expiresIn }` 을 반환한다. 토큰 로테이션(매 refresh마다 새 refreshToken 발급)은 보안적으로 올바른 설계이므로, 계약을 구현에 맞춰 수정한다.

이 태스크 완료 후: 계약과 구현이 일치하고, 클라이언트는 refresh 호출 후 새 refreshToken을 교체해야 함을 명시적으로 인지할 수 있다.

---

# Scope

## In Scope

- `specs/contracts/http/auth-api.md`의 refresh 응답에 `refreshToken` 필드 추가
- Token Rules 섹션에 토큰 로테이션 정책 명시
- 관련 테스트에서 refresh 응답의 `refreshToken` 검증 추가

## Out of Scope

- 구현 코드 변경 (이미 올바르게 구현되어 있음)
- refresh 로직 자체 변경
- 다른 API 계약 수정

---

# Acceptance Criteria

- [x] `auth-api.md`의 `POST /api/auth/refresh` Response 200에 `refreshToken: "string (opaque UUID, rotated)"` 필드가 포함된다
- [x] Token Rules 섹션에 "refresh 호출마다 새 refreshToken이 발급되고 기존 refreshToken은 무효화된다"는 내용이 명시된다
- [x] `AuthRefreshLogoutControllerTest` 또는 `AuthRefreshLogoutIntegrationTest`에서 refresh 응답의 `refreshToken` 필드 존재를 검증한다
- [x] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/auth-service/overview.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md`

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

변경 대상:
- contracts: `auth-api.md` refresh 응답 수정
- test: refresh 응답 검증 추가

---

# Implementation Notes

### 계약 수정 내용

```markdown
**Response 200**
```json
{
  "accessToken": "string (JWT)",
  "refreshToken": "string (opaque UUID, rotated)",
  "expiresIn": 3600
}
```

### Token Rules 추가

```markdown
- On refresh: a new refresh token is issued and the old one is immediately revoked (token rotation).
  Clients must replace the stored refresh token after every refresh call.
```

---

# Edge Cases

- 기존 클라이언트가 refreshToken을 응답에서 무시하더라도 기능 동작은 유지됨
- 계약 변경이므로 다운스트림 클라이언트에 breaking change가 아님 (응답 필드 추가는 하위 호환)

---

# Failure Scenarios

- 계약 수정 후 테스트가 refreshToken 필드를 검증하지 않으면 회귀 발생 가능 → 테스트 추가로 방지

---

# Test Requirements

- 슬라이스 테스트: refresh 응답에 `refreshToken` 필드가 존재하는지 검증
- 통합 테스트: 실제 refresh 호출 후 응답의 `refreshToken`이 이전과 다른지 검증 (토큰 로테이션 확인)

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
