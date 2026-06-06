---
id: TASK-BE-190
title: Fix TASK-BE-189 — security-service infrastructure·consumer 단위 테스트 파일명 UnitTest 규칙 준수
status: ready
type: TASK-BE
target_service: security-service
---

## Goal

Fix issue found in TASK-BE-189.

`platform/testing-strategy.md` Naming Conventions 기준상, infrastructure 순수 단위 테스트 클래스명은
`{ClassName}UnitTest` 이어야 한다. TASK-BE-189에서 등록된 아래 9개 파일이 `@ExtendWith(MockitoExtension.class)`
기반 순수 단위 테스트임에도 `*Test` 로 끝나 규칙을 위반하고 있다.

## Scope

아래 9개 파일의 클래스명·파일명을 `*UnitTest` 로 변경한다:

### infrastructure/redis (4개)

| 현재 파일명 | 변경 후 파일명 |
|---|---|
| `RedisEventDedupStoreTest.java` | `RedisEventDedupStoreUnitTest.java` |
| `RedisKnownDeviceStoreTest.java` | `RedisKnownDeviceStoreUnitTest.java` |
| `RedisLastKnownGeoStoreTest.java` | `RedisLastKnownGeoStoreUnitTest.java` |
| `RedisVelocityCounterTest.java` | `RedisVelocityCounterUnitTest.java` |

경로: `apps/security-service/src/test/java/com/example/security/infrastructure/redis/`

### consumer (5개)

| 현재 파일명 | 변경 후 파일명 |
|---|---|
| `LoginAttemptedConsumerTest.java` | `LoginAttemptedConsumerUnitTest.java` |
| `LoginFailedConsumerTest.java` | `LoginFailedConsumerUnitTest.java` |
| `LoginSucceededConsumerTest.java` | `LoginSucceededConsumerUnitTest.java` |
| `TokenRefreshedConsumerTest.java` | `TokenRefreshedConsumerUnitTest.java` |
| `TokenReuseDetectedConsumerTest.java` | `TokenReuseDetectedConsumerUnitTest.java` |

경로: `apps/security-service/src/test/java/com/example/security/consumer/`

파일 내 class 선언부도 각각 `*UnitTest` 로 수정한다.

## Acceptance Criteria

- 9개 파일이 `*UnitTest.java` 로 rename됨 (git mv 사용)
- 각 파일 내 public class 명도 `*UnitTest` 로 수정됨
- `./gradlew :apps:security-service:test` BUILD SUCCESSFUL, failures=0

## Related Specs

- `platform/testing-strategy.md` — Naming Conventions 섹션
- `specs/services/security-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `@DisplayName` 는 한국어 서술 내용이므로 변경 불필요
- 파일 rename 시 반드시 `git mv` 사용 (히스토리 보존)
- `SuspiciousEventPersistenceServiceTest` (application 레이어) 및 `SuspiciousEventJpaRepositoryTest` (`@DataJpaTest`)는 변경 대상 아님

## Failure Scenarios

- git mv 없이 신규 파일 생성 시 히스토리 단절
