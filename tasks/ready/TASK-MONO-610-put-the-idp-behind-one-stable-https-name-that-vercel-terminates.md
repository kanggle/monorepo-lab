# Task ID

TASK-MONO-610

# Title

`ADR-MONO-069` **(C2) 구현** — IdP 를 `auth.hubwang.com` 하나 뒤에 두고 Vercel 이 TLS 를 끝낸다. 🔴 배선 전에 **C2 를 탈락시킬 수 있는 두 가지**부터 잰다.

# Status

ready

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

# ⏸️ 선행 — 🙋 **소유자 조회 하나 (ADR § R1)**

> Vercel → `kanggle-fan` → Runtime Logs → `/api/auth/signin/iam` 의 `[auth][error]` 한 줄.
> 시각 **2026-08-29T17:03Z 전후**.

🔴 **이것은 착수 차단이 아니다** — C2 배선은 그 줄 없이도 진행할 수 있다. 다만 그 줄이
*"사소한 배선 문제(오타·누락 env·`AUTH_URL`)"* 로 밝혀지면 **ADR § R1 넷째 칸대로 이 결정
자체를 재검토**하므로, 이 티켓의 코드를 쓰기 전에 **한 번은 물어보는 것이 가장 싸다**.
🔴 지금 재시도해 얻는 로그는 **다른 오류**다(데모 정지 → IP 반납 ⇒ 연결 실패). 저장소가 대신
할 수 없다 — 이 호스트의 Vercel CLI 는 인증돼 있지 않다.

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

## AC-1 — 🔴 **`issuer` 의 소비자를 세고, 전수를 옮긴다**

ADR § 선택지 C 가 *"issuer 를 검증하는 모든 것이 같이 움직인다"* 라고 적고
`infra/demo/demo.env:58` 의 `IAM_PUBLIC_URL` **한 변수**를 지목했다.

- 🔴 **그 한 줄을 믿지 말고 다시 세라.** 선언 파일 grep 은 런타임 모집단이 아니다 —
  compose override · 앱 env · 시드 SQL · 하드코딩을 각각 센다.
- 🔴 **모집단 ≥ 1 을 단언**하라. 0 이 나오면 그것은 «옮길 게 없다» 가 아니라 **술어가 틀린 것**이다.
- 옮긴 뒤 **남은 평문 issuer 참조가 0건**임을 같은 술어로 확인한다(before/after 를 둘 다 적어라).

## AC-2 — 🔴 **헤어핀** — 데모 내부가 새 issuer 에 닿는가 (ADR V6)

데모 내부 소비자 **12개**가 `https://auth.hubwang.com` 을 **해소하고 도달**해야 한다.
컨테이너 **안에서** discovery + JWKS 가 200 인지 찍는다.

🔴 **바깥에서 200 이 나오는 것은 이 축의 증거가 아니다.** 헤어핀은 «자기 자신의 공인 이름으로
나갔다 되돌아오는» 경로이고, C2 에서는 **Vercel 을 경유해** 되돌아온다. 그 경로는 **미측정**이다.
🔴 **개수를 세라** — 12개 중 몇 개를 실제로 찔렀는지 적고, 안 찌른 것은 «통과» 가 아니라 **미검사**다.

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
