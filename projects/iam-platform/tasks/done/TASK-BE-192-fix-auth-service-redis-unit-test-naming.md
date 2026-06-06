---
id: TASK-BE-192
title: auth-service infrastructure/redis 단위 테스트 파일명 UnitTest 규칙 준수
status: ready
type: TASK-BE
target_service: auth-service
---

## Goal

`platform/testing-strategy.md` Naming Conventions 기준상, infrastructure 순수 단위 테스트 클래스명은
`{ClassName}UnitTest` 이어야 한다. 아래 3개 파일이 `@ExtendWith(MockitoExtension.class)`
기반 순수 단위 테스트임에도 `*Test` 로 끝나 규칙을 위반하고 있다.

## Scope

아래 3개 파일의 클래스명·파일명을 `*UnitTest` 로 변경한다:

### infrastructure/redis (3개)

| 현재 파일명 | 변경 후 파일명 |
|---|---|
| `RedisAccessTokenInvalidationStoreTest.java` | `RedisAccessTokenInvalidationStoreUnitTest.java` |
| `RedisOAuthStateStoreTest.java` | `RedisOAuthStateStoreUnitTest.java` |
| `RedisPasswordResetAttemptCounterTest.java` | `RedisPasswordResetAttemptCounterUnitTest.java` |

경로: `apps/auth-service/src/test/java/com/example/auth/infrastructure/redis/`

파일 내 class 선언부도 각각 `*UnitTest` 로 수정한다.

## Acceptance Criteria

- 3개 파일이 `*UnitTest.java` 로 rename됨 (git mv 사용)
- 각 파일 내 class 명도 `*UnitTest` 로 수정됨
- `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL, failures=0

## Related Specs

- `platform/testing-strategy.md` — Naming Conventions 섹션
- `specs/services/auth-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `@DisplayName` 는 한국어 서술 내용이므로 변경 불필요
- 파일 rename 시 반드시 `git mv` 사용 (히스토리 보존)
- 같은 패키지에 이미 올바른 `*UnitTest` 파일이 존재하므로 혼동 주의

## Failure Scenarios

- git mv 없이 신규 파일 생성 시 히스토리 단절
