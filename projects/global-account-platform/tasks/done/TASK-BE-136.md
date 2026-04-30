# Task ID

TASK-BE-136

# Title

membership-service — SubscriptionStatusHistoryRecorder 추출 (Activate/Cancel/Expire UseCase 히스토리 기록 중복 제거)

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

`ActivateSubscriptionUseCase`, `CancelSubscriptionUseCase`, `ExpireSubscriptionUseCase` 세 곳에서 `SubscriptionStatusHistoryEntry` 생성 + `historyRepository.append(...)` 호출이 동일한 4-7줄 패턴으로 반복된다:

```java
historyRepository.append(new SubscriptionStatusHistoryEntry(
        s.getId(), s.getAccountId(),
        from, SubscriptionStatus.CANCELLED,   // varies
        "USER_CANCEL", "USER",                // varies
        now));
```

membership-service `application` 패키지에 `SubscriptionStatusHistoryRecorder @Component`(package-private)를 추가해 세 UseCase의 다중 라인 생성자 호출을 단일 메서드 호출로 대체한다. BE-133 `PostAccessGuard` 및 BE-135 `PostMediaUrlsSerializer`와 동일한 패턴.

---

# Scope

## In Scope

- 신규 파일: `apps/membership-service/src/main/java/com/example/membership/application/SubscriptionStatusHistoryRecorder.java`
  - `@Component` (package-private)
  - 의존성: `SubscriptionStatusHistoryRepository`만 주입
  - public method: `void recordTransition(Subscription subscription, SubscriptionStatus from, SubscriptionStatus to, String operationCode, String actorType, LocalDateTime occurredAt)`
- `ActivateSubscriptionUseCase` 수정:
  - `SubscriptionStatusHistoryRepository` 직접 의존성 제거 → `SubscriptionStatusHistoryRecorder`로 교체
  - `historyRepository.append(new SubscriptionStatusHistoryEntry(...))` (7줄) → `historyRecorder.recordTransition(saved, SubscriptionStatus.NONE, SubscriptionStatus.ACTIVE, "USER_SUBSCRIBE", "USER", now)` (1줄)
  - import 정리 (`SubscriptionStatusHistoryEntry`, `SubscriptionStatusHistoryRepository` 제거)
- `CancelSubscriptionUseCase` 수정:
  - 동일한 의존성 교체 + 1줄 치환
- `ExpireSubscriptionUseCase` 수정:
  - 동일한 의존성 교체 + 1줄 치환
- 신규 테스트: `apps/membership-service/src/test/java/com/example/membership/application/SubscriptionStatusHistoryRecorderTest.java`
  - 단위 테스트 (mock `SubscriptionStatusHistoryRepository` 사용)
  - Korean `@DisplayName` 필수

## Out of Scope

- `SubscriptionStatusHistoryEntry` 도메인 객체 변경 없음
- `SubscriptionStatusHistoryRepository` 인터페이스 변경 없음
- `Subscription` 도메인 변경 없음 (`activate`/`cancel`/`expire` 메서드, `statusMachine` 모두 유지)
- 이벤트 발행 변경 없음 (`eventPublisher.publishActivated/Cancelled/Expired`은 각 UseCase에 그대로 유지)
- `subscriptionRepository.save(s)` 호출은 각 UseCase에 유지 (히스토리 기록과 분리)
- `from = s.getStatus()` 캡처도 각 UseCase에 유지 (도메인 메서드 호출 전에 읽어야 함 — 인라인이 더 명확)
- API 계약 / 이벤트 계약 / 행위 변경 없음

---

# Acceptance Criteria

- [ ] `SubscriptionStatusHistoryRecorder.java`가 membership-service `application` 패키지에 추가된다 (package-private `class`)
- [ ] `recordTransition(...)` 메서드가 정확히 6개 파라미터 (`subscription`, `from`, `to`, `operationCode`, `actorType`, `occurredAt`)를 받는다
- [ ] `recordTransition` 내부에서 `new SubscriptionStatusHistoryEntry(subscription.getId(), subscription.getAccountId(), from, to, operationCode, actorType, occurredAt)`를 만들어 `historyRepository.append(...)` 호출
- [ ] 세 UseCase에서 직접 `SubscriptionStatusHistoryRepository` 의존성이 제거되고 `SubscriptionStatusHistoryRecorder`로 교체된다
- [ ] 세 UseCase에서 `SubscriptionStatusHistoryEntry`의 직접 인스턴스화가 모두 사라진다
- [ ] 각 UseCase의 도메인 메서드 호출(`activate`/`cancel`/`expire`), `subscriptionRepository.save(s)`, `eventPublisher.publishXxx(s)` 호출은 그대로 유지된다
- [ ] `SubscriptionStatusHistoryRecorderTest` 단위 테스트 통과
- [ ] 기존 `ActivateSubscriptionUseCaseTest`, `CancelSubscriptionUseCaseTest`, `ExpireSubscriptionUseCaseTest`가 모두 통과한다
- [ ] `./gradlew :apps:membership-service:test` 통과

---

# Related Specs

- `specs/services/membership-service/architecture.md`
- `specs/services/membership-service/overview.md`
- `platform/testing-strategy.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 행위 변경 없음.

---

# Target Service

- `membership-service`

---

# Architecture

Follow:

- `specs/services/membership-service/architecture.md`
- Layered Architecture: `application` 레이어 내부 컴포넌트 추출 (BE-133 `PostAccessGuard`, BE-135 `PostMediaUrlsSerializer`와 동일한 구조)

---

# Implementation Notes

## SubscriptionStatusHistoryRecorder 구현

```java
package com.example.membership.application;

import com.example.membership.domain.subscription.Subscription;
import com.example.membership.domain.subscription.SubscriptionStatusHistoryEntry;
import com.example.membership.domain.subscription.SubscriptionStatusHistoryRepository;
import com.example.membership.domain.subscription.status.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class SubscriptionStatusHistoryRecorder {

    private final SubscriptionStatusHistoryRepository historyRepository;

    void recordTransition(Subscription subscription,
                          SubscriptionStatus from,
                          SubscriptionStatus to,
                          String operationCode,
                          String actorType,
                          LocalDateTime occurredAt) {
        historyRepository.append(new SubscriptionStatusHistoryEntry(
                subscription.getId(),
                subscription.getAccountId(),
                from, to,
                operationCode, actorType,
                occurredAt));
    }
}
```

- package-private — membership-service `application` 패키지 내부에서만 사용.

## 호출 변환

### ActivateSubscriptionUseCase (line 94-101)

Before:
```java
historyRepository.append(new SubscriptionStatusHistoryEntry(
        saved.getId(),
        saved.getAccountId(),
        SubscriptionStatus.NONE,
        SubscriptionStatus.ACTIVE,
        "USER_SUBSCRIBE",
        "USER",
        now));
```

After:
```java
historyRecorder.recordTransition(saved,
        SubscriptionStatus.NONE, SubscriptionStatus.ACTIVE,
        "USER_SUBSCRIBE", "USER", now);
```

### CancelSubscriptionUseCase (line 47-50)

Before:
```java
historyRepository.append(new SubscriptionStatusHistoryEntry(
        s.getId(), s.getAccountId(),
        from, SubscriptionStatus.CANCELLED,
        "USER_CANCEL", "USER", now));
```

After:
```java
historyRecorder.recordTransition(s,
        from, SubscriptionStatus.CANCELLED,
        "USER_CANCEL", "USER", now);
```

### ExpireSubscriptionUseCase (line 49-52)

Before:
```java
historyRepository.append(new SubscriptionStatusHistoryEntry(
        s.getId(), s.getAccountId(),
        from, SubscriptionStatus.EXPIRED,
        "SCHEDULED_EXPIRE", "SYSTEM", now));
```

After:
```java
historyRecorder.recordTransition(s,
        from, SubscriptionStatus.EXPIRED,
        "SCHEDULED_EXPIRE", "SYSTEM", now);
```

## 테스트 명명 규칙

`platform/testing-strategy.md`에 따라:
- Korean `@DisplayName` 필수
- 3-part 메서드 이름

예시:
```java
@Test
@DisplayName("recordTransition 호출 시 historyRepository.append에 정확한 엔트리가 전달된다")
void recordTransition_validInput_appendsEntry() { ... }

@Test
@DisplayName("recordTransition은 subscription의 id와 accountId를 엔트리에 복사한다")
void recordTransition_validSubscription_copiesIdAndAccountId() { ... }
```

`historyRepository.append`가 실패하면 예외가 그대로 전파됨 — 별도 처리 없음 (기존 동작과 동일).

---

# Edge Cases

- `from == to`: 도메인 메서드(`activate`/`cancel`/`expire`)가 이미 transition 검증을 하므로 여기까지 도달하지 않음 — 헬퍼는 그대로 기록 (기존 동작과 동일).
- `subscription`이 `null`: `subscription.getId()`에서 NPE — 기존 코드도 동일하게 NPE.
- `occurredAt`이 `null`: `SubscriptionStatusHistoryEntry` 생성자가 허용 여부에 따라 동작 — 기존과 동일.

---

# Failure Scenarios

- `historyRepository.append` 실패 시 예외 전파 — caller `@Transactional` 경계에서 롤백 (기존 동작과 동일).
- `Subscription` getter NPE: 기존 코드와 동일.

---

# Test Requirements

- `SubscriptionStatusHistoryRecorderTest` 단위 테스트 (Mockito로 `SubscriptionStatusHistoryRepository` mock):
  - 정상 호출 시 `append`가 정확한 entry로 1회 호출됨 검증
  - entry의 모든 필드가 파라미터와 일치함 검증 (id, accountId, from, to, operationCode, actorType, occurredAt)
- 기존 `ActivateSubscriptionUseCaseTest`, `CancelSubscriptionUseCaseTest`, `ExpireSubscriptionUseCaseTest` 전체 회귀 통과 확인
- Korean `@DisplayName`, 3-part 메서드 이름 준수

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing (SubscriptionStatusHistoryRecorderTest 신규 + 기존 UseCaseTest 회귀 통과)
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
