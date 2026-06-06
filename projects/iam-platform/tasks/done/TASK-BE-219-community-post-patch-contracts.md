---
id: TASK-BE-219
title: community-api.md PATCH 엔드포인트 contract 등록 (UpdatePost, ChangePostStatus)
type: spec
service: community-service
status: ready
---

## Goal

`UpdatePostUseCase`와 `ChangePostStatusUseCase`는 완전히 구현되어 있으나 HTTP contract와 컨트롤러 엔드포인트가 없다. Contract rule에 따라 구현 전에 contract를 먼저 등록한다.

`specs/contracts/http/community-api.md`에 다음 두 엔드포인트를 추가한다:
- `PATCH /api/community/posts/{postId}` — 포스트 내용 수정
- `PATCH /api/community/posts/{postId}/status` — 포스트 상태 전이 (아티스트 전용)

## Scope

- `specs/contracts/http/community-api.md` 수정만
- 구현 코드 변경 없음

## Acceptance Criteria

- `PATCH /api/community/posts/{postId}` 섹션이 community-api.md에 추가됨
  - Request body: `title` (optional string), `body` (optional string), `mediaUrls` (optional array of string)
  - Response 200: `{ postId, title, body, mediaUrls, updatedAt }`
  - Errors: 404 POST_NOT_FOUND, 403 PERMISSION_DENIED (작성자 본인만), 422 VALIDATION_ERROR
- `PATCH /api/community/posts/{postId}/status` 섹션이 community-api.md에 추가됨
  - Request body: `{ "status": "PUBLISHED" | "HIDDEN" | "DELETED", "reason": "string (optional)" }`
  - Response 204 (본문 없음)
  - 아티스트가 호출 가능한 전이: DRAFT→PUBLISHED, PUBLISHED→HIDDEN, PUBLISHED→DELETED
  - Errors: 404 POST_NOT_FOUND, 403 PERMISSION_DENIED (작성자 본인만), 422 STATE_TRANSITION_INVALID, 422 VALIDATION_ERROR
- 두 섹션 모두 `Auth required: Yes (JWT Bearer)` 명시

## Related Specs

- `specs/services/community-service/architecture.md` — application/ 섹션 (UpdatePostUseCase, ChangePostStatusUseCase 기술)
- `specs/services/community-service/architecture.md` — domain/post/status/ 섹션 (전이 규칙)

## Related Contracts

- `specs/contracts/http/community-api.md` — 수정 대상

## Edge Cases

- `reason` 필드: ChangePostStatus에서 optional (DELETED 전이 시 감사 목적으로 권장)
- `mediaUrls` 필드: UpdatePost에서 optional (null이면 기존 값 유지)
- 아티스트는 OPERATOR 전용 전이(예: HIDDEN→PUBLISHED 복구)를 이 엔드포인트로 호출 불가

## Failure Scenarios

- DELETED 상태 포스트에 status 변경 시도 → 422 STATE_TRANSITION_INVALID
- 작성자가 아닌 계정이 수정 시도 → 403 PERMISSION_DENIED
