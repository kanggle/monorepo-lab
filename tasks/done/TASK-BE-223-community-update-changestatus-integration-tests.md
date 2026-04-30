# TASK-BE-223: community-service UpdatePost & ChangePostStatus 통합 테스트

## Goal

BE-220에서 구현된 두 PATCH 엔드포인트(`UpdatePostUseCase`, `ChangePostStatusUseCase`)에 대한
application integration test를 작성한다. DB 영속성, `post_status_history` append-only 기록,
권한 거부, 유효하지 않은 상태 전이를 end-to-end로 검증한다.

## Scope

- `apps/community-service/src/test/java/com/example/community/integration/` 아래 두 파일 신규 작성:
  - `UpdatePostIntegrationTest.java`
  - `ChangePostStatusIntegrationTest.java`
- 기존 코드 수정 없음 — 순수 테스트 추가

## Acceptance Criteria

### UpdatePostIntegrationTest

- [ ] 작성자가 PATCH /api/community/posts/{postId} 호출 시 200 응답, DB의 title/body/updatedAt 변경 확인
- [ ] 비작성자가 수정 시도 시 403 PERMISSION_DENIED
- [ ] 존재하지 않는 postId 수정 시도 시 404 POST_NOT_FOUND

### ChangePostStatusIntegrationTest

- [ ] DRAFT→PUBLISHED 전이 시 204, `post_status_history`에 기록 1건 (fromStatus=DRAFT, toStatus=PUBLISHED)
- [ ] PUBLISHED→DELETED 전이 시 204, `post_status_history`에 기록 추가 (fromStatus=PUBLISHED, toStatus=DELETED)
- [ ] 허용되지 않은 전이 (DELETED→PUBLISHED) 시 422 POST_STATUS_TRANSITION_INVALID
- [ ] 비작성자의 상태 전이 시도 시 403 PERMISSION_DENIED
- [ ] 존재하지 않는 postId 상태 전이 시도 시 404 POST_NOT_FOUND

## Related Specs

- `specs/services/community-service/architecture.md`
- `specs/features/account-lifecycle.md` (ActorType 참조)

## Related Contracts

- `specs/contracts/http/community-api.md` (PATCH /api/community/posts/{postId}, PATCH /api/community/posts/{postId}/status)

## Edge Cases

- `ChangePostStatusUseCase`는 outbox를 직접 발행하지 않음 — outbox 검증 불필요
- `UpdatePostUseCase`도 outbox 미발행 — DB 검증만 수행
- 테스트 격리: `UUID.randomUUID()` 기반 계정 ID 사용
- `createPublishedPost()` helper는 `AddCommentIntegrationTest`와 동일한 패턴 사용

## Failure Scenarios

- DELETED 상태 포스트에 업데이트 시도: 현재 UseCase는 status 체크를 하지 않으므로 성공 응답 (200) — 추가 검증 불필요
- 잘못된 JSON body: @Valid가 없으므로 null 필드가 전달됨 — UpdatePostUseCase의 null 처리 확인 불필요 (컨트랙트는 all-optional)
