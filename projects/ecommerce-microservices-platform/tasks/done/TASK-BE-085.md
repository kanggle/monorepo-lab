# Task ID

TASK-BE-085

# Title

user-service Address 도메인 모델 리팩토링 — 반복 검증 로직 추출 및 update 메서드 분해

# Status

done

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

user-service의 `Address` 도메인 모델(202줄)에서 `update()` 메서드(56줄)와 `validateFieldLengths()`에 동일한 필드 길이 검증 패턴이 6번 반복된다. 검증 로직을 추출하여 코드 중복을 제거하고 가독성을 개선한다.

---

# Scope

## In Scope

- `Address.update()` 메서드를 더 작은 단위로 분해
- 반복되는 필드 길이 검증 로직을 private 헬퍼 메서드로 추출 (예: `validateFieldLength(String value, int maxLength, String fieldName)`)
- 생성자와 update 메서드에서 동일한 검증 헬퍼를 공유

## Out of Scope

- Address의 비즈니스 규칙 변경
- 다른 도메인 모델의 검증 리팩토링
- Value Object 추출 (별도 태스크로)

---

# Acceptance Criteria

- [ ] 필드 길이 검증이 단일 private 헬퍼 메서드로 통합되었다
- [ ] `update()` 메서드가 30줄 이하로 줄어들었다
- [ ] `Address` 클래스 전체 라인 수가 줄어들었다
- [ ] 검증 동작이 기존과 동일하다 (동일한 예외, 동일한 메시지)
- [ ] 모든 기존 테스트가 통과한다

---

# Related Specs

- `specs/services/user-service/architecture.md`
- `specs/platform/coding-rules.md`

# Related Skills

- `.claude/skills/backend/validation.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, API 계약 변경 없음)

---

# Target Service

- `user-service`

---

# Architecture

Follow:

- `specs/services/user-service/architecture.md`

---

# Implementation Notes

- 검증 헬퍼 예시: `private void validateFieldLength(String value, int maxLength, String fieldName)`
- 생성자의 초기 검증과 update의 수정 검증이 동일한 헬퍼를 호출하도록 통합
- 메서드 추출 시 기존 예외 타입과 메시지 포맷을 유지

---

# Edge Cases

- null 필드와 빈 문자열 필드의 검증 차이가 있는 경우 → 기존 동작 유지
- 선택적 필드(nullable)와 필수 필드의 검증 분기 → 헬퍼에 nullable 파라미터 추가

---

# Failure Scenarios

- 헬퍼 추출 시 기존 검증 순서가 바뀌어 다른 예외가 먼저 발생
- nullable 필드를 non-null로 검증하여 기존에 허용되던 입력이 거부됨

---

# Test Requirements

- 기존 Address 단위 테스트 전체 통과
- 기존 통합 테스트 전체 통과

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
