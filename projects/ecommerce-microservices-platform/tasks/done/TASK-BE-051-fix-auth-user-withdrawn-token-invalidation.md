# Task ID

TASK-BE-051

# Title

TASK-BE-050 리뷰 수정 — 탈퇴 사용자 토큰 무효화 누락 보완

# Status

done

# Owner

backend

# Task Tags

- code
- event

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

TASK-BE-050 리뷰에서 발견된 핵심 누락 사항을 수정한다.

`UserWithdrawalService`에서 탈퇴 사용자의 refresh token 일괄 폐기와 access token 블랙리스트 추가가 빠져 있어, 탈퇴 후에도 기존 토큰으로 인증이 가능한 보안 결함이 존재한다.

---

# Scope

## In Scope

- `RefreshTokenStore` 인터페이스에 userId 기반 전체 토큰 조회 메서드 추가
- `RedisRefreshTokenStore`에 userId 기반 토큰 관리를 위한 보조 인덱스(Set) 구현
  - `save()` 시 userId → token 매핑 Set에 추가
  - 새 메서드 `findAllTokensByUserId(UUID userId)` 구현
- `AccessTokenBlocklist` 인터페이스에 userId 기반 차단 메서드 추가
- `RedisAccessTokenBlocklist`에 userId 기반 차단 구현 (userId 차단 키 추가, `isBlocked` 에서 토큰 단위 + userId 단위 모두 확인)
- `UserWithdrawalService`에 누락된 의존성 주입 및 토큰 무효화 로직 추가
  - `RefreshTokenStore` — 해당 userId의 모든 refresh token 폐기
  - `AccessTokenBlocklist` — 해당 userId 차단 등록
  - `TokenProperties` — TTL 값 참조
- 토큰 무효화 관련 단위 테스트 및 통합 테스트 추가

## Out of Scope

- 기존 consumer, 이벤트 구조, DLQ 설정 변경 (정상 동작 확인됨)
- 계정 비활성화, 세션 삭제, 감사 로그 로직 변경 (정상 동작 확인됨)

---

# Acceptance Criteria

- [ ] `RefreshTokenStore`에 `Set<String> findAllTokensByUserId(UUID userId)` 메서드가 추가된다
- [ ] `RedisRefreshTokenStore.save()` 시 userId → tokenHash Set 보조 인덱스가 함께 저장된다
- [ ] `RedisRefreshTokenStore.findAllTokensByUserId()`가 해당 userId의 모든 활성 토큰을 반환한다
- [ ] `RedisRefreshTokenStore.invalidate()` 시 보조 인덱스에서도 해당 토큰이 제거된다
- [ ] `AccessTokenBlocklist`에 `void blockByUserId(UUID userId, long ttlSeconds)` 메서드가 추가된다
- [ ] `AccessTokenBlocklist.isBlocked()`가 토큰 단위 차단과 userId 단위 차단을 모두 확인한다
- [ ] `UserWithdrawalService`가 탈퇴 처리 시 해당 사용자의 모든 refresh token을 폐기한다
- [ ] `UserWithdrawalService`가 탈퇴 처리 시 해당 사용자의 userId를 access token 블랙리스트에 등록한다
- [ ] 토큰 무효화 실패 시에도 계정 비활성화는 유지된다 (fail-open)
- [ ] 단위 테스트: refresh token 일괄 폐기, access token userId 차단, 실패 시 fail-open
- [ ] 통합 테스트: 탈퇴 후 refresh token 폐기 및 access token 차단 검증

---

# Related Specs

- `specs/platform/architecture.md`
- `specs/services/auth-service/architecture.md`
- `specs/platform/event-driven-policy.md`
- `specs/platform/security-rules.md`

# Related Skills

- `.claude/skills/backend/architecture/layered.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/events/user-events.md` — UserWithdrawn 이벤트 페이로드 (참조)

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

---

# Implementation Notes

### RefreshTokenStore 보조 인덱스 설계

현재 refresh token은 Redis에 `auth:refresh:<sha256(token)> → userId` 형태로 저장된다. userId로 토큰을 역조회할 수 없으므로, save 시 보조 인덱스를 함께 관리해야 한다.

```
auth:user-tokens:<userId> → Set { sha256(token1), sha256(token2), ... }
```

- `save()`: 토큰 저장 시 `SADD auth:user-tokens:<userId> <tokenHash>` 추가
- `findAllTokensByUserId()`: `SMEMBERS auth:user-tokens:<userId>`로 모든 tokenHash 조회, 각 tokenHash에서 원본 키 복원 후 반환
- `invalidate()`: 기존 로직 + `SREM auth:user-tokens:<userId> <tokenHash>` 추가
- Set의 TTL: refresh token의 최대 TTL과 동일하게 갱신

### AccessTokenBlocklist userId 차단 설계

access token은 JWT로 stateless이므로 개별 토큰을 추적할 수 없다. 대신 userId 단위로 차단하고, JWT 검증 시 userId 기반 차단 여부도 확인한다.

```
auth:blocked-user:<userId> → "1" (TTL: access token 최대 유효 시간)
```

- `blockByUserId()`: `SET auth:blocked-user:<userId> 1 EX <accessTokenTtl>`
- `isBlocked()`: 기존 토큰 단위 차단 + JWT에서 userId 추출 후 userId 단위 차단 확인

**주의**: `isBlocked(String token)`에서 userId 차단도 확인하려면 JWT 파싱이 필요하다. 이를 `AccessTokenBlocklist`에서 직접 하는 것이 레이어 위반이라면, JWT 검증 필터(`JwtAuthenticationFilter`)에서 별도로 `isUserBlocked(UUID userId)` 체크를 추가하는 방식도 가능하다. 기존 아키텍처 패턴에 맞는 방식을 선택할 것.

### UserWithdrawalService 수정

```java
// 기존 의존성에 추가
private final RefreshTokenStore refreshTokenStore;
private final AccessTokenBlocklist accessTokenBlocklist;
private final TokenProperties tokenProperties;

// handleUserWithdrawal() 내 계정 비활성화 후 추가:
// 2. 모든 refresh token 폐기
// 3. access token userId 차단 등록
// 4. 모든 세션 삭제 (기존)
// 5. 감사 로그 (기존)
```

토큰 무효화 실패 시 DataAccessException catch 후 로그만 남기고 계속 진행 (fail-open, 세션 삭제와 동일 패턴).

---

# Edge Cases

- 해당 userId의 활성 refresh token이 0개인 경우 — 정상 완료
- 보조 인덱스에는 있지만 실제 토큰 키가 이미 만료된 경우 — invalidate가 false 반환, 정상 처리
- access token 블랙리스트 등록 실패 — fail-open, 로그 기록
- 기존 save/invalidate 호출에 보조 인덱스 연산 추가 시 원자성 — Lua 스크립트 또는 파이프라인 사용

---

# Failure Scenarios

- Redis 보조 인덱스 SADD 실패 → save 전체 실패로 전파 (토큰 저장과 인덱스는 함께 실패해야 함)
- findAllTokensByUserId 실패 → DataAccessException catch, 로그 후 계속 진행
- refresh token 개별 invalidate 실패 → 해당 토큰만 로그 후 나머지 계속 처리
- blockByUserId 실패 → DataAccessException catch, 로그 후 계속 진행

---

# Test Requirements

- `RedisRefreshTokenStore` 단위 테스트: save 시 보조 인덱스 생성, findAllTokensByUserId 조회, invalidate 시 인덱스 제거
- `RedisAccessTokenBlocklist` 단위 테스트: blockByUserId, isUserBlocked
- `UserWithdrawalService` 단위 테스트: refresh token 폐기 호출 검증, access token 차단 호출 검증, 실패 시 fail-open
- 통합 테스트: 전체 탈퇴 플로우에서 토큰 폐기 및 차단 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
