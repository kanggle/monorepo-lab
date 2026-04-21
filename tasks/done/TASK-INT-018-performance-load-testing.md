# Task ID

TASK-INT-018

# Title

성능/부하 테스트 구성 — 전체 서비스 부하 테스트 스크립트 및 기준 수립

# Status

done

# Owner

integration

# Task Tags

- test
- deploy

---

# Goal

프로덕션 배포 전 성능 검증을 위한 부하 테스트 환경을 구성한다.

주요 API 엔드포인트에 대한 부하 테스트 스크립트를 작성하고, 성능 기준(SLA)을 정의한다.

---

# Scope

## In Scope

- 부하 테스트 도구 선정 및 설정 (k6, Artillery 등)
- 핵심 API 엔드포인트별 부하 테스트 시나리오 작성
  - 인증 (로그인, 토큰 갱신)
  - 상품 조회 및 검색
  - 주문 생성
  - 결제 처리
- 성능 기준(응답 시간, 처리량, 에러율) 정의
- docker-compose 환경에서 실행 가능한 스크립트

## Out of Scope

- 클라우드 환경 부하 테스트
- 프론트엔드 성능 테스트
- 성능 튜닝 구현

---

# Acceptance Criteria

- [ ] 부하 테스트 도구 설정 파일 존재
- [ ] 최소 4개 핵심 API에 대한 테스트 시나리오 작성
- [ ] 성능 기준(P95 응답시간, RPS, 에러율) 정의
- [ ] docker-compose 환경에서 부하 테스트 실행 가능
- [ ] 테스트 결과 리포트 생성 가능

---

# Related Specs

- `specs/platform/testing-strategy.md`
- `specs/platform/deployment-policy.md`
- `specs/contracts/http/*`

# Related Skills

- N/A

---

# Related Contracts

- `specs/contracts/http/auth-api.md`
- `specs/contracts/http/product-api.md`
- `specs/contracts/http/search-api.md`
- `specs/contracts/http/order-api.md`
- `specs/contracts/http/payment-api.md`

---

# Participating Components

- gateway-service (진입점)
- auth-service
- product-service
- search-service
- order-service
- payment-service

# Trigger

프로덕션 배포 전 성능 검증 필요.

# Expected Flow

1. 부하 테스트 도구 선정 및 프로젝트 설정
2. 테스트 시나리오 작성 (인증 → 검색 → 주문 → 결제 흐름)
3. 성능 기준 정의
4. 실행 스크립트 및 리포트 설정

# Edge Cases

- 서비스 간 의존성으로 인한 병목 구간 식별
- 동시 사용자 수 증가에 따른 DB 커넥션 풀 한계
- Kafka 메시지 처리 지연

# Failure Scenarios

- 테스트 환경 리소스 부족으로 인한 부정확한 결과
- 외부 의존성(Elasticsearch, Kafka) 병목

# Test Requirements

- 부하 테스트 스크립트 자체의 동작 검증 (dry-run)

# Definition of Done

- [ ] 부하 테스트 환경 구성 완료
- [ ] 테스트 시나리오 작성 완료
- [ ] 성능 기준 문서화
- [ ] 실행 및 리포트 확인
- [ ] Ready for review
