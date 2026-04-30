---
id: TASK-BE-188
title: membership-service SubscriptionStatusHistoryJpaRepository 테스트 등록
status: ready
type: TASK-BE
target_service: membership-service
---

## Goal

membership-service의 미등록 infrastructure persistence 테스트 파일 1개를 태스크 시스템에 등록한다.

## Scope

| 파일명 | 테스트 수 | 설명 |
|---|---|---|
| `SubscriptionStatusHistoryJpaRepositoryTest.java` | 3 | saveAndFlush (ID발급, 다중이력, 전체필드매핑) |

경로: `apps/membership-service/src/test/java/com/example/membership/infrastructure/persistence/`

## Acceptance Criteria

- 파일이 git에 추가됨
- `./gradlew :apps:membership-service:test` BUILD SUCCESSFUL, failures=0
- `@DataJpaTest` + MySQL Testcontainers + `@EnabledIf("isDockerAvailable")` 패턴 준수
- append-only 테이블 특성으로 `@BeforeEach` 없이 UUID subscriptionId 로 격리
- `Integration (infrastructure)` → `{ClassName}Test` 네이밍 준수

## Related Specs

- `platform/testing-strategy.md` — Naming Conventions, Testcontainers 섹션
- `specs/services/membership-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `SubscriptionStatusHistory` 테이블은 BEFORE DELETE trigger로 삭제 금지 — `@BeforeEach` cleanup 없이 UUID 격리로 처리

## Failure Scenarios

- Docker 없는 환경에서는 `@EnabledIf("isDockerAvailable")`로 전체 클래스 skip (정상 동작)
