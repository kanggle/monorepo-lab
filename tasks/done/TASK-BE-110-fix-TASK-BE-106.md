---
id: TASK-BE-110
title: "fix(TASK-BE-106): PasswordPolicyViolationException을 domain 레이어로 이동 — 금지된 domain→application 의존 제거"
status: ready
priority: high
target_service: auth-service
tags: [refactor, architecture]
created_at: 2026-04-26
---

# TASK-BE-110: Fix TASK-BE-106 — PasswordPolicyViolationException 레이어 위반 수정

## Goal

TASK-BE-106 리뷰에서 발견된 **Critical** 아키텍처 위반을 수정한다.

현재 `PasswordPolicy` (domain 레이어)가 `PasswordPolicyViolationException` (application/exception/ 레이어)을 import하고 있다. 이는 `specs/services/auth-service/architecture.md`에 명시된 금지 의존성 방향(domain → application 금지)을 위반한다.

수정 방향: `PasswordPolicyViolationException`을 `domain/credentials/` 아래로 이동하여 domain 레이어가 자신이 선언한 예외를 던지도록 한다.

## Scope

### In

**이동 대상**
- `apps/auth-service/src/main/java/com/example/auth/application/exception/PasswordPolicyViolationException.java`
  → `apps/auth-service/src/main/java/com/example/auth/domain/credentials/PasswordPolicyViolationException.java`

**패키지 선언 수정**
- 이동 후 파일의 `package` 선언을 `com.example.auth.domain.credentials`로 변경

**import 수정 (기존 참조처 전수 확인)**
- `PasswordPolicy.java`: import 경로 수정
- `PasswordPolicyTest.java`: import 경로 수정
- 추후 구현될 TASK-BE-107/108/109의 application 레이어가 이 예외를 catch해야 할 경우 `domain.credentials` 패키지에서 import — application → domain 방향이므로 허용됨

**application/exception/ 디렉터리**
- 이동 후 해당 파일 삭제. 디렉터리가 비게 되면 삭제 가능하나 다른 예외 파일 존재 여부 먼저 확인

### Out
- `PasswordPolicy` 검증 로직 변경
- `Credential.changePassword()` 시그니처 변경
- TASK-BE-107/108/109 구현 범위

## Acceptance Criteria

1. `PasswordPolicyViolationException`의 패키지가 `com.example.auth.domain.credentials`이다.
2. `PasswordPolicy.java`가 `application` 패키지를 import하지 않는다.
3. `domain` 레이어가 `application`, `infrastructure`, `presentation` 레이어를 import하는 코드가 없다.
4. `./gradlew :apps:auth-service:test --tests "com.example.auth.domain.credentials.*"` BUILD SUCCESSFUL.
5. `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL.

## Related Specs

- `specs/services/auth-service/architecture.md` — Forbidden Dependencies 절: `domain → application` 금지
- `rules/common.md` — 레이어 의존 방향 규칙

## Related Contracts

- 없음 (API 계약 변경 없음)

## Edge Cases

- `application/exception/` 디렉터리에 다른 예외 클래스가 있을 수 있음: 해당 파일은 그대로 두고 `PasswordPolicyViolationException`만 이동
- TASK-BE-107/108/109가 구현 전이므로 참조처가 현재는 `PasswordPolicy.java`와 `PasswordPolicyTest.java`뿐임

## Failure Scenarios

- 이동 후 컴파일 오류: import 경로 수정 누락. 전수 검색으로 조기 발견 가능

## Test Requirements

### 단위 테스트
- 기존 `PasswordPolicyTest` 전체 통과 (import 수정 후)
- 기존 `CredentialTest` 전체 통과
