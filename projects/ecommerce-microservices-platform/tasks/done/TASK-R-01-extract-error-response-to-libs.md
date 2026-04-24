# Task ID

TASK-R-01

# Title

libs/java-web에 ErrorResponse 공통 추출

# Status

review

# Owner

backend

# Task Tags

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

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

11개 서비스에 중복된 ErrorResponse record를 libs/java-web으로 추출하고, 각 서비스가 공통 라이브러리를 참조하도록 변경한다. ErrorResponse는 플랫폼 에러 응답 포맷(code, message, timestamp)을 정의하는 공통 기술 DTO이며, shared-library-policy의 "standardized error response helpers" 허용 범주에 해당한다.

---

# Scope

## In Scope

- libs/java-web에 ErrorResponse record 생성 (code, message, timestamp 필드, specs/platform/error-handling.md 포맷 준수)
- 각 서비스(auth-service, order-service, payment-service, product-service, search-service, user-service, gateway-service, batch-worker, notification-service, shipping-service, promotion-service)의 중복 ErrorResponse 클래스 제거
- 각 서비스의 build.gradle에 libs/java-web 의존성 추가 (이미 있는 경우 확인만)
- GlobalExceptionHandler 등 ErrorResponse를 참조하는 코드의 import 경로 변경
- 기존 테스트의 import 경로 수정

## Out of Scope

- ErrorResponse 필드 변경 (기존 포맷 유지)
- GlobalExceptionHandler 로직 변경
- 새로운 에러 코드 추가
- 서비스별 커스텀 예외 클래스 이동 (도메인 소유 예외는 서비스 내부 유지)

---

# Acceptance Criteria

- [ ] libs/java-web에 ErrorResponse record가 존재한다
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

# Related Skills

- `.claude/skills/backend/refactoring.md`

---

# Related Contracts

- 해당 없음 (내부 리팩토링, API 계약 변경 없음. 에러 응답 JSON 포맷은 기존과 동일하게 유지)

---

# Target Service

- `libs/java-web`
- `auth-service`
- `order-service`
- `payment-service`
- `product-service`
- `search-service`
- `user-service`
- `gateway-service`
- `batch-worker`
- `notification-service`
- `shipping-service`
- `promotion-service`

---

# Architecture

Follow:

- `specs/platform/shared-library-policy.md` (공통 기술 유틸리티 허용 조건 확인)
- 각 서비스의 `specs/services/<service>/architecture.md`

---

# Implementation Notes

- ErrorResponse는 Java record로 구현한다.
- 패키지 위치: libs/java-web의 presentation 또는 web 관련 패키지 (기존 libs/java-web 패키지 구조에 맞춤).
- 서비스별 ErrorResponse 제거 시 import 경로만 변경하고, 필드나 동작은 변경하지 않는다.
- 서비스 간 ErrorResponse 필드가 미세하게 다를 경우, specs/platform/error-handling.md의 포맷(code, message, timestamp)을 기준으로 통일한다.

---

# Edge Cases

- 일부 서비스의 ErrorResponse가 추가 필드를 가지고 있는 경우 -> error-handling.md 기준 포맷으로 통일, 추가 필드가 있다면 해당 서비스에서 확장 클래스로 유지
- gateway-service가 Spring Cloud Gateway 기반으로 다른 web 프레임워크를 사용하는 경우 -> WebFlux/WebMVC 호환성 확인
- libs/java-web이 아직 존재하지 않는 경우 -> 라이브러리 모듈 신규 생성

---

# Failure Scenarios

- import 경로 변경 누락으로 컴파일 오류 -> 전 서비스 빌드 확인
- libs/java-web 의존성 추가 누락으로 클래스 찾기 실패 -> build.gradle 확인
- ErrorResponse 직렬화 동작 변경으로 API 응답 포맷 달라짐 -> 슬라이스 테스트에서 응답 JSON 검증
- 테스트 코드에서 서비스 내부 ErrorResponse를 직접 참조하는 경우 컴파일 오류

---

# Test Requirements

- libs/java-web ErrorResponse 단위 테스트 (생성, 직렬화 확인)
- 각 서비스의 기존 GlobalExceptionHandler 슬라이스 테스트 통과 확인
- 각 서비스의 기존 컨트롤러 슬라이스 테스트 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
