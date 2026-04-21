# Task ID

TASK-BE-015

# Title

auth-service 동시 세션 제한 및 비활성 타임아웃 — 사용자당 최대 세션 수 제한, 비활성 세션 자동 만료

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

사용자당 동시 활성 세션 수를 제한하고, 일정 기간 비활성 상태인 세션을 자동 만료시킨다.
무제한 동시 접속 및 장기 미사용 세션 유지로 인한 보안 위험을 해소한다.

이 태스크 완료 후: 사용자당 최대 5개 세션이 유지되고, 초과 시 가장 오래된 세션이 제거된다. 7일간 비활성 세션은 자동 만료된다.

---

# Scope

## In Scope

- Redis 기반 사용자별 세션 레지스트리 구현 (`UserSessionRegistry`)
- 로그인 시 세션 등록 및 동시 세션 수 확인
- 최대 세션 수 초과 시 가장 오래된 세션 제거 (FIFO)
- 토큰 갱신 시 세션 활동 시간 갱신 (비활성 타임아웃 리셋)
- 비활성 타임아웃(7일) 초과 세션 자동 만료 (Redis TTL 활용)
- 로그아웃 시 세션 레지스트리에서 제거
- 설정값 외부화 (최대 세션 수, 비활성 타임아웃)
- `SessionLimitExceeded` 이벤트 발행 (TASK-BE-014 의존, 없으면 로깅으로 대체)

## Out of Scope

- 세션 목록 조회 API (별도 태스크)
- 특정 세션 강제 종료 API (별도 태스크)
- 디바이스별 세션 구분
- 세션 수 제한 변경 API (관리자 기능)

---

# Acceptance Criteria

- [ ] 사용자당 최대 동시 세션 수가 설정값으로 관리된다 (기본값 5)
- [ ] 로그인 시 세션이 Redis에 등록된다
- [ ] 동시 세션 수 초과 시 가장 오래된 세션의 리프레시 토큰이 제거된다
- [ ] 제거된 세션으로 토큰 갱신 시도 시 `INVALID_REFRESH_TOKEN` 에러가 반환된다
- [ ] 토큰 갱신 시 해당 세션의 마지막 활동 시간이 갱신된다
- [ ] 비활성 타임아웃(기본 7일)을 초과한 세션은 자동 만료된다
- [ ] 로그아웃 시 세션 레지스트리에서 해당 세션이 제거된다
- [ ] `UserSessionRegistry` 인터페이스가 도메인 계층에 위치한다
- [ ] Redis 구현체가 인프라 계층에 위치한다
- [ ] 단위 테스트 및 통합 테스트가 추가된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/auth-service/overview.md`
- `specs/services/auth-service/architecture.md`
- `specs/platform/security-rules.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

계층 배치:
- Domain: `UserSessionRegistry` 인터페이스, `SessionProperties` 설정 인터페이스
- Application: `LoginService`, `RefreshTokenService`, `LogoutService`에서 세션 레지스트리 호출
- Infrastructure: `RedisUserSessionRegistry` (Redis Sorted Set 기반)
- Infrastructure Config: `SessionConfig` (최대 세션 수, 비활성 타임아웃 설정)

---

# Implementation Notes

### Redis 데이터 구조

사용자별 세션 목록을 Redis Sorted Set으로 관리한다:
- Key: `auth:sessions:{userId}`
- Member: `refreshTokenKey` (SHA-256 해시)
- Score: 마지막 활동 시간 (epoch millis)

```
ZADD auth:sessions:{userId} {timestamp} {refreshTokenKeyHash}
```

### 세션 제한 로직 (Lua Script)

```
-- 1. 비활성 세션 제거 (score < 현재시간 - 비활성타임아웃)
ZREMRANGEBYSCORE key 0 {cutoff}
-- 2. 현재 세션 수 확인
local count = ZCARD key
-- 3. 초과 시 가장 오래된 세션 제거
if count >= maxSessions then
    local evicted = ZRANGE key 0 0
    ZREM key evicted[1]
    return evicted[1]  -- 제거된 세션 ID 반환
end
-- 4. 새 세션 추가
ZADD key {timestamp} {newSessionKey}
return nil
```

### 설정값

```yaml
auth:
  session:
    max-concurrent: 5
    inactivity-timeout: 604800  # 7일 (초)
```

### 기존 코드 변경 최소화

`LoginService`에서 토큰 발급 후 세션 등록, `RefreshTokenService`에서 갱신 시 활동 시간 업데이트, `LogoutService`에서 세션 제거. 기존 리프레시 토큰 로직은 변경하지 않고, 세션 레지스트리를 추가 레이어로 적용한다.

---

# Edge Cases

- 동시에 같은 사용자가 여러 디바이스에서 로그인 → Lua Script 원자성으로 경쟁 조건 방지
- 세션 제거 후 해당 리프레시 토큰으로 갱신 시도 → 기존 `INVALID_REFRESH_TOKEN` 에러 활용
- Redis 장애 시 세션 제한 동작 불가 → 세션 제한 실패 시 로그인은 허용 (fail-open), 에러 로깅
- 사용자가 정확히 최대 세션 수만큼 동시 로그인 → 새 로그인 시 하나 제거 후 추가
- 비활성 타임아웃과 리프레시 토큰 TTL(30일) 차이 → 비활성 타임아웃(7일)이 먼저 세션 만료, 리프레시 토큰은 Redis에 남아있지만 세션 레지스트리에 없으면 유효

---

# Failure Scenarios

- Redis Sorted Set 원자적 연산 실패 → Lua Script로 보장, 실패 시 로그인 허용 + 에러 로깅
- Lua Script 실행 시간 초과 → Redis timeout 설정 내에서 처리, 초과 시 로그인 허용
- 세션 레지스트리와 리프레시 토큰 스토어 간 불일치 → 세션 레지스트리 없는 토큰은 유효하되, 세션이 없는 상태로 간주 (점진적 정합성)

---

# Test Requirements

- 단위 테스트: `RedisUserSessionRegistryTest` — 세션 등록, 제거, 초과 시 FIFO 제거, 비활성 제거 검증
- 단위 테스트: `LoginService` / `LogoutService` / `RefreshTokenService` 업데이트 — 세션 레지스트리 호출 검증
- 통합 테스트: 최대 세션 수 초과 로그인 → 오래된 세션 제거 → 제거된 토큰 갱신 실패 시나리오

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
