---
id: TASK-BE-175
title: "admin-service AccountServiceClient·SecurityServiceClient 기본 HTTP 동작 단위 테스트"
status: ready
type: BE
service: admin-service
priority: medium
---

## Goal

`AccountServiceClient`와 `SecurityServiceClient`의 기본 HTTP 동작(200/4xx/5xx/네트워크 오류)을 커버하는 단위 테스트를 작성한다.

기존 `AccountServiceClientResilienceTest`는 lock POST 경로에 대한 Resilience4j retry/CB 동작만 검증한다.
기존 `SecurityServiceClientCircuitBreakerTest`는 CB 상태 전이만 검증한다.
두 클래스 모두 HTTP 프로토콜 디스패치(200 → 성공 객체, 4xx → NonRetryable, 5xx → DownstreamFailure, 네트워크 오류 → DownstreamFailure)에 대한 직접 단위 테스트가 없다.

## Scope

- `apps/admin-service/src/test/java/com/example/admin/infrastructure/client/AccountServiceClientUnitTest.java` (신규)
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/client/SecurityServiceClientUnitTest.java` (신규)

## Acceptance Criteria

- `AccountServiceClientUnitTest`:
  - search 200 → AccountSearchResponse 반환
  - search 5xx → DownstreamFailureException
  - getDetail 4xx → NonRetryableDownstreamException
  - lock 200 → LockResponse 반환
  - lock 4xx → NonRetryableDownstreamException
  - lock 5xx → DownstreamFailureException
  - 네트워크 오류 → DownstreamFailureException

- `SecurityServiceClientUnitTest`:
  - queryLoginHistory 200 → LoginHistoryEntry 리스트 반환
  - querySuspiciousEvents 200 → SuspiciousEventEntry 리스트 반환
  - queryLoginHistory 4xx → NonRetryableDownstreamException
  - queryLoginHistory 5xx → DownstreamFailureException
  - 네트워크 오류 → DownstreamFailureException

- 모든 테스트 통과
- `./gradlew :apps:admin-service:test` 성공

## Related Specs

- `specs/contracts/http/internal/admin-to-account.md`
- `specs/services/admin-service/architecture.md`

## Related Contracts

- `specs/contracts/http/internal/admin-to-account.md`

## Edge Cases

- `AccountServiceClient`는 생성자에서 `HttpClient.Version.HTTP_1_1`을 명시적으로 설정 → WireMock H2C 처리 불필요
- `SecurityServiceClient`는 HTTP 버전 미지정(기본값 HTTP_2) → plain HTTP 연결에서 h2c upgrade 없이 HTTP/1.1로 동작 (CircuitBreakerTest에서 동일 패턴 검증됨)
- `Instant` 필드 직렬화: `Jackson2ObjectMapperBuilder`가 classpath의 `JavaTimeModule`을 자동 감지하므로 ISO-8601 문자열 사용 가능

## Failure Scenarios

- WireMock `Fault.EMPTY_RESPONSE` → `DownstreamFailureException` (not NonRetryable)
- 5xx → `DownstreamFailureException` (not `NonRetryableDownstreamException`)
- 4xx → `NonRetryableDownstreamException`
