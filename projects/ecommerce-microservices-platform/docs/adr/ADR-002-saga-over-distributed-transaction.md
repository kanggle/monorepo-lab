# ADR-002: Order + Payment + Inventory에 Saga + Outbox를 택한 이유

- **Status**: Accepted
- **Date**: 2026-03
- **Tags**: transactional, saga, outbox, idempotency

## Context

주문-결제-재고 흐름은 본 플랫폼에서 가장 강한 일관성 요구를 가진 경로다.

- **주문 생성** (order-service) → **재고 차감** (product-service) → **결제 승인** (payment-service) → **배송 생성** (shipping-service)
- 중간 어느 단계든 실패하면 앞 단계를 **보상(compensate)** 해야 한다 (환불, 재고 복구 등).
- 각 서비스는 **독립 DB** (ADR-001).

선택지:

- **A. 분산 트랜잭션 (XA / 2PC)**: 논리적으로 단순하지만 실패·성능·운영 비용이 비쌈. Spring / Kafka / PostgreSQL 환경에서 현실적 선택지 아님.
- **B. 공유 DB에서 로컬 트랜잭션**: 서비스 경계를 훼손 → ADR-001 무효화
- **C. Saga 패턴 + Outbox + Idempotency Key**: 결과적 일관성을 명시적으로 관리

## Decision

**C안(Saga + Outbox + Idempotency)을 채택**한다. 구체적으로:

1. **Choreography Saga**: 각 서비스가 Kafka 이벤트를 소비/발행하며 자기 단계를 수행. 중앙 오케스트레이터 없음.
2. **Transactional Outbox 패턴**: 이벤트 발행은 "DB 커밋 후"가 아니라 "outbox 테이블에 INSERT → 별도 relay가 Kafka로 전송". 커밋과 발행이 같은 트랜잭션 내.
3. **Idempotency Key**: 금전·재고 변경 API는 클라이언트 측 키를 받아 **중복 요청을 결정적으로 거부**.
4. **보상 액션 명시**: Saga 각 단계는 대응하는 compensating action을 `specs/services/<service>/sagas/`에 반드시 문서화.

## Consequences

### Positive
- 분산 트랜잭션 없이 **서비스 자율성 + 최종 일관성** 확보
- 장애 격리: 결제 서비스가 잠시 내려가도 주문 서비스는 계속 동작 (이벤트가 큐에 쌓이며 복구 시 재처리)
- 감사 가능성: 모든 상태 전이가 이벤트 히스토리로 보존 ([specs/rules/traits/transactional.md#T7](../../specs/rules/traits/transactional.md))
- **포트폴리오 가치**: 실제 분산 시스템 패턴을 코드로 증명

### Negative
- 보상 로직 설계·테스트 비용이 **원래 트랜잭션 작성 비용보다 큼**. 특히 "보상의 보상"이 필요한 엣지 케이스.
- 이벤트 스키마 변경 시 consumer 전체에 파급 → Contract-first 원칙을 강제([specs/contracts/](../../specs/contracts/))
- Debugging 난이도 ↑ — 단일 로그 스트림에서 flow를 재구성해야 함. OpenTelemetry + 상관관계 ID로 완화.

### 버린 대안: Orchestration Saga (중앙 코디네이터)
- **왜 안 택했나**: 코디네이터 서비스 자체가 SPOF가 되고, 비즈니스 로직이 coordinator에 쌓여 서비스 자율성이 다시 훼손됨
- **언제 재고할만 한가**: flow가 복잡해지고(5단계 이상) 단계별 실패 분기가 많아질 때. 현재는 Choreography로 충분

### 버린 대안: Event Sourcing
- **왜 안 택했나**: 학습 곡선·저장 비용이 크고, 본 프로젝트의 실제 요구는 "상태 히스토리 보관"인데 outbox + 상태 전이 테이블로 충분
- **재고 조건**: 감사 규제(PCI-DSS, SOC2)로 불변 이벤트 스트림이 강제될 경우

## References

- [specs/rules/traits/transactional.md](../../specs/rules/traits/transactional.md)
- [README.md § Event-Driven Architecture](../../README.md#event-driven-architecture)
- [README.md § Key Business Flows § 주문 → 결제 → 배송 (Saga)](../../README.md#주문--결제--배송-saga)
