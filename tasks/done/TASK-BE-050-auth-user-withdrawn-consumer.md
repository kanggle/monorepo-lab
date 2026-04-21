# Task ID

TASK-BE-050

# Title

auth-service UserWithdrawn 이벤트 소비 — 탈퇴 사용자 인증 정보 무효화

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

user-service가 발행하는 UserWithdrawn 이벤트를 auth-service에서 소비하여, 탈퇴한 사용자의 인증 정보를 완전히 무효화한다.

탈퇴 사용자의 모든 활성 세션을 종료하고, refresh token을 폐기하며, access token을 블랙리스트에 추가하여 더 이상 인증이 불가능한 상태로 만든다.

---

# Scope

## In Scope

- UserWithdrawn 이벤트 Kafka 컨슈머 구현
- 탈퇴 사용자의 모든 활성 refresh token 폐기
- 탈퇴 사용자의 access token Redis 블랙리스트 추가
- 탈퇴 사용자의 모든 세션 레코드 삭제/비활성화
- 사용자 계정 상태 비활성화 (credentials 테이블)
- DLQ 설정 (DeadLetterPublishingRecoverer)
- 멱등성 처리 (중복 이벤트 안전 처리)

## Out of Scope

- 사용자 탈퇴 API (user-service 소관)
- 탈퇴 사용자 데이터 물리 삭제 (별도 배치 작업)
- 탈퇴 취소/복구 기능

---

# Acceptance Criteria

- [ ] UserWithdrawn 이벤트 수신 시 해당 사용자의 모든 refresh token이 폐기된다
- [ ] 해당 사용자의 access token이 Redis 블랙리스트에 추가된다
- [ ] 해당 사용자의 모든 세션 레코드가 비활성화된다
- [ ] 해당 사용자의 credentials 상태가 비활성화된다
- [ ] 비활성화된 사용자로 로그인 시도 시 실패한다
- [ ] 동일 이벤트 중복 수신 시 에러 없이 처리된다 (멱등성)
- [ ] 처리 실패 시 DLQ로 라우팅된다
- [ ] 감사 로그에 계정 비활성화 이력이 기록된다

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

- `specs/contracts/events/user-events.md` — UserWithdrawn 이벤트 페이로드
- `specs/contracts/events/auth-events.md` — 참조 (기존 이벤트 패턴)

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

---

# Implementation Notes

- UserWithdrawn 페이로드: `{ userId, withdrawnAt }`
- auth-service의 기존 Kafka 컨슈머 패턴을 따를 것 (DLQ, 에러 핸들링)
- access token 블랙리스트는 기존 Redis 기반 `AccessTokenBlocklist` 활용
- refresh token 폐기는 DB에서 해당 userId의 모든 토큰 삭제
- 세션 관리는 기존 세션 테이블의 상태 업데이트
- credentials 테이블에 status 컬럼이 없다면 추가 필요 (Flyway migration)

---

# Edge Cases

- 이미 탈퇴 처리된 사용자에 대한 중복 이벤트 수신
- 해당 사용자의 활성 세션이 0개인 경우
- 해당 사용자의 refresh token이 이미 만료된 경우
- 이벤트 수신과 동시에 해당 사용자가 token refresh를 시도하는 경우

---

# Failure Scenarios

- Kafka 이벤트 역직렬화 실패 → DLQ로 라우팅
- Redis 블랙리스트 추가 실패 → 재시도 후 DLQ
- DB 업데이트 실패 → 트랜잭션 롤백, 재시도 후 DLQ
- 이벤트에 존재하지 않는 userId → 경고 로그 후 정상 완료 (멱등성)

---

# Test Requirements

- UserWithdrawn 컨슈머 단위 테스트 (정상 처리, 중복 이벤트, 존재하지 않는 사용자)
- refresh token 폐기 통합 테스트
- access token 블랙리스트 추가 통합 테스트
- 세션 비활성화 통합 테스트
- DLQ 라우팅 테스트 (역직렬화 실패 시)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
