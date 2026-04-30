---
id: TASK-BE-158
title: community-service JPA 리포지토리 슬라이스 테스트
status: ready
priority: medium
assignee: ""
---

## Goal

community-service 내 JPA 리포지토리 5개에 대한 쿼리 슬라이스 테스트를 작성하여
커스텀 쿼리(`@Query` JPQL 서브쿼리, `countBy`, `existsBy`, `findByOrderByAsc` 등)가
실제 MySQL 스키마에서 올바르게 동작함을 검증한다.

## Scope

대상 리포지토리:
- `PostJpaRepository` — findFeedForFan(@Query + FeedSubscription 서브쿼리, Pageable)
- `CommentJpaRepository` — countByPostIdAndDeletedAtIsNull, countsGroupedByPostId(@Query projection)
- `ReactionJpaRepository` — findByPostIdAndAccountId, countByPostId, countsGroupedByPostId(@Query projection)
- `FeedSubscriptionJpaRepository` — findByFanAccountIdAndArtistAccountId, existsByFanAccountIdAndArtistAccountId
- `PostStatusHistoryJpaRepository` — findByPostIdOrderByOccurredAtAsc

## Acceptance Criteria

- [ ] 5개 클래스 각각 `@DataJpaTest + @Testcontainers + @EnabledIf("isDockerAvailable")` 패턴 준수
- [ ] `withDatabaseName("community_db")` + Flyway 마이그레이션으로 실제 스키마 적용
- [ ] `findFeedForFan` 피드 쿼리 테스트: 발행 포스트 반환, DRAFT/미구독 아티스트 제외 검증
- [ ] 소프트 삭제(`deletedAt IS NULL`) 필터 검증: 댓글 카운트에서 soft-deleted 제외 확인
- [ ] Projection 기반 `countsGroupedByPostId` — 복수 postId 기준 그룹별 카운트 검증
- [ ] `post_status_history` append-only 테이블: 삭제 대신 고유 postId 사용, 오름차순 정렬 검증
- [ ] 테스트 메서드 네이밍: `{scenario}_{condition}_{expectedResult}`
- [ ] `@DisplayName` 한국어 비즈니스 설명
- [ ] `compileTestJava` 성공

## Related Specs

- `specs/services/community-service/architecture.md`

## Related Contracts

없음 (DB 내부 쿼리 검증)

## Edge Cases

- `findFeedForFan` 구독 없는 팬 → 빈 피드
- `findFeedForFan` DRAFT 상태 포스트 → 제외
- `countsGroupedByPostId` soft-deleted 댓글 제외
- `PostStatusHistoryJpaRepository` append-only 트리거로 인한 DELETE 불가 → unique postId로 격리

## Failure Scenarios

- Docker 미사용 환경: `@EnabledIf("isDockerAvailable")` 로 자동 스킵
- `post_status_history` 에 `deleteAll()` 호출 시 append-only 트리거 발화 → `@BeforeEach` 없이 unique postId로 격리
