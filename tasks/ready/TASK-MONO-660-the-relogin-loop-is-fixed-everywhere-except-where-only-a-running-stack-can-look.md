# Task ID

TASK-MONO-660

# Title

⏳ 재로그인 루프 수리 중 **라이브가 아니면 못 보는 한 칸** — 그리고 그 옆에서 드러난 «서버 사이드는 refresh 하지 않는다» 를 잰다

# Status

ready

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

- [ ] **AC-0 (착수 게이트 — verify-then-act)** — 재고 나서 행동한다.
      컨트롤 플레인 `/status` 가 `state=running` + `ip` 가 있고, `auth.hubwang.com/.well-known/openid-configuration`
      이 **200** 이어야 착수한다. 🔴 하나라도 아니면 **아무것도 하지 말고** 이 티켓을
      `ready/` 에 그대로 두고 끝낸다 — 「켜는 것」은 이 티켓의 범위가 아니다.
      🔵 남은 분 예산을 기록한다(창을 얼마나 쓸 수 있는지가 ②의 표본 수를 정한다).
- [ ] **AC-1 — ① 루프가 끊겼다** (본체)
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
- [ ] **AC-2 (대조군)** — 같은 창에서, **멀쩡한 세션**으로 `/login` 을 직접 친다.
      → **`/console` 로 간다**(편의가 살아 있다). 🔴 이 칸이 없으면 AC-1 의 PASS 가
      「루프를 끊었다」인지 「단락 회로를 통째로 죽였다」인지 갈리지 않는다.
- [ ] **AC-3 — ② 서버 사이드 refresh 부재를 «잰다»**
      멀쩡한 세션으로 로그인한 뒤 **아무것도 하지 않고** 액세스 토큰 수명이 지나도록
      두고, 그 뒤 SSR 화면(`/ecommerce`)을 연다.
      - 강제 로그아웃이 나면 → **일어난다**. 몇 분 만인지 기록한다.
      - 안 나면 → **그 창에서는 재현 안 됨**으로 기록한다.
      🔴 **창이 짧으면 이 칸은 ⚪ 로 닫아라** — *"측정 못 했다 + 왜"* 가 닫힘이고,
      추측을 판정으로 적는 것이 이 저장소가 가장 자주 밟은 함정이다.
- [ ] **AC-4 — 결과를 되돌려 준다.** ①이 FAIL 이면 fix 티켓을, ②가 결함이면 별도
      티켓을 **이 창 안에서** 기안한다. 🔴 산문으로 *"나중에"* 라고 쓰지 마라 —
      큐가 아닌 곳에 적은 의무는 사라진다(`TASK-MONO-537` 이 그 모양으로 9일을 잃었다).

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
