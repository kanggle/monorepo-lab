# Task ID

TASK-INT-007

# Title

E2E 통합 테스트 — docker compose 환경 전체 흐름 검증 스크립트

# Status

done

# Owner

backend

# Task Tags

- deploy
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

`docker compose up` 이후 전체 서비스가 정상 기동되었는지 확인하고, 핵심 비즈니스 플로우(회원가입 → 로그인 → 상품 조회 → 주문 생성 → 결제 처리)를 자동으로 검증하는 E2E 테스트 스크립트를 작성한다.

---

# Scope

## In Scope

- 전체 서비스 헬스체크 확인 스크립트 (`scripts/e2e-healthcheck.sh`)
- 핵심 비즈니스 플로우 E2E 테스트 스크립트 (`scripts/e2e-test.sh`)
  - 회원가입 (POST /api/auth/signup)
  - 로그인 (POST /api/auth/login)
  - 상품 목록 조회 (GET /api/products)
  - 상품 검색 (GET /api/search/products)
  - 주문 생성 (POST /api/orders)
  - 주문 조회 (GET /api/orders/{orderId})
  - 결제 상태 확인
- 각 단계별 성공/실패 판정 및 결과 출력

## Out of Scope

- 브라우저 기반 UI E2E 테스트 (Playwright, Cypress 등)
- 부하 테스트 / 성능 테스트
- 프론트엔드 렌더링 검증

---

# Acceptance Criteria

- [ ] `scripts/e2e-healthcheck.sh` 실행 시 모든 서비스 헬스체크 상태를 확인한다
- [ ] `scripts/e2e-test.sh` 실행 시 핵심 비즈니스 플로우를 순차적으로 실행하고 결과를 출력한다
- [ ] 각 API 호출의 HTTP 상태 코드와 응답 본문을 검증한다
- [ ] 테스트 실패 시 어느 단계에서 실패했는지 명확히 출력한다
- [ ] gateway-service를 통해 모든 API를 호출한다 (localhost:8080)

---

# Related Specs

- `specs/platform/testing-strategy.md`
- `specs/platform/deployment-policy.md`
- `specs/contracts/` (각 API 계약 참조)

# Related Skills

_(없음)_

---

# Related Contracts

- `specs/contracts/auth-api.md`
- `specs/contracts/product-api.md`
- `specs/contracts/search-api.md`
- `specs/contracts/order-api.md`
- `specs/contracts/payment-api.md`

---

# Target Service

- `scripts/e2e-healthcheck.sh` (신규)
- `scripts/e2e-test.sh` (신규)

---

# Architecture

_(해당 없음)_

---

# Edge Cases

- 서비스가 아직 기동 중일 때 헬스체크 재시도 (최대 대기 시간 설정)
- Kafka 이벤트 전파에 시간이 걸리는 경우 (주문→결제 간 지연)
- Elasticsearch 인덱싱 지연으로 검색 결과가 즉시 반영되지 않는 경우

---

# Failure Scenarios

- 특정 서비스가 기동 실패 시 해당 서비스명과 에러를 출력하고 테스트 중단
- API 호출 타임아웃 시 재시도 후 실패 보고

---

# Test Requirements

- 스크립트 자체가 테스트이므로 별도 테스트 불필요
- `docker compose up -d` 후 스크립트 실행으로 검증

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
