# Task ID

TASK-FE-075

# Title

web-store OIDC 프런트 동작 파리티 정렬 (consumer-integration-guide § Phase 4.5 F2/F3/F5 준수)

# Status

done

# Owner

frontend

# Task Tags

- code
- frontend
- security

---

# Goal

web-store(NextAuth v5 기반)를 IAM consumer-integration-guide § Phase 4.5(프런트 OIDC 동작 파리티, TASK-BE-399)에 정렬한다. 진단(2026-06-17) 기준 web-store 는 다음 gap 을 가진다:

- **gap B (F2, 보안)**: access token 이 `useSession()` 공개 세션 + token-bridge 로 **클라이언트 JS 에 노출**됨 (`shared/auth/auth.ts` session 콜백이 `accessToken` 을 public session 에 실음). 계약은 토큰 server-only HttpOnly 를 요구.
- **gap A (F3, UX)**: silent refresh **없음** — access token 만료 시 401 → 곧바로 강제 재로그인 (`shared/config/api.ts` `getRefreshToken: () => null`). refresh_token 은 JWT 에 보관되나 dead storage.
- **gap C (F5, 에러)**: 에러 코드가 레거시 어휘(`account_type_mismatch`/`Configuration`/`AccessDenied`) — 표준 어휘(`role_denied`/`config_error`/`access_denied`)로 매핑. (generic fallback 은 이미 보유 — 유지.)

F6(복귀)는 이미 `?from=` + `callbackUrl` 로 충족 / F1·F4·F7 충족 → 본 task 는 **B·A·C** 에 집중.

> 선행: TASK-BE-399 (계약) 머지 후 착수 권장.

---

# Acceptance Criteria

- [ ] **F2/gap B**: `accessToken` 을 public NextAuth session 에서 제거(`session` 콜백·`types.d.ts` 공개 표면). bearer 첨부는 **server-only 헬퍼 또는 same-origin route handler 프록시**로 전환. 클라이언트 JS 가 토큰을 읽을 수 없음(token-bridge 의 클라 노출 경로 제거 또는 server-side 한정).
- [ ] **F3/gap A**: 반응형 silent refresh 도입 — 보호 API 401 → `refresh_token` 으로 `/oauth2/token` 재발급 → 원요청 1회 재시도. rotation 보관. 동시 401 단일 in-flight dedupe(SHOULD). refresh 실패 시에만 전체 재인증 리다이렉트.
- [ ] **F5/gap C**: 에러 코드를 표준 어휘로 매핑(`account_type_mismatch`→`role_denied` 등). LoginForm 의 unknown-code generic fallback 유지.
- [ ] 회귀: 기존 로그인/로그아웃/role 가드(operator 거부) e2e + 단위 테스트 GREEN. `pnpm lint` + tsc + vitest 3종 (push 전 필수, console-web 함정과 동일하게 next lint 가 CI 게이트).
- [ ] F2 전환으로 SSR/CSR 양쪽 API 호출 경로(axios interceptor) 가 토큰을 server 경유로 획득함을 검증.

---

# Scope

## In Scope

- `apps/web-store/src/shared/auth/auth.ts` (session 콜백에서 accessToken 제거)
- `apps/web-store/src/shared/auth/token-bridge.ts` + `shared/config/api.ts` (토큰 첨부를 server-only/proxy 로 재설계, silent refresh 도입)
- `apps/web-store/src/features/auth/ui/LoginForm.tsx` (에러 코드 표준 어휘 매핑)
- `apps/web-store/src/shared/auth/types.d.ts` (공개 세션 타입에서 accessToken 제거)
- 관련 단위/e2e 테스트 갱신

## Out of Scope

- 로그인 진입(F1)·로그아웃 RP-initiated(F4, 이미 federated-logout 보유)·복귀(F6, 이미 충족) — 변경 없음.
- NextAuth → 자작 핸들러 마이그레이션 (계약은 메커니즘 자유; 라이브러리 유지).
- 소셜 버튼을 web-store 로 노출(idp-hint) — 별도 task.

---

# Related Specs

> **Before reading**: `projects/ecommerce-microservices-platform/PROJECT.md` → 선언 domain/traits 의 rule 레이어.

- `projects/iam-platform/specs/features/consumer-integration-guide.md` § Phase 4.5 (본 task 가 conform — F2/F3/F5)
- `specs/integration/iam-integration.md` (web-store OIDC 통합, TASK-MONO-027)
- `projects/iam-platform/specs/features/authentication.md` (refresh rotation·TTL)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` (`/oauth2/token` refresh grant)

---

# Target App

- `apps/web-store` (consumer storefront, Next.js + NextAuth v5)

---

# Edge Cases

- web-store 는 소비자+모바일 비중 큼 — silent refresh 는 **반응형(401-trigger)** 으로 충분, proactive 는 선택. 모바일 인앱 브라우저에서 쿠키 SameSite 동작 확인.
- F2 전환 시 axios interceptor 가 클라/서버 양 컨텍스트에서 호출됨 — 클라 컨텍스트는 same-origin route handler 프록시 경유, 서버 컨텍스트는 `getWebStoreSession()` 서버 헬퍼 경유로 분기.
- NextAuth v5 의 JWT 콜백에 refresh_token 이 이미 보관됨(현재 dead) — silent refresh 가 이를 활용하도록 연결.
- operator(=`CUSTOMER` 미보유)는 여전히 `role_denied` 로 거부(degraded session) — 기존 동작 보존.

---

# Failure Scenarios

- F2 전환을 잘못해 SSR 호출이 토큰을 못 받음 → API 401 회귀. server 헬퍼 경유 확인.
- silent refresh 무한 루프(refresh 자체 401 을 다시 refresh 시도) → in-flight dedupe + refresh 실패는 재인증 폴백으로 종결.
- `pnpm lint` 누락으로 next lint no-unused-vars CI RED (token-bridge 제거 후 미사용 import 잔존) — push 전 lint 필수.

---

# Test Requirements

- 단위: session 콜백이 accessToken 미노출, 에러 코드 매핑, refresh 핸들러 동작.
- e2e: 로그인→보호페이지, 만료 토큰 silent refresh 후 재시도 성공, operator 거부(`role_denied`), 로그아웃.
- `pnpm lint` + `tsc --noEmit` + `vitest` 3종 GREEN.

---

# Definition of Done

- [ ] B/A/C gap 정렬 완료 (F2 토큰 server-only, F3 silent refresh, F5 표준 에러 어휘)
- [ ] 3종 로컬 검증 + e2e GREEN
- [ ] Phase 4.5 파리티 체크리스트 web-store 열 충족
- [ ] Ready for review
