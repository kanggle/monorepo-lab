# Task ID

TASK-BE-009

# Title

Refresh token rotation 완성 + 재사용 탐지 — 토큰 체인 검증, bulk session invalidation

# Status

backlog

# Owner

backend

# Task Tags

- code
- api
- event

# depends_on

- TASK-BE-005
- TASK-BE-008

---

# Goal

auth-service의 refresh token rotation에 **재사용 탐지(reuse detection)** 를 추가한다. 이미 rotation된 refresh token이 다시 제출되면 해당 계정의 전체 세션을 즉시 무효화하고 `auth.token.reuse.detected` 이벤트를 발행한다.

---

# Scope

## In Scope

- `TokenReuseDetector` 도메인 서비스 구현 (rotated_from 체인 탐색)
- `RefreshTokenUseCase` 확장: rotation 시 재사용 검사 추가
- 재사용 탐지 시: 해당 account_id의 모든 refresh_tokens `revoked=TRUE` 일괄 처리
- `refresh:invalidate-all:{account_id}` Redis 키 설정 (TTL = max refresh lifetime)
- `auth.token.reuse.detected` 이벤트 발행 (outbox)
- `session.revoked` 이벤트 발행 (revokeReason=TOKEN_REUSE_DETECTED)
- security-service가 이벤트를 소비하여 자동 잠금 트리거 (기존 consumer 확장)

## Out of Scope

- security-service의 탐지 규칙 추가 (TASK-BE-011에서 TokenReuseRule)
- admin 대시보드 UI

---

# Acceptance Criteria

- [ ] 정상 rotation 성공 (기존 동작 유지)
- [ ] 이미 rotation된 token 재사용 시 → 401 `TOKEN_REUSE_DETECTED`
- [ ] 재사용 탐지 시 해당 계정의 모든 refresh_tokens `revoked=TRUE`
- [ ] `refresh:invalidate-all:{account_id}` Redis 키 설정됨
- [ ] `auth.token.reuse.detected` 이벤트 Kafka에 발행됨
- [ ] `session.revoked` 이벤트 발행됨 (revokeReason=TOKEN_REUSE_DETECTED)
- [ ] security-service가 이벤트 소비 → login_history에 outcome=TOKEN_REUSE 기록

---

# Related Specs

- `specs/features/authentication.md` — 토큰 재사용 탐지 섹션
- `specs/features/session-management.md` — Invalidation Scenarios
- `specs/use-cases/refresh-token-rotation.md` (UC-3, UC-4)
- `specs/services/auth-service/data-model.md` — rotated_from 체인

# Related Contracts

- `specs/contracts/http/auth-api.md` — POST /api/auth/refresh
- `specs/contracts/events/auth-events.md` — auth.token.reuse.detected
- `specs/contracts/events/session-events.md` — session.revoked

---

# Target Service

- `apps/auth-service` (primary)
- `apps/security-service` (consumer 확장)

---

# Edge Cases

- rotation과 재사용 감지 사이의 race condition → DB 낙관적 락으로 방어
- 이미 revoke된 계정에 대해 다시 재사용 탐지 → 멱등 (이미 revoked, 중복 이벤트 발행하지 않음)
- refresh:invalidate-all 키가 이미 존재 → TTL 갱신

---

# Failure Scenarios

- Redis 장애 시 invalidate-all 키 설정 실패 → DB revoked=TRUE가 방어선 (fallback)
- Kafka 장애 시 이벤트 발행 지연 → outbox에 유지, 보안 대응 지연은 불가피

---

# Test Requirements

- Unit: `TokenReuseDetector` — 정상 체인, 재사용 체인, 이미 revoked 체인
- Integration: 로그인 → refresh → 기존 token 재사용 → 401 + bulk revoke + 이벤트 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added and passing
- [ ] Contracts match
- [ ] Ready for review
