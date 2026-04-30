# Task ID

TASK-BE-002

# Title

OutboxPollingScheduler topic 매핑 설정화 — 서비스별 서브클래스 제거

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

account-service, admin-service, auth-service, membership-service 각각의 `OutboxPollingScheduler` 서브클래스가 `resolveTopic()` switch 문에서 이벤트 타입 → Kafka topic 매핑만 다르고 나머지 70%가 동일하다.

topic 매핑을 `application.yml`의 `outbox.topic-mapping` 프로퍼티로 이동하고 공통 베이스 스케줄러가 설정을 읽도록 변경하여, 서비스별 서브클래스를 제거한다.

---

# Scope

## In Scope

- account-service, admin-service, auth-service, membership-service의 `OutboxPollingScheduler` 서브클래스 제거
- 각 서비스 `application.yml`에 `outbox.topic-mapping` 섹션 추가
- 베이스 스케줄러(`libs/java-messaging` 또는 각 서비스 내 공통 클래스)가 `Map<String, String>` 설정을 주입받아 topic 결정

## Out of Scope

- Kafka topic 이름 자체 변경 없음
- 아웃박스 폴링 주기/배치 크기 변경 없음
- community-service, security-service (아웃박스 미사용이면 제외)

---

# Acceptance Criteria

- [ ] 4개 서비스의 `OutboxPollingScheduler` 서브클래스가 삭제된다
- [ ] 각 서비스 `application.yml`에 `outbox.topic-mapping` 설정이 추가된다
- [ ] 베이스 스케줄러가 설정 기반으로 topic을 결정한다
- [ ] 기존 Kafka topic 이름이 변경되지 않는다
- [ ] 빌드 및 테스트 통과

---

# Related Specs

- `specs/services/account-service/architecture.md`
- `specs/services/admin-service/architecture.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/membership-service/architecture.md`
- `platform/shared-library-policy.md`

# Related Skills

- `.claude/skills/messaging/outbox-pattern/SKILL.md`
- `.claude/skills/backend/scheduled-tasks/SKILL.md`
- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — Kafka topic 이름 변경 없음

---

# Target Service

- `account-service`
- `admin-service`
- `auth-service`
- `membership-service`

---

# Architecture

Follow:

- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- `application.yml` 구조 예시:
  ```yaml
  outbox:
    topic-mapping:
      ACCOUNT_CREATED: account.account.created.v1
      ACCOUNT_STATUS_CHANGED: account.status.changed.v1
  ```
- 베이스 스케줄러에 `@ConfigurationProperties(prefix = "outbox")` 또는 생성자 주입으로 `Map<String, String> topicMapping` 수신.
- 매핑에 없는 이벤트 타입은 `IllegalStateException` 발생 (기존 동작 유지).
- 설정 파일 변경만으로 topic 추가/변경 가능해야 한다.

---

# Edge Cases

- `topic-mapping`에 없는 이벤트 타입이 아웃박스에 삽입된 경우 → `IllegalStateException` + 알람
- 빈 `topic-mapping` 설정 → 스타트업 시 검증 실패 (`@Validated`)
- 동일 이벤트 타입이 두 서비스에서 다른 topic으로 매핑 → 정상 (서비스별 독립 설정)

---

# Failure Scenarios

- `application.yml` 누락 시 스케줄러 빈 생성 실패 → 스타트업 에러로 조기 감지
- 오타로 잘못된 topic 이름 → 메시지 발행 실패, DLQ 유입

---

# Test Requirements

- 베이스 스케줄러 단위 테스트: topic 매핑 정상/누락 케이스
- 각 서비스 통합 테스트: `application.yml` 설정이 실제 스케줄러에 주입되는지

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
