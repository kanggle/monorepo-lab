# Task ID

TASK-BE-003

# Title

도메인 이벤트 발행 책임 이동 — 인프라 계층에서 도메인 계층으로

# Status

ready

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

---

# Goal

현재 `AccountEventPublisher`, `MembershipEventPublisher`가 인프라 계층에서 "어떤 이벤트를 발행할지"(도메인 정책)와 "어떻게 발행할지"(아웃박스 기록)를 모두 결정하고 있다.

도메인 정책(어떤 이벤트, 어떤 페이로드)을 도메인 엔티티/애그리게이트의 팩토리 메서드로 이동하고, 인프라 계층은 생성된 도메인 이벤트를 아웃박스에 저장하는 역할만 담당하도록 계층 경계를 정리한다.

---

# Scope

## In Scope

- `account-service`: `Account` 도메인 엔티티에 이벤트 팩토리 메서드 추가 (`accountLocked()`, `accountDeleted()` 등)
- `membership-service`: `Membership` 도메인 엔티티에 이벤트 팩토리 메서드 추가
- `AccountEventPublisher`, `MembershipEventPublisher`는 페이로드 결정 로직을 도메인으로 위임하도록 수정
- 기존 이벤트 타입/페이로드 구조 유지 (계약 변경 없음)

## Out of Scope

- 이벤트 계약(Kafka topic, 페이로드 스키마) 변경 없음
- admin-service, auth-service (도메인 계층이 없는 thin layered 서비스) 제외
- 아웃박스 기록 메커니즘 변경 없음

---

# Acceptance Criteria

- [ ] `Account` 엔티티에 도메인 이벤트 팩토리 메서드가 추가된다
- [ ] `Membership` 엔티티에 도메인 이벤트 팩토리 메서드가 추가된다
- [ ] `AccountEventPublisher`가 페이로드 구성을 도메인 메서드에 위임한다
- [ ] `MembershipEventPublisher`가 페이로드 구성을 도메인 메서드에 위임한다
- [ ] 발행되는 이벤트의 타입, 페이로드 구조가 변경되지 않는다
- [ ] 도메인 이벤트 팩토리 메서드에 단위 테스트가 추가된다
- [ ] 빌드 및 테스트 통과

---

# Related Specs

- `specs/services/account-service/architecture.md`
- `specs/services/membership-service/architecture.md`
- `specs/contracts/events/` (이벤트 페이로드 계약)

# Related Skills

- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claire/skills/backend/architecture/layered/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/` — 이벤트 페이로드 구조 참조용 (변경 없음)

---

# Target Service

- `account-service`
- `membership-service`

---

# Architecture

Follow:

- `specs/services/account-service/architecture.md` — Layered Architecture + 명시적 상태 기계
- `specs/services/membership-service/architecture.md`

---

# Implementation Notes

- 도메인 이벤트 팩토리 메서드는 `DomainEvent` 또는 유사 값 객체를 반환하며 인프라 타입에 의존하지 않아야 한다.
- `AccountEventPublisher`는 `Account.buildLockedEvent()` 등의 결과를 받아 직렬화 + 아웃박스 저장만 수행.
- 도메인 메서드가 인프라 타입(`OutboxWriter`, `ObjectMapper`)을 import하면 계층 위반 — 반드시 순수 도메인 값만 사용.

---

# Edge Cases

- 상태 전이가 일어나지 않은 경우 이벤트 발행 안 함 — 도메인 메서드가 `Optional<DomainEvent>` 반환 또는 상태 확인 후 발행
- 페이로드 필드가 `null`인 경우(선택적 필드) — 기존 직렬화 방식 유지

---

# Failure Scenarios

- 도메인 메서드에서 인프라 타입 import 시 빌드 경고 또는 ArchUnit 위반
- 리팩토링 후 페이로드 구조가 바뀌어 컨슈머 파싱 실패 → 계약 테스트로 방지

---

# Test Requirements

- `Account` 도메인 이벤트 팩토리 메서드 단위 테스트
- `Membership` 도메인 이벤트 팩토리 메서드 단위 테스트
- `AccountEventPublisher` 통합 테스트: 도메인 메서드 위임 후 페이로드가 동일한지 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
