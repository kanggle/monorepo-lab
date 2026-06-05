---
id: TASK-BE-184
title: Fix TASK-BE-183 — auth-service Redis 단위 테스트 파일명 UnitTest 규칙 준수
status: ready
type: TASK-BE
target_service: auth-service
---

## Goal

Fix issue found in TASK-BE-183.

`platform/testing-strategy.md` Naming Conventions 기준상, infrastructure 순수 단위 테스트 클래스명은
`{ClassName}UnitTest` 이어야 한다. TASK-BE-183에서 등록된 Redis 테스트 4개가 `*Test` 로 끝나 규칙을
위반하고 있다.

## Scope

아래 4개 파일의 클래스명·파일명을 `*UnitTest` 로 변경한다:

| 현재 파일명 | 변경 후 파일명 |
|---|---|
| `RedisBulkInvalidationStoreTest.java` | `RedisBulkInvalidationStoreUnitTest.java` |
| `RedisLoginAttemptCounterTest.java` | `RedisLoginAttemptCounterUnitTest.java` |
| `RedisPasswordResetTokenStoreTest.java` | `RedisPasswordResetTokenStoreUnitTest.java` |
| `RedisTokenBlacklistTest.java` | `RedisTokenBlacklistUnitTest.java` |

경로: `apps/auth-service/src/test/java/com/example/auth/infrastructure/redis/`

내부 클래스명 및 `@DisplayName` 은 변경하지 않아도 되나, 클래스 선언부의 class 이름은 파일명과 일치해야 한다.

## Acceptance Criteria

- 4개 파일이 `*UnitTest.java` 로 rename됨 (git mv 사용)
- 각 파일 내 public class 명도 `*UnitTest` 로 수정됨
- `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL, failures=0

## Related Specs

- `platform/testing-strategy.md` — Naming Conventions 섹션
- `specs/services/auth-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `@DisplayName` 는 한국어 서술 내용이므로 변경 불필요
- 파일 rename 시 반드시 `git mv` 사용 (히스토리 보존)

## Failure Scenarios

- git mv 없이 신규 파일 생성 시 히스토리 단절
