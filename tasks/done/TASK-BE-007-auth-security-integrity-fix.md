# Task ID

TASK-BE-007

# Title

auth-service 보안 및 데이터 정합성 수정 — 비밀번호 최대 길이 제한 및 Redis 원자적 연산

# Status

done

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

auth-service의 보안 취약점과 데이터 정합성 문제를 수정한다.

**문제 1: SignupRequest 비밀번호 최대 길이 미제한**

`LoginRequest`에는 `@Size(max = 128)`이 있지만 `SignupRequest`에는 최대 길이 제한이 없다. BCrypt는 입력 길이에 비례하여 연산 비용이 증가하므로, 극단적으로 긴 비밀번호로 DoS 공격이 가능하다.

**문제 2: Redis invalidate 비원자적 연산**

`RedisRefreshTokenStore.invalidate()`에서 `delete(key)`와 `set(revokedKey)`가 별개 명령으로 실행된다. 두 명령 사이에 프로세스가 중단되면 토큰이 유효하지도 폐기되지도 않은 상태가 된다.

이 태스크 완료 후: SignupRequest에 비밀번호 최대 길이가 적용되고, Redis invalidate가 원자적으로 실행된다.

---

# Scope

## In Scope

- `SignupRequest`에 `@Size(max = 128)` 추가
- `RedisRefreshTokenStore.invalidate()`를 Redis pipeline 또는 Lua script로 원자적 실행으로 변경
- 관련 테스트 추가/수정

## Out of Scope

- 비밀번호 정책 전체 재설계
- Redis 클러스터 환경 최적화
- 다른 Redis 연산의 원자성 검토

---

# Acceptance Criteria

- [ ] `SignupRequest`의 password 필드에 `@Size(max = 128)` 제약조건이 있다
- [ ] 129자 이상의 비밀번호로 회원가입 시 400 VALIDATION_ERROR가 반환된다
- [ ] `RedisRefreshTokenStore.invalidate()`가 단일 원자적 연산으로 실행된다
- [ ] invalidate 실행 후 기존 refresh key가 삭제되고 revoked key가 설정된다
- [ ] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/platform/security-rules.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/auth-service/overview.md`

# Related Skills

- `.claude/skills/backend/validation.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/auth-api.md` — POST /api/auth/signup 요청 검증

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

변경 대상 레이어:
- presentation: `SignupRequest` validation 추가
- infrastructure: `RedisRefreshTokenStore.invalidate()` 원자적 연산으로 변경

---

# Implementation Notes

### 비밀번호 최대 길이

`LoginRequest`와 동일하게 `@Size(max = 128)`을 적용한다. BCrypt는 내부적으로 72바이트까지만 처리하지만, 128자 제한은 입력 단계에서 불필요한 연산을 방지한다.

### Redis 원자적 invalidate

방법 1 — Redis pipeline (executePipelined):
```java
redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    byte[] keyBytes = key(token).getBytes();
    byte[] revokedKeyBytes = revokedKey(token).getBytes();
    connection.keyCommands().del(keyBytes);
    connection.stringCommands().set(revokedKeyBytes, "1".getBytes());
    connection.keyCommands().expire(revokedKeyBytes, revokedTtlSeconds);
    return null;
});
```

방법 2 — Lua script (더 강력한 원자성 보장):
```lua
redis.call('DEL', KEYS[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[1])
```

Lua script 방식을 권장한다. pipeline은 네트워크 왕복을 줄이지만 원자성을 완전히 보장하지는 않으며, Lua script는 Redis 서버 내에서 단일 명령처럼 실행된다.

---

# Edge Cases

- 정확히 128자 비밀번호 → 정상 가입 허용
- 129자 비밀번호 → 400 VALIDATION_ERROR
- 한글 등 멀티바이트 문자가 포함된 128자 비밀번호 → 정상 가입 허용 (@Size는 문자 수 기준)
- invalidate 중 Redis 연결 끊김 → Lua script는 실행되지 않거나 전체 실행 (부분 실행 없음)
- 이미 폐기된 토큰에 대한 중복 invalidate → 멱등성 유지

---

# Failure Scenarios

- Redis 서버 다운 시 invalidate 실패 → RuntimeException 전파, 500 반환
- Lua script 미지원 Redis 버전 (2.6 미만) → 사용 불가, 하지만 Redis 7 사용 중이므로 해당 없음

---

# Test Requirements

- 단위 테스트: `SignupRequest` 128자 성공, 129자 실패 validation 테스트
- 슬라이스 테스트: 긴 비밀번호 회원가입 시 400 반환 확인
- 통합 테스트: `RedisRefreshTokenStore.invalidate()` 원자적 실행 후 상태 검증
- 단위 테스트: `RedisRefreshTokenStore.invalidate()` 호출 시 Lua script 실행 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
