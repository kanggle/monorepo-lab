# TASK-INT-003: search-service Kafka 컨슈머 전환

## Goal
search-service의 이벤트 소비를 Spring ApplicationEvent에서 Apache Kafka로 전환한다.
이벤트 레코드를 이벤트 envelope 구조(eventId, eventType, occurredAt, source, payload)를 포함하도록 업데이트한다.

## Scope
- 이벤트 레코드 4개 envelope 구조 적용:
  - `ProductCreatedEvent`: payload → ProductCreatedPayload
  - `ProductUpdatedEvent`: payload → ProductUpdatedPayload
  - `ProductDeletedEvent`: payload → ProductDeletedPayload
  - `StockChangedEvent`: payload → StockChangedPayload (orderId 포함)
- 컨슈머 4개 @KafkaListener 전환:
  - `ProductCreatedConsumer`: `product.product.created`
  - `ProductUpdatedConsumer`: `product.product.updated`
  - `ProductDeletedConsumer`: `product.product.deleted`
  - `StockChangedConsumer`: `product.product.stock-changed`
- 컨슈머 그룹: `search-service`
- 단위 테스트 및 통합 테스트 업데이트

## Acceptance Criteria
- 각 컨슈머가 해당 토픽에서 JSON 문자열을 수신하여 역직렬화 후 처리한다
- handle() 메서드는 package-private으로 단위 테스트에서 직접 호출 가능하다
- 단위 테스트(ProductCreatedConsumerTest)가 통과한다
- 통합 테스트(IndexSyncIntegrationTest, SearchQueryIntegrationTest)가 통과한다

## Related Specs
- `specs/platform/event-driven-policy.md`

## Related Contracts
- `specs/contracts/events/product-events.md`

## Edge Cases
- payload가 null인 이벤트는 무시
- JSON 역직렬화 실패 시 에러 로그 후 무시

## Failure Scenarios
- Elasticsearch 장애 시 예외 catch 후 에러 로그
