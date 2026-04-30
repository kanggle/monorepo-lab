---
id: TASK-BE-107
title: "PATCH /api/auth/password — 패스워드 변경 엔드포인트 구현"
status: ready
priority: high
target_service: auth-service
tags: [simple-code]
created_at: 2026-04-26
depends_on: [TASK-BE-106]
---

# TASK-BE-107: 패스워드 변경 엔드포인트

## Goal

인증된 사용자가 현재 패스워드를 확인 후 새 패스워드로 변경할 수 있는
`PATCH /api/auth/password` 엔드포인트를 구현한다.

TASK-BE-106에서 추가된 `PasswordPolicy` 도메인과 `Credential.changePassword()` 메서드를 사용한다.

## Scope

### In

**DTO**
- `ChangePasswordRequest`: `currentPassword`, `newPassword` 필드
- 위치: `presentation/dto/`

**Controller**
- `PasswordController` (새 파일) — 또는 기존 컨트롤러에 추가
- `PATCH /api/auth/password` — `@AuthenticationPrincipal`로 accountId 추출
- 위치: `presentation/`

**Command**
- `ChangePasswordCommand(accountId, currentPassword, newPassword)`
- 위치: `application/command/`

**UseCase**
- `ChangePasswordUseCase.execute(ChangePasswordCommand)` — `@Transactional`
- 로직:
  1. `credentialRepository.findByAccountId(accountId)` — 없으면 예외
  2. argon2id로 `currentPassword` 검증 → 불일치 시 `CredentialsInvalidException`
  3. `PasswordPolicy.validate(newPassword, credential.getEmail())`
  4. `CredentialHash newHash = CredentialHash.argon2id(argon2Encoder.encode(newPassword))`
  5. `Credential updated = credential.changePassword(newHash)`
  6. `credentialRepository.save(updated)`
  7. 모든 다른 refresh token revoke: `refreshTokenRepository.revokeAllByAccountIdExcept(accountId, currentJti)` (선택적 — 보안 강화 모드)
- 위치: `application/`

**예외 처리**
- `PasswordPolicyViolationException` → 400 `PASSWORD_POLICY_VIOLATION`
- ExceptionHandler에 등록

**단위 테스트**
- `ChangePasswordUseCaseTest`:
  - 정상 흐름: 해시 갱신 + save 호출 검증
  - 현재 패스워드 불일치: `CredentialsInvalidException` 발생
  - 정책 위반: `PasswordPolicyViolationException` 발생
  - 계정 없음: 예외 발생

**통합/컨트롤러 테스트**
- `PasswordControllerTest` (MockMvc):
  - 204 정상 응답
  - 400 현재 패스워드 불일치
  - 400 정책 위반
  - 401 토큰 없음

### Out
- 패스워드 재설정 플로우 (BE-108·109 담당)
- 이전 패스워드 재사용 금지 (백로그)
- 세션 revoke 여부 선택적 파라미터 (현재는 항상 revoke)

## Acceptance Criteria

1. `PATCH /api/auth/password`가 유효 요청 시 204를 반환한다.
2. `credentials.credential_hash`가 새 Argon2id 해시로 갱신된다.
3. 현재 패스워드 불일치 시 400 `CREDENTIALS_INVALID`를 반환한다.
4. 새 패스워드가 정책 미충족 시 400 `PASSWORD_POLICY_VIOLATION`을 반환한다.
5. 패스워드 평문이 어디에도 로깅되지 않는다 (R4 준수).
6. 단위 테스트 + 컨트롤러 테스트 모두 통과.

## Related Specs

- `specs/features/password-management.md` — 패스워드 변경 User Flow
- `specs/services/auth-service/architecture.md` — 레이어 규칙
- `rules/traits/regulated.md` — R4 (패스워드 평문 금지)
- `platform/testing-strategy.md`

## Related Contracts

- `specs/contracts/http/auth-api.md` — TASK-BE-106에서 추가된 PATCH /api/auth/password 계약

## Edge Cases

- `currentJti` 를 `@AuthenticationPrincipal`에서 추출할 수 없는 경우: 전체 refresh token revoke fallback
- Argon2 인코더가 스프링 컨텍스트에 이미 등록되어 있는지 확인 (`SecurityConfig` 참조)

## Failure Scenarios

- Argon2 검증 중 예외: 500으로 전파 (재시도 불가)
- DB 저장 실패: 트랜잭션 롤백

## Test Requirements

### 단위 테스트 (`ChangePasswordUseCaseTest`)
- `execute_validRequest_updatesHash`
- `execute_wrongCurrentPassword_throws`
- `execute_policyViolation_throws`
- `execute_credentialNotFound_throws`

### 컨트롤러 테스트 (`PasswordControllerTest`)
- MockMvc, `@WebMvcTest`
- 204, 400(CREDENTIALS_INVALID), 400(PASSWORD_POLICY_VIOLATION), 401 케이스

## Implementation Notes

### Argon2 인코더
기존 `SecurityConfig`에 `PasswordEncoder` 빈이 Argon2PasswordEncoder로 등록되어 있는지 확인.
없으면 `Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8()` 또는
`new Argon2PasswordEncoder(16, 32, 1, 65536, 3)` 으로 빈 등록.

### JTI 추출 패턴
`@AuthenticationPrincipal`로 주입되는 `UserDetails` 또는 Custom Principal에서
JWT claim의 `jti` 필드를 꺼낼 수 있는지 기존 `LoginController` / `LogoutController`를 참고.

### ExceptionHandler 등록
기존 `AuthExceptionHandler` (또는 `GlobalExceptionHandler`)에
`PasswordPolicyViolationException` → 400 `PASSWORD_POLICY_VIOLATION` 케이스 추가.
