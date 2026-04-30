---
id: TASK-BE-115
title: "fix(account): TASK-BE-114 후속 — 미등록 에러 코드 플랫폼 레지스트리 등록"
status: ready
priority: high
target_service: account-service
tags: [code, api]
created_at: 2026-04-26
---

# TASK-BE-115: TASK-BE-114 후속 — 미등록 에러 코드 플랫폼 레지스트리 등록

## Goal

TASK-BE-114(이메일 인증 플로우)에서 도입한 세 개의 에러 코드(`TOKEN_EXPIRED_OR_INVALID`,
`EMAIL_ALREADY_VERIFIED`, `RATE_LIMITED`)가 `platform/error-handling.md`에 등록되지 않은
채로 구현에 사용되고 있다.

`platform/error-handling.md` Rules 섹션:
> "Error codes must be registered in this document before use."

이 태스크는 위 규칙 위반을 수정한다:
1. 세 개의 에러 코드를 `platform/error-handling.md`의 `[domain: saas]` 섹션에 등록한다.
2. `RATE_LIMITED`와 기존 `RATE_LIMIT_EXCEEDED` 간의 네이밍 불일치를 해소한다 —
   두 코드가 공존하는 현상을 정리하거나, 각각의 사용 맥락(로그인 rate limit vs 이메일
   재발송 rate limit)을 명확히 구분하여 등록한다.

## Scope

### In

- `platform/error-handling.md` — `[domain: saas]` 아래 이메일 인증 에러 코드 섹션 추가:

  | Code | HTTP | Description |
  |---|---|---|
  | TOKEN_EXPIRED_OR_INVALID | 400 | 이메일 인증 토큰이 만료되었거나 존재하지 않음 (account-service 이메일 인증 flow) |
  | EMAIL_ALREADY_VERIFIED | 409 | 이미 인증된 이메일에 대한 재인증 또는 재발송 요청 (account-service 이메일 인증 flow) |
  | RATE_LIMITED | 429 | 이메일 인증 재발송 rate limit 초과 (5분 내 1회, account-service 이메일 인증 flow) |

- `RATE_LIMITED` 코드와 기존 `RATE_LIMIT_EXCEEDED` 코드의 공존을 명시적으로 구분:
  - `RATE_LIMIT_EXCEEDED` — 로그인 시도 rate limit (기존, gateway/auth-service)
  - `RATE_LIMITED` — 이메일 재발송 rate limit (TASK-BE-114, account-service)
  - 두 코드의 용도를 Description에 명시하여 혼동 방지

### Out

- 구현 코드 변경 없음 (에러 코드 값 자체는 변경하지 않음)
- `RATE_LIMITED` → `RATE_LIMIT_EXCEEDED` 코드 통일 (코드 변경은 별도 태스크로 분리)
- 컨트랙트 파일 변경 없음

## Acceptance Criteria

1. `platform/error-handling.md`의 `[domain: saas]` 섹션에 `TOKEN_EXPIRED_OR_INVALID`,
   `EMAIL_ALREADY_VERIFIED`, `RATE_LIMITED` 세 코드가 HTTP 상태코드, 설명과 함께 등록된다.
2. `RATE_LIMITED`와 `RATE_LIMIT_EXCEEDED`의 용도 구분이 각 코드의 Description에 명시된다.
3. `./gradlew :apps:account-service:test` BUILD SUCCESSFUL (코드 변경 없으므로 기존 통과 유지).

## Related Specs

- `platform/error-handling.md` — 에러 코드 레지스트리 (Rules: "Error codes must be registered before use")
- `specs/contracts/http/account-api.md` — `TOKEN_EXPIRED_OR_INVALID`, `EMAIL_ALREADY_VERIFIED`, `RATE_LIMITED` 사용처

## Related Contracts

- `specs/contracts/http/account-api.md`

## Edge Cases

- `RATE_LIMITED`와 `RATE_LIMIT_EXCEEDED`가 공존하는 경우 — 각각의 사용 맥락을 명확히 구분해야 함. 코드 통일은 이 태스크 범위 밖.

## Failure Scenarios

- 이미 등록된 코드와 충돌하는 경우 — 명시적으로 용도를 구분하여 공존 정당화.
