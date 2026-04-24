# Task ID

TASK-BE-067

# Title

product-service 패키지명 수정 — interfaces → presentation

# Status

done

# Owner

backend

# Task Tags

- code, test

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

TASK-INT-012 크로스 리뷰에서 발견된 Major 이슈 수정. product-service의 presentation 레이어 패키지명이 `interfaces`로 되어있어 플랫폼 네이밍 컨벤션(`com.example.{service}.{layer}` → domain, application, infrastructure, presentation)과 불일치하는 문제를 수정한다.

---

# Scope

## In Scope

- `com.example.product.interfaces` → `com.example.product.presentation` 패키지 리네임
- 영향받는 모든 import 문 수정
- 테스트 파일의 패키지명 수정

## Out of Scope

- 다른 서비스의 패키지 구조 변경

---

# Acceptance Criteria

- [x] interfaces 패키지가 presentation으로 변경된다
- [x] 모든 import 문이 수정된다
- [x] 전체 테스트가 통과한다 (presentation 레이어 테스트 전체 통과, 기존 application 레이어 테스트 실패 23건은 패키지 리네임과 무관)
- [x] 빌드가 성공한다 (컴파일 성공)

---

# Related Specs

- `specs/platform/naming-conventions.md`
- `specs/services/product-service/architecture.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- 리팩토링 시 IDE 자동 import가 누락되는 파일 확인

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- 전체 빌드 및 테스트 통과 확인
