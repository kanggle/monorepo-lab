# Task ID

TASK-BE-015

# Title

Fix TASK-BE-009 — revokedJtis 누락, invalidate-all 플래그 미검사, actorType 대소문자 불일치

# Status

ready

# Owner

backend

# Task Tags

- code
- event

# depends_on

- TASK-BE-009

---

# Goal

TASK-BE-009 리뷰에서 발견된 3개의 Critical 이슈를 수정한다:
1. `session.revoked` 이벤트의 `revokedJtis` 필드가 bulk revoke된 모든 jti를 포함하지 않음 (재사용된 jti 하나만 포함)
2. `refresh:invalidate-all:{accountId}` Redis 플래그가 `RefreshTokenUseCase.execute()`에서 검사되지 않음 (UC-3 step 3 미이행)
3. `actorType` 값이 `"SYSTEM"` (대문자) — 컨트랙트는 `"system"` (소문자) 요구

---

# Scope

## In Scope

1. **revokedJtis 전체 포함**:
   - `RefreshTokenRepository`에 `List<String> findActiveJtisByAccountId(String accountId)` 포트 메서드 추가
   - JPA 구현체에서 `SELECT jti FROM refresh_tokens WHERE account_id = ? AND revoked = FALSE` 구현
   - `RefreshTokenUseCase` 재사용 탐지 분기에서 `revokeAllByAccountId` **전에** 호출 → 결과를 `publishSessionRevoked`의 `revokedJtis`로 전달

2. **invalidate-all 플래그 검사**:
   - `RefreshTokenUseCase.execute()`에 블랙리스트 체크 다음 단계로 추가
   - `BulkInvalidationStore.isInvalidated(accountId)` + token의 `issued_at` 비교 (token iat < 플래그 timestamp → `SessionRevokedException`)
   - `RedisBulkInvalidationStore`에 `Optional<Instant> getInvalidatedAt(accountId)` 메서드 추가 (현재는 `hasKey()`만 있음)

3. **actorType 소문자로 변경**:
   - `RefreshTokenUseCase.ACTOR_TYPE_SYSTEM = "SYSTEM"` → `"system"`
   - 일관성을 위해 다른 actor type 상수도 검토

4. **테스트 업데이트**:
   - `RefreshTokenUseCaseTest.refreshFailsReuseDetected` — `verify(authEventPublisher).publishSessionRevoked(anyString(), listCaptor.capture(), ...)` + `listCaptor` 내용 검증
   - invalidate-all 플래그 검사 새 테스트 추가 (플래그 존재 + iat 이전 → 401)

## Out of Scope

- TokenReuseDetector 알고리즘 변경
- JWT UUID v7 마이그레이션 (TASK-BE-015 별도 백로그)

---

# Acceptance Criteria

- [ ] `RefreshTokenRepository.findActiveJtisByAccountId` 구현 및 테스트
- [ ] `session.revoked` 이벤트의 `revokedJtis`가 bulk revoke된 모든 jti 포함
- [ ] `refresh:invalidate-all:{accountId}` 플래그 존재 + token iat < 플래그 timestamp → 401 `SESSION_REVOKED`
- [ ] `actorType = "system"` (소문자)
- [ ] 기존 테스트 모두 통과 + 새 테스트 통과
- [ ] `./gradlew :apps:auth-service:test` 통과

---

# Related Specs

- `specs/features/authentication.md`
- `specs/features/session-management.md`
- `specs/use-cases/refresh-token-rotation.md` (UC-3 step 3)
- `specs/contracts/events/session-events.md`

# Related Contracts

- `specs/contracts/events/session-events.md`
- `specs/contracts/events/auth-events.md`

---

# Target Service

- `apps/auth-service`

---

# Architecture

`specs/services/auth-service/architecture.md` — Layered.

---

# Edge Cases

- `findActiveJtisByAccountId` 결과가 빈 리스트 → 이벤트 발행 스킵 (이미 모두 revoked)
- BulkInvalidationStore가 밀리초 단위 timestamp 저장 → Instant 변환 시 정밀도 손실 방지

---

# Failure Scenarios

- Redis 장애 시 `getInvalidatedAt` 예외 → fail-closed (거부) — `SessionRevokedException` 처리

---

# Test Requirements

- Unit: RefreshTokenUseCaseTest — 재사용 탐지 시 revokedJtis 내용 검증, invalidate-all 플래그 검사 테스트
- Integration: AuthIntegrationTest — 재사용 탐지 후 다른 토큰 사용 시도 → 401 `SESSION_REVOKED`

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Contract fields match session-events.md exactly
- [ ] Ready for review
