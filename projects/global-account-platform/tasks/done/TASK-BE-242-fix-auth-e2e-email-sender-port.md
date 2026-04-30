# TASK-BE-242: fix — auth-service e2e profile에 EmailSenderPort 빈 추가

## Goal

`docker-compose.e2e.yml` 환경에서 auth-service 시작 시 `RequestPasswordResetUseCase` 생성자 주입 단계에서 `EmailSenderPort` 빈을 찾지 못해 컨텍스트 초기화가 실패한다.

```
Parameter 3 of constructor in com.example.auth.application.RequestPasswordResetUseCase
required a bean of type 'com.example.auth.application.port.EmailSenderPort' that could not be found.
```

이는 TASK-BE-236(account의 `EmailVerificationNotifier`)과 동일한 패턴이지만 auth-service의 별개 port다. 비-prod profile(특히 e2e/dev)에서 사용할 noop / log-only 구현(`LoggingEmailSender`)을 추가한다.

## Scope

**In:**
- `apps/auth-service/src/main/java/com/example/auth/infrastructure/email/LoggingEmailSender.java` 신규 작성:
  - `EmailSenderPort` 인터페이스 구현
  - `@Component @Profile("!prod")`로 활성화 조건 명시 (BE-236과 동일 전략 — `@ConditionalOnMissingBean`은 컴포넌트 스캔 순서 의존성으로 회피)
  - 호출 시 toAddress + subject + body를 INFO 로그로 출력. 비밀번호 리셋 토큰은 토큰 자체가 본문에 들어가므로 마스킹 필요
- 단위 테스트 추가: `LoggingEmailSenderTest` — Logback ListAppender로 로그 출력 검증, NPE 없음, 토큰 마스킹 동작 확인
- 기존 `EmailSenderPort` 인터페이스 위치 및 prod 구현체(있다면) 확인 — prod 구현체가 이미 있다면 `@Profile("prod")`로 분리되어 있는지 점검

**Out:**
- 실제 SMTP 발송 로직 추가 없음 (prod 구현은 별도 태스크)
- 인터페이스 시그니처 변경 없음
- 비밀번호 리셋 비즈니스 로직 변경 없음

## Acceptance Criteria

- [ ] auth-service가 e2e profile로 정상 기동 (전체 컨텍스트 초기화 통과)
- [ ] `LoggingEmailSender`는 prod profile에서는 활성화되지 않음
- [ ] 단위 테스트 통과 (`./gradlew :apps:auth-service:test --tests "*LoggingEmailSender*"`)
- [ ] 기존 `RequestPasswordResetUseCase` 단위 테스트 회귀 없음
- [ ] 비밀번호 리셋 토큰이 로그에 평문 노출되지 않음

## Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/features/password-reset.md` (있을 경우)

## Related Contracts

- 없음 (내부 port 구현 추가만)

## Edge Cases

- prod profile에 이미 SMTP 구현체가 있을 경우: 두 빈이 충돌하지 않도록 `@Profile`로 격리 (prod ↔ !prod 디스조인트)
- `EmailSenderPort` 인터페이스의 메서드 시그니처가 단순 `void send(String to, String subject, String body)` 형태가 아니라 객체로 캡슐화된 경우: 그에 맞춰 구현
- 비밀번호 리셋 토큰 외 다른 민감 정보가 본문에 포함될 수 있다면 마스킹 정책 일관 적용

## Failure Scenarios

- noop 구현이 prod에 잘못 활성화 → 실제 비밀번호 리셋 메일이 발송되지 않음. `@Profile("!prod")` 검증 필수
- 인터페이스 메서드 시그니처 미스매치로 컴파일 실패 → 기존 `RequestPasswordResetUseCase`의 호출 시그니처와 정합 확인

## Implementation Notes

- BE-236에서 작성된 `LoggingEmailVerificationNotifier`를 참조 패턴으로 사용 (`apps/account-service/src/main/java/com/example/account/infrastructure/notifier/LoggingEmailVerificationNotifier.java`)
- 단위 테스트도 `LoggingEmailVerificationNotifierTest` 패턴 그대로 적용 가능
