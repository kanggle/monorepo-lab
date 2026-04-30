# Task ID

TASK-BE-036

# Title

Fix TASK-BE-023 — token-reuse cascade gaps, legacy event topic/shape mismatch, gateway X-Device-Id injection

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- test

# depends_on

- TASK-BE-023

---

# Goal

TASK-BE-023 리뷰에서 발견된 4건의 Critical을 수정한다. 수정이 없으면 token-reuse 감지 시 `device_sessions`가 logically active로 남고, legacy 이벤트는 존재하지 않는 토픽으로 발행되며, `GET /sessions/current`·`DELETE /sessions` (bulk) 엔드포인트가 항상 404를 반환한다.

---

# Scope

## In Scope

### Critical-1 — token-reuse cascade가 `device_sessions.revoked_at` 미기록
- `apps/auth-service/src/main/java/com/example/auth/application/RefreshTokenUseCase.java` `handleReuseDetected()`:
  - 현 시점: `refreshTokenRepository.revokeAllByAccountId(accountId)`만 호출
  - 수정: 영향 받은 `device_sessions` rows 모두 `revoked_at`·`revoke_reason=TOKEN_REUSE` 기록 후, **각 device 당** `publishAuthSessionRevoked(...)` 호출
- 구현 선택: `DeviceSessionRepository.findActiveByAccountId(accountId)` → 각 세션 revoke → outbox event per device

### Critical-2 — `publishSessionRevoked` legacy 토픽·페이로드 정렬
- `AuthEventPublisher.publishSessionRevoked(...)`가 `"session.revoked"` 토픽에 쓰고 있어 contract 위반 (선언된 토픽 아님)
- 현 호출처: `LogoutUseCase`, `RefreshTokenUseCase.handleReuseDetected()`
- 수정 방향(권장): 두 경로를 `publishAuthSessionRevoked(...)`로 전환하여 per-device 이벤트 발행. legacy `publishSessionRevoked` 메서드와 `"session.revoked"` 토픽 레퍼런스 삭제
- `LogoutUseCase`는 추가로 자신의 `device_session` row도 `revoke()` 처리해야 함 (S2에서 지적 — 이 태스크에서 같이 해결)

### Critical-3 — `REVOKE_REASON_TOKEN_REUSE` 문자열 enum 불일치
- `RefreshTokenUseCase.REVOKE_REASON_TOKEN_REUSE = "TOKEN_REUSE_DETECTED"` → `"TOKEN_REUSE"`로 수정 (또는 `RevokeReason.TOKEN_REUSE.name()` 사용)
- 관련 테스트·stub도 정렬

### Critical-4 — gateway-service `X-Device-Id` 헤더 미주입
- `apps/gateway-service/src/main/java/com/example/gateway/filter/JwtAuthenticationFilter.java`:
  - 현재 `X-Account-ID`만 주입
  - 수정: JWT access token에서 `device_id` claim 추출 후 `X-Device-Id` 헤더로 downstream에 주입. claim 부재 시 헤더 생략 (auth-service 컨트롤러는 이미 missing header에 대해 404 SESSION_NOT_FOUND 반환)
- 단위 테스트(`JwtAuthenticationFilterTest` 또는 동등)에 device_id claim → X-Device-Id 매핑 케이스 추가

## Out of Scope

- `RevokeAllOtherSessionsUseCase`/`EnforceConcurrentLimitUseCase` N+1 최적화 (Warning, 현재 max=10으로 bounded)
- `ListSessionsResult.total` 추후 pagination 도입 시 정리 (Warning, 잠재 버그)
- `IpMasker` IPv4-mapped IPv6 처리 (`::ffff:x.x.x.x` → IPv4 마스킹 루트) — Warning 레벨, 별도 정리 여지
- `UuidV7` 코드 주석 개선 (Suggestion)

---

# Acceptance Criteria

- [ ] token-reuse 감지 경로: `device_sessions.revoked_at` 기록 + 각 device 당 `auth.session.revoked` outbox row 발행
- [ ] `"session.revoked"` 문자열이 소스·테스트 어디에도 남아있지 않음 (`rg 'session\.revoked"' apps/` 결과는 `"auth.session.revoked"`만 매치)
- [ ] `LogoutUseCase`: 해당 `device_session.revoked_at` 기록 + `auth.session.revoked (USER_REQUESTED)` outbox 발행
- [ ] `REVOKE_REASON_TOKEN_REUSE` 상수 제거 또는 `"TOKEN_REUSE"`로 수정
- [ ] gateway `JwtAuthenticationFilter`가 `device_id` claim이 있으면 `X-Device-Id` 헤더 주입
- [ ] `./gradlew :apps:auth-service:test :apps:gateway-service:test` 모든 테스트 통과
- [ ] 통합 테스트 또는 integration-style slice 테스트로 C1·C4 재현 및 회귀 방지 확인 (가능한 범위에서)

---

# Related Specs

- `specs/services/auth-service/device-session.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/gateway-service/architecture.md`

# Related Contracts

- `specs/contracts/events/auth-events.md` (`auth.session.revoked` payload·reason enum)
- `specs/contracts/http/auth-api.md` (Access Token `device_id` claim)

---

# Target Service

- `apps/auth-service`
- `apps/gateway-service`

---

# Edge Cases

- `handleReuseDetected` 시점에 이미 revoke된 `device_sessions` rows → 멱등 skip
- `LogoutUseCase`에서 `device_id` claim이 없는 access token(이전 버전에서 발급된) → session revoke 생략하고 token만 revoke (로그 경고)
- gateway에서 JWT parsing 실패 시 기존 동작 유지 (downstream이 401로 차단)

---

# Failure Scenarios

- `device_sessions` revoke 중 일부 실패 → 트랜잭션 롤백으로 refresh token revoke도 함께 롤백 (현재 `handleReuseDetected`가 단일 트랜잭션 내에서 실행되는지 확인 필요)
- outbox 이벤트 발행 실패 → 트랜잭션 롤백, 재시도는 client retry에 위임

---

# Test Requirements

- Unit: `RefreshTokenUseCaseTest`에 reuse-detection 케이스 추가 — device_sessions 모두 revoked, per-device outbox 이벤트 기록 확인
- Unit: `LogoutUseCaseTest` — device_session revoke + outbox 이벤트
- Unit: `JwtAuthenticationFilterTest` (gateway) — device_id claim 유무에 따른 헤더 주입 검증
- 기존 테스트 regression 없음

---

# Definition of Done

- [ ] 4개 Critical 모두 수정
- [ ] 테스트 추가·통과
- [ ] Ready for review
