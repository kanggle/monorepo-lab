# Task ID

TASK-BE-075

# Title

LoginRateLimitFilter에서 AuthMetrics 구체 클래스 대신 AuthMetricsRecorder 인터페이스 사용

# Status

done

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

TASK-BE-073 리뷰에서 발견된 이슈 수정. LoginRateLimitFilter가 infrastructure의 AuthMetrics 구체 클래스를 직접 주입받고 있으나, 실제로 호출하는 메서드(`incrementLoginFailure`)는 AuthMetricsRecorder 인터페이스에 정의된 메서드이므로 인터페이스를 사용하도록 변경하여 일관성을 확보한다.

---

# Scope

## In Scope

- LoginRateLimitFilter의 AuthMetrics → AuthMetricsRecorder 타입 변경
- 관련 import 변경

## Out of Scope

- SpringAuthEventPublisher는 인터페이스에 없는 메서드(`incrementEventPublishFailure`)를 호출하므로 변경 대상 아님
- AuthMetricsRecorder 인터페이스 변경

---

# Acceptance Criteria

- [x] LoginRateLimitFilter가 AuthMetricsRecorder 인터페이스를 import한다
- [x] AuthMetrics 구체 클래스에 대한 직접 의존이 제거된다
- [x] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/services/auth-service/architecture.md`

---

# Related Contracts

_(없음)_

---

# Target Service

- auth-service

---

# Edge Cases

- Spring Bean 주입 시 AuthMetricsRecorder 타입으로 정상 resolve 되는지 확인

---

# Failure Scenarios

- Bean 주입 실패로 필터 등록 불가

---

# Test Requirements

- 기존 단위 테스트 통과 확인

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed
- [x] Specs updated first if required
- [x] Ready for review
