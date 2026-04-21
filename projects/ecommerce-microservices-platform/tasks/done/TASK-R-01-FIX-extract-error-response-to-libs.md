# Task ID

TASK-R-01-FIX

# Title

[FIX] libs/java-web에 ErrorResponse 공통 추출 (TASK-R-01 미완료)

# Status

review

# Owner

backend

# Task Tags

- fix
- refactor
- shared-library

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

TASK-R-01의 구현이 완료되지 않았다. libs/java-web에 ErrorResponse record가 존재하지 않으며, 11개 서비스 모두 자체 ErrorResponse 클래스를 그대로 유지하고 있다. ErrorResponse를 libs/java-web으로 추출하고 각 서비스의 중복 클래스를 제거하여 공통 라이브러리를 참조하도록 변경해야 한다.

---

# Scope

## In Scope

- libs/java-web에 ErrorResponse record 생성 (code, message, timestamp 필드, specs/platform/error-handling.md 포맷 준수)
- 다음 서비스의 중복 ErrorResponse 클래스 제거 및 import 경로 변경:
  - auth-service
  - order-service
  - payment-service
  - product-service
  - search-service
  - user-service
  - gateway-service
  - batch-worker
  - notification-service
  - shipping-service
  - promotion-service
  - review-service
- 각 서비스의 build.gradle에 libs/java-web 의존성 추가 (없는 경우)
- GlobalExceptionHandler 등 ErrorResponse를 참조하는 코드의 import 경로 변경
- 기존 테스트의 import 경로 수정

## Out of Scope

- ErrorResponse 필드 변경 (기존 포맷 유지)
- GlobalExceptionHandler 로직 변경
- 새로운 에러 코드 추가

---

# Acceptance Criteria

- [ ] libs/java-web에 ErrorResponse record가 존재한다 (`com.example.web.dto.ErrorResponse` 등 적합한 패키지)
- [ ] ErrorResponse는 code(String), message(String), timestamp(String) 필드를 가진다
- [ ] 모든 서비스의 중복 ErrorResponse 클래스가 제거되었다
- [ ] 모든 서비스가 libs/java-web의 ErrorResponse를 import한다
- [ ] 모든 서비스의 GlobalExceptionHandler가 정상 동작한다
- [ ] 모든 기존 테스트가 통과한다
- [ ] API 응답 포맷이 변경되지 않았다

---

# Related Specs

- `specs/platform/shared-library-policy.md`
- `specs/platform/error-handling.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, API 계약 변경 없음)

---

# Edge Cases

- 일부 서비스의 ErrorResponse에 of() 팩토리 메서드가 있는 경우 -> libs/java-web ErrorResponse에도 동일한 팩토리 메서드 제공
- gateway-service가 WebFlux 기반인 경우 -> 호환성 확인
- batch-worker에 web 의존성이 없는 경우 -> libs/java-web 의존성 추가 시 충돌 여부 확인

---

# Failure Scenarios

- import 경로 변경 누락으로 컴파일 오류 -> 전 서비스 빌드 확인
- libs/java-web 의존성 추가 누락으로 클래스 찾기 실패 -> build.gradle 확인
- ErrorResponse 직렬화 동작 변경으로 API 응답 포맷 달라짐 -> 슬라이스 테스트에서 JSON 검증

---

# Test Requirements

- libs/java-web ErrorResponse 단위 테스트 (생성, 직렬화 확인)
- 각 서비스의 기존 GlobalExceptionHandler 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
