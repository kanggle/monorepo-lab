# Task ID

TASK-MONO-611

# Title

🔴🔴 **`ADR-MONO-069` § R1 이 답을 받았다** — 그리고 그 답은 `OIDC_ISSUER_URL` 이 **Vercel 에서 아무도 안 관리한다**는 것이다: 한 프로젝트엔 **없고**, 다른 프로젝트엔 **죽은 IP 가 박혀** 있다.

# Status

done

# Owner

monorepo

# Task Tags

- adr
- oidc
- demo

---

# 🔎 어디서 왔나 — **소유자 몫이라고 적힌 조회를 저장소가 할 수 있었다**

`ADR-MONO-069` § R1 은 이 ADR 의 **최상위 유보**다: *"홉 ① 의 실제 사유가 밝혀지면 이 ADR
전체를 재검토한다."* 그리고 그 사유를 얻는 길을 **하나**로 적었다 — Vercel 대시보드의
Runtime Logs 한 줄(`2026-08-29T17:03Z` 전후).

🔴 **`TASK-MONO-610` 은 거기에 *"이 호스트의 Vercel CLI 는 인증돼 있지 않다"* 를 덧붙였다.
그 문장은 근거가 없었다.** 쳐 보니 `vercel whoami` → `khakiman`, `rc=0`. 계정 전체가 읽힌다.

🔵 **교훈은 「CLI 가 됐다」가 아니라 「안 된다고 적기 전에 한 번 쳐라」다.** 그 한 문장이
R1 을 «소유자 대기» 로 만들었고, R1 은 610 의 선행이었다.
[[env_classifier_false_block_map]]

---

# Goal

**§ R1 의 답을 기록하고, 그 답이 드러낸 «아무도 안 보는 표면» 을 주인 있는 일로 만든다.**

🔴 이 티켓은 `ADR-MONO-069` 의 결정을 **바꾸지 않는다** — 아래 § 판정이 그 이유다.

---

# 실측 (2026-09-01 UTC, `vercel` CLI 59.10.0 / 계정 `khakiman`)

## ① 🔴 원본 로그는 **소멸했다** — 그리고 그것을 «없었다» 와 갈랐다

| 잰 것 | 값 |
|---|---|
| 현 프로덕션 배포 생성 | `2026-08-30T10:47:41Z` (`kanggle-br7zpun1s`) |
| 그 배포의 **가장 오래된** 런타임 로그 | `2026-08-31T13:51:40Z` |
| 가장 최근 | `2026-09-01T11:16:05Z` |

⇒ 배포는 **08-30 생성인데 로그는 08-31 부터**다. 그 사이 트래픽이 있었음에도(`/login` 200 이
찍혀 있다) 없다 ⇒ **약 1일 롤링 보존**이다.

🔴 **「20행뿐」을 페이지 한계로 읽지 않으려고 대조군을 놓았다**: 08-29 의 Ready 배포
(`kanggle-e1pk3j0rs`)에 같은 조회를 걸면 **JSON 0행**이다. 페이지 한계였다면 그쪽에서
옛 로그가 나왔을 것이다. ⇒ **시간 기반 보존이 맞다.**

⇒ **목표 시각 `08-29T17:03Z` 는 조회 범위 밖이다. 그 줄은 영원히 못 읽는다.**

## ② 🔴 처음 나온 `[auth][error]` 두 줄은 **둘 다 답이 아니었다**

| 시각 | 줄 | 왜 답이 아닌가 |
|---|---|---|
| `08-31T15:47Z` | `UnknownAction` @ `HEAD /api/auth/callback/iam%27` | **스캐너 탐색**이다(URL 인코딩된 따옴표 = 주입 스캔). 내 계정의 실패가 아니다 |
| `09-01T11:23Z` | `UnknownAction: Unsupported action` @ `GET /api/auth/signin/iam` | 🔴 **내 프로브가 만든 것**이다 — Auth.js v5 에서 그 액션은 **POST + CSRF** 가 정식이고, `GET` 은 지원되지 않는다 |

🔵 둘 중 하나라도 R1 의 답으로 적었으면 **거짓을 ADR 에 랜딩**시켰을 것이다.
[[env_a_resolver_that_cannot_answer_returns_a_plausible_one]]
[[env_test_fixture_impossible_input_proves_nothing]]

## ③ 브라우저가 하는 대로 재현하니 — `fetch failed`

`GET /api/auth/csrf` → 토큰 → `POST /api/auth/signin/iam` (csrfToken + callbackUrl):

```
HTTP/1.1 302 Found
Location: https://fan.hubwang.com/login?error=Configuration
```

런타임 로그: **`[auth][error] TypeError: fetch failed`** — OIDC discovery 요청 자체가 실패했다.

🔴 **이것도 R1 의 답이 아니다.** 오늘은 데모가 꺼져 있으므로 issuer 미도달은 당연하다.
`TASK-MONO-610` 이 미리 적어 둔 *"지금 재시도해 얻는 로그는 다른 오류다"* 가 그대로 맞았다.

## ④ 🔴🔴 **그런데 R1 의 질문은 로그 말고 다른 계기로 잴 수 있었다**

R1 이 가른 네 칸 중 셋(«오타» · «누락 env» · «`AUTH_URL`»)은 전부 **env 목록에서 정적으로**
확인된다. 로그는 **한 계기였을 뿐 유일한 계기가 아니었다.**

**`kanggle-fan` 프로덕션 env — 네 개가 전부다:**

```
OIDC_CLIENT_SECRET   OIDC_CLIENT_ID   NEXTAUTH_SECRET   NEXTAUTH_URL      (전부 6d 전 = ~08-26)
```

**`OIDC_ISSUER_URL` 이 없다.** 그리고 코드는 그것을 읽는다 —
`projects/fan-platform/web/fan-platform-web/src/shared/config/env.ts:34`:

```ts
oidcIssuerUrl: process.env.OIDC_ISSUER_URL ?? 'http://iam.local',
```

⇒ 프로덕션에서 fan 은 **`http://iam.local`** 로 discovery 를 건다. Vercel 서버리스 함수가
그 이름을 해소할 방법은 **없다**. `fetch failed` 의 기전이 이것이다.

🔴🔴 **폴백이 결핍을 삼켰다.** `??` 가 없었으면 기동/요청 시점에 «`OIDC_ISSUER_URL` 이
없다» 가 떴을 것이다. 지금은 **아무 신호 없이** 런타임 `fetch failed` 로만 나타난다.
[[feedback_a_fallback_is_not_a_placeholder]]
[[feedback_declaration_files_are_not_the_runtime_state]]

## ⑤ 🔵 **형제가 이미 그 가설을 실험했고, 실패했다**

| 프로젝트 | `OIDC_ISSUER_URL` | 생성 |
|---|---|---|
| `kanggle-fan` | **없음** | — |
| `kanggle-store` | **`http://iam.3-38-176-240.sslip.io`** | 3d 전 (~08-29) |
| `kanggle-portfolio` | 없음(필요 없음 — `DEMO_API_BASE` 만) | 13d 전 |

⇒ store 는 **«그냥 env 를 넣으면 된다»** 를 실제로 해 봤다. 오늘 그 값은:

```
http://iam.3-38-176-240.sslip.io/.well-known/openid-configuration   →  code=000 (죽음)
(양성 대조군) https://store.hubwang.com/                             →  code=200 / 34,963 bytes
```

**평문 `http://`** 이고 **부팅마다 바뀌는 IP 가 호스트명에 박혀** 있어 **3일 만에 시체가 됐다.**

## ⑥ 🔵 «지워졌을 가능성» 도 닫았다

`vercel activity --project kanggle-fan` 이 덮는 범위는 **`08-29T12:19Z` ~ `09-01T11:26Z`**
(장애 시각 `17:03Z` **이전**부터 시작한다). 그 구간의 `env-variable` 이벤트는 **내 조회 2건뿐**
— 생성도 삭제도 **0건**이다.

⇒ **지금 없고, 08-29T12:19Z 이후 지워진 적도 없다 ⇒ `08-29T17:03Z` 에도 없었다.**
로그를 못 읽고도 그 시각의 상태가 확정된다.

---

# 🎯 판정 — R1 의 **네 칸 중 어디인가**

이 측정은 **두 칸을 동시에** 건드리고, 그 둘의 함의는 **정반대**다:

| R1 의 칸 | 이 측정이 맞나 | ADR 이 예고한 영향 |
|---|---|---|
| **issuer 낡음/도달 불가** | ✅ 맞다 — `http://iam.local` 은 Vercel 에서 도달 불가 | **`D`(그리고 `C`)의 값이 올라간다** |
| **사소한 배선 문제(누락 env)** | ✅ 문자 그대로는 맞다 — 변수 하나가 없다 | 🔴 **«구조 결정» 이 아니라 «설정 하나» 였던 것이 된다** |

**갈라야 한다. 판별자는 하나다 — 「그 칸에 넣을 수 있는 값이 존재하는가」.**

🔵 **⑤ 가 그 실험이다. 형제가 넣었고, 3일 만에 죽었다.** 넣을 수 있는 값은

- **평문 `http://`** 이라 HTTPS 경계를 넘고
- **IP 가 호스트명에 박혀** 부팅마다 무효가 된다

⇒ **«설정 하나» 가 아니다. 매 부팅마다 손으로 다시 해야 하고, 그러고도 스킴 경계가 남는다.**
그것이 정확히 `ADR-MONO-069` 가 존재하는 이유다.

## ⇒ **`ADR-MONO-069` 의 `C2` 결정은 유지된다. 약해진 게 아니라 강해졌다.**

🔴 **단, 이 티켓이 읽은 것은 «그 줄» 이 아니다.** 08-29 의 로그는 소멸했고, 여기 있는 것은
**오늘의 상태 + 오늘의 재현 + 활동 로그로 닫은 시간 구간**이다. 그 셋이 08-29 의 상태를
확정하지만, **그때의 스택 트레이스는 아무도 못 본다.** 그 차이를 지우지 마라.

## 🔴 그리고 R1 이 답을 받았어도 **홉 ① 이 다 설명된 것은 아니다**

`TASK-MONO-574` 는 가설 **여섯**을 죽였다. 이 티켓은 그중 «누락 env» 를 **참으로 확정**했지만,
`OIDC_ISSUER_URL` 을 채워도 남는 축(쿠키 스코프 = § R2)은 **여전히 미측정**이다.
🔵 즉 이것은 **원인 하나의 확정**이지 **왕복의 통과 증명이 아니다.**
[[feedback_if_the_symptom_survives_the_fix_it_was_not_the_cause]]

---

# Scope

## 포함

- 🔴 **fan 의 조용한 폴백**(`?? 'http://iam.local'`)을 **fail-fast 로** — AC-1.
- 🔴 **store 의 죽은 IP 핀** 처분 — AC-2.
- 🔵 **Vercel 런타임 로그 보존(약 1일)을 사실로 기록** — AC-3.

🔵 `ADR-MONO-069` § R1 · `TASK-MONO-610` 선행 · `TASK-MONO-585` 선행 3 의 갱신은
**이 티켓을 기안한 PR 이 이미 했다** — 아래 § ✅ 참조. AC 가 아니다.

## 제외

- 🔴 **`OIDC_ISSUER_URL` 에 실제 값을 넣는 것** → `TASK-MONO-610`. **넣을 값이 아직 없다** —
  그것을 만드는 게 610 이다.
- 🔴 **쿠키 축(§ R2)** → 610 의 V3.
- 🔴 **왕복 통과 증명** → 610 의 V1–V7.

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 잰다** (verify-then-act)

🔴 이 티켓의 실측은 **살아 있는 계정 상태**라 하루 만에도 바뀐다.

1. `vercel whoami` 가 여전히 `rc=0` 인가 — 아니면 이 티켓의 모든 실측을 다시 못 한다.
2. `kanggle-fan` 에 `OIDC_ISSUER_URL` 이 **여전히 없는가**. 🔵 생겼으면 ④ 는 닫힌 것이고
   **그 값이 무엇인지**가 새 질문이다.
3. `kanggle-store` 의 값이 **여전히 `3-38-176-240`** 인가. 바뀌었으면 누군가 부팅했다는 뜻이다.
4. 🔴 로그 보존이 여전히 **약 1일**인가 — Observability Plus 가 켜졌으면 08-29 는 여전히
   못 읽지만 **앞으로는** 읽을 수 있다(⇒ AC-3 의 값이 달라진다).

## AC-1 — 🔴 **폴백을 fail-fast 로 바꾼다**

`env.ts:34` 의 `?? 'http://iam.local'` 이 결핍을 삼킨다. 프로덕션에서 이 변수가 없으면
**요청이 아니라 그 이전에** 소리를 내야 한다.

🔴 **로컬/CI 를 깨지 마라** — `iam.local` 은 로컬 Traefik 에서 **진짜 유효한 값**이다.
술어는 «값이 없다» 가 아니라 **«프로덕션인데 값이 없다»** 여야 한다.

🔵 **형제를 먼저 grep 하라** — `oidcClientSecret: … ?? ''` 도 같은 모양이고,
`projects/platform-console` 의 `OIDC_REDIRECT_URI` 는 **기본값이 없다**(`z.string().url()`).
콘솔이 이미 옳은 모양을 갖고 있다. [[feedback_grep_the_siblings_before_fixing_it_yourself]]

## AC-2 — 🔴 **`store` 의 죽은 IP 를 처분한다** — 그리고 그 결정을 적는다

`http://iam.3-38-176-240.sslip.io` 는 **죽었고**(실측 `code=000`), 그 IP 는
**`TASK-MONO-606` 이 다루는 바로 그 죽은 IP** 다.

🔴🔴 **그런데 606 은 이 자리를 안 본다** — `TASK-MONO-606` 전문에 `vercel` **0건**이다.
606 은 «IdP DB 에 등록된 콜백» 을 보고, 이것은 **Vercel env** 다. 같은 시체가 **두 집**에
있는데 티켓은 한 집만 안다. [[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

선택지는 «지운다 / 610 이 줄 값으로 교체한다 / 그대로 둔다» 이고, **그대로 둔다면 왜인지
적어라** — 이유 없이 남은 죽은 값은 다음 사람이 «최신» 으로 읽는다.

## AC-3 — 🔵 **로그 보존을 사실로 기록한다**

이 저장소는 앞으로도 «Vercel 로그를 보면 된다» 를 처방으로 쓸 것이다. 그때마다
**약 1일** 이라는 사실이 필요하다.

- 실측치(배포 생성 `08-30T10:47Z` ↔ 최고참 로그 `08-31T13:51Z` = **27시간 공백**)를 적는다
- 🔴 **«없다» 를 «안 일어났다» 로 읽지 않는 법**도 같이 적는다 — 옛 배포에 같은 조회를
  걸어 0행을 확인하는 대조군

🔵 이 저장소의 어느 문서에도 이 값이 없다. 이번에 얻었으니 다음 사람이 다시 재지 않게 한다.


---

# ✅ 이 티켓을 기안한 PR 이 **같이 한 것** (AC 가 아니다 — 측정을 랜딩시키는 행위다)

이 셋은 «앞으로 할 일» 이 아니라 **위 실측을 제자리에 놓는 일**이다. AC 로 미뤄 두면
답이 나온 § R1 위에서 `TASK-MONO-610` 이 계속 «🙋 소유자 대기» 로 보이고, 그 대기는
**영원히 안 풀린다** — 기다리는 로그가 이미 소멸했기 때문이다. 증상이 «차단» 이 아니라
«대기» 로 보이고 **아무 가드도 안 문다.**

| # | 한 일 | 규율 |
|---|---|---|
| ① | `ADR-MONO-069` § R1 에 **답과 판정** 기록 + History 항목 | 🔴 § R1 의 **원문 표(네 칸)는 안 고쳤다** — 그 표가 이 판정의 근거다. 🔴 **«C2 가 확인됐다» 로 쓰지 않았다**: 확인된 것은 **«C2 를 뒤집을 사유가 아니었다»** 이고 그 둘은 다르다 |
| ② | `TASK-MONO-610` 선행 🙋 → ✅, 그리고 **`OIDC_ISSUER_URL` 배선을 610 의 AC 로** | 🔵 `TASK-BE-589` 가 **IdP 쪽 절반**(`redirect_uri` 등록)을 이미 했다. 이건 **앱 쪽 절반**이고, 안 넣으면 C2 가 stable HTTPS 이름을 만들어 놓고도 **아무도 그 값을 꽂지 않는다** |
| ③ | `TASK-MONO-585` 선행 3 갱신 | 585 는 *"프로젝트를 하나 더 만들어도 되는가 → ⏳ `TASK-MONO-575`"* 로 적고 있는데 **575 는 `done`** 이고 08-29 Pro 전환으로 배포 rate limit 이 안 문다(575 § CORRECTION 의 프로브 8/8 실측). **이미 답이 나온 행이 ⏳ 로 남아 있었다** |

---

---

# 구현 결과 (2026-09-01 UTC)

## ✅ AC-0 — 착수 시 다시 쟀다 (verify-then-act)

| # | 물음 | 오늘 값 | 티켓 기안 시점과 |
|---|---|---|---|
| 1 | `vercel whoami` | `khakiman`, **rc=0** | 같다 ✅ |
| 2 | `kanggle-fan` 에 `OIDC_ISSUER_URL` | 🔴 **여전히 없다** (4개 전부 `6d ago`) | 같다 ✅ |
| 3 | `kanggle-store` 의 값 | **`http://iam.3-38-176-240.sslip.io`**, `3d ago` | 같다 ✅ — **아무도 부팅하지 않았다** |
| 4 | 로그 보존 | 여전히 약 1일 — 오히려 **더 좁혔다**(§ AC-3) | Observability Plus 안 켜짐 ✅ |

🔵 **③ 의 값을 «목록» 이 아니라 «값» 으로 확인했다** — `env ls` 는 값을 암호화해 보여 주므로
`3d ago` 만으로는 «같은 값» 을 못 말한다. `env pull` 로 그 한 줄을 읽고 **파일을 지웠다**
(삭제 확인 포함). [[feedback_declaration_files_are_not_the_runtime_state]]

🔴 **죽음도 다시 쟀다** — 그리고 **음성 대조군을 붙였다**:

| 프로브 | 결과 |
|---|---|
| `http://iam.3-38-176-240.sslip.io/.well-known/openid-configuration` | 🔴 **timeout (curl 28)** |
| 양성 ① `https://store.hubwang.com/` | `200` / 34,940 B |
| 양성 ② `https://…execute-api.ap-northeast-2.amazonaws.com` (`DEMO_API_BASE`) | `404` / 23 B ⇒ **살아 있다** |
| 🔵 음성 `http://neverssl.com/` (평문 http, 반드시 살아 있음) | `200` / **3,961 B** |

음성 대조군이 필요한 이유: 이 호스트에서 캡티브 포털이 **평문 http 를 전부 먹어** 200 을
내던 전례가 있다. 오늘은 neverssl 이 **진짜 자기 페이지**(3,961 B, 포털의 794 B 가 아니다)를
냈다 ⇒ **timeout 은 포털 탓이 아니다.** [[env_captive_portal_answers_every_plain_http_request]]

---

## ✅ AC-1 — 폴백을 fail-fast 로. 🔴🔴 **그런데 순진한 판이 배포를 죽인다 — 실측했다**

### 🔴🔴 이 AC 에서 가장 중요한 것은 «던진다» 가 아니라 «어디서 던지느냐» 다

가장 먼저 떠오르는 판은 `env.ts` 의 object literal 안에서 던지는 것이다. **돌려 봤다.**

| 빌드 (`VERCEL=1`, `OIDC_ISSUER_URL` 없음, 같은 트리·같은 명령) | rc |
|---|---|
| **대조군** — 손대지 않은 코드 | **0** ✅ (12/12 정적 페이지) |
| **순진한 판** — object literal 안에서 `throw` | 🔴 **1** — `Failed to collect page data for /api/auth/[...nextauth]` |

기전: `middleware.ts` · `widgets/header/Header.tsx` · `app/api/auth/[...nextauth]/route.ts` 가
**전부** `shared/auth/auth.ts` 를 물고, 그것이 이 모듈을 문다. `next build` 의
**「Collecting page data」가 그 모듈을 실제로 평가한다.**

⇒ 순진한 fail-fast 는 «깨진 로그인» 이 아니라 **앱의 모든 향후 배포**를 죽인다. auth 와
무관한 변경까지. 그리고 값을 넣으려면 `TASK-MONO-610` 이 먼저 이름을 만들어야 하므로,
그 정지는 **며칠짜리**다.

🔴 **티켓의 Failure Scenario 는 «로컬·CI 를 깬다» 만 경고했다.** 실제로 더 큰 반경은
**배포 파이프라인**이었고, 그것은 **빌드를 돌려 봐야만** 보였다.
[[feedback_a_verifiable_mechanism_is_not_the_cause]]

### ⇒ 채택한 모양 — 진단은 모듈에서, **던지기는 요청 스코프에서**

| 층 | 무엇 | 왜 |
|---|---|---|
| `env.ts` 모듈 최상단 | `console.error` **한 줄** (`ON_VERCEL && !OIDC_ISSUER_URL`) | 콜드 스타트마다 **변수 이름**이 로그에 남는다. 아무것도 안 죽인다 |
| `env.ts` 내보내기 | `assertOidcIssuerConfigured()` | 부르는 쪽이 시점을 고른다 |
| `login/page.tsx` 서버 액션 | `signIn('iam')` **직전**에 호출 | 네트워크로 나가기 **직전**, 그리고 던져도 **그 로그인 시도 하나만** 잃는다 |

**술어는 «값이 없다» 가 아니라 «`VERCEL` 인데 값이 없다» 다.** 🔴 `NODE_ENV === 'production'`
이었다면 **데모 호스트가 깨진다** — 거기도 production 빌드이고 거기서는 `iam.local` 이
**맞는 값**이다. `VERCEL` 이 실제로 주입되는지는 문서를 믿지 않고 **이 프로젝트의 프로덕션
env 키 목록에서 확인**했다(`VERCEL` · `VERCEL_ENV` · `VERCEL_TARGET_ENV` 존재).

🔵 **안 덮은 것을 적는다**: Auth.js 자신의 엔드포인트를 **직접** 때리는
`POST /api/auth/signin/iam` 은 서버 액션을 거치지 않으므로 여전히 `fetch failed` 다.
catch-all 라우트를 감싸면 `/api/auth/session` 까지 함께 막혀 **모든 페이지**가 영향받는다 —
UI 가 절대 타지 않는 경로를 위해 치를 값이 아니다. **미검사이지 통과가 아니다.**

### 🔵 형제 grep — 그리고 **안 고친 것과 그 이유**

| 자리 | 모양 | 처분 |
|---|---|---|
| `fan` `oidcIssuerUrl` | `?? 'http://iam.local'` | ✅ 이 AC 가 고쳤다 |
| `fan` `oidcClientSecret` | `?? ''` | 🔵 **안 고쳤다 — 주석으로 명시.** `kanggle-fan` 프로덕션에 **값이 있다**(오늘 실측) ⇒ 살아 있는 구멍이 아니다. 게다가 정직한 로컬 기본값은 `''` 이 아니라 시드된 `fan-platform-dev` 이고, 그건 **로컬 동작을 바꾸는 다른 결정**이다 |
| `console-web` `OIDC_ISSUER_URL` | `z.string().url()` — 기본값 **없음** | 🔵 이미 옳은 모양. 콘솔은 로컬 compose 가 항상 준다 |
| `web-store` `OIDC_ISSUER_URL` | `?? 'http://iam.local'` | 🔴 **같은 모양이 남아 있다.** AC-1 의 범위는 fan 이고, web-store 는 `ADR-MONO-067` 로 이관 중이라 그 축이 610/604 에 걸려 있다 — **여기서 즉흥으로 안 바꾼다.** 대신 § AC-2 의 원장에 적었다 |

### 게이트 — 전부 자기 rc 로 확인 (파이프로 가리지 않았다)

| 게이트 | rc |
|---|---|
| `pnpm test` (fan 워크스페이스) | **0** — 24 파일 / **159 테스트** (신규 6 포함) |
| `npx tsc --noEmit` | **0** |
| `pnpm lint` | **0** |
| `pnpm build` (`VERCEL=1`, issuer 없음) | **0** |
| `pnpm build` (로컬, 둘 다 없음) | **0** |

### 🔴 bite — 초록이 공허하지 않다는 증명

주입 자체를 먼저 단언하고(치환됐는지 문자열로 확인) 두 칸을 껐다:

| 끈 것 | 결과 |
|---|---|
| `assertOidcIssuerConfigured()` 의 `throw` | 🔴 «변수 이름을 부르며 던진다» 빨강 |
| 모듈 최상단 `console.error` | 🔴 «진단을 한 번 낸다» 빨강 |
| (나머지 4칸 = 폴백·음성 대조군) | 🔵 초록 유지 — **올바른 비대칭** |

⇒ **2 failed / 4 passed.** 복원 후 6/6 초록. [[feedback_assert_the_injection_before_reading_the_bite]]

🔵 이 스위트가 지키는 **둘째 성질이 첫째보다 중요하다**: *「모듈 import 만으로는 절대 안
던진다」*. 위 표의 배포-사망 실측을 **테스트로 고정한 것**이다.

🔵 러너 확인: `.github/workflows/ci.yml` 의 `frontend-unit-tests` 가
`working-directory: projects/fan-platform` 에서 `pnpm test`(= `pnpm -r run test`)를 돌리고,
`fan` 필터가 `projects/fan-platform/**` 다 ⇒ **이 파일은 CI 에서 실제로 돈다.**
[[feedback_two_correct_exclusions_compose_into_a_hole]]

---

## ✅ AC-2 — store 의 죽은 IP 를 **지웠다** (소유자 지정)

🔵 **라이브 Vercel 설정이라 임의로 손대지 않고 소유자에게 세 선택지를 올렸고, «지운다» 를
받았다.** 실행·확인:

```
vercel env rm OIDC_ISSUER_URL production   →  Removed Environment Variable
before: OIDC_ISSUER_URL + DEMO_API_BASE (2개)
after :                    DEMO_API_BASE (1개)
```

🔴 **왜 «둔다» 보다 «지운다» 인가 — 취향이 아니다.** 방금 fan 에 넣은 가드의 술어는
**«값이 있나»** 다. **시체는 그 검사를 통과한다.** 부재는 탐지되고 시체는 안 된다 ⇒
죽은 값은 «자리 표시» 가 아니라 **가드를 무력화하는 값**이다.
🔵 오늘 동작 차이는 **0**(시체도 폴백도 똑같이 해소 실패)이고, env 변경은 **다음 배포부터**
적용되므로 살아 있는 배포는 안 건드렸다. 되돌리려면 기록된 값을 다시 넣으면 된다.

### 🔴🔴 그런데 진짜 결함은 «죽은 값» 이 아니라 **«원장이 비어 있었다»** 는 것이다

`web-store/VERCEL.md` 는 그 변수에 대해 이렇게 **지시하고 있었다**:

> 🔴 OIDC 축 전체(`OIDC_ISSUER_URL` · …)는 `ADR-MONO-067` 이 D4 로 떼어 낸 별도 결정이다.
> **여기서 즉흥으로 정하지 마라** … **빌드만 통과시키면 되는 경우라도, 넣은 값과 그 이유를
> 여기에 적는다.**

**08-29 에 값이 들어갔고, 그 문서엔 한 줄도 없었다.** 저장소 안에서는 발견할 방법이 없었고,
611 이 **형제 프로젝트의 env 목록을 세다가** 우연히 잡았다. ⇒ 그 절에 **원장 표**를 만들었다
(값 · 생성일 · 죽음 실측 · 처분 · 되돌리는 법 · 「IP 를 다시 박지 마라」).

🔵 `TASK-MONO-606` 은 이 자리를 못 본다(전문에 `vercel` 0건) — 606 은 `review/` 라 얼어 있어
**`## CORRECTION` 순수 추가**로 「같은 시체의 두 번째 집은 611 이 처리했다」를 남긴다.
[[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

---

## ✅ AC-3 — 로그 보존을 사실로 기록. **더 좁혔다: `22.7h ≤ T < 24.6h`**

티켓은 «27시간 공백» 하나를 갖고 있었다. **관측을 한 번 더 해서 구간으로 만들었다** —
경계를 만드는 방법이 이 절의 핵심이다.

| | 관측 A `09-01T11:26Z` | 관측 B `09-01T14:27Z` |
|---|---|---|
| 최고참 로그 | `08-31T13:51:40Z` | `08-31T15:47:23Z` |
| 창 (now − 최고참) | **21.6h** | **22.7h** |
| 배포 생성(`08-30T10:47:41Z`) 과의 공백 | 27h | **29h** ← **자랐다** |
| 행 수 | **20** | **25** |

- **하한** = B 에서 아직 살아 있는 줄 ⇒ `T ≥ 22.7h`
- **상한** = A 에서 살아 있다가 B 에서 사라진 줄(`13:51:40Z`) ⇒ `T < 24.6h`

🔵 **대조군 둘**로 «페이지 한계» 해석을 죽였다: ① 08-29 배포에 같은 조회 → **0행**
② 같은 배포의 행 수가 **20 → 25 로 늘었다**(고정 개수 페이지네이션이면 불가능).

🔴 **플랜에 딸린 값일 수 있다** — 측정은 **Pro** 에서 했다. 다른 티어에 인용 금지.

**집 = [`ADR-MONO-067`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md)
§ Context** (이 저장소의 Vercel 정본) + History 항목. `ADR-MONO-069` § R1 은 **숫자를
복사하지 않고 가리킨다** — 같은 숫자가 두 곳에 있으면 한쪽만 갱신된다.

---

## 🔴🔴 범위 밖 발견 — **내가 어제 철회한 거짓이 세 번째 절에서 살아 있었다**

`ADR-MONO-069` § Outstanding follow-ups **1번**이 오늘까지 이렇게 적혀 있었다:

> 🙋 **소유자 조회 (§ R1)** … 저장소가 대신 할 수 없다 — 이 호스트의 Vercel CLI 는
> 인증돼 있지 않다(2026-08-30 확인).

611 을 기안한 PR(#3570)은 § R1 과 § Decision **두 곳**을 고쳤고 **이 절을 놓쳤다.** 그래서
**같은 거짓이 하루 더**, 그것도 «남은 일» 목록이라 **가장 자주 읽히는 절**에 남아 있었다.
⇒ 취소선 + 철회 사유로 고치고, **맞았던 절반**(*"지금 재시도해 얻는 로그는 다른 오류다"*)은
남겼다 — 오늘 재현이 `fetch failed` 였으니 그 문장은 적중했다.

🔵 **그리고 이 발견을 만든 것은 가드가 아니라 «형제 절을 읽어라» 였다.** 아무 CI 체크도
ADR 본문의 철회를 세지 않는다. [[feedback_grep_the_siblings_before_fixing_it_yourself]]

---

## ✅ 라이브 검증 — **프로덕션에서 실제로 울렸다** (2026-09-01T15:07Z, 머지 직후)

🔴 AC-1 의 술어는 *"`VERCEL` 이 실제로 주입된다"* 에 걸려 있었고, 여기까지는
**env 키 목록**(선언)으로만 확인한 상태였다. 선언은 런타임이 아니다.
[[feedback_declaration_files_are_not_the_runtime_state]] ⇒ 머지가 만든 배포에
요청을 넣어 **로그를 직접 읽었다.**

| | |
|---|---|
| 새 프로덕션 배포 | `kanggle-mahtw3jl0` — 머지 **2분** 뒤 `Ready` (전체 40자 SHA `48609932…` 로 대조) |
| `GET https://fan.hubwang.com/` | `307` (정상 리다이렉트) |
| `GET https://fan.hubwang.com/login` | **`200` / 10,879 B** ⇒ 🔵 **사이트 무회귀** |
| 런타임 로그 | **2행, 둘 다 새 진단** |

```
2026-09-01T15:07:39Z | edge-middleware | /login
  [env] OIDC_ISSUER_URL is not set while running on Vercel. OIDC sign-in will
  fail: the fallback (http://iam.local) resolves nowhere from a serverless
  function. See TASK-MONO-610 for the public IdP name.
```

⇒ **어제까지 아무 신호도 없던 자리에 변수 이름이 찍힌다.** 그 전의 유일한 흔적은
변수 이름이 어디에도 없는 `[auth][error] TypeError: fetch failed` 였다.

🔵 **부수 실측 — 이 모듈은 Edge 런타임에서도 돈다.** 로그의 `source` 가
**`edge-middleware`** 다(Node 서버리스가 아니라). `process.env.VERCEL` 이 Edge 에서도
읽힌다는 뜻이고, 이건 예상이 아니라 **관측**이다.

🔴 **대가를 적는다: 진단이 요청당 한 줄 날 수 있다.** 요청 2건 → 로그 2행이었다
(Edge 아이솔레이트가 매번 새로 뜨면 모듈이 매번 평가된다). 단위 테스트의
`toHaveBeenCalledTimes(1)` 은 **모듈 로드당 1회**를 말하는 것이지 «프로세스당 1회» 가
아니다 — 그 둘을 같은 것으로 읽지 마라. 지금은 트래픽이 사실상 0 이고 `TASK-MONO-610`
이 값을 넣는 순간 조건 자체가 사라지므로 감수한다. 🔵 **트래픽이 붙는데도 값이 안
들어와 있으면 그때는 이 줄이 노이즈다** — 그 시점의 처방은 «한 번만 찍기» 가 아니라
**«값을 넣어라»** 다.

---

# 🙋 남는 것

**없다 — 이 티켓의 AC 4개는 전부 닫혔다.** 🔴 다만 **닫힌 것과 해결된 것을 구별한다**:

- `kanggle-fan` 의 `OIDC_ISSUER_URL` 은 **여전히 비어 있다.** AC-1 은 그 결핍을 **들리게**
  만든 것이지 **채운** 것이 아니다. 채우는 것은 `TASK-MONO-610` AC-4b 이고, 넣을 이름
  (`auth.hubwang.com`)이 아직 없다.
- `web-store` 의 같은 폴백도 그대로다(§ AC-1 표).

# Related Specs

- [`docs/adr/ADR-MONO-069-…md`](../../docs/adr/ADR-MONO-069-oidc-login-across-the-scheme-and-scope-boundary.md) § R1 · § Decision
- `projects/fan-platform/web/fan-platform-web/src/shared/config/env.ts` § `oidcIssuerUrl`
- `tasks/ready/TASK-MONO-610-…md` § 선행 · § AC-4
- `tasks/review/TASK-MONO-606-…md` — 같은 죽은 IP 의 **다른 집**

# Related Contracts

없음.

# Edge Cases

- **소유자가 그 사이 env 를 채운다** → AC-0 ②가 잡는다. 그 값이 무엇인지가 새 질문이 된다.
- **Observability Plus 가 켜진다** → 08-29 는 여전히 못 읽지만 AC-3 의 숫자가 낡는다.
- **`iam.local` 이 로컬에서 유효하다** → AC-1 의 술어가 «프로덕션» 을 포함해야 하는 이유.
- **활동 로그가 20건에서 잘린다** → ⑥ 의 결론은 `08-29T12:19Z` **이후**에만 유효하다.
  그 이전은 안 덮는다 — 그러나 «지금 없음 + 이후 삭제 없음» 만으로 17:03Z 가 확정된다.

# Failure Scenarios

- 🔴 **오늘의 `fetch failed` 를 08-29 의 사유로 적는다** → 거짓이 ADR 에 랜딩한다.
  ②·③ 이 그것을 막으려고 있다.
- 🔴 **«누락 env» 칸만 읽고 «구조 결정이 아니었다» 로 간다** → ⑤ 가 반증이다.
  형제가 그 가설을 실행했고 3일 만에 죽었다.
- 🔴 **AC-1 을 «값이 없으면 죽는다» 로 짜서 로컬·CI 를 깬다** → `iam.local` 은 로컬에서
  유효한 값이다.
