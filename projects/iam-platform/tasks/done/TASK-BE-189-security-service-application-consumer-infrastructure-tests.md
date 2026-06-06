---
id: TASK-BE-189
title: security-service application·consumer·infrastructure 테스트 11개 등록
status: ready
type: TASK-BE
target_service: security-service
---

## Goal

security-service의 미등록 테스트 파일 11개를 태스크 시스템에 등록한다.

## Scope

| 레이어 | 파일명 | 테스트 수 | 설명 |
|---|---|---|---|
| application | `SuspiciousEventPersistenceServiceTest.java` | 3 | recordSuspiciousEvent, updateLockResult |
| consumer | `LoginAttemptedConsumerTest.java` | 3 | onMessage (정상, dedup hit, blankEventId) |
| consumer | `LoginFailedConsumerTest.java` | 3 | onMessage (noReason→FAILURE, RATE_LIMITED, dedup) |
| consumer | `LoginSucceededConsumerTest.java` | 3 | onMessage (정상, dbDuplicate, malformedJson→DLQ) |
| consumer | `TokenRefreshedConsumerTest.java` | 3 | onMessage (정상→REFRESH, detection오류흡수, blank) |
| consumer | `TokenReuseDetectedConsumerTest.java` | 3 | onMessage (정상→TOKEN_REUSE, dedup, malformed→DLQ) |
| infrastructure/persistence | `SuspiciousEventJpaRepositoryTest.java` | 4 | findByAccountIdAndDetectedAtBetween |
| infrastructure/redis | `RedisEventDedupStoreTest.java` | 6 | isDuplicate (exists/absent/null/error), markProcessed |
| infrastructure/redis | `RedisKnownDeviceStoreTest.java` | 5 | isKnown, remember |
| infrastructure/redis | `RedisLastKnownGeoStoreTest.java` | 6 | get, put |
| infrastructure/redis | `RedisVelocityCounterTest.java` | 6 | incrementAndGet, peek |

총 45개 테스트.

경로:
- `apps/security-service/src/test/java/com/example/security/application/`
- `apps/security-service/src/test/java/com/example/security/consumer/`
- `apps/security-service/src/test/java/com/example/security/infrastructure/persistence/`
- `apps/security-service/src/test/java/com/example/security/infrastructure/redis/`

## Acceptance Criteria

- 11개 파일이 git에 추가됨
- `./gradlew :apps:security-service:test` BUILD SUCCESSFUL, failures=0
- application·consumer·redis 테스트: `@ExtendWith(MockitoExtension.class)` Mockito 순수 단위 테스트
- persistence 테스트: `@DataJpaTest` + MySQL Testcontainers + `@EnabledIf("isDockerAvailable")`

## Related Specs

- `platform/testing-strategy.md` — Naming Conventions 섹션
- `specs/services/security-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- consumer 테스트의 Malformed JSON은 RuntimeException 전파 → DLQ 라우팅 검증 목적
- Redis fail-open/fail-closed 동작: isDuplicate→false(fail-open), isKnown→true(fail-open), velocity→0(fail-open)

## Failure Scenarios

- Docker 없는 환경에서 JPA persistence 테스트는 `@EnabledIf`로 skip (정상 동작)
