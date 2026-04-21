# Task ID

TASK-BE-065

# Title

전 서비스 입력 검증 강화 — @Valid 누락, 금액/헤더 검증 보완

# Status

review

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

TASK-INT-012 크로스 리뷰에서 발견된 Major 이슈 수정. 전체 서비스에서 발견된 입력 검증 누락을 수정한다.

---

# Scope

## In Scope

- product-service AdminProductController: UpdateProductRequest에 @Valid 추가
- product-service UpdateProductRequest: 음수 가격 검증 추가
- payment-service OrderPlacedEventConsumer: amount null/음수 검증 추가
- payment-service PaymentController: @Validated 추가
- search-service SearchController: Spring validation 어노테이션 적용
- auth-service RefreshRequest: refreshToken 최소 길이 검증 추가
- gateway-service JwtAuthenticationFilter: JWT 시크릿 최소 길이 검증

## Out of Scope

- 검증 로직 공통 라이브러리 추출

---

# Acceptance Criteria

- [ ] 모든 대상 컨트롤러/컨슈머에 입력 검증이 추가된다
- [ ] 잘못된 입력 시 적절한 에러 응답이 반환된다
- [ ] 각 수정 항목에 대한 테스트가 추가된다

---

# Related Specs

- `specs/platform/coding-rules.md`
- `specs/platform/security-rules.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- UpdateProductRequest에 price만 전달되고 음수인 경우
- amount가 0인 결제 이벤트

---

# Failure Scenarios

- 검증 실패 시 400 Bad Request 또는 이벤트 skip/DLQ 처리

---

# Test Requirements

- 각 검증 항목별 단위/슬라이스 테스트
- 음수 가격, null amount 등 경계값 테스트
