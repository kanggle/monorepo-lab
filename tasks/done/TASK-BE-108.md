---
id: TASK-BE-108
title: "POST /api/auth/password-reset/request — 패스워드 재설정 요청 + Redis 토큰 저장소"
status: ready
priority: high
target_service: auth-service
tags: [simple-code]
created_at: 2026-04-26
depends_on: [TASK-BE-106]
---

# TASK-BE-108: 패스워드 재설정 요청 엔드포인트

## Goal

사용자가 이메일로 패스워드 재설정 토큰을 요청하는
`POST /api/auth/password-reset/request` 엔드포인트를 구현한다.

이메일 발송 인프라가 없으므로, `EmailSenderPort` 인터페이스와
**로그 전용 Stub 구현체** (`Slf4jEmailSender`)를 함께 제공한다.
실제 SMTP/SES 연동은 별도 인프라 태스크에서 구현한다.

TASK-BE-109(재설정 확인)이 이 태스크의 `PasswordResetTokenStore`에 의존한다.

## Scope

### In

**Redis 토큰 저장소 인터페이스 + 구현체**
- `domain/repository/PasswordResetTokenStore.java` (포트 인터페이스)
  - `void save(String token, String accountId, Duration ttl)`
  - `Optional<String> findAccountId(String token)` — 만료·미존재 시 empty
  - `void delete(String token)` — 사용 후 삭제 (단일 사용 보장)
- `infrastructure/redis/RedisPasswordResetTokenStore.java` (어댑터)
  - Redis key: `pwd-reset:{token}`
  - TTL: 1시간
  - 토큰 형식: UUID v4 (SecureRandom 기반)

**EmailSenderPort + Stub**
- `application/port/EmailSenderPort.java` (인터페이스)
  - `void sendPasswordResetEmail(String toEmail, String resetToken)`
- `infrastructure/email/Slf4jEmailSender.java` (Stub 구현체)
  - `log.info("PASSWORD_RESET_EMAIL to={} token={}", toEmail, resetToken)` — 평문 토큰 로그 (개발용)
  - `@Primary` 또는 `@ConditionalOnMissingBean` 로 등록하여 실제 구현 추가 시 자동 교체
  - **주의**: 프로덕션에서는 토큰을 로그에 남기지 않도록 실제 구현으로 교체 필요

**DTO**
- `PasswordResetRequestRequest`: `email` 필드
- 위치: `presentation/dto/`

**Controller**
- 기존 `PasswordController`에 추가 (BE-107에서 생성됨) 또는 별도 `PasswordResetController`
- `POST /api/auth/password-reset/request` — 인증 불필요

**Command**
- `RequestPasswordResetCommand(email)`
- 위치: `application/command/`

**UseCase**
- `RequestPasswordResetUseCase.execute(RequestPasswordResetCommand)` — `@Transactional(readOnly = true)` 또는 non-transactional
- 로직:
  1. `credentialRepository.findByEmail(email)` — 없어도 조용히 종료 (이메일 존재 여부 유출 방지)
  2. 존재하면:
     - `String token = UUID.randomUUID().toString()`
     - `passwordResetTokenStore.save(token, credential.getAccountId(), Duration.ofHours(1))`
     - `emailSenderPort.sendPasswordResetEmail(email, token)`
  3. 항상 정상 반환 (204)
- 위치: `application/`

**단위 테스트**
- `RequestPasswordResetUseCaseTest`:
  - 이메일 존재 시: tokenStore.save + emailSender.send 호출 검증
  - 이메일 미존재 시: tokenStore, emailSender 미호출 검증 (조용히 종료)

**컨트롤러 테스트**
- `PasswordResetControllerTest` (MockMvc):
  - 존재·미존재 이메일 모두 204 응답 확인

### Out
- 실제 SMTP/SendGrid/SES 연동 (별도 인프라 태스크)
- `POST /api/auth/password-reset/confirm` (BE-109 담당)

## Acceptance Criteria

1. `POST /api/auth/password-reset/request`가 모든 경우에 204를 반환한다.
2. 이메일이 존재하면 Redis에 `pwd-reset:{token}` 키로 TTL 1시간 저장된다.
3. 이메일이 존재하면 `EmailSenderPort.sendPasswordResetEmail()` 이 호출된다.
4. 이메일이 존재하지 않으면 아무 부작용 없이 204를 반환한다.
5. `EmailSenderPort`는 인터페이스로 분리되어 있어 실제 구현으로 교체 가능하다.
6. 단위 테스트 + 컨트롤러 테스트 모두 통과.

## Related Specs

- `specs/features/password-management.md` — 패스워드 재설정 User Flow
- `specs/services/auth-service/architecture.md`
- `rules/traits/regulated.md` — R4

## Related Contracts

- `specs/contracts/http/auth-api.md` — TASK-BE-106에서 추가된 POST /api/auth/password-reset/request 계약

## Edge Cases

- 이메일 정규화: `email.toLowerCase().trim()` 처리
- 동일 계정에 재요청 시: 기존 토큰 덮어쓰기 (Redis SET은 항상 overwrite)
- Rate limiting은 Gateway Layer가 담당 — UseCase에서는 별도 처리 불필요

## Failure Scenarios

- Redis 연결 실패: 예외 전파 (500) — EmailSender는 호출하지 않음
- EmailSender 실패: 예외 로그 후 204 반환 (이메일 발송은 best-effort)
  - `try { emailSenderPort.send(...) } catch (Exception e) { log.warn("...") }`

## Test Requirements

### 단위 테스트 (`RequestPasswordResetUseCaseTest`)
- `execute_existingEmail_savesTokenAndCallsEmailSender`
- `execute_unknownEmail_noSideEffects`

### 컨트롤러 테스트 (`PasswordResetControllerTest`)
- `requestReset_alwaysReturns204` (이메일 존재·미존재 두 케이스)

## Implementation Notes

### Redis Key 설계
```
key:   "pwd-reset:{UUID v4 token}"
value: "{accountId}"
TTL:   3600s (1시간)
```

### EmailSenderPort Stub 등록
```java
@Component
@ConditionalOnMissingBean(EmailSenderPort.class)
public class Slf4jEmailSender implements EmailSenderPort {
    ...
}
```
실제 구현체가 `@Component`로 등록되면 자동으로 Stub이 밀려남.

### CredentialRepository.findByEmail() 추가 필요
`CredentialRepository` 포트 인터페이스와 `CredentialRepositoryAdapter`에
`Optional<Credential> findByEmail(String email)` 메서드 추가.
`CredentialJpaRepository.findByEmail()` JPQL 또는 Spring Data 메서드 자동 생성.
