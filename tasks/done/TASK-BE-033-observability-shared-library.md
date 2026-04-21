# TASK-BE-033: libs/java-observability 공통 라이브러리 — 구조화 로깅, MDC 필터, Micrometer 공통 설정

## Goal
전 서비스에서 공통으로 사용할 Observability 공통 라이브러리(`libs/java-observability`)를 구현한다.
구조화 JSON 로깅(Logback), MDC 기반 traceId 전파 필터, Micrometer 공통 메트릭 설정을 제공한다.

## Scope
- `libs/java-observability/build.gradle`: 의존성 정의 (Logback, Micrometer, OpenTelemetry API)
- Logback 구조화 JSON 로깅 설정 (`logback-spring.xml` base 제공)
  - production 프로파일: JSON 포맷 (timestamp, level, service, traceId, message)
  - local 프로파일: human-readable 포맷
- MDC 기반 traceId 전파 서블릿 필터
  - OpenTelemetry trace context에서 traceId를 MDC에 주입
  - 요청 종료 시 MDC 정리
- Micrometer 공통 태그 자동 설정 (service name)
- 공통 HTTP 요청 메트릭 설정 헬퍼

## Acceptance Criteria
- `libs/java-observability/build.gradle`에 필요한 의존성이 정의된다
- Logback JSON 포맷 설정 파일이 제공된다
  - production: 구조화 JSON (필수 필드: timestamp, level, service, traceId, message)
  - local: 콘솔 human-readable 포맷
- MDC traceId 전파 필터가 제공된다
  - OpenTelemetry Span에서 traceId를 추출하여 MDC에 설정
  - 필터 체인 완료 후 MDC를 정리
- Micrometer 공통 태그 자동 설정이 `@AutoConfiguration`으로 제공된다
- 비밀번호, 토큰, 카드번호, PII가 로그에 포함되지 않도록 패턴 마스킹 가이드가 주석으로 제공된다
- 단위 테스트가 포함된다

## Related Specs
- `specs/platform/observability.md`
- `specs/platform/shared-library-policy.md`

## Related Contracts
- 없음 (공통 라이브러리, API 계약 변경 없음)

## Edge Cases
- OpenTelemetry가 설정되지 않은 환경에서도 MDC 필터가 에러 없이 동작해야 한다 (traceId를 "N/A" 또는 빈 값으로 설정)
- 서비스별 logback-spring.xml이 이미 존재하는 경우 공통 설정을 include하는 방식으로 확장 가능해야 한다

## Failure Scenarios
- OpenTelemetry 라이브러리가 classpath에 없을 경우 MDC 필터가 graceful하게 동작 (traceId 없이 로깅)
- Micrometer가 classpath에 없을 경우 자동 설정이 활성화되지 않음 (`@ConditionalOnClass`)
