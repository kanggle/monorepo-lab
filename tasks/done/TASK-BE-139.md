# Task ID

TASK-BE-139

# Title

security-service — SecurityEventPublisher SuspiciousEvent 공통 페이로드 빌더 추출

# Status

ready

# Owner

backend

# Task Tags

- refactor

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

`SecurityEventPublisher`의 3개 publish 메서드는 모두 동일한 4개의 `SuspiciousEvent` 기반 공통 필드를 동일한 순서로 구성한다:

```java
payload.put("suspiciousEventId", event.getId());
payload.put("accountId", event.getAccountId());
payload.put("ruleCode", event.getRuleCode());
payload.put("riskScore", event.getRiskScore());
```

대상 메서드:

- `publishSuspiciousDetected(SuspiciousEvent event)` — 공통 4필드 + `actionTaken, evidence, triggerEventId, detectedAt`
- `publishAutoLockTriggered(SuspiciousEvent event, AccountLockClient.Status status)` — 공통 4필드 + `lockRequestResult, lockRequestedAt`
- `publishAutoLockPending(SuspiciousEvent event)` — 공통 4필드 + `reason, raisedAt`

`private Map<String, Object> buildSuspiciousEventBase(SuspiciousEvent event)` 헬퍼를 추출하고, 각 메서드는 base map을 받아 추가 필드만 put하도록 리팩토링한다. BE-131의 `buildLoginSucceededBase` 패턴을 그대로 따른다.

---

# Scope

## In Scope

- `apps/security-service/src/main/java/com/example/security/application/event/SecurityEventPublisher.java` 단일 파일 수정
- `private Map<String, Object> buildSuspiciousEventBase(SuspiciousEvent event)` 헬퍼 추출
- 3개 publish 메서드에서 공통 4줄을 헬퍼 호출로 대체

## Out of Scope

- 다른 publish/유틸 메서드 변경 없음
- API 계약 / 이벤트 페이로드 형태 변경 없음 (필드 순서 포함)
- 행위(behavior) 변경 없음

---

# Acceptance Criteria

- [ ] `buildSuspiciousEventBase(SuspiciousEvent event)` private 헬퍼가 추가된다
- [ ] 3개 publish 메서드 각각이 `buildSuspiciousEventBase(event)`를 호출하고 메서드별 추가 필드만 put한다
- [ ] 방출되는 JSON 페이로드의 필드 목록 및 값과 순서가 리팩토링 전후 동일하다
- [ ] `:apps:security-service:test` 가 통과한다 (`SecurityEventPublisherTest` 포함)

---

# Related Specs

- `specs/services/security-service/architecture.md`
- `specs/contracts/events/security-events.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/security-events.md` — `security.suspicious.detected`, `security.auto.lock.triggered`, `security.auto.lock.pending` 페이로드 스펙 (수정 없음, 확인용)

---

# Target Service

- `security-service`

---

# Architecture

Follow:

- `specs/services/security-service/architecture.md`
- application 레이어 이벤트 퍼블리셔 내부 리팩토링

---

# Implementation Notes

- `buildSuspiciousEventBase`는 `LinkedHashMap`을 생성하여 4개 공통 필드를 삽입 후 반환:
  - `suspiciousEventId → accountId → ruleCode → riskScore`
- 각 메서드에서 반환된 map에 메서드별 추가 필드를 `put`하고 `writeEnvelope(...)`을 호출.
- **필드 삽입 순서**: 기존 메서드와 동일한 순서를 유지해야 한다. `LinkedHashMap`을 사용하므로 삽입 순서가 직렬화 순서를 결정한다.
  - `publishSuspiciousDetected`: 공통 4필드 → actionTaken → evidence → triggerEventId → detectedAt
  - `publishAutoLockTriggered`: 공통 4필드 → lockRequestResult → lockRequestedAt
  - `publishAutoLockPending`: 공통 4필드 → reason → raisedAt
- BE-131 `AuthEventPublisher#buildLoginSucceededBase` 패턴을 그대로 따른다.

---

# Edge Cases

- `event` 파라미터는 non-null로 가정 (호출 경로 모두 도메인 객체 보유 시점에 호출). null 방어는 도입하지 않음 — 동일한 NPE 동작 유지.
- 공통 4필드만 base에 포함. 메서드별 필드를 base에 섞으면 다른 메서드에서 누락/중복 발생 → 페이로드 계약 위반.

---

# Failure Scenarios

- base 헬퍼가 메서드별 필드를 추가하면 페이로드에 누락된 필드 또는 잘못된 키가 등장 → consumer 파싱 오류.
- 필드 순서가 바뀌면 byte-identical envelope 보장 깨짐 — 기존 순서 엄수.

---

# Test Requirements

- 기존 `SecurityEventPublisherTest`가 통과해야 한다 (필드 존재·값 검증).
- 추가 테스트 작성은 불필요 (행위 변경 없음).

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
