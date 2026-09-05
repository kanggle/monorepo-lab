# Task ID

TASK-MONO-624

# Title

**왕복은 첫 홉 다음부터 한 번도 안 쟀다** — `ADR-MONO-069` 가 ACCEPT 후 기안하라고 적어 둔 티켓

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- oidc
- verification
- demo-gated

---

# Goal

`ADR-MONO-069` § Verification 의 **V1~V7** 을 **실측으로 채운다.**
그 표는 `TASK-MONO-574` 가 «미측정» 으로 남긴 칸들이고, **이 티켓의 산출물은 그 칸을 처음
채우는 것**이다.

# 🔴 왜 이 티켓이 지금 생겼나 — **ADR 이 시켰는데 아무도 안 했다**

`ADR-MONO-069` § Outstanding follow-ups **항목 3**:

> ⏸️ **홉 ②③④⑤ + 로그아웃 실측** — `TASK-MONO-574` 가 *"D4 가 «어느 배선으로 갈지» 를
> 정하기 전에는 무엇을 잴지가 안 정해진다"* 며 이 ADR 뒤로 미룬 항목. § Verification 이
> 그 «무엇을» 을 안별로 적었다. **ACCEPT 후 티켓 기안.**

ADR 은 **2026-09-01 에 `C2` 로 ACCEPTED** 됐다. 그 조건이 충족된 지 나흘이 지났는데
**그 티켓이 없다** — 이 항목을 언급하는 파일은 전부 `done/` 이거나 `TASK-MONO-585` 자신이다.

🔴 그리고 585 는 **닫히기 직전**이다(AC 여섯 칸 전부 닫힘). 585 안에 적힌 검증 목록을
근거로 «누군가 재겠지» 라고 두면, 그 목록은 **`done/` 안으로 들어가 다시 안 읽힌다.**
`ADR-MONO-069` 자신이 이 실패 모드에 이름을 붙여 뒀다:

> *"두 티켓이 서로에게 떠넘기면 일이 사라진다 — 중복보다 공백이 조용하다."*

⇒ **이 티켓이 그 자리다.**

---

# 📏 착수 전에 이미 쟀다 (2026-09-05 UTC) — **V1 · V7 은 console 에서 통과다**

🔵 상속해도 되는 값이 아니라 **AC-0 이 다시 재야 할 값**이지만, 여기 적어 두는 이유는
«무엇이 이미 되는가» 를 알아야 남은 것의 크기가 보이기 때문이다.

## V1 (console) ✅ — 302 목적지가 IdP 다

```
GET https://console.hubwang.com/api/auth/login   →  307
Location: https://auth.hubwang.com/oauth2/authorize
  ?response_type=code
  &client_id=platform-console-web
  &redirect_uri=https%3A%2F%2Fconsole.hubwang.com%2Fapi%2Fauth%2Fcallback
  &scope=openid+profile+email+tenant.read
  &code_challenge_method=S256 &code_challenge=… &state=…
```

네 가지가 한 번에 확인됐다:

| 축 | 결과 |
|---|---|
| issuer 가 `C2` 의 고정 발급자인가 | ✅ `auth.hubwang.com` |
| `redirect_uri` 가 IdP 시드값과 일치하나 | ✅ `V0034`(`TASK-BE-589`)가 등록한 문자열과 **정확히** 일치 |
| Vercel 프로젝트에 OIDC env 가 주입됐나 | ✅ 안 됐으면 이 URL 이 조립될 수 없다 |
| PKCE | ✅ `S256` + `state` |

🔵 대조군: `/api/auth/__nope__` = **404** ⇒ 위 `307` 은 「모든 경로가 307」이 아니다.

## 🔴 그리고 `TASK-MONO-358` 축이 구조적으로 해소됐다

콘솔 로그인이 역사적으로 죽던 자리다(*"지금껏 동작한 콘솔 로그인은 `localhost:3000` 뿐"* —
평문 오리진에 `Secure` 쿠키를 주면 브라우저가 **저장조차 안 해서** 콜백이 `invalid_state`).

```
Set-Cookie: console_pkce_verifier=…; Path=/; Max-Age=600; Secure; HttpOnly; SameSite=lax
Set-Cookie: console_oauth_state=…;   Path=/; Max-Age=600; Secure; HttpOnly; SameSite=lax
→ cookie jar 에 둘 다 실제로 저장됨
```

🔴 **여기서 내 술어가 한 번 틀렸다** — 첫 파싱이 jar 를 «비어 있음» 으로 읽었다. curl 이
HttpOnly 쿠키를 `#HttpOnly_` **접두로** 쓰는데 `grep -v '^#'` 로 그 줄들을 지웠기 때문이다.
**없는 결함을 보고하기 직전이었다.** 재측정 시 같은 함정을 피하라.

## V7 (console) ✅ — 데모 `stopped` 일 때 502 가 아니라 정의된 화면

```
console.hubwang.com/          →  307 /dashboards/overview → 307 /login?redirect=… → 200
서빙 HTML 안에 demo-backend-notice 존재, 문구:
  "데모 서버가 꺼져 있어 로그인과 운영 데이터를 사용할 수 없습니다."
```

## 🔴 남은 것이 **왜** 남았나 — IdP 자체가 데모 스택 안에 있다

```
auth.hubwang.com/                                  503
auth.hubwang.com/.well-known/openid-configuration  503
```

⇒ **V2 · V3 · V4 · V5 · V6 는 데모가 켜져 있어야만 잰다.**

---

# ⏳ 착수 게이트 — **데모가 켜져 있을 때만 착수하라**

| # | 확인 | 아니면 |
|---|---|---|
| ① | `auth.hubwang.com/.well-known/openid-configuration` 이 **200** 인가 | **STOP.** 데모를 켜는 것은 **소유자 몫**이고 예산을 쓴다 |
| ② | 그 200 이 **이 티켓을 위해 켠 창**인가, 아니면 다른 일로 켠 창에 **얹는 것**인가 | 🔵 얹는 쪽이 항상 낫다 — 예산은 창 단위로 소모된다 |
| ③ | 창의 남은 시간이 **V2~V6 을 다 재기에 충분한가** | 🔴 부족하면 **V5 를 먼저 포기**하고 그 사실을 적어라. 반쯤 잰 V5 는 안 잰 것보다 나쁘다(§ Edge Cases ②) |

🔵 이 티켓은 **`ready/` 에서 기다린다.** 날짜가 아니라 **상태**에 묶여 있으므로
`TASK-MONO-587` 같은 날짜 게이트와 다르다 — 다음 기동 창이 열릴 때 함께 집어라.

---

# Scope

**In** — `ADR-MONO-069` § Verification 표의 V1~V7 을, **`C2` 배선 위에서**, **앱별로**.

**Out**

- 배선을 바꾸는 일. 이 티켓은 **재는 티켓**이다. 결함이 나오면 **별도 티켓**으로 올린다.
- `ADR-MONO-069` § Outstanding follow-ups 의 **항목 4**(PSL/LE 재측정 — `B` 를 골랐을 때만)
  와 **항목 5**(`ADR-MONO-067` § D2 의 «D 상신»). 둘 다 이 티켓의 축이 아니다.
- 데모를 **켜는 일** — 소유자 몫이고 예산을 쓴다.

# Acceptance Criteria

- [ ] **AC-0 (착수 시 재측정 — 상속 금지)** — 위 § 실측의 V1 · V7 을 **다시 잰다.**
      🔴 위 값은 **2026-09-05 의 배포 `6282303744` 위에서** 잰 것이다. 그 사이 재배포가 있었다면
      다른 산출물을 재게 된다 — `deployments?sha=` 로 **오늘의 프로덕션 배포를 먼저 확인**하라.
      🔴 그리고 **데모가 켜져 있으면 V7 은 잴 수 없다**(정의상 `stopped` 상태의 거동이다).
      ⇒ V7 은 **창을 열기 전에** 재고, 나머지는 창 안에서 재라. **순서가 있다.**

- [ ] **AC-1 — V2 (홉 ③)** 콜백에서 `state`/PKCE 쿠키가 살아 돌아오는가.
      통과 기준 = 콜백이 `invalid_state` 를 **안 낸다**.
      🔴 판정은 **최종 화면이 아니라 콜백 응답**으로 한다 — 로그인 후 화면이 떠도 그 경로가
      쿠키를 썼다는 증거는 아니다.

- [ ] **AC-2 — V3 (홉 ⑤)** 세션 쿠키의 `Secure`/`SameSite` **실제 값**.
      🔴 **추론이 아니라 응답 헤더를 찍는다.** 위 § 의 `console_pkce_verifier` 처럼 원문을 적어라.

- [ ] **AC-3 — V4 로그아웃** (`/connect/logout`).
      🔴 **로그인만 재고 «왕복 OK» 라고 적지 마라** (`TASK-MONO-574` AC-2 가 그 실패를 기록했다).

- [ ] **AC-4 — V5 부팅 2회 연속** 로그인, 사람 개입 없이.
      🔴🔴 **이것이 `ADR-MONO-069` 의 본체다.** 축 ②(범위)는 «한 번 됐다» 로 판정되지 않는다 —
      **부팅 두 번을 아무것도 안 고치고** 건너야 성립한다. 단일 표본을 성질로 승격시키지 않는다.
      🔵 두 번째 부팅이 **다른 창**이어도 된다. 그때 «첫 창에서 손댄 것이 없다» 를 함께 적어라.

- [ ] **AC-5 — V6 헤어핀** — 데모 **내부** 12개 소비자가 새 issuer 를 검증하는가.
      통과 기준 = **컨테이너 안에서** discovery + JWKS **200**.
      🔴 호스트에서 잰 200 은 이 축의 답이 아니다 — 헤어핀은 내부→외부→내부 경로다.

- [ ] **AC-6 — 앱별로 나눈다.** `ADR-MONO-069` § AC-3 이 그렇게 요구한다.
      🔴 `console-web` 은 **next-auth 를 안 쓴다**(자체 PKCE 구현) — fan 의 결과를 console 에
      상속하지 마라. ADR 이 console 을 *"이 축에서 미개척"* 이라고 부른다.

- [ ] **AC-7 — `TASK-MONO-574` 의 홉 표를 실제로 갱신**하거나, 574 가 `done/`(frozen) 이면
      **어디가 그 표의 새 집인지 적는다.** 🔴 «채웠다» 고만 적고 채운 값이 어디에도 없으면
      다음 사람이 또 미측정으로 읽는다.

# Related Specs

- [`docs/adr/ADR-MONO-069-oidc-login-across-the-scheme-and-scope-boundary.md`](../../docs/adr/ADR-MONO-069-oidc-login-across-the-scheme-and-scope-boundary.md)
  § Verification (V1~V7 정본) · § Outstanding follow-ups 항목 3 (이 티켓의 출처)
- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) § D4

# Related Contracts

없음 — 재는 티켓이다. 와이어 형태를 안 바꾼다.

# Related Tasks

- `TASK-MONO-574` — 홉 표의 원본. ②③④⑤ 를 «미측정» 으로 남겼다 (`done/`)
- `TASK-MONO-585` — `redirect_uri` 시드(선행 4)를 들었고 `TASK-BE-589`/`V0034` 로 닫았다.
  그 결과가 위 V1 의 `redirect_uri` 일치로 **라이브에서 확인**됐다
- `TASK-BE-589` — `V0034` (`https://console.hubwang.com/api/auth/callback` 등록)
- `TASK-MONO-610` — IdP 를 `auth.hubwang.com` 뒤로 옮긴 배선 (`C2` 의 구현)

# Edge Cases

① **데모를 켠 창이 이 티켓 전용이 아닐 수 있다.** 그러면 다른 작업과 창을 나눠 쓴다 —
   🔴 그때 **먼저 V2~V4 를 잡아라**(짧다). V5 는 창 두 개가 필요하므로 마지막이다.

② 🔴 **반쯤 잰 V5 는 안 잰 V5 보다 나쁘다.** «첫 부팅은 됐다» 를 V5 통과로 적으면 그것이
   정확히 이 ADR 이 금지한 «단일 표본의 성질 승격» 이다. 못 재면 **⚪ 로 남기고 왜인지 적어라.**

③ **`auth.hubwang.com` 이 200 이라고 IdP 가 준비된 것은 아니다.** `C2` 는 Vercel 포워더가
   앞에 있으므로, 포워더가 살아 있고 뒤가 안 떴을 때의 응답을 구별해야 한다.
   🔵 판정은 `/.well-known/openid-configuration` 의 **본문**으로 — 200 만 보지 마라
   ([[env_gateway_401_is_not_backend_readiness]] 와 같은 부류).

④ **쿠키를 curl 로 잴 때 `#HttpOnly_` 접두를 지우지 마라** — 위 § 에서 실제로 밟았다.

⑤ **로그인에는 자격증명이 필요하다.** 시드된 운영자 계정을 쓰고, **어느 계정으로 쟀는지**
   적어라. 계정마다 테넌트/권한이 달라 «로그인은 됐는데 화면이 비었다» 가 나온다.

# Failure Scenarios

① **V2 가 `invalid_state` 로 실패** → 🔴 이 티켓은 **고치지 않는다.** 쿠키 원문 + 콜백 응답을
   그대로 적고 **별도 티켓**으로 올린다. `TASK-MONO-358` 이 같은 증상의 다른 원인을 갖고 있으니
   그 티켓을 먼저 읽어라(평문 오리진 축은 이제 해당 없음 — HTTPS 다).

② **V5 의 두 번째 부팅에서 사람 손이 필요했다** → 그것이 **발견**이다. 무엇을 손댔는지
   적고 `ADR-MONO-069` 의 *"부팅마다 사람이 하는 일: 없다"* 주장과 대조하라 — ADR 의 표가
   틀렸다는 뜻이 된다.

③ **창이 닫혀 못 끝냈다** → ⚪ 로 남기고 **어느 V 가 안 쟀는지 칸 단위로** 적는다.
   🔴 «대부분 통과» 같은 요약으로 덮지 마라.

# Definition of Done

- [ ] V1~V7 각 칸이 **✅ 또는 ⚪(+사유)** 로 채워졌다 — 빈칸 없음
- [ ] 앱별로 나뉘어 있다 (AC-6)
- [ ] 갱신된 홉 표의 **집이 어디인지** 적혀 있다 (AC-7)
- [ ] 결함이 나왔다면 **별도 티켓**으로 기안됐다 (이 티켓이 고치지 않는다)

---

분석=Opus 5 / 구현 권장=Opus (라이브 측정 + 대조군 설계 + ⚪ 판정이 섞여 있다.
🔴 특히 V5 는 «단일 표본을 성질로 승격시키지 않는다» 를 지켜야 하는 자리다).
