# Task ID

TASK-PC-FE-120

# Title

console-web 인증 속도 향상: `/api/auth/refresh`의 operator 재교환 + assume-tenant 재assume **직렬 → 병렬** — 서로 독립인 두 RFC 8693 교환을 회전된 base 토큰에서 동시 발사해 refresh 지연을 `oidc + operator + assume` → `oidc + max(operator, assume)`로 단축 (cold-start 56초 캐스케이드의 operator+assume 직렬 구간 제거)

# Status

review

# Owner

frontend (Opus 4.8 분석·구현 — 인증 라우트 SSR 워터폴 제거, security-critical 토큰 경계 behavior-preserving; contract/spec/backend 무변경)

# Task Tags

- code
- test
- performance

---

# Dependency Markers

- **builds on**: TASK-PC-FE-117/118(SSR fetch 병렬화 — 동일한 "독립 fetch 동시 발사" 패턴을 인증 라우트로 확장). ADR-MONO-014 D2(operator 재교환 모델, no operator-refresh state). ADR-MONO-020 D4 / §2.7(assume-tenant 재assume). TASK-BE-311(쿠키 SameSite=Lax).
- **memory-link**: 메모리 `env_console_cold_start_timeout_cascade` — "56s 로그인(TOKEN_EXCHANGE_TIMEOUT_MS=operator+assume 둘 다)". 그 operator+assume **직렬 구간**이 바로 이 refresh 라우트다. cold backend에서 operator(최대 5s) + assume(최대 5s)가 순차 합산 → 본 task가 이를 `max`로 접어 캐스케이드의 한 축을 단축(나머지=백엔드 워밍/B-1 env override, 무관).
- **independence 근거(핵심)**: `exchangeForOperatorToken`(admin-service `/api/admin/auth/token-exchange`, JSON `{accessToken,expiresIn,tokenType:'admin'}`)와 `exchangeForAssumedToken`(SAS `/oauth2/token`, form-urlencoded + `audience`, Bearer shape)는 **서로 다른 서비스·다른 와이어**이고 **둘 다 회전된 base `access_token`에만 의존**(operator 결과가 assume 입력이 아니며 그 역도 아님). 따라서 동시 발사 안전.
- **scope-bounding**: 초기 로그인 콜백(`/api/auth/callback`)은 `oidc-token → operator`가 **의존 직렬**(operator가 access_token 필요)이고 assume이 없으므로(home-tenant=base 토큰, PC-FE-036) **병렬화 불가·이미 최적** → 무변경.

# Goal

`/api/auth/refresh`(POST)에서 IAM refresh_token grant 성공 후 수행하는 두 재교환을 직렬에서 병렬로 전환한다:

- **현재(직렬)**: `await exchangeForOperatorToken(access_token)` → (activeTenant 있으면) `await exchangeForAssumedToken(access_token, activeTenant)`. 지연 = `oidc + operator + assume`.
- **변경(병렬)**: 두 교환을 회전된 `access_token`에서 **동시 발사**, operator를 먼저 await(fatal 게이트 — 실패 시 전체 세션 드롭 + 401)하고, 이어 in-flight assume을 await(non-fatal — 실패 시 tenant 쌍만 드롭). 지연 = `oidc + max(operator, assume)`.

behavior-preserving: 모든 분기의 관찰 결과(쿠키 최종 상태, status code, 드롭 시맨틱)는 불변. 변경은 **두 교환의 시작 시점**(순차 → 동시)뿐.

# Scope

## In Scope

- **`src/app/api/auth/refresh/route.ts`** — IAM refresh 성공 후:
  - `const operatorPromise = exchangeForOperatorToken(data.access_token);`
  - `const assumePromise = activeTenant ? exchangeForAssumedToken(data.access_token, activeTenant) : null;`
  - **unhandled-rejection 가드**: operator 실패 early-return 경로에서 assumePromise를 await하지 않으므로, `if (assumePromise) void assumePromise.catch(() => {});`로 핸들러를 선부착(이후 성공 경로의 `await assumePromise` try/catch가 실제 처리; 동일 promise 다중 핸들러 안전). `exchangeForAssumedToken`은 throw하므로(domain-health와 달리) 이 가드 필수.
  - operator를 먼저 `await operatorPromise`(기존 fatal try/catch: 401 fail-closed / unavailable → 전체 세션 쿠키 드롭 + 401). 성공 시 OPERATOR_COOKIE set.
  - 이어 `if (assumePromise)` 성공 경로에서 `await assumePromise`(기존 non-fatal try/catch: 실패 → ASSUMED_TOKEN_COOKIE + TENANT_COOKIE 드롭). 성공 시 ASSUMED_TOKEN_COOKIE set.
  - 쿠키 set/delete 시맨틱·순서상 최종 상태 불변(operator/assumed는 서로 다른 쿠키; 각 await 후 set).
- **Tests** (`tests/unit/auth-refresh-parallel.test.ts`, new) — 동시 발사 증명 + 기존 분기 무회귀 보강:
  - **concurrency 증명**: IAM refresh 성공 + operator 교환 fetch를 **pending(deferred)** 으로 두고, assume 교환 fetch가 **operator 미완료 상태에서도 호출됨**을 단언(직렬 회귀 시 assume은 operator resolve까지 미호출 → 0). 단언 후 operator deferred를 resolve해 라우트 정상 종료(AbortController 5s 타이머 잔류 방지).
  - operator-fail + activeTenant 동시 케이스(기존 테스트 미커버): operator 401 → 전체 세션 드롭 + 401, 투기 발사된 assume promise가 unhandled rejection 없이 무시됨(ASSUMED/TENANT 쿠키도 드롭, 스테일 assumed 토큰 미설정).
- **기존 `tests/unit/auth-routes.test.ts`** — refresh 6개 케이스 전부 **무수정 통과** 목표(behavior-preserving 검증). 변경 불필요해야 정상.

## Out of Scope

- 초기 로그인 콜백(`/api/auth/callback`) — oidc-token→operator 의존 직렬, 이미 최적(무변경).
- OIDC `/oauth2/token` fetch에 AbortController/timeout 부재(callback line 110 / refresh line 72) — 행 IdP worst-case 캡 robustness 항목이나 **실패 동작 변경**이 따르므로 별건(본 task는 순수 behavior-preserving 병렬화만).
- `exchangeForOperatorToken` / `exchangeForAssumedToken` 내부 로직, TOKEN_EXCHANGE_TIMEOUT_MS 값 튜닝(env, 운영), 백엔드/계약/스펙.

# Acceptance Criteria

- [ ] `/api/auth/refresh` 성공(activeTenant 있음) 시: operator 재교환과 assume 재assume이 **동시에 시작**되고(operator pending 중에도 assume fetch 발생), 최종 쿠키 상태(ACCESS/REFRESH/OPERATOR/ASSUMED/TENANT)가 기존과 동일.
- [ ] operator 재교환 401/unavailable → 전체 세션 쿠키 드롭 + 401 (기존 시맨틱 불변). activeTenant가 있어 assume이 투기 발사됐어도 unhandled rejection 없이 무시되고 스테일 assumed 토큰을 남기지 않는다.
- [ ] assume 재assume 실패 → ASSUMED_TOKEN_COOKIE + TENANT_COOKIE만 드롭, base+operator 세션 유지(200) (기존 시맨틱 불변).
- [ ] activeTenant 없음 → assume 미발사(promise=null), operator만 재교환 (기존 시맨틱 불변).
- [ ] 기존 `auth-routes.test.ts`의 refresh 6 케이스 전부 무수정 통과.
- [ ] `pnpm exec vitest run` green(무회귀), `npx tsc --noEmit` clean, `pnpm lint` clean. scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.6(operator exchange)/§2.7(assume-tenant) — read-only 소비, 변경 없음.
- `iam/specs/contracts/http/admin-api.md` §token-exchange / `auth-api.md` §Assume-Tenant Exchange — producer 계약, 소비만.

# Related Contracts

- 변경 없음. 동일 두 교환을 동일 입력·동일 횟수로 호출(시작 시점만 동시화); 와이어·토큰 경계 불변.

# Target Service

- `platform-console` / `apps/console-web` — `app/api/auth/refresh/route.ts` 1개 nodejs 라우트 핸들러. security-critical 토큰 경계 behavior-preserving 성능 최적화.

# Architecture

- 인증 라우트 SSR 워터폴 제거 — PC-FE-117/118의 "독립 fetch 동시 발사" 패턴을 토큰 재교환에 적용. operator(fatal 게이트)와 assume(non-fatal, tenant-coupled)은 회전된 base 토큰에만 의존하는 독립 RFC 8693 교환이므로 동시 발사 후 fatal 쪽을 먼저 await해 게이트. assume은 throw하는 교환이라 early-return 경로 unhandled-rejection 가드(`void promise.catch(()=>{})`) 필수 — domain-health(non-throw) 케이스와의 차이. 토큰 경계(§2.6 operator ↔ §2.7 assume)·fail-closed 시맨틱은 전부 보존(병렬화는 시작 시점만 바꾸고 결과 처리·드롭 규칙은 동일).

# Edge Cases

- 성공 + activeTenant: operator∥assume 둘 다 성공 → 두 쿠키 최신화, tenant 유지(`max` 지연).
- operator 실패 + activeTenant: 투기 assume 발사됨 → operator await에서 throw → 전체 드롭 + 401; assume promise는 `.catch` 가드로 무시(reject든 resolve든 결과 미사용, ASSUMED/TENANT도 명시 드롭).
- operator 성공 + assume 실패: operator 쿠키 set 후 assume await throw → tenant 쌍 드롭, 200.
- activeTenant 없음: assumePromise=null → operator만, 기존과 동일.
- 두 교환 중 느린 쪽이 refresh 지연 지배(`max`); cold backend에서 operator+assume 5s+5s 직렬이 ~5s로 접힘.

# Failure Scenarios

- operator-fail 경로에서 투기 assume promise가 unhandled rejection → 프로세스 경고/로그 오염: `void assumePromise.catch(()=>{})` 선부착으로 차단; 신규 테스트가 operator-fail+activeTenant 케이스로 가드.
- 병렬화가 fatal 게이트를 무너뜨림(assume 결과로 세션 판정) → operator만 fatal 게이트, assume은 절대 세션 전체를 좌우 못 함(기존과 동일); AC가 분기별 쿠키 시맨틱 불변 단언.
- 쿠키 set 순서 경쟁으로 스테일 토큰 잔류 → operator/assumed는 서로 다른 쿠키 키, 각 await 후 set, operator-fail 시 둘 다 명시 드롭 → 경쟁 없음.
- 기존 refresh 테스트 회귀 → behavior-preserving 실패 신호; AC가 6 케이스 무수정 통과 요구.
- lint(no-unused-vars / no-floating-promises 류) → CI 프런트 잡 RED: push 전 `pnpm lint` 필수.

# Definition of Done

- [ ] refresh operator+assume 재교환 동시 발사(direct 워터폴 제거), 모든 분기 behavior-preserving
- [ ] 초기 로그인 콜백 무변경(이미 최적), 토큰 경계·fail-closed 시맨틱 보존
- [ ] 기존 auth-routes refresh 6 케이스 무수정 통과 + 신규 동시성/엣지 테스트 green
- [ ] vitest + tsc + lint green, 무회귀; scope = console-web only
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
