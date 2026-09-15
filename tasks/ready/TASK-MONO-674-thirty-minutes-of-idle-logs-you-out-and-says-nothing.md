# TASK-MONO-674 — 30분 가만히 있으면 로그아웃되는데, **화면은 아무 말도 안 한다**

# Status

ready

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

- [ ] 🔴 ①의 문구(`?error=session_expired`)를 **단언하는 자리가 어디인지 먼저 찾아라.**
      있으면 ②도 같은 자리에서 단언한다. 없으면 «없다» 를 적어라 — 🔵 **가드가 없다는 사실
      자체가 이 결함이 조용히 살아남은 이유**일 수 있다.
- [ ] **bite**: 고친 뒤 되돌리면 빨개져야 한다.

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
