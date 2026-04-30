# TASK-BE-224: community-service AddReaction dedup + GetFeed 통합 테스트

## Goal

`architecture.md` 필수 시나리오 중 현재 integration test로 미검증된 두 가지를 커버한다:
- "계정당 포스트당 반응 1개 제한" — `AddReactionUseCase`의 upsert dedup을 DB 레벨에서 검증
- "삭제 포스트 피드 미노출" — `GetFeedUseCase`의 페이지네이션 결과에서 DELETED 포스트 제외 확인

## Scope

- `apps/community-service/src/test/java/com/example/community/integration/` 아래 두 파일 신규 작성:
  - `AddReactionIntegrationTest.java`
  - `GetFeedIntegrationTest.java`
- 기존 코드 수정 없음

## Acceptance Criteria

### AddReactionIntegrationTest

- [ ] 동일 계정이 동일 포스트에 같은 emojiCode로 두 번 반응 시 → DB reaction 행 1개 유지 (upsert)
- [ ] 동일 계정이 동일 포스트에 다른 emojiCode로 반응 교체 시 → DB reaction 행 1개, emojiCode 변경 확인
- [ ] 반응 추가 시 200 응답, `community.reaction.added` outbox 행 생성

### GetFeedIntegrationTest

- [ ] PUBLISHED 포스트는 피드에 포함됨 (팔로잉 아티스트 기준)
- [ ] DELETED 포스트는 피드에서 미노출
- [ ] HIDDEN 포스트는 피드에서 미노출
- [ ] 팔로우하지 않은 아티스트의 포스트는 피드에 미포함
- [ ] GET /api/community/feed?page=0&size=20 → 200 응답, content 배열 + pagination 필드 존재

## Related Specs

- `specs/services/community-service/architecture.md` (필수 시나리오)
- `specs/contracts/http/community-api.md` (GET /api/community/feed, POST /api/community/posts/{postId}/reactions)

## Related Contracts

- `specs/contracts/http/community-api.md`

## Edge Cases

- `AddReactionUseCase`: `Reaction.upsert()` 또는 `ReactionRepository.upsertReaction()` 패턴 사용 여부 확인 필요
- `GetFeedUseCase`: `FeedSubscription` 테이블에 팔로우 관계 등록 필요, `Post` 의 `deletedAt` 또는 `status` 기반 필터링
- 테스트 격리: UUID 기반 계정 ID 사용, 각 테스트마다 독립적인 포스트/팔로우 관계 생성

## Failure Scenarios

- MEMBERS_ONLY 포스트의 피드 locked 필드: 멤버십 확인이 필요하므로 WireMock 스터빙 필요할 수 있음
