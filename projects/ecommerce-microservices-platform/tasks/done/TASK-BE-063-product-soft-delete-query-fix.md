# Task ID

TASK-BE-063

# Title

product-service soft-delete 쿼리 버그 수정 — findById에서 삭제된 상품 조회 가능

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

TASK-INT-012 크로스 리뷰에서 발견된 Critical 이슈 수정. ProductRepositoryAdapter.findById()가 soft-delete된 상품(deletedAt IS NOT NULL)을 필터링하지 않아, 삭제된 상품이 조회되는 버그를 수정한다. existsById()는 정상적으로 deletedAt IS NULL 조건을 포함하고 있어 두 메서드 간 불일치가 존재한다.

---

# Scope

## In Scope

- ProductRepositoryAdapter.findById(): deletedAt IS NULL 조건 추가
- findWithVariantsById() JPQL 쿼리 수정
- 관련 테스트 추가

## Out of Scope

- soft-delete 정책 변경

---

# Acceptance Criteria

- [ ] findById()가 deletedAt이 null인 상품만 반환한다
- [ ] soft-delete된 상품 조회 시 ProductNotFoundException이 발생한다
- [ ] existsById()와 findById()의 동작이 일관된다
- [ ] 단위 테스트 및 통합 테스트가 추가된다

---

# Related Specs

- `specs/services/product-service/architecture.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- soft-delete 직후 동일 상품 조회 시 즉시 404 반환 확인
- 캐시 사용 시 삭제된 상품 캐시 무효화 여부

---

# Failure Scenarios

- 삭제된 상품이 여전히 조회되면 주문 플로우에서 존재하지 않는 상품으로 주문 가능

---

# Test Requirements

- soft-delete된 상품 findById 호출 시 Optional.empty() 반환 테스트
- existsById와 findById 일관성 테스트
