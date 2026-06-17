# Task ID

TASK-FAN-FE-007

# Title

fan-platform-web OIDC 프런트 동작 파리티 정렬 — F3 silent refresh (consumer-integration-guide § Phase 4.5)

# Status

ready

# Owner

frontend

# Task Tags

- code
- frontend

---

# Goal

fan-platform-web(NextAuth v5)를 IAM `consumer-integration-guide.md` § Phase 4.5(프런트 OIDC 동작 파리티)에 정렬한다. 진단(2026-06-17) 결과 fan-web 은 **F1·F2·F4·F6·F7·F8 이미 MATCH** — 특히 F2(토큰 기밀성)는 web-store(FE-075 전)와 달리 이미 server-only(session 콜백이 accessToken 미노출, `useSession`/token-bridge 부재)다. **유일한 실질 gap 은 F3(silent refresh)**:

- `src/shared/auth/auth.ts` jwt 콜백이 `accessToken`/`refreshToken`/`expiresAt`/`idToken` 를 저장하지만 **refresh 로직이 전무** — `refreshToken` 은 dead. access token(TTL 30분) 만료 후 복구 경로 없이 API 401 → error boundary.

본 task 는 web-store FE-075 의 검증된 패턴(jwt-callback proactive refresh + rotation + 실패 시 session degrade)을 fan-web 에 이식한다. F2 가 이미 충족이라 BFF 프록시 전환은 불필요(서버사이드 `gatewayFetch` 만 사용).

> 참조 템플릿(main 머지됨): `projects/ecommerce-microservices-platform/apps/web-store/src/shared/auth/auth-callbacks.ts`

---

# Acceptance Criteria

- [ ] **F3 silent refresh**: jwt 콜백에 proactive 갱신 추가 — `account` 없는 후속 호출에서 access token 이 만료 임박(예: 60s margin)이고 `refreshToken` 존재 시 `${OIDC_ISSUER_URL}/oauth2/token`(grant_type=refresh_token, client_secret_basic)으로 교환·rotation 보관. NextAuth 의 jwt 직렬화가 in-flight dedupe.
- [ ] **refresh 실패 → 재인증 폴백**: 실패 시 `token.error` 표식 → session 콜백이 anonymous(accountId null)로 degrade → middleware 가 다음 내비게이션에서 `/login?from=…` 로 보냄(F3 fallback, F6 보존 기존 동작 재사용).
- [ ] refresh 로직은 **테스트 가능하게 분리**(web-store 처럼 `auth-callbacks.ts` 로 next-auth-free pure 모듈 추출 권장 — vitest 가 `NextAuth()` 팩토리 import 없이 검증).
- [ ] 단위 테스트: refresh 성공(rotation 반영)·실패(error 표식)·만료 임박 판정·session degrade.
- [ ] **기존 동작 불변**: F2(토큰 미노출)·F6(`?from=` 파라미터 이름/값) 그대로. `?from=` 를 바꾸지 말 것 — `e2e-smoke/auth-guard.spec.ts` 가 `searchParams.get('from')` 값을 단언하므로 rename 시 e2e RED.
- [ ] 3종 GREEN: `pnpm lint` + `tsc --noEmit` + `vitest run`. (PC-FE-115 교훈: Playwright e2e-smoke 는 별도 게이트지만, 본 task 는 redirect/URL 동작을 안 바꾸므로 smoke 영향 없음 — CI 에서 재확인.)

---

# Scope

## In Scope

- `apps`(web) `src/shared/auth/auth.ts` — jwt 콜백 refresh 호출 + session 콜백 error degrade
- `src/shared/auth/auth-callbacks.ts` (신규, 권장) — `refreshAccessToken` + pure jwt/session 콜백 로직
- 관련 단위 테스트

## Out of Scope

- F2 — 이미 server-only, 변경 없음.
- F6/return-to — 이미 `?from=` 충족, 변경 없음(rename 금지).
- F1/F4/F7/F8 — 이미 MATCH.
- gatewayFetch 의 reactive 401 재시도 — 서버사이드 호출 + proactive refresh 로 대부분 커버되므로 optional(필요 판단 시 최소 추가, e2e 영향 주의). 본 task 핵심은 jwt-callback proactive refresh.

---

# Related Specs

> **Before reading**: `projects/fan-platform/PROJECT.md` → 선언 domain/traits 의 rule 레이어.

- `projects/iam-platform/specs/features/consumer-integration-guide.md` § Phase 4.5 (본 task 가 conform — F3)
- `projects/fan-platform/specs/integration/iam-integration.md` (fan-web OIDC 통합)
- `projects/iam-platform/specs/features/authentication.md` (refresh rotation·TTL)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` (`/oauth2/token` refresh grant)

---

# Target App

- `projects/fan-platform/web/fan-platform-web` (consumer fan web, Next.js 15 + NextAuth v5)

---

# Edge Cases

- fan-web 은 `@/shared/config/env`(oidcIssuerUrl/oidcClientId/oidcClientSecret)를 사용 — refreshAccessToken 이 이를 사용(web-store 는 process.env 직접; fan 은 env 모듈 경유).
- 토큰 endpoint 가 rotation refresh token 을 누락 응답하면(이론상) 보낸 refresh token 으로 fallback(세션이 refresh 능력 상실 방지).
- session degrade 가 accountId null → `isAuthenticated`/middleware `Boolean(auth)` 와 일관되는지 확인(refresh 실패가 미인증으로 수렴).

---

# Failure Scenarios

- refresh 무한 루프(refresh 자체 401 을 다시 refresh) → 실패는 `token.error` 로 종결, 재refresh 안 함.
- `?from=` 를 무심코 `redirect`/`callbackUrl` 로 rename → `auth-guard.spec.ts` 의 `from` 단언 RED. **rename 금지.**
- env 모듈 미사용(process.env 직접)으로 issuer/secret 불일치 → refresh 401. fan 의 `env` 모듈 경유.

---

# Test Requirements

- 단위: refresh 성공/실패/만료판정/degrade (pure auth-callbacks 모듈).
- `pnpm lint` + `tsc --noEmit` + `vitest run` GREEN. CI Frontend E2E smoke 회귀 0(URL 동작 불변).

---

# Definition of Done

- [ ] F3 silent refresh 구현(proactive + rotation + 실패 degrade)
- [ ] 단위 테스트 추가, 3종 GREEN
- [ ] F2/F6 불변(토큰 미노출·`?from=` 유지)
- [ ] Phase 4.5 파리티 체크리스트 fan-web 열 충족(F1~F8 전부 MATCH)
- [ ] Ready for review
