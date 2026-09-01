# Task ID

TASK-MONO-612

# Title

🔴🔴 데모 백엔드는 결제를 **mock 으로 승인**하는데 Vercel 스토어는 그것을 모른다 — 그리고 그 정합을 재던 가드가 **반쪽이 됐다**

# Status

ready

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
