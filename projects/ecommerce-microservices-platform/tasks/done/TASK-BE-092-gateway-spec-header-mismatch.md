# Task ID

TASK-BE-092

# Title

gateway-service 스펙 헤더 불일치 수정 — X-User-Role vs X-User-Email 명확화

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

`specs/services/gateway-service/overview.md`에서 "X-User-Id, X-User-Role headers"로 명시되어 있으나, 실제 구현은 `X-User-Id`, `X-User-Email`을 주입한다.

현재 JWT claims에 `role` 필드가 없고 `email`이 있으므로, 스펙을 구현에 맞춰 `X-User-Id, X-User-Email`로 수정한다.

향후 `X-User-Role` 헤더가 필요해지면 별도 태스크로 JWT claims에 role 추가와 함께 구현한다.

코드 리뷰에서 발견된 이슈.

---

# Scope

## In Scope

- `specs/services/gateway-service/overview.md`의 In Scope 항목에서 "X-User-Role" → "X-User-Email"로 수정

## Out of Scope

- JWT claims 변경
- `JwtAuthenticationFilter` 코드 변경
- 다른 서비스의 헤더 처리 변경

---

# Acceptance Criteria

- [ ] `specs/services/gateway-service/overview.md`의 In Scope에 "X-User-Id, X-User-Email headers"로 명시된다
- [ ] 스펙과 구현이 일치한다

---

# Related Specs

- `specs/services/gateway-service/overview.md`

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

- 스펙 문서 수정만 필요. 코드 변경 없음.

---

# Edge Cases

- 없음

---

# Failure Scenarios

- 없음

---

# Test Requirements

- 없음 (스펙 문서 수정만)

---

# Definition of Done

- [ ] Specs updated
- [ ] Ready for review
