# Task ID

TASK-BE-130

# Title

auth-service — LoginUseCase 자격증명 실패 처리 인라인 중복 제거 — recordCredentialFailureAndThrow 헬퍼 추출

# Status

ready

# Owner

backend

# Task Tags

- refactor

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`LoginUseCase.execute()` 안에서 아래 4-줄 패턴이 네 곳에서 반복된다:

```java
loginAttemptCounter.incrementFailureCount(emailHash);
int newCount = loginAttemptCounter.getFailureCount(emailHash);
authEventPublisher.publishLoginFailed(accountId, emailHash, "CREDENTIALS_INVALID", newCount, ctx);
throw new CredentialsInvalidException();
```

발생 위치:
1. 라인 73-76: credential 미발견 분기
2. 라인 87-90: credential hash null 가드
3. 라인 99-103: account 미발견 분기 (lambda 내부)
4. 라인 111-114: 비밀번호 불일치 분기

`private void recordCredentialFailureAndThrow(String accountId, String emailHash, SessionContext ctx)` 헬퍼를 추출하여 중복 4곳을 단일 호출로 대체한다.

---

# Scope

## In Scope

- `LoginUseCase.java` 단일 파일 수정
- private 헬퍼 메서드 `recordCredentialFailureAndThrow(String accountId, String emailHash, SessionContext ctx)` 추출
- lambda 내부 분기(라인 99-103)도 헬퍼 호출로 대체

## Out of Scope

- `AuthEventPublisher`, `LoginAttemptCounter` 등 의존 클래스 변경 없음
- API 계약 변경 없음
- 행위(behavior) 변경 없음

---

# Acceptance Criteria

- [ ] `LoginUseCase` 안에 4-줄 반복 패턴이 사라지고 `recordCredentialFailureAndThrow` 단일 호출로 대체된다
- [ ] `recordCredentialFailureAndThrow`가 내부에서 `incrementFailureCount → getFailureCount → publishLoginFailed("CREDENTIALS_INVALID") → throw CredentialsInvalidException` 순서를 실행한다
- [ ] lambda 내부의 분기도 헬퍼를 호출하도록 수정된다 (lambda가 `UncheckedRunnable` 방식으로 변환되거나 exception을 re-throw하는 구조 유지)
- [ ] 기존 `LoginUseCaseTest`가 모두 통과한다
- [ ] 빌드 통과

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `platform/service-types/rest-api.md`

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 행위 변경 없음

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`
- Layered Architecture: application 레이어 내부 리팩토링

---

# Implementation Notes

- `private void recordCredentialFailureAndThrow(String accountId, String emailHash, SessionContext ctx)` 는 호출 즉시 `CredentialsInvalidException`을 던지므로 never-returns 계약이다. 메서드 시그니처에 이를 명시하거나 Javadoc으로 표시한다.
- lambda 분기(라인 99-103, `orElseThrow` 내부)는 lambda 안에서 헬퍼를 호출하고 `throw new RuntimeException("unreachable")` 을 추가하거나, 아니면 `orElseThrow` 블록을 if-statement 방식으로 리팩토링하여 헬퍼를 직접 호출하는 구조로 변경할 수 있다.
- lambda 이외 3곳은 단순 교체.
- 기존 `LoginUseCaseTest`(Mockito 기반 단위 테스트)가 있으므로 모든 케이스 커버 여부 확인.

---

# Edge Cases

- lambda 안의 호출: `recordCredentialFailureAndThrow`가 `void`이고 exception을 던지므로, lambda에서 `CredentialsInvalidException`이 정상 전파되어야 한다.
- `accountId`가 null인 초기 분기(credential 미발견)에서도 헬퍼가 null을 그대로 넘긴다 — `publishLoginFailed`는 이미 null accountId를 허용한다.

---

# Failure Scenarios

- 헬퍼 추출 후 exception이 caller에게 전파되지 않으면 login failure 경로가 200 OK를 반환하는 버그 — 테스트로 감지 가능.
- `loginAttemptCounter` 호출 순서(increment → get)가 바뀌면 failCount 값이 달라짐 — 헬퍼 내부 순서를 기존과 동일하게 유지.

---

# Test Requirements

- 기존 `LoginUseCaseTest` 전체 케이스 재실행하여 모두 통과 확인
- 추가 테스트 불필요 (행위 변경 없음, 기존 커버리지로 충분)

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests passing
- [ ] Contracts updated if needed (해당 없음)
- [ ] Specs updated first if required (해당 없음)
- [ ] Ready for review
