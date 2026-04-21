# Task ID

TASK-R-08

# Title

notification-service application port에서 Spring Data 타입 제거

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

notification-service의 NotificationRepository 및 TemplateRepository port 인터페이스가 Spring Data의 Page/Pageable 타입에 의존하고 있다. Hexagonal 아키텍처에서 application port는 프레임워크 독립적이어야 하며, 외부 프레임워크 타입이 포함되면 안 된다.

application port에서 Spring Data 타입을 제거하고, 도메인 또는 application 레이어에 프레임워크 독립적인 페이징 타입을 도입하여 교체한다.

---

# Scope

## In Scope

- NotificationRepository port에서 Spring Data Page/Pageable 제거
- TemplateRepository port에서 Spring Data Page/Pageable 제거
- 프레임워크 독립적인 페이징 타입 도입 (예: PageResult, PageQuery)
- adapter/out 구현체에서 Spring Data 타입과 도메인 페이징 타입 간 변환
- application service에서 새 페이징 타입 사용

## Out of Scope

- 다른 서비스의 port 인터페이스 수정
- API 응답 형식 변경
- 실제 페이징 동작 변경
- Spring Data JPA 자체 제거

---

# Acceptance Criteria

- [ ] NotificationRepository port 인터페이스에 Spring Data 타입(Page, Pageable, Sort 등)이 없다
- [ ] TemplateRepository port 인터페이스에 Spring Data 타입이 없다
- [ ] application 레이어에 프레임워크 독립적인 페이징 타입이 존재한다
- [ ] adapter/out 구현체에서 Spring Data 타입과 도메인 페이징 타입 간 변환이 이루어진다
- [ ] 기존 페이징 API 동작이 변경되지 않는다
- [ ] domain, application 패키지에 Spring Data import가 없다

---

# Related Specs

- `specs/services/notification-service/architecture.md`

# Related Skills

- `.claude/skills/backend/architecture/hexagonal.md`
- `.claude/skills/backend/testing-backend.md`

---

# Related Contracts

- `specs/contracts/http/notification-api.md`

---

# Target Service

- `notification-service`

---

# Architecture

Follow:

- `specs/services/notification-service/architecture.md`

Forbidden Dependencies:
- "domain must not depend on framework, persistence, or external channel details"
- "application must not directly reference adapter implementations"

---

# Implementation Notes

- application 레이어에 PageQuery(page, size) 및 PageResult<T>(content, totalElements, totalPages, page, size) 같은 프레임워크 독립 타입 도입
- adapter/out persistence 구현체에서 PageQuery -> Pageable 변환, Page -> PageResult 변환 수행
- inbound adapter(controller)에서 요청 파라미터를 PageQuery로 변환하여 application service에 전달

---

# Edge Cases

- 빈 결과 페이지 반환 시 PageResult 변환 정상 동작 확인
- page=0, size=0 등 경계값 처리
- 정렬 조건이 있는 경우 도메인 타입으로의 매핑

---

# Failure Scenarios

- 변환 로직 오류로 인한 페이징 결과 불일치
- PageResult 필드 매핑 누락으로 인한 API 응답 변경

---

# Test Requirements

- 단위 테스트: PageQuery -> Pageable 변환, Page -> PageResult 변환 검증
- 단위 테스트: application service가 프레임워크 독립 타입만 사용하는지 검증
- 슬라이스 테스트: 기존 API 응답 형식이 변경되지 않았는지 검증 (@WebMvcTest)
- 통합 테스트: 실제 DB 페이징 조회가 정상 동작하는지 검증 (Testcontainers)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
