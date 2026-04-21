# Task ID

TASK-BE-108-fix-001

# Title

UserWithdrawn 이벤트 클래스 구현 및 컨트랙트 테스트 추가

# Status

done

# Owner

backend

# Task Tags

- code
- event
- test

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

TASK-BE-108 리뷰에서 발견된 이슈를 수정한다.

user-service에 `UserWithdrawn` 이벤트 클래스가 존재하지 않아, TASK-BE-108에서 요구한 `UserWithdrawn` 이벤트 컨트랙트 테스트가 누락되었다. `UserWithdrawn` 이벤트 발행 구현 및 해당 컨트랙트 테스트를 추가한다.

---

# Scope

## In Scope

- `UserWithdrawnEvent` 이벤트 클래스 생성 (envelope: `event_id`, `event_type`, `occurred_at`, `source`, `payload`)
- `UserWithdrawn` payload 구현: `userId` (UUID), `withdrawnAt` (ISO 8601)
- `UserProfile.withdraw()` 호출 시 `UserWithdrawnEvent` 발행
- `UserEventContractTest`에 `UserWithdrawn` envelope 및 payload 컨트랙트 테스트 추가

## Out of Scope

- 다른 서비스의 `UserWithdrawn` 소비자 로직 변경
- `UserProfileUpdated` 이벤트 관련 변경

---

# Acceptance Criteria

- [ ] `UserWithdrawnEvent` 클래스가 specs/contracts/events/user-events.md의 envelope + payload 구조를 따른다
- [ ] `UserWithdrawn` payload는 `{userId, withdrawnAt}` 필드만 포함한다
- [ ] `UserProfile.withdraw()` 호출 시 `UserWithdrawnEvent`가 발행된다
- [ ] `UserEventContractTest`에 `UserWithdrawn` envelope 검증 테스트가 추가된다
- [ ] `UserEventContractTest`에 `UserWithdrawn` payload 필드 검증 테스트가 추가된다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/user-service/architecture.md`
- `specs/platform/testing-strategy.md`

# Related Contracts

- `specs/contracts/events/user-events.md`

---

# Edge Cases

- `withdrawnAt`이 null인 경우 — 이벤트 생성 시점의 현재 시각을 사용

---

# Failure Scenarios

- 이벤트 발행 실패 시 — 기존 이벤트 발행 패턴(outbox 또는 동기 발행)을 따름

---

# Test Requirements

- `UserEventContractTest`에 `UserWithdrawn` envelope 및 payload 컨트랙트 테스트 추가

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Ready for review
