# Task ID

TASK-MONO-576

# Title

`ADR-MONO-067` **D4**(OIDC·쿠키 축)를 **PROPOSED ADR** 로 분리해 쓴다. 이 축이 안 풀리면 단계 3·4 는 못 간다.

# Status

review

# Owner

monorepo

# Task Tags

- adr
- auth
- decision

---

# ⏳ 선행

`TASK-MONO-574`(AC-0 ③ OIDC 왕복 실측)의 **결과가 이 ADR 의 근거**다.
🔴 **실측 없이 쓰지 마라** — `ADR-MONO-067` § AC-0 이 못 박았다: *"「안 될 것 같다」가 아니라
실측이어야 한다. 이 저장소는 쿠키 축에서 두 방향 모두 데인 적이 있다."*

---

# Goal

`ADR-MONO-067` 이 **의도적으로 미룬** D4 를 별도 ADR(**`ADR-MONO-069`**, PROPOSED)로 쓴다.

🔴 **번호가 바뀌었다 (2026-08-26).** 이 티켓이 쓰일 때 비어 있던 `ADR-MONO-068` 은
그 뒤 `TASK-MONO-577` 이 **해석기 위치 결정**으로 가져갔다
(`docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md`, ACCEPTED).
착수 시 **`docs/adr/` 를 다시 세고 그다음 번호를 쓴다** — 이 줄의 `069` 도 오늘 값이다.

ADR-067 본문:

> **D4(OIDC/쿠키)는 별도 결정.** 로그인 리다이렉트는 최상위 내비게이션이라 프록시로 못 감싼다.
> 이 축이 안 풀리면 단계 3·4 는 못 간다.

---

# Scope

## 포함

- `docs/adr/ADR-MONO-069-*.md` **PROPOSED** 작성 (🔴 번호는 착수 시 재확인 — 위 §).
- 선택지 열거 + 각각의 근거·비용·되돌리기 비용.
- `docs/adr/INDEX.md` 행 추가.

## 제외

- 🔴 **ACCEPT 하지 않는다.** 소유자의 **정확형 지정**만이 ACCEPT 이고, 제 추천은 선택이 아니다.
- 구현. 이 티켓은 결정 문서만 만든다.
- ADR-067 본문 수정 — D4 해소는 **후속 ADR 이 가리키는** 방식으로 한다.

---

# Context — 실측된 제약 (2026-08-23)

세 앱 다 next-auth v5 OIDC. 소비 지점의 성격이 갈린다:

| 소비 | 종류 | 프록시로 흡수 |
|---|---|---|
| discovery · `/oauth2/token` | 서버 fetch | ✅ |
| **authorize 리다이렉트** · **`/connect/logout`** | **최상위 내비게이션** | ❌ |

최상위 내비게이션은 mixed content 규칙 **밖**이라 이동은 막히지 않는다 — 그래서
*"열리니까 된다"* 로 오독되기 쉽다. [[env_top_level_navigation_is_exempt_from_mixed_content]]

딸린 제약:

- **`redirect_uri` 가 Flyway 시드**(fan: `http://localhost:3002/`·`http://fan-platform.local/`,
  GAP V0011+V0028) ⇒ Vercel 도메인을 넣으려면 마이그레이션 변경, 그리고 **production 고정 도메인**이
  전제(preview URL 은 배포마다 다르다).
- **`issuer` 가 부팅마다 바뀐다** — discovery 와 `iss` 클레임이 함께 움직이는데 next-auth 는
  issuer 를 설정값으로 받는다.
- 🔵 쿠키는 유리하다 — `NEXTAUTH_URL` 이 `https://` 면 `secureCookie` 가 자동으로 켜진다.

---

# Acceptance Criteria

## AC-0 — 574 의 결과를 읽고 시작한다

574 가 **어느 홉에서** 무엇을 관측했는지에 따라 선택지 집합이 달라진다. 통과했다면 (B) 유지가
후보에 남고, ③ 에서 쿠키가 유실됐다면 (B) 는 인증 축에서 성립하지 않는다.

🔴 574 가 없으면 **STOP**. 추론으로 채우지 않는다.

### ✅ 574 는 닫혔다 (2026-08-30) — **그런데 답이 «③ 이 깨졌다» 가 아니다. 읽는 법이 바뀐다.**

`TASK-MONO-574` 판정 = **거짓**, 끊긴 곳 = **홉 ①**(앱이 authorize URL 을 만들지 못함,
`error=Configuration`). ⇒ **위 두 갈래 중 어느 쪽도 아니다.**

🔴🔴 **홉 ②③④⑤ 는 «실패» 가 아니라 «미측정» 이다.** 그러니 이 AC 를 *"③ 에서 쿠키가
유실됐으니 (B) 탈락"* 으로 읽으면 **없는 측정을 근거로 선택지를 지우는 것**이다.
**쿠키 축은 여전히 열려 있다** — (B) 를 인증 축에서 탈락시킬 근거는 아직 없다.

**그리고 574 가 실제로 준 입력은 다른 것이다 — § AC-1.5 가 요구하는 바로 그 축이다:**
issuer 는 **부팅 범위**(`sslip.io` IP 파생), Vercel env 는 **배포 범위**. 이 불일치가
「매 부팅마다 소유자가 env 를 고치고 재배포」를 요구하고, 그것은 절차가 아니다.
⇒ **단계 3·4 를 막는 것은 쿠키가 아니라 이 범위 불일치다.**

#### 🙋 AC-0 의 남은 입력 **하나** — 소유자 조회 (기동·예산 불요)

> **Vercel 대시보드 → `kanggle-fan` → Runtime Logs 에서
> `/api/auth/signin/iam` 요청의 `[auth][error]` 한 줄. 시각은 2026-08-29T17:03Z 전후.**

- **왜 필요한가**: `error=Configuration` 은 원인을 안 말하는 라벨이고, 574 가 **가설 여섯을
  죽이고도** 못 좁혔다. `@auth/core` 는 서버에 실제 사유를 찍는다 — 그 한 줄이 추론 여섯보다 정확하다.
- 🔴 **지금 재시도해 얻는 로그는 다른 오류다** — 데모가 정지돼 IP 가 반납됐으므로 **연결 실패**가
  찍힌다. 원래 원인을 보려면 **그때의 로그**여야 한다(Pro 플랜이라 보관 기간이 길다).
- 🔴 **저장소가 대신 할 수 없다** — 이 호스트의 Vercel CLI 는 인증돼 있지 않다(2026-08-30 확인:
  `npx vercel whoami` 가 npx 캐시 파손으로 rc=1, 자격증명 디렉터리도 없음).
- 🔵 **이 줄이 없어도 AC-1 의 선택지 열거는 시작할 수 있다.** 다만 **AC-2(무엇이 이 결정을
  무효로 만드는가)** 에 *"① 의 실제 사유가 밝혀지면 재검토"* 를 반드시 남겨라 — 사유가
  사소한 배선 문제로 밝혀지면 (B) 의 비용 계산이 바뀐다.

## AC-1 — 선택지를 **최소 3개** 열거하고, 각각 반증 가능하게 쓴다

최소한 아래를 다룬다(더 있으면 추가):

| # | 축 | 핵심 질문 |
|---|---|---|
| A | (B) 유지 — 인증도 평문 IdP 로 왕복 | 574 가 참인가. `redirect_uri`·`issuer` 를 어떻게 고정하나 |
| B | **EC2 에 TLS 종단**(Traefik ACME + `sslip.io`) | ~~AC-0 ②·③ 과 D4 가 **동시에** 사라진다.~~ 🔴 **정정 (2026-08-30)** — 아래 § AC-1.5 표와 **같은 결함**이다. `ACME × sslip.io` 변형(B1)은 **성립 불가**(`infra/demo/demo.env:165-167`)이고, TLS 는 축 ①(스킴)만 없앤다. `ADR-MONO-069` § 선택지 B → 정정 ① |
| C | IdP 만 별도로 안정 도메인 뒤에 둔다 | 앱은 (B), IdP 만 고정. 부분 해결의 경계가 어디인가 |

각 선택지마다 **되돌리기 비용**을 적는다. 시드 마이그레이션이 걸린 선택지는 되돌리기가 비싸다.

## 🔴🔴 AC-1.5 — **움직이는 issuer** 를 어떻게 고정할지 답해야 한다 (2026-08-25 추가)

`TASK-MONO-574` 의 선행을 실측하다 드러났다. 선택지 열거에 **이 축이 빠져 있었다.**

`OIDC_ISSUER_URL` 은 Vercel env 라 **배포 시점에 고정**되는데, 데모 IdP 주소는
`iam.<ip-대시>.sslip.io` 라 **부팅마다 바뀐다.** 한 번 채워도 다음 부팅에 낡는다.

⇒ **D2(주소는 런타임 조회여야 한다)가 인증 축에서 그대로 재현된다. 그런데 더 어렵다** —
next-auth 는 `issuer` 를 **설정값**으로 받고, 그 값은 **discovery 문서**와 토큰의 **`iss` 클레임**
양쪽에 묶인다. 데이터 평면처럼 "부를 때마다 조회" 로 우회되지 않는다.

각 선택지는 이 질문에 답해야 한다:

| 선택지 | 움직이는 issuer 를 어떻게 다루나 |
|---|---|
| A (B 유지) | 부팅마다 Vercel env 갱신? 그러면 **재배포가 필요**하고 rate limit 을 먹는다 |
| B (EC2 TLS 종단) | ~~안정 도메인이 생기면 **이 문제도 같이 사라진다** — 이 축에서 B 의 값이 올라간다~~ 🔴 **거짓. 정정 (2026-08-30) — 아래 §** |
| C (IdP 만 고정) | 정확히 이 문제만 겨냥한 안. 경계가 어디인지 적어라 |
| **D** | 🔴 **이 표에 없던 축** — `issuer` 를 **요청 시점에 해석**한다. 아래 § 정정 ② |

🔵 이 관측 하나가 **B 의 무게를 바꾼다.** ADR 은 그것을 근거로 적되, **고르지는 않는다.**

### 🔴🔴 정정 ① — B 행은 **거짓이다** (2026-08-30, 소유자 지시로 실측)

*"TLS 를 붙이면 안정 도메인이 생긴다"* 는 **두 축을 하나로 본 결과**다. 실측하면 갈린다:

1. **TLS 는 스킴만 바꾼다.** § AC-1 표가 B 를 `Traefik ACME + sslip.io` 로 정의했는데,
   `https://iam.3-38-176-240.sslip.io` 는 `http://…` 와 **똑같이 IP 파생**이다.
   ⇒ 축 ①(스킴)만 사라지고 **축 ②(범위)는 그대로 남는다.**
2. 🔴 **그리고 그 변형은 저장소가 이미 기각해 뒀다** — `infra/demo/demo.env:165-167`:
   *"`sslip.io` 는 Public Suffix List 에 **없다** → Let's Encrypt 가 `sslip.io` 전체를 하나의
   등록 도메인으로 묶어 주당 50장 한도를 전 세계와 공유한다. 실 도메인을 사지 않는 한
   ACME 발급은 성립하지 않는다."*

⇒ B 는 **변형을 갈라야 한다**: **B1**(`sslip.io`×ACME) = 성립 불가 · **B2**(`*.hubwang.com`) = 가능하나
DNS 쓰기·인증서 배포·전파 경주가 **전부 미측정**. `ADR-MONO-069` § 선택지 B.

🔵 **이 티켓이 그 실측을 몰랐던 것이 아니라, `ADR-MONO-067` 의 선택지 (A) 를 옮겨 쓰면서
`demo.env` 의 실측을 안 들고 왔다.** [[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

### 🔴🔴 정정 ② — *"부를 때마다 조회로 우회되지 않는다"* 도 **거짓이다** (2026-08-30 실측)

위 § 본문(그리고 `TASK-MONO-574` § 실측 ③)이 *"next-auth 는 `issuer` 를 설정값으로 받아
데이터 평면처럼 우회할 수 없다"* 라고 적었다. **설치된 버전을 열어 보니 그렇지 않다:**

```
index.d.ts:323  NextAuth(config: NextAuthConfig | ((request: NextRequest|undefined) => Awaitable<NextAuthConfig>))
index.js:102    if (typeof config === "function") { … await config(req) … }   ← 요청마다, 캐시 없음
```

⇒ **선택지 D 가 열린다**(축 ② 전용 해법, 축 ① 은 안 푼다). `ADR-MONO-069` § 선택지 D.
🔴 타입·구현을 읽은 것이고 **라이브 미검증**이다.

## AC-2 — "무엇이 이 결정을 무효로 만드는가"를 적는다

🔴 각 선택지에 **반증 조건**을 붙인다. 조건 없는 결정은 나중에 왜 그렇게 골랐는지 알 수 없게 된다.
예: A 는 *"IdP 주소가 부팅마다 바뀌지 않게 되면 근거가 약해진다"*.

## AC-3 — 세 앱을 **한 덩어리로 다루지 않는다**

574 가 한 앱만 쟀다면, 나머지 둘에 대해서는 **"같은 라이브러리이므로 같을 것"이 가설임을 명시**한다.
[[feedback_independent_surfaces_never_measure_their_intersection]]

## AC-4 — PROPOSED 로 남긴다

- Status: **PROPOSED**. `## Decision` 은 비워 두거나 *"소유자 지정 대기"* 로 적는다.
- 🔴 **self-ACCEPT 0.** 그 사실을 ADR 본문에 명시한다(이 저장소의 선례 형식).
- `docs/adr/INDEX.md` 에 행 추가 — ADR 인덱스 드리프트 가드가 있다.

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § D4, § AC-0 (3)
- `platform/architecture-decision-rule.md` (ADR 작성 규정)
- `projects/iam-platform/specs/features/consumer-integration-guide.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/` — `redirect_uri` 등록이 계약이면 선택지마다
  **계약 변경이 필요한지**를 적는다.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 574 가 "전부 통과" 로 나온다 | A 가 유력해지지만 **B·C 를 지우지 않는다** — 통과가 곧 최선은 아니다(부팅마다 시드 갱신 비용은 남는다) |
| 574 가 판정 불가 | ADR 을 쓰되 **"근거 미확보"** 를 명시하고 ACCEPT 를 574 재측정에 건다 |
| B 를 고르면 ADR-067 이 무너지나 | 아니다 — 067 의 *"화면은 Vercel"* 은 유지되고 **스킴 축만** 바뀐다. 그 경계를 ADR 에 정확히 적는다 |
| ADR 번호가 068 이 아니게 된다 | 착수 시점에 `docs/adr/` 을 다시 세라. 번호는 **인용되면 식별자**가 되므로 나중에 못 바꾼다 |

---

# Failure Scenarios

| 실패 | 징후 | 대응 |
|---|---|---|
| 추측으로 채운 ADR | 선택지에 실측 인용이 없다 | AC-0 위반. 574 결과 없이는 쓰지 않는다 |
| 내가 고른 것을 결정으로 적는다 | Status 가 ACCEPTED | 🔴 **정확형 게이트 위반.** 추천은 추천으로만 적는다 |
| 067 과 모순되게 쓴다 | 두 ADR 이 같은 축에 다른 말 | D4 는 067 이 **미룬** 축이다. 067 을 뒤집는 선택지라면 **SUPERSEDE 를 명시**해야 하고, 그건 더 큰 결정이다 |

---

# ✅ AC 판정 (2026-08-30) — 산출물 = [`ADR-MONO-069`](../../docs/adr/ADR-MONO-069-oidc-login-across-the-scheme-and-scope-boundary.md) **PROPOSED**

**번호를 다시 셌다** — § Goal 이 요구한 대로. `docs/adr/` 최대는 `ADR-MONO-068` 이고
`069` 는 비어 있었다 ⇒ **`069` 를 쓴다.** (`068` 은 `TASK-MONO-577` 이 가져갔다.)

## AC-0 — 574 의 결과를 읽고 시작한다 ✅

| 확인 | 값 |
|---|---|
| 574 판정 | **거짓** · 끊긴 곳 = **홉 ①** (`error=Configuration`) |
| ②③④⑤ | ⚪ **미측정** — ADR § 축 ①-b 가 *"이것으로 선택지를 지우지 않는다"* 를 명시 |
| 574 가 실제로 준 입력 | **범위 불일치**(부팅 범위 issuer vs 배포 범위 설정) ⇒ ADR 의 축 ② |
| 🙋 소유자 입력 1건 | ⏸️ **아직 없다** (Vercel Runtime Logs `[auth][error]`) |

🔵 **그 줄 없이 착수한 것은 이 AC 가 허용한 경로다** — *"그 줄이 없어도 AC-1 선택지 열거는
착수 가능하되, **AC-2 에 «① 의 실제 사유가 밝혀지면 재검토» 를 반드시 남겨라**"*.
⇒ `ADR-MONO-069` § **R1** 이 그것이고, **최상위 유보**로 올려 사유 유형별 영향까지 표로 적었다
(그중 한 칸이 *"사소한 배선 문제면 A 의 비용 계산이 통째로 바뀐다"* 이다).

## AC-1 — 선택지 최소 3개, 각각 반증 가능 ✅ (**4개**)

**A**(현상 유지+수동 env) · **B**(EC2 TLS 종단 — B1/B2 로 분기) · **C**(IdP 만 안정 이름 — C1/C2) ·
🔴 **D**(요청 시점 issuer 해석 — **이 티켓의 표에 없던 축**, § AC-1.5 정정 ② 가 근거).
각 안에 **되돌리기 비용** + **무효화 조건**(A-i~iii · B-i~iii · C-i~iv · D-i~iv)을 붙였다.

🔴 **B 는 티켓이 적은 형태로는 성립하지 않는다** — § AC-1.5 정정 ①.

## AC-1.5 — 움직이는 issuer ✅

ADR § AC-1.5 요약표가 안별로 *"사람이 부팅마다 하는 일"* 과 *"재배포가 필요한가"* 를 적는다.
🔴 그리고 **그 표는 축 ② 만 잰다**는 경고를 표 아래에 붙였다 — 축 ① 은 B·C 에서만 사라진다.

## AC-2 — 무엇이 이 결정을 무효로 만드는가 ✅

선택지별 반증 조건 **14개** + 전역 무효화 조건 **3개**(**R1** 홉 ① 실제 사유 · **R2** 쿠키 축 미측정 ·
**R3** 데모 IP 고정 결정이 067 에서 미결).
🔵 R2 가 **위험의 종류**를 갈랐다: **B·C 는 쿠키 축에 무조건 안전하고 A·D 는 노출돼 있다** —
비용표에는 안 나타나는 축이다.

## AC-3 — 세 앱을 한 덩어리로 다루지 않는다 ✅ — **그리고 전제 하나가 거짓이었다**

🔴🔴 **`console-web` 은 next-auth 를 쓰지 않는다**(`package.json` 전수 0건). 자체 route handler 로
Authorization Code + PKCE 를 직접 구현한다(`api/auth/{login,callback,logout}/route.ts` · `shared/lib/pkce.ts`).
⇒ `ADR-MONO-067` § Context 와 `TASK-MONO-574` § Context 의 *"세 앱 다 next-auth v5 OIDC"* 는
**console 에 대해 거짓**이고, fan 의 `error=Configuration`(=`@auth/core` 라벨)이 console 에서
재현되리라는 것은 **가설조차 아니다 — 다른 모집단이다.**
🔵 부수 효과로 **축 ② 의 난이도가 앱마다 다르다**는 것이 드러났다(console 은 요청 처리 중
문자열을 조립하므로 라이브러리 협조 불요).

## AC-4 — PROPOSED 로 남긴다 ✅

- `Status: PROPOSED` · `## Decision` **비어 있음**(*"소유자 지정 대기"*).
- 🔴 **self-ACCEPT 0**, 그리고 **§ 추천 절 자체를 두지 않았다** — 소유자가 자기 사전 분석을
  갖고 있다고 밝힌 상태에서 추천을 적으면 그 지정이 § The ACCEPTED Gate 케이스 ②
  (*"추천을 경유한 지정"*)와 **구별되지 않는다.**
- 🔴 **게이트가 행사된 사실을 ADR § History 에 기록했다** — 소유자가 착수 중
  *"내 사전 분석으로는 C 가 유력하다 … 단 고르지 마라"* 를 보냈고, 그것은
  **ACCEPT 가 아니다**(`ACCEPTED` 낱말 없음 + 지정이 아님을 스스로 명시).
- `docs/adr/INDEX.md` 행 추가 — `scripts/check-adr-index-drift.sh` **rc=0**
  (*"all 73 monorepo ADRs are indexed, no phantom rows, and every Status and Date matches"*).
- **rider 점검을 수행하고 결과를 적었다**: A·B·D 는 **없음**, **C 에만 하나**(apex 쿠키 공유 축).

---

## 🎯 이 티켓이 만든 잔여 — **소유자를 명시한다** (일이 사라지지 않게)

| 잔여 | 어디로 |
|---|---|
| 🙋 Vercel `kanggle-fan` Runtime Logs 의 `[auth][error]` (08-29T17:03Z 전후) | **소유자.** `ADR-MONO-069` § R1 — ACCEPT 전에 읽는 것이 **가장 싼 다음 행동**이다(기동·예산 불요) |
| 🔴 `console-web` 의 `redirect_uri` 시드가 **아무 티켓에도 없다**(`V0015`/`V0021` 은 `.local`/`localhost` 뿐) | **`TASK-MONO-585`** — 이 PR 에서 그 티켓 본문에 선행으로 적었다. 🔵 **선택지와 무관한 공통 선행**이다 |
| 홉 ②③④⑤ + 로그아웃 실측 | ⏸️ **ACCEPT 이후.** `ADR-MONO-069` § Verification 이 안별로 «무엇을 잴지» 를 적어 뒀다(V1~V7, **V5 = 부팅 2회 무개입**이 본체) |

🔴 **이 티켓은 여기서 닫는다** — § Scope 제외가 *"ACCEPT 하지 않는다 · 구현하지 않는다"* 로
못 박았고, 남은 것은 **소유자의 지정**이지 이 티켓의 일이 아니다.
