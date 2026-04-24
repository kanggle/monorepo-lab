# Task ID

TASK-BE-097

# Title

gateway-service 통합 테스트 user-service 라우트 누락 수정

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

`application-integration-test.yml`에 `user-service` 라우트 정의가 누락되어 있다. 메인 `application.yml`에는 `/api/users/**`, `/api/admin/users/**` 라우트가 존재하지만 통합 테스트 프로파일에는 빠져 있어 user-service 관련 통합 테스트가 불가능하다.

통합 테스트 프로파일에 user-service 라우트를 추가한다.

gateway-service 코드 리뷰에서 발견된 이슈.

---

# Scope

## In Scope

- `application-integration-test.yml`에 user-service 라우트 추가 (`/api/users/**`, `/api/admin/users/**`)

## Out of Scope

- 메인 `application.yml` 변경
- 다른 서비스 라우트 변경
- 새로운 통합 테스트 케이스 추가

---

# Acceptance Criteria

- [ ] `application-integration-test.yml`에 user-service 라우트가 추가된다 (`/api/users/**`, `/api/admin/users/**`)
- [ ] 기존 통합 테스트가 정상 통과한다
- [ ] 메인 `application.yml`의 라우트 목록과 통합 테스트 프로파일의 라우트 목록이 서비스 수준에서 일치한다

---

# Related Specs

- `specs/services/gateway-service/architecture.md`

# Related Skills

- `.claude/skills/backend/`

---

# Related Contracts

- 없음

---

# Target Service

- `gateway-service`

---

# Architecture

Follow:

- `specs/services/gateway-service/architecture.md`

---

# Implementation Notes

- 기존 통합 테스트 라우트와 동일한 패턴으로 추가: `uri: http://localhost:19999`, rate limiter 없음
- predicates: `Path=/api/users/**,/api/admin/users/**`

---

# Edge Cases

- 없음

---

# Failure Scenarios

- 라우트 predicate 오타 시 통합 테스트에서 user-service 경로가 매칭되지 않음

---

# Test Requirements

- 기존 통합 테스트 전체 통과 확인

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Ready for review
