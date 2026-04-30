---
id: TASK-BE-183
title: auth-service infrastructure 계층 테스트 (10개 파일)
status: ready
type: TASK-BE
target_service: auth-service
---

## Goal

auth-service infrastructure 계층의 기존 미추적 테스트 파일 10개를 공식 태스크로 등록한다.
대상: client, email, redis (4개), persistence (2개), presentation (2개) 레이어.

## Scope

- `apps/auth-service/src/test/java/com/example/auth/infrastructure/client/AccountServiceClientUnitTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/email/Slf4jEmailSenderUnitTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/redis/RedisBulkInvalidationStoreTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/redis/RedisLoginAttemptCounterTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/redis/RedisPasswordResetTokenStoreTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/redis/RedisTokenBlacklistTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/persistence/CredentialJpaRepositoryTest.java`
- `apps/auth-service/src/test/java/com/example/auth/infrastructure/persistence/SocialIdentityJpaRepositoryTest.java`
- `apps/auth-service/src/test/java/com/example/auth/presentation/LogoutControllerTest.java`
- `apps/auth-service/src/test/java/com/example/auth/presentation/OAuthControllerTest.java`

신규 프로덕션 코드 작성 없음. 기존 테스트 파일 등록만 수행.

## Acceptance Criteria

- 10개 테스트 파일이 git에 추적됨
- `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL
- 단위/슬라이스 테스트: failures=0, errors=0
- JPA/Testcontainers 테스트: Docker 미사용 환경에서 skipped (failures=0)

## Related Specs

- `specs/services/auth-service/architecture.md`

## Related Contracts

없음 (테스트 전용, 계약 변경 없음)

## Edge Cases

- `AccountServiceClientUnitTest`: JDK HTTP/1.1 강제 설정으로 WireMock RST_STREAM 회피
- `Slf4jEmailSenderUnitTest`: R4 보안 규칙 — 리셋 토큰 로그 미출력, 이메일 마스킹 검증
- Redis 테스트: fail-open (getFailureCount, blacklist write) vs fail-closed (isBlacklisted, getInvalidatedAt) 동작 검증
- JPA 슬라이스 테스트: `@EnabledIf("isDockerAvailable")` — Docker 없을 시 graceful skip
- `CredentialJpaRepositoryTest`: `@BeforeEach` 에서 `jdbc.update("DELETE FROM credentials")` 로 데이터 격리

## Failure Scenarios

- WireMock 포트 충돌 시 `AccountServiceClientUnitTest` 실패 가능 (dynamicPort() 사용으로 회피)
- Redis Mockito 테스트: `opsForValue()` stub 누락 시 NullPointerException (각 테스트 별 명시적 stub)

## Test Results (실행 시점: 2026-04-29)

| 파일 | 테스트 수 | 통과 | Skip |
|---|---|---|---|
| AccountServiceClientUnitTest | 7 | 7 | 0 |
| Slf4jEmailSenderUnitTest | 4 | 4 | 0 |
| RedisBulkInvalidationStoreTest | 9 | 9 | 0 |
| RedisLoginAttemptCounterTest | 7 | 7 | 0 |
| RedisPasswordResetTokenStoreTest | 5 | 5 | 0 |
| RedisTokenBlacklistTest | 5 | 5 | 0 |
| CredentialJpaRepositoryTest | 4 | 0 | 4 |
| SocialIdentityJpaRepositoryTest | 5 | 0 | 5 |
| LogoutControllerTest | 4 | 4 | 0 |
| OAuthControllerTest | 8 | 8 | 0 |
| **합계** | **58** | **49** | **9** |
