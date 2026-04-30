# TASK-BE-226: gateway-service rate-limit Micrometer 메트릭 추가

## Goal

`RateLimitFilter`와 `TokenBucketRateLimiter`에 Micrometer 카운터를 추가한다.
현재 rate-limit 허용/거부 결과에 대한 메트릭이 전혀 없어 공격 탐지 및 임계치 조정이
불가능하다. `gateway_ratelimit_total{scope, result}` 카운터를 추가한다.

## Scope

- `apps/gateway-service/src/main/java/com/example/gateway/filter/RateLimitFilter.java` 수정
- `apps/gateway-service/src/test/java/` 관련 테스트 추가 또는 수정

## Acceptance Criteria

- [ ] scope-specific 제한 허용 시 `gateway_ratelimit_total{scope=login|signup|refresh, result=allowed}` 증가
- [ ] scope-specific 제한 거부 시 `gateway_ratelimit_total{scope=..., result=rejected}` 증가
- [ ] global 제한 허용 시 `gateway_ratelimit_total{scope=global, result=allowed}` 증가
- [ ] global 제한 거부 시 `gateway_ratelimit_total{scope=global, result=rejected}` 증가
- [ ] scope가 null(non-rate-limited path)인 경우에도 global 결과는 기록됨
- [ ] Redis 오류로 fail-open 처리 시에도 카운터 증가 (result=allowed)
- [ ] 기존 rate-limit 동작 변경 없음

## Related Specs

- `specs/features/rate-limiting.md` (메트릭 요구사항: `gateway_ratelimit_total{scope, result}`)

## Related Contracts

- 없음 (내부 관측성)

## Edge Cases

- scope=null 경로: global 카운터만 증가, scope-specific 카운터는 증가하지 않음
- Redis 장애 + fail-open: result=allowed 카운터 증가
- Redis 장애 + fail-closed: result=rejected 카운터 증가

## Failure Scenarios

- MeterRegistry bean 없음: Spring Boot Actuator가 이미 classpath에 있으므로 발생하지 않음
