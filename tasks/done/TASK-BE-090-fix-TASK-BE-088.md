---
id: TASK-BE-090
title: "Fix TASK-BE-088 — GdprControllerTest HTTP 상태 코드 불일치 및 누락 필드 검증 수정"
status: ready
area: backend
service: account-service
---

## Goal

TASK-BE-088에서 작성된 GdprControllerTest에서 발견된 두 가지 문제를 수정한다.

1. `POST /internal/accounts/{accountId}/gdpr-delete` 의 `StateTransitionException` 에러 응답 HTTP 상태 코드가 계약(`specs/contracts/http/internal/admin-to-account.md`)에서 `400`으로 명시되어 있으나, 구현(GlobalExceptionHandler)은 `409 CONFLICT`를 반환하고, 테스트도 `409`를 검증하고 있어 계약과 충돌한다.
2. `GET /internal/accounts/{accountId}/export` 정상 응답 테스트에서 계약 필드인 `createdAt`을 검증하지 않는다.

## Scope

### In

- `apps/account-service/src/main/java/com/example/account/presentation/advice/GlobalExceptionHandler.java`
  - `StateTransitionException` 핸들러의 HTTP 상태 코드를 계약에 맞게 수정
  - 계약: `specs/contracts/http/internal/admin-to-account.md` 는 `400 STATE_TRANSITION_INVALID`
  - 단, `platform/error-handling.md` Admin Operations 섹션의 `STATE_TRANSITION_INVALID | 422` 와도 충돌하므로, 상위 우선순위 문서(계약 vs 에러 레지스트리)를 확인하여 한쪽으로 통일해야 함. 충돌 해소 우선순위: `specs/contracts/` > `platform/error-handling.md`. 계약을 따라 400으로 통일하되, 에러 레지스트리도 함께 업데이트.
- `apps/account-service/src/test/java/com/example/account/presentation/GdprControllerTest.java`
  - `gdprDelete_alreadyDeleted_returns409StateTransitionInvalid` → HTTP 상태 코드 수정 및 메서드명 변경
  - `export_validRequest_returns200WithAccountAndProfile` → `createdAt` 필드 검증 추가
- `platform/error-handling.md`
  - `STATE_TRANSITION_INVALID` 의 HTTP 상태 코드를 실제 계약과 일치하도록 수정 (400 또는 합의된 코드로 통일)
- 기존 상태 전이 관련 테스트(`AccountStatusControllerTest`, `InternalControllerTest`, `AccountSignupIntegrationTest`)가 `STATE_TRANSITION_INVALID` 에 대해 `isConflict()` (409)를 기대하고 있으므로, 이 테스트들도 함께 수정

### Out

- account-service 이외의 서비스 코드 변경
- 신규 기능 구현

## Acceptance Criteria

- [ ] `platform/error-handling.md` 의 `STATE_TRANSITION_INVALID` HTTP 상태 코드가 계약과 일치하도록 수정됨
- [ ] `GlobalExceptionHandler.handleStateTransitionInvalid()` 가 수정된 상태 코드를 반환함
- [ ] `GdprControllerTest.gdprDelete_alreadyDeleted_*` 메서드가 수정된 상태 코드를 검증함
- [ ] `GdprControllerTest.export_validRequest_returns200WithAccountAndProfile()` 에서 `$.createdAt` 필드가 검증됨
- [ ] `AccountStatusControllerTest`, `InternalControllerTest`, `AccountSignupIntegrationTest` 등 기존 `STATE_TRANSITION_INVALID` 관련 테스트도 수정된 상태 코드에 맞춰 업데이트됨
- [ ] `./gradlew :apps:account-service:test` 성공

## Related Specs

- specs/contracts/http/internal/admin-to-account.md
- specs/features/data-rights.md
- specs/services/account-service/architecture.md
- platform/error-handling.md
- platform/testing-strategy.md

## Related Contracts

- specs/contracts/http/internal/admin-to-account.md

## Edge Cases

- `STATE_TRANSITION_INVALID` 에 대해 400, 409, 422 세 가지 코드 중 하나를 선택할 때 계약(`specs/contracts/`)이 가장 높은 우선순위임 (CLAUDE.md Source of Truth Priority)
- `specs/contracts/http/internal/admin-to-account.md` 의 lock/unlock 엔드포인트도 `409 STATE_TRANSITION_INVALID` 로 명시되어 있으므로, gdpr-delete 전용 400과 다른 엔드포인트의 409 간 일관성을 검토하여 계약을 우선 수정하거나 구현을 계약에 맞춤

## Failure Scenarios

- lock/unlock 계약과 gdpr-delete 계약의 상태 코드가 다를 경우, 계약 문서를 우선 통일하고 구현을 따라야 함 — 계약 수정 없이 구현만 변경하는 것은 금지
- 에러 레지스트리 수정 없이 구현만 변경하면 차후 코드 리뷰에서 동일한 문제가 재발함
