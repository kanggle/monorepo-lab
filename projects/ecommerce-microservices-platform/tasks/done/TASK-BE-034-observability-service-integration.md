# TASK-BE-034: 전 서비스 Observability 적용 — 의존성, 트레이싱, 메트릭 노출, Health Check 표준화

## Goal
모든 활성 서비스(auth, gateway, order, payment, product, search)에 `libs/java-observability` 의존성을 추가하고,
OpenTelemetry 트레이싱, Prometheus 메트릭 엔드포인트, Health Check를 플랫폼 스펙에 맞게 표준화한다.
docker-compose에 Jaeger와 Prometheus 인프라를 추가한다.

## Scope
- 각 서비스 `build.gradle`: `java-observability` 의존성 추가, OpenTelemetry/Micrometer 의존성 추가
- 각 서비스 `application.yml`:
  - Actuator 엔드포인트 노출: `health`, `info`, `prometheus`
  - Health Check: liveness/readiness 그룹 설정 (DB, Redis 등 서비스별 의존 인프라 반영)
  - Micrometer Prometheus 레지스트리 활성화
  - OpenTelemetry 트레이싱 설정 (서비스 이름, exporter endpoint)
- 각 서비스 `logback-spring.xml`: 공통 라이브러리의 JSON 로깅 설정 적용
- `docker-compose.yml`: Jaeger, Prometheus 컨테이너 추가
- Prometheus `prometheus.yml` 스크래핑 설정 (각 서비스 `/actuator/prometheus` 타겟)

## Acceptance Criteria
- 모든 활성 서비스가 `libs/java-observability`에 의존한다
- 모든 서비스가 `GET /actuator/health`에서 200을 반환한다
  - liveness: 프로세스 생존 확인
  - readiness: DB + 캐시(해당 시) 연결 확인
- 모든 서비스가 `GET /actuator/prometheus`에서 Prometheus 포맷 메트릭을 노출한다
  - 필수 메트릭: `http_requests_total`, `http_request_duration_seconds`, `jvm_memory_used_bytes`, `db_connection_pool_active`
- 모든 서비스에 OpenTelemetry API 의존성과 MDC 트레이싱 필터가 적용된다
  - 실제 trace header 전파(`traceparent`, `tracestate`) 및 span 생성은 TASK-BE-036(Java Agent 적용) 완료 후 동작한다
- 모든 서비스의 로그에 `traceId`가 MDC를 통해 포함될 수 있는 구조가 적용된다
- docker-compose에 Jaeger(포트 16686)와 Prometheus(포트 9090)가 추가된다
- Jaeger/Prometheus 인프라가 정상 기동되며, 각 서비스의 span 조회는 TASK-BE-036 완료 후 검증한다

## Related Specs
- `specs/platform/observability.md`
- `specs/platform/shared-library-policy.md`
- `specs/services/auth-service/architecture.md`
- `specs/services/gateway-service/architecture.md`
- `specs/services/order-service/architecture.md`
- `specs/services/payment-service/architecture.md`
- `specs/services/product-service/architecture.md`
- `specs/services/search-service/architecture.md`

## Related Tasks
- `TASK-BE-036`: OpenTelemetry Java Agent docker-compose 설정. 본 태스크는 라이브러리 의존성, Actuator 설정, 로깅 표준화를 담당하며, 실제 분산 트레이싱 동작(trace header 자동 전파, span 생성, Jaeger 조회)은 TASK-BE-036의 Java Agent 적용 후 완전히 동작한다.

## Related Contracts
- 없음 (인프라 설정, API 계약 변경 없음)

## Edge Cases
- gateway-service는 WebFlux 기반이므로 서블릿 필터 대신 WebFilter를 사용해야 한다
- search-service는 DB 대신 Elasticsearch health check를 readiness에 포함해야 한다
- 서비스별 기존 Actuator 설정이 있는 경우 덮어쓰지 않고 확장한다

## Failure Scenarios
- Jaeger 미기동 시 트레이싱 exporter가 실패해도 서비스 기동에 영향 없어야 한다
- Prometheus 미기동 시 메트릭 수집이 중단되지만 서비스 정상 동작해야 한다
- OpenTelemetry agent 미설정 시에도 서비스가 정상 기동된다
