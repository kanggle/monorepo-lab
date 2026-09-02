# Task ID

TASK-MONO-612

# Title

🔴🔴 데모 백엔드는 결제를 **mock 으로 승인**하는데 Vercel 스토어는 그것을 모른다 — 그리고 그 정합을 재던 가드가 **반쪽이 됐다**

# Status

in-progress

# Owner

monorepo

# Task Tags

- demo
- ci
- adr

---

# 🔎 어디서 왔나 — **가드가 무관한 변경에서 물었다**

`TASK-MONO-604` 가 데모에서 `web-store` 를 억제하자 `verify-demo-wrapper.sh` 의
가드 **(x)** 가 빨개졌다: *"(x) ecommerce 렌더에서 web-store 의 DEMO_PAYMENT_MOCK 를
찾지 못했습니다"*.

🔵 **가드는 옳았다.** (x) 가 지키던 불변식은 이것이다 — 데모 결제는 **두 곳이 동의**해야
성립한다:

| 절반 | 어디 | 값 |
|---|---|---|
| 백엔드 | `payment-service` `SPRING_PROFILES_ACTIVE` | **`demo-pg`** (mock PG 가 모든 결제를 승인) |
| 프런트 | `web-store` `DEMO_PAYMENT_MOCK` | **`1`** (체크아웃이 Toss SDK 를 건너뛴다) |

🔴🔴 **그런데 프런트 절반이 저장소 밖으로 이사 갔다.** 방문자 스토어는 Vercel
(`kanggle-store`)이고, 그 프로세스의 env 는 compose 가 아니라 **Vercel 프로젝트 env** 다.
저장소는 그 값을 렌더할 수 없다 ⇒ **이 축은 CI 에서 미집행**이다.

---

# 🔴 그리고 지금 **한쪽만 켜져 있을 가능성이 높다**

2026-09-01 실측 — `kanggle-store` 프로덕션 env 의 **키 이름 전수**:

```
OIDC_ISSUER_URL   (TASK-MONO-611 이 삭제)      DEMO_API_BASE
```

**`DEMO_PAYMENT_MOCK` 이 없다.** 그리고 코드는
`apps/web-store/src/app/api/store-config/route.ts:22` 에서
`demoPayment: process.env.DEMO_PAYMENT_MOCK === '1'` 을 읽는다 ⇒ Vercel 에서는 `false`.

한편 데모 백엔드는 `infra/demo/demo.env:276` 이 `ECOMMERCE_PAYMENT_PROFILES=demo-pg` 다.

⇒ **백엔드만 켜진 조합**이고, 가드 (x) 가 그 조합의 증상을 이미 문장으로 적어 뒀다:
*"프런트가 더미 키로 Toss SDK 를 로드하다 실패 배너를 띄운다."*

## 🔴 다만 이것은 **선언 기반 추론이지 라이브 측정이 아니다**

라이브로 확인하려 했으나 `https://store.hubwang.com/api/store-config` 는
**`307 → /login`** 이다(인증 게이트). 즉 익명으로는 `demoPayment` 를 못 읽는다.
[[feedback_declaration_files_are_not_the_runtime_state]]

**AC-0 이 그 간극을 닫는 것부터 시작한다.** 「env 목록에 없다」와 「런타임에 false 다」는
다른 문장이고, 이 티켓은 그 둘을 섞지 않는다.

---

# Goal

**데모 결제 mock 의 두 절반을 다시 한 축에서 판정 가능하게 만든다** — 그리고 지금
어긋나 있다면 맞춘다.

---

# Scope

**In**

- Vercel `kanggle-store` 의 `DEMO_PAYMENT_MOCK` 실태 확인 및 처분
- 그 축을 **무엇이 지키는가** 를 정한다(가드·문서·둘 다 아님 중 택일하고 **이유를 적는다**)
- 형제 축 점검: `fan-platform-web` 도 같은 값을 읽는다(`demo.env:279` 가 명시) — 단계 4
  (`TASK-MONO-586`)가 팬을 옮기면 **같은 구멍이 한 번 더** 생긴다

**Out**

- 데모에서 `web-store` 를 되살리는 것 — `TASK-MONO-604` 의 결정이다
- Toss/PortOne 실결제 배선 — 이 티켓은 **mock 정합**만 본다

---

# Acceptance Criteria

## AC-0 — 🔴 **추론을 측정으로 바꾼다** (착수 전)

1. `kanggle-store` 프로덕션 env 에 `DEMO_PAYMENT_MOCK` 이 **여전히 없는가** (`vercel env ls`).
2. 🔴 **런타임 값을 실제로 읽는다.** `/api/store-config` 는 인증 게이트 뒤이므로,
   ⑴ 로그인해서 읽거나 ⑵ 체크아웃 화면의 관측 가능한 결과로 판정하거나
   ⑶ 그것도 안 되면 **「미측정」이라고 적는다** — 「없으니까 false 다」로 넘어가지 마라.
3. 데모 백엔드가 여전히 `demo-pg` 인가 (`infra/demo/demo.env`).

## AC-1 — 두 절반을 맞춘다

- 어긋나 있으면 **어느 쪽으로** 맞출지 정하고 이유를 적는다.
  🔵 기본 방향은 «프런트를 백엔드에 맞춘다»(Vercel 에 `DEMO_PAYMENT_MOCK=1`) — 데모는
  돈을 받지 않는다. 🔴 그러나 이건 **소유자의 Vercel 설정**이므로 지정을 받아야 한다.

## AC-2 — 🔴 **미집행 축에 주인을 붙인다**

지금 `verify-demo-wrapper.sh` 의 (x) 는 프런트 절반이 없으면 **백엔드만 재고**,
매 실행마다 *"이 축은 CI 에서 미집행이다. 소유 티켓: TASK-MONO-612"* 를 찍는다.
이 티켓이 닫힐 때 그 문구가 **거짓이 되지 않게** 하라 — 셋 중 하나다:

1. 저장소가 Vercel env 를 읽어 판정하는 가드를 만든다(토큰 필요 — CI 비용·보안 판단)
2. 판정 불가를 **명시적으로 수용**하고 (x) 의 문구를 「누가·언제 손으로 확인하는가」로 바꾼다
3. 프런트 절반을 저장소가 볼 수 있는 곳으로 되돌린다(예: 빌드 타임 고정)

🔴 **아무것도 안 하고 (x) 의 문구만 지우는 것은 금지다** — 그러면 공백이 조용해진다.

## AC-3 — 팬 축을 **미리** 센다

`demo.env:279` 는 *"`DEMO_PAYMENT_MOCK` 는 팬 웹도 함께 읽는다 — 한 값, 두 프런트"* 라고
적는다. `TASK-MONO-586`(단계 4)이 팬을 Vercel 로 옮기면 (x2) 가 **같은 방식으로 반쪽이
된다.** 그 사실을 586 에 남겨라(그 티켓은 `review/` 이므로 `## CORRECTION` 순수 추가).

---

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) § 단계 2
- `infra/demo/verify-demo-wrapper.sh` § 가드 (x) · (x2)
- `tasks/ready/TASK-MONO-604-…md` — 이 티켓을 낳은 변경
- `projects/ecommerce-microservices-platform/apps/web-store/VERCEL.md` — 그 프로젝트 env 의 원장

# Related Contracts

없음.

# Edge Cases

- **소유자가 그 사이 값을 넣는다** → AC-0 ①이 잡는다. 그러면 남는 것은 AC-2(주인 붙이기)뿐이다.
- **데모가 `demo-pg` 를 끈다** → 정합의 방향이 뒤집힌다. AC-0 ③이 그래서 있다.
- **`/api/store-config` 가 계속 인증 뒤에 있다** → AC-0 ②의 ⑶ 로 간다. **미측정을
  측정으로 위장하지 마라.**

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| env 목록만 보고 「false 다」로 확정 | 선언과 런타임을 섞는다 | AC-0 ② |
| (x) 의 문구만 지우고 닫는다 | 공백이 조용해진다 | AC-2 마지막 줄 |
| 팬을 잊는다 | 단계 4 에서 같은 구멍이 반복된다 | AC-3 |

---

# 📏 AC-0 — 측정 결과 (2026-09-02 UTC) ✅

세 질문 중 **둘은 값으로 닫혔고, 하나는 「미측정」으로 닫힌다** — 그리고 그 「미측정」은
추정이 아니라 **왜 못 재는지를 잰** 결과다.

## ① `kanggle-store` 프로덕션 env — 🔴 **여전히 없다**

```
$ vercel env ls production --project kanggle-store
 name             type    environments          created
 DEMO_API_BASE    Config  Production, Preview   4d ago
```

키 **전수 1개**. `DEMO_PAYMENT_MOCK` 없음. (`OIDC_ISSUER_URL` 은 `TASK-MONO-611` 이 지운
뒤 그대로다 — 2개 → 1개.)

🔵 **두 번째 선언 경로도 없다**: 같은 앱의 `vercel.json` · `next.config.*` · `package.json`
을 grep 해 `DEMO_PAYMENT_MOCK` **0건** ⇒ *"빌드 타임에 다른 출처가 주입해서 이 목록이
거짓"* 인 경우는 배제됐다. [[feedback_declaration_files_are_not_the_runtime_state]]

## ② 런타임 값 — 🔴 **⑶ 「미측정」.** 그리고 그 이유는 추정이 아니다

⑴(로그인해서 읽기)과 ⑵(체크아웃 화면의 관측 가능한 결과) 둘 다 **불가능하다**. 이유는
`/api/store-config` 의 307 이 아니라 **그보다 한 겹 위**에 있었다 — 라이브 스토어의
`next-auth` 가 통째로 **500** 이다. 즉 이 사이트에서는 **아무도 로그인을 시작조차 못 한다.**

| 찌른 곳 | 🔵 `kanggle-fan` (양성 대조군) | 🔴 `kanggle-store` (대상) |
|---|---|---|
| `/api/auth/providers` | **200** · 167 B · `iam` 를 나열 | **500** · 108 B · *"problem with the server configuration"* |
| `/api/auth/csrf` | **200** · 80 B · 토큰 발급 | **500** · 108 B |
| `/` (음성 대조군 — 앱 자체는 사나) | 307 → `/login` | **200** · 34,963 B |
| 프로젝트 auth env | `NEXTAUTH_SECRET` · `NEXTAUTH_URL` · `OIDC_CLIENT_ID` · `OIDC_CLIENT_SECRET` (**4개**, 7d ago) | **0개** |

🔵 **양성 대조군이 있다는 것이 이 표의 값어치다** — 같은 코드베이스 계열의 형제가 같은
순간 200 을 낸다 ⇒ *"next-auth 가 원래 이렇다"* 도 *"네트워크가 이상하다"* 도 아니다.
🔴 **그러나 단일 변수 귀속은 하지 않는다**: 두 팔 사이에 변수가 **네 개** 다르다. 여기서
말할 수 있는 것은 *"`kanggle-store` 의 auth env 가 통째로 비어 있고 그 프로젝트의
`/api/auth/*` 가 전부 500 이다"* 까지이지 *"`NEXTAUTH_SECRET` 하나가 원인이다"* 가 아니다.
[[feedback_control_group_design_four_axes]] [[feedback_a_reported_figure_must_name_what_was_measured]]

**⇒ AC-0 ② 의 답: 미측정.** 「없으니까 false 다」로 넘어가지 않는다.

### 🔵 그리고 이 측정이 **AC-1 의 크기를 바꾼다**

가드 (x) 가 적어 둔 「백엔드만 켜짐」의 증상은 *"프런트가 더미 키로 Toss SDK 를 로드하다
실패 배너를 띄운다"* 다. 그런데 **오늘 그 배너를 볼 수 있는 사람이 0명이다** — 체크아웃은
게이트 뒤에 있고 게이트를 넘는 유일한 길인 로그인이 500 이다. 즉 이 어긋남은 실재하지만
**현재 사용자에게 보이지 않는다**. 🔴 **「고칠 필요가 없다」가 아니라 「순서가 있다」는
뜻이다**: 로그인이 살아나는 순간 이 어긋남이 **바로 그날** 보이기 시작한다.

## ③ 데모 백엔드 — ✅ **여전히 `demo-pg`**

`infra/demo/demo.env:276` = `ECOMMERCE_PAYMENT_PROFILES=demo-pg`,
`:277` = `DEMO_PAYMENT_MOCK=1`(compose 가 렌더하는 쪽). ⇒ 정합의 방향은 뒤집히지 않았다.

---

# 🔴🔴 AC-0 이 **범위 밖의 것**을 하나 잡았다 — 그리고 그건 조용한 공백이었다

`kanggle-store` 의 auth env **0개**는 이 티켓의 대상이 아니지만, **어느 티켓의 대상도
아니었다**:

- `TASK-MONO-582`(프로젝트 생성)는 `NEXTAUTH_SECRET` 을 **「빌드를 죽이나」** 로만 봤고,
  *"✅ 빌드를 안 죽였다"* 로 닫았다. 그 판정은 **거짓이 아니다** — 잰 것이 빌드였다.
  런타임은 그 문장의 사정거리 밖이었다.
- `TASK-MONO-610` AC-4b 는 두 프로젝트의 **`OIDC_ISSUER_URL`** 만 이름으로 든다.
- `TASK-MONO-611` 은 store 의 **죽은 issuer 를 지웠고**, 그 문서는 *"오늘 동작 차이는 0"*
  이라 적었다 — 맞다, 이미 500 이었으니까.

⇒ **`kanggle-store` 에 issuer 만 꽂으면 로그인은 여전히 500 이다.** 이 사실을 610 의
AC-4b 에 실측과 함께 넣었다(그 티켓은 `in-progress` 라 본문 수정이 정당하다).
🔴 **여기서 값을 넣지 않는다** — 소유자의 Vercel 설정이고, `VERCEL.md` 가 *"넣은 값과 그
이유를 여기에 적는다"* 를 요구한다. [[feedback_a_partial_deletion_reads_as_a_total_one]]

---

# ✅ AC-2 — 미집행 축에 주인을 붙였다 (**선택지 2**)

세 선택지 중 **2(판정 불가를 명시적으로 수용하고 문구를 「누가·언제」로 바꾼다)** 를
골랐다. 나머지 둘을 왜 안 골랐는지가 이 선택의 절반이다.

| 선택지 | 판정 | 이유 |
|---|---|---|
| 1 — 저장소가 Vercel env 를 읽는 가드 | ❌ 지금은 아니다 | 읽기 토큰 **생성이 소유자 몫**이고 CI 에 상주시키는 것은 보안 판단이다. 🔴 게다가 그 가드가 재는 것은 **여전히 선언**이다 — `vercel env ls` 는 값의 존재를 말하지 *그 값을 든 함수가 지금 도는지*는 말하지 않는다(env 변경은 **다음 배포부터** 적용된다). 비용을 치르고도 AC-0 ②의 간극은 안 닫힌다 |
| 2 — 판정 불가 수용 + 문구를 프로토콜로 | ✅ **채택** | 저장소가 **오늘 실제로 할 수 있는 유일한 것**이고, 「누가·언제·무슨 명령·기대값·원장」을 다 적으면 공백이 조용해지지 않는다 |
| 3 — 프런트 절반을 저장소가 보는 곳으로 | ⏸️ **보류(폐기 아님)** | 기술적으로 가능하고 매력적이다 — `DEMO_API_BASE` 가 이미 *"이 프로세스는 Vercel 데모다"* 의 **런타임 표지**이므로(`(store)/layout.tsx:12-14`) `demoPayment` 를 거기서 **유도**할 수 있고, 그러면 Vercel env 자체가 불필요해진다. 🔴 **그러나 그것은 AC-1 의 방향을 코드로 선점한다**(「데모면 무조건 mock」). AC-1 이 소유자 지정 사안이라고 적힌 이상 여기서 코드로 결정하지 않는다 ⇒ **AC-1 이 닫힌 뒤 다시 꺼낸다** |

## 무엇을 바꿨나

`infra/demo/verify-demo-wrapper.sh` 의 (x) 미집행 분기 문구가 이제 **실행 가능한
프로토콜**을 든다 — 누가·언제·정확한 명령 한 줄·기대값·원장 경로.

🔴 **날짜 박힌 실측값은 일부러 안 넣었다.** 그 줄은 소유자가 값을 넣는 순간 거짓이 되고,
**아무것도 그것을 빨갛게 만들지 못한다**. 게이트 없는 숫자는 반드시 낡는다 ⇒ 실측값은
**날짜와 함께 원장(`VERCEL.md`)과 이 티켓**에 두고, 스크립트에는 *"이 명령으로 다시
재라"* 만 남긴다. [[feedback_a_figure_nothing_can_fail_on_will_drift]]

## 🔴 이 선택이 **인정하는 것**

선택지 2 는 이 축을 **자동 판정으로 되돌리지 않는다**. 다음 사람이 이 결정을 뒤집고
싶다면 그 자리는 **3번**이고, 그 문이 열리는 조건은 **AC-1 이 닫히는 것**이다.

---

# 🙋 AC-1 — **소유자 지정 대기** (열려 있음)

측정은 끝났다: **백엔드만 켜져 있다.** 방향만 남았고 그건 Vercel 설정이다.

| | |
|---|---|
| 🔵 권장 | **프런트를 백엔드에 맞춘다** — `kanggle-store` 프로덕션에 `DEMO_PAYMENT_MOCK=1` |
| 명령 | `vercel env add DEMO_PAYMENT_MOCK production --project kanggle-store` (값 `1`) |
| 🔴 적용 시점 | **다음 배포부터**다. env 를 넣는 것만으로 현재 살아 있는 배포는 안 바뀐다 |
| 🔴 반대 방향 | 데모 백엔드에서 `demo-pg` 를 끄는 것 — 그러면 데모가 **실 Toss 키**를 요구한다. 데모는 돈을 받지 않으므로 권장하지 않는다 |
| 되돌리기 | `vercel env rm DEMO_PAYMENT_MOCK production --project kanggle-store` |

🔴 **지정 뒤에도 「닫혔다」는 env 목록이 아니라 `/api/store-config` 가 `{"demoPayment":true}`
를 내는 것으로 판정한다** — 그리고 그건 **로그인이 살아난 뒤에야** 가능하다(AC-0 ②).
⇒ 실질적으로 **`TASK-MONO-610` 의 기동 창에 얹힌다.**

---

# ✅ AC-3 — 팬 축을 미리 셌다

`TASK-MONO-586`(review, frozen)에 `## CORRECTION` 순수 추가로 남겼다. 🔴 거기서 발견한
것 하나: **(x2) 는 (x) 의 복사본이 아니다 — 술어가 반대다**(`demo.env:279-285`).
ecommerce 는 「`demo-pg` 를 **켜야** mock」이고 fan 은 「`portone` 을 **켜야** 실 PG」다.
⇒ 586 이 팬을 옮길 때 (x) 의 처방을 그대로 옮기면 **정반대를 단언**하게 된다.

---

# 📌 남은 것

| AC | 상태 |
|---|---|
| AC-0 | ✅ ①③ 값으로 닫힘 · ② **미측정으로 닫힘**(이유를 측정) |
| AC-1 | 🙋 **소유자 지정 대기** — 그리고 판정은 610 의 기동 창에 얹힌다 |
| AC-2 | ✅ 선택지 2 채택 + 가드 문구 교체 |
| AC-3 | ✅ 586 에 CORRECTION |

⇒ 이 티켓은 **`in-progress` 에 남는다**. `review/` 는 frozen 이라, 남은 AC-1 을
CORRECTION 으로만 적게 되고 그러면 「위를 먼저 읽는 사람」이 닫힌 티켓으로 오해한다.
