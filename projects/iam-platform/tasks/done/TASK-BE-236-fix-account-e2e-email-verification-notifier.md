# TASK-BE-236: fix — account-service e2e profile에 EmailVerificationNotifier 빈 추가

## Goal

`docker-compose.e2e.yml` 환경에서 account-service 시작 시 `SendVerificationEmailUseCase` 생성자 주입 단계에서 `EmailVerificationNotifier` 빈을 찾지 못해 컨텍스트 초기화가 실패한다.

```
Parameter 2 of constructor in com.example.account.application.service.SendVerificationEmailUseCase
required a bean of type 'com.example.account.application.port.EmailVerificationNotifier' that could not be found.
```

prod 환경에는 SMTP 기반 구현이 있을 것으로 추정되나, 비-prod profile(특히 e2e)에는 구현체가 없어 빈 컨텍스트가 비어있다. e2e 및 dev profile에서 사용할 noop / log-only 구현(`LoggingEmailVerificationNotifier`)을 추가한다.

## Scope

**In:**
- `apps/account-service/src/main/java/com/example/account/infrastructure/notifier/LoggingEmailVerificationNotifier.java` 신규 작성 — `EmailVerificationNotifier` 인터페이스 구현, `@Component @Profile("!prod")` (혹은 `@ConditionalOnMissingBean`)로 활성화 조건 명시. 호출 시 toAddress + verificationLink을 INFO 로그로 출력
- 기존 `EmailVerificationNotifier` 인터페이스 위치 및 prod 구현체 확인 (구현체가 이미 있는지, 어떤 조건으로 활성화되는지 점검)
- 단위 테스트 추가: `LoggingEmailVerificationNotifierTest` — 호출 시 NPE 없음, 로그 호출 검증

**Out:**
- 실제 SMTP 발송 로직 추가 없음 (prod 구현은 별도 태스크)
- 인터페이스 시그니처 변경 없음

## Acceptance Criteria

- [ ] account-service가 e2e profile로 정상 기동
- [ ] `LoggingEmailVerificationNotifier`는 prod profile에서는 활성화되지 않음 (또는 prod 구현체가 있을 경우 그 쪽을 우선)
- [ ] 단위 테스트 통과
- [ ] e2e 통합 테스트(`TenantProvisioningE2ETest`)가 verification email 호출 경로에서 실패하지 않음

## Related Specs

- `specs/services/account-service/architecture.md`
- `specs/features/email-verification.md` (있을 경우)

## Related Contracts

- 없음 (내부 port 구현 추가만)

## Edge Cases

- prod profile에 이미 SMTP 구현(`SmtpEmailVerificationNotifier` 등)이 있을 경우: 두 빈이 충돌하지 않도록 profile 분리 또는 `@ConditionalOnMissingBean` 사용
- `EmailVerificationNotifier` 인터페이스 자체가 미정의일 경우: 인터페이스부터 도메인 port로 정의 (이 경우 별도 태스크로 분할 권장)

## Failure Scenarios

- noop 구현이 prod에 잘못 활성화되어 실제 verification email이 발송되지 않음 → profile 조건 검증 필요
- 인터페이스 메서드 시그니처 미스매치로 컴파일 실패 → 기존 `SendVerificationEmailUseCase`의 호출 시그니처와 정합 확인
