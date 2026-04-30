---
id: TASK-BE-200
title: auth-service integration tests — @EnabledIf 제거 및 하드코딩 WireMock 포트 동적화
status: ready
type: TASK-BE
target_service: auth-service
---

## Goal

auth-service integration test 4개에서 중복된 `@EnabledIf("isDockerAvailable")` +
`isDockerAvailable()` 메서드를 제거하고, 하드코딩된 WireMock 포트를 dynamic port로 전환한다.

`AbstractIntegrationTest`가 이미 `@ExtendWith(DockerAvailableCondition.class)`를 선언하고
있으므로 `@EnabledIf` + `isDockerAvailable()` 는 중복이다. 또한 `AuthIntegrationTest`(18082)와
`DeviceSessionIntegrationTest`(18088)의 하드코딩 포트는 CI 병렬 실행 시 `BindException` 위험이
있다.

## Scope

`apps/auth-service/src/test/java/com/example/auth/integration/`

| File | Fix |
|---|---|
| `AuthIntegrationTest.java` | `@EnabledIf` + `isDockerAvailable()` 제거, WireMock 18082 → dynamic, DynamicPropertySource URL 갱신 |
| `DeviceSessionIntegrationTest.java` | `@EnabledIf` + `isDockerAvailable()` 제거, WireMock 18088 → dynamic, DynamicPropertySource URL 갱신, Javadoc 중 `isDockerAvailable()` 언급 제거 |
| `OAuthLoginIntegrationTest.java` | `@EnabledIf` + `isDockerAvailable()` 제거 (WireMock 이미 dynamic) |
| `OutboxRelayIntegrationTest.java` | `@EnabledIf` + `isDockerAvailable()` 제거 (WireMock 없음) |

## Acceptance Criteria

- 4개 파일 모두 `@EnabledIf("isDockerAvailable")` 어노테이션 없음
- 4개 파일 모두 `isDockerAvailable()` 메서드 없음
- `AuthIntegrationTest` WireMock dynamic port 사용, `@DynamicPropertySource`에서 `wireMock::baseUrl` 사용
- `DeviceSessionIntegrationTest` WireMock dynamic port 사용, `@DynamicPropertySource`에서 `wireMock::baseUrl` 사용
- `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL, failures=0

## Related Specs

- `platform/testing-strategy.md` — "Never hardcode container host ports"
- `platform/coding-rules.md` — "Do not hard-code environment-specific values (URLs, secrets, ports)"

## Related Contracts

없음

## Edge Cases

- `AuthIntegrationTest`의 WireMock을 `@BeforeAll`에서 동적 포트로 시작하면 `@DynamicPropertySource`
  람다가 `wireMock.baseUrl()`을 참조할 때 WireMock이 이미 시작된 상태여야 한다.
  `@BeforeAll`은 `@DynamicPropertySource` 보다 먼저 호출되므로 람다(lazy) 방식이면 안전하다.

## Failure Scenarios

- 하드코딩 포트 잔존 → CI 병렬 포크에서 `BindException`
- `isDockerAvailable()` 잔존 → 중복 체크이지만 무해; 삭제 우선순위는 낮지 않음
