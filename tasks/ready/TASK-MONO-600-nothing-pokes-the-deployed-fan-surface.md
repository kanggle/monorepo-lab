# Task ID

TASK-MONO-600

# Title

🔴 **배포된 팬 표면을 아무도 안 찌른다.** 라우트 가드가 프로덕션에서 3일간 죽어 있었고,
그것을 알아낸 것은 잡이 아니라 **사람이 손으로 `curl` 을 친 것**이었다.

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- demo
- vercel
- observability

---

# Goal

`fan.hubwang.com` 이 살아 있는지 / 가드가 실제로 도는지를 재는 **잡을 만든다.**

이 티켓은 `TASK-FAN-FE-019` 의 AC-4 가 **문장이 아니라 티켓으로** 분리한 축이다.
019 는 «인증 설정이 없을 때 앱이 무엇을 해야 하는가» 를 정하고 그것을 **PR 시점 e2e** 로
걸었다. 그 축으로는 **배포된 판**을 못 잰다 — 019 의 새 칸은 러너가 띄운 로컬 서버를
찌르고, 프로덕션은 찌르지 않는다.

🔴 **왜 산문으로 남기지 않았나**: 미측정 채널을 «후속으로 본다» 라고 적어 두면 옆에 있는
초록 숫자(019 의 «9 passed»)에 먹힌다. 019 는 자기가 재지 **않은** 것을 티켓으로 만들어야
그 공백이 큐에 남는다.
[[feedback_a_census_measures_where_you_looked_not_what_exists]]

---

# Context — 실측

## ① 발견 경로가 자동화가 아니었다

`TASK-FAN-FE-018`(2026-08-27 UTC)이 `fan.hubwang.com` 의 모든 보호 경로가 미인증으로
200 을 준다는 것을 찾아냈다. 찾은 방법은 **사람이 `curl` 을 친 것**이다. 그 사이
`ci.yml` 의 `frontend-e2e-smoke` 는 **매 PR 마다 초록**이었다(같은 잡의 fan 칸 4개가
`4 passed`). 잡이 잰 것은 러너 위의 로컬 서버였고, 뚫려 있던 것은 배포판이었다.

## ② 스크립트는 있는데 부르는 잡이 없다

`projects/fan-platform/web/fan-platform-web/check-fan-fresh.sh` 는 서빙 중인 팬이 `main`
판인지를 `/build-info.json` 의 커밋으로 판정하고, `--self-test` 대조군까지 갖고 있다
(`TASK-MONO-563`). 그런데 `ci.yml` 이 그 파일에 하는 일은 **`bash -n` 문법 검사뿐이다**
(`ci.yml:1290`). 라이브 origin 이 필요해 **실행하는 잡이 없다.**

🔵 그 스크립트가 재는 축은 **신선도**(서빙 중인 판 = `main` 인가)이지 **가드가 도는가**가
아니다. 이 티켓이 필요한 축은 최소 둘이고, 하나로 덮이지 않는다.

## ③ 기본 origin 이 `fan.hubwang.com` 이 아니다

`check-fan-fresh.sh` 의 `ORIGIN` 기본값은 `https://kanggle-fan.vercel.app` 이다.
018 이 잰 것은 `https://fan.hubwang.com` 이다. **어느 쪽이 권위인지 이 티켓이 정한다** —
둘 다 같은 배포를 가리키더라도, 잡이 찌르는 호스트는 **사람이 보는 호스트**여야 한다
(도메인 배선이 끊기면 잡은 초록인 채 방문자만 죽는다).

---

# Scope

**In:**

- `.github/workflows/` — 새 스케줄 워크플로, 또는 기존 워크플로의 새 잡
- `projects/fan-platform/web/fan-platform-web/check-fan-fresh.sh` — 재사용한다면 그 배선

**Out:**

- 🔴 **PR 시점 가드는 이미 있다** — `TASK-FAN-FE-019` 가 «설정 있음»/«설정 없음» 두 축을
  `frontend-e2e-smoke` 에 걸어 뒀다. 같은 모양을 또 만들지 마라.
- 앱 코드의 fail-closed 동작 자체 → `TASK-FAN-FE-019` 에서 결정·구현 완료.
- OIDC 왕복 성공 여부 → `TASK-MONO-574`.
- 다른 Vercel 표면(web-store · console · 론처)으로의 일반화 → 팬에서 한 번 돌려 본 뒤.

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 잰다** (verify-then-act)

🔴 **이 티켓의 숫자를 상속하지 마라.** 착수 시점에 `fan.hubwang.com` 의 네 칸
(`/artists` · `/nonexistent-xyz` · `/login` · `/api/auth/providers`)을 그대로 다시 찍고
기록한다. 그 사이 소유자가 env 를 바꿨거나 배포가 밀렸을 수 있다.

🔴 라이브 측정에는 **`curl --ssl-no-revoke`** 를 쓴다. 이 호스트가 캡티브 포털 뒤에 있으면
HTTPS 가 `curl 000` 이 되어 **측정이 통째로 거짓**이 된다. **`000` 을 «죽었다» 로 읽지
마라** — «잴 수 없었다» 다.

🔴 Vercel Deployment Protection 이 켜져 있으면 잡이 받는 것은 앱이 아니라 **인증 벽**이다.
그 상태에서 «200 이 아니다» 는 앱에 대한 판정이 아니다. 무엇을 받았는지 본문으로 확인한다.

## AC-1 — **무엇이 찌르는가**를 정한다

| 선택지 | 언제 도는가 | 대가 |
|---|---|---|
| **(A) 스케줄 워크플로** (`cron`) | 고정 주기 | 🔵 배포 경로와 무관하게 계속 잰다 — 소유자가 Vercel 콘솔에서 env 를 지워도 잡힌다. 🔴 발견까지 최대 1주기 지연 |
| **(B) 배포 후 훅** | 배포마다 | 🔵 즉시. 🔴 **배포가 안 생기면 안 돈다** — 팬은 `vercel-ignore.sh` 로 자주 건너뛰어지고, 018 의 결함은 «배포가 안 됐다» 가 아니라 «배포된 판이 잘못됐다» 였다 |
| **(C) 둘 다** | — | 🔴 먼저 하나로 도는 것을 확인하고 나서 |

🔵 018 의 결함 모양(배포는 여러 번 성공했고, 그래도 뚫려 있었다)은 **(A) 쪽을 지지한다** —
배포 이벤트에 매달린 판정자는 «배포가 안 일어난 동안» 을 못 본다. 다만 고르는 것은 착수자다.

🔴 **주기를 적을 때 근거를 같이 적는다.** "매일" 이 아니라 "이 결함이 3일 살아 있었으므로
그보다 짧게" 처럼. 근거 없는 숫자는 드리프트한다.
[[feedback_a_figure_nothing_can_fail_on_will_drift]]

## AC-2 — **무엇을 재는가** — 축이 둘이고 하나로 덮이지 않는다

1. **신선도** — 서빙 중인 판이 `main` 인가. `check-fan-fresh.sh` 가 이미 답한다.
   재사용할지 새로 쓸지 정하고, 재사용한다면 `--origin` 을 AC-3 이 정한 호스트로 넘긴다.
2. **가드가 도는가** — 미인증 요청이 보호 경로에서 `/login` 으로 꺾이는가.
   🔴 **`/nonexistent-xyz` 를 빼지 마라.** 그것이 «미들웨어를 거쳤다» 를 «그 페이지가
   있다/없다» 와 가르는 유일한 칸이다(018 의 판별자).
   🔵 **음성 대조군**: `/login` 은 200 이어야 한다. 없으면 «전부 302» 라는 잘못된 고침이
   초록으로 보인다.
   🔴 `302` 를 문자 그대로 단언하지 마라 — 실측은 **307**(`NextResponse.redirect` 기본값).

🔴 신선도만 재고 닫지 마라. 018 의 결함은 **신선한 판이 뚫려 있던 것**이다 — 축 1 은
초록이었을 것이다.

## AC-3 — **어느 호스트가 권위인가**

`fan.hubwang.com` 과 `kanggle-fan.vercel.app` 중 잡이 찌르는 쪽을 정하고 근거를 적는다.
🔵 기본값 제안: **사람이 보는 호스트**(`fan.hubwang.com`). 도메인 배선이 끊기면
`*.vercel.app` 은 초록인 채 방문자만 죽고, 그 상태를 아무도 안 본다.

## AC-4 — 🔴 **실패를 누가 보는가**

스케줄 잡의 실패는 **아무도 안 보는 것이 기본값**이다 — PR 에 체크로 붙지 않고, 알림이
없으면 Actions 탭에만 남는다. 이 티켓은 그 경로를 명시적으로 정해야 한다(잡 실패가
어디에 뜨는가 / 누가 그것을 읽는가). 정하지 못하면 **그 사실을 티켓에 적고** 닫지 마라 —
«도는데 아무도 안 보는 잡» 은 이 저장소가 이미 이름 붙인 실패 모드다.

## AC-5 — **가드가 실제로 문다**

🔴 새 잡이 «초록» 인 것은 증거가 아니다. **일부러 틀린 입력으로 빨개지는지** 확인한다:
- 존재하지 않는 origin(예: `--origin https://fan-does-not-exist.hubwang.com`) → 잡이
  **빨강**인가, 아니면 «판정 불가» 를 초록으로 접는가.
  🔴 `check-fan-fresh.sh` 의 종료코드 **2 = 판정 불가**를 0 으로 접지 마라(스크립트 헤더가
  그것을 명시한다).
- 가드 축은 `/login` 이 아닌 보호 경로가 200 을 주는 상태에서 빨개져야 한다.
  🔵 019 가 그 상태를 로컬에서 재현하는 법을 이미 남겼다 — 인증 env 없이 `next start`.

## AC-6 — 검증 (숫자 없는 «통과» 금지)

- 새 워크플로를 **한 번 실제로 돌린다**(`workflow_dispatch` 로). 런 URL 과 결론을 적는다.
- 🔴 **Actions 분 소진 지문을 확인한다** — 스텝 0개 / 로그 없음으로 «성공» 처럼 보이는
  런이 이 저장소에 있었다. 판별 = 재실행해도 steps=0.
- 잡 이름을 적는다.

---

# Related Specs

- `projects/fan-platform/tasks/review/TASK-FAN-FE-018-the-route-guard-does-not-run-in-production.md` — ①의 출처
- `projects/fan-platform/tasks/review/TASK-FAN-FE-019-the-auth-guard-supplies-the-env-whose-absence-broke-it.md` — 이 티켓을 분리한 AC-4
- `projects/fan-platform/web/fan-platform-web/check-fan-fresh.sh` — ②
- `.github/workflows/ci.yml` § `frontend-e2e-smoke` — PR 시점 축(이미 있음)
- `.github/workflows/nightly-e2e.yml` — 스케줄 워크플로의 기존 사례
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 착수 시 표면이 이미 뚫려 있다 | 🔵 **잡부터 만든다.** 고치는 것은 별 축이고, 잡이 없으면 다음에도 사람이 손으로 찾는다 |
| Deployment Protection 이 켜져 있다 | AC-0 — 인증 벽을 앱 응답으로 오독하지 않는다. 우회가 필요하면 그것 자체가 선행 |
| 캡티브 포털 / 사내망에서 `curl 000` | AC-0 — «잴 수 없었다». 러너에서는 안 나겠지만 **로컬 재현 때** 난다 |
| 팬 배포가 오래 건너뛰어져 판이 낡았다 | 축 1(신선도)이 **정상적으로** 빨개지는 경우다. 축 2 와 갈라 적는다 |
| Actions 분이 소진돼 잡이 안 돈다 | AC-6 — steps=0 지문. **런이 없는 것**과 **런이 초록인 것**을 구별한다 |
| 스케줄 워크플로가 fork/portfolio 저장소에서도 돈다 | 기존 워크플로들의 `github.repository` 가드 관행을 따른다 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방어 |
|---|---|---|
| PR 시점 e2e 를 하나 더 만든다 | 칸은 늘고 배포판은 여전히 안 재짐 | Scope Out — 019 가 이미 걸었다 |
| 신선도만 재고 닫는다 | 신선한데 뚫린 판을 초록으로 통과 | AC-2 — 축이 둘 |
| `*.vercel.app` 만 찌른다 | 도메인이 끊겨도 초록 | AC-3 |
| 잡은 도는데 실패를 아무도 안 본다 | 빨강이 Actions 탭에서 늙는다 | AC-4 |
| «판정 불가»(exit 2)를 초록으로 접는다 | 측정이 죽은 채 영원히 초록 | AC-5 + 스크립트 헤더의 명시 |
| `302` 를 단언한다 | 실제 307 이라 정상 동작이 빨강 | AC-2 |
