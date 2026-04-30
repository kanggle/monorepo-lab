---
id: TASK-FE-022
title: "fix(TASK-FE-019): refreshToken 쿠키 TTL 불일치 수정 및 ACCOUNT_DORMANT/ACCOUNT_DELETED 테스트 추가"
status: ready
area: frontend
service: admin-web
---

## Goal

TASK-FE-019 리뷰에서 발견된 두 가지 문제를 수정한다.

1. `apps/admin-web/src/app/api/auth/oauth/callback/route.ts` line 79에서 `refreshExpiresIn`이 없을 때 사용되는 fallback 값 `60 * 60 * 24 * 14` (1,209,600초, 14일)이 `specs/contracts/http/auth-api.md` refresh token 스펙 `exp: iat + 604800` (7일)과 충돌한다. fallback을 `604800`으로 수정해야 한다.

2. `apps/admin-web/tests/unit/OAuthCallbackHandler.test.tsx`에 `ACCOUNT_DORMANT`(403), `ACCOUNT_DELETED`(403) 에러 케이스 테스트가 없다. Acceptance Criteria와 Test Requirements에 명시된 항목이므로 추가한다.

## Scope

### In

- `apps/admin-web/src/app/api/auth/oauth/callback/route.ts`
  - line 79: `maxAge: data.refreshExpiresIn ?? 60 * 60 * 24 * 14` 를 `maxAge: data.refreshExpiresIn ?? 604800`으로 수정
- `apps/admin-web/tests/unit/OAuthCallbackHandler.test.tsx`
  - `ACCOUNT_DORMANT` 403 응답 -> `/login?error=ACCOUNT_DORMANT` 라우팅 테스트 추가
  - `ACCOUNT_DELETED` 403 응답 -> `/login?error=ACCOUNT_DELETED` 라우팅 테스트 추가

### Out

- 그 외 TASK-FE-019 구현 파일 변경
- 새로운 기능 추가
- E2E 테스트 수정

## Acceptance Criteria

- [ ] `callback/route.ts`의 refreshToken 쿠키 fallback maxAge가 `604800`(7일)으로 수정되어 `specs/contracts/http/auth-api.md` 스펙과 일치함
- [ ] `OAuthCallbackHandler.test.tsx`에 403 ACCOUNT_DORMANT 응답 -> `/login?error=ACCOUNT_DORMANT`로 라우팅되는 테스트가 추가됨
- [ ] `OAuthCallbackHandler.test.tsx`에 403 ACCOUNT_DELETED 응답 -> `/login?error=ACCOUNT_DELETED`로 라우팅되는 테스트가 추가됨
- [ ] `pnpm --filter admin-web build` 성공 (TypeScript 에러 없음)
- [ ] `pnpm --filter admin-web test` 통과

## Related Specs

- `specs/contracts/http/auth-api.md` — refresh token `exp: iat + 604800` (7일) 명세, `ACCOUNT_DORMANT`/`ACCOUNT_DELETED` 에러 테이블
- `specs/features/oauth-social-login.md` — 계정 상태 DORMANT/DELETED 시 소셜 로그인 거부

## Related Contracts

- `specs/contracts/http/auth-api.md` (`POST /api/auth/oauth/callback` - 에러 응답 목록)
- 변경 없음 (기존 계약 소비만)

## Edge Cases

- `refreshExpiresIn`이 auth-service 응답에 포함되는 경우(정상 경로): fallback이 사용되지 않으므로 영향 없음
- `refreshExpiresIn`이 응답에 없는 경우(비정상): fallback 604800으로 스펙을 만족함

## Failure Scenarios

- 수정 후 TypeScript 빌드 실패: 숫자 리터럴 교체이므로 타입 오류 없음. 빌드 결과로 확인
- 추가 테스트 실행 실패: 기존 `OAuthCallbackHandler.test.tsx` 패턴을 따라 mock 구성, `ACCOUNT_DORMANT`/`ACCOUNT_DELETED`는 403 상태 코드로 응답
