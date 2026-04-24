# ADR-001: 단일 판매자 B2C에서 모놀리식이 아닌 MSA를 택한 이유

- **Status**: Accepted
- **Date**: 2026-03
- **Tags**: architecture, microservices

## Context

본 프로젝트는 단일 판매자 기반 B2C 이커머스 플랫폼이다. 상품·주문·결제·배송·리뷰·알림·검색·프로모션 등 10여 개 bounded context가 존재한다.

일반적 선택지:

- **A. 모놀리식 (Modular Monolith)**: Spring Boot 단일 앱 + 모듈 분리. 배포·트랜잭션 단순.
- **B. 마이크로서비스 (MSA)**: 서비스별 독립 배포·DB·언어 선택 가능.

단일 판매자 규모에서는 A가 "과잉 투자"라는 일반론이 있다.

## Decision

**B안(MSA)을 채택**한다. 단, "MSA를 했다"는 자랑이 아니라 **학습·검증 플랫폼**으로 설계한다.

서비스 분할 기준:

| 서비스 | 분할 이유 |
|---|---|
| `order-service`, `payment-service` | 강한 일관성 요구 → DDD 명시적 모델링 필요 |
| `product-service`, `search-service` | 읽기/쓰기 비율·캐시 전략이 극단적으로 다름 |
| `notification-service`, `shipping-service` | 외부 시스템 통합(PG, 택배, SMS) 격리 |
| `gateway-service` | 인증·라우팅 횡단 관심사 |

## Consequences

### Positive
- 서비스별 **Service Type 선언**(rest-api / event-consumer / batch-job)에 따라 로딩 규칙이 달라지도록 설계(`specs/platform/service-types/`). 한 조직이 다양한 백엔드 스타일을 한 저장소에서 학습할 수 있음.
- bounded context 경계가 실제 배포 단위와 일치 → 도메인 오염 방지
- Saga·Outbox·Idempotency 같은 분산 시스템 패턴을 **실제 코드에서 다룰 수 있음** (포트폴리오/학습 가치)
- 서비스마다 다른 **아키텍처 스타일**(DDD / CRUD / Pipeline) 정당하게 혼용 가능 → [ADR-003](ADR-003-frontend-architecture-dual-strategy.md)

### Negative
- 운영 복잡도 ↑: docker-compose 서비스 30+개, Kafka·Prometheus·Grafana 포함
- 프론트엔드가 단일 API 게이트웨이 경유 → 게이트웨이 자체가 SPOF 후보
- 분산 트랜잭션 문제를 Saga로 풀어야 함 → [ADR-002](ADR-002-saga-over-distributed-transaction.md)
- 로컬 기동이 무거움 (첫 빌드 15~20분, RAM 8GB+ 권장)

### 버린 대안: Modular Monolith
- **왜 안 택했나**: "언제든 MSA로 쪼갤 수 있도록 잘 분리하자"는 명분은 실무에서 거의 실현 안 된다는 경험칙. 한 프로세스 안에서는 호출이 저렴하므로 경계가 흐려지기 쉬움
- **만약 재선택한다면**: 단일 판매자 + 트래픽 하위 중간 규모면 Modular Monolith가 정답일 수도. 본 결정은 **학습·증명 프로젝트**라는 맥락에 종속됨을 명시

## References

- [PROJECT.md](../../PROJECT.md) — domain=ecommerce, traits=[transactional, content-heavy, read-heavy, integration-heavy]
- [specs/platform/service-types/INDEX.md](../../specs/platform/service-types/INDEX.md)
- [specs/services/](../../specs/services/) — 13개 서비스 architecture.md
