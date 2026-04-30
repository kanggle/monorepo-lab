# TASK-BE-225: community-service GetPost MEMBERS_ONLY 통합 테스트

## Goal

`architecture.md` 필수 시나리오 "MEMBERS_ONLY 포스트를 FREE 팬이 조회 시 403" 및
"membership-service 503 시 fail-closed (403)"에 대한 integration test를 `GetPost` 엔드포인트
기준으로 작성한다. 현재 `AddCommentIntegrationTest`에서 댓글 흐름 기준으로만 검증되어 있으며,
`GetPostUseCase` 자체에는 end-to-end 통합 테스트가 없다.

## Scope

- `apps/community-service/src/test/java/com/example/community/integration/GetPostIntegrationTest.java` 신규 작성
- 기존 코드 수정 없음

## Acceptance Criteria

- [ ] PUBLIC 포스트 조회 시 200 응답 및 필드 확인 (postId, type, visibility, status, body, commentCount, reactionCount)
- [ ] MEMBERS_ONLY 포스트를 FREE 팬이 조회 시 403 MEMBERSHIP_REQUIRED
- [ ] membership-service 503 응답 시 fail-closed → 403 MEMBERSHIP_REQUIRED
- [ ] DELETED 포스트 조회 시 404 POST_NOT_FOUND
- [ ] 존재하지 않는 postId 조회 시 404 POST_NOT_FOUND

## Related Specs

- `specs/services/community-service/architecture.md` (필수 시나리오)
- `specs/contracts/http/community-api.md` (GET /api/community/posts/{postId})
- `specs/contracts/http/internal/community-to-membership.md`

## Related Contracts

- `specs/contracts/http/community-api.md`

## Edge Cases

- PUBLIC 포스트는 MEMBERSHIP_WM 스터빙 불필요 (ContentAccessChecker가 호출되지 않음)
- MEMBERS_ONLY 포스트는 MEMBERSHIP_WM의 `/internal/membership/access` 엔드포인트 스터빙 필요
- `GetPostUseCase`가 DRAFT 상태 포스트도 조회 가능 여부 확인 (404 vs 200)
- 테스트 격리: UUID 기반 계정/포스트 ID 사용

## Failure Scenarios

- membership-service가 503 일 때 Circuit Breaker 동작: fail-closed → 403
