# Task ID

TASK-MONO-574

# Title

`ADR-MONO-067` **AC-0 ③** — HTTPS 프런트 ↔ 평문 IdP 로그인이 **끝까지** 되는지 실측한다. 이 축이 안 풀리면 단계 3·4 는 못 간다.

# Status

review

# Owner

monorepo

# Task Tags

- adr
- measurement
- auth

---

## 🟢 소유자 지정 (2026-08-26) — **기동은 «DNS 배선 후 한 번에»**

> 잔여 **104분** / 부팅 **~11분** / 9-01 리셋

기동 창은 **하나**다. 그 창이 열리기 전에 **아래가 다 서 있어야** 한다 — 안 그러면
`TASK-MONO-574` 가 스스로 적은 대로 *"예산만 쓰고 아무것도 못 잰다"*:

> 🔴🔴 **이 표는 08-26 판이고 1·2·3 은 이미 끝났다 (08-28 완료 · 08-29 라이브 재확인).**
> **아래 § 재측정 을 읽지 않고 이 표만 보면 「소유자가 DNS 부터 해야 한다」로 읽힌다** —
> 그리고 **2026-08-29 에 내가 정확히 그렇게 보고했다.** 재측정 절이 *"표만 읽고 «소유자
> 3단계가 남았다» 로 보고하면 이미 끝난 일을 다시 시키게 된다"* 라고 **하루 전에 경고해
> 뒀는데도** 그렇게 됐다. 🔴 그래서 이번엔 **표 자신을 고친다** — 경고를 아래에만 두면
> 위를 먼저 읽는 사람은 계속 틀린다.
> [[feedback_one_fact_in_two_sections_only_one_gets_fixed]]
> [[feedback_measure_the_plans_premise_before_starting_the_phase]]

| 순서 | 할 일 | 누가 | 상태 |
|---:|---|---|---|
| 1 | `hubwang.com` 를 Vercel 네임서버로 위임 | 소유자 | ✅ **완료** — `hubwang.com` **200** (08-29 재확인) |
| 2 | `hubwang.com` + `www`(301) → `kanggle-portfolio` · `fan.hubwang.com` → `kanggle-fan` | 소유자 | ✅ **완료** — `fan.hubwang.com` 응답함 |
| 3 | 🔴 **2 가 끝난 뒤에** `NEXTAUTH_URL=https://fan.hubwang.com` + `NEXTAUTH_SECRET` + OIDC 4종 | 소유자 | ✅ **입력됨** — `/api/auth/session` **200** · `/api/auth/providers` **200**(iam 등록, 콜백=커스텀 도메인). 🔵 **값의 «정확성»은 아직 미판정** — 아래 |
| 4 | IdP 에 `https://fan.hubwang.com/api/auth/callback/iam` 재시드 | 저장소 | ✅ **완료** — `TASK-BE-582` `V0033` (PR #3477) |
| **5** | **AMI 재굽기 → 기동 1회** — 이 창에서 `TASK-MONO-581` 의 남은 항목 + `TASK-MONO-574` 를 **전부** | 승인 완료 | ⏸️ **여기만 남았다** |

🔴 **3 의 순서를 지켜야 한다** — 도메인을 붙이기 전에 `NEXTAUTH_URL` 을 넣으면
그 값이 빌드에 인라인돼 굳는다. (지켜졌다 — 2 → 3 순으로 들어갔고 재배포가 성공했다.)

### 🔵 3 이 «입력됨」이지 «옳다»가 아닌 이유 — 이것이 이 티켓의 측정 대상이다

`https://fan.hubwang.com/api/auth/signin/iam` 은 **08-28 과 08-29 둘 다 302 → `?error=Configuration`**
이다. 🔵 관측과 정합적인 가설은 `OIDC_ISSUER_URL` 이 `http://iam.<ip>.sslip.io` 인데
컨트롤 플레인이 `ip: null`(`stopped`)이라 **issuer discovery 가 도달할 곳이 없다**는 것이다.
🔴 **가설이다** — issuer 에 무슨 값이 들어 있는지는 대시보드 사안이라 저장소에서 못 본다.
⇒ **기동 뒤 같은 요청이 통과하면 확정되고, 안 통과하면 이 티켓의 본 측정이 거기서 시작된다.**
🔴 그러므로 3 을 «미완» 으로 되돌려 소유자에게 다시 시키지 마라 — **다음 동작은 5(기동)다.**

### ✅ 2026-08-29 라이브 재확인 (기록도 단일표본이므로 다시 쟀다)

```
hubwang.com                              200
fan.hubwang.com/api/auth/session         200
fan.hubwang.com/api/auth/providers       200   iam / callbackUrl=https://fan.hubwang.com/api/auth/callback/iam
fan.hubwang.com/artists                  307 → /login?from=%2Fartists     (라우트 가드 작동)
fan.hubwang.com/api/auth/signin/iam      302 → /login?error=Configuration (기동 전이므로 예상됨)
control-plane /status                    {"state":"stopped","ip":null,"used_minutes":496,"budget_minutes":600}
```

🔵 `used_minutes` 가 **496 그대로** — 08-26·08-28 기록과 같으므로 **그 사이 기동이 없었다.**
🔵 `TASK-MONO-581` AC-0 도 같은 날 쟀다: 승인 ✅ · **AMI 는 최신이 아니어서 재굽기 필요** ·
예산 104분 충분. ⇒ **5 를 지금 실행할 수 있다.**

🔵 **자체 도메인은 오히려 유리하다** — `vercel.app` 은 preview URL 이 배포마다 달라
고정 `redirect_uri` 를 못 박았는데, `fan.hubwang.com` 은 고정이라 4의 시드가 **한 번으로 끝난다.**

🔴🔴 **이 티켓의 우선순위가 올라갔다 (2026-08-26).** `TASK-MONO-585`/`586` 에서
라우트를 세어 보니 **console 67개 중 익명 도달 1개, fan 11개 중 1개**이고 그 하나는
로그인 화면 자신이다. 즉 **D4 가 안 풀리면 단계 3·4 는 방문자에게 보이는 변화를 하나도
만들지 못한다.** `ADR-MONO-067` 이 *주장*하던 것이 이제 **실측**이다.

---

# ⏳ 선행 — **실측으로 다시 썼다 (2026-08-25). 기동만으로는 못 잰다.**

| # | 선행 | 상태 | 누가 |
|---|---|---|---|
| 1 | `TASK-MONO-571` (AC-0 ②) | ✅ **해소** — `PLAINTEXT_HTTP_EGRESS_WORKS` | — |
| 2 | **Vercel `kanggle-fan` 의 OIDC env** | ✅ **해소 (2026-08-28 재측정)** — 아래 §재측정 | 소유자 (완료) |
| 3 | **IdP 에 Vercel 도메인 `redirect_uri` 등록** | ✅ **해소 (2026-08-26)** — [`TASK-BE-582`](../../projects/iam-platform/tasks/review/TASK-BE-582-register-the-fan-vercel-callback-nobody-owned-this.md) `V0033` (PR #3477, CI 13/13) | iam-platform |
| 4 | 데모 인스턴스 기동 | ⏸️ | 🔴 **사용자 승인** (예산 차감, 부팅 ~11분) |

🔴 **2·3 없이 기동하면 예산만 쓰고 아무것도 못 잰다.** 그래서 기동 전에 이 둘을 먼저 확인했다.

## 🟢 재측정 (2026-08-28 UTC) — **선행 넷 중 셋이 해소됐다. 남은 것은 기동 하나뿐이다**

🔴 **이 절은 위 표를 «다시 읽어서» 쓴 것이 아니라 라이브를 찔러서 쓴 것이다.** 위 표는
08-26 판이고, 그 사이 소유자가 DNS·도메인·env 를 전부 넣고 재배포가 성공했다 —
**표만 읽고 «소유자 3단계가 남았다» 로 보고하면 이미 끝난 일을 다시 시키게 된다.**
[[feedback_measure_the_plans_premise_before_starting_the_phase]] [[feedback_a_figure_nothing_can_fail_on_will_drift]]

| 찌른 곳 | 응답 | 뜻 |
|---|---|---|
| `https://fan.hubwang.com/api/auth/session` | **200** | DNS 위임 · 도메인 연결 · `NEXTAUTH_URL`/`SECRET` **전부 됨** |
| `https://fan.hubwang.com/artists` | **307** → `/login?from=%2Fartists` | 🔵 **라우트 가드가 프로덕션에서 실제로 돈다** (`TASK-FAN-FE-018` 가설 B 의 라이브 확인) |
| `https://fan.hubwang.com/api/auth/providers` | `{"iam":{…,"callbackUrl":"https://fan.hubwang.com/api/auth/callback/iam"}}` | OIDC provider 등록됨. 🔵 콜백이 **`TASK-BE-582` 가 시드한 값과 일치** |
| `https://fan.hubwang.com/api/auth/signin/iam` | **302** → `/login?error=Configuration` | 🔴 **로그인은 아직 실패한다** |
| 컨트롤 플레인 `/status` | `{"state":"stopped","ip":null,"used_minutes":496,"budget_minutes":600}` | 🔴 **데모 인스턴스가 꺼져 있다** |

### 🔴 `error=Configuration` 의 **의미가 08-23 과 다르다**

§실측 ① 의 08-23 관측은 `/api/auth/providers` **자체가** 에러 문서를 냈다(= `NEXTAUTH_SECRET`
부재). 지금은 **providers 가 정상이고 signin 만** 실패한다. 같은 문자열, **다른 결함**이다 —
문구가 같다고 같은 원인으로 접지 마라. [[feedback_if_the_symptom_survives_the_fix_it_was_not_the_cause]]

🔵 **관측과 정합적인 가설**: `OIDC_ISSUER_URL` 이 `http://iam.<ip>.sslip.io` 인데 `/status` 가
`ip: null` 이므로 issuer discovery 가 도달할 곳이 없다. 🔴 **그러나 이것은 가설이다** —
issuer 값이 무엇으로 채워져 있는지는 대시보드 사안이라 저장소에서 못 본다. **기동 뒤 같은
요청이 통과하면 확정되고, 안 통과하면 이 티켓의 본 측정이 시작된다.**
[[feedback_a_verifiable_mechanism_is_not_the_cause]]

### 예산 — 티켓이 적은 값과 **같다**

`used=496 / budget=600` ⇒ **잔여 104분**. § 소유자 지정(08-26)이 적은 «잔여 104분» 과 동일하므로
**그 사이 기동이 한 번도 없었다.** 9-01 리셋.

⇒ **남은 선행은 4번(기동) 하나**이고, `TASK-MONO-581` 이 그 창에 다섯 검증을 묶어 두었다.
🔴 재배포·Vercel 한도는 **이 티켓의 블로커가 아니다** — 그쪽은 이미 다 됐다.

🔴🔴 **선행 3 은 아무 티켓도 안 들고 있었다 (2026-08-26 발견).**
`TASK-MONO-584` 는 *"`redirect_uri` 시드 → **574**(이미 소유)"* 라 적었고, **이 티켓은**
§ Related Contracts 에서 *"**변경은 이 티켓 범위 밖**(측정만 한다)"* 라 적었다. **둘 다 안 들었다.**
→ `TASK-BE-582` 를 새로 기안했다. 🔵 그 티켓은 **소유자 대시보드와 독립**이라
DNS 배선을 기다리지 않고 지금 진행할 수 있다 — 즉 **이 티켓의 선행 중 하나가 먼저 없어진다.**

## 실측 ① — Vercel 의 fan 은 OIDC 가 설정되어 있지 않다

```
GET https://kanggle-fan.vercel.app/api/auth/signin/iam
  → 302  Location: https://kanggle-fan.vercel.app/login?error=Configuration

GET https://kanggle-fan.vercel.app/api/auth/providers
  → {"message":"There was a problem with the server configuration. …"}
```

🔴🔴 **2026-08-26 — 이 절의 주소가 바뀌었다. 위의 실측 블록은 고치지 않았다.**

`TASK-MONO-584` 가 랜딩하면서 팬의 공개 호스트명이 **`fan.hubwang.com`** 으로 정해졌다
(정본 표: `TEMPLATE.md` § 공개 호스트명 배분).

- ✅ **위 «실측 ①» 의 `kanggle-fan.vercel.app` 응답은 그대로 둔다** — 그것은 2026-08-23 에
  **실제로 관측된 것**이고, 관측 기록을 나중 사실로 덮어쓰면 그 측정이 언제 무엇을 잰 것인지
  복구할 수 없게 된다.
- 🔴 **처방(아래 표)만 갱신한다.** `NEXTAUTH_URL` 은 이제 `vercel.app` 이 아니다.
- 🔴 **선행 3(`redirect_uri` 시드)도 대상이 바뀐다** — 등록할 값은
  `https://fan.hubwang.com/api/auth/callback/iam` 이다. 🔵 이것은 오히려 **유리한 변화**다:
  `vercel.app` 은 배포마다 preview URL 이 달라 고정 `redirect_uri` 를 못 박았는데,
  **자체 도메인은 고정**이라 시드가 한 번으로 끝난다.

**소유자가 대시보드에서 채워야 하는 값** (`src/shared/config/env.ts` 전수):

| 변수 | 값 | 비고 |
|---|---|---|
| `NEXTAUTH_URL` | 🔴 **`https://fan.hubwang.com`** (← 갱신, 아래 §) | 🔵 이게 `https://` 라 `secureCookie` 가 **자동으로 켜진다** |
| `NEXTAUTH_SECRET` | (생성) | 없으면 next-auth 가 통째로 `error=Configuration` |
| `OIDC_ISSUER_URL` | `http://iam.<ip-대시>.sslip.io` | 🔴 아래 § 참조 — **부팅마다 바뀐다** |
| `OIDC_CLIENT_ID` | `fan-platform-user-flow-client` | 기본값과 동일 |
| `OIDC_CLIENT_SECRET` | (시드의 값) | 기본값이 빈 문자열 |

## 실측 ② — IdP 에 Vercel 도메인이 등록돼 있지 않다

`fan-platform-user-flow-client` 의 등록된 `redirect_uri` (마이그레이션 시드 전수):

```
http://fan-platform.local/api/auth/callback/iam
http://localhost:3000/api/auth/callback/iam
```

**`https://kanggle-fan.vercel.app/api/auth/callback/iam` 이 없다.** env 를 다 채워도 IdP 가
콜백을 거절하므로, 그 상태로 재면 얻는 것은 *"스킴 경계가 문제다"* 가 아니라
**"설정이 안 됐다"** 이고 — 그건 이 티켓이 묻는 질문이 아니다.

## 🔴🔴 실측 ③ — 그리고 더 큰 것: **issuer 주소가 부팅마다 바뀐다**

`OIDC_ISSUER_URL` 은 Vercel env 라 **배포 시점에 고정**된다. 그런데 데모 IdP 주소는
`iam.<ip-대시>.sslip.io` 이고 **부팅마다 IP 가 바뀐다.** 한 번 채워도 다음 부팅에 낡는다.

⇒ **D2(주소는 런타임 조회여야 한다)가 인증 축에서도 그대로 재현된다.** 그런데 여기서는 더
어렵다 — next-auth 는 `issuer` 를 **설정값**으로 받고, 그 값은 discovery 문서와 토큰의
`iss` 클레임 **양쪽**에 묶인다.

🔵 이것은 `TASK-MONO-576`(D4 ADR)의 **입력이 하나 더 늘었다**는 뜻이다. D4 는 *"왕복이 되는가"*
만이 아니라 **"움직이는 issuer 를 어떻게 고정하는가"** 도 답해야 한다.

---

# Goal

`ADR-MONO-067` § AC-0 3번 항목을 실측한다.

> **OIDC 왕복** — HTTPS 프런트 ↔ 평문 IdP 에서 로그인이 끝까지 되는지. 🔴 *"안 될 것 같다"* 가
> 아니라 **실측**이어야 한다. 이 저장소는 쿠키 축에서 두 방향 모두 데인 적이 있다.

---

# Context — 왜 이것이 프록시로 안 덮이는가

세 앱 다 next-auth v5 OIDC 다. 소비 지점의 **성격이 갈린다**(2026-08-23 fan 실측, console·web-store 도 같은 라이브러리):

| 소비 | 종류 | 프록시로 흡수되나 |
|---|---|---|
| discovery(`.well-known`) · `/oauth2/token` | 서버 fetch | ✅ D2 와 같은 취급 |
| **authorize 리다이렉트** · **`/connect/logout`** | **최상위 내비게이션** | ❌ **불가** |

최상위 내비게이션은 mixed content 규칙 **밖**이라 이동 자체는 막히지 않는다. 그래서 *"열리니까
된다"* 로 오독하기 쉽다 — 그러나 **열리는 것과 왕복이 끝나는 것은 다른 질문**이다.
[[env_top_level_navigation_is_exempt_from_mixed_content]]

## 이미 아는 걸림돌 (실측)

- **`redirect_uri` 가 Flyway 시드다.** fan 의 등록된 앱 루트는 `http://localhost:3002/` 와
  `http://fan-platform.local/`(GAP V0011+V0028, `federated-logout.ts` 주석). Vercel 도메인을
  넣으려면 **마이그레이션 변경**이고, preview URL 은 배포마다 달라서 **production 고정 도메인**이
  전제다.
- **`issuer` 가 부팅마다 바뀐다.** discovery 문서와 토큰의 `iss` 클레임이 함께 움직이는데
  next-auth 는 issuer 를 **설정값**으로 받는다.
- 🔵 **쿠키는 오히려 유리하다** — fan `session.ts` 가 `NEXTAUTH_URL` 이 `https://` 면
  `secureCookie` 를 켜므로 Vercel 에서 자동으로 맞는다.

---

# Acceptance Criteria

## AC-0 — 전제 재확인 (verify-then-act)

착수 시점에 다음이 여전히 참인지 본다. 하나라도 어긋나면 STOP 하고 티켓을 갱신한다.

- `TASK-MONO-571` 의 AC-0 ② 결과가 **참**이다.
- 데모 인스턴스가 `running` 이고 IdP 가 응답한다(`/status` 의 `state`).
- 앱의 `redirect_uri` 등록값이 이 티켓이 적은 것과 같다 — **시드 파일을 grep 해서** 확인한다.
  기억이 아니라 정의 파일이 근거다.

## AC-1 — 왕복을 **단계별로** 기록한다, 성패 한 줄이 아니라

로그인은 여러 홉이고, **어디서 끊기는지가 조치를 가른다.** 각 홉의 상태 코드와 `Location`,
그리고 쿠키의 `Set-Cookie` 속성을 그대로 남긴다.

| 홉 | 무엇을 확인 |
|---|---|
| ① 앱 → `/api/auth/signin` | 302 의 목적지가 IdP 인가 |
| ② 브라우저 → IdP authorize (평문 HTTP) | 페이지가 뜨는가. **여기서 막히면 브라우저 정책** |
| ③ IdP → 앱 콜백 (평문 → HTTPS) | `state`/PKCE 쿠키가 **살아 돌아오는가** |
| ④ 앱 서버 → IdP token | 서버 fetch. ② 와 다른 축이다 |
| ⑤ 세션 쿠키 | `Secure`/`SameSite` 가 무엇으로 붙었나 |

🔴 **③ 이 이 티켓의 본체다.** 크로스사이트 최상위 리다이렉트라 `SameSite=Lax` 면 살아야
하는데, 이 저장소는 쿠키 축에서 **양방향으로** 데인 적이 있다. 추론하지 말고 찍어라.

## AC-2 — 로그아웃도 같이 잰다

`/connect/logout` 은 authorize 와 **같은 종류**(최상위 내비게이션)이고 별도로 깨질 수 있다.
로그인만 재고 "왕복 OK" 라고 적지 않는다.

## AC-3 — 판정과 **판정하지 못한 것**을 함께 적는다

- 참: ①~⑤ 가 전부 통과하고 보호된 페이지가 렌더된다.
- 🔴 **거짓일 때 "무엇이" 거짓인지 적는다** — 브라우저 정책 / 쿠키 / `redirect_uri` 불일치 /
  `iss` 불일치는 **다른 결함**이고 후속 조치가 전부 다르다.
- 잰 앱이 하나면 **그 앱에 대해서만** 적는다. 세 앱이 같은 라이브러리를 쓴다는 것은
  **가설이지 측정이 아니다**.

## AC-4 — 결과를 `ADR-MONO-067` § AC-0 3번에 기록한다

거짓이면 **D4 의 후속 결정**(`TASK-MONO-576`)의 입력이 된다. 이 티켓은 결정을 내리지 않는다.

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § AC-0 (3), § D4
- `projects/fan-platform/specs/integration/iam-integration.md`
- `projects/iam-platform/specs/features/consumer-integration-guide.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/` — `redirect_uri` 등록이 계약이면 여기가 근거다.
  **변경은 이 티켓 범위 밖**(측정만 한다).

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 브라우저가 ② 에서 경고만 띄우고 진행한다 | **경고는 실패가 아니다.** 무엇이 떴는지 기록하고 왕복을 계속한다 |
| `redirect_uri` 불일치로 IdP 가 거절한다 | 이건 **설정 미비**이지 스킴 경계 문제가 아니다. 시드를 맞춘 뒤 다시 잰다 — 그 전 결과로 ③ 을 판정하지 않는다 |
| 데모가 부팅 중(`starting`)이라 IdP 가 502 | 측정 아님. `running` 을 기다린다 |
| 한 앱만 쟀다 | AC-3 대로 그 앱에 대해서만 적는다 |
| 프리뷰 URL 로 재려 한다 | ❌ **Deployment Protection 이 302 로 막는다**(2026-08-23 실측). 프로덕션 별칭만 공개다 |

---

# Failure Scenarios

| 실패 | 뜻 | 다음 |
|---|---|---|
| ③ 에서 `state`/PKCE 쿠키가 유실 | 스킴 경계가 진짜 벽이다 | D4 가 (B) 로 안 풀린다 ⇒ **EC2 TLS 종단 축**이 유력해진다 |
| ② 에서 브라우저가 차단 | 내비게이션 예외가 이 조합엔 안 통한다 | 같은 결론, 더 강함 |
| ④ 만 실패 | 서버 fetch 축 = AC-0 ② 와 같은 문제 | ② 결과와 대조한다. 둘이 어긋나면 **하나가 틀린 것** |
| 전부 통과 | (B) 가 인증 축에서도 성립 | D4 를 (B) 로 확정하는 근거. 단 **잰 앱에 한해서다** |

---

# 🔴🔴 라이브 측정 (2026-08-29, `TASK-MONO-581` 창) — **왕복은 실패했다. 가설 둘이 죽었다.**

`TASK-MONO-581` 이 재굽기 + 기동을 한 창에 묶었고, 이 티켓의 선행 4(기동)가 그 안에서
해소됐다. **결과: 로그인은 여전히 안 된다.** 그러나 이 창이 후보 공간을 크게 줄였다.

| 시각 (UTC) | 무엇 | 값 |
|---|---|---|
| 16:03 | 데모 `up complete` · IP `3.38.176.240` | — |
| 16:05 | `fan.hubwang.com/api/auth/signin/iam` | **302 → `/login?error=Configuration`** |
| 16:05 | `iam.3-38-176-240.sslip.io/.well-known/openid-configuration` | **200** |
| 16:05 | 그 문서의 `issuer` | `http://iam.3-38-176-240.sslip.io` |
| 17:03 | 소유자가 env 를 그 값으로 넣고 **재배포 확인** 후 재측정 | **302 → `error=Configuration`** (그대로) |

## 반증 ① — *"`ip: null` 이라 discovery 가 도달할 곳이 없다"* (이 티켓 § 재측정, 08-28)

**IP 가 생겼고 discovery 가 200 인데 증상이 그대로다.** 그 가설은 «관측과 정합적» 이었을 뿐
원인이 아니었다. 🔵 이 티켓이 그때 스스로 *"이것은 가설이다"* 라고 적어 둔 것이 옳았다.
[[feedback_if_the_symptom_survives_the_fix_it_was_not_the_cause]] [[feedback_a_verifiable_mechanism_is_not_the_cause]]

## 반증 ② — *"Vercel env 가 낡은 IP 를 들고 있다"* (같은 날 내가 세운 가설)

소유자가 **현재 IP 로 정확히** `OIDC_ISSUER_URL=http://iam.3-38-176-240.sslip.io` 를 넣고
**재배포까지 확인**했다. **증상이 그대로다.** ⇒ issuer 값의 신선도 문제가 아니다.

🔴 **첫 재측정은 판정 불가였고, 그것을 「반증」으로 읽을 뻔했다** — env 는 다음 배포부터
적용되는데 **배포가 새것인지 가릴 기준선을 안 찍어 뒀다**(청크 해시를 사후에 찍어도 비교
대상이 없다). 소유자에게 물어 «눌렀다» 를 확인하고 나서야 이 칸이 성립했다.
🔵 **교훈: 「값을 바꿨다」를 재려면 바꾸기 전에 식별자를 찍어라.**

## 🎯 결정적 판별 — *"Vercel 이 평문 HTTP 에 못 닿는다"* 도 **아니다**

**같은 순간**, 같은 Vercel 계정의 형제 앱이 평문 HTTP 데모 백엔드로 **서버측 fetch 를
성공**시켰다:

| `store.hubwang.com/products` | 데모 `stopped` | 데모 `running` |
|---|---|---|
| 크기 | 40,618 B | **74,981 B** |
| `demo-backend-notice` | 2건 | **0건** |
| 시드 상품 링크 | 0 | **8개** |

🔵 **같은 플랫폼 · 같은 순간 · 같은 대상(평문 HTTP sslip 호스트)** — 그래서 이것이 대조군이다.
⇒ 남은 후보는 **네트워크가 아니라 애플리케이션/라이브러리 쪽**이다.

## 🔴 남은 후보 — **선언하지 않는다.** 오늘 가설 둘이 죽었다.

코드에서 **확인된 사실**만 적는다:

- `fan-platform-web` 은 `next-auth@5.0.0-beta.25` 이고, provider 에 **`issuer` 만** 주고
  discovery 에 맡긴다 (`src/shared/auth/auth.ts:56`). `wellKnown` 오버라이드도, 평문 허용
  플래그도 **소스 어디에도 없다**.
- 🔵 형제 `web-store` 도 **같은 `next-auth@5.0.0-beta.25`** 다.

**다음에 할 판별 — 기동이 필요 없다:**

1. `@auth/core` 가 쓰는 discovery 구현이 **`http:` issuer 를 거부하는지**를 의존성 코드에서
   읽는다. 🔴 이번엔 이 호스트에 `node_modules` 가 없어 못 읽었다.
2. 형제 `web-store` 에 같은 평문 issuer 로 OIDC 를 물려 **같은 방식으로 실패하는지** 본다.
   같으면 「이 라이브러리 × 평문 issuer」, 다르면 「이 앱의 배선」이다.

🔵 **이 창의 산출물은 「574 를 못 닫았다」가 아니라 「후보 셋을 하나로 줄였고, 그 하나는
기동 없이 잴 수 있다」이다.**

## 🔴 그리고 구조적 발견 — issuer 는 **부팅 범위**, env 는 **배포 범위**

IdP 가 광고하는 issuer 가 IP 파생이라 **부팅마다 바뀐다.** Vercel env 는 배포 시점에 굳는다.
⇒ **우연히 같은 부팅이 아니면 영원히 안 맞는다.** 이번에 소유자가 손으로 맞춘 값도
**인스턴스 정지와 함께 죽었다**(IP 반납).

🔴 **그러므로 이 티켓을 「라이브로」 닫는 것은 `ADR-MONO-067` 의 D4 가 먼저 정해져야 한다**
(`TASK-MONO-576`). 매 부팅마다 소유자가 env 를 고치고 재배포하는 것은 절차가 아니다 —
그리고 그 재배포 하나하나가 Vercel 일일 배포 한도를 먹는다(`TASK-MONO-590`).

---

# 🔴🔴 후보 제거 — **의존성 코드를 직접 읽었다** (2026-08-30, 기동 없이)

위 § 라이브 측정이 *"남은 후보는 앱/라이브러리 축 하나"* 로 닫혔다. 그 하나를 **기동 없이**
쟀다 — `node_modules` 가 `projects/fan-platform/node_modules/.pnpm/` 아래에 **있었다**
(앱 디렉터리 밑에서만 찾다 «없다» 로 보고할 뻔했다).

**해석된 버전**: `next-auth@5.0.0-beta.25` → `@auth/core@0.37.2` → `oauth4webapi@3.8.6`.

## ✅ 기전은 실재한다 — `oauth4webapi` 는 기본적으로 평문을 거부한다

```js
// oauth4webapi/build/index.js
async function performDiscovery(input, urlName, transform, options) {
    checkProtocol(input, options?.[allowInsecureRequests] !== true);   // 기본 = 강제
export function checkProtocol(url, enforceHttps) {
    if (enforceHttps && url.protocol !== 'https:')
        throw OPE('only requests to HTTPS are allowed', HTTP_REQUEST_FORBIDDEN, url);
}
```

🔵 **그러나 그 기전이 우리 경로에서 발동하지 않는다.** `@auth/core@0.37.2` 의 discovery
호출부 **전부**가 그 플래그를 켠다:

| 호출부 | `allowInsecureRequests` |
|---|---|
| `lib/actions/signin/authorization-url.js:18` | ✅ `true` (주석: *"TODO: move away from allowing insecure HTTP requests"*) |
| `lib/actions/callback/oauth/callback.js:38` | ✅ `true` |

⇒ **「라이브러리가 평문 issuer 를 거부한다」는 반증됐다.** (가설 ③)

## ✅ 끝 슬래시 불일치도 아니다 (가설 ④)

`processDiscoveryResponse` 는 **양쪽을 정규화해서** 비교한다:

```js
if (expected !== _nodiscoverycheck && new URL(json.issuer).href !== expected.href)
```

`new URL('http://iam.x.sslip.io').href` 는 양쪽 다 `http://iam.x.sslip.io/` 가 된다.
⇒ 슬래시 유무는 이 비교를 못 깬다.

## ✅ `clientSecret` 누락도 **이 오류**를 만들지 않는다 (가설 ⑤)

`env.ts:36` 이 `oidcClientSecret: process.env.OIDC_CLIENT_SECRET ?? ''` 로 **빈 문자열**을
기본값으로 준다. 🔴 그래서 «Vercel 에 그 값이 없으면?» 을 의심했는데, `@auth/core` 의
`assertConfig` 는 **`clientSecret` 을 검사하지 않는다**(`MissingSecret`·`InvalidEndpoints`·
`MissingAuthorize`·`UntrustedHost` 등만 본다) ⇒ 이 경로의 `Configuration` 사유가 아니다.

🔵 **다만 이건 따로 확인할 가치가 있다** — 빈 secret 은 **토큰 교환에서** 죽고, 그때
증상은 signin 이 아니라 callback 이다. **지금 증상과 다른 결함이지 없는 결함이 아니다.**

## 📋 지금까지 죽은 가설 — **여섯**

| # | 가설 | 어떻게 죽었나 |
|---|---|---|
| ① | `ip:null` 이라 discovery 도달 불가 | IP 생기고 discovery **200** 인데 증상 그대로 |
| ② | Vercel env 가 낡은 IP | 현재 IP 로 넣고 **재배포 확인** — 그대로 |
| ③ | 라이브러리가 평문 issuer 거부 | 호출부 전부가 `allowInsecureRequests: true` |
| ④ | issuer 끝 슬래시 불일치 | 비교 전에 **양쪽 정규화** |
| ⑤ | `clientSecret` 누락 | `assertConfig` 가 그 필드를 안 본다 |
| ⑥ | Vercel 이 평문 HTTP 에 못 닿음 | **같은 순간** 형제 `store.hubwang.com` 이 성공 |

🔴 **여섯 번 틀렸다는 것 자체가 정보다** — 이 증상(`error=Configuration`)은 **원인을 안
말해 주는 문구**이고, 그래서 밖에서 추론하는 것으로는 못 좁힌다.
[[feedback_a_verifiable_mechanism_is_not_the_cause]]

## 🎯 남은 한 수 — **서버 로그를 읽는다.** 추론을 그만둔다.

`@auth/core` 는 실패 시 서버에서 `[auth][error]` 로 **실제 원인**을 찍는다. 그 줄 하나가
위 여섯 번의 추론보다 정확하다.

- **어디서**: Vercel 대시보드 → `kanggle-fan` → **Logs** (Runtime Logs)
- **무엇을**: `/api/auth/signin/iam` 요청의 `[auth][error]` 줄
- **언제 것**: **2026-08-29T17:03Z 전후** — 소유자가 env 를 넣고 재배포한 뒤의 시도.
  🔵 Pro 플랜이라 보관 기간이 길다.

🔴 **지금 다시 시도해서 얻는 로그는 다른 오류다** — 데모가 정지돼 IP 가 반납됐으므로
그때는 **연결 실패**가 찍힌다. 원래 원인을 보려면 **그때의 로그**여야 한다.

🔵 그리고 이것으로 이 티켓의 성격이 바뀐다: 남은 것은 **측정이 아니라 조회**이고,
기동도 예산도 필요 없다.

---

# ✅ AC 판정 (2026-08-30) — **판정 «거짓». 그러나 끊긴 곳이 이 티켓이 예상한 곳이 아니다.**

## AC-0 — 전제 재확인 ✅

| 전제 | 확인 | 근거 |
|---|---|---|
| `TASK-MONO-571` AC-0 ② 가 참 | ✅ | `PLAINTEXT_HTTP_EGRESS_WORKS` (ADR-067 § AC-0 ②) |
| 데모가 `running` 이고 IdP 가 응답 | ✅ | `TASK-MONO-581` 기동 창(2026-08-29). discovery **200** |
| `redirect_uri` 등록값이 티켓이 적은 것과 같다 | ✅ | 시드 파일 grep — `V0033`(`TASK-BE-582`, PR #3477). 🔵 **기억이 아니라 정의 파일** |

## AC-1 — 왕복을 단계별로 ✅ (그리고 **어디까지 갔는지가 판정의 본체다**)

| 홉 | 관측 | 판정 |
|---|---|---|
| ① 앱 → `/api/auth/signin/iam` | **302 → `/login?error=Configuration`** | 🔴 **여기서 끊긴다.** 목적지가 IdP 가 **아니다** |
| ② 브라우저 → IdP authorize | — | ⚪ **미측정** (① 이 authorize URL 을 안 만들었다) |
| ③ IdP → 앱 콜백 (`state`/PKCE 쿠키) | — | ⚪ **미측정** |
| ④ 앱 서버 → IdP token | — | ⚪ **미측정** |
| ⑤ 세션 쿠키 속성 | — | ⚪ **미측정** |

🔴🔴 **⚪ 는 «실패» 가 아니다.** 이 티켓이 *"③ 이 이 티켓의 본체다"* 라고 적어 둔 축 —
평문 → HTTPS 크로스사이트 콜백에서 `SameSite=Lax` 쿠키가 살아 돌아오는가 — 은 **한 번도 잰
적이 없다.** 첫 홉이 죽어서 거기까지 못 갔다. 이것을 «쿠키 축이 깨졌다» 로 기록하면 **후속
결정의 입력이 통째로 거짓**이 된다. [[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]]

## AC-2 — 로그아웃 ⚪ **미측정, 그리고 측정 불가였다**

`/connect/logout` 은 **세션이 있어야** 재는 것이고 ① 이 죽어 세션이 생기지 않았다.
🔴 *"로그인만 재고 왕복 OK 라고 적지 않는다"* 의 **거울상**이다 — 로그인이 죽었다고
로그아웃을 «같이 죽었다»로 적지도 않는다.

## AC-3 — 무엇이 거짓인지 + 판정하지 못한 것 ✅

**무엇이 거짓인가**: 왕복이 **성립하지 않는다.** 끊기는 지점은 **앱 서버가 authorize
리다이렉트를 구성하는 단계**이고, 원인 문구는 `error=Configuration` 이다.

🔴 **그 문구는 원인이 아니라 «원인을 안 말해 주는 라벨» 이다.** 가설 **여섯 개를 죽였는데도**
좁혀지지 않았다 — 위 § 라이브 측정(①②⑥) · § 후보 제거(③④⑤). 여섯 번 틀렸다는 것이
이 티켓의 실질 산출물이고, 그래서 **일곱 번째 추론을 하지 않는다.**

**판정하지 못한 것** (후속이 이것을 «판정됨» 으로 상속하면 안 된다):

- 홉 ②③④⑤ 전부 — 특히 **③ 쿠키 축**. 이 ADR 이 두 방향으로 데였다던 그 축이다.
- 로그아웃(`/connect/logout`).
- **잰 앱은 `kanggle-fan` 하나다.** console·web-store 가 같은 라이브러리를 쓴다는 것은
  **가설이지 측정이 아니다**(AC-3 이 명시적으로 금지한 일반화).
- 빈 `clientSecret` 의 영향 — `assertConfig` 는 안 보지만 **토큰 교환(홉 ④)에서 죽는다.**
  🔵 **지금 증상과 다른 결함이지, 없는 결함이 아니다.**

## AC-4 — `ADR-MONO-067` § AC-0 ③ 에 기록 ✅

`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § AC-0 항목 3 을 **완료로 갱신**
했다(이 PR). 원문은 취소선으로 보존. 🔴 이 티켓은 **결정을 내리지 않는다** — D4 는
`TASK-MONO-576`.

---

# 🎯 잔여 작업의 **소유자를 명시한다** (일이 사라지지 않게)

🔴🔴 **두 티켓이 서로에게 떠넘기면 일이 사라진다 — 중복보다 공백이 조용하다.**
그래서 남은 두 조각을 각각 **한 곳에** 붙였다.

| 잔여 | 어디로 | 상태 |
|---|---|---|
| Vercel `kanggle-fan` Runtime Logs 의 `[auth][error]` 한 줄 (**2026-08-29T17:03Z 전후**) | `TASK-MONO-576` **AC-0 입력** (이 PR 에서 그 티켓에 명시) | 🙋 **소유자 조회** |
| 홉 ②③④⑤ + 로그아웃 실측 | 🔴 **아직 티켓이 없다.** D4(`576`)가 «어느 배선으로 갈지» 를 정하기 전에는 **무엇을 잴지가 안 정해진다** — `576` AC-2 가 그 조건을 적는다 | ⏸️ `576` 이후 |

🔵 **이 티켓을 여기서 닫는 이유**: AC-4 가 *"거짓이면 D4 의 후속 결정(`576`)의 입력이 된다.
이 티켓은 결정을 내리지 않는다"* 라고 못 박았다. 판정은 났고 기록도 갔다. 남은 것은
**이 티켓의 일이 아니라 `576` 의 입력**이고, 조회는 저장소가 할 수 없는 일이다.
🔴 **기동도 예산도 더 필요 없다** — 이 티켓을 열어 두는 것으로 얻는 것이 없다.
