---
id: TASK-BE-185
title: account-service application/infrastructure 계층 테스트 (5개 파일)
status: ready
type: TASK-BE
target_service: account-service
---

## Goal

account-service application/infrastructure 계층의 기존 미추적 테스트 파일 5개를 공식 태스크로 등록한다.
대상: application service (3개), infrastructure client (1개), infrastructure persistence (1개).

## Scope

- `apps/account-service/src/test/java/com/example/account/application/service/AccountSearchQueryServiceTest.java`
- `apps/account-service/src/test/java/com/example/account/application/service/AccountStatusUseCaseTest.java`
- `apps/account-service/src/test/java/com/example/account/application/service/ProfileUseCaseTest.java`
- `apps/account-service/src/test/java/com/example/account/infrastructure/client/AuthServiceClientTest.java`
- `apps/account-service/src/test/java/com/example/account/infrastructure/persistence/ProfileJpaRepositoryTest.java`

신규 프로덕션 코드 작성 없음. 기존 테스트 파일 등록만 수행.

## Acceptance Criteria

- 5개 테스트 파일이 git에 추적됨
- `./gradlew :apps:account-service:test` BUILD SUCCESSFUL
- 단위/WireMock 테스트: failures=0, errors=0
- JPA/Testcontainers 테스트: Docker 미사용 환경에서 skipped (failures=0)

## Related Specs

- `specs/services/account-service/architecture.md`

## Related Contracts

없음 (테스트 전용, 계약 변경 없음)

## Edge Cases

- `AuthServiceClientTest`: JDK HTTP/1.1 강제 설정으로 WireMock RST_STREAM 회피
- `AccountStatusUseCaseTest`: `AccountStatusMachine` 실제 인스턴스 사용 (mock 아님)
- `ProfileJpaRepositoryTest`: FK 체크 비활성화 후 profiles/accounts 순차 DELETE
- `ProfileJpaRepositoryTest`: `@EnabledIf("isDockerAvailable")` — Docker 없을 시 graceful skip

## Failure Scenarios

- WireMock 포트 충돌 시 `AuthServiceClientTest` 실패 가능 (dynamicPort() 사용으로 회피)

## Test Results (실행 시점: 2026-04-29)

| 파일 | 테스트 수 | 통과 | Skip |
|---|---|---|---|
| AccountSearchQueryServiceTest | 7 | 7 | 0 |
| AccountStatusUseCaseTest | 8 | 8 | 0 |
| ProfileUseCaseTest | 6 | 6 | 0 |
| AuthServiceClientTest | 4 | 4 | 0 |
| ProfileJpaRepositoryTest | 3 | 0 | 3 |
| **합계** | **28** | **25** | **3** |
