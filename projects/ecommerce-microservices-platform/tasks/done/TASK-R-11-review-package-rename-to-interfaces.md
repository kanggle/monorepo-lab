# Task ID

TASK-R-11

# Title

review-service 패키지명 presentation -> interfaces 통일

# Status

review

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

DDD-style 서비스 간 패키지 네이밍 일관성을 확보한다. shipping-service와 promotion-service는 interfaces 패키지를 사용하는데 review-service만 presentation 패키지를 사용하고 있다. review-service의 architecture.md에서도 "interface (presentation)"으로 권장하고 있으므로 interfaces로 통일한다.

---

# Scope

## In Scope

- review-service의 presentation 패키지를 interfaces 패키지로 리네임
- 모든 import 경로 업데이트
- 패키지 내 모든 클래스(controller, request/response DTO 등) 이동

## Out of Scope

- 다른 서비스의 패키지 구조 변경
- 클래스 내부 로직 변경
- 새로운 클래스 추가
- API 동작 변경

---

# Acceptance Criteria

- [ ] presentation 패키지가 제거되고 interfaces 패키지로 대체되었다
- [ ] 모든 import 경로가 업데이트되었다
- [ ] 컴파일 에러가 없다
- [ ] 기존 기능이 정상 동작한다
- [ ] shipping-service, promotion-service와 동일한 패키지 네이밍 규칙을 따른다

---

# Related Specs

- `specs/services/review-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`

---

# Related Contracts

- 없음 (내부 구조 변경만 해당)

---

# Target Service

- `review-service`

---

# Architecture

Follow:

- `specs/services/review-service/architecture.md`

Internal Structure Rule - Recommended internal areas:
- interface (presentation)
- application
- domain
- infrastructure

---

# Implementation Notes

- 패키지명은 interfaces (복수형)로 통일 - Java 예약어 interface와 충돌 방지
- IDE 리팩토링 기능을 사용하면 안전하게 이동 가능하나, 수동 변경 시 모든 참조 업데이트 필수
- @ComponentScan 또는 base package 설정이 있으면 함께 업데이트

---

# Edge Cases

- test 코드에서 presentation 패키지를 참조하는 경우 함께 업데이트
- configuration 파일에서 패키지 경로를 문자열로 참조하는 경우 확인

---

# Failure Scenarios

- import 경로 업데이트 누락으로 컴파일 에러 발생
- component scan 범위 변경으로 빈 등록 실패

---

# Test Requirements

- 기존 모든 테스트가 통과하는지 확인
- 통합 테스트: Spring context가 정상 로드되는지 확인 (@SpringBootTest)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
