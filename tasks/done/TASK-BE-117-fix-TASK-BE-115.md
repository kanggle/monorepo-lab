---
id: TASK-BE-117
title: "fix(platform): TASK-BE-115 후속 — rules/domains/saas.md 이메일 인증 에러 코드 크로스레퍼런스 누락 추가"
status: ready
priority: high
target_service: account-service
tags: [code, api]
created_at: 2026-04-26
---

# TASK-BE-117: TASK-BE-115 후속 — rules/domains/saas.md 이메일 인증 에러 코드 크로스레퍼런스 누락 추가

## Goal

TASK-BE-115에서 `platform/error-handling.md`의 `Email Verification [domain: saas]` 섹션에
`TOKEN_EXPIRED_OR_INVALID`, `EMAIL_ALREADY_VERIFIED`, `RATE_LIMITED` 세 코드를 등록하였으나,
`platform/error-handling.md`의 Change Protocol을 준수하지 않았다.

Change Protocol 요건:
> "New domain-specific error codes → add to this file **and** cross-reference from the matching `rules/domains/<domain>.md`."

`rules/domains/saas.md`의 `## Standard Error Codes` 섹션에 위 세 코드에 대한
크로스레퍼런스가 누락된 상태다. 이 태스크는 해당 누락을 수정한다.

## Scope

### In

- `rules/domains/saas.md` — `## Standard Error Codes` 섹션 아래 이메일 인증 에러 코드
  서브섹션(또는 항목) 추가:

  ```
  ### Email Verification (account-service 전용)

  다음 코드는 `platform/error-handling.md`의 `Email Verification [domain: saas]` 섹션에 등록되어 있다.
  account-service 이메일 인증 플로우(`POST /api/accounts/signup/verify-email`,
  `POST /api/accounts/signup/resend-verification-email`)에서만 발생한다.

  - `TOKEN_EXPIRED_OR_INVALID` — 이메일 인증 토큰 만료·미존재·이미 소비됨 (400)
  - `EMAIL_ALREADY_VERIFIED` — 해당 계정의 이메일이 이미 인증된 상태 (409)
  - `RATE_LIMITED` — 이메일 재발송 rate limit 초과 (5분 내 1회). `RATE_LIMIT_EXCEEDED`(로그인 rate limit)와 구분. (429)
  ```

### Out

- `platform/error-handling.md` 변경 없음 (TASK-BE-115에서 이미 완료)
- 구현 코드 변경 없음
- 컨트랙트 파일 변경 없음

## Acceptance Criteria

- [ ] `rules/domains/saas.md`의 `## Standard Error Codes` 섹션 아래에
  `TOKEN_EXPIRED_OR_INVALID`, `EMAIL_ALREADY_VERIFIED`, `RATE_LIMITED` 세 코드가
  HTTP 상태코드 및 사용 맥락 설명과 함께 크로스레퍼런스된다.
- [ ] 각 코드 항목이 `platform/error-handling.md`의 `Email Verification [domain: saas]`
  섹션을 명시적으로 가리킨다 (섹션 링크 또는 인라인 설명).
- [ ] `RATE_LIMITED`가 `RATE_LIMIT_EXCEEDED`(로그인 rate limit)와 구별됨이 Description에 명시된다.
- [ ] `./gradlew :apps:account-service:test` BUILD SUCCESSFUL (코드 변경 없으므로 기존 통과 유지).

## Related Specs

- `platform/error-handling.md` — Change Protocol: "New domain-specific error codes → add to this file **and** cross-reference from the matching `rules/domains/<domain>.md`."
- `rules/domains/saas.md` — Standard Error Codes 섹션 (크로스레퍼런스 추가 대상)

## Related Contracts

- `specs/contracts/http/account-api.md`

## Edge Cases

- `rules/domains/saas.md`에 이미 `LOGIN_RATE_LIMITED` 항목이 있음 — `RATE_LIMITED`(account-service)와 혼동되지 않도록 명시적으로 구분할 것.
- 새 서브섹션 제목이 기존 Admin Operations 서브섹션 스타일과 일관되어야 함.

## Failure Scenarios

- `rules/domains/saas.md`의 기존 항목과 네이밍 충돌 시 — 사용 맥락을 명확히 구분하여 병기.
- Change Protocol 요건이 추후 변경될 경우 — 이 태스크 범위는 현행 Change Protocol 기준으로 한정.
