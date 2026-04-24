# TASK-INT-004: order-service Kafka 전환 — 프로듀서 + 컨슈머

## Goal
order-service의 이벤트 발행/소비를 Spring ApplicationEvent에서 Apache Kafka로 전환한다.

## Scope
프로듀서:
- `OrderPlacementService`: ApplicationEventPublisher → KafkaTemplate (`order.order.placed`)
- `OrderCancellationService`: ApplicationEventPublisher → KafkaTemplate (`order.order.cancelled`)

컨슈머:
- `StockChangedEventConsumer`: @EventListener → @KafkaListener (`product.product.stock-changed`, 그룹: `order-service`)

테스트:
- `OrderPlacementServiceTest`: Mock ApplicationEventPublisher → Mock KafkaTemplate
- `OrderCancellationServiceTest`: Mock ApplicationEventPublisher → Mock KafkaTemplate
- `OrderConfirmationIntegrationTest`: @EmbeddedKafka + KafkaTemplate.send() + Awaitility
- `OrderPlacementIntegrationTest`, `OrderCancellationIntegrationTest`, `OrderQueryIntegrationTest`: @EmbeddedKafka 추가

## Acceptance Criteria
- 주문 생성 시 order.order.placed 토픽으로 이벤트 발행
- 주문 취소 시 order.order.cancelled 토픽으로 이벤트 발행
- product.product.stock-changed 토픽에서 ORDER_RESERVED 이벤트 수신 시 주문 확정
- 모든 단위/통합 테스트 통과

## Related Specs
- `specs/platform/event-driven-policy.md`

## Related Contracts
- `specs/contracts/events/order-events.md`
- `specs/contracts/events/product-events.md`

## Edge Cases
- Kafka 발행 실패 시 try-catch로 감싸 서비스 로직에 영향 없음
- ORDER_RESERVED 외 reason은 무시

## Failure Scenarios
- StockChanged 처리 실패 시 에러 로그 후 Kafka offset 커밋 (At-least-once)
