# Task ID

TASK-BE-052

# Title

TASK-BE-051 리뷰 수정 — invalidate 보조 인덱스 제거 누락 및 Redis 키 spec 반영

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

TASK-BE-051 리뷰에서 발견된 2가지 이슈를 수정한다.

1. `RedisRefreshTokenStore.invalidate()` 호출 시 보조 인덱스(userId → tokenHash Set)에서 해당 tokenHash가 제거되지 않아 stale entry가 남는 문제
2. TASK-BE-051에서 추가된 새 Redis 키 패턴(`user-tokens`, `blocked-user`)이 `specs/services/auth-service/redis-keys.md`에 반영되지 않은 문제

---

# Scope

## In Scope

- `specs/services/auth-service/redis-keys.md`에 새 키 패턴 추가 (`user-tokens:{userId}`, `blocked-user:{userId}`)
- `RedisRefreshTokenStore.invalidate()` 수정 — 보조 인덱스에서 tokenHash 제거(SREM)
  - invalidate 전에 `findUserIdByToken(token)`으로 userId 조회 후 SREM 실행
  - userId 조회 실패 시(토큰 만료 등) SREM 생략 (fail-open)
- 단위 테스트: invalidate 시 보조 인덱스 제거 검증 추가

## Out of Scope

- `invalidate()` 메서드 시그니처 변경 (기존 호출자 영향 최소화)
- `invalidateAllByUserId()` 변경 (이미 Set 전체를 삭제하므로 영향 없음)
- 기존 Redis 키 패턴 변경

---

# Acceptance Criteria

- [ ] `specs/services/auth-service/redis-keys.md`에 `user-tokens:{userId}` 키 패턴이 문서화된다
- [ ] `specs/services/auth-service/redis-keys.md`에 `blocked-user:{userId}` 키 패턴이 문서화된다
- [ ] `RedisRefreshTokenStore.invalidate()` 호출 시 보조 인덱스에서 해당 tokenHash가 제거된다
- [ ] userId 조회 실패 시(토큰 이미 만료) SREM을 생략하고 정상 완료된다
- [ ] 단위 테스트: invalidate 후 보조 인덱스에서 tokenHash가 제거됨을 검증한다

---

# Related Specs

- `specs/services/auth-service/redis-keys.md`
- `specs/platform/naming-conventions.md`
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

_(없음 — 내부 Redis 키 변경만 해당)_

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

---

# Implementation Notes

### invalidate() 수정 방안

현재 `invalidate()`는 token만 받고 userId를 모른다. 메서드 시그니처를 변경하면 `LogoutService` 등 기존 호출자에 영향이 가므로, **invalidate 내부에서 userId를 조회**하는 방식으로 구현한다.

```java
@Override
public boolean invalidate(String token, long revokedTtlSeconds) {
    String tokenHash = TokenKeyHasher.sha256Hex(token);

    // 보조 인덱스 정리를 위해 userId 조회 (DEL 전에)
    String userIdStr = redisTemplate.opsForValue().get(refreshPrefix + tokenHash);

    Long deleted = redisTemplate.execute(
        INVALIDATE_SCRIPT,
        List.of(refreshPrefix + tokenHash, revokedPrefix + tokenHash),
        String.valueOf(revokedTtlSeconds)
    );

    // 보조 인덱스에서 제거 — fail-open
    if (userIdStr != null) {
        try {
            redisTemplate.opsForSet().remove(userTokensPrefix + userIdStr, tokenHash);
        } catch (Exception e) {
            log.warn("Failed to remove tokenHash from user index: userId={}", userIdStr, e);
        }
    }

    return deleted != null && deleted > 0;
}
```

### redis-keys.md 추가 항목

```markdown
| User refresh token index | `user-tokens:{userId}` | `user-tokens:550e8400-...` |
| Blocked user marker | `blocked-user:{userId}` | `blocked-user:550e8400-...` |
```

TTL 규칙:
- `user-tokens:{userId}` TTL: refresh token TTL과 동일 (save 시 갱신)
- `blocked-user:{userId}` TTL: access token TTL과 동일

---

# Edge Cases

- invalidate 시 토큰이 이미 만료되어 userId 조회 불가 → SREM 생략, 정상 완료
- 보조 인덱스 SREM 실패 → fail-open, 로그만 남김
- Set에 해당 tokenHash가 없는 경우 → SREM은 0 반환, 에러 없음

---

# Failure Scenarios

- Redis GET(userId 조회) 실패 → SREM 생략, invalidate 자체는 정상 수행
- Redis SREM 실패 → 로그 후 계속 진행

---

# Test Requirements

- `RedisRefreshTokenStoreUnitTest`: invalidate 후 SREM 호출 검증
- `RedisRefreshTokenStoreUnitTest`: userId 조회 실패 시 SREM 미호출 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
