# Task ID

TASK-R-12

# Title

컨트롤러 비즈니스 로직(validateAdminRole) application service로 이동

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

shipping-service와 promotion-service 컨트롤러에 있는 validateAdminRole 권한 검증 로직을 application service로 이동한다. 아키텍처 규칙 "controllers must not bypass application services"를 준수하고, 컨트롤러는 HTTP 매핑과 요청 검증만 담당하도록 한다.

---

# Scope

## In Scope

- shipping-service 컨트롤러의 validateAdminRole 로직을 application service로 이동
- promotion-service 컨트롤러의 validateAdminRole 로직을 application service로 이동
- application service에서 권한 검증 수행 후 비즈니스 로직 실행
- 컨트롤러에서 권한 검증 코드 제거

## Out of Scope

- Spring Security 기반 권한 체계 도입
- 다른 서비스의 권한 검증 로직 변경
- 새로운 권한 검증 규칙 추가
- API 동작 변경 (권한 없는 사용자에 대한 응답은 동일해야 함)

---

# Acceptance Criteria

- [ ] shipping-service 컨트롤러에 validateAdminRole 또는 동등한 비즈니스 로직이 없다
- [ ] promotion-service 컨트롤러에 validateAdminRole 또는 동등한 비즈니스 로직이 없다
- [ ] application service에서 권한 검증이 수행된다
- [ ] 권한 없는 사용자의 요청 시 기존과 동일한 HTTP 상태 코드와 에러 응답이 반환된다
- [ ] 컨트롤러는 HTTP 매핑과 요청 위임만 수행한다

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/services/shipping-service/architecture.md`
- `specs/services/promotion-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/ddd.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/shipping-api.md`
- `specs/contracts/http/promotion-api.md`

---

# Target Service

- `shipping-service`
- `promotion-service`

---

# Architecture

Follow:

- `specs/services/shipping-service/architecture.md`
- `specs/services/promotion-service/architecture.md`

Forbidden Dependencies (양쪽 동일):
- "controllers must not bypass application services"

Boundary Rules:
- "interface layer handles HTTP mapping and request validation entry"
- "application layer coordinates use-cases and transaction boundaries"

---

# Implementation Notes

- 컨트롤러에서 사용자 역할 정보를 추출하여 Command에 포함시키거나, application service 메서드 파라미터로 전달
- application service에서 역할 검증 실패 시 AccessDeniedException 또는 동등한 도메인 예외 발생
- 두 서비스 모두 DDD-style이므로 동일한 패턴 적용 가능
- GlobalExceptionHandler에서 해당 예외를 403으로 매핑하는 핸들러가 있는지 확인

---

# Edge Cases

- 관리자 역할 정보가 헤더에 없는 경우 처리
- 관리자 역할 문자열 비교 시 대소문자 처리
- 여러 엔드포인트에 걸쳐 동일한 검증 로직이 중복되는 경우 application service 내에서 공통 메서드로 추출

---

# Failure Scenarios

- application service로 이동 후 역할 정보 전달 누락으로 인한 검증 미동작
- 예외 매핑 누락으로 인한 500 반환 (기존 403이어야 함)

---

# Test Requirements

- 단위 테스트: application service에서 권한 검증 성공/실패 케이스 검증
- 슬라이스 테스트: 권한 없는 요청 시 403 반환 검증 (@WebMvcTest)
- 슬라이스 테스트: 권한 있는 요청 시 정상 처리 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
