# Task ID

TASK-FAN-FE-019

# Title

🔴🔴 **라우트 가드의 e2e 가, 자기가 지켜야 할 결핍 조건을 스스로 없앤다.** 스펙은 있고
`ci.yml` 에서 머지 시점에 돌았는데, 프로덕션이 3일간 조용히 뚫려 있었다.

# Status

done

# Owner

fan-platform

# Task Tags

- frontend
- testing
- ci
- security

---

# Goal

`TASK-FAN-FE-018` 이 닫히면서 **AC-4 의 전제가 거짓임이 측정으로 드러났다.**
*"미들웨어가 안 돌면 빨개지는 테스트가 하나도 없다"* 는 틀렸다 — 테스트는 있고 PR 시점에
돈다. 그런데도 프로덕션은 뚫려 있었다. **가드가 못 문 이유를 축으로 삼는 티켓**이다.

🔵 이 티켓은 «테스트를 하나 더 쓴다» 가 아니다. **같은 모양의 로컬 e2e 를 추가하면 아무것도
더 잡지 못한다** — 018 이 그것까지 확인했다. 여기서 정할 것은 **결핍일 때 앱이 무엇을 해야
하는가** 이고, 그것은 제품 결정이라 테스트보다 먼저 온다.

---

# Context — 실측 (2026-08-27 UTC, `TASK-FAN-FE-018` 판정에서)

## ① 가드는 있었다. 러너도 있었다.

`web/fan-platform-web/e2e-smoke/auth-guard.spec.ts` 가 `/artists` · `/posts/:id` 에
**비인증 요청을 보내** `/login` 리다이렉트와 `from` 쿼리를 단언한다. 선언 파일 grep 이 아니라
**요청의 결과**를 본다 — 018 의 AC-4 가 요구한 그 모양이다.

러너 = `.github/workflows/ci.yml` 의 **`frontend-e2e-smoke`** 잡
(*"Frontend E2E smoke (web-store + fan-platform-web + console-web, Playwright)"*),
`needs.changes.outputs.fan == 'true'` 로 활성화, `pnpm e2e:smoke` 호출.
🔵 **nightly 전용이 아니다 — PR 시점에 돈다.** 018 이 걱정한 «밤에만 도는 곳» 이 아니었다.

## ② 🔴🔴 그런데 config 가 결핍 조건을 없앤다

`playwright.smoke.config.ts` 의 `webServer.env`:

```
NEXTAUTH_SECRET: 'smoke-test-secret-32-bytes-min-OK'
NEXTAUTH_URL:    'http://localhost:3002'
OIDC_ISSUER_URL / OIDC_CLIENT_ID / OIDC_CLIENT_SECRET / AUTH_TRUST_HOST
```

**프로덕션에서 빠져 있던 바로 그 변수들이다.** CI 잡 스텝에는 인증 env 가 없고, 있는 것은
이 config 다. 그래서 스펙이 실제로 단언하는 명제는

> *"설정이 **있을 때** 미들웨어가 리다이렉트한다"*

이고, 프로덕션이 실패한 지점은 *"설정이 **없다**"* 였다. **두 명제는 겹치지 않는다.**
가드는 초록이고 프로덕션은 조용한 404 였다.
[[feedback_why_a_guard_does_not_bite]] [[feedback_control_group_design_four_axes]]

🔵 config 의 의도 자체는 옳다 — 주석이 *"백엔드·GAP 미기동에서도 결정론적으로 검증"* 이라
적어 뒀고, 도달 불가 loopback 강제는 그 목적에 맞다. **결함은 «인증 설정» 축을 같이 채워
넣은 것**이고, 그 축이 정확히 결함이 살던 곳이다.

## ③ 오늘 관측된 fail-open 방향

env 가 없을 때 보호 경로의 실제 응답은 **404**(조용한 통과)였다. 500 도 리다이렉트도 아니다.
🔴 라우트 가드가 가져서는 안 되는 방향이다 — 증상이 «실패» 가 아니라 **«아무 일도 없음»**
이라 아무도 안 본다. [[feedback_a_verifiable_mechanism_is_not_the_cause]]

## ④ 배포된 표면을 재는 잡이 없다

①의 어느 것도 `fan.hubwang.com` 을 찌르지 않는다. `check-fan-fresh.sh` 가 있지만 라이브
origin 이 필요해 **부르는 잡이 없다** — `ci.yml` 은 그 파일의 `bash -n` 만 본다.
⇒ 배포 뒤 «가드가 실제로 도는가» 를 아무도 안 잰다.

---

# Scope

**In:**

- `web/fan-platform-web/playwright.smoke.config.ts` — 결핍 대조군을 위한 설정 축
- `web/fan-platform-web/e2e-smoke/` — 새 칸
- `src/middleware.ts`(또는 auth 설정) — AC-1 이 fail-closed 를 고르면
- `.github/workflows/ci.yml` — 새 칸이 도는 자리

**Out:**

- 🔴 `fan.hubwang.com` 라이브 감시(④)는 **별 축**이다. 배포 후 검증이라 PR 시점 잡으로는
  못 덮고, 스케줄/후크 설계가 필요하다 ⇒ AC-4 에서 **티켓으로 분리**한다(문장으로 남기지
  마라 — 미측정 채널은 옆 숫자에 먹힌다).
- Vercel env 설정 자체 → 소유자, 이미 완료.
- OIDC 왕복 성공 여부 → `TASK-MONO-574`.

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 읽는다**

1. `playwright.smoke.config.ts` 의 `webServer.env` 를 **읽고** 시작한다. ②의 목록이
   그대로인지 확인한다 — 그 사이 누가 줄였거나 늘렸을 수 있다.
2. `auth-guard.spec.ts` 가 아직 `frontend-e2e-smoke` 에서 도는지 확인한다.
   🔵 **양성 대조군**: 그 잡의 최근 성공 런에서 junit 카운트를 읽어 스펙이 **실제로 실행된
   개수**를 본다 — `reporter` 에 junit 이 있는 이유가 그것이다(`TASK-MONO-545`).
   «0 discovered» 와 «전부 통과» 를 구별하지 않으면 이 티켓도 같은 함정에 빠진다.
   [[feedback_a_runner_that_matches_no_package_exits_zero]]

## AC-1 — 🔴🔴 **결핍일 때의 동작을 «정한다». 테스트보다 먼저다.**

인증 설정이 없을 때 보호 경로가 무엇을 해야 하는지 고르고, 근거를 코드 옆에 적는다.

| 선택지 | 동작 | 대가 |
|---|---|---|
| **(A) fail-closed — `/login` 리다이렉트** | 설정 유무와 무관하게 미인증은 항상 막힌다 | 🔵 가드의 목적과 일치. 🔴 설정이 깨진 채로 «정상처럼» 보여 진짜 장애가 늦게 발견될 수 있다 |
| **(B) fail-closed — 5xx** | 설정 결핍을 **장애로** 드러낸다 | 🔵 조용하지 않다. 🔴 사이트 전체가 죽는다 — 공개 경로까지 |
| **(C) 현행 유지(통과)** | 아무것도 안 바꾼다 | 🔴 오늘 뚫린 그 동작이다. 고른다면 **왜 안전한지**를 적어야 한다 |

🔴 **정하지 않고 테스트만 쓰지 마라.** 그러면 그 테스트가 **현재 동작을 정답으로 굳힌다** —
이 저장소가 이미 이름 붙인 실패 모드다.
[[feedback_a_pin_can_freeze_the_defect_it_was_written_to_guard]]
[[env_conditional_bean_default_must_be_the_safe_side]]

🔵 (A)/(B) 를 고르면 `src/middleware.ts` 쪽 변경이 따라온다 — Scope 의 In 에 그래서 있다.

## AC-2 — **결핍 대조군 칸**을 만든다

인증 env 를 **뺀** 서버로 보호 경로를 찌르는 칸. 기대값은 AC-1 이 고른 것.

- 🔴 기존 `playwright.smoke.config.ts` 를 **고쳐서** 뺴면 지금 초록인 칸들이 같이 죽는다.
  두 축이 필요하다: «설정 있음»(기존, 리다이렉트 단언) + «설정 없음»(신규).
  두 번째 config / project 로 가르든, `webServer.env` 를 파라미터화하든 **둘 다 남아야 한다.**
- 🔵 **음성 대조군을 같이 둔다**: 결핍 서버에서도 `/login` 은 여전히 200 이어야 한다.
  안 그러면 «전부 막힘» 이라는 자명한 오답이 통과한다.
- 🔴 **주입부터 단언한다** — 결핍 서버가 정말 env 없이 떴는지를 먼저 확인한다(예:
  `/api/auth/providers` 가 결핍 상태의 응답을 내는지). 안 하면 «안 물었다» 와
  «시험한 적이 없다» 가 구별되지 않는다.
  [[feedback_assert_the_injection_before_reading_the_bite]]

## AC-3 — 🔴 **`302` 를 문자 그대로 단언하지 마라**

018 실측: 실제 코드는 **`307`** 이다(`NextResponse.redirect` 기본값, 메서드 보존).
018 의 AC 문구가 `302` 라고 적혀 있었고 **그대로 단언했으면 고쳐진 동작에 빨간불이 켜졌다.**
새 칸은 «리다이렉트인가 + `Location` 이 `/login` 인가» 를 보고, 코드를 박아야 한다면
**측정한 값(307)** 을 쓰고 *"무엇이 이 값을 바꾸는가"* 를 같이 적는다.
[[feedback_a_reported_figure_must_name_what_was_measured]]

## AC-4 — 🔴 배포된 표면 감시는 **티켓으로 분리한다**

④는 이 티켓이 안 고친다(축이 다르다 — 배포 후 검증). 🔴 **문장으로 남기지 말고 티켓을
만든다.** 미측정 채널을 산문으로 적어 두면 옆의 초록 숫자에 먹힌다.
그 티켓이 답할 것: 무엇이 `fan.hubwang.com` 을 찌르는가(스케줄 워크플로 / 배포 후 훅),
`check-fan-fresh.sh` 를 재사용하는가 새로 쓰는가, 실패를 누가 보는가.
[[feedback_a_census_measures_where_you_looked_not_what_exists]]

## AC-5 — 검증 (숫자 없는 «통과» 금지)

- `pnpm --filter fan-platform-web e2e:smoke` — **칸 수를 적는다**(기존 + 신규).
- `pnpm --filter fan-platform-web lint` — 🔴 프런트 로컬 검증에 필수다.
- `pnpm --filter fan-platform-web build` — 🔴 e2e 초록이 빌드 초록을 뜻하지 않는다
  (dev 서버는 타입체크를 안 한다).
- 🔴 **bite**: AC-1 의 수정을 되돌린 사본에서 새 칸이 **실패하는지** 확인한다.
- 🔴 **CI 에서 도는지 확인하고 잡 이름을 적는다.** 새 config 를 만들었다면
  `frontend-e2e-smoke` 가 그것도 부르는지 — 스크립트 하나만 부르면 **새 칸은 로컬 전용이
  되어 썩는다.** [[feedback_two_correct_exclusions_compose_into_a_hole]]

---

# Related Specs

- `projects/fan-platform/tasks/review/TASK-FAN-FE-018-the-route-guard-does-not-run-in-production.md` — 이 티켓의 출처
- `projects/fan-platform/web/fan-platform-web/playwright.smoke.config.ts` — ②의 자리
- `projects/fan-platform/web/fan-platform-web/e2e-smoke/auth-guard.spec.ts` — 이미 있는 가드
- `.github/workflows/ci.yml` § `frontend-e2e-smoke` — 러너
- `projects/fan-platform/web/fan-platform-web/check-fan-fresh.sh` — ④, 부르는 잡이 없다
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 결핍 서버가 기동 자체에 실패한다 | 🔵 그것도 **판정**이다 — AC-1 (B) 에 가깝다. 다만 «기동 실패» 와 «가드가 막았다» 는 다른 사건이므로 갈라 적는다 |
| `AUTH_TRUST_HOST` 만 빼도 같은 증상이 난다 | AC-2 의 결핍 집합을 **하나씩** 빼며 확인할 필요는 없다. 프로덕션에서 빠졌던 **네 개 묶음**이 모집단이다 |
| 두 config 가 포트를 다투다 | 결핍 서버는 다른 포트로 띄운다. `reuseExistingServer` 가 CI 밖에서 **남의 서버에 붙는** 함정이 이 저장소에 이미 있다 |
| 신규 칸이 nightly 로 밀린다 | AC-5 — 잡 이름을 적는 칸이 그것을 막는다 |
| 018 이 `review/` 라 못 고친다 | 🔵 맞다. 이 티켓이 그 채널이다 — 018 의 기록을 고치지 말고 여기서 이어라 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방어 |
|---|---|---|
| 같은 모양의 로컬 e2e 를 하나 더 쓴다 | 칸은 늘고 잡는 결함은 0 | Goal + ① — **이미 있다** |
| AC-1 을 안 정하고 테스트부터 쓴다 | 현재 fail-open 동작이 «정답» 으로 굳는다 | AC-1 — 결정이 먼저 |
| 기존 config 의 env 를 지워 결핍 축을 만든다 | 지금 초록인 칸들이 같이 죽고, 되돌리면 이 티켓도 사라진다 | AC-2 — **두 축 다 남긴다** |
| 결핍 서버가 실제로는 env 를 물려받는다 | 새 칸이 «설정 있음» 을 다시 재고 초록 | AC-2 — 주입부터 단언 |
| `302` 를 단언한다 | 실제 307 이라 고쳐진 동작이 빨강 | AC-3 |
| ④를 «후속으로 본다» 는 문장으로만 남긴다 | 배포된 표면은 계속 아무도 안 잰다 | AC-4 — **티켓을 만든다** |

---

# ✅ 판정 (2026-08-28 UTC) — AC-1 = **(A) fail-closed**. 결함은 라이브 없이 로컬에서 재현된다.

## AC-0 — 상속하지 않고 다시 읽었다

**(1) `playwright.smoke.config.ts` 의 `webServer.env`** — ②의 목록 그대로다. 줄지도 늘지도
않았다. 실제로는 8개이고 축이 둘이다: 백엔드 축 2개(`NEXT_PUBLIC_GATEWAY_URL`,
`GATEWAY_URL_INTERNAL` — 도달 불가 loopback) + **인증 축 6개**(`OIDC_ISSUER_URL`,
`OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `NEXTAUTH_URL`, `NEXTAUTH_SECRET`,
`AUTH_TRUST_HOST`). ②가 지목한 것이 이 여섯이고, 이번 수정이 결핍시키는 것도 이 여섯이다.

**(2) 러너 + 🔵 양성 대조군 (junit 카운트)** — `auth-guard.spec.ts` 는 아직
`frontend-e2e-smoke` 에서 돈다. 「0 discovered」가 아님을 **런의 숫자로** 확인했다:

- run [33062760603](https://github.com/kanggle/monorepo-lab/actions/runs/33062760603) /
  job `98485387593` — commit `4e750c183`, 2026-08-27 10:25Z, **success**
- fan 단계: `Running 4 tests using 1 worker` → `4 passed`, 그리고 두 칸이 **이름으로**
  찍혀 있다 — `auth-guard.spec.ts:8:7`, `auth-guard.spec.ts:14:7`
- junit 집계: `TEST-SUMMARY lane=frontend-e2e-smoke tests=15 failures=0 errors=0 skipped=0`
  → `web-store 3` / **`fan-platform-web 4`** / `console-web 8`

⇒ 스펙은 **실행됐고 통과했다.** 018 이 말한 대로 러너는 문제가 아니었다.

## 🔵 결함을 **로컬에서 재현했다** — 라이브 접근 없이

018 은 `fan.hubwang.com` 을 찔러야 했고 배포 한도에 막혀 하루를 잃었다. 같은 결함이
**로컬 prod build + 인증 env 부재**로 그대로 난다(2026-08-28 UTC, worktree,
`next start --port 3003`, 인증 6개 미설정):

| 경로 | 로컬 결핍 서버 | 018 이 프로덕션에서 잰 값 |
|---|---|---|
| `/artists` | **200** | 200 |
| `/me` | **200** | 200 |
| `/posts/abc-123` | **200** | (동형) |
| `/nonexistent-xyz` | **404** | 404 ← 판별자 |
| `/login` | 200 | 200 |
| `/api/auth/providers` | **500** `{"message":"There was a problem with the server configuration…"}` | 500 (동일 본문) |

⇒ 결핍 축은 **로컬에서 결정론적으로 성립한다.** 이 티켓이 라이브를 안 찔러도 되는 이유이자,
AC-2 의 새 칸이 CI 에서 무료로 도는 이유다.

## 🔴🔴 기전 — 왜 «조용한 통과» 였나 (018 이 «B» 라고만 부른 것의 안쪽)

`next-auth/lib/index.js` 의 `auth()` 는 자기 세션 요청의 응답을 **상태 코드를 보지 않고**
파싱한다 — `getSession(...).then((r) => r.json())`, `response.ok` 검사가 없다.
설정이 없으면 auth.js 는 500 + **JSON 본문**을 내므로 `auth()` 의 반환값은

```
{ message: "There was a problem with the server configuration. …" }
```

이고, 이것은 **truthy** 다. 그래서 `if (!session)` 은 «설정 오류» 를 «세션이 있다» 로 읽고
`NextResponse.next()` 를 냈다. 404/200 은 미들웨어가 **안 돈** 것이 아니라 **돌고 통과시킨**
것이다. (018 의 가설 B 가 맞았고, 이제 그 «던진다» 가 실제로는 «던지지 않고 오류 본문을
돌려준다» 였음이 확정됐다.)

🔵 판정에 쓸 사실: `!x` 는 «값이 있나» 를 묻는다. 가드가 물어야 했던 것은 **«이 값이 세션인가»**
였다. [[feedback_a_verifiable_mechanism_is_not_the_cause]]

## AC-1 — **(A) fail-closed, `/login` 리다이렉트**. 근거는 코드 옆에 있다

`src/middleware.ts` 상단 주석 블록이 결정과 근거를 담는다(표 + 대가 + 왜 (B) 가 아닌지).
요지:

- **(B) 5xx 기각** — 공개 경로(`/login`, `/api/auth/*`)까지 죽는다. 사이트 전체가 내려가고,
  무엇이 고장났는지 알려 줄 수 있는 유일한 페이지가 함께 사라진다.
- **(C) 현행 유지 기각** — 오늘 뚫린 그 동작이다.
- **(A) 의 대가**(설정이 깨진 채 «정상처럼» 보인다)는 두 가지로 상쇄한다:
  ① 설정 결핍은 **독립적인 시끄러운 신호**를 그대로 갖는다(`/api/auth/*` → 500, AC-2 가
  단언), ② 닫히는 분기가 **매번 `console.error` 를 찍는다.** ⇒ «닫혔다» 와 «설정이
  깨졌다» 가 밖에서 구별된다.

🔴 **술어를 env 이름이 아니라 «반환값의 모양» 으로 잡았다.** `process.env.NEXTAUTH_SECRET`
검사는 *선언*이고, 이 결함은 정확히 «선언이 런타임과 달랐던 것» 이다. 신뢰의 대상은
`auth()` 가 실제로 돌려준 값이므로, 묻는 대상도 그것이어야 한다.
[[feedback_declaration_files_are_not_the_runtime_state]]

## AC-2 — 결핍 대조군. **두 축이 다 남아 있다**

| 축 | 포트 | 인증 env | 도는 스펙 |
|---|---|---|---|
| 설정 **있음** (기존) | 3002 | 6개 그대로 | `auth-guard` · `home` · `login` (4칸, **무변경**) |
| 설정 **없음** (신규) | 3003 | 6개를 `''` 로 강제 | `auth-config-absent` (5칸) |

- 🔵 **새 config 파일을 만들지 않았다.** `frontend-e2e-smoke` 는 `pnpm e2e:smoke` **하나만**
  부르고 `summarise-test-results` 는 junit **하나만** 읽는다. 별 config 였다면 새 칸은
  CI 에서 안 돌고 카운트에도 안 잡혀 **로컬 전용으로 썩었을 것**이다.
  [[feedback_two_correct_exclusions_compose_into_a_hole]]
- 🔴 **주입부터 단언한다** — 새 스펙의 첫 칸이 `/api/auth/providers` 가 **500 + 설정 오류
  본문**을 내는지 본다. 결핍 서버가 설정을 물려받으면 **여기가 먼저 빨개진다.**
  왜 필요한가: `webServer.env` 는 `process.env` 위에 병합되고 `next start` 는 그 위에
  `.env.local` 을 얹는데, 이 앱의 `.env.local`(untracked, LOCAL DEMO ONLY)에는
  **`NEXTAUTH_SECRET` 이 들어 있다.** 키를 «안 넣는» 방식이면 로컬에서 결핍 서버가 설정을
  되찾아 「설정 있음」을 다시 재게 된다. 빈 문자열로 덮는 이유는 `@next/env` 의
  `processEnv()` 가 `.env*` 키를 `typeof initialEnv[key] === 'undefined'` 일 때만
  적용하기 때문이고, 🔴 **그 추론을 믿고 끝내지 않기 위해** 주입 단언 칸이 있다.
  [[feedback_assert_the_injection_before_reading_the_bite]]
- 🔵 **음성 대조군** — 결핍 서버에서도 `/login` 은 **200**. 「전부 막힘」이라는 자명한 오답과
  리다이렉트 루프를 동시에 막는다.
- 🔴 **결핍 서버는 다른 포트(3003) + `reuseExistingServer: false`** (CI 밖에서도).
  이 저장소에는 러너가 «남의 세션이 띄운 서버» 에 붙어 엉뚱한 트리를 재는 함정이 이미 있다.

## AC-3 — `302` 를 단언하지 않았다

새 칸이 재는 명제는 ① 리다이렉트인가(`[301,302,303,307,308]` 중 하나), ② `Location` 의
경로가 `/login` 이고 `from` 이 원래 경로인가 — 두 개다. **오늘 실측값은 307**
(`NextResponse.redirect` 기본값, 메서드 보존)이지만 **판정에 쓰지 않는다.**
무엇이 이 값을 바꾸는가: `NextResponse.redirect(url, 302)` 로 명시하거나 Next 가 기본값을
바꾸면 달라지고, 그 둘 중 어느 것도 가드가 지키려는 성질을 깨지 않기 때문이다.
[[feedback_a_pin_can_freeze_the_defect_it_was_written_to_guard]]

## AC-4 — 티켓으로 분리했다: **`TASK-MONO-600`**

`tasks/ready/TASK-MONO-600-nothing-pokes-the-deployed-fan-surface.md` (root — 새 잡이
`.github/workflows/` 에 앉으므로 monorepo-level). 문장으로 남기지 않았다.
그 티켓이 답할 것: 무엇이 찌르는가(스케줄 vs 배포 후 훅), 어느 호스트가 권위인가
(`fan.hubwang.com` vs `kanggle-fan.vercel.app` — 기본값이 다르다), `check-fan-fresh.sh` 를
재사용하는가, **실패를 누가 보는가**, 그리고 그 잡이 일부러 틀린 입력에 빨개지는가.

## AC-5 — 검증 (숫자)

| 게이트 | 명령 | 결과 |
|---|---|---|
| e2e | `pnpm --filter fan-platform-web e2e:smoke` | **9 passed** — 기존 **4** + 신규 **5** |
| lint | `pnpm --filter fan-platform-web lint` | rc=0, `✔ No ESLint warnings or errors` |
| build | `pnpm --filter fan-platform-web build` | rc=0, `ƒ Middleware 86.3 kB` |
| unit | `pnpm --filter fan-platform-web test` | 23 files / **153 passed** |

칸 수 내역 — 기존 4(`auth-guard` 2 · `home` 1 · `login` 1, 프로젝트 `chromium`) +
신규 5(주입 1 · 음성 대조군 1 · 보호 경로 3, 프로젝트 `chromium-auth-config-absent`).
junit 도 `tests="9"` 로 그렇게 적었다. ⇒ CI 의 fan 행은 **4 → 9**, lane 합계는 **15 → 20**.

### 🔴 bite — 고침을 되돌린 사본에서 새 칸이 **빨개진다**

`src/middleware.ts` 만 수정 전으로 되돌리고 재빌드 → **3 failed / 6 passed**.
실패한 칸과 그때 받은 값:

| 칸 | 기대 | 되돌린 코드가 준 값 |
|---|---|---|
| `/artists` | 리다이렉트 | **200** |
| `/posts/abc-123` | 리다이렉트 | **200** |
| `/nonexistent-xyz` | 리다이렉트 | **404** |

🔵 **정확히 018 이 프로덕션에서 잰 지문이다.** 그리고 나머지는 초록으로 남았다 —
주입 칸(500)과 음성 대조군(`/login` 200)은 고침과 무관하게 참이므로 **설계대로** 통과했고,
「설정 있음」 축의 기존 4칸도 통과했다 ⇒ **기존 초록을 죽이지 않았다.**

### 🔴 CI 에서 도는 자리 — 잡 이름

**`frontend-e2e-smoke`** — *"Frontend E2E smoke (web-store + fan-platform-web +
console-web, Playwright)"*, `.github/workflows/ci.yml`, `needs.changes.outputs.fan == 'true'`.
`ci.yml` **변경 없음**이 의도다: 새 칸이 기존 config·기존 스크립트·기존 junit 파일을 타므로
잡이 이미 부른다. nightly 로 밀리지 않는다.

---

## 남은 것 / 안 한 것 (🔴 초록 숫자 옆에서 먹히지 않게)

- 🔴 **배포된 표면은 이 티켓도 안 쟀다.** `fan.hubwang.com` 은 여기서 한 번도 안 찔렀다 —
  전부 로컬 재현이다. 그 채널은 `TASK-MONO-600` 이고, **그것이 닫히기 전까지 «가드가
  프로덕션에서 돈다» 는 측정된 사실이 아니다.**
- 🔴 **인증에 성공한 세션 경로는 여전히 어떤 테스트도 안 잰다.** 새 술어는 «`user` 를
  이름댈 수 있는 세션만 통과» 인데, 통과하는 쪽을 재려면 진짜 OIDC 세션이 필요하고 smoke
  단계에는 없다. 근거는 라이브러리 코드다 — `next-auth/lib/index.js` 가 세션 응답을
  `{ user, ...session }` 로 감싸므로 인증된 세션에는 `user` 가 반드시 있다. **읽어서 확인한
  것이지 실행해서 확인한 것이 아니다.** 실행 축은 `TASK-MONO-574`(OIDC 왕복)가 가깝다.
- 🔵 **부수 효과 하나를 의도적으로 남겼다**: silent refresh 실패로 `sessionCallback` 이
  `user: undefined` 로 강등한 세션도 이제 **닫힌다**. 이전에는 truthy 라 통과했다 —
  `auth-callbacks.ts` 의 주석이 *"middleware 가 `/login?from=…` 으로 돌려보낸다"* 고 적어
  둔 동작과 코드가 어긋나 있었고, 이번 술어가 그것을 일치시킨다. 🔴 **이 경로에도 테스트는
  없다**(F3 강등을 재현하려면 만료된 refresh token 이 필요하다).
- 🔵 018 의 기록은 고치지 않았다(`review/` 는 frozen) — 이 티켓이 그 채널이다.
