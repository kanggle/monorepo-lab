---
id: TASK-BE-109
title: "POST /api/auth/password-reset/confirm — 패스워드 재설정 확인 엔드포인트 구현"
status: ready
priority: high
target_service: auth-service
tags: [simple-code]
created_at: 2026-04-26
depends_on: [TASK-BE-108]
---

# TASK-BE-109: 패스워드 재설정 확인 엔드포인트

## Goal

사용자가 재설정 토큰과 새 패스워드를 전송하여 패스워드를 재설정하는
`POST /api/auth/password-reset/confirm` 엔드포인트를 구현한다.

TASK-BE-108에서 구현된 `PasswordResetTokenStore`를 사용하여 토큰 검증 후
패스워드를 변경하고 해당 계정의 모든 세션을 revoke한다.

## Scope

### In

**DTO**
- `PasswordResetConfirmRequest`: `token`, `newPassword` 필드
- 위치: `presentation/dto/`

**예외**
- `PasswordResetTokenInvalidException` (application/exception/)
  - 토큰 없음·만료·이미 사용됨 모두 동일 예외로 처리

**Controller**
- 기존 `PasswordController` 또는 `PasswordResetController`에 추가
- `POST /api/auth/password-reset/confirm` — 인증 불필요

**Command**
- `ConfirmPasswordResetCommand(token, newPassword)`
- 위치: `application/command/`

**UseCase**
- `ConfirmPasswordResetUseCase.execute(ConfirmPasswordResetCommand)` — `@Transactional`
- 로직:
  1. `passwordResetTokenStore.findAccountId(token)` — empty 시 `PasswordResetTokenInvalidException`
  2. `PasswordPolicy.validate(newPassword, credential.getEmail())` (이메일은 아래에서 가져옴)
  3. `credentialRepository.findByAccountId(accountId)` — 없으면 `PasswordResetTokenInvalidException`
  4. `PasswordPolicy.validate(newPassword, credential.getEmail())`
  5. `CredentialHash newHash = CredentialHash.argon2id(argon2Encoder.encode(newPassword))`
  6. `Credential updated = credential.changePassword(newHash)`
  7. `credentialRepository.save(updated)`
  8. 모든 세션 revoke:
     - `refreshTokenRepository.revokeAllByAccountId(accountId)`
     - `bulkInvalidationStore.invalidateAll(accountId, Instant.now())`
  9. `passwordResetTokenStore.delete(token)` — 단일 사용 보장 (토큰 즉시 삭제)
- 위치: `application/`

**예외 처리 등록**
- `PasswordResetTokenInvalidException` → 400 `PASSWORD_RESET_TOKEN_INVALID`
- ExceptionHandler에 등록

**단위 테스트**
- `ConfirmPasswordResetUseCaseTest`:
  - 정상 흐름: hash 변경 + 전체 세션 revoke + 토큰 삭제 검증
  - 토큰 미존재: `PasswordResetTokenInvalidException`
  - 토큰 만료(empty): `PasswordResetTokenInvalidException`
  - 정책 위반: `PasswordPolicyViolationException`

**컨트롤러 테스트**
- `PasswordResetControllerTest`:
  - 204 정상
  - 400 `PASSWORD_RESET_TOKEN_INVALID`
  - 400 `PASSWORD_POLICY_VIOLATION`

### Out
- 이메일 재발송 (rate limiting scope)
- 재설정 이력 저장 (백로그)

## Acceptance Criteria

1. 유효 토큰 + 정책 준수 패스워드 → 204, `credentials.credential_hash` 갱신.
2. 재설정 완료 후 해당 계정의 모든 refresh token이 revoke된다.
3. 사용된 토큰은 즉시 Redis에서 삭제된다 (단일 사용 보장).
4. 토큰 없음·만료 → 400 `PASSWORD_RESET_TOKEN_INVALID`.
5. 정책 위반 → 400 `PASSWORD_POLICY_VIOLATION`.
6. 패스워드 평문이 어디에도 로깅되지 않는다 (R4 준수).
7. 단위 테스트 + 컨트롤러 테스트 모두 통과.

## Related Specs

- `specs/features/password-management.md` — 패스워드 재설정 User Flow
- `specs/services/auth-service/architecture.md`
- `rules/traits/regulated.md` — R4
- `platform/testing-strategy.md`

## Related Contracts

- `specs/contracts/http/auth-api.md` — TASK-BE-106에서 추가된 POST /api/auth/password-reset/confirm 계약

## Edge Cases

- 토큰 검증과 계정 조회 사이에 계정이 삭제된 경우: `PasswordResetTokenInvalidException` (보안상 동일 응답)
- DELETED/LOCKED 계정의 재설정 허용 여부: 스펙에 명시 없음 → 허용 (상태 확인 불필요)
- `bulkInvalidationStore.invalidateAll()` 와 `refreshTokenRepository.revokeAllByAccountId()` 동시 호출: 두 경로 모두 실행 (중복 안전)

## Failure Scenarios

- Redis 삭제 실패 (`passwordResetTokenStore.delete`): 예외 전파 (204 반환 전) → 재시도 가능
- DB 저장 실패: 트랜잭션 롤백 → Redis 토큰은 유지됨 (재시도 허용)

## Test Requirements

### 단위 테스트 (`ConfirmPasswordResetUseCaseTest`)
- `execute_validToken_updatesHashAndRevokesAllSessions`
- `execute_unknownToken_throws`
- `execute_policyViolation_throws`

### 컨트롤러 테스트 (기존 `PasswordResetControllerTest` 확장)
- `confirmReset_validRequest_returns204`
- `confirmReset_invalidToken_returns400`
- `confirmReset_policyViolation_returns400`

## Implementation Notes

### 전체 세션 revoke 패턴
기존 `LogoutUseCase` 또는 `RevokeAllOtherSessionsUseCase`에서
`refreshTokenRepository.revokeAllByAccountId()` 와
`bulkInvalidationStore.invalidateAll()` 호출 패턴을 참고.

### 토큰 삭제 순서
반드시 DB 저장 완료 + 세션 revoke 후에 `passwordResetTokenStore.delete(token)` 호출.
DB 저장 실패 시 토큰이 유효한 상태로 남아야 재시도가 가능하다.
