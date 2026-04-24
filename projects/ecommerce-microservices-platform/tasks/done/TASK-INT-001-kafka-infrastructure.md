# TASK-INT-001: Kafka 인프라 설정 — docker-compose, build.gradle, application.yml

## Goal
Apache Kafka를 프로젝트 인프라에 추가하고, 이벤트를 발행/소비하는 각 서비스에 spring-kafka 의존성 및 Kafka 설정을 추가한다.

## Scope
- `docker-compose.yml`: Bitnami Kafka (KRaft 모드) 추가
- `apps/product-service/build.gradle`: spring-kafka, spring-kafka-test 추가
- `apps/search-service/build.gradle`: spring-kafka, spring-kafka-test 추가
- `apps/order-service/build.gradle`: spring-kafka, spring-kafka-test 추가
- `apps/payment-service/build.gradle`: spring-kafka, spring-kafka-test 추가
- 각 서비스 `application.yml`: Kafka bootstrap-servers, producer/consumer 설정 추가

## Acceptance Criteria
- docker-compose.yml에 Kafka 서비스가 포트 9092로 노출된다
- 각 서비스 build.gradle에 spring-kafka 의존성이 추가된다
- 각 서비스 application.yml에 Kafka 설정이 추가된다
- 프로듀서: JsonSerializer 사용
- 컨슈머: StringDeserializer 사용 (서비스별 ObjectMapper로 수동 역직렬화)

## Related Specs
- `specs/platform/event-driven-policy.md`

## Related Contracts
- `specs/contracts/events/order-events.md`
- `specs/contracts/events/payment-events.md`
- `specs/contracts/events/product-events.md`

## Edge Cases
- KAFKA_BOOTSTRAP_SERVERS 환경변수 미설정 시 localhost:9092 사용

## Failure Scenarios
- Kafka 브로커 미기동 시 컨슈머는 재시도하며 컨텍스트 기동은 실패하지 않음
- 프로듀서 발행 실패 시 try-catch로 감싸 서비스 로직에 영향 없음
