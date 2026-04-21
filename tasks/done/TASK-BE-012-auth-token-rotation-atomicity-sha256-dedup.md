# Task ID

TASK-BE-012

# Title

auth-service Token Rotation 원자성 보강 및 SHA-256 해시 중복 제거

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

두 가지 문제를 수정한다.

**문제 1: Token Rotation의 부분 실패 가능성**

`RefreshTokenService.refresh()`는 `@Transactional(readOnly = true)`로 설정되어 있지만 내부에서 Redis 쓰기 연산을 수행한다. `save(newToken)` 성공 후 `invalidate(oldToken)` 실패 시, 새 토큰과 기존 토큰이 동시에 유효한 상태가 될 수 있다.

**문제 2: SHA-256 해싱 코드 중복**

`RedisRefreshTokenStore`와 `RedisAccessTokenBlocklist` 두 클래스에 동일한 `sha256Hex()` private 메서드가 중복 구현되어 있다. 변경 시 양쪽을 모두 수정해야 하는 유지보수 문제가 있다.

이 태스크 완료 후: `RefreshTokenService`에 올바른 트랜잭션 설정이 적용되고, SHA-256 해싱이 단일 유틸리티로 통합된다.

---

# Scope

## In Scope

- `RefreshTokenService`의 `@Transactional(readOnly = true)`를 `@Transactional(readOnly = false)`로 변경 (또는 어노테이션 제거 후 명시적 설정)
- `infrastructure/util/TokenKeyHasher.java` (또는 유사 명칭) 생성 — SHA-256 해싱 공통화
- `RedisRefreshTokenStore`와 `RedisAccessTokenBlocklist`에서 중복 구현 제거 후 공통 유틸리티 사용
- 관련 테스트 수정/추가

## Out of Scope

- Redis Lua 스크립트를 이용한 완전한 원자적 token rotation (새 토큰 저장 + 기존 토큰 무효화를 단일 스크립트로) — 복잡도 대비 효과 검토 필요, 별도 태스크로 분리
- `LoginRateLimiter` 관련 변경
- 다른 서비스 변경

---

# Acceptance Criteria

- [x] `RefreshTokenService` 클래스 레벨 또는 `refresh()` 메서드에 `@Transactional(readOnly = false)` 또는 적절한 트랜잭션 설정이 있다
- [x] `infrastructure` 레이어 내에 SHA-256 해싱 유틸리티가 단일 위치에 존재한다
- [x] `RedisRefreshTokenStore`에 `sha256Hex()` private 메서드가 없고 공통 유틸리티를 사용한다
- [x] `RedisAccessTokenBlocklist`에 `sha256Hex()` private 메서드가 없고 공통 유틸리티를 사용한다
- [x] 기존 모든 테스트가 통과한다

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/platform/shared-library-policy.md`
- `specs/platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/transaction-handling.md`
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

변경 대상 레이어:
- application: `RefreshTokenService` 트랜잭션 설정 수정
- infrastructure: SHA-256 유틸리티 생성, `RedisRefreshTokenStore`, `RedisAccessTokenBlocklist` 수정

공통 유틸리티는 `infrastructure/util/` 패키지에 위치한다. `specs/platform/shared-library-policy.md`에 따라 서비스 내부 유틸리티이므로 `libs/`로 이동하지 않는다.

---

# Implementation Notes

### RefreshTokenService 트랜잭션 수정

`refresh()` 메서드는 DB 읽기(`userRepository.findById`)와 Redis 쓰기(`refreshTokenStore.save`, `refreshTokenStore.invalidate`)를 모두 수행한다.

- DB 트랜잭션: `readOnly = false` 또는 메서드 단위로 분리
- Redis 연산: Lua 스크립트로 atomic 처리는 이미 `invalidate()`에서 수행 중 (TASK-BE-007에서 완료)
- `save(newToken)` + `invalidate(oldToken)` 사이 실패는 Redis multi/exec 또는 순서 재설계로 완화 가능

권장: `@Transactional(readOnly = false)`로 변경 후, 실패 시 Redis 보상 트랜잭션 없이 DB 트랜잭션만 롤백됨을 문서화.

### SHA-256 유틸리티

```java
// infrastructure/util/TokenKeyHasher.java
package com.example.auth.infrastructure.util;

public final class TokenKeyHasher {
    private TokenKeyHasher() {}

    public static String sha256Hex(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                .digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
```

---

# Edge Cases

- `RefreshTokenService`가 `readOnly = false`로 변경되어도 DB 쓰기 연산이 없으므로 동작 변화 없음
- SHA-256 유틸리티로 통합 후 기존 Redis 키 형식은 동일 (SHA-256 로직 자체는 변경 없음)
- Redis `save(newToken)` 성공 후 `invalidate(oldToken)` 실패 시 → 기존 토큰이 TTL까지 유효하게 남음 (보안 이슈이나 Lua 스크립트 완전 통합은 Out of Scope)

---

# Failure Scenarios

- `@Transactional(readOnly = false)` 변경 후 기존 테스트에서 트랜잭션 관련 동작 변화 → 기존 테스트 확인 필요
- 유틸리티 클래스 패키지 위치가 `specs/services/auth-service/architecture.md`에 정의된 패키지 구조와 맞지 않을 경우 → architecture.md 확인 후 위치 결정

---

# Test Requirements

- 단위 테스트: `TokenKeyHasher.sha256Hex()`가 동일 입력에 동일 해시를 반환하는지 검증
- 기존 `RedisRefreshTokenStoreTest`와 `RedisRefreshTokenStoreUnitTest`가 리팩토링 후에도 통과하는지 확인

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
