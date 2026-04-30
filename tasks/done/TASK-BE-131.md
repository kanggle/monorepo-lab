# Task ID

TASK-BE-131

# Title

auth-service — AuthEventPublisher publishLoginSucceeded 오버로드 공통 페이로드 빌더 추출

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

`AuthEventPublisher`에 `publishLoginSucceeded` 오버로드가 3개 존재한다:

- **3-arg** (라인 61-72): `accountId, sessionJti, ctx` — 7개 공통 필드 구성
- **5-arg** (라인 85-99): `accountId, sessionJti, ctx, deviceId, isNewDevice` — 7개 공통 + 2개 추가
- **6-arg** (라인 107-122): `accountId, sessionJti, ctx, deviceId, isNewDevice, loginMethod` — 7개 공통 + 3개 추가

세 오버로드 모두 아래 7개 필드를 동일하게 반복 구성한다:

```java
payload.put("accountId", accountId);
payload.put("ipMasked", ctx.ipMasked());
payload.put("userAgentFamily", ctx.userAgentFamily());
payload.put("deviceFingerprint", ctx.deviceFingerprint());
payload.put("geoCountry", ctx.resolvedGeoCountry());
payload.put("sessionJti", sessionJti);
payload.put("timestamp", Instant.now().toString());
```

`private Map<String, Object> buildLoginSucceededBase(String accountId, String sessionJti, SessionContext ctx)` 헬퍼를 추출하고, 각 오버로드는 base map을 받아 추가 필드만 put하도록 리팩토링한다.

---

# Scope

## In Scope

- `AuthEventPublisher.java` 단일 파일 수정
- `private Map<String, Object> buildLoginSucceededBase(...)` 헬퍼 추출
- 3개 오버로드에서 공통 7줄을 헬퍼 호출로 대체

## Out of Scope

- 다른 `publish*` 메서드 변경 없음
- API 계약 / 이벤트 페이로드 형태 변경 없음 (필드 순서 포함)
- 행위(behavior) 변경 없음

---

# Acceptance Criteria

- [ ] `buildLoginSucceededBase(String accountId, String sessionJti, SessionContext ctx)` private 헬퍼가 추가된다
- [ ] 3개 오버로드 각각이 `buildLoginSucceededBase`를 호출하고 추가 필드만 put한다
- [ ] 방출되는 JSON 페이로드의 필드 목록 및 값이 리팩토링 전후 동일하다
- [ ] 빌드 및 기존 `AuthEventPublisher` 관련 테스트가 통과한다

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/contracts/events/auth-events.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`
- `.claude/skills/messaging/outbox-pattern/SKILL.md`

---

# Related Contracts

- `specs/contracts/events/auth-events.md` — `auth.login.succeeded` 이벤트 페이로드 스펙 (수정 없음, 확인용)

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`
- application 레이어 이벤트 퍼블리셔 내부 리팩토링

---

# Implementation Notes

- `buildLoginSucceededBase`는 `LinkedHashMap`을 생성하여 7개 공통 필드를 삽입 후 반환.
- 각 오버로드에서 반환된 map에 추가 필드를 `put`하고 `write("auth.login.succeeded", accountId, payload)`를 호출.
- **필드 삽입 순서**: 기존 오버로드와 동일한 순서를 유지해야 한다. `LinkedHashMap`을 사용하므로 삽입 순서가 직렬화 순서를 결정한다.
  - 공통 7필드 순서: accountId → ipMasked → userAgentFamily → deviceFingerprint → geoCountry → sessionJti → timestamp
  - 5-arg 추가: deviceId → isNewDevice (timestamp 이전 or 이후 현재 코드 확인)
  - 6-arg 추가: loginMethod (현재 코드 순서 유지)
- 3-arg 오버로드의 Javadoc("Built independently of the 5-arg form to avoid leaking null keys")은 base 헬퍼 도입으로 더 이상 정확하지 않으므로 제거하거나 업데이트.

---

# Edge Cases

- 3-arg 오버로드는 `deviceId`, `isNewDevice`, `loginMethod` 필드를 포함하지 않아야 한다 (기존 계약: field absence, not null). base 헬퍼에 이 필드를 추가하면 계약 위반 — base 헬퍼는 공통 7필드만 포함.
- `Instant.now()` 호출이 각 오버로드별로 독립적이어야 할 경우 vs 공유 timestamp: 기존 코드는 오버로드별로 호출하므로 base 헬퍼 내부에서 한 번만 호출해도 무방하지만, 미묘한 차이를 피하기 위해 base 헬퍼에서 once 호출하는 것으로 통일.

---

# Failure Scenarios

- base 헬퍼가 공통 필드 이외에 `deviceId=null` 등을 추가하면 3-arg consumer가 null key를 수신해 파싱 오류 — 기존 3-arg의 "no null keys" 계약 위반.
- 필드 순서가 바뀌면 이미 파싱 중인 consumer가 위치 기반 파싱을 쓴다면 문제 — 일반적으로 JSON은 key-based이므로 위험도 낮지만 기존 순서 유지가 원칙.

---

# Test Requirements

- 기존 `AuthEventPublisher` 단위 테스트 또는 integration 테스트가 있다면 재실행하여 통과 확인
- 없다면 3개 오버로드 각각에 대해 `SimpleMeterRegistry` 또는 Mock `OutboxWriter`를 이용한 단순 단위 테스트 추가 (각 오버로드가 올바른 필드를 생성하는지 검증)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
