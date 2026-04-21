# TASK-INT-005: payment-service Kafka 전환 — 컨슈머 + 프로듀서

## Goal
payment-service의 이벤트 발행/소비를 Spring ApplicationEvent에서 Apache Kafka로 전환한다.

## Scope
컨슈머:
- `OrderPlacedEventConsumer`: @EventListener → @KafkaListener (`order.order.placed`, 그룹: `payment-service`)
- `OrderCancelledEventConsumer`: @EventListener → @KafkaListener (`order.order.cancelled`, 그룹: `payment-service`)

프로듀서:
- `PaymentProcessingService`: ApplicationEventPublisher → KafkaTemplate (`payment.payment.completed`)
- `PaymentRefundService`: ApplicationEventPublisher → KafkaTemplate (`payment.payment.refunded`)

테스트:
- `PaymentProcessingServiceTest`: Mock ApplicationEventPublisher → Mock KafkaTemplate
- `PaymentRefundServiceTest`: Mock ApplicationEventPublisher → Mock KafkaTemplate
- `PaymentProcessingIntegrationTest`: @EmbeddedKafka + KafkaTemplate.send() + Awaitility
- `PaymentRefundIntegrationTest`: @EmbeddedKafka + KafkaTemplate.send() + Awaitility
- `OrderPlacedEventConsumerTest`, `OrderCancelledEventConsumerTest`: handle() 직접 호출로 변경 없음

## Acceptance Criteria
- order.order.placed 수신 시 Payment COMPLETED 생성 + payment.payment.completed 발행
- order.order.cancelled 수신 시 Payment REFUNDED 처리 + payment.payment.refunded 발행
- 모든 단위/통합 테스트 통과

## Related Specs
- `specs/platform/event-driven-policy.md`

## Related Contracts
- `specs/contracts/events/order-events.md`
- `specs/contracts/events/payment-events.md`

## Edge Cases
- Kafka 발행 실패 시 try-catch로 감싸 서비스 로직에 영향 없음
- 중복 이벤트는 멱등 처리

## Failure Scenarios
- 처리 실패 시 에러 로그 후 Kafka offset 커밋 (At-least-once)
