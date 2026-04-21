# Task ID

TASK-BE-057-fix

# Title

TASK-BE-057 리뷰 이슈 수정 — service 태그 추가, null 방어, 로그 포맷 통일

# Status

done

# Owner

backend

# Task Tags

- code
- event
- test

---

# Goal

TASK-BE-057 리뷰에서 발견된 3가지 이슈를 수정한다.

---

# Issues

## 1. `service` 태그 누락

AC에 `service, event_type` 태그가 모두 요구되지만 현재 `event_type`만 포함되어 있다.

**수정 대상**: 5개 서비스의 Metrics 클래스 `incrementEventPublishFailure()` 메서드

**수정 내용**: `EventMetricNames`에 `TAG_SERVICE` 상수 추가, 각 서비스 Metrics에서 service 태그 포함

## 2. MeterRegistry null 방어 코드 없음

Failure Scenario에 "MeterRegistry 미등록 시 NullPointerException — 방어 코드 필요"가 명시되어 있으나 방어 코드가 없다.

**수정 대상**: 5개 서비스의 Metrics 클래스 생성자

**수정 내용**: `Objects.requireNonNull(registry)` 추가

## 3. 로그 메시지 포맷 불일치

order-service, payment-service에서 eventType이 하드코딩되어 있다.

**수정 대상**:
- `OrderPlacementService.java`
- `OrderCancellationService.java`
- `PaymentProcessingService.java`
- `PaymentRefundService.java`

**수정 내용**: 파라미터화된 로그 포맷으로 통일

---

# Acceptance Criteria

- [ ] 모든 서비스의 `event_publish_failure_total` 메트릭에 `service`, `event_type` 태그가 포함된다
- [ ] 모든 Metrics 클래스 생성자에 MeterRegistry null 방어 코드가 있다
- [ ] 모든 서비스의 이벤트 발행 실패 로그가 파라미터화된 동일 포맷을 사용한다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/testing-strategy.md`

# Related Contracts

- (TASK-BE-057과 동일)

---

# Scope

## In Scope

- 위 3가지 이슈 수정
- 관련 테스트 업데이트

## Out of Scope

- TASK-BE-057 범위 외 변경

---

# Edge Cases

- (TASK-BE-057과 동일)

# Failure Scenarios

- (TASK-BE-057과 동일)
