# Task ID

TASK-MONO-600

# Title

🔴 **배포된 팬 표면을 아무도 안 찌른다.** 라우트 가드가 프로덕션에서 3일간 죽어 있었고,
그것을 알아낸 것은 잡이 아니라 **사람이 손으로 `curl` 을 친 것**이었다.

# Status

review

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

---

# ✅ 판정 (2026-08-29 UTC) — 잡이 생겼고, 만드는 도중에 **감시자가 이미 눈이 멀어 있었다**

## AC-0 — 상속하지 않고 네 칸을 다시 찍었다

`curl --ssl-no-revoke`, 2026-08-29 04:2xZ, `https://fan.hubwang.com`:

| 경로 | 실측 | 뜻 |
|---|---|---|
| `/artists` | **307** → `/login?from=%2Fartists` | 가드가 닫는다 |
| `/nonexistent-xyz` | **307** → `/login?from=%2Fnonexistent-xyz` | 🔵 **판별자** — 018 때는 404 였다 |
| `/login` | **200** | 음성 대조군 — 리다이렉트 루프 아님 |
| `/api/auth/providers` | **200** | 설정 있음 |

⇒ 018 의 판정(“env 가 들어오자 가드가 돈다”)은 **오늘도 참**이다. 🔵 캡티브 포털에도
Deployment Protection 에도 안 걸렸다 — `curl 000` 없음, 본문 정상.

🔴 **그러나 이것은 사람이 손으로 잰 값이다.** 이 티켓이 존재하는 이유가 그것이고, 아래가
그 손을 대체한다.

## 🔴🔴 만드는 도중에 나온 것 — **신선도 판정자는 08-27 부터 눈이 멀어 있었다**

AC-2 의 “재사용하는가” 를 묻기 위해 `check-fan-fresh.sh` 를 **실제로 돌렸다**(선언을 읽지
않고). 결과는 **exit 2 = 판정 불가**였고, 스크립트가 적은 사유는

> `⇒ Vercel Deployment Protection(인증 벽) 의심`

였다. **틀렸다.** 리다이렉트를 안 따라가고 다시 재니:

```
/build-info.json  →  307  →  https://fan.hubwang.com/login?from=%2Fbuild-info.json
/robots.txt /sitemap.xml /favicon.ico  →  404   (matcher 가 제외한 것들)
```

원인은 인증 벽이 아니라 **우리 자신의 라우트 가드**다. `src/middleware.ts` 의 matcher 가
`build-info.json` 을 제외하지 않아 미인증 요청이 `/login` 으로 꺾였고, 스크립트는 로그인
HTML 을 본문으로 읽었다. ⇒ **가드가 살아난 2026-08-27 그날부터** 신선도 판정은 계속
«판정 불가» 였다. 🔴 **부르는 잡이 없어서 아무도 몰랐다** — 이 티켓의 ②가 바로 그
「러너 없는 스위트는 썩는다」이고, 그것이 스스로를 증명했다.

🔴 그리고 **틀린 사유가 더 나쁘다**: 다음 사람은 Vercel 설정을 뒤졌을 것이다. 고칠 곳은
`src/middleware.ts` 인데.
[[feedback_a_verifiable_mechanism_is_not_the_cause]] [[feedback_guard_predicate_wrong_verify_the_artifact]]

**고침 3종:**

1. `build-info.json` 을 matcher 의 공개 메타데이터 목록에 넣었다(robots.txt·sitemap.xml 과
   같은 등급). 🔵 **공개 저장소**의 `{commit, ref, builtAt}` 이라 비밀이 아니고, 배포 밖의
   감시자가 「어느 커밋이 살아 있나」를 물을 수 있는 **유일한 기계 판독 값**이다.
2. `auth-guard.spec.ts` 에 그 제외를 단언하는 칸을 넣었다 — 🔴 다시 막히면 감시자는
   **조용히** 눈이 머는데, 그 침묵은 「측정 못 함」의 얼굴로 오므로 리다이렉트 단언으로는
   안 잡힌다.
3. `check-fan-fresh.sh` 가 이제 **최종 URL 로 두 사유를 가른다** — 이 오리진의 `/login` 이면
   「앱의 라우트 가드」, 아니면 「Deployment Protection 의심」.

## AC-1 — **무엇이 찌르는가** = (A) 스케줄. 잡 = `fan-surface-watch`

`nightly-e2e.yml` 에 잡을 하나 더한다. 새 워크플로 파일을 만들지 않았다:

- 🔵 **선례가 그 파일 안에 있다** — `demo-image-liveness` 가 *“TIME 트리거가 필요해서
  `ci.yml` 이 아니라 여기 산다”* 는 같은 근거로 거기 있다. 표면 부패도 **diff 없이** 온다
  (소유자가 env 를 지우거나, 배포가 건너뛰어지거나, 도메인이 풀리거나).
- 🔵 `workflow_dispatch` · `github.repository` 가드 · 취소 그룹 정책을 그대로 물려받는다.

🔴 **(B) 배포 후 훅을 안 고른 이유**: 018 의 결함은 «배포가 안 됐다» 가 아니라 «배포된 판이
잘못됐다» 였다. 배포 이벤트에 매달린 판정자는 **배포가 안 일어난 동안**을 못 본다 — 그리고
팬은 `vercel-ignore.sh` 로 자주 건너뛰어진다.

🔴 **주기의 근거를 숫자로**: 이 결함은 **3일** 살아 있었다. 야간(1일)은 그것을 **3배** 빠르게
자른다. 더 조이는 것은 «하루 안에 열렸다 닫힌 사례» 가 **측정된 뒤**에 한다 — 이 계정은
스케줄 작업으로 한도를 태운 전력이 있다. [[feedback_a_figure_nothing_can_fail_on_will_drift]]

🔴 **`push` 에서는 일부러 안 돈다.** Vercel 은 머지 후 75초~5분 뒤에 게시하므로, 머지 직후
런은 **이전 빌드**를 읽고 «낡음» 이라 부른다 — 아무도 고칠 수 없는 이유로 빨간 가드는
꺼진다.

## AC-2 — 축이 **둘이고 하나가 다른 하나를 못 덮는다**

| 축 | 스텝 | 판정자 |
|---|---|---|
| ① 가드가 도는가 | `Axis 1 — the route guard closes` | **신규** `check-fan-guard-live.sh` |
| ② 서빙 판이 `main` 인가 | `Axis 2 — the serving build is main` | **재사용** `check-fan-fresh.sh` |

🔴 018 의 결함은 **신선한 판이 뚫려 있던 것**이라 ②만 있었으면 그 3일 내내 초록이다.
거울상(가드는 도는데 몇 주 낡은 판)은 ①만 있으면 초록이다. **둘 다이거나, 둘 다 무의미하다.**
`if: ${{ !cancelled() }}` 로 ①의 실패가 ②를 가리지 않게 했다.

**왜 새 파일인가**(재사용 vs 신규의 답): 두 축은 exit code 의 **의미가 다르다**
(신선도의 `1`=낡음, 가드의 `1`=안 닫힘). 한 파일에 합치면 rc 가 무엇을 말하는지 흐려진다.

**`check-fan-guard-live.sh` 의 칸**: `/nonexistent-xyz`(판별자) · `/artists` · `/me` 는
`/login` 으로 꺾여야 하고, 🔵 `/login` 은 200(음성 대조군), 🔵 `/api/auth/providers` 는
200(설정 대조군)이어야 한다. 마지막 칸이 있는 이유는 `TASK-FAN-FE-019` 이후 **설정이
없어도 가드가 닫히기** 때문이다 — 그것 없이는 «닫혔다» 와 «설정이 깨졌다» 가 같은 초록이 된다.

## AC-3 — 권위 호스트 = **`fan.hubwang.com`**

`check-fan-fresh.sh` 의 기본값은 `kanggle-fan.vercel.app` 이지만, 잡은 **사람이 보는
호스트**를 찌른다. 도메인 배선이 풀리면 `*.vercel.app` 은 초록인 채 방문자만 죽고, 그 상태를
아무도 안 본다. 🔵 스크립트 기본값은 안 바꿨다 — 다른 목적(프리뷰 대조)에 그 값이 유효하다.

## AC-4 — **실패를 누가 보는가**

이 워크플로가 이미 쓰는 채널과 **같다**: 기본 브랜치의 스케줄 런이 실패하면 ① main 배지가
빨개지고 ② GitHub 이 저장소 소유자에게 메일을 보낸다.

🔴 **그 한계를 여기 적어 둔다**: PR 에 체크로 안 붙고, 메일을 안 읽으면 Actions 탭에서
늙는다. 이슈 자동 생성 / 웹훅은 `nightly-e2e.yml` 헤더가 이미 **미해결**로 적어 둔 v2
범위(`ADR-MONO-011 § 6.1`)이고, 이 티켓이 그것을 새로 만들지 않았다.
⇒ **채널은 있고, 강도는 이 워크플로의 다른 11개 잡과 같다.**

## AC-5 — 🔴 가드가 **실제로 문다**(초록은 증거가 아니다)

| bite | 명령 | rc | 기대 |
|---|---|---|---|
| 도달 불가 | `--origin https://fan-does-not-exist.hubwang.com` | **2** | 2 («판정 불가» 를 0 으로 안 접는다) |
| 가드 없는 오리진 | `--origin https://example.com` | **1** | 1 (`404, 리다이렉트 없음 ⇒ 가드가 안 닫는다`) |
| 실제 표면 | (기본) | **0** | 0 |
| matcher 재차단 | `build-info.json` 제외 제거 후 e2e | **1 failed / 9 passed** | 그 칸만 빨강 |

🔵 두 번째 bite 가 특히 중요하다 — `example.com` 은 모든 경로에 404 를 주므로 **018 의
fail-open 지문과 같은 모양**이고, 그 상태에서 스크립트가 정확히 그 문장을 낸다.

### 🔴🔴 `--self-test` 가 **첫 실행에서 내 스크립트의 결함을 잡았다**

고정 입력 대조표를 돌리자마자 한 줄이 FAIL 이었다:

```
FAIL (307, 'https://evil.example.com/login') -> redirect-to-login  (기대: redirect-elsewhere)
```

초판 술어가 `*/login` 글롭이라 **남의 호스트로 던지는 리다이렉트를 «건강한 가드» 로**
읽었다. 즉 `NEXTAUTH_URL` 오설정으로 열린 리다이렉트가 생겨도 이 감시자는 초록을 냈을
것이고, 018 의 Edge Cases 가 *“리다이렉트가 `/login` 이 아니라 외부로 가면 **멈춘다**”* 로
이미 이름 붙인 사건이 **감시자에게 안 보였을** 것이다.

🔵 **라이브 실행은 이것을 영원히 못 잡는다** — 실제 Location 이 올바른 호스트이기 때문이다.
잡은 것은 «값만 다른 반대 쌍» 이 있는 고정 입력표뿐이다.
[[feedback_why_a_guard_does_not_bite]] [[feedback_control_group_design_four_axes]]

고친 뒤 표는 **10칸**이고, 🔴 개수를 문구에 하드코딩하지 않고 **세서** 말한다(행을 더하고
문구를 안 고치면 그 문구가 거짓이 된다). 🔵 케이스 0개면 «전부 통과» 가 아니라 «아무것도 안
쟀다» 로 exit 2.

## AC-6 — 실제 런 + 잡 이름

- 잡 이름: **`Deployed fan surface (the guard runs, and the build is main)`**
  (job id `fan-surface-watch`, `.github/workflows/nightly-e2e.yml`)
- `workflow_dispatch` 실측 런:
  [run 33234619412](https://github.com/kanggle/monorepo-lab/actions/runs/33234619412)
  (job `99053299297`, 브랜치 `feat/mono-600-deployed-surface-watch`) — **잡 결론 = failure**,
  그리고 **그것이 기대값이다**. 스텝별로:

  | # | 스텝 | 결론 |
  |---|---|---|
  | 3 | Self-test the live-guard classifier | **success** |
  | 4 | Axis 1 — the route guard closes | **success** |
  | 5 | Axis 2 — the serving build is main | **failure** |

  🔵 **두 축이 서로 다른 답을 냈다** — 이것이 «축이 둘이어야 한다»(AC-2)의 실측 증명이다.
  하나로 합쳤으면 이 구별이 사라졌다. ②의 실패 사유는 아래 «남은 것» 첫 항목(배포 한도로
  matcher 고침이 아직 라이브에 없음)이고, 잡이 말하려던 사실 그대로다.
- 🔴 **Actions 분 소진 지문**(스텝 0개 / 로그 없음)이 아님을 확인했다 — 스텝별 로그가 있고
  각 스텝의 결론이 개별로 기록됐다.

## 검증 (숫자)

| 게이트 | 결과 |
|---|---|
| `e2e:smoke` | **10 passed** (직전 9 + `build-info.json` 칸 1) |
| `lint` | rc=0 |
| `build` | rc=0, `ƒ Middleware 86.2 kB`, `[build-info] … commit=dc94fe560…` |
| `test`(unit) | 23 files / **153 passed** |
| `check-fan-guard-live.sh --self-test` | rc=0, 고정 입력 **10칸** |
| YAML | `nightly-e2e.yml` 12 jobs · `ci.yml` 51 jobs 파싱 OK |

`ci.yml` 의 `bash -n` 모집단에 새 스크립트를 넣었고, 🔴 **그 스텝의 주석이 거짓이 됐으므로
같이 고쳤다** — 이전 문구는 *“실행되는 곳이 없으므로 문법만이라도”* 였는데 이제 야간 잡이
fan 쪽 둘을 **실제로 실행한다**(론처 판정자는 여전히 부르는 잡이 없다).
[[feedback_retract_the_exemption_when_the_defect_is_fixed]]

---

## 🔴 남은 것 / 안 한 것

- 🔴🔴 **첫 야간 런의 ②축은 빨간색이 «맞다»**, 그리고 그것은 이 가드의 결함이 아니다.
  `build-info.json` 의 matcher 제외는 **배포돼야** 효력이 생기는데, 팬 배포는
  `Deployment rate limited — retry in 24 hours` 로 08-28 13:56Z 이후 생성조차 안 되고 있다
  (`61a8efbd7`·`dc94fe560` 둘 다). 그때까지 ②는 **exit 2 = 판정 불가**이고,
  🔴 그것을 0 으로 접지 않는 것이 이 저장소의 규칙이므로 잡은 RED 다.
  🔵 **해소 조건**: 한도가 풀린 뒤(가장 이른 창 **2026-08-29 14:48Z**) 팬 트리거 경로를
  건드리는 커밋이 하나 랜딩하면 ②가 초록이 된다. ①축은 지금도 초록이다.
  ⇒ 「첫날 RED 인 가드는 꺼진다」(`TASK-MONO-360`)의 위험을 알고 남긴다 — **끄지 마라.**
  RED 의 사유가 정확히 이 잡이 말하려던 사실(«배포된 판이 최신이 아니다»)이다.
- 🔴 **이 잡은 `korea-travel-guide`·web-store·console 표면을 안 잰다.** 팬 하나에서 한 번
  돌려 본 뒤 일반화하기로 한 범위 그대로다.
- 🔴 **인증에 성공한 사용자 경로는 여전히 아무도 안 잰다** — ①축이 재는 것은 «미인증이
  막히는가» 뿐이다. 로그인이 실제로 끝까지 도는지는 `TASK-MONO-574`(OIDC 왕복)의 축이다.
- 🔵 실패 채널의 강도는 이 워크플로의 다른 잡들과 **같고**, 그 이상은 만들지 않았다(AC-4).
