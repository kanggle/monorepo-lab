# TASK-INT-002: product-service Kafka 프로듀서 전환

## Goal
product-service의 이벤트 발행을 Spring ApplicationEvent에서 Apache Kafka로 전환한다.
StockChangedPayload에 orderId 필드를 추가하여 product-events.md 계약과 일치시킨다.

## Scope
- `SpringProductEventPublisher` → `KafkaProductEventPublisher` 교체
- `StockChangedPayload`: orderId 필드 추가
- `AdjustStockService`: StockChangedPayload 생성 시 orderId=null 전달

토픽 매핑:
- ProductCreated → `product.product.created`
- ProductUpdated → `product.product.updated`
- ProductDeleted → `product.product.deleted`
- StockChanged → `product.product.stock-changed`

## Acceptance Criteria
- ProductEvent가 KafkaTemplate을 통해 해당 토픽으로 발행된다
- StockChangedPayload에 orderId 필드가 있다 (nullable)
- 기존 product-service 단위 테스트가 통과한다

## Related Specs
- `specs/platform/event-driven-policy.md`

## Related Contracts
- `specs/contracts/events/product-events.md`

## Edge Cases
- 알 수 없는 eventType은 IllegalArgumentException 발생

## Failure Scenarios
- KafkaTemplate.send() 실패 시 try-catch로 감싸 서비스 로직에 영향 없음
