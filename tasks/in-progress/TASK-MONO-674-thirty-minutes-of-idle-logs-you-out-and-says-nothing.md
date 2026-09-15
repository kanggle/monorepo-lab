# TASK-MONO-674 — 30분 가만히 있으면 로그아웃되는데, **화면은 아무 말도 안 한다**

# Status

in-progress

**Type:** TASK-MONO (monorepo-level — 콘솔 인증 경로 + IAM 토큰 수명)

**Analysis model:** Opus 5 / **구현 권장:** Opus 5 (인증 수명 설계다 — 문구 한 줄로 끝나지 않는다)

**선행:** `TASK-MONO-660` AC-3 (이 티켓이 그 측정의 산물이다)

---

# Goal

`TASK-MONO-660` 이 ②로 적어 둔 «서버 사이드 refresh 부재» 를 **2026-09-12 데모 창에서 실제로
쟀고, 일어난다.** 이 티켓은 그 결과를 받아 처리한다.

| 축 | 실측 |
|---|---|
| 액세스 토큰 수명 | **1800초(30분)** — `V0008__create_oauth_tables.sql` 의 `settings.token.access-token-time-to-live` |
| 리프레시 토큰 수명 | 2592000초(30일) — **있는데 안 쓰인다**는 것이 요지다 |
| 대기 | T+0 에 `/ecommerce` 정상 · 그 뒤 **아무것도 안 함** |
| T+2049초(34분 9초) | `/ecommerce` → **`/login?redirect=%2Fecommerce`** |

---

# 🔴🔴 요지 — 증상이 ①과 같아 보이지만 **다른 문**이다

`TASK-MONO-660` ①(재로그인 루프)은 고쳐졌다. 그런데 ②는 그 옆을 지나간다:

| | 최종 경로 | 사유 문구 |
|---|---|---|
| ① 훼손 토큰(진짜 401) | `/login?error=session_expired` | 🟢 *"세션이 만료되어 로그아웃되었습니다"* |
| ② **30분 만료** | `/login?redirect=%2Fecommerce` | 🔴 **없다** |

🔵 둘 다 «로그인 화면» 이라 **화면만 보면 같아 보인다.** 갈리는 것은 **쿼리스트링**이다.
🔴 그래서 ①을 고친 뒤 «재로그인 문제는 끝났다» 고 읽기 쉽고, 실제로 660 이 그 위험을
ⓐ 갈래로 적어 두었다.

## 사용자에게 보이는 것

30분 자리를 비우고 돌아오면 **이유 없이 로그인 화면**이다. 데모 시연 중이라면 시연자가
*"왜 로그아웃됐지"* 를 설명할 방법이 없다 — 화면이 아무 말도 안 하기 때문이다.

---

# Scope

이 티켓은 **갈래를 고르는 것**부터다. 고르기 전에 구현하지 마라.

| 갈래 | 하는 일 | 대가 |
|---|---|---|
| **ⓐ 서버 사이드 refresh** | 만료가 임박/발생하면 `console_refresh_token` 으로 조용히 갱신 | 🔴 리프레시 회전·재사용 정책, 동시 요청 경합, 실패 시 폴백을 다 설계해야 한다. 30일 리프레시가 **이미 발급돼 있으므로** 기전은 있다 |
| **ⓑ 사유를 붙인다** | 만료 경로도 `?error=session_expired` 로 보낸다 | 🔵 싸다. 🔴 그러나 **증상만 고친다** — 30분마다 다시 로그인하는 것은 그대로다 |
| **ⓒ 토큰 수명을 늘린다** | 1800초를 늘린다 | 🔴 **보안 축을 건드린다** — 데모 편의로 수명을 늘리는 것은 ADR 급 결정이고, 이 저장소의 다른 서비스도 같은 클라이언트 설정을 공유한다 |

🔵 **ⓐ 와 ⓑ 는 배타가 아니다** — ⓐ 를 해도 refresh 가 실패하는 날은 있고, 그때 사유 문구가
있어야 한다. 🔴 그러나 **ⓑ 만 하고 닫으면 ②는 안 고쳐진 채 «처리됨» 으로 보인다.**

**Out of scope**: `TASK-MONO-660` ①(이미 PASS) · store/fan 의 세션(다른 신원 경로다) ·
IAM 자체의 세션 수명(`JSESSIONID`).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (전제부터 다시 재라)

- [x] 🔴 **토큰 수명을 다시 읽어라.** 이 티켓은 `1800`(V0008 의 client settings)을 근거로
      서 있다. 그 값이 바뀌었으면 **이 티켓의 수치가 전부 낡은 것**이다.
      → 🟢 **값은 그대로 1800초다. 🔴 그러나 출처가 틀렸다 — V0008 이 아니라 V0015 다**
      (2026-09-15 저장소 재측정). 콘솔 클라이언트 id 는 `platform-console-web`
      (`V0015__seed_platform_console_oidc_client.sql:85`, `console-web/src/shared/config/env.ts:60`)
      이고 V0008 은 이 클라이언트를 **심지 않는다**(`test-internal-client`·`demo-spa-client` 만).
      `V0015:95` = access **1800** · refresh **2592000** · 🔴 **`reuse-refresh-tokens=false`**(회전한다).
      이 클라이언트를 건드리는 뒤 마이그레이션(V0020·V0021·V0023·V0024·V0034)은 전부
      grant/redirect/scope/tenant 만 바꾸고 **`token_settings` 는 0건**이다.
      🔵 ⇒ Related Specs 의 *"V0008 — 토큰 수명 셋의 유일한 출처"* 는 **V0015** 로 읽어라.
      🔴 `reuse=false` 라 Edge Case «두 탭 동시 만료 → 재사용 거부» 는 **가설이 아니라 실제 조건**이다.
- [x] 🔴 **`console_refresh_token` 이 실제로 발급되는지** 확인하라 — 창 실측에서 쿠키 이름은
      봤지만(`JSESSIONID` · `console_access_token` · `console_refresh_token` ·
      `console_id_token` · `console_operator_token` · `console_active_tenant`) **쓰이는지는
      안 쟀다.** ⓐ 의 전제가 그것이다.
      → 🟢 **발급된다** — 런타임 증거가 이미 있다: `TASK-MONO-660` 09-11 절의 `storageState` 에
      `console_refresh_token expires 2026-10-11T07:30:51Z`(심은 시각 +30일). 코드는
      `api/auth/callback/route.ts:142-146`(`maxAge: 2_592_000`).
      🔴 **쓰이는 곳은 딱 하나이고, 이 결함의 경로에는 없다**:
      `POST /api/auth/refresh`(`api/auth/refresh/route.ts:59,70-71`)를 부르는 것은
      `shared/api/client.ts:62` 뿐이고, 그것은 `:90` `res.status === 401 && … && isBrowser()` 뒤다.
      middleware · `(console)` 레이아웃 · 서버 컴포넌트는 **0건**.
- [x] 🔴🔴 **(660 close chore 가 넘긴 행) 30분 만료는 «어느 문» 으로 나가는가** — 660 의 09-11 절은
      «쿠키-없음 가드» 라 했고 09-12 절은 «서버 사이드 refresh 부재(`client.ts:90`)» 로 읽었다.
      → 🟢 **코드로 갈렸다: 백엔드 401 이 아니라 `(console)` 레이아웃의 쿠키 가드다.**

      | 단계 | 코드 |
      |---|---|
      | 액세스 쿠키 수명 = 토큰 `expires_in` | `callback/route.ts:137-140` `maxAge: data.expires_in` → 30분 뒤 **브라우저가 쿠키를 버린다** |
      | 가드 | `app/(console)/layout.tsx:90` `if (!(await isAuthenticated())) redirect(await buildLoginRedirect());` |
      | 술어 | `shared/lib/session.ts:234-235` — **쿠키만 본다**(access ≠ null && operator ≠ null). 리프레시 쿠키는 **안 본다** |
      | URL | `shared/lib/login-redirect.ts:57` `/login?redirect=…` — `error` 없음 ⇒ `login/page.tsx:85-87` 가 문구를 안 그린다 |

      🔴 ⇒ **`client.ts:90` 의 `isBrowser()` 는 이 결함의 원인이 아니다** — 요청이 백엔드까지 가지도 않는다.
      🔵 **처방 자리가 바뀐다**: 갈래 ⓐ 는 «SSR 401 에 refresh» 가 아니라 **«레이아웃 가드가 액세스
      쿠키 없음 + 리프레시 쿠키 있음을 보면 갱신을 시도한다»** 이고, 갈래 ⓑ 는 **같은 조건에서
      사유를 붙이는 것**이다(리프레시 쿠키의 존재가 «로그인한 적이 있다» 와 «처음 온 사람» 을 가른다).
      🔴 **가드에 박힌 테스트**: `tests/unit/demo-tour-console-guard-regression.test.tsx:94`
      `expect(globalThis.fetch).not.toHaveBeenCalled()` — 가드 앞에 refresh `fetch` 를 넣는 ⓐ 는
      **이 핀과 정면으로 부딪친다**(그 핀이 무엇을 지키려던 것인지부터 읽어야 한다).
      🔵 AC-3 첫 칸의 답도 여기 있다: `?error=session_expired` 는 **약 53개 서버 401 자리**와
      단위 테스트 수십 개가 단언하고, `?redirect=` 는 `layout-login-redirect.test.ts` ·
      `e2e-smoke/console-guard.spec.ts` 가 단언한다. 🔴 «액세스 쿠키 없음 + 리프레시 쿠키 있음» 을
      주는 테스트는 **0건** — 이 결함이 조용히 산 이유다.

## AC-1 — 갈래를 고른다 (🔴 소유자 결정)

- [x] ⓐ/ⓑ/ⓒ 중 하나(또는 ⓐ+ⓑ)를 **소유자에게 묻는다.** 🔴 내 추천을 결정으로 적지 마라.
      → 2026-09-15 선택창으로 물었다. 🔵 추천(`(Recommended)` 표지)은 **내 것**이었고, 고른 것은 소유자다.
- [x] 답을 **소유자의 말 그대로** 적는다.
      → 소유자 선택(선택창 라벨 원문): **「ⓐ+ⓑ 갱신+실패시 사유 (Recommended)」**
      — 선택지 설명(내가 쓴 것): *액세스 쿠키 없음 + 리프레시 쿠키 있음 → 조용히 갱신. Next 레이아웃은 쿠키를 못 쓰므로 middleware 또는 `/api/auth/refresh` 경유 후 복귀. 갱신 실패 → `?error=session_expired`. 회전 정책이라 두 탭 경합 처리, 가드 핀 테스트(fetch 미호출 단언) 재검토.*
- [x] 🔵 안 고른 갈래가 **무엇을 포기하는 것인지** 함께 적는다.
      → **ⓑ만** 을 버렸다 = 싼 수리를 포기하고 갱신 설계 비용(회전·경합·폴백)을 떠안는다.
      **ⓐ만** 을 버렸다 = 갱신 실패일에도 사유 문구를 보장한다(실패 경로가 조용해지지 않는다).
      **ⓒ** 를 버렸다 = 토큰 수명(보안 축)은 **1800초 그대로** 둔다 — ADR 없음.

## AC-2 — 판정은 **다시 창을 열어야** 난다

- [ ] 🔴 이 결함은 **30분 이상 살아 있는 스택**에서만 재진다. PR CI 로는 판정 불가다.
- [ ] 술어는 **최종 경로의 쿼리까지**다 — `/login` 만 보면 ①과 ②가 구별되지 않는다.
- [ ] 🔴 **T+0 대조군을 반드시 같이** 재라(대기 전에 같은 화면이 열렸는가). 660 의 측정이
      그것을 포함했고, 없으면 «원래 안 열리는 화면» 과 구별이 안 된다.
- [ ] 창을 못 열면 이 칸을 ⚪ 로 두고 **갈 곳을 적어라**(`TASK-MONO-672`).

## AC-3 — 회귀를 막는다

- [x] 🔴 ①의 문구(`?error=session_expired`)를 **단언하는 자리가 어디인지 먼저 찾아라.**
      있으면 ②도 같은 자리에서 단언한다. 없으면 «없다» 를 적어라 — 🔵 **가드가 없다는 사실
      자체가 이 결함이 조용히 살아남은 이유**일 수 있다.
      → ①을 단언하는 자리는 셋이다: `tests/unit/relogin-loop.test.tsx`(로그인 화면이 마커를 보고 문구를 그리고 `/console` 로 안 튕긴다) ·
      `login-error-messages.test.tsx`(문구 표) · `relogin-marker.test.ts`(서버 401 지점의 소스 스캔).
      ②는 **그중 첫 자리**에 같은 모양으로 넣었다 — `relogin-loop.test.tsx` «② 유휴 만료 갱신 실패도 같은 마커…»
      (`error=session_expired&redirect=/ecommerce` + 쿠키 살아 있음 → 리다이렉트 0, 문구 있음, 목적지 보존).
      🔴 `relogin-marker.test.ts` 에는 **넣지 않았다** — 그 스캔의 술어는 «코드 행에 `redirect('/login')` 리터럴» 이고
      ②의 실패 경로는 라우트 핸들러의 `NextResponse.redirect(new URL(RE_LOGIN_PATH, origin))` 라 그 술어가 **볼 수 없는 모양**이다.
      그 대신 ②의 URL 은 실제 라우트를 부르는 `auth-idle-refresh.test.ts` 가 `RE_LOGIN_PATH`·`SESSION_EXPIRED` 상수로 단언한다.
      가드 쪽(`?redirect=` 를 단언하던 자리 = `demo-tour-console-guard-regression.test.tsx`)은 **그 파일을 안 고치고** 같은 목 모양의
      형제 파일 `console-guard-idle-refresh.test.tsx`(5칸: 쿠키 이름 대조 · 갱신 라우트로 · `/demo`·`/login` 으로 안 샘 · fetch 0 · 리프레시 쿠키 뺀 대조군)에 넣었다.
      🔵 이유: 동시 진행 중인 `TASK-MONO-680`(#3812)이 demo-tour 묶음을 만진다(코디네이터 요청). 형제 파일은 HEAD 그대로다.
- [x] **bite**: 고친 뒤 되돌리면 빨개져야 한다. → 두 곳을 끄고 재고 되돌렸다(아래 § 구현 노트 «bite»).

---

# 구현 노트 (2026-09-15 UTC · 🔴 AC-2 는 창이 필요해 이 티켓은 in-progress 로 남는다)

## 설계 선택 — 레이아웃이 **라우트 핸들러로 한 번 튕긴다** (middleware 아님)

- 가드 `app/(console)/layout.tsx:112-121`: `isAuthenticated()` 거짓 → `hasRefreshToken()`(`shared/lib/session.ts:250`) 이면
  `GET /api/auth/refresh?redirect=<path>`(`buildSessionRefreshRedirectFor`, `login-redirect.ts:95`), 아니면 기존 `/login?redirect=`.
- `GET /api/auth/refresh`(`api/auth/refresh/route.ts:92`)가 회전·재교환·쿠키 설정 후 요청 경로로 307. 토큰 처리 본체는
  `POST`(브라우저 401 재시도)와 **한 함수**로 뺐다 — `shared/lib/session-refresh.ts:94 refreshSessionCookies`. 두 진입점은 실패에 **어떻게 답하나**만 다르다.
- **middleware 를 안 고른 이유**: ① 라우트 그룹 `(console)` 은 URL 에 안 보여 middleware 가 «보호 경로인가» 를 **따로 복제**해야 한다 —
  가드는 이미 레이아웃이 **그 자체**다. ② Next 15 middleware 는 기본 Edge 런타임이고 `jwt.ts` 는 `Buffer`(nodejs)를 쓴다. ③ middleware 는
  공개 `(demo)` 요청·프리페치에도 돌아 네트워크 호출이 가드 **앞**에 선다 — `demo-tour-console-guard-regression.test.tsx:94` 가 막으려던 모양.
  대가: 유휴 만료 때만 왕복 1회(307 두 번)가 더 든다.
- 🔵 **:94 핀이 지키는 것**(9f0fcd2d6, ADR-MONO-070/071 공개 둘러보기): «미인증 요청은 셸 안 코드에 **도달하지 않는다**(fetch 0)». 갱신은
  **다음 요청**(라우트 핸들러)이 하므로 레이아웃의 fetch 는 여전히 0 — 그 핀(과 파일)은 **그대로** 두고, 같은 단언을 **리프레시 쿠키 있음** 칸에
  걸어 형제 파일 `tests/unit/console-guard-idle-refresh.test.tsx` 에 넣었다.

## 설계 질문 넷

1. **갱신 후 세션은 완결인가 — 예, 한 칸을 메워서.** 콜백이 세우는 것: access(`callback/route.ts:137-140`, `maxAge=expires_in`) ·
   refresh(:142-146, 30일) · id_token(:150-155, `expires_in`) · operator(:160-165, `op.expiresIn` 기본 3600) · 홈 테넌트(:223-229, `expires_in`).
   갱신이 다시 세우는 것: access·refresh·id_token(응답에 있을 때) · **operator 재교환**(`session-refresh.ts:165`) · 테넌트 쿠키가 살아 있으면
   **재assume**(:167). 🔴 **빠져 있던 칸 = 홈 테넌트**: 콜백의 기본값은 `maxAge=expires_in` 이라 액세스 쿠키와 **같이 죽는다** ⇒ 옛 코드로
   갱신하면 인증은 되는데 테넌트 없음 → 개요 화면이 «테넌트를 고르세요» 로 막힌다(TASK-PC-FE-036 결함의 재발). 갱신 경로에서 테넌트 쿠키가
   없으면 **콜백과 같은 규칙으로** 홈 테넌트를 다시 세운다(`session-refresh.ts:221`, GET·POST 둘 다). 스위처로 고른 비-홈 테넌트는
   `api/tenant/route.ts:159` 가 **maxAge 없는 세션 쿠키**로 세우므로 유휴에 안 죽고 재assume 갈래를 탄다.
   60분 유휴(operator 쿠키도 사라짐)도 같은 경로다 — 갱신은 operator 를 **항상** 재교환한다. operator 만 없는 상태(TTL 을 env 로 줄인 배포)도
   가드가 갱신으로 보낸다(단위 테스트 `it.each` 두 번째 칸).
   🔴 남는 것: IAM 갱신 응답이 `id_token` 을 안 주면 id_token 쿠키는 30분 뒤 사라진 채다 → 로그아웃의 `id_token_hint` 누락(표시 이름은 액세스 토큰으로 폴백). 창에서 확인할 칸.
2. **회전 경합**: IAM `400 invalid_grant`(`session-refresh.ts:127 rotationSuspect`) → GET 은 **쿠키를 하나도 안 지우고**
   (`route.ts:147-156`) `REFRESH_RACE_GRACE_MS`(2000ms, `session-refresh.ts:68`) 기다린 뒤 `?retry=1` 로 **한 번** 다시 온다. 그 요청은 브라우저의
   **현재** 쿠키를 싣는다 ⇒ 옆 탭의 새 세션이 도착했으면 `route.ts:120` 에서 요청 경로로 간다(IAM 호출 0). 없으면 `:133` 에서 session_expired,
   역시 삭제 0. 🔴 지우지 않는 이유: 진 탭의 삭제 Set-Cookie 가 이긴 탭의 새 쿠키 **뒤에** 도착하면 그것을 덮는다.
   대가: 진짜로 폐기된 리프레시 토큰은 사유 표시까지 2초 더 걸린다(실패 경로에서만).
3. **루프·열린 리다이렉트**: 가드 한 번에 라우트는 **최대 두 번**(첫 시도 + `retry=1`), `retry=1` 은 절대 다시 갱신하지 않는다(`route.ts:133`).
   실패 목적지는 `/login…`·`/onboarding` 뿐이고 둘 다 가드 밖이며, `/login?error=session_expired` 는 TASK-PC-FE-278 로 `/console` 단락을 안 탄다.
   브라우저가 새 쿠키 저장을 거부해도 순환하지 않는다 — 옛 리프레시 토큰은 이미 회전돼 다음 시도가 거절된다(그리고 콜백·갱신 쿠키 옵션이
   같아 «콜백 쿠키는 저장되고 갱신 쿠키만 거부» 는 현실 상태가 아니다). 열린 리다이렉트: 쿼리의 `redirect` 는 공격자 입력이므로
   `resolveRefreshReturnPath`(`login-redirect.ts:106`) = 소비 측 `sanitizeReturnPath`(`//`·`/\`·절대 URL) **위에** 가드 술어 `isGuardReturnPath`
   (`login-redirect.ts:67`, `/login…`·`/api/…` — 갱신 라우트 자신을 목적지로 삼는 순환도 여기서 막힌다)를 겹쳐 건다. 거절 → `/`.
   생산 측(`buildLoginRedirectFor`·`buildSessionRefreshRedirectFor`)은 같은 `isGuardReturnPath` 하나를 쓴다. 모든 Location 은 `publicOrigin(env)` 기준.
4. **상수**: 실패 URL 은 `RE_LOGIN_PATH`(`shared/lib/re-login.ts:54`) 로 만들고 단언은 `RE_LOGIN_PATH`·`SESSION_EXPIRED` 로 한다. 경로 문자열은
   `SESSION_REFRESH_PATH`·`REFRESH_RETRY_PARAM`(`login-redirect.ts`)에 두고 레이아웃에 손으로 박지 않았다(`layout-login-redirect.test.ts` 가 `'/api/auth/refresh` 부재를 문다).

## 🔴 소유자 결정 문구와 **다른 곳** (전부 적는다)

| # | 결정의 글자 | 구현 | 이유 |
|---|---|---|---|
| D1 | 실패 → `/login?error=session_expired` | `/login?error=session_expired&redirect=<path>` | 재로그인 뒤 요청 화면으로 돌아가게. 마커는 그대로라 `login/page.tsx:81` 판정 불변 |
| D2 | 실패 → session_expired | operator 재교환 **fail-closed(401)** 만 `/onboarding` | 갱신 자체는 성공했고 «운영자가 아니다» 라는 답이다 — 콜백(`callback/route.ts:177-193`)과 같은 처리. 운영자 비활성화도 여기로 간다(콜백과 동일) |
| D3 | (없음) | IAM 5xx·네트워크 실패는 사유를 보이되 **쿠키를 안 지운다** | 일시 장애로 30일 리프레시를 버리지 않는다. 4xx 거절·operator 불가는 세션 전체 삭제(§ 2.6 fail-closed) |
| D4 | (없음) | 홈 테넌트 재기본값을 **POST 갱신에도** 적용 | 한 함수라 갈라 두면 두 경로가 다른 세션을 만든다. 기존 POST 테스트 전부 초록 |
| D5 | (없음) | `invalid_grant` 시 2초 대기 후 1회 재확인 | 질문 2 |

토큰 수명(1800s)은 **안 건드렸다**(갈래 ⓒ 없음).

## 테스트 · rc (console-web, 이 worktree)

- 표적 8파일 `npx vitest run tests/unit/{auth-idle-refresh,demo-tour-console-guard-regression,layout-login-redirect,relogin-loop,relogin-marker,auth-routes,auth-refresh-parallel,login-error-messages}…` → **rc=0 · 8 files · 114 tests**
- 전체 `npx vitest run` → **rc=1** · 285/293 파일 통과, 실패 8파일 = `AccountsScreen`·`AuditScreen`·`ecommerce-idempotency-key-lifetime`·`OperatorsScreen`·`SeedConfigScreen`·`WmsInboundScreen`·`LedgerOpsScreen`·`features/operators/CreateOperatorForm`, 전부 **`Test timed out in 5000ms`**.
  귀속: 그 8파일은 바뀐 모듈을 import 하지 않는다(grep 0건) · 그 8파일만 `--minWorkers=1 --maxWorkers=2` 로 재실행 → **rc=0 · 8/8 files**. ⇒ 부하 타임아웃이지 이 변경이 아니다(단, main 에서 같은 전체 실행을 대조군으로 돌리지는 **않았다**).
- `npx tsc --noEmit` → **rc=0** · `npx next lint` → **rc=0**(경고·오류 0)
- 새/바뀐 칸: `auth-idle-refresh.test.ts`(32칸 = 쿠키 없음 대조군 1 · ⓐ 복귀 2 · 세션 완결 1 · ⓑ 실패/onboarding 5 · 경합 4 · 루프 상한 11 · 열린 리다이렉트 8) ·
  `console-guard-idle-refresh.test.tsx` 5칸(신규) · `layout-login-redirect` +2 describe · `relogin-loop` +1 · `demo-tour-console-guard-regression` **변경 0(HEAD 그대로)**.
- 🔴 위 «표적 8파일 · 114» 와 «전체» 는 가드 칸 넷이 **아직 demo-tour 파일 안에 있던** 트리에서 쟀다. 코디네이터 요청으로 그 칸을 형제 파일로 옮긴 뒤
  `npx vitest run tests/unit/{console-guard-idle-refresh,demo-tour-console-guard-regression,layout-login-redirect}…` → **rc=0 · 3 files · 37 tests** 로 다시 쟀다
  (옮긴 파일과 원복한 파일 둘 다). 전체 스위트는 이동 뒤 **다시 돌리지 않았다** — 바뀐 것은 테스트 파일 둘뿐이고 제품 코드는 같다.

## bite

| 끈 것 | 결과 | 되돌린 뒤 |
|---|---|---|
| L: `layout.tsx` 갱신 갈래(`(false && (await hasRefreshToken()))`) | 1차(칸이 demo-tour 파일에 있을 때) `demo-tour-console-guard-regression` **rc=1 · 1 failed / 7**. 🔁 이동 뒤 재측정 `console-guard-idle-refresh` **rc=1 · 2 failed / 5** — «갱신 라우트로 보낸다» · «`/demo`·`/login` 으로 안 샌다», 🔵 대조군(리프레시 쿠키 뺌)은 초록 | 표적 초록 |
| R: `route.ts` 경합 갈래(`false && outcome.rotationSuspect`) | `auth-idle-refresh` **rc=1 · 1 failed / 32** — «`400 invalid_grant` 면 아무 쿠키도 지우지 않고 `retry=1`» | 초록 |

🔵 R 을 껐을 때 루프 상한 칸(`invalid_grant (race)`)은 초록이었다 — 그 칸의 술어는 «돌아가면 반드시 retry=1» 이고 `/login` 도 허용하므로 **경합을 재는 칸이 아니다**. 경합은 위 한 칸이 문다.

## e2e

- `e2e-smoke/console-guard.spec.ts:21-29`·`e2e-smoke/demo-tour.spec.ts:74` 는 **새 브라우저 컨텍스트**(리프레시 쿠키 없음) ⇒ 여전히 `/login?redirect=` — **안 고쳤다**, 고칠 이유가 없다.
- nightly 전용 `tests/e2e/*`(`operators-*`·`overview-consolidation`)는 새로 로그인하고 30분 안에 끝나 유휴 경로를 안 탄다. **유휴 만료를 재는 e2e 는 0개**이고 PR CI 로는 못 잰다(AC-2).

## AC-2 창에서 잴 것 (술어 = **최종 URL 의 쿼리까지**)

1. **T+0 대조군**: 로그인 → `/ecommerce` 열림(쿠키 6종 기록: access·refresh·id·operator·tenant·(assumed)).
2. **유휴 > 30분** (access 만료, operator 는 살아 있음): 새로고침 → 최종 URL = **`/ecommerce`**(쿼리 포함 원래 경로), 화면 열림. 네트워크 탭에 `/api/auth/refresh?redirect=%2Fecommerce` 307 두 번. 헤더 테넌트가 비지 않았는가(홈 테넌트 재기본값).
3. **유휴 > 60분** (operator 도 만료): 같은 판정. 🔴 operator TTL 이 env 로 바뀌었는지 먼저 읽는다.
4. **소프트 내비게이션**: 유휴 뒤 새로고침이 아니라 **사이드바 링크 클릭**으로도 같은 결과인가(RSC 요청이 라우트 핸들러로 튕기는 경로 — 단위 테스트가 못 잰다).
5. **실패 경로**: 리프레시 쿠키를 훼손(값 변경)하고 유휴 만료 상태로 `/ecommerce` → 최종 URL **`/login?error=session_expired&redirect=%2Fecommerce`** + 문구 보임, 대기 ~2초.
6. **두 탭**: 유휴 만료 상태에서 두 탭을 거의 동시에 새로고침 → **둘 다** 화면이 열리는가(한쪽이 `retry=1` 을 거쳐도).
7. 대조군: 로그아웃 후 `/ecommerce` → `/login?redirect=%2Fecommerce`(사유 없음) 그대로.
8. id_token 쿠키가 갱신 뒤 다시 서는가(질문 1 의 남은 칸).

## 남은 것 / 곁발견

- ⏳ AC-2(위) — 창이 없으면 `TASK-MONO-672` 로.
- 🔵 곁발견(미수정, 범위 밖): `layout.tsx` 의 `getCatalog()` **401** 캐치는 여전히 마커 없는 `buildLoginRedirect()` 다 — 쿠키가 살아 있는데 백엔드가 거절하면 `/login?redirect=` → `/login` 이 쿠키를 보고 `/console` 로 되튕긴다(TASK-PC-FE-278 이 서버 401 지점 53곳에서 고친 모양). `relogin-marker.test.ts` 는 `redirect('/login')` 리터럴만 찾아서 이 호출을 못 본다.
  → 🔵 **받는 티켓 = `tasks/ready/TASK-MONO-685-the-console-layout-catalog-401-still-bounces-without-the-session-expired-marker.md`** (2026-09-15 같은 PR 에서 기안, INDEX ready 행 확인 — 산문 «범위 밖» 으로만 남기지 않는다). 🔴 처음엔 684 로 기안했는데 동시 세션 #3822 가 **같은 684 를 먼저 main 에 머지**해 685 로 옮겼다(배정 직전 확인 뒤에도 병렬 세션이 번호를 가져갈 수 있다).

---

# Related Specs / Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.5 · § 2.6
- `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/V0008__create_oauth_tables.sql`
  — 토큰 수명 셋의 유일한 출처
- `TASK-MONO-660` — 이 티켓의 출처. AC-3 이 실측을 담고 있다
- `ADR-MONO-069` · `ADR-MONO-072` — OIDC / SSO 세션이 테넌트를 정한다

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| refresh 가 실패한다(회전 충돌 등) | 🔴 **조용히 로그아웃하지 마라** — 사유 문구가 그때 필요하다 |
| 두 탭이 동시에 만료를 맞는다 | 🔴 refresh 경합. 한 탭이 회전시킨 토큰을 다른 탭이 쓰면 재사용 거부가 날 수 있다 |
| `reuse-refresh-tokens` 설정이 클라이언트마다 다르다 | 🔵 V0008/V0009/V0010 에서 `true`/`false` 가 **섞여 있다** — 콘솔 클라이언트가 어느 쪽인지 먼저 재라 |
| 사용자가 30분 안에 움직였다 | 재현 안 된다. 그래서 **「아무것도 하지 않는다」가 술어의 일부**다 |

# Failure Scenarios

1. **ⓑ 만 하고 닫는다** → 화면은 친절해지고 **30분마다 재로그인은 그대로**다. 티켓은
   «처리됨» 인데 사용자 경험은 안 바뀐다.
2. **ⓒ 로 수명을 늘려 «해결»한다** → 보안 축을 데모 편의로 옮긴 것이고, 그 결정이 ADR 없이
   전 서비스에 퍼진다.
3. **창 없이 «고쳤다» 고 한다** → AC-2 가 막는다. 이 결함은 30분짜리 관측이 유일한 판정이다.
4. **①의 PASS 를 ②의 PASS 로 읽는다** → 이 티켓이 존재하는 이유 그 자체다.
