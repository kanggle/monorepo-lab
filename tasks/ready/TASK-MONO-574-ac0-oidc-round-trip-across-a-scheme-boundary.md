# Task ID

TASK-MONO-574

# Title

`ADR-MONO-067` **AC-0 ③** — HTTPS 프런트 ↔ 평문 IdP 로그인이 **끝까지** 되는지 실측한다. 이 축이 안 풀리면 단계 3·4 는 못 간다.

# Status

ready

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

| 순서 | 할 일 | 누가 |
|---:|---|---|
| 1 | `hubwang.com` 를 Vercel 네임서버로 위임 | 🔴 소유자 |
| 2 | `hubwang.com` + `www`(301) → `kanggle-portfolio` · `fan.hubwang.com` → `kanggle-fan` | 🔴 소유자 |
| 3 | 🔴 **2 가 끝난 뒤에** `NEXTAUTH_URL=https://fan.hubwang.com` + `NEXTAUTH_SECRET` + OIDC 4종 | 🔴 소유자 |
| 4 | IdP 에 `https://fan.hubwang.com/api/auth/callback/iam` 재시드 | 저장소 |
| 5 | **AMI 재굽기 → 기동 1회** — 이 창에서 `TASK-MONO-581` 의 남은 항목 + `TASK-MONO-574` 를 **전부** | 승인 완료 |

🔴 **3 의 순서를 지켜야 한다** — 도메인을 붙이기 전에 `NEXTAUTH_URL` 을 넣으면
그 값이 빌드에 인라인돼 굳는다.

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
| 2 | **Vercel `kanggle-fan` 의 OIDC env** | ❌ **미설정 (실측)** | 🔴 **소유자 대시보드** |
| 3 | **IdP 에 Vercel 도메인 `redirect_uri` 등록** | 🟡 **티켓 생김** → [`TASK-BE-582`](../../projects/iam-platform/tasks/ready/TASK-BE-582-register-the-fan-vercel-callback-nobody-owned-this.md) | iam-platform |
| 4 | 데모 인스턴스 기동 | ⏸️ | 🔴 **사용자 승인** (예산 차감, 부팅 ~11분) |

🔴 **2·3 없이 기동하면 예산만 쓰고 아무것도 못 잰다.** 그래서 기동 전에 이 둘을 먼저 확인했다.

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
