# Task ID

TASK-BE-069

# Title

order-service DTO 네이밍 수정 및 gateway-service 서비스 레이어 도입

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

TASK-INT-012 크로스 리뷰에서 발견된 Major 이슈 수정. order-service의 혼란스러운 내부 DTO 네이밍과 gateway-service의 서비스 레이어 부재 및 DTO 미사용 문제를 수정한다.

---

# Scope

## In Scope

### order-service
- OrderDetailResponse 내부 레코드 `OrderItemItem` → `OrderItemDetail`로 변경
- OrderDetailResponse 내부 레코드 `ShippingAddressItem` → `ShippingAddressDetail`로 변경

### gateway-service
- JwtAuthenticationFilter 에러 응답용 DTO 클래스 도입 (Map<String, String> 대체)
- 공통 라우트 판단 로직(isPublicRoute, resolveTargetService) 서비스 클래스로 추출
- 에러 응답 DTO 단위 테스트 추가

## Out of Scope

- gateway-service 전체 아키텍처 재설계

---

# Acceptance Criteria

- [ ] order-service 내부 DTO 이름이 명확하게 변경된다
- [ ] gateway-service에 에러 응답 DTO가 도입된다
- [ ] 공통 라우트 로직이 서비스 클래스로 추출된다
- [ ] 전체 테스트가 통과한다

---

# Related Specs

- `specs/platform/naming-conventions.md`
- `specs/platform/coding-rules.md`

# Related Contracts

_(없음)_

---

# Edge Cases

- DTO 이름 변경 후 기존 직렬화 호환성 (JSON 필드명은 변경 없음)

---

# Failure Scenarios

_(없음)_

---

# Test Requirements

- 변경된 DTO 직렬화/역직렬화 테스트
- 서비스 클래스 단위 테스트
