# TASK-BE-203: AdminActionJpaRepositoryTest / AdminRefreshTokenJpaAdapterTest MySQLContainer 누락된 withStartupTimeout 추가

## Goal

TASK-BE-202 리뷰에서 발견된 결함을 수정한다.
admin-service의 두 `@DataJpaTest` 파일에서 `MySQLContainer` 선언에
`.withStartupTimeout(Duration.ofMinutes(3))`이 빠져 있어 느린 CI 환경에서
`ContainerLaunchException: ... timed out` 이 발생할 수 있다.

## Scope

- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/AdminActionJpaRepositoryTest.java`
  - `MySQLContainer` 체인에 `.withStartupTimeout(Duration.ofMinutes(3))` 추가
- `apps/admin-service/src/test/java/com/example/admin/infrastructure/persistence/AdminRefreshTokenJpaAdapterTest.java`
  - `MySQLContainer` 체인에 `.withStartupTimeout(Duration.ofMinutes(3))` 추가

## Acceptance Criteria

- [ ] 두 파일의 `MySQLContainer` 선언에 `.withStartupTimeout(Duration.ofMinutes(3))`이 존재함
- [ ] `./gradlew :apps:admin-service:test` BUILD SUCCESSFUL

## Related Specs

- `platform/testing-strategy.md` (Wait Strategy and Startup Timeout 섹션)

## Related Contracts

없음

## Edge Cases

- `Duration`은 이미 두 파일 모두 import되어 있으므로 추가 import 불필요

## Failure Scenarios

- `.withStartupTimeout` 누락 상태에서는 CI 환경에 따라 간헐적으로만 실패하므로, 로컬에서는 재현이 어려울 수 있음
