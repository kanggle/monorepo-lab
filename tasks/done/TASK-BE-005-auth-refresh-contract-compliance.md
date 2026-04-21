# Task ID

TASK-BE-005

# Title

auth-service refresh API 계약 준수 및 아키텍처 개선

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

auth-service의 refresh API가 `specs/contracts/http/auth-api.md` 계약을 위반하고 있는 문제를 수정하고, 관련 아키텍처 문제를 함께 개선한다.

**계약 위반 현황:**

`auth-api.md`의 refresh 응답 정의:
```json
{ "accessToken": "string (JWT)", "expiresIn": 3600 }
```

현재 구현은 `refreshToken`을 응답에 포함하고 token rotation을 수행하고 있다. 이는 계약에 정의되지 않은 동작이며, rotation 과정에서 동시 요청 시 복수의 유효한 refresh token이 발급되는 race condition도 존재한다.

이 태스크 완료 후: refresh API가 계약대로 accessToken만 반환하고, token rotation이 제거되어 동시성 취약점이 해소되고, `rotate()` 메서드가 도메인 인터페이스에서 제거된다.

**선행 태스크:** TASK-BE-003 review 완료 후 수행 권장

---

# Scope

## In Scope

- `RefreshResponse` DTO에서 `refreshToken` 필드 제거
- `RefreshTokenService`에서 token rotation 로직 제거 (새 refreshToken 생성 및 rotate 호출 제거)
- `RefreshTokenStore` 인터페이스에서 `rotate()` 메서드 제거
- `RedisRefreshTokenStore`에서 `rotate()` 구현 제거
- `invalidate()`의 revoked TTL 파라미터와 `rotate()`의 TTL 불일치 해소 (rotate 제거로 자연 해소)
- 관련 테스트 수정

## Out of Scope

- token rotation 기능 재설계 (별도 스펙 변경 필요)
- refresh token 갱신 정책 변경 (별도 스펙 변경 필요)
- 다중 디바이스 세션 관리

---

# Acceptance Criteria

- [ ] POST /api/auth/refresh 응답이 `{ "accessToken": "...", "expiresIn": 3600 }` 형식이다 (refreshToken 없음)
- [ ] refresh 요청 시 기존 refreshToken이 Redis에 그대로 유지된다 (rotation 없음)
- [ ] `RefreshTokenStore` 인터페이스에 `rotate()` 메서드가 존재하지 않는다
- [ ] `RedisRefreshTokenStore`에 `rotate()` 구현이 존재하지 않는다
- [ ] 동일한 refreshToken으로 연속 refresh 요청 시 각각 유효한 accessToken을 반환한다
- [ ] 기존 로그아웃 플로우가 정상 동작한다 (invalidate 사용)
- [ ] 컨트롤러 슬라이스 테스트 수정 및 통과
- [ ] 통합 테스트 수정 및 통과

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/springboot-api.md`
- `.claude/skills/backend/dto-mapping.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — POST /api/auth/refresh 응답 형식 준수

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

변경 대상 레이어:
- domain: `RefreshTokenStore` 인터페이스에서 `rotate()` 제거
- application: `RefreshTokenService`에서 rotation 로직 제거
- infrastructure: `RedisRefreshTokenStore`에서 `rotate()` 구현 제거
- presentation: `RefreshResponse` DTO에서 `refreshToken` 필드 제거

---

# Implementation Notes

### RefreshTokenService 변경

기존:
```java
String newRefreshToken = UUID.randomUUID().toString();
refreshTokenStore.rotate(oldToken, newRefreshToken, userId, refreshTtlSeconds);
return new RefreshResponse(newAccessToken, newRefreshToken, expiresIn);
```

변경 후:
```java
// rotation 없이 새 accessToken만 발급
return new RefreshResponse(newAccessToken, expiresIn);
```

### RefreshTokenStore 인터페이스 변경

`rotate()` 메서드 제거. 남는 메서드: `save`, `findUserIdByToken`, `isRevoked`, `invalidate`

### Race Condition 해소

token rotation을 제거하면 동시 refresh 요청이 각각 새 accessToken만 발급하므로, 복수의 유효한 refreshToken이 생성되는 race condition이 자연 해소된다.

---

# Edge Cases

- 동일한 refreshToken으로 동시에 여러 refresh 요청 → 각각 유효한 accessToken 반환 (refreshToken은 변경 없음)
- refreshToken TTL이 거의 만료된 상태에서 refresh → 새 accessToken 정상 발급, refreshToken은 남은 TTL대로 자연 만료
- 로그아웃 후 refresh 시도 → 401 REFRESH_TOKEN_REVOKED (기존 동작 유지)

---

# Failure Scenarios

- Redis 장애 시 refreshToken 조회 실패 → 500 반환
- 사용자가 삭제되었지만 refreshToken이 남아있는 경우 → 401 INVALID_REFRESH_TOKEN

---

# Test Requirements

- 단위 테스트: `RefreshTokenService` — rotation 없이 accessToken만 반환 확인
- 단위 테스트: `RedisRefreshTokenStore` — `rotate()` 관련 테스트 제거
- 컨트롤러 슬라이스 테스트: refresh 응답에 `refreshToken` 필드 없음 확인
- 통합 테스트: 동일 refreshToken으로 연속 refresh 성공 확인
- 통합 테스트: 로그아웃 후 refresh 실패 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
