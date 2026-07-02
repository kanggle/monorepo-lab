# TASK-FE-083-fix-001 — web-store 인증 미들웨어가 `/sw.js` 를 차단해 Service Worker 등록이 막히는 버그

- **Status**: done
- **DONE (2026-07-02, 3-dim verified — PR #2097 `dc9077608`)**: state=MERGED + tip 일치 + pre-merge failing=0(Frontend unit tests[Node20 middleware 테스트 포함]+lint&build+E2E smoke GREEN). `/sw.js` 공개 allowlist + matcher 제외로 SW 등록 차단 해소.
- **Project**: ecommerce-microservices-platform
- **Service**: web-store
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (직접)
- **IMPLEMENTED (2026-07-02)**: `middleware.ts` 공개 경로 `if` 에 `pathname === '/sw.js'` + `config.matcher` negative-lookahead 에 `sw.js` 추가. 신규 `src/__tests__/middleware.test.ts`(sw.js 통과 / 보호경로 307 / matcher 제외). 라이브 fed-e2e 재현으로 발견(앱 기동 전 미적발). ⚠️로컬 vitest 불가→CI Node20 권위.

## Goal

Fix bug found in **TASK-FE-083**: web-store 의 라우트 가드 미들웨어(`src/middleware.ts`)가 `/sw.js` 를 공개 경로 allowlist 에 포함하지 않아, Service Worker 스크립트 요청이 `/login` 으로 307 리다이렉트된다. Service Worker 스크립트는 인증 없이 받아져야 하는데(브라우저의 `navigator.serviceWorker.register('/sw.js')` 및 주기적 SW 업데이트 fetch), 미들웨어가 이를 가로채면 SW 등록/갱신이 실패해 FE-083 의 브라우저 푸시가 동작하지 않는다.

라이브 재현(2026-07-02, fed-e2e 스택): `curl http://localhost:3001/sw.js` → `307 → /login?from=%2Fsw.js`. 단위/컴포넌트 테스트는 미들웨어 매칭을 타지 않아 미적발 — 앱을 실제 기동해야 드러나는 통합 갭.

## Scope

**In scope** (web-store only):

1. `src/middleware.ts` — `config.matcher` 의 negative-lookahead 제외 목록에 `sw.js` 추가(미들웨어가 아예 실행되지 않도록; 1차 방어) + 미들웨어 본문 공개 경로 `if` 에 `pathname === '/sw.js'` 추가(2차 방어).
2. `src/__tests__/middleware.test.ts` (신규) — `/sw.js` 가 리다이렉트 없이 통과(미인증에도) + 보호 경로(`/cart`)는 미인증 시 `/login` 307 + `config.matcher` 가 `sw.js` 를 제외함을 단언.

**Out of scope**: 다른 정적 자산 정책, SW 캐싱 전략, 푸시 로직(FE-083 에서 완료).

## Acceptance Criteria

- **AC-1 — `/sw.js` 공개.** 미인증 요청도 `/sw.js` 가 `/login` 으로 리다이렉트되지 않는다(SW 등록/업데이트 fetch 가 스크립트를 정상 수신).
- **AC-2 — 가드 보존.** 기존 보호 경로(`/cart`, `/my`, `/checkout` 등)는 미인증 시 여전히 `/login?from=` 으로 307 리다이렉트된다(회귀 없음).
- **AC-3 — matcher 제외.** `config.matcher` 가 `sw.js` 를 제외하여 프로덕션에서 미들웨어가 SW 스크립트 요청에 대해 실행되지 않는다.
- **AC-4 — 게이트.** web-store 프런트 유닛(vitest) GREEN(신규 middleware 테스트 포함). ⚠️ 로컬 vitest 불가(Node24↔vitest4) → CI Node20 권위.

## Related Specs

- TASK-FE-083 — 원 구현(Service Worker `public/sw.js` 추가). 본 fix 가 그 SW 를 실제로 등록 가능하게 만든다.
- `specs/services/web-store/architecture.md` § Web Push (Service Worker) — SW 는 정적 서빙(비번들)이라는 서술과 정합.

## Related Contracts

- 없음(프런트 라우팅 가드 변경, 계약 무관).

## Edge Cases

- 로그인 상태에서 SW 등록: 세션 쿠키가 동봉되어 기존에도 통과할 수 있었으나(우연), 미인증 SW 업데이트 fetch·세션 만료 시 실패 → allowlist 로 결정적 보장.
- `/sw.js` 외 다른 경로는 영향 없음(제외 목록에 정확히 `sw.js` 만 추가).

## Failure Scenarios

- matcher 에서 `sw.js` 를 부정확한 정규식으로 추가하면 다른 경로(예: `/xsw.js...`)를 의도치 않게 제외할 수 있음 → 기존 항목과 동일하게 `|` 로 정확히 `sw.js` 만 추가하여 방지.
