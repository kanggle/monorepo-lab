# Task ID

TASK-MONO-610

# Title

`ADR-MONO-069` **(C2) 구현** — IdP 를 `auth.hubwang.com` 하나 뒤에 두고 Vercel 이 TLS 를 끝낸다. 🔴 배선 전에 **C2 를 탈락시킬 수 있는 두 가지**부터 잰다.

# Status

in-progress

# Owner

monorepo

# Task Tags

- adr-followup
- vercel
- oidc
- demo

---

# 🔎 어디서 왔나

`ADR-MONO-069` 가 **2026-09-01 에 `C2` 로 ACCEPTED** 됐다(소유자 정확형 지정,
rider = *"apex 쿠키는 호스트 한정"*). 그 ADR 의 § Decision § 구현 이 이 티켓을 지명한다.

🔴 **이 티켓은 «로그인을 고치는 것»이 아니다.** ADR § Consequences 가 못 박았다 —
*"어느 안도 «지금 당장 로그인이 된다» 를 보장하지 않는다. 홉 ① 의 실제 사유가 아직 없기 때문이다."*
이 티켓이 배선하는 것은 **구조**이고, 그 구조가 참인지는 § Verification 이 잰다.

---

# ✅ 선행 — **해제됐다 (2026-09-01, `TASK-MONO-611`)**

> 🔴 아래 원문은 **보존**한다 — 그때의 판단 근거이고, 그 중 한 문장이 틀렸다는 것이
> 이 해제의 절반이기 때문이다.

**답: fan 에 `OIDC_ISSUER_URL` 이 없었다.** 코드가 `?? 'http://iam.local'` 로 폴백하고,
Vercel 서버리스 함수는 그 이름을 해소할 수 없다. `vercel activity` 가 장애 시각(`17:03Z`)
**이전**인 `08-29T12:19Z` 부터 덮는데 env 생성·삭제가 **0건**이므로 **그때도 없었다.**

🔴 **그 로그 줄 자체는 못 읽었다 — 소멸했다**(Vercel 런타임 로그 보존 ≈ **1일**, 실측).
읽은 것은 **오늘의 env 상태 + 오늘의 재현 + 활동 로그로 닫은 시간 구간**이다.

🔴 **그런데 이것은 «사소한 배선 문제» 칸으로 안 떨어진다.** 형제 `kanggle-store` 가 그
변수를 **실제로 넣었고**(`http://iam.3-38-176-240.sslip.io`, 08-29) 오늘 **죽었다**
(`code=000`) — 평문 HTTP + 부팅마다 바뀌는 IP. ⇒ **`C2` 는 유지된다.**

🔴🔴 **그리고 아래 원문의 마지막 문장 — *"이 호스트의 Vercel CLI 는 인증돼 있지 않다"* —
는 근거 없는 단정이었다.** `vercel whoami` → `khakiman`, `rc=0`. 그 한 문장 때문에 이
선행이 **소유자 대기**로 남아 있었고, 기다리는 로그는 이미 소멸해 있었다. 증상이
«차단» 이 아니라 **«대기»** 로 보이고 **아무 가드도 안 문다.**

<details>
<summary>원문 (2026-08-30 기안 시점) — 보존</summary>

## ⏸️ 선행 — 🙋 **소유자 조회 하나 (ADR § R1)**

> Vercel → `kanggle-fan` → Runtime Logs → `/api/auth/signin/iam` 의 `[auth][error]` 한 줄.
> 시각 **2026-08-29T17:03Z 전후**.

🔴 **이것은 착수 차단이 아니다** — C2 배선은 그 줄 없이도 진행할 수 있다. 다만 그 줄이
*"사소한 배선 문제(오타·누락 env·`AUTH_URL`)"* 로 밝혀지면 **ADR § R1 넷째 칸대로 이 결정
자체를 재검토**하므로, 이 티켓의 코드를 쓰기 전에 **한 번은 물어보는 것이 가장 싸다**.
🔴 지금 재시도해 얻는 로그는 **다른 오류**다(데모 정지 → IP 반납 ⇒ 연결 실패). 저장소가 대신
할 수 없다 — 이 호스트의 Vercel CLI 는 인증돼 있지 않다.

</details>

---

# ✅ 그 선행이 답을 받았다 (2026-09-01) — **`ADR-MONO-068 § D6 ACCEPTED — B2`**

소유자 정확형 지정. 🔵 **그러나 「해제」가 아니다** — 블로커의 이름이 «결정이 없다» 에서
**«`B2` 가 아직 구현되지 않았다»** 로 바뀌었다.

**새 순서** (`ADR-MONO-068 § D6.1 ④`):

| | 무엇 | 누구 |
|---|---|---|
| 1 | **`TASK-MONO-613`** — 가드 모집단의 구멍 | 저장소 |
| 2 | **`TASK-MONO-614`** — `B2` 구현(패키지 + 소비자 둘 + **가드 교체**, 원자적 한 PR) | 저장소 |
| 3 | **이 티켓의 포워더** — 패키지를 import 한다 | 저장소 |
| 4 | 🙋 Vercel 프로젝트 + `auth.hubwang.com` 도메인 부착 | 소유자 |
| 5 | 🙋 재굽기 + 기동 창 — V1–V8 | 소유자 |

🔴 **4 를 3 보다 먼저 해도 붙일 데가 없다**(아래 원문 그대로 유효).

---

# 🔴🔴 새 선행 (2026-09-01) — **C2 의 첫 단계는 DNS 가 아니라 `ADR-MONO-068` 승격 결정이다**

이 티켓의 § Scope 는 C2 의 포워더에 대해 *"새로 발명할 것이 없다 — 이 저장소가 이미 **두 번**
구현한 모양이다"* 라고 적었다. **그 문장이 정확히 이 선행을 만든다.**

## 왜

`ADR-MONO-068` § D5 의 결정은 `C`(사본 허용 + 정규화 동일성 가드)이고, § D5.4 가 그 근거를
*"**세 번째 사본이 오면 그때 `B` 로 승격**하고, 그 시점엔 정규화 실측이 3표본이 되어 있다"*
로 적었다. C2 의 포워더는 그날 IP 를 `resolveDemoBackend()` 로 얻으므로 **세 번째 해석기**다.

**가드가 그 숫자를 직접 말한다** (2026-09-01 실측):

```
[resolver-copies] OK — 해석기를 가진 앱 2 개 (승격 3) · 정규화 비교 1 쌍 · …   rc=0
```

`scripts/check-demo-resolver-copies.sh` 의 칸 (1b): *"해석기를 가진 앱이 **3개 이상**이면
RED"*, 실패 문구는 *"🔴 3개 = B 로 승격 — D5.4 의 전제가 소진"*.

## 🔴 그리고 가드를 완화하는 길은 **미리 막혀 있다**

§ D3 이 *"트리거가 발화하면 가드를 **완화하지 마라**"* 라고 명시했고, § D5.4 표는 `A`(상한을
1→2 로 올리기)를 **바로 그 이유로** 기각했다. ⇒ **상한을 3으로 올려서 통과시키는 것은 금지다.**

## ⇒ 순서가 바뀐다

| | 내가 앞서 올린 순서 | **실제 순서** |
|---|---|---|
| 1 | 🙋 `auth.hubwang.com` DNS + Vercel 프로젝트 | 🙋 **`ADR-MONO-068` 승격 결정** (소유자 정확형 지정) |
| 2 | 저장소가 포워더 구현 | 저장소가 포워더 구현 (**결정 뒤**) |
| 3 | — | 🙋 Vercel 프로젝트 생성 + 도메인 부착 |

🔴 **DNS 를 먼저 해도 붙일 데가 없다** — Vercel 프로젝트는 **빌드되는 Root Directory** 를
가리켜야 하고, 그 앱이 저장소에 아직 없다.

🔵 `B`(루트 워크스페이스 / 공유 패키지)의 비용은 § D5.4 가 이미 실측해 뒀다 — lockfile
**5개 / 20,580줄** 병합 · `pnpm-workspace.yaml` 은 **2개뿐** · **세 Vercel 프로젝트의 install
단계**(`TASK-MONO-562` 가 **261자 하나로 배포를 0초에 죽인** 자리). **그래서 이것이 «그냥
하면 되는 일» 이 아니고 결정이다.**

🔴 **나는 여기서 안을 고르지 않는다.** `ADR-MONO-069` 가 `C2` 를 받을 때와 같은 게이트다 —
**소유자 정확형 지정**만이 ACCEPT 다.

## 🔴🔴 그리고 «우회로» 가 하나 열려 있다 — 그것 자체가 결함이다

가드의 모집단은 **`^projects/[^/]+/(apps|web)/[^/]+`** 로 못박혀 있다. 포워더를
`infra/demo/…` 같은 곳에 두면 **가드가 그 앱을 못 보고, 승격 트리거는 조용히 안 문다.**

🔵 오늘 그 구멍은 **잠재적**이다 — 저장소의 `next.config.*` 는 **3개이고 전부 `projects/`
안**이다(실측). 그러나 이 티켓이 만들려는 것이 정확히 **첫 번째 예외**다.

⇒ **`TASK-MONO-613` 으로 분리했다.** 🔴 **디렉터리를 골라 트리거를 피하는 것은 금지다** —
그건 결정을 우회하는 것이지 만족시키는 것이 아니다.

---

# Goal

`https://auth.hubwang.com` 이 IdP 의 **오리진이자 `issuer`** 가 되게 하고, 그 뒤에서 Vercel
프로젝트 하나가 그날의 `http://iam.<ip>.sslip.io` 로 **서버측 포워딩**한다.
브라우저는 평문을 **한 번도 보지 않는다**.

---

# Scope

**In:**

- Vercel 프로젝트 1개 — `auth.hubwang.com` catch-all route handler. 그날 IP 는
  `ADR-MONO-068` 의 해석기(`resolveDemoBackend()`)로 얻는다. 🔵 **새로 발명할 것이 없다** —
  이 저장소가 이미 두 번 구현한 모양이다.
- `infra/demo/demo.env` 의 `IAM_PUBLIC_URL` → 고정 이름
- IdP 가 광고하는 `issuer` 와 그것을 검증하는 **모든 소비자**
- `redirect_uri` / post-logout 시드 중 **issuer 변경으로 깨지는 것**
- 🔴 넷째 Vercel 프로젝트가 만드는 배선 부채(아래 § 넷째 프로젝트)

**Out:**

- **홉 ① 버그 자체** — ADR § R1. 이 티켓은 구조만 놓는다.
- `console-web` 의 `redirect_uri` 시드 — **`TASK-MONO-585`** 가 들고 있다(공통 선행이며
  C2 를 골라도 사라지지 않는다). 🔴 **여기서 다시 하지 마라, 그러면 같은 사실이 두 곳에 생긴다.**
- C1(EC2/Traefik 종단) — C2 가 탈락할 때의 **다음 지정 대상**이지 이 티켓의 범위가 아니다.

---

# Acceptance Criteria

## AC-0 — 🔴🔴 **배선하기 전에 C2 를 죽일 수 있는 둘을 먼저 잰다** (verify-then-act)

`ADR-MONO-069` § 선택지 C 의 미측정 **2·3** 이고, C-ii 가 여기 걸려 있다:

| # | 무엇 | 거짓이면 |
|---|---|---|
| 2 | **`Host` 헤더와 `Location` 재작성** — IdP 가 자기 요청의 `Host` 로 절대 URL 을 만들면, 포워딩이 그것을 **평문 sslip 이름으로 되돌려 놓는다**. 그러면 브라우저가 다시 평문으로 튄다 | **C2 탈락** |
| 3 | **`Set-Cookie` 의 `Domain`** — IdP 세션 쿠키가 `auth.hubwang.com` 에 심기는가 | **C2 탈락** |

🔴 **둘 다 «응답 헤더를 찍어서» 판정한다 — 추론 금지.** 최소 재현은 데모 IdP 하나만 띄우고
`Host: auth.hubwang.com` 으로 authorize 를 부르는 것으로 충분하다(전체 스택 기동 불요).

🔴 **탈락이면 STOP 하고 코드를 쓰지 마라.** ADR § Decision 이 명시한다 —
*"그때 이 § Decision 은 **C1 로 다시 지정받아야 한다**(에이전트가 대신 바꾸지 않는다)."*
측정값을 이 티켓에 적고 **소유자에게 재지정을 요청**한다.

🔵 **통과여도 «C2 가 된다» 가 아니다** — 두 축이 안 죽었다는 뜻일 뿐이다.

## ✅ AC-0 실측 (2026-09-01) — **두 축 다 안 죽었다. `C2` 는 살아 있다**

로컬 Docker 의 `iam-auth-service-1` 하나로 쟀다(AC-0 이 허용한 «전체 스택 기동 불요»).
**AWS 데모 기동 0회, 예산 0원.**

### 🔴 먼저 — 첫 두 시도는 **공허했다**

`/oauth2/authorize` 가 **`401` · 본문 0바이트**를 냈다. 쿠키를 들고 다시 쳐도 같았다.
🔵 **기전을 찾았다: 콘텐츠 협상이다.** Spring Security 의 엔트리포인트가 `Accept` 로 갈린다 —
`Accept: text/html` 을 붙이자 **`302` + `Location`** 이 나왔다.

⇒ **`401` 은 «막혔다» 가 아니라 «물어보지도 못했다» 였고, 그 상태에서 `Location` 이 없다고
«재작성 안 한다» 로 읽었으면 판정 전체가 거짓이 됐다.**
[[env_gateway_401_is_not_backend_readiness]] [[env_empty_detector_output_is_not_absence]]

### 축 2 — `Host` 와 `Location` 재작성 · **✅ 통과**

**(2-a) 절대 URL 은 `Host` 를 따라온다** — 설정된 `iam.local` 로 되돌아가지 **않는다**:

| 보낸 `Host` | 받은 `Location` |
|---|---|
| `localhost:18081` | `http://localhost:18081/login` |
| **`auth.hubwang.com`** | **`http://auth.hubwang.com/login`** |

**(2-b) 그런데 스킴이 평문이었다** — 그리고 그것이 이 축의 진짜 실패 모드다.
Vercel 이 TLS 를 끝내고 HTTP 로 포워딩하면 IdP 는 평문으로 되돌려 보낸다.

**(2-c) `X-Forwarded-Proto` 를 존중하는가 — 존중한다:**

| 조건 | `Location` |
|---|---|
| `X-Forwarded-Proto: https` | **`https://auth.hubwang.com/login`** ✅ |
| + `X-Forwarded-Host` + `X-Forwarded-Port: 443` | 동일 ✅ |
| 🔵 **음성 대조군** — `X-Forwarded-Proto` 없음 | `http://auth.hubwang.com/login` |

🔵🔵 **대조군이 반대로 움직였다.** 「https 를 넣으면 https 가 나온다」만 봤다면
«원래 https 였다» 와 구별되지 않는다. 빼면 http 로 되돌아가므로 **그 헤더가 원인**이다.
⇒ `server.forward-headers-strategy` 가 이미 켜져 있다.

🔴🔴 **그런데 그것은 «앱의 성질» 이 아니라 «오버레이의 조건» 이다.** 확인해 보니
`auth-service/application.yml` 에는 `forward-headers-strategy` 가 **없고**, 내가 잰
컨테이너는 그 값을 **env 로** 받고 있었다: `SERVER_FORWARD_HEADERS_STRATEGY=FRAMEWORK`.

⇒ **축 2 의 통과는 그 env 가 붙어 있는 동안만 참이다.** 단일 측정을 성질로 승격시키면
안 되는 자리다. 🔵 **다행히 가드가 이미 있다** — `verify-demo-wrapper.sh` 의 (l) 이
*"iam-oidc 라우터와 SERVER_FORWARD_HEADERS_STRATEGY 는 항상 함께 있어야 하는 한 쌍"*
이라며 한쪽만 있으면 빨간불을 낸다. 그 가드가 이 축을 지킨다.
[[feedback_local_proves_behaviour_not_performance]]

### 축 3 — `Set-Cookie` 의 `Domain` · **✅ 통과, 그리고 기본값이 이미 옳다**

```
Set-Cookie: JSESSIONID=...; Path=/; HttpOnly
```

두 `Host` 모두 **`Domain` 속성이 없다**. Domain 없는 쿠키는 **호스트 한정**이므로
`auth.hubwang.com` 에만 심긴다 — 소유자 rider(*"apex 쿠키는 호스트 한정"*)가 요구한
바로 그 동작이고, **손대지 않아도 그렇다**. ⇒ **AC-4 의 배선은 «바꾸는 일» 이 아니라
«유지되는지 지키는 일»** 이다.

### ⇒ **판정: `C2` 는 탈락하지 않는다. `C1` 재지정은 필요 없다**

🔵 그러나 AC-0 자신이 미리 적어 둔 대로 — **«통과여도 C2 가 된다» 가 아니다. 두 축이 안
죽었다는 뜻일 뿐이다.**

### 🔴 이 측정이 **덮지 않는 것** — 그리고 하나는 새로 찾은 위험이다

| # | 무엇 | 어디로 |
|---|---|---|
| 1 | 🔴🔴 **`Secure` 도 `SameSite` 도 안 붙는다** — `X-Forwarded-Proto: https` 를 줘도 `JSESSIONID` 는 `Path=/; HttpOnly` 뿐이다. HTTPS 뒤에서 **비-Secure 세션 쿠키**는 실재 결함이다 | **V3** (AC-3). 🔵 AC-0 의 축이 아니라 여기서 처음 적는다 |
| 2 | 🔴 **`OIDC_ISSUER_URL` 을 안 바꾸면 discovery 가 `iam.local` 을 계속 광고한다** — 실측: 컨테이너 env 가 `http://iam.local` 이고 discovery 의 `issuer`/`authorization_endpoint` 가 **두 `Host` 에서 바이트 동일**하다(이 값만은 `Host` 가 아니라 **설정**에서 온다). 리다이렉트는 `auth.hubwang.com` 인데 `iss` 는 `iam.local` 이면 RP 가 거절한다 | **AC-1** — 이 실측이 AC-1 의 필요성을 확인한다 |
| 3 | 🔴 **돌린 이미지가 낡았다** — Flyway 가 *"latest available migration (**0032**)"* 라고 찍는다. `V0033`(08-26)·`V0034`(09-01) 이 없는 이미지다 | 🔵 이 두 축은 **프레임워크 동작**이라 마이그레이션 무관이다. 다만 **적어 둔다** — 이 컨테이너로 «로그인이 된다» 를 재면 그건 거짓이다 |
| 4 | 실제 왕복 | **V1–V7** — 기동 창 |

🔵 **3번이 중요하다**: AC-0 은 «두 축이 안 죽었나» 만 물었고 그건 낡은 이미지로도 답할 수
있다. 그러나 **V1–V7 은 그럴 수 없다** — 그때는 `V0034` 를 담은 이미지여야 한다.

---

## AC-1 — 🔴 **`issuer` 의 소비자를 세고, 전수를 옮긴다**

ADR § 선택지 C 가 *"issuer 를 검증하는 모든 것이 같이 움직인다"* 라고 적고
`infra/demo/demo.env:58` 의 `IAM_PUBLIC_URL` **한 변수**를 지목했다.

- 🔴 **그 한 줄을 믿지 말고 다시 세라.** 선언 파일 grep 은 런타임 모집단이 아니다 —
  compose override · 앱 env · 시드 SQL · 하드코딩을 각각 센다.
- 🔴 **모집단 ≥ 1 을 단언**하라. 0 이 나오면 그것은 «옮길 게 없다» 가 아니라 **술어가 틀린 것**이다.
- 옮긴 뒤 **남은 평문 issuer 참조가 0건**임을 같은 술어로 확인한다(before/after 를 둘 다 적어라).

## ✅ AC-1 실측 (2026-09-01) — **ADR 의 «한 곳에 모여 있다» 는 12/17 만 맞다**

### 🔴 먼저 — **내 술어가 세 번 틀렸다**. 그 셋이 다 모집단을 줄이는 쪽이었다

| # | 결함 | 증상 |
|---|---|---|
| 1 | 중첩 기본값 `${A:${B:…}}` 에서 **바깥 변수만** 봤다 | 「미커버 23건」이라는 **거짓 경보** |
| 2 | 정규식이 `iam\.\$\{DEMO_DOMAIN\}` — 닫는 `}` 를 요구했다 | 실제 문자열은 `iam.${DEMO_DOMAIN:-local}` ⇒ **override 의 issuer 를 통째로 못 봤다** |
| 3 | 리터럴을 찾으려고 `${VAR:-…}` 을 **먼저 지웠다** | 그 제거가 `http://iam.${DEMO_DOMAIN:-local}` 의 **도메인 부분을 파괴** ⇒ 「공개 이름 리터럴 0건」 |

🔴🔴 **셋 다 «0건» 또는 «작은 수» 로 실패했다** — 즉 **찾던 결론과 같은 모양**이었다.
AC-1 이 *"0 이 나오면 술어가 틀린 것"* 이라고 미리 적어 둔 것이 정확히 이것을 잡았다.
[[feedback_my_verification_predicate_is_the_likeliest_defect]]
[[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]]

### 모집단 — **양성 대조군을 먼저 찍었다**

```
주석 제거 후 `iam.local` / `iam.${DEMO_DOMAIN` 를 담은 compose 줄 = 60 건
  🔵 demo.env 가 덮는 `${VAR:-…}` 안 = 51 건
  🔴 그 밖                           =  9 건
```

🔵 **51 이 «필터 전 발견» 이자 «커버됨» 이다** — 필터 전이 0이었다면 정규식이 깨진 것이고,
그 경우 「9건」은 결과가 아니다. 그래서 두 수를 **둘 다** 적는다.

| 모집단 | 개수 | 판정 |
|---|---:|---|
| **P1** `demo.env` 에서 `${IAM_PUBLIC_URL}` 파생 | **12** | ✅ ADR 의 숫자가 맞다. 공짜로 따라온다 |
| **P2** compose 기본값 중 demo.env 가 **안 주는** 변수 | **0** (발견 51 / 커버 51) | ✅ compose 층은 완전히 덮인다 |
| **P3-a** 🔴 **issuer 를 스스로 재조립**하는 리터럴 | **2** | 🔴 ADR 이 못 본 것 — 아래 |
| **P3-b** Traefik 라우터 규칙 · 네트워크 alias (**서빙 측**) | **3** | ⏭️ AC-2 |
| **P3-c** `kafka.iam.…` · `grafana.iam.…` | **4** | 🔵 issuer 무관(관측 도구 호스트명) |
| **P4** 앱 `application.yml` 기본값 | **4** (발견 66 / 커버 62) | 🔴 아래 — **demo.env 로는 못 고친다** |

### 🔴 P3-a — **`IAM_PUBLIC_URL` 을 거치지 않고 이름을 재조립하는 두 줄**

```
infra/demo/iam-traefik.override.yml:48    OIDC_ISSUER_URL:   http://iam.${DEMO_DOMAIN:-local}
infra/demo/iam-traefik.override.yml:232   ADMIN_OIDC_ISSUER: http://iam.${DEMO_DOMAIN:-local}
```

🔵 **리터럴이어야 하는 제약 자체는 진짜다** — 그 파일 헤더가 적어 뒀다:
`docker-compose.e2e.yml` 이 `OIDC_ISSUER_URL` 을 **리터럴로** 박으므로 override 도
리터럴로 이겨야 한다. 🔴 **그러나 그 제약은 «`${DEMO_DOMAIN}` 을 쓰라» 가 아니다** —
`${IAM_PUBLIC_URL:-…}` 도 compose 가 해석하는 셸 치환이라 제약을 똑같이 지킨다.

⇒ **두 줄을 `${IAM_PUBLIC_URL:-http://iam.local}` 로 바꿨다. 값은 오늘과 완전히 동일하다.**
바뀐 것은 **누가 이 문자열을 소유하는가** 뿐이고, 그래야 나중 전환이 **한 줄**이 된다.

🔴 **왜 이게 중요한가**: 이 줄이 남아 있으면 issuer 를 옮길 때 **여기만 옛 이름으로 남고**,
증상은 **「전 도메인 401」**이며 어느 파일이 원인인지 말해 주지 않는다 —
그 파일 헤더가 기록한 **바로 그 실패 모드**다.

### 🔴 P4 — 소셜 로그인 허용목록 **4건은 `demo.env` 로 못 고친다**

`auth-service/application.yml` 의 `OAUTH_{GOOGLE,KAKAO,MICROSOFT,NAVER}_ALLOWED_REDIRECT_URIS`
기본값에 `http://iam.local/login/oauth/<provider>/callback` 이 들어 있다.

🔴🔴 **그런데 `projects/iam-platform/docker-compose.yml` 은 `OAUTH_` 를 하나도 전달하지
않는다(실측 `0`건).** ⇒ demo.env 에 넣어도 **컨테이너에 안 간다.** 고치려면 compose 를
건드려야 한다. **AC-1 이 경고한 «선언 파일 grep ≠ 런타임 모집단» 이 정확히 이것이다.**

🔵 **다만 오늘은 불활성이다** — `demo.env` 는 `OAUTH_` 를 0건 주고 `client-id` 기본값이
`test-google-client-id` 다. 데모에서 소셜 로그인은 **애초에 동작하지 않는다.**
⇒ **지금 고치지 않는다. 트리거를 적는다: 누군가 실제 provider 자격증명을 넣는 순간**
이 4건이 살아나고, 그때 issuer 가 옮겨져 있으면 소셜 로그인만 조용히 죽는다.

### ✅ before / after — **같은 술어로**

| | 커버 | 미커버 |
|---|---:|---:|
| **before** | 51 | **9** |
| **after** | 53 | **7** |

남은 7 = 서빙 측 3(AC-2) + 관측 도구 4(무관). **issuer 소비자 축은 0이 됐다.**

### ✅ 런타임 검증 — **파일이 아니라 합성된 config 에서 읽었다**

```
$ DEMO_DOMAIN=<값> docker compose --env-file infra/demo/demo.env \
    -f projects/iam-platform/docker-compose.yml \
    -f projects/iam-platform/docker-compose.e2e.yml \
    -f infra/demo/iam-traefik.override.yml \
    -f infra/demo/iam-relay.override.yml config
```

| `DEMO_DOMAIN` | `OIDC_ISSUER_URL` | `ADMIN_OIDC_ISSUER` |
|---|---|---|
| `local` | `http://iam.local` | `http://iam.local` |
| `3-38-176-240.sslip.io` | `http://iam.3-38-176-240.sslip.io` | `http://iam.3-38-176-240.sslip.io` |

`rc=0`, stderr **0바이트**. 🔵 `-f` 사슬은 `infra/demo/projects.sh:35` 의 `[iam]` 정의
그대로다 — 내가 고른 것이 아니다.

🔵 출력에 `OIDC_ISSUER_URL: http://auth-service:8081` 한 줄이 남는데, **대조군으로
`origin/main` 의 override 를 넣어 돌려도 똑같이 1건**이다 ⇒ **내 변경이 만든 것이 아니다.**
들여쓰기 2칸에 `DB_PORT`·`KAFKA_BOOTSTRAP_SERVERS` 와 나란한 것으로 보아 **서비스가 아니라
YAML 앵커 정의**가 `config` 출력에 찍힌 것이다(그 앵커를 쓰는 서비스들은 override 가
병합돼 `http://iam.local` 로 나온다).

### 🔴 **값은 안 바꿨다**

`IAM_PUBLIC_URL` 을 `https://auth.hubwang.com` 으로 **뒤집지 않았다.** Vercel 프로젝트와
DNS 가 없는 상태에서 뒤집으면 **데모 로그인이 통째로 죽는다.** 이 AC 가 한 일은
**뒤집기를 한 줄로 만드는 것**이다.

---

## AC-2 — 🔴 **헤어핀** — 데모 내부가 새 issuer 에 닿는가 (ADR V6)

데모 내부 소비자 **12개**가 `https://auth.hubwang.com` 을 **해소하고 도달**해야 한다.
컨테이너 **안에서** discovery + JWKS 가 200 인지 찍는다.

🔴 **바깥에서 200 이 나오는 것은 이 축의 증거가 아니다.** 헤어핀은 «자기 자신의 공인 이름으로
나갔다 되돌아오는» 경로이고, C2 에서는 **Vercel 을 경유해** 되돌아온다. 그 경로는 **미측정**이다.
🔴 **개수를 세라** — 12개 중 몇 개를 실제로 찔렀는지 적고, 안 찌른 것은 «통과» 가 아니라 **미검사**다.

## 🟡 AC-2 **정적 절반** (2026-09-01) — 모집단이 **12 가 아니라 34 였고, 헤어핀은 5 다**

🔴 **이 AC 는 아직 안 닫혔다.** 여기 있는 것은 «몇 개를 찔러야 하는가» 이고,
«찔렀더니 200 이더라» 는 **새 이름이 실재해야** 잴 수 있다(⇒ 기동 창 + Vercel 프로젝트).

### 🔴 「소비자 12개」가 틀렸다 — 그 12 는 **키** 수다

AC-2 의 «12» 는 `ADR-MONO-069` 의 *"`IAM_PUBLIC_URL` 로부터 파생되는 키 12개"* 를
그대로 옮긴 것이다. **키와 컨테이너는 다른 축이다** — 키 하나가 여러 서비스로 퍼진다.

**측정 방법**: `DEMO_DOMAIN` 에 **센티넬**(`ac2probe.invalid`)을 주입하고 8개 스택의
합성 `docker compose config --format json` 을 읽어 **서비스 단위**로 셌다.
🔵 센티넬이 0건이면 치환이 안 된 것이지 «소비자가 없다» 가 아니다 — 그 단언을 넣었다.

| | 값 |
|---|---:|
| issuer 를 값으로 받는 **서비스** | **34** |
| 전달되는 **env 키** | **49** |
| AC-2 가 적었던 수 | ~~12~~ ← **키 축의 수였다** |

스택별: iam 5/15 · wms 6/17 · ecommerce 2/34 · scm 5/9 · fan 6/9 · finance 3/7 ·
erp 5/8 · console 2/2.

### 🔴🔴 그런데 34 가 전부 헤어핀은 아니다 — 그리고 **내 첫 분류가 틀렸다**

헤어핀이 필요한 것은 «`iss` 를 문자열로 비교하는 것» 이 아니라 **네트워크로 가져오는 것**
이다. `demo.env` 헤더가 그 설계를 명시했다: *"issuer 만 공개 호스트명으로 올리고,
fetch 대상은 컨테이너 DNS 로 못박는다."*

**첫 시도는 «키 이름» 으로 갈랐다**(`JWK|TOKEN_URI|REGISTRY|…`) → 3개.
**두 번째는 «JWKS 키를 받는가, 그 값이 어디를 가리키는가» 로 갈랐다** → 다른 3개 + 1개.

| 축 | 값 |
|---|---:|
| JWKS 키를 **하나도 안 받음** (Next.js 프런트 3종 — discovery 를 직접 한다) | **3** |
| JWKS 를 **공개 이름**으로 받음 (`fan/membership-service`) | **1** |
| JWKS 를 **컨테이너 DNS** 로 받음 | **30** |
| 합계 | **34** ✅ 앞 census 와 일치 |

🔴🔴 **두 대리지표가 서로 다른 답을 냈고, 그 불일치가 답이다.** 키 이름 축은
`web-store`·`fan-platform-web` 을 **놓쳤고**(JWKS 키 자체가 없어서), JWKS 축은
`console-bff` 의 **REST 대상**(`CONSOLE_BFF_OUTBOUND_IAM_BASE_URL`)을 놓쳤다.
**어느 하나만 썼으면 표면을 과소평가했다.**
[[feedback_comparing_two_extracts_measures_the_extractors]]
[[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]]

### ⇒ **공개 이름이 데모 안에서 도달돼야 하는 서비스 = 5개** (합집합)

| # | 서비스 | 왜 |
|---|---|---|
| 1 | `ecommerce/web-store` | JWKS 키 없음 ⇒ OIDC **discovery** 를 직접 |
| 2 | `fan/fan-platform-web` | 〃 |
| 3 | `console/console-web` | 〃 **+** registry·token-exchange·onboarding·admin-api **REST** |
| 4 | `console/console-bff` | `CONSOLE_BFF_OUTBOUND_IAM_BASE_URL` **REST** (JWKS 는 컨테이너 DNS 라 안전) |
| 5 | `fan/membership-service` | `INTERNAL_JWT_JWK_SET_URI` 가 **공개 이름**을 가리킨다 |

🔵 **30개는 헤어핀을 안 탄다** — `iss` 는 문자열 비교이고 JWKS 는 컨테이너 DNS 다.
설계가 의도한 대로였고, 그것을 **선언이 아니라 합성 config 에서** 확인했다.

🔵 **1·2 는 성격이 다르다**: `web-store`·`fan-platform-web` 은 `ADR-MONO-067` 로
**Vercel 로 이전 중**이다. Vercel 에서 도는 인스턴스에겐 이것은 헤어핀이 아니라 **평범한
외부 fetch** 다. 🔴 그러나 **데모 호스트에서도 여전히 돈다**(`TASK-MONO-604` 가 그 사실
자체를 다루는 티켓이다) ⇒ **두 자리 모두 세야 한다.**

### 🔴 **찌른 것은 0개다. 미검사이지 통과가 아니다**

AC-2 는 *"12개 중 몇 개를 실제로 찔렀는지 적고, 안 찌른 것은 «통과» 가 아니라 **미검사**"*
라고 요구했다. 오늘 기준:

| | |
|---|---:|
| 찔러야 하는 서비스 | **5** |
| 컨테이너 **안에서** discovery/JWKS 200 을 확인한 것 | **0** |

🔴 지금은 **잴 수 없다** — `auth.hubwang.com` 이 아직 없다. DNS + 네 번째 Vercel
프로젝트가 선행이고, 그건 **소유자 몫**이다.

---

## AC-3 — § Verification V1–V7 을 **안별로** 채운다

`TASK-MONO-574` 가 홉 ②③④⑤ 를 «미측정» 으로 남겼고, 이 티켓의 산출물이 **그 칸을 처음 채우는 것**이다.

| # | 무엇 | 통과 기준 |
|---|---|---|
| V1 | 홉 ① — 302 의 **목적지가 IdP 인가** | `Location` 이 issuer 오리진 |
| V2 | 홉 ③ — `state`/PKCE 쿠키가 **살아 돌아오는가** | 콜백이 `invalid_state` 를 안 낸다 |
| V3 | 홉 ⑤ — 세션 쿠키의 `Secure`/`SameSite` **실제 값** | 🔴 추론 아닌 **응답 헤더** |
| V4 | **로그아웃**(`/connect/logout`) | 🔴 로그인만 재고 «왕복 OK» 라 적지 마라 |
| V5 | 🔴🔴 **부팅 2회 연속** 로그인 — 사람 개입 없이 | 두 번째 부팅에서 **아무것도 안 고치고** 통과 |
| V6 | 헤어핀 (AC-2) | 위 |
| V7 | 데모 `stopped` 일 때 로그인 화면 | 502 가 아니라 **정의된 화면** |
| **V8** | 🔵 **`TASK-BE-589` 의 기존-볼륨 칸** (아래) | `V0034` 행이 **실제로** 들어갔는가 |

**V8 — 이 티켓의 일이 아니라 이 티켓의 «기동 창»에 얹는 것이다.** `TASK-BE-589`(콘솔
`redirect_uri` 시드)의 AC-4 는 신선 볼륨만 CI 로 닫았다. CI 는 **항상** 신선 볼륨이라
마이그레이션 순서/멱등성 결함에 **영구히 초록**이고, 데모 호스트는 기존 볼륨을 쓴다.

```sql
SELECT redirect_uris FROM oauth_clients WHERE client_id='platform-console-web';
-- https://console.hubwang.com/api/auth/callback 이 실제로 있는가
```

🔴 **마이그레이션 파일을 grep 하지 마라** — 파일에 있는 것과 행에 들어간 것은 다른 축이고,
그 차이가 이 판정의 존재 이유다. 🔴 판정 전에 `flyway_schema_history` 의 최대 version 이
`V0034` **미만**이었는지 확인한다 — 아니면 이건 신선 볼륨 판정의 재탕이다.
🔵 **`TASK-BE-582` 가 이 칸을 열어 둔 채 닫았고, 줍는 데 티켓 하나(`TASK-MONO-605`)가
더 들었다.** 589 는 같은 일을 반복하지 않으려고 여기에 미리 붙인다.

🔴🔴 **V5 가 이 결정의 본체다.** 축 ② 는 «한 번 됐다» 로 판정되지 않는다 — **부팅 두 번을
사람 손 없이** 건너야 성립한다. 단일 표본을 성질로 승격시키지 마라.

🔵 **기동이 필요하다** ⇒ 소유자 승인·예산 사안이다. AC-0/1 은 그것 없이 진행할 수 있다.

## ✅ 포워더 구현 (2026-09-02) — `infra/demo/auth-forwarder`, Vercel 프로젝트 `kanggle-auth`

`§ D6.1` 순서의 **3번**이다(1=613, 2=614 는 둘 다 머지됐다). `@demo/backend-resolver` 를
**import** 해서 그날 IP 를 얻는 catch-all 라우트 핸들러 하나짜리 Next 앱이다.
자리·형태·소유자 작업은 [`infra/demo/auth-forwarder/README.md`](../../infra/demo/auth-forwarder/README.md).

🔵 **형태를 Next 로 고른 이유는 «실측된 것을 고른다» 다.** 프레임워크 없는 `api/` 함수가
더 가볍지만, `link:` 로 연결한 TS 소스 패키지를 그 번들러가 어떻게 다루는지는 **미측정**이다.
반면 `transpilePackages` + `link:` 는 `TASK-MONO-614` 에서 **두 앱이 프로덕션까지 통과**했다.

### 🔴 두 설계 결정 — AC-0 의 실측이 그대로 코드가 됐다

| # | 결정 | 근거 |
|---|---|---|
| ① | 업스트림에 보내는 `Host` 는 **`iam.<DEMO_DOMAIN>`** (공개 이름이 아니다) | 데모 앞단 Traefik 은 라우터를 `Host` 로 고른다. 공개 이름을 넘기면 DNS·TCP 는 성공하는데 **404** 가 난다 |
| ② | 공개 이름은 **`X-Forwarded-*`** 로 | AC-0 축 2 실측: 그 헤더를 **빼면 `http://`, 넣으면 `https://`** 로 `Location` 이 갈렸다(음성 대조군이 반대로 움직였다) |

### 🔴🔴 로컬 하네스를 만들어 종단 간으로 쟀고, **결함 둘을 잡았다**

스텁 IdP(:80) + 스텁 컨트롤 플레인(:19099) + `next start`. **12칸.**
🔵 IdP 스텁은 **받은 헤더를 그대로 되돌려 준다** — 포워더가 무엇을 보냈는지 «추론» 이 아니라
**헤더로** 판정한다(AC-0 이 요구한 관측 지점과 같다).

| 결함 | 무엇이 틀렸나 | 왜 조용한가 |
|---|---|---|
| **(a)** `X-Forwarded-Host` 를 `new URL(req.url).host` 로 만들었다 | 그것은 들어온 `Host` 헤더가 **아니다**. 실측: `Host: 127.0.0.1:3003` 으로 불렀는데 값은 **`localhost:3003`** 이었다 | 🔴 Vercel 에서는 커스텀 도메인이 아니라 **배포 URL** 이 될 수 있다 ⇒ IdP 가 `https://<deployment>.vercel.app/login` 을 광고한다. **이 프로젝트의 존재 이유가 무너지는데 아무것도 «실패» 하지 않는다** |
| **(b)** 응답의 `content-encoding` 을 통과시켰다 | undici 는 본문을 **자동 해제하면서 헤더는 남긴다**(실측: `content-encoding: gzip` + `content-length: 35` 가 남고 본문은 이미 평문) | 증상이 «프록시가 죽었다» 가 아니라 **«어떤 페이지만 깨진다»** 다 |

🔵 **그리고 하네스 자신이 한 번 거짓말했다.** 앞 실행의 `next start` 가 3003 을 물고 있어
새 서버가 못 붙었고, 테스트는 **낡은 빌드**를 쟀다 — 코드를 두 군데 고쳤는데 결과가 하나도
안 바뀌어 «수정이 안 먹는다» 로 오진할 뻔했다. ⇒ 하네스가 **시작 전에 포트가 비어 있음을
단언**하고, 끝나면 포트를 물고 있는 PID 를 직접 죽이도록 고쳤다.
[[feedback_a_harness_must_pin_which_tree_it_measures]]

### ✅ 실측 결과

| 축 | 결과 |
|---|---|
| 업스트림이 받은 `Host` | ✅ `iam.127-0-0-1.sslip.io` (① 이 성립한다) |
| `X-Forwarded-Proto` / `-Host` / `-Port` | ✅ `https` / **들어온 Host 헤더** / `443` |
| `Location` 통과 | ✅ **302 를 따라가지 않고** `https://<들어온 Host>/login` 을 그대로 넘긴다 |
| `Set-Cookie` **2개** | ✅ 합쳐지지 않는다(`getSetCookie()` + `append`) |
| gzip 응답 | ✅ `content-encoding` 제거 · 본문 평문 |
| 경로·쿼리·POST 본문 | ✅ `/a/b?x=1&y=2` · `POST` 보존 |
| **V7 — 데모 `stopped`** | ✅ **503 + 정의된 화면**(502 아님) |
| **로컬·CI (`DEMO_API_BASE` 없음)** | ✅ 503 + *"데모 컨트롤 플레인이 설정되어 있지 않습니다"* |

🔴 **이것은 «로그인이 된다» 가 아니다.** 스텁은 Spring 의 `ForwardedHeaderFilter` 동작을
흉내낸 것이고, 진짜 IdP·진짜 브라우저·진짜 왕복은 **V1–V8**(기동 창)이 잰다.

### ✅ AC-5 — 넷째 Vercel 프로젝트의 배선 부채를 **같은 PR 에서** 갚았다

| 무엇 | 조치 |
|---|---|
| `vercel-deploy.yml` matrix + secret | `kanggle-auth` 항목 추가(판정자 4개 · secret 4개). 🔴 없으면 칸 (14)가 *"main=false 인데 워크플로가 이 프로젝트를 안 굽습니다"* 로 문다 |
| `check-vercel-build-triggers.sh` `FLOOR` | **3 → 4** |
| `vercel.json` `deploymentEnabled.main` | `false` (607 의 훅 축을 따른다) + `preview/*: true` |
| `ci.yml` | `auth-forwarder` paths-filter + 프런트 잡에 **install/typecheck/build** 3스텝 |

🔴 **`VERCEL_DEPLOY_HOOK_AUTH` 는 소유자가 프로젝트를 만든 뒤에야 생긴다.** 그때까지
`kanggle-auth` 잡은 *"secret 이 비어 있습니다"* 로 **빨갛다** — 그리고 **그것이 정확한
신호다**: 앱은 저장소에 있는데 배포할 곳이 없다. 🔵 판정자가 이 앱의 경로를 안 건드린
커밋은 건너뛰므로 **매 커밋 빨간 것은 아니다**.

### 🔴🔴 그리고 `TASK-MONO-614` 가 만든 **조용한 회귀**를 찾아 고쳤다

가드가 스스로 말했다 — 포워더에 대해 *"로컬 경로 의존이 **없습니다**"*.

614 는 칸 (12)에 **`file:`** 를 더했는데, 같은 티켓이 그 뒤 vitest 문제로 의존을
**`file:` → `link:`** 로 바꿨다. 그러자 그 칸은 다시 눈이 멀었다:

| | 614 중간 | 614 최종(= main) | 지금 |
|---|---|---|---|
| web-store | 7/7 | 🔴 **6/6** | ✅ 7/7 |
| fan | 1/1 | 🔴 **SKIP** | ✅ 1/1 |
| auth-forwarder | — | — | ✅ 1/1 |

🔴 **두 수정은 각각 옳았고, 그 사이의 구멍만 아무도 안 봤다.** 그리고 그 회귀는
**빨간불을 하나도 안 냈다** — 가드가 조용해지는 방향이었기 때문이다.
⇒ 칸 (12)가 `link:` 도 보게 했다. **bite**: 포워더의 pathspec 을 빼자(제거를 먼저 단언)
`1개 중 0개` · rc=1. self-test 는 칸 (i)가 `4 -> 3` 으로 따라간다.
[[feedback_two_correct_exclusions_compose_into_a_hole]] [[feedback_why_a_guard_does_not_bite]]

### 🔵 `TASK-MONO-613` 이 예견한 «첫 번째 예외»가 도착했다

613 은 *"오늘 그 구멍은 잠재적이다 — 그러나 610 이 만들려는 것이 정확히 첫 번째 예외다"*
라고 적었다. 이 앱이 `projects/` **밖의 첫 Next 앱**이고, 해석기 가드는 지금
**선언 앱 4개**를 본다. 🔴 613 이 없었다면 이 앱은 모집단 **밖**이었을 것이다.

### 🔴 이 티켓은 **아직 `in-progress`** 다 — 닫히지 않은 AC 가 셋 있다

| AC | 왜 안 닫혔나 |
|---|---|
| AC-2 라이브 절반 | 새 이름이 **실재해야** 헤어핀을 잰다 ⇒ 소유자 1~3 + 기동 창 |
| AC-3 (V1–V8) | 기동 창. 🔴 **V5 는 부팅 2회**를 요구한다 |
| AC-4b | `kanggle-fan` · `kanggle-store` 의 `OIDC_ISSUER_URL` 을 새 issuer 로 — 대시보드. 🔴 **그리고 store 는 그것만으로 안 된다**(아래 확대) |

🔵 **`review/` 로 올리지 않는 것이 옳다.** `review/` 는 frozen 이라 남은 AC 를
CORRECTION 으로만 적게 되고, 그러면 «위를 먼저 읽는 사람» 이 닫힌 티켓으로 오해한다.

---

## AC-4b — 🔴 **새 issuer 이름을 실제로 «꽂는다»** (`TASK-MONO-611` 이 발견)

C2 가 stable HTTPS 이름을 만들어도 **아무도 그 값을 앱에 넣지 않으면** 로그인은 그대로
죽어 있다. `TASK-MONO-611` 이 실측했다 — `kanggle-fan` 프로덕션에 `OIDC_ISSUER_URL` 이
**없고**, 코드는 `http://iam.local` 로 조용히 폴백한다.

- `kanggle-fan` 프로덕션 env 에 `OIDC_ISSUER_URL` = **새 issuer** 를 세팅한다
- 🔴 `kanggle-store` 의 **죽은 값**(`http://iam.3-38-176-240.sslip.io`)도 같이 교체한다 —
  안 하면 fan 만 고쳐지고 store 는 시체를 든 채 남는다
- 🔵 **`TASK-BE-589` 가 IdP 쪽 절반을 이미 했다**(`redirect_uri` 등록). 이건 **앱 쪽 절반**이다
- 🔴 **판정은 env 목록이 아니라 «로그인이 되는가»** 다 — 그건 V1–V7 이 한다

### 🔴🔴 확대 (2026-09-02 UTC, `TASK-MONO-612` AC-0 이 잡았다) — **store 는 issuer 만으로 안 산다**

위 목록은 `OIDC_ISSUER_URL` **하나**를 이름으로 든다. 실측해 보니 `kanggle-store` 는
그 하나를 꽂아도 **여전히 500** 이다 — 그 프로젝트의 auth env 가 **통째로 비어 있다**.

| 찌른 곳 | 🔵 `kanggle-fan` (양성 대조군) | 🔴 `kanggle-store` |
|---|---|---|
| `/api/auth/providers` | **200** · 167 B · `iam` 나열 | **500** · 108 B |
| `/api/auth/csrf` | **200** · 80 B | **500** · 108 B |
| `/` (음성 대조군) | 307 → `/login` | **200** · 34,963 B |
| 프로덕션 env 전수 | `NEXTAUTH_SECRET` · `NEXTAUTH_URL` · `OIDC_CLIENT_ID` · `OIDC_CLIENT_SECRET` | **`DEMO_API_BASE` 하나** |

🔴 **단일 변수 귀속은 안 한다** — 두 팔 사이 변수가 네 개 다르다. 주장은 *"store 의 auth
env 가 없고 `/api/auth/*` 가 전부 500"* 까지다. [[feedback_control_group_design_four_axes]]

**⇒ AC-4b 의 소유자 작업이 store 쪽에서는 다섯 줄이다**(fan 은 한 줄 그대로):

| # | `kanggle-store` 프로덕션 env | 값 |
|---|---|---|
| 1 | `OIDC_ISSUER_URL` | 새 issuer (`https://auth.hubwang.com`) |
| 2 | `NEXTAUTH_URL` | `https://store.hubwang.com` |
| 3 | `NEXTAUTH_SECRET` | 🙋 **소유자 생성** (fan 과 같은 값일 필요 없다) |
| 4 | `OIDC_CLIENT_ID` | `ecommerce-web-store-client` — 🔴 **단, V0035 선행**(아래) |
| 5 | `OIDC_CLIENT_SECRET` | 같은 클라이언트의 시크릿 — 🔴 **단, V0035 선행** |

#### 🔴🔴 그 확인을 했다 — **4·5 는 대시보드 작업이 아니다. IdP 마이그레이션이 선행이다**

| 물음 | 실측 (2026-09-02 UTC) |
|---|---|
| store 용 OIDC 클라이언트가 있나 | ✅ **있다** — `ecommerce-web-store-client`(`V0012` 시드, dev secret `ecommerce-dev`) |
| 콜백 **경로 모양**은 무엇인가 | ✅ **`/api/auth/callback/iam`** — provider `id: 'iam'`(`web-store/src/shared/auth/auth.ts:76`)이 결정한다 |
| `store.hubwang.com` 의 `redirect_uri` 가 등록돼 있나 | 🔴 **없다.** 등록된 것은 `http://localhost:3001/api/auth/callback/iam` 과 `http://web.ecommerce.local/api/auth/callback/iam` 뿐 |
| 그 부재가 누락인가 | ❌ **의도된 것이다** — `V0033`·`V0034` 가 *"`store.hubwang.com` … deliberately absent"* 라고 명시하고, *"각 표면은 자기 단계가 올 때, **경로를 추정이 아니라 측정한 뒤** 자기 마이그레이션을 갖는다"* 를 규칙으로 든다 |

⇒ **`V0035` 가 필요하다**(`V0033`/`V0034` 와 같은 형태 — 현재 최신이 `V0034`).
🔵 **그 규칙이 요구한 선행 조건은 이제 충족됐다** — 경로 모양이 측정됐다.

🔴 **이것이 없으면 소유자가 1·2·3 을 넣고도 `redirect_uri_mismatch` 를 만난다.** 그리고
`V0033` 헤더의 실측대로 **그 오류는 URI 도 클라이언트도 이름으로 대지 않는다** — 즉
소유자는 *"issuer 를 꽂았는데 여전히 안 된다"* 만 보게 된다.

🔴 **`TASK-BE-589` 는 이 절반을 하지 않았다.** 그것은 **팬/콘솔** 축이었다. 여기서
떠넘기지 않기 위해 적어 둔다 — **이 행은 `projects/iam-platform/` 의 프로젝트 티켓이고
`610` 도 `612` 도 아니다** ⇒ **별도 spec PR 로 큐에 올린다.**
[[feedback_the_unguarded_operation_is_where_the_invariant_breaks]]

🔵 **왜 이걸 아무도 안 봤나**: `TASK-MONO-582` 는 `NEXTAUTH_SECRET` 을 «빌드를 죽이나» 로만
봤고 *"✅ 빌드를 안 죽였다"* 로 닫았다 — **잰 것이 빌드였다**. `TASK-MONO-611` 은 죽은
issuer 를 지우며 *"오늘 동작 차이는 0"* 이라 적었다 — 맞다, **이미 500 이었으니까**. 세
티켓이 각각 옳았고 그 사이에 공백이 남았다.
[[feedback_two_correct_exclusions_compose_into_a_hole]]
[[feedback_a_reported_figure_must_name_what_was_measured]]

🔵 원장은 `projects/ecommerce-microservices-platform/apps/web-store/VERCEL.md` 에 넣었다.

## AC-4 — 🔵 **rider 의 배선** — apex 쿠키는 **호스트 한정**

소유자 지정: `ADR-MONO-069 ACCEPTED — C2 (apex 쿠키는 호스트 한정)`.

- IdP 세션 쿠키의 `Domain` 은 **`auth.hubwang.com` 자신**이다. `.hubwang.com` 으로 넓히지 않는다.
- ⇒ `fan.hubwang.com` · `console.hubwang.com` 은 IdP 세션 쿠키를 **받지 않는다**.
- 🔵 그래도 SSO 는 성립한다 — 브라우저가 IdP 오리진으로 **이동**할 때 실린다.
- 🔴 **실제 `Set-Cookie` 헤더로 확인하라**(AC-0 ③ 과 같은 관측 지점). 설정값만 보고 적지 마라.

> 🔵 **소유자가 한 줄로 뒤집을 수 있다**: *"apex 공유로 바꾼다"* 면 `Domain=.hubwang.com` 이 되고,
> 그때는 **모든 서브도메인이 IdP 세션 쿠키를 받는다** — 그 노출 범위가 이 rider 가 좁힌 것이다.

## AC-5 — 🔴 **넷째 Vercel 프로젝트가 만드는 배선 부채를 같은 PR 에서 갚는다**

지금 셋을 전제로 적힌 것들이 **넷째가 들어오는 순간 틀린다**:

| 무엇 | 지금 | 안 고치면 |
|---|---|---|
| `.github/workflows/vercel-deploy.yml` matrix + secret | 3쌍 | 🔴 칸 **(15)** 가 «판정자 N개 vs secret M개 짝이 안 맞습니다» 로 문다 |
| `scripts/check-vercel-build-triggers.sh` `FLOOR` | **3** | 하한이 낡는다 |
| `vercel.json` `git.deploymentEnabled.main` | 셋 다 `false` | 🔴 **넷째가 `true` 로 들어오면 칸 (14)는 `note` 로 통과시킨다**(`TASK-MONO-607` AC-4 ①) ⇒ 607 이 없앤 낭비가 그 경로로 되돌아온다 |
| `TASK-MONO-607` § 재개 트리거 ③ | *"프로젝트가 4개로 늘어 소비가 2배"* | 🔵 **이 티켓이 그 트리거를 발화시킨다** — 607 은 `done/` 이므로 되살리지 말고 **여기 적고 필요하면 새 티켓**을 판다 |

🔴 **가드를 끄지 마라.** (15)가 무는 것은 결함이 아니라 **설계대로 동작하는 것**이다.

## AC-6 — 이 구현이 **안 고치는 것**을 적는다

- **홉 ① 버그**(ADR § R1) — 구조를 놓아도 그 버그는 그대로일 수 있다.
- **`console-web` 의 `redirect_uri` 시드** — `TASK-MONO-585`.
- **`console-web` 이 이 축에서 미개척**이라는 사실 — Vercel 프로젝트조차 없다. C2 배선 뒤
  console 에서 **처음 보는 결함**이 나올 자리가 남아 있다(ADR § Consequences).
- **`ADR-MONO-067` § D2(주소 고정) 미결** — 채택되면 C2 는 **더 싸질 뿐** 무효는 아니다.

---

# Related Specs

- [`docs/adr/ADR-MONO-069-…md`](../../docs/adr/ADR-MONO-069-oidc-login-across-the-scheme-and-scope-boundary.md) § Decision · § 선택지 C · § Verification · § R1–R3
- `docs/adr/ADR-MONO-068-…md` — 재사용할 해석기
- `docs/adr/ADR-MONO-067-…md` § D2(미결) · 단계 3·4
- `tasks/done/TASK-MONO-574-…md` — 홉 표(②③④⑤ 미측정)
- `tasks/ready/TASK-MONO-585-…md` — console `redirect_uri` 시드 선행
- `tasks/done/TASK-MONO-607-…md` § 재개 트리거 ③ · AC-4

# Related Contracts

`iam-platform` 의 OIDC discovery(`issuer`) — 값이 바뀐다. 소비자 전수는 AC-1.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| AC-0 의 2 또는 3 이 **거짓** | 🔴 **STOP.** 코드 쓰지 말고 측정값을 적은 뒤 **C1 재지정을 요청**한다 |
| AC-1 의 소비자 수가 **0** | 술어가 틀린 것이다. 0 을 «옮길 게 없다» 로 읽지 마라 |
| 헤어핀 12개 중 일부만 찔렀다 | 안 찌른 것은 **미검사**로 적는다. «통과» 로 세지 마라 |
| 이미 발급된 토큰의 `iss` | 🔴 issuer 변경은 **되돌리기가 싸지 않다**(ADR § C 되돌리기). 기존 세션이 깨지는 것을 예상하고 적어라 |
| 데모가 `stopped` 인 상태로 배선 | AC-0/1/5 는 가능. **V1–V7 은 기동이 필요**하다 |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| 포워딩이 `Location` 을 평문으로 되돌려 놓는다 | 브라우저가 `http://iam.<ip>.sslip.io` 로 튄다 | AC-0 ② 가 **배선 전에** 잡아야 한다. 배선 후에 발견했다면 AC-0 을 건너뛴 것이다 |
| 헤어핀이 안 된다 | 컨테이너 안에서 discovery 가 타임아웃 | C2 의 구조적 한계일 수 있다 — **C1 재지정 후보**다. 우회 배선을 발명하지 마라 |
| 넷째 프로젝트를 배선 없이 넣었다 | 칸 (15) RED | 🔵 **가드가 제 일을 한 것이다.** AC-5 를 같은 PR 에서 처리하라 |
| V5 를 **한 번의 부팅**으로 통과 처리 | «로그인 됐다» 한 줄 | 🔴 그건 이 결정을 검증하지 않았다. 부팅 2회가 기준이다 |
| 로그인만 재고 닫는다 | V4 공백 | `TASK-MONO-574` AC-2 가 정확히 그것을 경고했다 |
