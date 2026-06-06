# Task ID

TASK-BE-118

# Title

Fix TASK-BE-003: account.locked eventId를 UUID v7으로 교체 및 AccountEventPublisher 통합 테스트 추가

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

TASK-BE-003 리뷰에서 발견된 두 가지 문제를 수정한다.

1. **계약 위반**: `Account.buildLockedEvent()`가 `UUID.randomUUID()`(UUID v4)를 사용하지만 이벤트 계약 (`specs/contracts/events/account-events.md`)은 `"eventId": "string (UUID v7)"`을 명시한다. UUID v7은 시간 순서 기반 식별자로, security-service 멱등 키 용도에 맞게 교체해야 한다.
2. **누락 통합 테스트**: TASK-BE-003의 Test Requirements에 "AccountEventPublisher 통합 테스트: 도메인 메서드 위임 후 페이로드가 동일한지 확인"이 명시되어 있으나 실제 통합 테스트 없이 단위 테스트만 추가되었다.

---

# Scope

## In Scope

- `Account.buildLockedEvent()`의 `eventId` 생성 로직을 UUID v7으로 교체
- UUID v7 생성 유틸리티 도입 (libs/java-common 또는 account-service 내 유틸)
- `AccountEventPublisherTest`에서 UUID 버전 검증을 UUID v7 기준으로 수정
- `AccountEventPublisher` 통합 테스트 추가 (Spring context + Testcontainers, 도메인 메서드 위임 후 페이로드 일치 확인)

## Out of Scope

- 이벤트 계약 스키마 변경 없음 (필드명 유지, 타입 `string` 유지)
- membership-service 변경 없음
- `AccountAnonymizationScheduler`의 application 계층 의존 문제는 별도 아키텍처 개선 태스크 대상

---

# Acceptance Criteria

- [ ] `Account.buildLockedEvent()`가 UUID v7 형식의 `eventId`를 생성한다
- [ ] 생성된 `eventId`가 UUID v7 형식 검증(버전 비트 7)을 통과한다
- [ ] 기존 `AccountEventPublisherTest` UUID 파싱 검증이 UUID v7 기준으로 통과한다
- [ ] `AccountEventPublisher` 통합 테스트가 추가되어 도메인 메서드 위임 후 페이로드 필드가 계약과 일치함을 확인한다
- [ ] 빌드 및 테스트 통과

---

# Related Specs

- `specs/services/account-service/architecture.md`
- `specs/contracts/events/account-events.md` — `account.locked.eventId` UUID v7 요건

# Related Skills

- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/backend/testing-backend/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/account-events.md` — `account.locked` payload의 `eventId` 타입: `string (UUID v7)`

---

# Target Service

- `account-service`

---

# Architecture

Follow:

- `specs/services/account-service/architecture.md` — Layered Architecture

---

# Implementation Notes

- UUID v7 생성: Java 표준 라이브러리는 UUID v7을 미지원. `com.fasterxml.uuid:java-uuid-generator` (JUG) 또는 `io.hypersistence:hypersistence-utils`의 `UuidCreator.getTimeOrderedEpoch()`를 사용하거나, 직접 생성 유틸을 domain 계층에 추가한다.
- 도메인 계층(`Account.java`)에서 UUID v7 생성 시 외부 라이브러리 의존이 생기면 libs/java-common에 유틸 추출 또는 application 계층에서 생성 후 전달하는 방식을 검토한다.
- 통합 테스트는 `AbstractIntegrationTest`를 상속하고, 실제 `OutboxWriter` + `ObjectMapper`를 주입하여 outbox row에 기록된 payload가 계약 필드를 포함하는지 확인한다.
- UUID v7 검증: `UUID.fromString(eventId).version() == 7` 또는 동등 로직.

---

# Edge Cases

- UUID v7 라이브러리가 없는 경우 빌드 실패 → 의존성 추가 후 재검증
- 통합 테스트 내 Testcontainers Kafka/MySQL 미기동 시 → `AbstractIntegrationTest` 상속으로 처리

---

# Failure Scenarios

- UUID v7 생성 로직이 잘못 구현되어 version bit가 7이 아닌 경우 → 단위 테스트에서 version 검증으로 차단
- 통합 테스트가 잘못된 payload 필드를 검증할 경우 → 계약 파일과 교차 검증 필요

---

# Test Requirements

- `Account.buildLockedEvent()` UUID v7 버전 검증 단위 테스트 (기존 `AccountDomainEventTest` 내 수정/추가)
- `AccountEventPublisher` 통합 테스트: `publishAccountLocked` 호출 후 outbox에 저장된 payload가 계약 필드(eventId, accountId, reasonCode, actorType, lockedAt)를 모두 포함하며 eventId가 UUID v7임을 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
