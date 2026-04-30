---
id: TASK-BE-164
title: "account-service 애플리케이션 서비스 단위 테스트 추가"
status: ready
priority: medium
service: account-service
type: test
---

## Goal

account-service application 레이어에서 테스트가 없는 3개 서비스 클래스에 대해
`@ExtendWith(MockitoExtension.class)` 단위 테스트를 추가한다.

## Scope

- `AccountSearchQueryService` → `AccountSearchQueryServiceTest`
- `AccountStatusUseCase` → `AccountStatusUseCaseTest`
- `ProfileUseCase` → `ProfileUseCaseTest`

## Acceptance Criteria

- 각 클래스의 정상/예외 경로 모두 커버
- `AccountStatusUseCase`는 실제 `AccountStatusMachine` 인스턴스 사용 (순수 도메인 로직)
- `compileTestJava` 성공
- Mockito strict stubs

## Related Specs

- `specs/services/account-service/architecture.md`

## Related Contracts

없음 (애플리케이션 레이어 내부 로직 검증)

## Edge Cases

- `AccountSearchQueryService.search`: size > 100, blank/null email
- `AccountStatusUseCase.getStatus`: 히스토리 있음/없음
- `AccountStatusUseCase.changeStatus`: ACTIVE→LOCKED (publishAccountLocked), LOCKED→ACTIVE (publishAccountUnlocked)
- `AccountStatusUseCase.deleteAccount`: grace period 포함 반환값 검증
- `ProfileUseCase`: account/profile 각각 미존재 시 AccountNotFoundException

## Failure Scenarios

- 계정 미존재 → `AccountNotFoundException`
- size > 100 → `IllegalArgumentException`
