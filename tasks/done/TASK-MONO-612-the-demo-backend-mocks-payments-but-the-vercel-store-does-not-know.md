# Task ID

TASK-MONO-612

# Title

🔴🔴 데모 백엔드는 결제를 **mock 으로 승인**하는데 Vercel 스토어는 그것을 모른다 — 그리고 그 정합을 재던 가드가 **반쪽이 됐다**

# Status

done

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

---

# 📏 AC-0 실측 (2026-09-02 UTC, `TASK-MONO-610` 기동 창)

AC-0 의 세 칸 중 **둘을 쟀고 하나는 미측정**이다. AC-0 이 *"「없으니까 false 다」로
넘어가지 마라"* 라고 못 박았으므로 그 규율대로 적는다.

| # | 물음 | 실측 |
|---|---|---|
| ① | `kanggle-store` 프로덕션에 `DEMO_PAYMENT_MOCK` 이 여전히 없는가 | 🔴 **미확인** — 이 창에서 조회한 것은 `kanggle-fan` 뿐이다. store 는 안 열었다 |
| ② | **런타임 값**을 실제로 읽는다 | 🔴 **미측정** — `/api/store-config` 는 인증 게이트 뒤이고, `store.hubwang.com` 로그인은 **성립할 수 없다**(아래) |
| ③ | 데모 백엔드가 여전히 `demo-pg` 인가 | ✅ **그렇다** — `infra/demo/demo.env`: `ECOMMERCE_PAYMENT_PROFILES=demo-pg` · `DEMO_PAYMENT_MOCK=1` |

## 🔴 ② 가 이 창에서 원리적으로 닫힐 수 없었던 이유

`store.hubwang.com` 로그인에는 **두 선행**이 다 필요하고 둘 다 미충족이었다:

1. **`V0035` 가 데모 호스트의 IdP 에 적용돼 있어야 한다.** 실측 — 그 호스트의 flyway
   최대 version 은 **`0033`** 이고, 마이그레이션은 저장소 파일이 아니라 **`auth-service`
   이미지 안**(`/app/BOOT-INF/classes/db/migration/`)에 있다(이미지 생성 2026-08-29T14:37Z).
   ⇒ 저장소를 갱신해도 안 들어간다. **이미지 재빌드가 선행**이고, 소유자 지정으로
   이번 창의 범위 밖이었다.
2. **소유자의 Vercel env 다섯 줄**(`TASK-MONO-610` AC-4b) — 넣지 않았다.

⇒ AC-0 ②는 **「미측정」으로 남긴다.** 🔵 AC-0 이 미리 허용한 세 번째 갈래(⑶)가 이것이다.

## 🔵 AC-3(팬 축)에 붙일 사실 하나

같은 창에서 `kanggle-fan` 프로덕션 env 를 전수 조회했다 —
`OIDC_CLIENT_ID` · `OIDC_CLIENT_SECRET` · `NEXTAUTH_URL` · `NEXTAUTH_SECRET` **넷뿐**이고
`DEMO_PAYMENT_MOCK` 은 **없다**. ⇒ `demo.env:279` 가 적은 *"한 값, 두 프런트"* 는
**Vercel 쪽에서는 아직 한 프런트도 그 값을 안 받는다**는 뜻이다.
🔴 이 줄은 「팬도 틀렸다」가 아니라 **「팬 축도 같은 모양으로 반쪽이 될 것」** 이라는
AC-3 의 예측이 실측으로 확인됐다는 뜻이다.

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

---

# 🔴 AC-0 ② 재측정 (2026-09-03 UTC) — `TASK-MONO-616` 기동 창 #3 · **여전히 미측정, 그러나 사유가 바뀌었다**

`TASK-MONO-616` 이 이 칸을 매니페스트 **칸 5** 로 들고 창에 들어갔다. 결과는 09-02 와 같은
**미측정**이지만, **막고 있는 것이 다르다.** 그 차이를 적어 둔다 — 사유가 바뀌었는데
같은 문장을 두면 다음 사람이 낡은 원인을 고치러 간다.

| | 09-02 (창 #1) | **09-03 (창 #3)** |
|---|---|---|
| `store.hubwang.com/api/auth/providers` | **500** / 108 B | 🟢 **200** / 171 B |
| `/api/auth/csrf` | **500** / 80 B | 🟢 **200** / 80 B |
| 막는 것 | *"next-auth 가 통째로 500 — 아무도 로그인을 **시작조차** 못 한다"* | 🔴 **홉①이 authorize URL 을 못 만든다** → `/login?error=Configuration` |
| 귀속 | auth env 가 **0개** | 🔵 `NEXTAUTH_URL`·`NEXTAUTH_SECRET` 은 **들어갔다**. `OIDC_ISSUER_URL` 이 **아직** |
| `/api/store-config` | 307 → `/login` | **307 → `/login?from=%2Fapi%2Fstore-config`** (게이트 그대로) |

⇒ **AC-0 ② = ⑶ 미측정(사유: 로그인이 discovery 단계에서 끊긴다).**
🔴 「없으니까 false 다」로 넘어가지 않는다 — 이번에도.

## 🔵 한 칸 전진했다 — 그리고 그것이 이 티켓의 **순서**를 확정한다

09-02 에는 *"로그인이 살아나는 순간 이 어긋남이 바로 그날 보이기 시작한다"* 라고만 적을 수
있었다. 이제 로그인까지 남은 것이 **정확히 한 줄**(`TASK-MONO-610` AC-4b 의 1번 =
`OIDC_ISSUER_URL`)로 좁혀졌다.

```
① OIDC_ISSUER_URL 투입  →  ② store 경로를 건드리는 배포(ignoreCommand 때문)
   →  ③ 기동 창에서 로그인  →  ④ /api/store-config 의 demoPayment 를 **읽는다**
```

🔴 **PASS 의 정의를 미리 못박아 둔다**(`TASK-MONO-616` 매니페스트가 고정한 것):
이 칸의 PASS 는 **값이 `true` 인 것이 아니라 「런타임 값을 읽었다」 쪽이다.** `false` 가 나와도
칸은 닫히고, 그 `false` 는 **AC-1(소유자 지정)의 입력**이지 이 칸의 실패가 아니다.
🔵 이 구별이 없으면 소유자 대기 항목이 다음 창에서 「FAIL」로 오기록된다.

## 🔵 AC-3(팬 축)에 붙는 사실 하나

이번 창에서 `web.fan-platform.<도메인>` 은 **307** 이었다 — 팬은 **여전히 데모가 서빙한다**.
⇒ (x2) 가 반쪽이 되는 시점은 아직 오지 않았고, 그 일은 `ADR-MONO-067` **단계 4** 의 몫이다.
🔴 그 단계의 주인이 `TASK-MONO-586`(frozen) 인지 새 티켓인지는 **아직 정해지지 않았다** —
`TASK-MONO-616` § AC-3 이 그 공백을 이름으로 적어 뒀다.

---

# 🔵 AC-0 ② 후속 (2026-09-03 UTC) — **선행 사슬의 ②가 닫혔다.** 이 칸은 여전히 ③ 대기다

이 티켓이 적은 순서 `① OIDC_ISSUER_URL 투입 → ② store 경로 배포 → ③ 기동 창 로그인 →
④ /api/store-config 읽기` 에서 **①②가 끝났다**(소유자 투입 + 새 배포 승격 확인).

판별은 Vercel 이 정적 자산 URL 에 붙이는 **배포 id** 로 했다 —
`dpl_HLV6QzMSPd1uQnQ8zfqyq4zTuFMN`(창 전·창 안 동일) → `dpl_FLxzzBt8B7VxEtshaXXSYdWLZg9a`(투입 뒤).
🔴 정적 청크 해시는 이 축을 **판별하지 못한다**(`OIDC_ISSUER_URL` 은 `NEXT_PUBLIC_` 이
아니라 클라 번들에 안 들어간다) — 상세는 `TASK-MONO-610` § AC-4b 후속.

🔴 **그래도 이 칸은 여전히 「미측정」이다.** `/api/store-config` 는 인증 게이트 뒤이고
(실측 유지: `307 → /login?from=%2Fapi%2Fstore-config`), 로그인은 **데모가 켜져야** 성립한다.
⇒ **④는 ③ 없이 오지 않는다.**

🔵 **PASS 의 정의는 그대로다** — 이 칸의 PASS 는 값이 `true` 인 것이 아니라
「런타임 값을 읽었다」 쪽이다. `false` 가 나와도 칸은 닫히고, 그 값이 AC-1 의 입력이 된다.

---

# 🟢 AC-1 지정됨 · AC-0 ① 값 갱신 (2026-09-03 UTC) — **소유자 확인**

## AC-0 ① — 이 티켓의 09-02 실측이 **낡았다**

| 시점 | `kanggle-store` 프로덕션 env 의 `DEMO_PAYMENT_MOCK` | 출처 |
|---|---|---|
| 2026-09-02 | 🔴 **없다** (env 는 `DEMO_API_BASE` 하나뿐) | 이 티켓 § AC-0 실측 (`vercel env ls`) |
| **2026-09-03** | 🟢 **있다 — `1`** | 🔵 **소유자가 대시보드에서 확인** |

🔴 **출처를 명시해 둔다: 이것은 내가 잰 값이 아니라 소유자가 보고한 값이다.**
저장소는 Vercel env 를 읽을 수 없고(그 사실이 AC-2 가 「선택지 2」를 고른 이유다),
그래서 이 행은 **보유자의 진술**이지 측정이 아니다. [[feedback_the_reporter_is_not_the_holder]]

🔵 **키 이름은 소스로 확인했다** — `web-store/src/app/api/store-config/route.ts:22` 가
`process.env.DEMO_PAYMENT_MOCK === '1'` 이다. 팬과 **같은 이름**이고(한 값, 두 프런트 —
`demo.env:279`) **극성만 반대**다. 🔴 형제 이름을 베껴 적는 함정을 피하려고 확인했다.

## AC-1 — 🟢 **지정됐다.** 방향은 「프런트를 백엔드에 맞춘다」

이 티켓이 권장으로 적은 방향 그대로다 — 데모 백엔드가 `demo-pg`(mock)이고 **데모는 돈을
받지 않으므로** 반대 방향(백엔드에서 `demo-pg` 끄기 = 실 Toss 키 요구)은 택하지 않았다.

⇒ **AC-1 은 「🙋 소유자 지정 대기」가 아니다.** 남은 것은 **그 값이 런타임에 읽히는지**뿐이고,
그것이 AC-0 ② 다.

## 🔴 그래도 AC-0 ②는 **여전히 미측정** — 선언은 런타임이 아니다

`/api/store-config` 는 인증 게이트 뒤 그대로다(재확인: `307 → /login?from=%2Fapi%2Fstore-config`).
⇒ 익명으로는 못 읽고, 로그인은 **데모가 켜져야** 성립한다.
[[feedback_declaration_files_are_not_the_runtime_state]]

🔵 **다만 배포에 실렸을 가능성은 높다**(추론, 판정 아님): 오늘 창 **전**(17:06)에 이미
`store.hubwang.com/api/auth/*` 가 **200** 이었으므로 `NEXTAUTH_*` 는 그때 배포
(`dpl_HLV6QzMS…`)에 들어 있었고, `DEMO_PAYMENT_MOCK` 이 같은 배치였다면 그 배포에도,
그 뒤 빌드된 `dpl_FLxzzBt8…` 에도 실려 있다. 🔴 **추론을 판정으로 승격시키지 않는다** —
이 티켓의 AC-0 이 애초에 *"추론을 측정으로 바꾼다"* 였다.

## ⇒ 다음 기동 창에서 **한 번에 셋이 닫힌다**

| 무엇 | 기대 |
|---|---|
| `TASK-MONO-610` AC-4b store | 로그인 왕복이 세션까지 |
| **AC-0 ②** | `/api/store-config` 가 값을 낸다 |
| **AC-1 검증** | 그 값이 `{"demoPayment":true}` |

🔴 **PASS 의 정의는 안 바뀐다** — AC-0 ② 의 PASS 는 값이 `true` 인 것이 아니라
「런타임 값을 읽었다」 쪽이다. `false` 가 나오면 AC-0 ②는 **PASS 이고 AC-1 이 다시 열린다**
(그때는 「env 는 있는데 배포에 안 실렸다」가 되고, 처방은 store 경로 배포 하나다).

## 📌 남은 것 (갱신)

| AC | 상태 |
|---|---|
| AC-0 ①③ | ✅ ① **값 갱신됨**(있다) · ③ `demo-pg` 유지 |
| AC-0 ② | ⚪ **미측정** — 기동 창 |
| AC-1 | 🟢 **지정됨** — 검증만 남음(위 창에서 AC-0 ②와 같이) |
| AC-2 · AC-3 | ✅ |

---

# 🟢 AC-0 ② · AC-1 검증 — **둘 다 닫혔다** (기동 창 #4, 2026-09-04 UTC, `TASK-MONO-621` 칸 2·3)

```
로그인 왕복(칸 1) → 세션 쿠키
GET https://store.hubwang.com/api/store-config      200 / 20 B
  {"demoPayment":true}
```

| AC | 판정 | 근거 |
|---|---|---|
| **AC-0 ②** | 🟢 **PASS** | **런타임 값을 읽었다.** 이 티켓이 못박은 대로 PASS 의 정의는 「값이 `true`」가 아니라 「읽었다」이고, 그것이 충족됐다 |
| **AC-1 검증** | 🟢 **PASS** | 그 값이 **`true`** ⇒ 지정된 방향(「프런트를 백엔드에 맞춘다」)이 **런타임에 발효돼 있다** |

🔵 **음성 대조군을 같은 창에서 다시 쟀다** — 익명 요청은 여전히
`307 → /login?from=%2Fapi%2Fstore-config`. ⇒ 200 은 **게이트를 넘어서 받은 값**이지
게이트가 열려 버린 것이 아니다. (이 구별이 없으면 200 이 «인증이 깨졌다» 와 섞인다.)

🔵 **선행이 실제로 선행이었다** — 칸 1(`610` AC-4b store)이 FAIL 이었으면 이 칸은
「미측정(사유: 로그인 불가)」으로 닫혔을 것이다. 매니페스트가 그 순서를 미리 고정했고,
칸 1 이 PASS 라서 여기까지 왔다.

## 📌 남은 것 — **없다**

| AC | 상태 |
|---|---|
| AC-0 ①③ | ✅ |
| **AC-0 ②** | 🟢 **닫힘** (창 #4) |
| **AC-1** | 🟢 **지정 + 런타임 검증 완료** (창 #4) |
| AC-2 · AC-3 | ✅ |

⇒ 🔵 **이 티켓은 `review/` 로 올릴 수 있다.** 🔴 다만 그 이동은 이 창의 산출물이 아니라
**별도 lifecycle PR** 이다(`tasks/INDEX.md` § PR Separation Rule).
