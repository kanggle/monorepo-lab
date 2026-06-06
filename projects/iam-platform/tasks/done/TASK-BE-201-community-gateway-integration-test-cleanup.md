# TASK-BE-201: community-service + gateway-service 통합 테스트 정리

## Goal

TASK-BE-200(auth-service 정리)에 이어, community-service와 gateway-service 통합 테스트에서
중복·불필요한 Docker 가용성 체크를 제거하고, community-service의 hardcoded WireMock 포트를
dynamic port로 교체한다.

## Scope

**community-service:**
- `apps/community-service/src/test/java/com/example/community/integration/CommunityIntegrationTestBase.java`
  - `@EnabledIf("isDockerAvailable")` 제거 — `AbstractIntegrationTest` 상속으로 이미 커버됨
  - `isDockerAvailable()` 정적 메서드 제거
  - WireMock 고정 포트(18083, 18084) → `WireMockConfiguration.options().dynamicPort()`
  - `@DynamicPropertySource` 하드코딩 URL → `MEMBERSHIP_WM::baseUrl`, `ACCOUNT_WM::baseUrl`

**gateway-service (AbstractIntegrationTest 미상속):**
- `apps/gateway-service/src/test/java/com/example/gateway/integration/GatewayIntegrationTest.java`
- `apps/gateway-service/src/test/java/com/example/gateway/integration/GatewayResilienceIntegrationTest.java`
- `apps/gateway-service/src/test/java/com/example/gateway/integration/GatewayRateLimitIntegrationTest.java`
  - `@EnabledIf("isDockerAvailable")` 제거
  - `isDockerAvailable()` 정적 메서드 제거
  - `@ExtendWith(DockerAvailableCondition.class)` 추가 (AbstractIntegrationTest를 상속하지 않으므로 명시적으로 추가)

## Acceptance Criteria

- [ ] `CommunityIntegrationTestBase`에 `@EnabledIf` 및 `isDockerAvailable()` 없음
- [ ] `CommunityIntegrationTestBase` WireMock이 dynamic port 사용
- [ ] `CommunityIntegrationTestBase` `@DynamicPropertySource`가 `MEMBERSHIP_WM::baseUrl`, `ACCOUNT_WM::baseUrl` 사용
- [ ] 3개 gateway 통합 테스트에 `@EnabledIf` 및 `isDockerAvailable()` 없음
- [ ] 3개 gateway 통합 테스트에 `@ExtendWith(DockerAvailableCondition.class)` 존재
- [ ] `./gradlew :apps:community-service:test` BUILD SUCCESSFUL
- [ ] `./gradlew :apps:gateway-service:test` BUILD SUCCESSFUL

## Related Specs

- `platform/testing-strategy.md`

## Related Contracts

없음

## Edge Cases

- `CommunityIntegrationTestBase`는 여러 서브클래스가 상속하므로 `startWireMock()`의 `isRunning()` 가드는 유지

## Failure Scenarios

- dynamic port 전환 후 `@DynamicPropertySource` 람다 평가 타이밍 문제 → 이전 auth-service 패턴과 동일, 검증된 패턴
