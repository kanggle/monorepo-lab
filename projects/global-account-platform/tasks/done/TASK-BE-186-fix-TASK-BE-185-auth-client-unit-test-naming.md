---
id: TASK-BE-186
title: Fix TASK-BE-185 — account-service AuthServiceClient 단위 테스트 파일명 UnitTest 규칙 준수
status: ready
type: TASK-BE
target_service: account-service
---

## Goal

Fix issue found in TASK-BE-185.

`platform/testing-strategy.md` Naming Conventions 기준상, infrastructure 순수 단위 테스트 클래스명은
`{ClassName}UnitTest` 이어야 한다. TASK-BE-185에서 등록된 `AuthServiceClientTest`가 WireMock 기반
순수 단위 테스트임에도 `*Test` 로 끝나 규칙을 위반하고 있다.

## Scope

아래 1개 파일의 클래스명·파일명을 `*UnitTest` 로 변경한다:

| 현재 파일명 | 변경 후 파일명 |
|---|---|
| `AuthServiceClientTest.java` | `AuthServiceClientUnitTest.java` |

경로: `apps/account-service/src/test/java/com/example/account/infrastructure/client/`

파일 내 class 선언부도 `AuthServiceClientUnitTest` 로 수정한다.

## Acceptance Criteria

- 파일이 `AuthServiceClientUnitTest.java` 로 rename됨 (git mv 사용)
- 파일 내 public class 명도 `AuthServiceClientUnitTest` 로 수정됨
- `./gradlew :apps:account-service:test` BUILD SUCCESSFUL, failures=0

## Related Specs

- `platform/testing-strategy.md` — Naming Conventions 섹션
- `specs/services/account-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `@DisplayName` 는 한국어 서술 내용이므로 변경 불필요
- 파일 rename 시 반드시 `git mv` 사용 (히스토리 보존)

## Failure Scenarios

- git mv 없이 신규 파일 생성 시 히스토리 단절
