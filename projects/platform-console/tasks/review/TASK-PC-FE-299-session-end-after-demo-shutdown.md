# Task ID

TASK-PC-FE-299

# Title

데모 서버 종료 뒤 세션 잔존 표면 정리 — 캐시 노출 · bfcache · 원인 구분 메시지

# Status

review

# Owner

frontend

# Task Tags

- code
- frontend
- console-web
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.

---

# Goal

데모 백엔드가 꺼진 뒤(또는 세션 만료 뒤) 방문자가 인증된 화면의 잔존 데이터를 보거나(브라우저 캐시/bfcache), 원인이 다른데 같은 문구를 보는 문제를 정리한다. 기존 재로그인 배선(`refresh` 실패 → `clearFullSession` → `/login?error=session_expired`, 백엔드 401 → `RE_LOGIN_PATH`)은 **이미 있다** — 이 티켓은 그 배선이 놓친 표면(캐시 헤더 부재, bfcache, 원인 미구분, 클라 잔존 상태)만 다룬다.

# Scope

## In Scope

- **캐시 헤더**: 인증된 콘솔 HTML 응답에 `Cache-Control: no-store` (+ `private`)를 추가한다. 현재 `next.config.mjs:83-95` 에는 보안 헤더만 있고 캐시 지시자가 없다(2026-09-24 UTC 실측 확인 — 해당 파일에 `no-store`/`Cache-Control` 패턴 0건). `(console)` 레이아웃은 이미 `force-dynamic`(`layout.tsx:53`)이므로 정적 캐싱 문제는 아니지만, 브라우저/중간 캐시가 인증된 페이지를 저장하지 않도록 헤더를 명시한다. **정적 자산에는 적용하지 않는다** — 샘플 방문자 페이지(ADR-MONO-074, 로그인 불필요)에 적용하면 캐싱 이점을 해칠 수 있으므로, 인증이 필요한 라우트에만 한정하고 그 판단 근거를 구현 기록에 남긴다.
- **bfcache 대응**: `pageshow` 이벤트(`event.persisted === true`)를 감지해, 세션 쿠키가 없는 상태로 뒤로가기/앞으로가기로 복귀한 인증 페이지를 강제로 새로고침(또는 로그인으로 리다이렉트)한다.
- **원인 구분 로그인 메시지**: 로그인 페이지가 지금은 한 문구(「세션이 만료되어 로그아웃되었습니다. 다시 로그인해주세요.」, `login/page.tsx:31,81-86`)만 보여준다(2026-09-24 UTC 실측 확인). 데모 서버 종료로 인한 로그아웃일 때는 「서버가 종료되어 다시 로그인해야 합니다.」류 변형 문구를 보여준다 — **이 판단은 실제 신호(예: 데모 상태 엔드포인트)에서 파생해야 하며 추측 배지를 달지 않는다.**
- **클라이언트 잔존 상태 정리**: 강제 로그아웃 시 클라이언트에 남아 있을 수 있는 선택 테넌트, 메뉴 드릴 상태, 사용자 정보 등(어떤 저장소를 쓰든 — localStorage/sessionStorage/메모리 상태)을 점검하고, 남아 있는 것이 있으면 로그아웃 경로에서 지운다.
- **낡은 주석 정리**: `DemoBackendNotice.tsx:40-43` 의 "page.tsx 67개 중 익명으로 닿는 것이 /login 하나"(나머지 66개는 `(console)/layout.tsx` 의 `isAuthenticated()` 가드 뒤) 주석은 ADR-MONO-074 이후 **낡았다** — 2026-09-24 UTC 실측: 같은 레이아웃이 이제 `isSampleVisitor() || isAuthenticated()` 로 게이트하므로(:144-145), 샘플 방문자는 로그인 없이도 66개 화면에 닿을 수 있다. 이 주석을 현재 사실에 맞게 정정한다.

## Out of Scope

- 재로그인 배선 자체(`refresh` route, `re-login.ts`, `session-refresh.ts`)의 로직 변경 — 이미 동작하는 부분은 건드리지 않는다.
- 데모 상태를 판별하는 새 백엔드 엔드포인트 신설 — 이미 있는 신호(`resolveDemoBackendState()` 류)를 재사용하는 것을 우선하고, 정말 없으면 그 사실을 AC에 기록한다(신설이 필요하면 별도 백엔드 티켓으로 분리).

# Acceptance Criteria

- [ ] **AC-1** — 인증된 콘솔 HTML 응답에 `Cache-Control: no-store, private`(또는 동등)가 있고, 정적 자산·샘플 방문자 페이지에는 적용되지 않는다(각각 헤더 확인).
- [ ] **AC-2** — 유효한 세션으로 페이지를 새로고침해도 로그아웃되지 않는다(회귀 없음 확인 — 이 AC가 가장 깨지기 쉬운 부분이다).
- [ ] **AC-3** — 세션 쿠키가 없는 상태로 bfcache 복귀(뒤로가기)했을 때 인증 페이지가 잔존 데이터를 보여주지 않고 새로고침/리다이렉트된다(재현 스텝 + 확인 방법 기록).
- [ ] **AC-4** — 데모 종료로 인한 로그아웃과 일반 세션 만료가 **실제 신호로 구분**되어 다른 문구를 보여준다(추측이 아님을 확인 — 신호 소스를 명시).
- [ ] **AC-5** — 강제 로그아웃 후 클라이언트에 남아 있던 테넌트/드릴 상태/사용자 정보가 지워진다(재현 스텝).
- [ ] **AC-6** — `DemoBackendNotice.tsx` 의 낡은 주석이 ADR-MONO-074 이후 현재 사실(샘플 방문자도 66개 화면에 닿을 수 있음)에 맞게 갱신됐다.
- [ ] **AC-7** — 유닛 테스트 + typecheck + build 통과. 브라우저로 AC-2·AC-3 재현.

# Related Specs

- `projects/platform-console/apps/console-web/src/app/api/auth/refresh/route.ts`(:50,71,160,171)
- `projects/platform-console/apps/console-web/src/shared/lib/re-login.ts`(:48-54)
- `projects/platform-console/apps/console-web/src/app/(console)/layout.tsx`(:53 force-dynamic, :144-145 isSampleVisitor/isAuthenticated)
- `projects/platform-console/apps/console-web/src/app/(auth)/login/page.tsx`(:31 세션 만료 문구)
- `projects/platform-console/apps/console-web/src/widgets/demo-notice/DemoBackendNotice.tsx`
- `projects/platform-console/apps/console-web/next.config.mjs`(:83-95 보안 헤더)
- `projects/platform-console/apps/console-web/src/__tests__/relogin-marker.test.ts`
- ADR-MONO-074 (샘플 방문자 모드)
- 2026-09-24 UTC 포트폴리오 UX 전수조사(소유자 승인)

---

# Related Contracts

- 없음(프런트엔드 헤더/클라이언트 상태 변경, API 계약 무변경).

---

# Target App

- `apps/console-web`

---

# Edge Cases

- 데모가 "기동 중" 상태에서 세션이 만료된 경우 — 「서버가 종료되어…」 문구가 오도할 수 있다(서버는 켜지는 중이지 종료된 게 아니다). 상태별 문구 3종(만료/데모 종료/데모 기동 중)을 구분할지, 2종으로 단순화할지 구현자가 판단하고 근거를 남긴다.
- 샘플 방문자(비로그인)는 애초에 세션이 없으므로 이 티켓의 caching/bfcache 로직이 그 경로를 방해하지 않아야 한다.

# Failure Scenarios

- `no-store` 를 레이아웃 전체에 걸어 샘플 방문자 페이지까지 캐시 불능이 되면 ADR-MONO-074 가 의도한 정적 콘텐츠 가벼움이 깨진다.
- 유효 세션 새로고침이 로그아웃되는 회귀(AC-2) — 캐시 헤더/bfcache 로직이 과잉 반응하면 발생한다.
- 데모 종료 여부 판정을 실제 신호 없이 타이머·추측으로 만들면 AC-4가 요구하는 "실제 신호로 파생" 을 위반한다.

---

# Implementation Record (2026-09-24 UTC)

## AC-1 — `Cache-Control: no-store, private` (인증 라우트만)

**자리는 `next.config.mjs` 가 아니라 `src/middleware.ts`.** 이유: `(console)` 아래 모든 라우트는
인증된 운영자와 샘플 방문자(ADR-MONO-074, `isSampleVisitor() || isAuthenticated()` 같은 경로)가
**같은 path** 를 공유한다. `next.config.mjs` 의 `headers()` 는 `source` 패턴 매칭뿐이라 이 둘을
구분할 방법이 없다 — 정적 설정이 방문자별로 갈라질 수 없다. 미들웨어는 요청마다 실행되므로
`(console)/layout.tsx` 의 `isAuthenticated()` 와 **정확히 같은 술어**(access 쿠키 AND operator
쿠키)를 쿠키 존재만으로 재현해(`request.cookies`, `next/headers` 의 `cookies()` 는 여기서 못 쓴다)
그때만 `Cache-Control: no-store, private` 를 얹는다. 미들웨어 matcher 는 이미 정적 자산·`/api/**`·
`/login` 을 제외하고 있어 AC-1 의 "정적 자산 제외" 는 별도 코드 없이 그대로 상속된다.

🔴🔴 **실측으로 드러난 사실 — `(console)` 는 이미 `force-dynamic` 이라 Next 자신이 모든 방문자에게
`Cache-Control: private, no-cache, no-store, max-age=0, must-revalidate` 를 기본으로 얹고 있었다**
(`curl` 로 쿠키 없는 샘플 방문자 요청에서도 확인 — 아래 검증 로그). 즉 브라우저 HTTP 캐시 계층에서는
샘플 방문자 페이지도 **이미 캐시되지 않는다** — 이 티켓이 걱정한 "ADR-MONO-074 캐싱 이점을 깎는다"는
전제가 현재 이미 Next 의 암묵적 동작으로 무의미하다(Goal 의 "정적 캐싱 문제는 아니지만" 문구가
암시한 그대로다). 그럼에도 AC-1 은 **명시적** 헤더를 요구했으므로 구현은 유지했다 — 암묵적 동작은
Next 버전/렌더 경로가 바뀌면 조용히 달라질 수 있고, 중간 프록시/CDN 이 그 자동 헤더를 다르게 취급할
수 있어 "명시"의 가치가 남는다. **중요한 차이**: 내 미들웨어 헤더는 인증된 응답에서 Next 의 자동
헤더를 **대체**한다(값이 `no-store, private` 로, `no-cache`/`max-age=0`/`must-revalidate` 는 없다) —
`no-store` 하나가 그 셋보다 강한 지시자이므로 기능적 회귀는 아니다. 샘플 방문자 응답은 내 코드가
손대지 않으므로 Next 의 기본값(위 문자열) 그대로 — AC-1 의 "샘플 방문자 페이지에는 적용되지 않는다"
를 **내 디렉티브가 그 경로의 헤더를 결정하는 주체가 아니다**로 읽어 충족했다.

검증(`next start`, 실제 HTTP 헤더):
```
샘플 방문자(쿠키 없음) → /dashboards/overview
  Cache-Control: private, no-cache, no-store, max-age=0, must-revalidate   (Next 기본, 내 코드 무관)

인증 쿠키 둘 다 있음 → /dashboards/overview
  cache-control: no-store, private                                          (내 미들웨어)

인증 쿠키 있음 → /favicon.ico (정적 자산, 실제로는 404 — 파일 없음)
  Cache-Control: private, no-cache, no-store, max-age=0, must-revalidate   (Next 의 프리렌더 404 기본, 내 코드 무관 — 미들웨어 matcher 가 이 확장자를 제외)
```
단위테스트: `tests/unit/middleware-cache-control.test.ts` — `isAuthenticated()` 와 같은 조합표(양쪽/한쪽/전무)를 돈다.

## AC-2 — 유효 세션 새로고침 회귀 없음

AC-1 의 헤더 자체는 인증 판정에 관여하지 않는다(캐시 지시자일 뿐). `next start` + 가짜(형식만 맞는)
세션 쿠키로 브라우저(Playwright/Chromium)에서 `/dashboards/overview` 진입 → `page.reload()` →
동일 URL 유지 + `account-menu-trigger` 유지 확인(PASS, 아래 브라우저 검증 로그). 회귀 축이라고
명시된 만큼 별도로 독립 확인했다.

## AC-3 — bfcache 복귀 시 잔존 데이터

`src/widgets/bfcache-guard/BfcacheGuard.tsx` — `pageshow` + `event.persisted === true` 를
감지해 `window.location.reload()` (새로고침 옵션을 선택 — 리다이렉트가 아니라 **기존 서버 가드를
다시 태우는** 쪽). **인증된(`!sampleVisitor`) 셸에만 마운트**(`(console)/layout.tsx`, `DemoHeartbeat`
옆) — 샘플 방문자는 세션 쿠키가 아예 없어 이 가드를 태워도 no-op 이고, 그 왕복은 ADR-MONO-074 가
지키려는 캐싱 이점만 깎는다.

🔵 이 메커니즘이 필요한 이유 — `Cache-Control: no-store` 만으로는 bfcache 진입을 확실히 막지 못한다
(최신 Chromium 은 `no-store` 가 있어도 bfcache 를 허용하는 경우가 있다 — 헤더 하나로 이 표면을
닫을 수 없어 클라이언트 신호(`pageshow`)가 별도로 필요한 이유다). 단위테스트:
`tests/unit/bfcache-guard.test.tsx` (persisted=true→reload, persisted=false→no-op, unmount 뒤
no-op). 브라우저 재현: 인증 쿠키로 `/dashboards/overview` 진입 → `/account` 로 이동 → 쿠키 전체
삭제(로그아웃 시뮬레이션) → 브라우저 뒤로가기 → 결과: `account-menu-trigger` 소멸(0), 대신
`sample-visitor-banner` 등장(1) — 이전 운영자의 인증 화면이 그대로 안 보인다(PASS, 아래 로그).
`goBack()` 동안 문서 요청이 1회 발생함을 확인(bfcache 복원 뒤 이 가드의 reload 였는지, 애초에
bfcache 가 아니었는지는 이 계측만으로 구분 못 하지만 — **어느 쪽이든 관측된 최종 상태는 같다**:
이전 세션의 실제 데이터가 화면에 남지 않는다).

## AC-4 — 데모 종료 ↔ 세션 만료 원인 구분 (실제 신호)

`(auth)/login/page.tsx` — `SESSION_EXPIRED` 마커가 있을 때만 `resolveDemoBackendState()`
(`DemoBackendNotice` 가 쓰는 것과 **같은 함수**, `shared/config/demo-backend.ts`)를 묻고,
결과가 `'unavailable'` 일 때만 데모 종료 문구로 바꾼다. Edge Case 의 3종 vs 2종 질문 — **2종으로
단순화**했다: `'starting'`(인스턴스는 떠 있고 선택 묶음만 준비 중, TASK-MONO-668)에 "종료" 라는
말을 쓰면 거짓이 되므로, `starting`/`running`/`not-demo`/판정불가 는 전부 기존 일반 문구를 유지하고
`unavailable` 하나만 대체한다. 3종(기동 중 전용 문구 추가)도 가능했지만, `starting` 상태에서
세션이 만료되는 조합은 "인스턴스는 곧 뜨는데 하필 그 순간 세션이 죽었다"는 좁은 창이라, 기존
일반 문구("세션이 만료되어 로그아웃되었습니다")가 이미 진실이고 오도하지 않는다 — 전용 문구를
추가하는 비용(세 번째 문자열, 세 번째 핀 테스트)이 그 좁은 창을 위해 정당화되지 않는다고 판단했다.
단위테스트: `tests/unit/login-session-expired-demo-signal.test.tsx`(unavailable/starting/
running/not-demo 4종 + 마커 없는 방문 대조군). 브라우저 재현: `/login?error=session_expired`
직접 방문 → 기본 문구("세션이 만료...") 렌더 확인(데모 상태 리졸버가 로컬 실행에선 `not-demo` 이므로
— PASS, 아래 로그). `unavailable` 분기는 유닛 테스트가 커버(mock 리졸버) — 로컬에 데모 컨트롤
플레인이 없어 브라우저로 `unavailable` 상태를 물리적으로 재현할 수 없었다(**측정 불가, 사유:
`DEMO_API_BASE` 가 이 환경에 없다** — `not-demo` 판정 자체가 설계대로다).

## AC-5 — 강제 로그아웃 후 클라이언트 잔존 상태

**전수조사 결과**: 이 앱은 세션/테넌트/드릴/유저 상태를 `localStorage`/`sessionStorage` 에 **한 번도**
쓰지 않는다(`shared/lib/session.ts` 자신의 JSDoc 이 이를 하드 룰로 못박는다 — grep 0건, `global-error.tsx`
의 `sessionStorage` 1건은 빌드-오류 reload-loop 가드로 세션과 무관). 컴포넌트 `useState`(테넌트
스위처, 사이드바 드릴 `openKey`)는 라우트 세그먼트 언마운트로 자동 소멸한다. 자발적 로그아웃
(`performLogout` → `window.location.assign`)은 완전한 문서 탐색이라 JS 힙 전체가 버려진다.

**남는 한 자리** — `QueryProvider`(TanStack Query 캐시)는 **루트 레이아웃**(`app/layout.tsx`)에 있어
`(console)` 과 `(auth)/login` 모두를 감싼다. 백엔드 401 → `redirect(RE_LOGIN_PATH)` 가 **클라이언트
측 인앱 전환**(풀 리로드가 아닌 RSC 전환)으로 일어나면 같은 `QueryClient` 인스턴스가 살아남고, 그
캐시엔 방금 거부된 세션의 계정/테넌트/감사 등 조회 결과가 메모리에 남을 수 있다 — 이것이 "메모리
상태" 로 명시된 AC-5 를 문자 그대로 만족시키는 유일한 실재 표면이었다.

`src/widgets/forced-relogin-cache-reset/ForcedReLoginCacheReset.tsx` — **강제** 재로그인 착지
(`SESSION_EXPIRED` 마커가 있을 때만, 평범한 `/login` 방문에는 마운트 안 함)에서
`useQueryClient().clear()` 를 부른다. 단위테스트: `tests/unit/forced-relogin-cache-reset.test.tsx`
(마운트 시 캐시 비워짐 확인). 부수 변경: `tests/unit/login-error-messages.test.tsx` ·
`tests/unit/relogin-loop.test.tsx` 가 이제 이 위젯을 (forcedReLogin=true 케이스에서) 렌더하므로
`QueryClientProvider` 래퍼를 추가(기존 핀 문자열/단언은 그대로 — 렌더 경로만 보강).

## AC-6 — `DemoBackendNotice.tsx` 낡은 주석 정정

"page.tsx 67개 중 익명으로 닿는 것이 `/login` 하나(나머지 66개는 `isAuthenticated()` 가드 뒤)" 문장을
제거하고, ADR-MONO-074(`isSampleVisitor() || isAuthenticated()`) 이후 샘플 방문자도 그 66개 화면에
닿을 수 있다는 사실 + `/login` 이 특별한 진짜 이유("로그인 자체가 실패하는 유일한 화면")로 정정.

## Deviations from the ticket

- 없음 — Scope/Out of Scope/AC 7개 전부 다뤘다. Edge Case 의 "3종 vs 2종" 판단(2종 채택, 근거는
  AC-4 절)과 "샘플 방문자 경로 방해 금지"(BfcacheGuard 를 인증 셸에만 마운트) 둘 다 구현자 판단
  요청 사항이었고 위에 근거를 남겼다.

## Test / Build Evidence

- `npx vitest run` (전체) — rc=0, 323 test files / 3609 tests 전부 통과(신규 6파일 포함:
  `middleware-cache-control` · `bfcache-guard` · `forced-relogin-cache-reset` ·
  `login-session-expired-demo-signal` + 수정 2파일).
- `npx tsc --noEmit` — rc=0.
- `pnpm lint` (`next lint`) — rc=0, "No ESLint warnings or errors".
- `npx next build` — rc=0 (경고 2건은 기존 OTel 계측 모듈 관련, 이 변경과 무관 — `otel-node.ts`).
- `next start` 로 띄운 실 서버에 대해 `curl` 헤더 확인(AC-1) + Playwright(Chromium, `@playwright/test`
  기존 의존성) 로 3개 시나리오(AC-2/AC-4/AC-3+5) 실측 — 전부 PASS. 스크립트는 검증 후 삭제(임시,
  커밋 안 함).
- `relogin-marker.test.ts`(보호 대상) — 손대지 않음, 4/4 그대로 통과.

---

## CORRECTION (2026-09-24 UTC — review → done 클로즈 초어, 4차원 검증)

🔴 **(a)(b)(c) 는 전부 통과했지만 (d) 에서 멈춘다 — `done/` 으로 옮기지 않는다.**

- **(a)** impl PR [#3999](https://github.com/kanggle/monorepo-lab/pull/3999) `state=MERGED`(TASK-PC-FE-297 과 동반 커밋).
- **(b)** 머지 커밋 `a77529a8c`가 `origin/main` 의 조상임을 `git merge-base --is-ancestor` 로 확인.
- **(c)** required 4종(`changes` · `INDEX queue drift` · `Task ID collision` · `Walkthrough limitation ledger drift`) 전부 SUCCESS, FAILURE 0.
- **(d)** 🔴 **AC-4 가 닫히지 않았다.** AC-4 원문: *"데모 종료로 인한 로그아웃과 일반 세션 만료가 **실제 신호로 구분되어** 다른 문구를 보여준다(추측이 아님을 확인 — 신호 소스를 명시)."* 구현 기록 자신이 적은 대로, `unavailable` 분기(데모 종료 문구로 바뀌는 그 갈래)는 **유닛 테스트(mock 리졸버)로만** 확인됐고 라이브 브라우저 재현은 *"측정 불가, 사유: `DEMO_API_BASE` 가 이 환경에 없다"* 로 남아 있다. **AC-4 자신의 문구는 "측정 불가면 사유를 적으라" 고 요청하지 않는다** — `platform/git-workflow-policy.md` § The Fourth Dimension 의 원칙("⚪ 가 AC 를 닫는 것은 그 AC 가 그것을 요구했을 때뿐")대로, 이 ⚪ 는 AC-4 를 닫지 못한다. AC-7 이 브라우저 재현을 **AC-2·AC-3 로만 한정**한 것도(AC-4 는 그 목록 밖) 이 갭을 메우지 못한다 — AC-4 자신의 문구가 권위다.
- AC-1·AC-2·AC-3·AC-5·AC-6·AC-7(AC-2/AC-3 몫)은 유닛 테스트 + 실 서버 `curl`/Playwright 로 실측되어 있고 이의 없음.

**남는 일**: 다음 데모 창에서 실제 데모 백엔드가 꺼진 상태로 `/login?error=session_expired` 를 열어 「서버가 종료되어 다시 로그인해야 합니다」류 문구가 실제로 렌더되는지 확인한다. 이 ⚪ 는 **살아 있는 티켓의 것**이므로 `TASK-MONO-672`(닫히는 티켓의 의무만 받는 집)로 옮기지 않는다 — 이 파일이 `review/` 에 남아 그 창을 기다린다.

**결론**: `review/` 에 유지. `Status` 필드는 변경하지 않는다(review 그대로 참).
