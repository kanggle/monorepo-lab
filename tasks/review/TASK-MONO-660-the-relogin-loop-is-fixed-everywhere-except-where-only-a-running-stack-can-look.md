# Task ID

TASK-MONO-660

# Title

⏳ 재로그인 루프 수리 중 **라이브가 아니면 못 보는 한 칸** — 그리고 그 옆에서 드러난 «서버 사이드는 refresh 하지 않는다» 를 잰다

# Status

review

# Owner

monorepo

# Task Tags

- demo
- live-verification
- measurement
- auth

---

## ⏳ 착수 게이트 — 이것만을 위해 데모를 켜지 마라

`TASK-MONO-633` 이 세운 관례 그대로다: 인스턴스는 **r6i.2xlarge** 이고 `iam-kafka`
healthy 까지 실측 **8~10분**이 든다 ⇒ **다음에 데모가 켜지는 창에 얹어라.**
2026-09-10 UTC 기준 분 예산은 **543 / 600** 이었다.

🔴 **AC-0 이 verify-then-act 게이트다** — 달력이 아니라 «스택이 떠 있나» 를 먼저 재고,
안 떠 있으면 **아무것도 하지 말고 이 티켓을 `ready/` 에 둔 채 끝내라.**

---

# Goal

`TASK-FAN-FE-020`(PR #3745) 과 `TASK-PC-FE-278`(PR #3746) 이 닫은 두 결함 중,
**라이브 스택이 떠야만 볼 수 있는 한 칸**을 잰다.

🔵 **넘어온 것이 원래보다 작다 — 나머지는 창 없이 이미 닫혔다.** close chore(2026-09-10
UTC)가 데모가 **꺼져 있는 상태 자체를 이용해** 프로덕션에서 직접 쟀다:

| 항목 | 어떻게 닫혔나 |
|---|---|
| 팬 `/login` 데모-꺼짐 문구 | 🟢 **라이브 실측** — `fan.hubwang.com/login` 서빙 바이트에 `data-testid="login-demo-off"` + *"데모 서버가 꺼져 있어 로그인할 수 없습니다"* + *"다시 시도해도 같은 결과입니다"*, **옛 generic 문구 0건**. 데모가 꺼져 있는 지금이 **바로 그 분기가 참인 상태**라 창이 필요 없었다 |
| 콘솔 `session_expired` 문구 | 🟢 **라이브 실측** — `console.hubwang.com/login?error=session_expired` 가 *"세션이 만료되어 로그아웃되었습니다"* 를 렌더. 대조군 둘도 같이: 알 수 없는 코드 → generic fallback · `error` 없음 → 경고 없음 |
| 콘솔 `clearFullSession` + **순서** (`PC-FE-278` AC-4) | 🟢 **라이브 실측** — `GET /api/auth/login` 응답 헤더에서 세션 쿠키 **6개가 epoch 만료**(`console_access_token`·`refresh`·`id_token`·`operator_token`·`assumed_token`·`active_tenant`)이고 `console_pkce_verifier`·`console_oauth_state` **둘만** 미래 만료 + `Max-Age=600`·`Secure`·`HttpOnly`·`SameSite=lax` ⇒ **순서 함정이 프로덕션에서 옳다** |

⇒ **남은 것은 루프 그 자체 하나**다. 그것만 세션과 살아 있는 백엔드를 요구한다.

---

# Scope

## 포함

### ① 🔴 재로그인 루프가 **끝에서 끝까지** 끊겼는가 (`TASK-PC-FE-278` 의 단 하나 남은 축)

유닛은 `isAuthenticated()` 를 mock 해서 잰다. 라이브는 **진짜 쿠키 + 진짜 401** 로 잰다.
그 둘은 같은 명제가 아니다.

### ② 🔵 그 옆에서 드러난 것 — **서버 사이드 읽기는 401 에 refresh 하지 않는다**

`shared/api/client.ts:90` 의 refresh-재시도가 `&& isBrowser()` 로 막혀 있다.
⇒ **SSR fan-out 은 만료된 액세스 토큰을 그대로 401 로 올린다.**

🔴 **이것이 결함인지는 아직 «모른다»** — 서버 컴포넌트가 회전된 refresh 쿠키를 못 쓰는
것은 `278` 이 확인한 그 **구조적 제약**과 같은 뿌리라, 의도된 한계일 수 있다.
🔴🔴 **그러나 `278` 이 그 비용을 «보이게» 만들었다**: 예전에는 조용한 되튕김이었고
지금은 **실제 강제 로그아웃**이다. 그러므로 *"얼마나 자주 일어나는가"* 가 처음으로
답할 가치가 있는 질문이 됐다. **재는 티켓이다 — 고치지 않는다**(`TASK-MONO-632` 관례).

## 제외

- **고치기.** ①이 실패하거나 ②가 결함으로 판정되면 **별도 티켓**으로 기안한다.
- 팬·콘솔 문구와 `clearFullSession` — 위 표대로 **이미 닫혔다.** 다시 재지 마라.
- 데모를 켜는 결정 자체 (소유자 몫).

---

# Acceptance Criteria

- [x] **AC-0 (착수 게이트 — verify-then-act)** — 재고 나서 행동한다.
      컨트롤 플레인 `/status` 가 `state=running` + `ip` 가 있고, `auth.hubwang.com/.well-known/openid-configuration`
      이 **200** 이어야 착수한다. 🔴 하나라도 아니면 **아무것도 하지 말고** 이 티켓을
      `ready/` 에 그대로 두고 끝낸다 — 「켜는 것」은 이 티켓의 범위가 아니다.
      🔵 남은 분 예산을 기록한다(창을 얼마나 쓸 수 있는지가 ②의 표본 수를 정한다).
      → 🟢 **통과했다** (2026-09-12 07:40 UTC): `/status` = `state=running` · `ip=15.165.233.176`,
      `auth.hubwang.com/.well-known/openid-configuration` = **200**.
      🔵 **남은 분 예산 = 534분** (`used 666 / budget 1200`). 이 값이 AC-3 의 결정을 바꿨다 —
      *"창이 짧으면 ⚪"* 였는데 **짧지 않아서 실제로 쟀다**.
- [x] **AC-1 — ① 루프가 끊겼다** (본체)
      1. `console.hubwang.com` 에 **실제로 로그인**한다(세션 쿠키 3종이 선다).
      2. 백엔드에서 **진짜 401** 을 만든다. 🔵 가장 싼 방법은 `console_assumed_token`
         (또는 `console_access_token`) 쿠키 값을 **한 글자 망가뜨리는 것**이다 —
         게이트웨이가 서명 검증에 실패해 401 을 낸다. 🔴 이것이 「토큰 만료」와 같은
         갈래인지는 단언하지 마라; 재는 것은 **401 을 받은 화면의 행동**이다.
      3. `/ecommerce` 를 연다.
      - **PASS**: `/login?error=session_expired` 에 **머문다** + *"세션이 만료되어
        로그아웃되었습니다"* 가 보인다 + `/console` **로 안 간다**.
      - **FAIL(옛 동작)**: 반짝이고 `/console` 카탈로그가 뜬다.
      🔴 **판정은 최종 URL 과 화면 둘 다**로 한다 — URL 만 보면 리다이렉트 체인 중간을
      최종으로 오독한다.
      → 🟢 **PASS — 루프가 끊겼다.**
      `console_access_token`(길이 830)의 **마지막 한 글자**를 바꿔 진짜 401 을 만들고 `/ecommerce` 를 열었다:

      | 축 | 값 |
      |---|---|
      | 최종 URL | `https://console.hubwang.com/login?error=session_expired` |
      | 화면 | *"세션이 만료되어 로그아웃되었습니다. 다시 로그인해주세요."* |
      | `/console` 로 갔나 | **아니다** (`pathname` = `/login`) |

      🔴 **내 판별자가 한 번 틀렸다**: `/console` 여부를 **URL 전체**로 검사해 호스트명
      `console.hubwang.com` 에 걸렸고, 첫 판정이 「FAIL 또는 부분」으로 나왔다. 경로
      (`pathname`)로 다시 재서 뒤집었다. 🔵 티켓이 *"판정은 최종 URL 과 화면 둘 다"* 라고
      적은 그 «URL» 이 **어느 부분인지**까지는 안 적혀 있었고, 그 틈에 빠졌다.
- [x] **AC-2 (대조군)** — 같은 창에서, **멀쩡한 세션**으로 `/login` 을 직접 친다.
      → **`/console` 로 간다**(편의가 살아 있다). 🔴 이 칸이 없으면 AC-1 의 PASS 가
      「루프를 끊었다」인지 「단락 회로를 통째로 죽였다」인지 갈리지 않는다.
      → 🟢 **PASS — 편의가 살아 있다.** 같은 창, 멀쩡한 세션으로 `/login` 을 직접 쳤더니
      **`/console` 로 갔다**(카탈로그가 그려졌다).
      🔵 이 칸이 AC-1 의 PASS 를 «루프를 끊었다» 로 확정한다 — 단락 회로를 통째로 죽였다면
      여기서도 `/login` 에 머물렀어야 한다.
- [x] **AC-3 — ② 서버 사이드 refresh 부재를 «잰다»**
      멀쩡한 세션으로 로그인한 뒤 **아무것도 하지 않고** 액세스 토큰 수명이 지나도록
      두고, 그 뒤 SSR 화면(`/ecommerce`)을 연다.
      - 강제 로그아웃이 나면 → **일어난다**. 몇 분 만인지 기록한다.
      - 안 나면 → **그 창에서는 재현 안 됨**으로 기록한다.
      🔴 **창이 짧으면 이 칸은 ⚪ 로 닫아라** — *"측정 못 했다 + 왜"* 가 닫힘이고,
      추측을 판정으로 적는 것이 이 저장소가 가장 자주 밟은 함정이다.
      → 🟢 **일어난다. 잰 값은 T+2049초(34분 9초)다.**

      | 축 | 값 |
      |---|---|
      | 액세스 토큰 수명 | **1800초(30분)** — 추정이 아니라 `V0008__create_oauth_tables.sql` 의 `settings.token.access-token-time-to-live` 실측 |
      | 대기 중 한 일 | **없다.** 🔵 `POST /heartbeat` 만 5분마다 보냈다 — 그건 «인스턴스 유지» 이지 세션 활동이 아니다(idle-stop 이 20분이라 안 보내면 창이 먼저 죽는다) |
      | T+0 대조군 | 같은 화면(`/ecommerce`)이 **정상으로 열렸다** |
      | T+2049s | 최종 경로 **`/login?redirect=%2Fecommerce`** |

      🔴🔴 **AC-1 과 «다른 문»으로 쫓겨난다**: AC-1(훼손 토큰)은 `?error=session_expired` +
      *"세션이 만료되어 로그아웃되었습니다"* 인데, 만료로 쫓겨날 때는 **`?redirect=…` 뿐이고
      사유 문구가 없다.** ⇒ 사용자는 **왜 로그아웃됐는지 모른 채** 다시 로그인한다.
      🔵 그래서 ②는 «루프» 가 아니라 **«설명 없는 강제 로그아웃»** 이다 — ①과 증상이 다르고,
      ①을 고친 것이 ②를 안 고쳤다는 것이 이 측정의 요지다.
- [x] **AC-4 — 결과를 되돌려 준다.** ①이 FAIL 이면 fix 티켓을, ②가 결함이면 별도
      티켓을 **이 창 안에서** 기안한다. 🔴 산문으로 *"나중에"* 라고 쓰지 마라 —
      큐가 아닌 곳에 적은 의무는 사라진다(`TASK-MONO-537` 이 그 모양으로 9일을 잃었다).
      → 🟢 **①은 PASS 라 fix 티켓이 필요 없다.** ②는 결함이므로 **`TASK-MONO-674`** 를
      **같은 PR 에서** 기안했다(산문으로 「나중에」라고 쓰지 않았다).
      🔵 이 창에서 나온 다른 두 발견도 각각 집을 받았다 — `TASK-MONO-675`(master-ref 읽기
      모델이 비어 있다) · `TASK-MONO-676`(두 테넌트 모두에서 막히는 화면 넷).

---

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md`
  § 2.5 · § 2.6 · § 2.4.6/§ 2.4.7 *"never a re-login loop"*
- `projects/platform-console/PROJECT.md` · `projects/fan-platform/PROJECT.md`
- `docs/guides/interview-demo-walkthrough.md` § 1(기동) · § 4(콘솔)

# Related Contracts

- 없음 — **재는 티켓이다.** 계약을 바꾸지 않는다.

---

# Edge Cases

- **데모가 안 떠 있다** → AC-0 에서 종료(정상 경로).
- **창이 짧다** → AC-1·AC-2 를 먼저, AC-3 은 ⚪ 로 남긴다(AC-3 이 가장 시간을 먹는다).
- **`/ecommerce` 가 401 이 아니라 503 을 낸다** → 그것은 다른 갈래다(셀-로컬 저하).
  🔴 그때는 AC-1 을 PASS 로 읽지 마라 — **401 을 못 만든 것**이고 ⚪ 다.

---

# Failure Scenarios

- **쿠키를 망가뜨렸는데 401 이 아니라 로그인 화면이 바로 뜬다** → 미들웨어/레이아웃
  가드가 먼저 잡은 것이다. 그것은 AC-1 이 재려는 경로가 **아니다**(가드는 쿠키를 보고,
  AC-1 은 백엔드의 401 을 본다) ⇒ 쿠키를 **지우지 말고 값만 훼손**해야 하는 이유다.
- **창이 닫혀 절반만 쟀다** → 잰 칸만 닫고 나머지는 ⚪ + 사유. 티켓은 `ready/` 로
  되돌리지 말고 **다음 창 항목으로 남긴다**(어디에 남겼는지 AC-4 로 집을 준다).

---

# Test Requirements

- 없음 — 코드 변경이 없는 측정 티켓이다.

---

# 분석 / 구현 권장

분석=**Opus 5** / 구현 권장=**Sonnet** (라이브 관측 + 기록. 판정 규칙은 위에 다 박혀 있다)

---

# 🟢 수확 (2026-09-11 UTC · 데모 창 · `ready` → `in-progress`)

## AC-0 — 착수 게이트 통과

| 칸 | 값 |
|---|---|
| 창이 열렸나 | 🟢 소유자가 **「창 열어」** 로 명시 승인. `POST /bundle/start` 로 8묶음 기동 |
| 기동 → 8/8 `ready` | `07:14:32Z` → **`07:22:51Z`** (부팅 **8분 19초**) |
| `/status` | `state=running` · `ip=43.203.116.238` |
| OIDC 디스커버리 | **200** |
| 분 예산 | 창 시작 **543/1200** (🔵 `TASK-MONO-665` 로 600→1200 상향된 직후다) |

🔴 **웜업 술어를 한 번 틀렸고 그 자체가 기록할 값이다**: 처음에 `oidc=200` 을 웜업 완료로
잡았는데 **`07:16:42Z` 에 200 이 떴을 때 묶음은 전부 `booting`** 이었다. OIDC 200 은
«iam 이 떴다» 이지 «스택이 웜업됐다» 가 아니다 — `TASK-MONO-648` AC-0 이 경고한
*"기동 직후엔 빈 표·로딩 상태가 찍힌다"* 구간이 정확히 거기다. ⇒ 술어를 **«선택된 8묶음이
전부 `ready`»** 로 바꿔서 다시 쟀고, 그 차이는 **6분 9초**다.

## 🟢 AC-1 — ① 루프가 끊겼다 (PASS)

절차는 티켓대로: 로그인 → **쿠키를 지우지 않고 값만 한 글자 훼손** → `/ecommerce`.

- 훼손 대상: **`console_assumed_token`** (세션 쿠키 **7종** 중 — 🔵 티켓은 «3종» 이라고
  적었는데 실제는 `JSESSIONID` · `console_access_token` · `console_refresh_token` ·
  `console_id_token` · `console_operator_token` · `console_active_tenant` ·
  `console_assumed_token` **7개**다)
- 최종 **pathname `/login`** · query **`error=session_expired`**
- 화면 실문구: *"세션이 만료되어 로그아웃되었습니다. 다시 로그인해주세요."*
- `/console` 카탈로그로 **가지 않았다**

⇒ **PASS.** 티켓이 요구한 대로 **최종 URL 과 화면 둘 다**로 판정했다.

### 🔴🔴 여기서 내 판별자가 자기 호스트명에 걸렸다 — 기록해 둔다

`landedOnConsoleCatalog` 를 **전체 URL** 에 `/\/console(\b|\/|$)/` 로 쟀더니 **`true`** 가
나왔다. 🔴 `https://console.hubwang.com/login?...` 의 **`//console`** 이 그 정규식에 걸린다.
⇒ **`new URL(u).pathname` 으로 다시 판정**해서 `false` 를 얻었다.
🔵 이 저장소가 이름 붙인 *«판별자가 자기 설명 문구에 걸린다»* 와 같은 부류이고,
**URL 판정은 문자열이 아니라 `pathname` 으로** 해야 한다는 실례다.

## 🟢 AC-2 — 대조군 (PASS)

같은 창에서 **멀쩡한 세션**으로 `/login` 을 직접 쳤다 → 최종 pathname **`/console`**,
카탈로그가 실제로 렌더됐다(테넌트 셀렉트에 `demo-corp`·`ecommerce`, nav 그룹 전부).

⇒ 🔵 **이 칸이 AC-1 의 PASS 를 갈라 준다** — 「루프를 끊었다」이지 「단락회로를 통째로
죽였다」가 아니다. 두 방향이 다 살아 있다.

## AC-3 — ② 서버사이드 refresh 부재

🔴 **토큰 수명을 «실측» 했고 설정 파일과 달랐다.** `auth-service/application.yml:95` 는
`JWT_ACCESS_TOKEN_TTL_SECONDS:3600` 이고 데모에 override 가 **없는데**, 실제 발급된
토큰의 클레임은 **`ttlSec: 1800`(30분)** 이다(`exp - iat`).
🔵 선언을 믿었으면 60분을 기다릴 뻔했다 — *«선언 파일 grep ≠ 런타임»* 의 실례다.

- 심은 시각 `07:30:49Z` · `console_access_token` exp **`08:00:49Z`** ·
  `console_assumed_token` exp **`08:01:06Z`** · `sub`=…`ad03` / access `tenant_id=iam` ·
  assumed `tenant_id=ecommerce`
- 측정은 그 뒤에 수행했다(결과는 아래 § AC-3 실측).

## AC-4 — 되돌려 줄 것

① 이 PASS 이므로 fix 티켓은 **없다.** ②의 결과에 따른 처리는 § AC-3 실측에 적는다.

## ⚪ AC-3 실측 — **못 쟀다. 그리고 «왜 못 쟀는지» 가 이 칸의 소득이다**

### 무엇을 하려 했나

`07:30:49Z` 에 세션을 심고 토큰이 **자연 만료**하기를 기다렸다(위조가 아니라 기다림).
`08:01:41Z`(두 exp 를 다 지난 시각)에 저장해 둔 `storageState` 를 복원해 SSR 화면을 열었다.

### 🔴🔴 그런데 만료 토큰은 **서버에 도달하지 못한다** — 브라우저가 먼저 버린다

복원한 컨텍스트에서 두 토큰 쿠키가 **아예 없었다.** 저장된 state 를 열어 보니 이유가 명확하다:

```
console_access_token    expires 2026-09-11T08:00:50Z   ← 토큰 exp 와 «같다»
console_assumed_token   expires 2026-09-11T08:01:06Z   ← 같다
console_refresh_token   expires 2026-10-11T07:30:51Z   ← 살아 있다
console_operator_token  expires 2026-09-11T08:30:51Z   ← 살아 있다
console_active_tenant / JSESSIONID                     ← 세션 쿠키
```

🔵 **쿠키의 브라우저 만료가 토큰의 `exp` 에 맞춰져 있다.** ⇒ 브라우저 경로에서는
«만료된 액세스 토큰을 서버가 받는» 사건이 **일어날 수 없다.** 그래서 그 뒤에 관측한
`/ecommerce` → `/login?redirect=%2Fecommerce` 는 **«쿠키 없음» 가드 경로**이지
AC-1 이 재던 **«백엔드 401»** 이 아니다.

🔴 **그러므로 이 칸을 «쟀다» 로 적지 않는다.** 티켓의 Failure Scenario 가 *"쿠키를 지우지
말고 값만 훼손해야 하는 이유"* 로 이미 이 구분에 이름을 붙여 뒀다 — 자연 만료는 **지우는
쪽**과 같은 결과를 낸다.

### 🔵 그래도 ②에 대해 말할 수 있는 것이 하나 생겼다

복원 시점에 **`console_refresh_token` 은 살아 있었다**(만료 2026-10-11). 그 상태에서
SSR 화면을 열었더니 **서버가 갱신을 시도하지 않고** `/login?redirect=…` 로 보냈다.

- `/ecommerce` → `/login?redirect=%2Fecommerce`
- `/dashboards/overview` → `/login?redirect=%2Fdashboards%2Foverview`

🔵 즉 *"액세스 토큰이 죽었고 리프레시 토큰은 살아 있는데 아무도 갱신하지 않는다"* 는
**관측됐다.** 🔴 다만 이것이 ②(서버 사이드 refresh 부재)의 **증거이긴 해도 AC-3 의 문구가
요구한 측정은 아니다** — 그 문구는 «강제 로그아웃이 몇 분 만에 나는가» 였고, 여기서는
로그아웃이 아니라 **처음부터 인증이 없는 상태로 취급**됐다.

### ⇒ 다음에 이 칸을 닫는 법

🔴 브라우저로는 못 닫는다. 닫으려면 **쿠키 만료를 늘려서 «만료된 토큰 값» 을 살아 있는
쿠키에 담아** 보내야 한다(`expires` 를 미래로 둔 채 값은 만료된 JWT). 그러면 서버가
그 토큰을 실제로 받고 401 을 내는 경로가 재현된다.
🔵 이 절차는 이 창에서 만든 **새 지식**이고, 다음 사람이 같은 30분을 다시 쓰지 않게 한다.


---

# 🟢🟢 수확 (2026-09-12 UTC · 데모 창 · 소유자 승인 「재굽기 + apply + 창」)

## 창의 이력 — 이 티켓이 쓴 값의 출처

| | |
|---|---|
| AMI | `ami-058f6293d1408f91e` (`RepoCommit = 8cf474346`, **provenance = ami-tag**) |
| 인스턴스 | `i-027d6b39694585491` · `r6i.2xlarge` · 07:05:18Z 기동 |
| 창이 쓴 예산 | **50분** (`used 651 → 701` / budget 1200) |
| 닫음 | `POST /stop` → `state=stopped` (제어 API + `aws ec2 describe-instances` **양쪽** 확인) |

## 🔵 네 칸이 전부 실측으로 닫혔다 — ⚪ 가 하나도 없다

AC-3 이 *"창이 짧으면 ⚪ 로 닫아라"* 라고 허용했지만 **예산이 534분 남아 있어서 짧지 않았다.**
⇒ 허용된 ⚪ 를 쓰지 않고 **34분을 실제로 기다려 쟀다.** 🔴 «잴 수 있는데 ⚪ 로 닫는 것»은
이 티켓이 경계한 그 함정의 거울상이다.

## 🔴 이 티켓이 남긴 가장 중요한 구별

**①과 ②는 같은 증상(로그인 화면)으로 보이지만 다른 문이다.**

| | 경로 | 사유 문구 |
|---|---|---|
| ① 훼손 토큰 (401) | `/login?error=session_expired` | 🟢 있다 |
| ② 30분 만료 | `/login?redirect=%2Fecommerce` | 🔴 **없다** |

🔵 ①만 보고 «고쳤다» 고 하면 ②는 계속 산다. **최종 URL 의 쿼리까지 봐야** 갈린다.
