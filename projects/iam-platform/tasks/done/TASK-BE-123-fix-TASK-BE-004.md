# Task ID

TASK-BE-123

# Title

Fix TASK-BE-004: admin-service 테스트 실패 수정 및 누락된 서비스별 EventPublisher 통합 테스트 추가

# Status

ready

# Owner

backend

# Task Tags

- code
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

---

# Goal

TASK-BE-004 리뷰에서 발견된 두 가지 문제를 수정한다:

1. **admin-service 테스트 실패 (Critical)**: TASK-BE-004 working tree에 admin-service의 `OperatorAdminUseCase` 분리 리팩토링이 함께 포함되어 있어 43개 테스트가 실패한다. `OperatorAdminUseCase` 분리 작업을 완성하거나(누락된 use-case 클래스 추가), 또는 해당 변경을 TASK-BE-004와 분리하여 admin-service 테스트가 통과하도록 한다.

2. **서비스별 EventPublisher 통합 테스트 누락 (Warning)**: TASK-BE-004 Acceptance Criteria에 "각 서비스 `*EventPublisher` 통합 테스트: 아웃박스에 올바른 JSON이 저장되는지"가 명시되어 있으나, `SecurityEventPublisher`, `CommunityEventPublisher`, `MembershipEventPublisher`, `AdminEventPublisher`에 대한 테스트가 없다. `AccountEventPublisher`와 `AuthEventPublisher`는 단위 테스트만 존재하며 통합 테스트가 없다.

---

# Scope

## In Scope

- admin-service 테스트 통과 복원:
  - `OperatorAdminUseCase` 분리로 인해 참조 오류가 발생하는 테스트 클래스 수정
  - `AdminPiiMaskingUtils` 삭제 → `PiiMaskingUtils` (libs/java-security) 전환 관련 누락 처리
  - `AdminOutboxPollingScheduler` 삭제 후 테스트 참조 정리
- 누락된 서비스별 EventPublisher 단위 테스트 추가:
  - `SecurityEventPublisherTest` (publishSuspiciousDetected, publishAutoLockTriggered, publishAutoLockPending 메서드)
  - `CommunityEventPublisherTest` (publishPostPublished, publishCommentCreated, publishReactionAdded 메서드)
  - `MembershipEventPublisherTest` (publishActivated, publishExpired, publishCancelled 메서드)
  - `AdminEventPublisherTest` (publishAdminActionPerformed 메서드)
- 전체 빌드 및 테스트 통과 확인 (`libs:java-messaging:test`, 6개 서비스 test)

## Out of Scope

- BaseEventPublisher 구현 변경 없음
- 이벤트 계약 변경 없음
- 이미 구현된 AccountEventPublisher, AuthEventPublisher 테스트 변경 없음 (통과 중)
- 통합 테스트(Testcontainers)는 이 fix 범위에 포함하지 않음 — 단위 테스트로 기준 충족

---

# Acceptance Criteria

- [ ] `./gradlew :libs:java-messaging:test :apps:account-service:test :apps:auth-service:test :apps:security-service:test :apps:community-service:test :apps:admin-service:test :apps:membership-service:test` 가 모두 BUILD SUCCESSFUL
- [ ] `SecurityEventPublisherTest` 추가 — 3개 이상의 테스트 메서드 포함
- [ ] `CommunityEventPublisherTest` 추가 — 3개 이상의 테스트 메서드 포함
- [ ] `MembershipEventPublisherTest` 추가 — 3개 이상의 테스트 메서드 포함
- [ ] `AdminEventPublisherTest` 추가 — 1개 이상의 테스트 메서드 포함
- [ ] admin-service에서 `OperatorAdminUseCase` 참조 컴파일 오류 없음
- [ ] 모든 신규 테스트는 Mockito 기반 단위 테스트 (`@ExtendWith(MockitoExtension.class)`)

---

# Related Specs

- `platform/shared-library-policy.md`
- `platform/testing-strategy.md`
- `specs/services/account-service/architecture.md`

# Related Skills

- `.claude/skills/backend/testing-backend/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`

---

# Related Contracts

없음 — 이벤트 계약 변경 없음

---

# Target Service

- `apps/admin-service`
- `apps/security-service`
- `apps/community-service`
- `apps/membership-service`

---

# Architecture

- `platform/shared-library-policy.md`
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- 누락된 테스트는 `BaseEventPublisherTest`와 동일한 Mockito 패턴 사용:
  `@Mock OutboxWriter`, `@Spy ObjectMapper`, `@InjectMocks *EventPublisher`
- admin-service: `OperatorAdminUseCase` 분리 작업(ChangeMyPasswordUseCase, CreateOperatorUseCase, OperatorQueryService, PatchOperatorRoleUseCase, PatchOperatorStatusUseCase)의 누락 클래스 또는 테스트 참조 정합성 확인
- `SecurityEventPublisher`, `CommunityEventPublisher`, `MembershipEventPublisher` 테스트는 outboxWriter.save() 호출 캡처 후 페이로드 JSON 검증 패턴 적용

---

# Edge Cases

- `AdminEventPublisher.publishAdminActionPerformed`에서 `targetId`가 null인 경우 aggregateId = "-" 처리 검증
- `AdminEventPublisher.displayHintFor`에서 targetType이 ACCOUNT이고 targetId가 이메일이 아닌 경우 null 반환 검증

---

# Failure Scenarios

- admin-service 컴파일 오류: `OperatorAdminUseCase` 참조가 제거된 클래스/테스트가 남아 있는 경우
- 테스트 간 공유 상태: ObjectMapper Spy가 테스트 메서드 간 오염되지 않도록 각 테스트에서 신규 인스턴스 사용

---

# Test Requirements

- `SecurityEventPublisherTest`: publishSuspiciousDetected, publishAutoLockTriggered, publishAutoLockPending 각각 정상 페이로드 검증
- `CommunityEventPublisherTest`: publishPostPublished, publishCommentCreated, publishReactionAdded 각각 정상 페이로드 검증
- `MembershipEventPublisherTest`: publishActivated, publishExpired, publishCancelled 각각 outboxWriter 호출 검증
- `AdminEventPublisherTest`: publishAdminActionPerformed 정상 케이스 + targetId null 케이스

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
