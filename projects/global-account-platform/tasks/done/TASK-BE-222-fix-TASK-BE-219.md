---
id: TASK-BE-222
title: fix TASK-BE-219 — POST_STATUS_TRANSITION_INVALID 에러 코드 등록 및 contract 수정
type: fix
service: community-service
status: ready
---

## Goal

TASK-BE-219에서 작성된 `PATCH /api/community/posts/{postId}/status` contract가
`STATE_TRANSITION_INVALID` (admin 전용, HTTP 409)를 그대로 사용하나,
community-service의 포스트 상태 전이 위반은 별도 에러 코드와 422 응답이 맞다.

- `platform/error-handling.md` Community 섹션에 `POST_STATUS_TRANSITION_INVALID | 422` 등록
- `specs/contracts/http/community-api.md` PATCH status 섹션의 에러 코드를 수정
- `GlobalExceptionHandler`의 STATE_TRANSITION_INVALID 처리를 `POST_STATUS_TRANSITION_INVALID` + 422로 교체

## Scope

1. `platform/error-handling.md` — Community 섹션에 행 추가
2. `specs/contracts/http/community-api.md` — PATCH /{postId}/status 에러 테이블 수정
3. `apps/community-service/src/main/java/com/example/community/presentation/exception/GlobalExceptionHandler.java` — 핸들러 수정

## Acceptance Criteria

- `platform/error-handling.md` Community 섹션에 `POST_STATUS_TRANSITION_INVALID | 422 | Post status transition is not allowed (e.g., DELETED→*)` 등록
- `specs/contracts/http/community-api.md` PATCH /{postId}/status 에러 테이블:
  - `STATE_TRANSITION_INVALID` → `POST_STATUS_TRANSITION_INVALID` 로 변경 (HTTP 422 유지)
- `GlobalExceptionHandler`:
  - 기존 `if ("STATE_TRANSITION_INVALID".equals(e.getMessage()))` → `HttpStatus.UNPROCESSABLE_ENTITY` + `"POST_STATUS_TRANSITION_INVALID"` 반환으로 변경
- 기존 `STATE_TRANSITION_INVALID` (admin 용, 409) 는 그대로 유지 — 건드리지 않음
- 테스트: GlobalExceptionHandler 관련 기존 테스트가 있으면 확인하고 필요 시 수정

## Related Specs

- `specs/services/community-service/architecture.md`
- `platform/error-handling.md`

## Related Contracts

- `specs/contracts/http/community-api.md`

## Edge Cases

- `PostStatusMachine`은 `IllegalStateException("STATE_TRANSITION_INVALID")` 를 throw — 메시지 문자열로 분기하는 기존 핸들러 패턴 유지, HTTP status와 응답 코드만 변경
- `STATE_TRANSITION_INVALID` (admin 용) 핸들러는 건드리지 않음 (admin-service에서 사용 중)

## Failure Scenarios

- `platform/error-handling.md` 수정 없이 코드만 바꾸는 경우 → "Error codes must be registered before use" 위반
