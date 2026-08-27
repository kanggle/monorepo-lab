# Task ID

TASK-FAN-FE-019

# Title

🔴🔴 **라우트 가드의 e2e 가, 자기가 지켜야 할 결핍 조건을 스스로 없앤다.** 스펙은 있고
`ci.yml` 에서 머지 시점에 돌았는데, 프로덕션이 3일간 조용히 뚫려 있었다.

# Status

ready

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
