# Task ID

TASK-R-20

# Title

AccessDeniedException 중복 제거 및 도메인 예외 통일

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

shipping-service, promotion-service, user-service에 동일한 AccessDeniedException이 중복 존재한다. shared-library-policy.md의 기준에 따라 적절한 방식으로 중복을 제거한다. 각 서비스의 도메인 예외로 통일하거나, 공통 기술 예외로 libs에 추출할 수 있다.

---

# Scope

## In Scope

- shipping-service, promotion-service, user-service의 AccessDeniedException 중복 분석
- shared-library-policy.md 기준에 따른 통일 방안 결정 (libs 추출 또는 각 서비스 도메인 예외로 유지)
- 선택한 방안에 따른 구현
- GlobalExceptionHandler의 AccessDeniedException 핸들러 수정 (필요 시)
- 관련 테스트 수정

## Out of Scope

- AccessDeniedException의 동작 변경 (403 ACCESS_DENIED 유지)
- 다른 예외 클래스의 중복 제거
- 새로운 예외 계층 구조 설계

---

# Acceptance Criteria

- [ ] AccessDeniedException 중복이 제거되었다 (libs 추출 또는 각 서비스 도메인 예외 통일 중 하나)
- [ ] 선택한 방안이 shared-library-policy.md 기준을 준수한다
- [ ] 모든 대상 서비스에서 접근 거부 시 403 ACCESS_DENIED를 반환한다
- [ ] 에러 응답이 표준 ErrorResponse 포맷(code, message, timestamp)을 따른다
- [ ] 기존 테스트가 모두 통과한다

---

# Related Specs

- `specs/platform/shared-library-policy.md`
- `specs/platform/error-handling.md`

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, API 계약 변경 없음)

---

# Target Service

- `shipping-service`
- `promotion-service`
- `user-service`
- `libs/java-web` (libs 추출 방안 선택 시)

---

# Architecture

Follow:

- `specs/platform/shared-library-policy.md`
- `specs/platform/error-handling.md`
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- shared-library-policy.md 판단 기준:
  1. 2개 이상 서비스에서 사용? -> Yes (3개)
  2. 기술/공통인가 도메인 소유인가? -> 접근 거부는 공통 기술 예외
  3. 한 서비스의 내부 모델에 의존하지 않고 안정적인가? -> Yes
  4. libs 이동이 중복 감소 + 결합도 증가 없는가? -> Yes
- "common exception primitives" 허용 범주에 해당하므로 libs/java-web 추출이 적절
- libs 추출 시 패키지: libs/java-web의 exception 관련 패키지
- 각 서비스의 GlobalExceptionHandler에서 import 경로 변경

---

# Edge Cases

- 각 서비스의 AccessDeniedException이 미세하게 다른 경우 (필드, 생성자 등) -> 공통 부분만 추출하고 차이점은 서비스에서 확장
- Spring Security의 AccessDeniedException과 이름 충돌 -> 패키지로 구분하여 import 명확화
- libs/java-web이 아직 존재하지 않는 경우 -> TASK-R-01에서 생성된 구조 확인

---

# Failure Scenarios

- import 경로 변경 누락으로 컴파일 오류 -> 전체 빌드 확인
- libs/java-web 의존성 누락으로 클래스 찾기 실패 -> build.gradle 확인
- GlobalExceptionHandler에서 잘못된 예외 클래스를 참조 -> 슬라이스 테스트로 확인

---

# Test Requirements

- libs/java-web AccessDeniedException 단위 테스트 (libs 추출 시)
- 각 서비스의 GlobalExceptionHandler 슬라이스 테스트: 접근 거부 시 403 ACCESS_DENIED 반환 검증
- 기존 컨트롤러 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
