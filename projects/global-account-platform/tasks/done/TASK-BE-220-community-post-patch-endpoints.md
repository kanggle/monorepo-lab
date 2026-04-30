---
id: TASK-BE-220
title: community-service PostController PATCH 엔드포인트 구현 (UpdatePost, ChangePostStatus)
type: feature
service: community-service
status: ready
---

## Goal

TASK-BE-219에서 contract를 등록한 두 PATCH 엔드포인트를 구현한다.
`UpdatePostUseCase`와 `ChangePostStatusUseCase`는 이미 완성된 상태이므로
controller + DTO + 테스트만 추가한다.

## Scope

1. `UpdatePostUseCase.execute()` 반환 타입을 `void` → `UpdatePostResponse` 변경
2. `presentation/dto/UpdatePostRequest.java` 신규
3. `presentation/dto/UpdatePostResponse.java` 신규
4. `presentation/dto/ChangePostStatusRequest.java` 신규
5. `PostController`에 PATCH 엔드포인트 2개 추가
6. `@WebMvcTest` 슬라이스 테스트 (`PostControllerTest` 기존 파일에 케이스 추가 혹은 신규)

## Acceptance Criteria

### UpdatePostUseCase
- `execute()` 리턴 타입: `UpdatePostResponse(String postId, String title, String body, List<String> mediaUrls, Instant updatedAt)`
- 반환 값: 저장 후 post 엔티티에서 직접 추출 (post.getId(), post.getTitle(), post.getBody(), input mediaUrls 재사용, post.getUpdatedAt())

### UpdatePostRequest DTO
- `title`: optional String
- `body`: optional String
- `mediaUrls`: optional List<String>
- 세 필드 모두 null 허용 (전달된 필드만 업데이트)

### ChangePostStatusRequest DTO
- `status`: `@NotNull` `PostStatus` (PUBLISHED | HIDDEN | DELETED)
- `reason`: optional String

### PostController PATCH 엔드포인트
- `PATCH /api/community/posts/{postId}` → `UpdatePostUseCase.execute()` 호출 → 200 `UpdatePostResponse`
- `PATCH /api/community/posts/{postId}/status` → `ChangePostStatusUseCase.execute()` 호출 → 204 No Content
  - actorType: `ActorType.ARTIST` (공개 API는 아티스트만)
  - actorId: `actor.accountId()`

### 테스트 (PostControllerTest — @WebMvcTest)
- `patchPost_authorUpdatesContent_returns200`: 정상 수정 → 200 + body
- `patchPost_nonAuthor_returns403`: PERMISSION_DENIED → 403
- `patchPost_notFound_returns404`: POST_NOT_FOUND → 404
- `patchStatus_publishDraft_returns204`: DRAFT→PUBLISHED → 204
- `patchStatus_invalidTransition_returns422`: DELETED 포스트 → 422 POST_STATUS_TRANSITION_INVALID
- `patchStatus_notFound_returns404`: POST_NOT_FOUND → 404

## Related Specs

- `specs/services/community-service/architecture.md`
- `specs/contracts/http/community-api.md` (BE-219에서 등록)

## Related Contracts

- `specs/contracts/http/community-api.md`

## Edge Cases

- 세 필드 모두 null인 UpdatePostRequest: UpdatePostUseCase에서 `post.updateContent(null, null, null)` 호출 — Post 엔티티 updateContent() 동작 확인 필요
- `reason` null인 ChangePostStatusRequest: `ChangePostStatusUseCase.execute()` 에 null 전달 가능 (이미 nullable)

## Failure Scenarios

- 존재하지 않는 postId → `PostNotFoundException` → 404
- DELETED 상태 포스트 수정 시도 → `PostStatusMachine.transition()` 내부에서 `IllegalStateException` → 422 POST_STATUS_TRANSITION_INVALID
- 작성자 아닌 계정이 수정 → `PermissionDeniedException` → 403
