# Task ID

TASK-INT-020

# Title

E2E 테스트 커버리지 확대 — 핵심 비즈니스 흐름 전체 시나리오 추가

# Status

done

# Owner

integration

# Task Tags

- test
- deploy

---

# Goal

기존 E2E 테스트 스크립트를 확장하여 핵심 비즈니스 흐름의 전체 시나리오를 검증한다.

현재 기본 플로우 외에 에러 케이스, 동시성 시나리오, 이벤트 기반 흐름을 추가한다.

---

# Scope

## In Scope

- 회원가입 → 로그인 → 상품 검색 → 주문 → 결제 → 주문 확인 전체 흐름
- 주문 취소 → 환불 처리 흐름
- 사용자 탈퇴 시 관련 데이터 처리 흐름
- 재고 부족 시 주문 실패 흐름
- 동시 주문 시 재고 정합성 검증
- 토큰 만료 및 갱신 흐름

## Out of Scope

- 프론트엔드 E2E (Cypress/Playwright)
- 성능 테스트 (TASK-INT-018에서 다룸)
- 인프라 장애 시나리오

---

# Acceptance Criteria

- [ ] 정상 전체 흐름 E2E 테스트 존재 (회원가입 ~ 주문 확인)
- [ ] 주문 취소 및 환불 E2E 테스트 존재
- [ ] 사용자 탈퇴 흐름 E2E 테스트 존재
- [ ] 재고 부족 주문 실패 E2E 테스트 존재
- [ ] 동시 주문 재고 정합성 E2E 테스트 존재
- [ ] 토큰 갱신 흐름 E2E 테스트 존재
- [ ] docker-compose 환경에서 전체 테스트 실행 가능

---

# Related Specs

- `specs/platform/testing-strategy.md`
- `specs/contracts/http/*`
- `specs/contracts/events/*`

# Related Skills

- N/A

---

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/search-api.md`
- `specs/contracts/http/order-api.md`
- `specs/contracts/http/payment-api.md`
- `specs/contracts/http/user-api.md`
- `specs/contracts/events/order-events.md`
- `specs/contracts/events/payment-events.md`
- `specs/contracts/events/user-events.md`

---

# Participating Components

- 전체 서비스

# Trigger

기존 E2E 테스트가 기본 플로우만 검증하여 엣지 케이스 커버리지 확대 필요.

# Expected Flow

1. 기존 E2E 테스트 스크립트 분석
2. 추가 시나리오 설계
3. 테스트 스크립트 작성
4. docker-compose 환경에서 전체 실행 확인

# Edge Cases

- 이벤트 기반 흐름에서 비동기 처리 완료 대기
- 서비스 간 데이터 일관성 검증 타이밍
- 테스트 데이터 격리

# Failure Scenarios

- 비동기 이벤트 처리 지연으로 인한 테스트 실패
- 테스트 간 데이터 간섭

# Test Requirements

- E2E 테스트 스크립트 실행 및 전체 통과

# Definition of Done

- [ ] 추가 E2E 시나리오 작성 완료
- [ ] docker-compose 환경에서 전체 통과
- [ ] 테스트 실행 문서화
- [ ] Ready for review
