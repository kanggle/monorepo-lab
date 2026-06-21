# TASK-PC-FE-116 — console-web 미커버 순수/crypto shared·feature lib 단위테스트

- **Status**: ready
- **Project**: platform-console
- **App**: console-web (Next.js)
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (test-only, 동작 무변경)

## Goal

console-web 의 단위테스트는 `tests/unit/**` 에 ~90건 존재하나(API 클라이언트·proxy·state·screen 위주), **순수 함수/crypto 프리미티브 일부가 무커버**다. 본 task 는 회귀 시 *조용히 깨지는* 미커버 순수 로직 4종에 vitest 단위테스트를 추가한다. 코드(소스) 변경 0 — **테스트만 추가**.

대상(전부 기존 테스트 부재 확인):

1. `src/shared/lib/pkce.ts` — PKCE(RFC 7636) + OAuth state. OIDC Auth Code 흐름의 보안 프리미티브. verifier 엔트로피·`S256` challenge 파생·base64url 패딩제거가 회귀하면 로그인이 조용히 깨진다(가장 가치 높음).
2. `src/shared/lib/pagination.ts` — `clampPageSize` 경계 클램프([1,max] + default).
3. `src/features/tenant/lib/tenant-options.ts` — `selectableTenants` (available 필터 + tenant union dedup + sort; multi-tenant M4 "set 을 넓히지 않는다" 불변).
4. `src/features/catalog/lib/console-route.ts` — `resolveConsoleRoute` (`iam` → `/accounts`, 그 외 `baseRoute` passthrough).

## Scope

**In scope** (console-web only, 신규 테스트 파일만):

1. `tests/unit/pkce.test.ts` — `generateCodeVerifier` (base64url charset·패딩없음·길이≥43), `deriveCodeChallenge` (RFC 7636 Appendix B 알려진 벡터 + 결정성), `generateState` (base64url·비공백·매 호출 상이).
2. `tests/unit/pagination.test.ts` — `clampPageSize` (default fallback, clamp-down to max, clamp-up to 1, 0/음수 → 1, 정상값 passthrough).
3. `tests/unit/tenant-options.test.ts` — `selectableTenants` (dedup, unavailable 제외, sort, 빈 입력, available=false 만 있을 때 빈 set).
4. `tests/unit/console-route.test.ts` — `resolveConsoleRoute` (iam→/accounts, wms/scm/erp/finance/ecommerce→baseRoute passthrough).

**Out of scope**: 소스 코드 변경 일체, 신규 기능, 이미 커버된 모듈(jwt/assume-tenant/operator-token/tone/empty-state) 재테스트, web-store/fan-platform-web(별 프로젝트·web-store 는 vitest4×Node24 로컬 미기동).

## Acceptance Criteria

- **AC-1 — pkce 정확성.** `deriveCodeChallenge('dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk')` === `'E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM'` (RFC 7636 §B 벡터). verifier/state 가 base64url(`[A-Za-z0-9_-]+`, `=` 패딩 없음)·verifier 길이 ≥ 43.
- **AC-2 — 경계.** `clampPageSize`: default/0/음수/초과/정상 5 분기 모두 정확.
- **AC-3 — tenant set.** `selectableTenants`: dedup + unavailable 제외 + 사전순 정렬 + 빈 입력 → `[]`.
- **AC-4 — route.** `resolveConsoleRoute`: iam→`/accounts`, 그 외 5 product → 각 `baseRoute`.
- **AC-5 — 3 게이트.** `pnpm lint` clean + `npx tsc --noEmit` clean + `pnpm exec vitest run` 전건 GREEN(신규 + 기존 무회귀). 소스 diff 0(테스트 파일만 추가).

## Related Specs

- `console-integration-contract.md` § 2.2 (registry product 형태 — tenant-options/console-route 픽스처 근거), § 2.4.1 (iam→accounts parity 바인딩).
- ADR-MONO-013 § 3 (parity "accounts") — console-route iam 바인딩 근거.
- RFC 7636 §4.1/§4.2/§B — pkce verifier/challenge/테스트 벡터.

## Related Contracts

- 없음(소비 코드 무변경 — 순수 로직 회귀 테스트 추가만).

## Edge Cases

- pkce `crypto.subtle`/`crypto.getRandomValues`: Node 20+ 글로벌 webcrypto(vitest jsdom env)에서 가용 — 테스트가 실 crypto 사용(목 불요). 결정적 단언은 알려진 벡터로.
- `selectableTenants` available=false product 의 tenants 는 절대 포함 금지(M4 격리).
- `clampPageSize(0,...)` / 음수 → 1 (Math.max(1, ...)); `undefined` → defaultSize 경유 후 클램프.

## Failure Scenarios

- 없음(런타임 동작 무변경, 소스 unchanged). 본 테스트 자체가 미래 회귀 검출 장치다.
