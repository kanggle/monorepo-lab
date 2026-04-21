# Task ID

TASK-FE-028

# Title

프론트엔드 타입 안전성 강화 및 에러 바운더리 추가

# Status

review

# Owner

frontend

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

TASK-INT-012 크로스 리뷰에서 발견된 Critical/Major 이슈 수정. 프론트엔드에서 unsafe type cast 패턴과 에러 바운더리 누락 문제를 수정한다.

---

# Scope

## In Scope

- web-store LoginForm.tsx: `err as ApiErrorResponse` 안전한 타입 가드로 교체
- admin-dashboard login/page.tsx: 타입 캐스팅 안전성 개선
- admin-dashboard ProductForm.tsx, StockAdjustmentForm.tsx: 타입 캐스팅 안전성 개선
- web-store app/layout.tsx: error.tsx 에러 바운더리 추가
- admin-dashboard app/layout.tsx: error.tsx 에러 바운더리 추가
- web-store page.tsx, products/page.tsx: let → const 리팩토링

## Out of Scope

- 글로벌 에러 리포팅 시스템 도입

---

# Acceptance Criteria

- [ ] 모든 unsafe type cast가 타입 가드로 교체된다
- [ ] web-store와 admin-dashboard 루트에 error.tsx가 추가된다
- [ ] let 대신 const가 사용된다
- [ ] 기존 테스트가 통과한다

---

# Related Specs

- `specs/platform/coding-rules.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- API 에러 응답이 예상과 다른 형식일 때 타입 가드가 안전하게 처리

---

# Failure Scenarios

- 에러 바운더리가 렌더링 에러를 정상적으로 캐치하고 fallback UI를 보여줌

---

# Test Requirements

- 타입 가드 함수 단위 테스트
- 에러 바운더리 렌더링 테스트
