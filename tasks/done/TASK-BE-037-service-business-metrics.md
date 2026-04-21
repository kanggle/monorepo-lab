# TASK-BE-037: 전 서비스 비즈니스 메트릭 구현 — Micrometer Counter/Histogram 등록

## Goal
auth-service(TASK-BE-035)에서 완료한 비즈니스 메트릭 패턴을 나머지 5개 서비스(gateway, order, payment, product, search)에 동일하게 적용한다.
각 서비스의 observability 스펙에 정의된 비즈니스 메트릭을 Micrometer를 사용하여 구현한다.

## Scope
- `gateway-service`: `specs/services/gateway-service/observability.md`에 정의된 4개 메트릭 구현
- `order-service`: `specs/services/order-service/observability.md`에 정의된 5개 메트릭 구현
- `payment-service`: `specs/services/payment-service/observability.md`에 정의된 5개 메트릭 구현
- `product-service`: `specs/services/product-service/observability.md`에 정의된 5개 메트릭 구현
- `search-service`: `specs/services/search-service/observability.md`에 정의된 5개 메트릭 구현
- 각 서비스에서 적절한 위치(application service, filter, event handler 등)에 MeterRegistry를 주입하여 메트릭 기록
- auth-service의 기존 구현 패턴(TASK-BE-035)을 참고하여 일관된 방식 적용

## Acceptance Criteria
- 각 서비스의 observability 스펙에 정의된 모든 메트릭이 Micrometer로 등록된다
- `GET /actuator/prometheus`에서 정의된 비즈니스 메트릭이 노출된다
- Counter 메트릭은 관련 비즈니스 이벤트 발생 시 정확히 증가한다
- Histogram 메트릭(search_query_duration_seconds)은 실제 소요 시간을 기록한다
- 메트릭 태그(label)가 스펙에 맞게 설정된다 (예: reason, type, from/to 등)
- 각 메트릭에 대한 단위 테스트가 포함된다

## Related Specs
- `specs/platform/observability.md`
- `specs/services/gateway-service/observability.md`
- `specs/services/order-service/observability.md`
- `specs/services/payment-service/observability.md`
- `specs/services/product-service/observability.md`
- `specs/services/search-service/observability.md`

## Related Contracts
- 없음 (내부 메트릭 구현, API 계약 변경 없음)

## Edge Cases
- gateway-service는 WebFlux 기반이므로 WebFilter에서 메트릭을 기록해야 한다
- search-service의 Histogram은 Elasticsearch 쿼리 시간만 측정해야 한다 (전체 HTTP 응답 시간과 구분)
- 이벤트 핸들러에서 메트릭 기록 시 예외가 발생해도 비즈니스 로직에 영향을 주지 않아야 한다

## Failure Scenarios
- MeterRegistry 주입 실패 시 서비스 기동에 영향 없어야 한다
- 메트릭 기록 중 예외 발생 시 비즈니스 로직은 정상 수행되어야 한다
