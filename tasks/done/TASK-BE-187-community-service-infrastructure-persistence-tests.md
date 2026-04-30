---
id: TASK-BE-187
title: community-service infrastructure persistence 테스트 등록
status: ready
type: TASK-BE
target_service: community-service
---

## Goal

community-service의 미등록 infrastructure persistence 테스트 파일 5개를 태스크 시스템에 등록한다.
해당 파일들은 이미 작성·검증된 상태이며, 이 태스크는 태스크 시스템 내 이력 등록이 목적이다.

## Scope

아래 5개 파일을 등록한다:

| 파일명 | 테스트 수 | 설명 |
|---|---|---|
| `CommentJpaRepositoryTest.java` | 4 | countByPostIdAndDeletedAtIsNull, countsGroupedByPostId |
| `FeedSubscriptionJpaRepositoryTest.java` | 4 | findByFanAccountIdAndArtistAccountId, existsByFanAccountIdAndArtistAccountId |
| `PostJpaRepositoryTest.java` | 4 | findFeedForFan (구독 피드 쿼리) |
| `PostStatusHistoryJpaRepositoryTest.java` | 3 | findByPostIdOrderByOccurredAtAsc |
| `ReactionJpaRepositoryTest.java` | 4 | findByPostIdAndAccountId, countByPostId, countsGroupedByPostId |

경로: `apps/community-service/src/test/java/com/example/community/infrastructure/persistence/`

## Acceptance Criteria

- 5개 파일이 git에 추가됨
- `./gradlew :apps:community-service:test` BUILD SUCCESSFUL, failures=0
- 모든 테스트 클래스가 `@DataJpaTest` + MySQL Testcontainers + `@EnabledIf("isDockerAvailable")` 패턴 준수
- 네이밍 규칙: `Integration (infrastructure)` → `{ClassName}Test` 준수

## Related Specs

- `platform/testing-strategy.md` — Naming Conventions, Testcontainers 섹션
- `specs/services/community-service/architecture.md`

## Related Contracts

없음

## Edge Cases

- `PostStatusHistoryJpaRepositoryTest`는 append-only 테이블 특성으로 `@BeforeEach` cleanup 없음 — postId UUID로 격리
- Docker 미사용 환경에서는 `@EnabledIf("isDockerAvailable")`로 전체 클래스 skip

## Failure Scenarios

- Docker 없는 환경에서 Testcontainers 직접 실행 시 skip (정상 동작)
