---
id: TASK-BE-163
title: "auth-service 세션 UseCase 단위 테스트 추가"
status: ready
priority: medium
service: auth-service
type: test
---

## Goal

auth-service의 device-session 관련 UseCase 5개에 대한 단위 테스트가 없음.
`@ExtendWith(MockitoExtension.class)` 패턴으로 각 Use Case의 핵심 분기를 커버한다.

## Scope

- `GetCurrentSessionUseCase` → `GetCurrentSessionUseCaseTest`
- `ListSessionsUseCase` → `ListSessionsUseCaseTest`
- `RegisterOrUpdateDeviceSessionUseCase` → `RegisterOrUpdateDeviceSessionUseCaseTest`
- `RevokeSessionUseCase` → `RevokeSessionUseCaseTest`
- `RevokeAllOtherSessionsUseCase` → `RevokeAllOtherSessionsUseCaseTest`

## Acceptance Criteria

- 각 UseCase의 정상 경로 및 예외 경로 모두 커버
- `compileTestJava` 성공
- Mockito strict stubs

## Related Specs

- `specs/services/auth-service/device-session.md`

## Related Contracts

- `specs/contracts/http/auth-api.md` (세션 엔드포인트)

## Edge Cases

- `GetCurrentSessionUseCase`: blank deviceId, 타 계정 세션, revoked 세션
- `ListSessionsUseCase`: 빈 목록, currentDeviceId 표시
- `RegisterOrUpdateDeviceSessionUseCase`: unknown fingerprint, 기존 active 세션 touch, 새 세션 생성
- `RevokeSessionUseCase`: 미존재, 타 계정, 이미 revoked (idempotent), 정상 revoke
- `RevokeAllOtherSessionsUseCase`: blank/null deviceId, current 세션 미존재, 다른 세션 없음, 정상 bulk revoke

## Failure Scenarios

- `SessionNotFoundException` 적절히 발생
- `SessionOwnershipMismatchException` 적절히 발생
