# TASK-FE-078 — web-store middleware 가 `/api/bff` 를 게이팅해 BFF 재인증/공개읽기 파손 수정

- **Status**: ready
- **Project**: ecommerce-microservices-platform
- **App**: web-store (Next.js)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (single-file 버그 수정)

## Goal

storefront 에서 로그인 상태에서도 위시리스트(`/my/wishlist`)·내 리뷰(`/my/reviews`)·공개 상품 페이지 리뷰가 "불러오는데 실패했습니다"로 깨지는 회귀를 수정한다.

근본 원인: `src/middleware.ts` 의 public-path 화이트리스트가 same-origin BFF 프록시 경로(`/api/bff/*`)를 **누락**한다. 반면 `src/shared/auth/auth.ts` 의 `authConfig.callbacks.authorized` 콜백은 `/api/bff` 를 명시적으로 public 처리하도록 문서화·구현돼 있다(주석: *"BFF must not be middleware-bounced to /login (that would break XHR re-auth)"*). NextAuth v5 에서 실제 게이트는 커스텀 `middleware.ts` 이고 `authorized` 콜백은 사용되지 않으므로, **두 게이트가 불일치**하며 잘못된 쪽(middleware)이 권위를 가진다.

결과(런타임 재현 완료, federation-hardening-e2e 백엔드 + web-store dev :3001):

1. **로그인했으나 access token 만료**(OIDC access token 수명 30분, 무인 silent-refresh 실패 시): client axios XHR `/api/bff/...` 가 깔끔한 `401 X-Reauth: 1`(→ `onAuthError` 풀 재인증, F1) 대신 `307 → /login` HTML 을 받는다. axios 는 리다이렉트를 따라가 HTML 을 받고 JSON 파싱 실패 → react-query `isError` → "불러오는데 실패했습니다" 메시지. ("로그인했는데도 실패"의 정체.)
2. **익명 방문자**: 공개 상품 상세 페이지의 리뷰 목록/요약(`/api/bff/api/reviews/products/{id}`, `/summary`)이 백엔드는 200 을 주는데 middleware 가 307→/login 으로 차단 → 익명 사용자에게 리뷰가 영영 안 보임.

## Scope

**In scope** (web-store only):

1. `src/middleware.ts` — public-path 분기에 `pathname.startsWith('/api/bff')` 추가. 그 이유를 인라인 주석으로 문서화(`auth.ts` 의 `authorized` 콜백 의도와 일치).

**Out of scope**: 백엔드/게이트웨이 변경, BFF 라우트 핸들러(`src/app/api/bff/[...path]/route.ts`) 변경(이미 401 X-Reauth 를 올바르게 반환), 위시리스트 버튼 익명 처리(이미 `WishlistButton` 이 `useAuth` 로 게이팅해 익명 XHR 미발생).

## Acceptance Criteria

- **AC-1 — 익명 공개읽기.** 비로그인 브라우저에서 `/api/bff/api/reviews/products/{uuid}` 및 `/summary` → **HTTP 200**(백엔드 공개 응답 패스스루). 수정 전엔 307(opaqueredirect).
- **AC-2 — 보호 자원 깔끔한 재인증.** 세션 없는 `/api/bff/api/wishlists/me` → 백엔드 **401**(307 HTML 아님). client axios 는 401 X-Reauth 를 받아 `onAuthError`(풀 재인증)로 분기.
- **AC-3 — 로그인 회귀 없음.** CONSUMER(`CUSTOMER` role) 로그인 후 `/my/wishlist`, `/my/reviews` → `/api/bff/...` 200, "실패" 메시지 미표시(데이터 없으면 "비어 있음").
- **AC-4 — 게이트.** `next lint` clean + `tsc --noEmit` clean(로컬 검증) + CI `vitest run` GREEN(web-store 는 vitest4×Node24 로컬 미기동 → CI Node20 권위, [[env_webstore_vitest4_node24_module_evaluator]]). 기존 `bff-proxy.test.ts` 무변경(라우트 핸들러 테스트, middleware 와 독립).

## Related Specs

- `projects/iam-platform/specs/features/consumer-integration-guide.md` § Phase 4.5 F1/F2/F3 — BFF 토큰 비밀유지 + 401 재인증 계약.
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` — web-store OIDC/BFF 통합.

## Related Contracts

- `specs/contracts/http/review-api.md` — 상품 리뷰 목록/요약은 익명 읽기 허용(공개 스토어프론트 계약 근거).

## Edge Cases

- 만료 직전 proactive refresh 성공 시: 정상 200(영향 없음). 본 수정은 refresh 실패(`RefreshAccessTokenError`) 또는 세션 부재 시의 경로만 교정.
- 익명 사용자 + 위시리스트 버튼: `WishlistButton` 외곽이 `useAuth()` 로 미인증 시 `WishlistButtonInner`(체크 XHR) 자체를 렌더하지 않으므로, 익명 401 이 onAuthError(로그인 강제 리다이렉트)로 새지 않음.
- BFF route 핸들러는 백엔드 401 을 그대로 401 X-Reauth 로 변환(기존 동작) — middleware 가 더 이상 가로채지 않으므로 이 경로가 정상 작동.

## Failure Scenarios

- middleware 가 `/api/bff` 를 다시 게이팅하게 되면 회귀 재발(307 HTML → "실패"). AC-1/AC-2 가 회귀 검출 계약.
- `/api/bff` 를 public 처리해도 보안 저하 없음: 백엔드 게이트웨이가 인증을 강제하고, BFF 는 서버 세션에서만 bearer 를 첨부(클라이언트 토큰 비노출, F2 유지).
