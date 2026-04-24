# TASK-BE-036: docker-compose OpenTelemetry Java Agent 설정 — 트레이싱 전파 및 Jaeger 연동

## Goal
TASK-BE-034 리뷰에서 발견된 누락 사항을 수정한다.
docker-compose의 각 서비스에 OpenTelemetry Java Agent를 추가하여 분산 트레이싱(trace header 전파, Span 생성, Jaeger 조회)이 실제로 동작하도록 한다.

## Scope
- OpenTelemetry Java Agent JAR 다운로드 방식 결정 (Dockerfile 빌드 시 다운로드 또는 볼륨 마운트)
- 각 서비스 Dockerfile 또는 docker-compose.yml에 `-javaagent` JVM 인수 추가
- `OTEL_EXPORTER_OTLP_ENDPOINT`, `OTEL_SERVICE_NAME` 환경변수 검증 (기존 설정 활용)
- `OTEL_TRACES_EXPORTER=otlp` 설정 추가
- 트레이싱 동작 검증: Jaeger UI에서 서비스별 Span 조회 가능 확인

## Acceptance Criteria
- 모든 서비스가 OpenTelemetry Java Agent와 함께 기동된다
- HTTP 요청 시 `traceparent`, `tracestate` 헤더가 자동 전파된다
- 각 서비스의 Span이 Jaeger UI(http://localhost:16686)에서 조회 가능하다
- MDC의 `traceId`가 실제 OpenTelemetry trace ID 값으로 채워진다
- OTel Agent 추가 후에도 기존 서비스 기동 시간이 크게 증가하지 않는다

## Related Specs
- `specs/platform/observability.md`

## Related Contracts
- 없음 (인프라 설정, API 계약 변경 없음)

## Edge Cases
- OTel Agent JAR 다운로드 실패 시 서비스 기동이 차단되지 않도록 fallback 고려
- gateway-service(WebFlux)에서 OTel Agent의 WebFlux 자동 계측이 정상 동작하는지 확인
- Jaeger 미기동 시에도 서비스 정상 기동 확인 (기존 Failure Scenario 유지)

## Failure Scenarios
- OTel Agent JAR 파일 경로 오류 시 JVM 기동 실패 → 경로 검증 필요
- Jaeger 미기동 시 exporter 실패하지만 서비스는 정상 동작
- Agent 버전과 서비스 Java 버전 호환성 확인 (Java 21)
