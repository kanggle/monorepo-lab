---
id: TASK-BE-111
title: "fix(TASK-BE-108): R4 위반 수정 — Slf4jEmailSender 평문 이메일/토큰 로그 + ConditionalOnMissingBean 타입 불일치"
status: ready
priority: high
target_service: auth-service
tags: [simple-code]
created_at: 2026-04-26
fix_for: TASK-BE-108
---

# TASK-BE-111: TASK-BE-108 Fix

## Goal

TASK-BE-108 코드 리뷰에서 식별된 두 가지 이슈를 수정한다.

1. **R4 위반 (Critical)**: `Slf4jEmailSender`가 평문 이메일(PII)과 평문 토큰(시크릿)을 INFO 레벨 로그에 출력한다.  
   `rules/traits/regulated.md` R4는 토큰·시크릿을 로그에 절대 출력하지 않도록 금지하며("설령 디버그 레벨이라도"), 이메일은 마스킹 처리해야 한다. 이는 태스크 스펙(priority 10)보다 높은 우선순위인 trait 규칙(priority 4)이므로 수정이 필요하다.

2. **`@ConditionalOnMissingBean` 조건 불일치 (Warning)**: 태스크 스펙(`TASK-BE-108` Implementation Notes)은 `@ConditionalOnMissingBean(EmailSenderPort.class)` (타입 기반)을 지정했으나, 구현은 `@ConditionalOnMissingBean(name = "realEmailSender")` (이름 기반)을 사용한다. 이름 기반 조건은 실제 구현체의 빈 이름이 정확히 `"realEmailSender"`인 경우에만 Stub이 비활성화되어, 다른 이름으로 등록된 실제 구현이 Stub을 자동으로 교체하지 못하는 문제가 있다.

## Scope

### In

**`Slf4jEmailSender.java` 수정**
- `@ConditionalOnMissingBean(name = "realEmailSender")` → `@ConditionalOnMissingBean(EmailSenderPort.class)` 로 변경
- 로그 라인 수정:
  - 이메일: `@` 앞 첫 글자만 남기고 마스킹 (예: `u***@example.com`)
  - 토큰: 로그에서 완전히 제거. `[REDACTED]` 또는 출력하지 않음
  - 권장 로그 형식: `log.info("[DEV STUB] Password reset email queued — to={}", maskedEmail(toEmail))`
  - 마스킹 헬퍼는 private 메서드로 인라인 구현 (PII 마스킹 유틸이 별도 공통 라이브러리에 없으면 로컬 private 메서드 사용)

**`CredentialRepositoryAdapter.findByAccountIdEmail` 이중 정규화 제거 (Warning)**
- UseCase에서 이미 `Credential.normalizeEmail()`로 정규화된 이메일이 adapter에 전달되므로, adapter 내부의 `Credential.normalizeEmail(email)` 재호출을 제거한다.
- null 체크(`if (email == null)`)는 유지한다.

### Out
- `Slf4jEmailSender` 이외의 다른 파일 수정 (adapter 제외)
- 실제 SMTP/SES 구현체 작성
- 기존 테스트 케이스의 수정 (테스트가 깨지지 않는 한)

## Acceptance Criteria

1. `Slf4jEmailSender`의 `@ConditionalOnMissingBean`이 타입 기반(`EmailSenderPort.class`)이다.
2. `Slf4jEmailSender` 로그에 평문 토큰이 출력되지 않는다 (R4 준수).
3. `Slf4jEmailSender` 로그에 평문 이메일이 출력되지 않고 마스킹된 형태로 출력된다 (R4 준수).
4. `CredentialRepositoryAdapter.findByAccountIdEmail`에서 이중 정규화 호출이 제거된다 (null 체크는 유지).
5. `./gradlew :apps:auth-service:test --tests "com.example.auth.application.RequestPasswordResetUseCaseTest" --tests "com.example.auth.presentation.PasswordResetControllerTest"` BUILD SUCCESSFUL.

## Related Specs

- `rules/traits/regulated.md` — R4 (로그에서 PII/시크릿 마스킹 필수, 토큰 절대 출력 금지)
- `specs/services/auth-service/architecture.md`
- `tasks/done/TASK-BE-108.md` — 원본 태스크 스펙

## Related Contracts

- `specs/contracts/http/auth-api.md`

## Edge Cases

- 이메일 마스킹 로직은 `@` 기호가 없는 입력에도 NPE/IndexOutOfBounds 없이 처리해야 한다 (fallback: `"[masked]"` 반환).
- `@ConditionalOnMissingBean(EmailSenderPort.class)` 변경 후 `@WebMvcTest` 컨텍스트에서 `Slf4jEmailSender` 자동 등록 여부 확인 — `@MockitoBean RequestPasswordResetUseCase`가 이미 emailSenderPort를 mock으로 처리하므로 컨트롤러 테스트에는 영향 없음.

## Failure Scenarios

- 이메일 마스킹 함수가 예외를 던지면 전체 요청이 실패해서는 안 된다 — try/catch로 감싸고 `"[masked]"` fallback 사용.

## Test Requirements

### 단위 테스트
- 기존 `RequestPasswordResetUseCaseTest` 전체 통과 (변경 없음 예상)
- 기존 `PasswordResetControllerTest` 전체 통과 (변경 없음 예상)
