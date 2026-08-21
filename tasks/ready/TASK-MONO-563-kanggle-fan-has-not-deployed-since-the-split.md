# Task ID

TASK-MONO-563

# Title

`kanggle-fan` 은 **557 이후 한 번도 배포된 적이 없다** — rate limit 이 24시간 동안 그 실패를 자기 문구로 덮었다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- ci
- frontend

---

# 배경 — 2026-08-21 UTC, `TASK-MONO-562` 구현 중 대조군에서 드러남

`TASK-MONO-562` 가 Vercel 배포 rate limit 을 다루는 동안 창이 풀렸고, `kanggle-fan` 의 실패 문구가
바뀌었다:

```
이전:  Vercel – kanggle-fan   failure   Deployment rate limited — retry in 24 hours.
이후:  Vercel – kanggle-fan   failure   Deployment has failed  (0초, project-configuration 문서 링크)
```

562 가 fan 에 `vercel.json` 을 신설했으므로 **그 변경이 원인처럼 보였다.** 갈랐다.

## ◑ 대조군이 확정한 것 = **최초 원인이 아니다** ("무죄" 까지는 아니다 — 아래 § 추가 참조)

`origin/main` 에서 `projects/fan-platform/web/` 아래 파일 하나만 추가한 커밋을 만들어 브랜치로 밀었다.
**그 시점 main 에는 fan `vercel.json` 이 존재하지 않는다.** 결과:

```
Vercel – kanggle-fan   failure   Deployment has failed —
  run this Vercel CLI command: npx vercel inspect dpl_GbR77kk91nzn5TcVErVUpL1Xp4Zt --logs
```

⇒ **설정 없이도 깨진다.** `kanggle-fan` 은 562 이전부터 깨져 있었다.

🔴 **단 여기서 멈춰라.** 이것이 증명하는 것은 *"562 의 변경이 최초 원인이 아니다"* 뿐이다. *"562 의 변경이 아무 기여도 하지 않는다"* 는 **증명되지 않았고**, 아래 § 의 문구 두 종류가 그 가능성을 살려 둔다. 이 티켓의 초판은 이 자리에 "무죄 확정" 이라고 적었고 그건 과했다.

🔵 같은 실행에서 `kanggle-portfolio` 는 `Canceled by Ignored Build Step` 으로 **success** 를 냈다
(그 프로젝트에는 대시보드에만 있는 무시 규칙이 있다 — 562 § 부수 발견).

## 🔴🔴 진짜 크기 — 분리 이후 성공한 프로덕션 배포가 **0건**

`main` 의 Vercel 커밋 상태를 전수로 분류했다 (2026-08-19 이후, 19건):

| 컨텍스트 | 상태 | 건수 |
|---|---|---|
| `Vercel – kanggle-fan` | **failure** | 7 |
| `Vercel – kanggle-portfolio` | **failure** | 6 |
| `Vercel` (프로젝트가 하나였던 시절) | success | 6 |

**`TASK-MONO-557` 이 론처를 분리한 뒤 `main` 에서 성공한 프로덕션 배포가 한 건도 없다.**

## 🔴 왜 아무도 못 봤나 — 한 원인이 모든 실패에 **같은 문장**을 붙였다

rate limit 은 배포 **생성 시점**에 걸리므로, 원인이 무엇이든 실패는 전부
`Deployment rate limited — retry in 24 hours.` 로 보고된다. 즉 그 창 안에서는
**"한도 때문에 못 했다" 와 "빌드가 깨졌다" 가 구별되지 않는다.**

이 저장소가 이미 이름 붙인 실패 모드다 — fail-closed 장애가 다른 결함의 옷을 입는다
(`env_fail_closed_outage_impersonates_security_defect`). 그리고 `site/build.sh` 헤더의 명제와도
같은 과다: **"배포가 죽은 것과 사이트가 죽은 것은 다른 사건이다."** 여기서는 한 걸음 더 나아가
**"배포가 죽은 두 가지 다른 이유"** 가 구별되지 않았다.

## 🔵 앱 자체는 빌드된다 — 그것도 측정했다

CI 잡 **`Frontend lint & build (ecommerce + fan-platform)` 이 통과한다.** 즉
`projects/fan-platform/web/fan-platform-web` 의 `next build` 는 성립한다.
⇒ 결함은 **애플리케이션이 아니라 Vercel 프로젝트 설정 쪽**일 가능성이 높다.
🔴 그러나 이것은 **가설이지 측정이 아니다** — Vercel 빌드 로그를 아직 아무도 읽지 않았다.

## 🔴🔴 2026-08-21 — **두 커밋으로 비대칭이 확정됐다: portfolio 는 건너뛰고 fan 은 안 건너뛴다**

`TASK-MONO-562` 의 무시 규칙이 머지된 뒤, 성격이 다른 커밋 두 개가 같은 답을 냈다.

| 커밋 | 무엇을 건드렸나 | `kanggle-portfolio` | `kanggle-fan` |
|---|---|---|---|
| [#3413](https://github.com/kanggle/monorepo-lab/pull/3413) | `infra/demo/aws/site/**` 만 | `Deployment has completed` (자기 경로 ⇒ **빌드가 맞다**) | **빌드하고 실패** (건너뛰었어야 함) |
| [#3414](https://github.com/kanggle/monorepo-lab/pull/3414) | `tasks/**` 만 | **`Canceled by Ignored Build Step`** ✅ | **빌드하고 실패** (건너뛰었어야 함) |

⇒ **portfolio 쪽 규칙은 실전에서 작동한다**(무관한 커밋이 더는 배포를 굽지 않는다).
**fan 쪽은 두 번 다 발화하지 않았다.**

🔵 그리고 이 표가 가설 하나를 좁힌다: portfolio 에서 **무시 단계는 배포 파이프라인의 이른 시점에
실제로 실행된다**(그러니 "무시 단계가 돌기 전에 죽는다" 는 fan 에만 해당하는 특별한 사정이어야 한다).
남은 후보는 여전히 둘이고 **로그 없이는 못 가른다**:

- **(a) fan 의 Root Directory 가 저장소의 두 `vercel.json` 위치 중 어느 것도 아니다** ⇒ 규칙이 읽히지 않는다.
- **(b) fan 의 배포가 무시 단계 **이전** 단계에서 죽는다** ⇒ 규칙은 읽히지만 도달하지 못한다.

🔴 **(a)라면 이 티켓의 고침은 "빌드를 고치는 것" 만으로 끝나지 않는다** — Root Directory 를
확인해 규칙을 그 자리에 두는 것까지가 범위다. 그렇지 않으면 빌드를 고친 뒤에도 fan 은
**모든 커밋에 배포를 계속 굽는다**(562 가 없애려던 바로 그 상태).

## 🔴🔴 2026-08-21 추가 — **배포가 "낡은" 게 아니라 하나도 없다**, 그리고 실패 문구가 **두 종류**다

### (1) `kanggle-fan.vercel.app` 은 아무것도 서빙하지 않는다

```
curl -sL https://kanggle-fan.vercel.app/
  → HTTP 404   "The deployment could not be found on Vercel."   DEPLOYMENT_NOT_FOUND
```

🔵 **론처와 다른 상태다.** 론처는 배포가 막힌 동안 *마지막 성공 판을 계속 서빙*해서 겉으로 멀쩡했다
(그게 `TASK-MONO-562` 가 잡은 무성음 실패다). fan 은 **성공한 배포 자체가 없어서** 주소가 죽어 있다.
⇒ 이 프로젝트에는 "낡은 판" 이라는 상태가 존재한 적이 없다.

### (2) 실패 문구가 두 종류이고, 그 차이가 진단의 출발점이다

| 커밋 | fan `vercel.json` | description / target_url |
|---|---|---|
| `270ed172f` (562 이전 main) | 없음 | `Deployment rate limited` / `upgradeToPro=build-rate-limit` |
| **대조군 프로브** (main 기반) | **없음** | `Deployment has failed — npx vercel inspect dpl_GbR77…` / **배포 URL** |
| `25ac714d7` (#3413 head) | 있음 | `Deployment has failed — npx vercel inspect dpl_GQp5k…` / **배포 URL** |
| `0d5adb306` · `d1f263aa3` · `5d6f46212` (main) | 있음 | **`Deployment failed.`** / **`/docs/concepts/projects/project-configuration`** |
| `4ec303d21` · `8438cffc3` (PR head) | 있음 | 위와 같음 |

- **배포 URL 형태** = 배포가 생성되고 **빌드**가 실패했다 ⇒ `vercel inspect <id> --logs` 로 읽을 것이 있다.
- **project-configuration 문서 형태** = **설정 거부**의 모양. `TASK-MONO-557` 이 `"//installCommand"`
  주석 키로 배포를 깼을 때와 같은 링크다.

🔴 **이 표가 뒤집는 것:** 이 티켓의 초판은 *"562 의 변경은 무죄로 **확정**"* 이라고 적었다.
대조군이 증명한 것은 **"그 설정 없이도 실패한다"**(= 최초 원인이 아니다) 까지이고,
**두 문구가 갈린다는 사실은 그 설정이 기존 결함 위에 두 번째 실패 모드를 얹었을 가능성을
배제하지 못한다.** 게다가 `25ac714d7` 은 설정이 있는데도 빌드-실패 형태라 깔끔하게 갈리지도 않는다.
⇒ **"무죄 확정" 이 아니라 "최초 원인 아님 + 추가 기여 여부 미결" 이 정확한 상태다.**

### (3) AC-0 을 이 두 값으로 시작하라 — 한 번의 접속이면 된다

1. **Settings → Root Directory** — 규칙이 읽히는 자리인지(위 § 비대칭의 후보 (a)).
2. **최신 실패 배포의 Build Logs 첫 에러 줄** — 설정 거부인지 빌드 실패인지.

🔵 두 형태 중 **배포 URL 이 붙은 것**(`dpl_GQp5ks8xrB8Ksdm2YyHVvKsDj1XH`, `dpl_GbR77kk91nzn5TcVErVUpL1Xp4Zt`)
이 로그가 남아 있을 가능성이 높다 — 거기부터 열어라.

# Goal

`kanggle-fan` 이 다시 배포되게 하고, **"배포가 실패했다" 가 그 자체로 보이게** 한다
— rate limit 같은 상위 원인이 그 사실을 덮지 못하도록.

# Scope

## In Scope

- `kanggle-fan` Vercel 프로젝트의 빌드 실패 **원인 규명과 수정**.
- 그 수정이 저장소에 남는 형태여야 한다(대시보드 전용 설정이면 562 AC-2 와 같은 문제를 재생산한다).
- fan 프런트의 **배포 신선도 판정** — `TASK-MONO-562` 가 론처에 만든 것과 같은 축.

## Out of Scope

- 배포 트리거/무시 규칙의 **설계** — `TASK-MONO-562` 소관(판정자·가드·pathspec 형식).
  ⚠️ **단 fan 에서 그 규칙이 발화하도록 만드는 것은 In Scope 다.** 위 § 실측이 그 이유다 —
  규칙은 저장소에 있는데 fan 에서 두 번 다 안 걸렸고, 원인 후보 (a)는 **Root Directory** 이며
  그것을 확인하는 일과 빌드 로그를 읽는 일은 **같은 한 번의 접속**이다. 🔴 이걸 562 로
  미루면 *"빌드는 고쳤는데 fan 이 여전히 모든 커밋에 배포를 굽는"* 상태로 닫히게 된다.
- 유료 플랜 전환 — 소유자 판단(562 AC-4 에 숫자와 함께 정리돼 있다).
- fan 앱의 기능 변경.

# Acceptance Criteria

**AC-0 — 재측정 (verify-then-act). 🔴 인계된 진단을 쓰지 마라.**
착수 시점에 **빌드 로그를 실제로 읽는다**:

```
npx vercel inspect <deploymentId> --logs
```

⚠️ Vercel 대시보드/CLI 인증은 **사용자 승인 대상**이다.
🔴 위 § *"앱 자체는 빌드된다"* 는 **CI 에서 잰 것**이고 Vercel 빌드 환경은 다르다(Root Directory,
패키지 매니저 탐지, Node 버전). CI 초록을 Vercel 초록의 근거로 쓰지 마라 — 이 저장소가
`TASK-MONO-557` 에서 정확히 그렇게 틀렸다(루트 `pnpm-lock.yaml` 로 올라가 monorepo 전체를 설치했다).

**AC-1 — `main` 에서 프로덕션 배포가 성공한다.**
🔴 판정은 **커밋 상태가 초록** 이 아니다 — `Canceled by Ignored Build Step` 도 초록이다(562 실측).
판정은 **배포가 실제로 일어나고 서빙되는 판이 그 커밋인가**다.

**AC-2 — 원인이 저장소에 기록된다. 🔴 대조군 필수.**
수정이 대시보드 설정이면 그 값을 저장소 파일로 옮기거나, 옮길 수 없는 값이면
**왜 옮길 수 없는지**를 적는다. 🔴 대조군: 수정을 되돌렸을 때 **다시 실패하는지** 확인한다 —
확인하지 않으면 "창이 풀려서 통과한 것" 과 "고쳐서 통과한 것" 이 구별되지 않는다.
이 티켓 자체가 그 구별 실패에서 나왔다.

**AC-3 — 배포 실패가 상위 원인에 가려지지 않는다.**
rate limit 이든 할당량이든, **"이 프로젝트의 마지막 성공 배포는 언제/어느 커밋인가"** 를
물을 수 있어야 한다. `TASK-MONO-562` 가 론처에 만든 `build-info.json` +
`check-launcher-fresh.sh` 와 **같은 축**을 fan 에도 둔다.
🔴 대조군: 일부러 옛 판을 가리켰을 때 판정이 **다르다고 말하는지**(같은 값끼리 비교하면 언제나 통과).

**AC-4 — 상태 문구를 신뢰하지 않는 집계.**
`gh api .../status` 의 description 은 **원인이 아니라 최상위 증상**이다. 어떤 프로젝트의
배포 건강을 판단할 때 description 문자열로 분류하지 말고, **성공한 배포의 존재**로 판단하라.
(이 티켓의 실측: 13건 전부 failure 였고 그중 12건이 같은 문구였다.)

# Related Specs

- `infra/demo/aws/site/build.sh` — *"배포가 죽은 것과 사이트가 죽은 것은 다른 사건이다"* 의 출처
- `TASK-MONO-557` — 프로젝트를 둘로 나눈 결정. 이 결함이 시작된 지점
- `TASK-MONO-562` — 배포 트리거 규칙 + 론처 신선도 판정. 이 티켓의 형제
- `projects/fan-platform/web/fan-platform-web/VERCEL.md` — fan 쪽 배선 기록

# Related Contracts

없음 (배포 파이프라인 전용).

# Edge Cases

- **Root Directory 가 무엇인지 저장소는 모른다** — 562 가 후보 두 자리에 규칙을 뒀다.
  AC-0 에서 실제 값을 확인하면 한 자리로 줄여라.
- **창이 다시 닫힐 수 있다** — rate limit 은 롤링이다. 실패가 다시 같은 문구로 보고되면
  이 티켓의 판정이 또 불가능해진다. 그 경우 **AC-0 을 로그로** 수행하라(상태 문구가 아니라).
- **CI 와 Vercel 의 빌드 환경 차이** — Node 버전, 패키지 매니저 탐지 범위, 환경변수.
- 프리뷰 배포와 프로덕션 배포가 **다른 이유로** 실패할 수 있다(562 실측: 같은 커밋에서
  프리뷰는 success, 프로덕션은 낡은 판 그대로).

# Failure Scenarios

- **문구가 바뀐 것을 "고쳐졌다" 로 읽는 것.** rate limit 이 풀리면 문구는 반드시 바뀐다.
  그것은 창이 풀린 사건이지 수정이 아니다. AC-2 의 대조군이 이 축을 지킨다.
- **CI 초록을 근거로 닫는 것.** CI 는 Vercel 이 아니다(AC-0 각주).
- **무시 규칙으로 빨간 체크를 없애고 끝내는 것.** fan 이 자기 소스가 안 바뀌면 건너뛰므로
  체크는 사라진다 — 그러나 **빌드는 여전히 깨져 있다.** 562 가 각 `vercel.json` 을 자기
  트리거 경로에 넣어 둔 이유가 이것이고, AC-3 이 그 나머지 절반이다.

# Notes

- 분석 = **Opus 5** / 구현 권장 = **Sonnet** — 원인이 설정 한 줄일 가능성이 높다. 어려운 부분은
  코드가 아니라 **AC-2 의 대조군**(되돌렸을 때 다시 실패하는지)과 **AC-0 을 로그로 하는 것**이다.
- ⚠️ Vercel 대시보드/CLI 인증은 **사용자 승인 대상**이다.
- 관련 메모리: `env_fail_closed_outage_impersonates_security_defect`,
  `env_vercel_prod_deploy_not_triggered_by_merge`, `env_empty_detector_output_is_not_absence`.
