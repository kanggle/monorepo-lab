# Task ID

TASK-FAN-FE-020

# Title

`/login` 이 실패의 **원인을 이름댄다** — 「잠시 후 다시 시도」 한 문장이 데모 꺼짐을 앱 고장으로 읽히게 한다

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

`fan.hubwang.com` 방문자가 로그인에 실패했을 때, **그 실패가 무엇 때문인지 화면이 말하도록** 한다.

현재 `/login` 은 Auth.js 가 돌려준 `error` 코드가 **무엇이든** 한 문장만 낸다 —
*"로그인에 실패했습니다. 잠시 후 다시 시도해주세요."* 그 문장은 **재시도가 고친다**고
약속하는데, 실제로 가장 흔한 원인(데모 백엔드 꺼짐)에서는 **재시도가 절대 안 고친다.**

이 티켓이 닫는 구멍은 **이미 알려져 있었다.** `DemoBackendNotice`(fan) 의 JSDoc 이
그 자리를 이렇게 적어 뒀다:

> 🔴 **`/login` 은 이 위젯이 덮지 않는다 — 알고 비워 둔 자리다.** …로그인 실패는
> **다른 증상**이라 다른 처방이 필요하다(그쪽은 `TASK-MONO-574` 의 왕복 측정이 먼저다).
> 여기서 조용히 같이 처리한 척하면 그 구멍이 안 보이게 된다.

선행 조건이었던 왕복 측정은 끝났다(`TASK-MONO-610` AC-4b — fan 왕복이 기동 창 #3 에서
`roles=["FAN"]` 로 세션까지 도달). **그러므로 지금이 그 「다른 처방」을 만들 차례다.**

🔴 **이 티켓은 배너를 `/login` 에 복사하는 일이 아니다.** `(main)` 배너의 문구
(*"지금 보이는 피드와 아티스트는 샘플 데이터입니다"*)는 `/login` 에서 **거짓**이다 —
로그인 화면에는 샘플 피드가 없다. 형제 위젯이 「다른 처방」이라고 쓴 이유가 그것이다.

---

# Scope

## In Scope

- `src/app/(auth)/login/page.tsx`
  - Auth.js `error` 코드 → 문구 매핑(+ 알 수 없는 코드용 fallback)
  - 데모 백엔드가 `unavailable` 일 때 **그 사실을 이름대는** 로그인 전용 문구
- 위 두 가지의 단위 테스트

## Out of Scope

- **로그인 버튼 비활성화** — 방문자가 방금 데모를 켰을 수 있고 판정은 캐시된 값이다.
  막는 것과 말해 주는 것은 다른 결정이고, 이 티켓은 후자만 한다.
- `(main)` 배너 문구 변경 (`TASK-MONO-642` 가 정한 그대로 둔다)
- Auth.js 설정·`OIDC_ISSUER_URL`·시크릿 투입 — **결함이 아님이 실측됐다**(AC-0)
- 콘솔 쪽 재로그인 루프 — 별도 티켓(`TASK-PC-FE-278`)

---

# Acceptance Criteria

- [x] **AC-0 (실측 고정)** — 이 티켓의 전제가 실측이고 추론이 아님을 본문에 남긴다.
      2026-09-10 UTC 라이브 측정:
      `fan.hubwang.com/api/auth/{providers,session,csrf}` = **200 / 200 / 200**,
      `/api/auth/signin/iam` = **302 → `/login?error=Configuration`**,
      `auth.hubwang.com/.well-known/openid-configuration` = **503**(본문 =
      *"데모 백엔드가 지금 꺼져 있습니다"*), 데모 컨트롤 플레인 `/status` = `state: stopped`.
      🔴 **2026-08-26 의 「`NEXTAUTH_SECRET` 미투입」 뿌리가 아니다** — 그때는
      `providers`/`session`/`csrf` 가 **셋 다 500** 이었고 지금은 셋 다 200 이다.
      ⇒ 시크릿은 들어가 있고, 깨진 것은 **discovery 한 다리**뿐이다.
- [x] **AC-1** — `error` 코드별 문구가 존재하고, **알 수 없는 코드도 조용하지 않다**
      (fallback 이 렌더된다). 테스트가 코드→문구를 직접 단언한다.
- [x] **AC-2** — `resolveDemoBackendState()` 가 `'unavailable'` 이면 `/login` 은
      **데모가 꺼져 있다고 말한다**, `error` 파라미터 유무와 무관하게.
- [x] **AC-3** — `'unavailable'` 일 때 generic 「잠시 후 다시 시도」 문장은 **동시에
      렌더되지 않는다**. 🔴 두 문장이 같이 뜨면 화면이 스스로 모순된다 — 하나는
      *"기다리면 된다"*, 다른 하나는 *"켜야 한다"* 이다.
- [x] **AC-4** — `'not-demo'`(로컬 개발·CI)에서는 데모 문구가 **렌더되지 않는다**.
      거기서 "데모가 꺼졌다"는 거짓이다 — 형제 위젯이 이미 같은 규칙을 쓴다.
- [x] **AC-5** — 게이트 3종이 **각각 독립 statement + 명시 `rc=$?`** 로 초록:
      `next lint` · `tsc --noEmit` · `vitest run`. 앱 코드가 바뀌므로 `next build` 추가.
      🔴 파이프 금지(`cmd | tail` 은 `tail` 의 종료코드를 준다).
- [x] **AC-6 (bite)** — 수정 파일만 되돌리면 신규 테스트가 **실제로 빨개진다**.
      그리고 기존 초록을 하나도 안 죽인다.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md`
> (`domain: fan-platform`, `traits: [transactional, content-heavy, read-heavy,
> integration-heavy, multi-tenant]`) → `rules/common.md` → 선언된 domain/trait 파일.

- `projects/fan-platform/PROJECT.md`
- `docs/adr/ADR-MONO-067-*` (데모 방문자 화면의 Vercel 이관 — 이 상태가 생긴 이유)
- `docs/adr/ADR-MONO-068-*` (§ D6 = B2 — 데모 백엔드 해석기 단일 구현)
- `infra/demo/backend-resolver/README.md` (`DemoBackendState` = `not-demo | running | unavailable`)

# Related Skills

- `.claude/skills/INDEX.md`

---

# Related Contracts

- 없음 — **API·이벤트 계약을 건드리지 않는다.** 화면 문구와 그 분기뿐이다.
  (`specs/contracts/` 변경 0건이 이 티켓의 성질이고, 리뷰어는 그것을 확인하면 된다.)

---

# Target App

- `projects/fan-platform/web/fan-platform-web`

---

# Implementation Notes

- 판정은 **서버 컴포넌트**에서 한다. `resolveDemoBackendState()` 는 `DEMO_API_BASE`
  (비공개 env)에 의존하고 **그 이름이 클라이언트 번들에 들어가면 안 된다.**
  `/login` 은 이미 서버 컴포넌트이므로 경계 이동이 없다.
- 문구는 `(main)` 배너를 **재사용하지 않는다**. 그 문장은 「샘플 피드가 보인다」를
  전제하는데 `/login` 에는 피드가 없다.
- Auth.js v5 가 `/login?error=` 로 내보내는 코드 중 팬이 실제로 만날 수 있는 것:
  `Configuration`(설정/discovery/시크릿) · `AccessDenied` · `Verification` ·
  그 외 → fallback. 🔴 **목록을 «전수»라고 주장하지 마라** — 라이브러리 판이
  올라가면 코드가 늘 수 있고, 그래서 fallback 이 AC-1 의 본체다.

---

# Edge Cases

- `error` 없음 + 데모 `running` → 아무 경고도 없다 (정상 로그인 화면)
- `error` 없음 + 데모 `unavailable` → 데모 문구만 (클릭 전에 미리 말해 준다)
- `error=Configuration` + 데모 `unavailable` → 데모 문구만 (AC-3)
- `error=Configuration` + 데모 `running` → generic/코드별 문구 (진짜 설정 결함일 수 있다)
- 알 수 없는 코드(`error=Zzz`) → fallback (조용한 실패 금지)
- `not-demo` → 데모 문구 없음, 코드별 문구는 정상 동작

---

# Failure Scenarios

- **컨트롤 플레인 자체가 안 뜬다** → 해석기가 `unavailable` 로 떨어진다.
  🔵 그때 데모 문구를 내는 것은 **과잉이 아니다** — 켜져 있는지 확인할 방법이 없으면
  「못 켠 상태」로 말하는 쪽이 fail-safe 다(방문자에게 재시도를 약속하지 않는다).
- **해석기 호출이 느리다** → `/login` 렌더가 늦어진다. 🔵 `(main)` 레이아웃이 이미
  같은 호출을 하고 결과를 캐시하므로 새로 생기는 비용이 아니다.
- **데모가 켜져 있는데 로그인이 진짜 깨졌다** → AC-3 의 분기가 generic 문구를 살려
  두므로 그 경우는 그대로 보인다. 데모 문구가 진짜 결함을 **가리지 않는다.**

---

# Test Requirements

- `/login` 페이지 렌더 단위 테스트 (해석기 mock, 상태 3종 × `error` 유무)
- 알 수 없는 코드의 fallback 단언
- bite: 수정 되돌리면 빨개지는 것 확인 (AC-6)

---

# Definition of Done

- [x] UI 구현
- [x] 상태 분기 처리 (`not-demo` / `running` / `unavailable`)
- [x] 테스트 추가
- [x] 게이트 4종 통과 (lint / tsc / vitest / build) — 각각 `rc=$?` 명시
- [x] Ready for review

---

# 구현 기록 (ready → review, 2026-09-10 UTC)

## 무엇을 고쳤나

- `src/app/(auth)/login/page.tsx` — 판정 순서를 **「데모 상태 → 코드 → 문구」** 로 바꿨다.
  `ERROR_MESSAGES` 맵 + `GENERIC_ERROR` fallback + `DEMO_OFF_MESSAGE`(로그인 전용).
  세 문구는 **상호배타**로 렌더된다.
- `src/__tests__/login-page.test.tsx` — 신규 **10칸**.

## AC 판정

| AC | 판정 | 근거 |
|---|---|---|
| AC-0 | ✅ | 아래 § 라이브 실측 |
| AC-1 | ✅ | 「알 수 없는 코드도 조용하지 않다」 칸이 fallback 을 직접 단언 |
| AC-2 | ✅ | `state: stopped` → `login-demo-off` 렌더 (error 유무 무관, 2칸) |
| AC-3 | ✅ | 같은 칸에서 `login-error` **부재** + 본문에 「잠시 후 다시 시도해주세요」 **부재** 단언 |
| AC-4 | ✅ | `DEMO_API_BASE` 없음 → 데모 문구 부재 + **fetch 를 부르지도 않음** |
| AC-5 | ✅ | 아래 § 게이트 |
| AC-6 | ✅ | 아래 § bite |

## § 라이브 실측 (AC-0) — 2026-09-10 UTC

```
fan.hubwang.com/api/auth/providers            200   {"iam":{...,"type":"oidc",...}}
fan.hubwang.com/api/auth/session              200
fan.hubwang.com/api/auth/csrf                 200
fan.hubwang.com/api/auth/signin/iam           302 → /login?error=Configuration
auth.hubwang.com/.well-known/openid-configuration
                                              503   "데모 백엔드가 지금 꺼져 있습니다"
<control-plane>/status   {"state":"stopped","ip":null,"used_minutes":543,"budget_minutes":600}
```

🔴 **2026-08-26 의 「`NEXTAUTH_SECRET` 미투입」 뿌리가 아니다.** 그때는
`providers`/`session`/`csrf` 가 **셋 다 500** 이었다. 지금 셋 다 200 이므로 시크릿은
들어가 있고, 깨진 것은 **discovery 한 다리**뿐이다 ⇒ 저장소가 고칠 것은 인증 설정이
아니라 **그 실패를 화면이 뭐라고 말하는가**이다.

## § 게이트 (AC-5) — 각각 독립 statement, 파이프 없음

```
tsc --noEmit                 rc=0
next lint --dir src          rc=0   ✔ No ESLint warnings or errors
vitest run (전체)            rc=0   32 files / 256 tests passed   (이전 31 / 246)
next build                   rc=0   ƒ /login  163 B
```

🔵 `/login` 은 `ƒ`(dynamic) 다 — `searchParams` 를 읽으므로 이 티켓 이전에도 그랬고,
데모 상태를 요청 시각에 읽으려면 **그래야 한다**. 정적이었다면 이 기능이 성립하지 않는다.

## § bite (AC-6)

`page.tsx` 만 원본으로 되돌리고 신규 스위트 재실행 (🔴 `git checkout --` 는 쓰지 않았다 —
미커밋분을 지운다. 파일 복사로만 했고 복원 후 바이트 동일성을 확인했다):

```
BITE rc=1   →  8 failed | 2 passed  (10)
```

🔵 **남은 초록 2칸은 대조군이다** — 「error 없으면 경고 없음」과 「데모가 아니면 컨트롤
플레인을 부르지도 않음」. 이 둘은 수정에 의존하지 않는 것이 **정상**이고, 그래서 이 스위트가
「전부 빨개지는」 스위트가 아니라는 것 자체가 대조군이 살아 있다는 증거다.
복원 후 전체 재실행 = **32 files / 256 tests, rc=0**.

## 안 잰 것

- 🔴 **라이브 `fan.hubwang.com` 에서 이 화면을 보지 못했다.** 데모가 `stopped` 이고
  분 예산이 543/600 이라 켤 수 없었다. 배포 후 데모가 켜져 있는 창에서 `/login` 을
  한 번 열어 보는 것이 남은 확인이다.
- 🔴 **Auth.js 가 내는 `error` 코드의 전수 목록은 재지 않았다** — 그래서 fallback 이
  AC-1 의 본체다. 맵에 없는 코드는 전부 fallback 으로 간다.
- `e2e-smoke` 는 이 화면의 새 분기를 덮지 않는다(스모크는 「설정 있음/없음」 축이다).
