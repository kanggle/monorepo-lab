# Task ID

TASK-BE-004

# Title

BaseEventPublisher 추출 — libs/java-messaging 공통 추상 클래스

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

6개 서비스(account, admin, auth, community, membership, security)의 `*EventPublisher`가 모두 동일한 구조를 복제하고 있다:
- `OutboxWriter` 주입
- `ObjectMapper` 주입
- JSON 직렬화 + 에러 핸들링
- 이벤트 엔벨로프 구성 (eventId, eventType, source, occurredAt, schemaVersion)

`libs/java-messaging`에 `BaseEventPublisher` 추상 클래스를 추가하고, 각 서비스의 이벤트 퍼블리셔가 이를 상속하도록 한다.

---

# Scope

## In Scope

- `libs/java-messaging`에 `BaseEventPublisher` 추상 클래스 추가
  - `writeEvent(String topic, Object payload)` 템플릿 메서드 구현
  - JSON 직렬화 공통 로직
  - 이벤트 엔벨로프 구성 공통 로직
- account, admin, auth, community, membership, security `*EventPublisher` 수정 — `BaseEventPublisher` 상속
- 중복 코드 제거 (OutboxWriter 직접 호출 코드 → 베이스 메서드 위임)

## Out of Scope

- 이벤트 계약(topic 이름, 페이로드 스키마) 변경 없음
- 아웃박스 테이블 스키마 변경 없음
- TASK-BE-003과 중복 작업 없음 (도메인 이벤트 팩토리 메서드는 TASK-BE-003 담당)

---

# Acceptance Criteria

- [ ] `libs/java-messaging`에 `BaseEventPublisher`가 추가된다
- [ ] 6개 서비스의 `*EventPublisher`가 `BaseEventPublisher`를 상속한다
- [ ] 각 퍼블리셔에서 직렬화/엔벨로프 중복 코드가 제거된다
- [ ] `platform/shared-library-policy.md`의 4가지 Decision Rule을 모두 충족한다
- [ ] `libs/java-messaging`이 서비스 모듈에 의존하지 않는다 (단방향 의존)
- [ ] `BaseEventPublisher` 단위 테스트 추가
- [ ] 빌드 및 테스트 통과

---

# Related Specs

- `platform/shared-library-policy.md`
- `specs/services/account-service/architecture.md`
- `specs/services/membership-service/architecture.md`

# Related Skills

- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/messaging/event-implementation/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 이벤트 계약 변경 없음

---

# Target Service

- `libs/java-messaging`
- `account-service`, `admin-service`, `auth-service`, `community-service`, `membership-service`, `security-service`

---

# Architecture

Follow:

- `platform/shared-library-policy.md` — libs에 허용되는 기술 공통 유틸리티
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- `BaseEventPublisher`는 `OutboxWriter`와 `ObjectMapper`를 생성자 주입으로 받는다.
- 서비스별 퍼블리셔는 `super(outboxWriter, objectMapper)` 호출.
- 직렬화 실패 시 `EventPublishException` (기존 예외 타입 유지 또는 공통 예외로 통합).
- `libs/java-messaging`은 도메인 타입에 의존하지 않아야 한다 — 메서드 파라미터는 `Object` 또는 `String`.

---

# Edge Cases

- 직렬화 실패 시 아웃박스 미기록 + 예외 전파 (기존 동작 유지)
- `schemaVersion`이 서비스마다 다른 경우 — 추상 메서드 또는 파라미터로 수신

---

# Failure Scenarios

- `libs/java-messaging`이 서비스 도메인 클래스를 import하면 순환 의존 → 빌드 실패로 조기 감지
- 베이스 클래스 변경이 6개 서비스에 동시 영향 → 단위 테스트 커버리지로 완화

---

# Test Requirements

- `BaseEventPublisher` 단위 테스트 (직렬화 정상/실패, 엔벨로프 구성 검증)
- 각 서비스 `*EventPublisher` 통합 테스트: 아웃박스에 올바른 JSON이 저장되는지

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
