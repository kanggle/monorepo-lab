# Task ID

TASK-MONO-563

# Title

`kanggle-fan` 은 **557 이후 한 번도 배포된 적이 없다** — rate limit 이 24시간 동안 그 실패를 자기 문구로 덮었다

# Status

review

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

## 🔴🔴 2026-08-21 AC-0 (부분) — **Root Directory 를 소유자가 읽었고, 후보 (a)가 죽었다**

소유자가 대시보드에서 확인한 값:

```
kanggle-fan → Settings → Root Directory = projects/fan-platform/web/fan-platform-web
```

⇒ **`TASK-MONO-562` 가 후보 두 자리에 둔 규칙 중 깊은 쪽이 읽히는 자리다.**
그 자리에는 `vercel.json` 이 **있다.** 따라서 위 § 의 후보 (a) *"Root Directory 가 두 위치 중
어느 것도 아니라 규칙이 읽히지 않는다"* 는 **반증됐다.**

🔵 그리고 얕은 쪽(`projects/fan-platform/vercel.json`)은 읽히지 않는 것이 확정됐으므로 **삭제했다.**
읽히지 않는 설정 사본은 무해가 아니라 **거짓 증거**다 — 규칙이 두 벌이면 다음 사람은 어느 쪽이
행사되는지 모른 채 한쪽만 고치고, 그 사실이 아무 데서도 드러나지 않는다.

### 🔴 후보 (b)가 강화된다 — portfolio 가 판정자 도달 가능성을 이미 증명했다

`kanggle-portfolio` 의 `ignoreCommand` 도 `$(git rev-parse --show-toplevel)/scripts/…` 로
**저장소 루트 스크립트**를 부르고, 그것이 `#3414`·`#3415` 에서 `Canceled by Ignored Build Step`
을 실제로 냈다. ⇒ 무시 단계는 전체 클론을 본다(적어도 그 프로젝트에서는).
그런데 fan 은 규칙이 제자리에 있는데도 **두 커밋 다 발화하지 않았다.**
⇒ **fan 의 배포는 무시 단계에 도달하기 전에 죽는다**(후보 (b)).

## 🔴 2026-08-21 실측 — 설치 지점이 CI 와 다르고, 그 차이는 **첫 명령에서 치명적**이다

Root Directory 가 확정되자 검사 가능한 명제가 생겼다: **거기엔 lockfile 이 없다.**
`pnpm-lock.yaml` 과 `pnpm-workspace.yaml` 은 한 단계 위 `projects/fan-platform/` 에 있고,
이 앱은 그 워크스페이스의 **멤버**다. 두 컨텍스트를 각각 만들어 실제로 돌렸다
(`git archive` 로 커밋된 트리만 떼어냄):

| 설치 지점 | `CI=1 pnpm install --frozen-lockfile` | `pnpm build` |
|---|---|---|
| 워크스페이스 루트가 보일 때 | ✅ rc=0 (39.6s) | ✅ rc=0 · 14 라우트 |
| **Root Directory 만** | ❌ `ERR_PNPM_NO_LOCKFILE` — **첫 명령에서 사망** | 도달 못 함 |
| Root Directory 만 + `--no-frozen-lockfile` | ✅ rc=0 (48.2s) | ✅ rc=0 · 14 라우트 |

pnpm 자신이 그 실패에 이렇게 덧붙인다: *"Note that in CI environments this setting is true by
default."* Vercel 은 `CI=1` 이므로 **아무도 요청하지 않아도 frozen 이 켜진다.**

🔵 **폐기한 가설도 적는다** — 처음 의심한 것은 *"lockfile 이 낡았다"* 였다(CI 의 fan 스텝만
`--no-frozen-lockfile` 이라 그것을 숨길 수 있다). **틀렸다**: 워크스페이스 루트에서
`CI=1 pnpm install --frozen-lockfile --lockfile-only` 이 **rc=0** 이다. lockfile 은 매니페스트와
동기 상태다(둘 다 `22c4d7105` 에서 같이 바뀌었다). 결함은 lockfile 의 **내용**이 아니라 **위치**다.

🔵 CI 주석도 낡았다: *"No lockfile checked in yet"* 이라 적혀 있으나 `projects/fan-platform/pnpm-lock.yaml`
은 217KB 로 존재한다. (이 티켓의 범위 밖 — 고치려면 fan 스텝을 `--frozen-lockfile` 로 되돌리는
별도 작업이고, 그건 이 수정의 대조군을 오염시킨다.)

## 이번 변경 — 세 줄

1. `projects/fan-platform/web/fan-platform-web/vercel.json` 에 **`"installCommand": "pnpm install --no-frozen-lockfile"`**.
   🔵 새 정책이 아니라 **CI 의 fan 스텝 3곳이 이미 쓰는 그 명령**이다.
2. `projects/fan-platform/vercel.json` **삭제**(읽히지 않는 자리).
3. `VERCEL.md` — Root Directory 확정값, 위 실측표, 그리고 아래 § 판별 절차.

가드 `scripts/check-vercel-build-triggers.sh` 재실행: 본검사 rc=0(설정 2개), `--self-test` 4칸 전부
문다 — 특히 **(c) 설정 1개 삭제(총 2, 하한 2) → 하한 위반으로 문다**. 모집단이 3→2 로 줄었어도
그 칸이 공허해지지 않았다(전에 정확히 그 자리에서 공허해져 CI 가 잡아냈다).

## 🔴🔴 아직 원인이 **확정된 것이 아니다**

확정된 것은 *"이 기전은 실재하고, 이 한 줄이 그것을 없앤다"* 까지다.
**Vercel 빌드 로그는 여전히 아무도 읽지 못했다**(인증이 소유자 승인 대상) — AC-0 이 요구하는
것은 로그이고 위 표는 그 대체물이 아니다. 이 저장소가 이미 이름 붙인 함정이다:
**검증 가능한 기전은 원인이 아니다.**

⇒ **이 PR 의 `Vercel – kanggle-fan` 체크가 그 자체로 측정이다.**

| PR 에서 그 체크가 | 읽는 법 |
|---|---|
| **성공** | 설치 컨텍스트가 원인이었다. AC-1 후보 성립 → 되돌림 대조군(AC-2)으로 확정 |
| **여전히 실패, 문구가 바뀜** | 첫 결함은 넘었고 **두 번째**가 있다. 새 문구가 다음 단서 |
| **여전히 실패, 문구 동일** | 설치 이전에 죽는다 ⇒ 로그 없이는 못 간다. 질문이 훨씬 좁아짐 |

## 남은 갈림길 — `tasks/**` 만 바꾼 다음 커밋이 스스로 가른다

Vercel 의 *"Root Directory 바깥 소스를 빌드 단계에 포함"* 도 대시보드 전용 값이다.
**OFF 라면** 판정자(`/scripts/vercel-should-build.sh`)가 빌드 컨텍스트에 없어
`ignoreCommand` 는 영원히 발화하지 못하고, fan 은 **모든 커밋에 배포를 굽는다**
(= 이 티켓 § "(a)라면 …" 이 경고한 상태가 (b) 경로로도 성립한다).

| 배포가 성공하기 시작한 뒤, `tasks/**` 만 바꾼 커밋에서 fan 이 | 결론 |
|---|---|
| `Canceled by Ignored Build Step` | 포함 **ON** — 규칙이 행사된다 |
| 빌드하고 돌았다 | 포함 **OFF** — 대시보드에서 켜야 한다(소유자 승인 대상) |

🔴 이 칸은 **fan 이 한 번은 배포에 성공한 뒤에만** 읽을 수 있다. 그 전에는 실패가 두 원인을
다시 같은 문구로 덮는다 — 이 티켓이 태어난 바로 그 이유.

## 🔴🔴 2026-08-21 — **원인을 찾았다: `ignoreCommand` 가 261자이고 스키마 한도는 256자다**

첫 수리(`installCommand`)를 올린 PR [#3416](https://github.com/kanggle/monorepo-lab/pull/3416)
의 fan 체크가 **여전히 실패**했고, 그 원본 상태가 두 가지를 알려 줬다.

### (1) 실패 문구 두 종류는 **지문이 아니었다** — 같은 배포에 동시에 붙는다

```
2026-08-21T15:00:16Z  Vercel – kanggle-fan  failure
    desc=Deployment failed.
    url=.../docs/concepts/projects/project-configuration
2026-08-21T15:00:16Z  Vercel – kanggle-fan  failure
    desc=Deployment has failed — npx vercel inspect dpl_AacdiYBhHZ2H5rX1Qybes4GBH9S7 --logs
    url=.../kanggle-fan/AacdiYBhHZ2H5rX1Qybes4GBH9S7
```

**같은 초에 두 상태가 다 달렸다.** ⇒ 이 티켓 위 § 이 *"배포 URL 형태 = 빌드 실패 / 문서 링크
형태 = 설정 거부"* 라고 세운 판별표는 **틀렸다.** 어느 쪽이 보이느냐는 API/UI 가 무엇을
집어 오느냐의 문제였다. 🔴 **커밋마다 한 종류만 보였던 것은 표본이 아니라 표시였다.**

🔵 다만 같은 원본이 다른 것을 줬다: 같은 실행에서 `kanggle-portfolio` 는 15:00:16 에 아직
**`pending`**("Vercel is deploying your app")인데 fan 은 **이미 `failure`** 다.
⇒ **fan 은 설치가 돌 시간조차 없었다.** 첫 수리가 도달하지 못한 층에서 죽는다.

### (2) 인증 없이 잴 수 있는 축이 남아 있었다 — **공개 스키마**

`vercel.json` 의 `$schema` 는 공개 URL 이다. 받아서 두 설정을 대조했다
(`https://openapi.vercel.sh/vercel.json`, 413,562 bytes — 최상위 property **40개**,
`additionalProperties: false`):

| 파일 | 스키마에 없는 키 | `ignoreCommand` 값 길이 | 한도 **256** |
|---|---|---|---|
| `infra/demo/aws/site/vercel.json` (배포 **성공**) | 없음 | **129** | ✅ |
| `projects/fan-platform/web/fan-platform-web/vercel.json` | 없음 | **261** | ❌ **+5** |

**`maxLength: 256`.** `TASK-MONO-562` 는 pathspec 5개를 그 문자열에 직접 넣었고 5자가 넘쳤다.

🔴🔴 **그리고 이 결함은 이 저장소가 이미 이름 붙여 둔 클래스다.** `VERCEL.md` 의 첫 문단이
*"스키마가 엄격해 모르는 최상위 키를 거부한다 — 557 이 `"//installCommand"` 로 배포를 깼다"*
라고 경고하고 있었다. **557 은 모르는 키로, 562 는 길이로 같은 방에 들어갔다.**
경고문을 쓴 변경이 그 경고를 어겼다.

### 이것이 관측 전부를 설명한다

- **0초 실패** — 설정 거부는 배포 *생성* 시점이다. 빌드 로그가 없는 이유.
- **`project-configuration` 링크** — 557 이 모르는 키로 받았던 바로 그 링크.
- **무시 규칙이 fan 에서만 안 돈다** — `ignoreCommand` 가 **실행된 적이 없다.**
  같은 커밋에서 portfolio(129자)는 정상 발화했다. **비대칭의 정체.**
- **`DEPLOYMENT_NOT_FOUND`** — 562 이후 성공한 배포가 하나도 없다.

🔵 **단 562 이전의 실패까지 이것으로 설명되지는 않는다.** 대조군 프로브(= fan `vercel.json` 이
**없는** 트리)도 실패했고, 그 트리엔 길이 위반이 존재할 수 없다. ⇒ **결함은 둘이고 층이 다르다**:
길이 위반은 배포 **생성** 시점, 설치 컨텍스트는 **빌드** 시점. 앞의 것을 고치기 전에는 뒤의 것이
실행조차 되지 않는다. 그래서 둘 다 고쳤고, **어느 쪽이 562 이전 실패의 원인이었는지는 여전히
빌드 로그로만 확정된다.**

🔴 그리고 이것으로 이 티켓의 *"추가 기여 여부 미결"* 이 **결론난다: 562 의 변경은 두 번째
실패 모드를 얹었다.** 최초 원인은 아니었지만 무죄도 아니다.

## 수리 — 목록을 문자열 밖으로

`ignoreCommand` 에 경로를 우겨넣는 모양 자체가 재발 장치다. 목록을 **프로젝트 소유 래퍼**로 옮겼다:

- 신설 `projects/fan-platform/web/fan-platform-web/vercel-ignore.sh` — pathspec 5개 + 각각의 근거.
  공용 판정자는 프로젝트를 몰라야 하므로(HARDSTOP-03) 인자로 받는 구조는 그대로다.
- `vercel.json` 의 `ignoreCommand` 는 그 래퍼를 부르기만 한다 — **261 → 99자.**
  경로를 더해도 이제 문자열은 길어지지 않는다.

### 가드 — 이 축을 아무도 안 보고 있었다

`scripts/check-vercel-build-triggers.sh`:

- **칸 (5) 신설** — 최상위 문자열 값의 길이 ≤ 256. 🔴 **원문이 아니라 디코드된 값**으로 잰다
  (`\"` 는 원문 2자·값 1자라 grep 으로 세면 틀리고, 이 판정은 **5자 차이**로 갈렸다).
  node 가 없으면 조용히 건너뛰지 않고 **크게 실패**한다(검사기가 죽은 것과 위반이 없는 것은 다른 사건이다).
- **추출이 래퍼를 따라간다** — 안 그러면 정상 설정에 칸 (4)가 오발화한다.
- **자기시험 (e)** — 한도 초과를 주입해 무는지. 🔴 **무는지 읽기 전에 주입됐는지 단언**한다
  (주입 0건이면 "안 물었다" 와 "시험한 적 없다" 가 구별되지 않고 후자는 초록으로 보인다).
- **자기시험 (f)** — **래퍼 쪽** pathspec 을 망가뜨려 무는지. 래퍼를 못 찾으면 통과가 아니라 **실패**로 낸다.
- CI: 경로 필터에 `**/vercel-ignore.sh`, 그리고 래퍼들의 `bash -n`(가드는 pathspec 을 grep 으로
  뽑으므로 **문법 오류를 못 본다** — 목록은 멀쩡해 보이는데 배포 시점에 래퍼가 죽는다).

**🔵 대조군(합성이 아님)**: 562 판 트리를 그대로 새 가드에 물렸더니
`(5) ignoreCommand 가 261자입니다 — 한도 256자 초과` 로 **정확한 수치를 지목**했다.
자기시험 **6칸 전부 문다**. 본검사 rc=0.

🔴 **폐기한 가설도 남긴다**: 이 세션에서 처음 의심한 것은 *"lockfile 이 낡았다"* 였다. 틀렸다
(`--frozen-lockfile --lockfile-only` rc=0). 두 번째는 *"설치 컨텍스트"* 였고 그것은 **실재하지만
이 층의 원인은 아니었다** — 배포가 거기까지 가지 못한다. **검증 가능한 기전은 원인이 아니다**를
한 세션에 두 번 밟았다.

## ✅ 2026-08-21 15:17 UTC — **분리 이후 첫 성공 배포.** 그리고 AC-2 대조군이 **자연스럽게** 잡혔다

`Vercel – kanggle-fan  success  Deployment has completed`

되돌림 대조군을 따로 만들 필요가 없었다 — **같은 브랜치의 직전 커밋이 정확히 "수리를 되돌린
상태"** 였기 때문이다. 두 커밋은 16분 간격이고, 프로젝트 설정도 rate-limit 창도 같다.

| 커밋 | `installCommand` | `ignoreCommand` | 배포 상태 추이 |
|---|---|---|---|
| `d3877859f` | ✅ 있음 | **261자** | `pending` **없음** — failure 둘이 **같은 초**(15:00:16Z). **0초** |
| `7fcf7d619` | ✅ 있음 | **99자** | `pending` 15:16:48 → **success** 15:17:54 — **66초, 진짜 빌드** |

🔵 **`pending` 을 거쳤다는 것 자체가 판별자다.** 설정 거부도 rate limit 도 `pending` 에
도달하지 못한다. 이전 실패들은 전부 `pending` 없이 곧장 failure 였다.

⇒ **길이가 원인이었다는 것이 라이브에서 격리됐다.** `installCommand` 는 두 커밋 모두에
있었으므로 이 대조군의 변수가 아니다.

🔴 **그래서 `installCommand` 의 *필요성* 은 증명되지 않았다.** 증명된 것은
*"길이를 고치면 배포가 성공한다(installCommand 가 있는 상태에서)"* 이다. 없었어도 성공했을지는
시험하지 않았다 — 다만 562 이전(= `vercel.json` 자체가 없어 Vercel 기본 설치 = frozen)에도
실패했다는 기존 관측이 그 방향을 지지하고, 이 세션의 실측이 그 기전을 재현했다
(`ERR_PNPM_NO_LOCKFILE`). **지지지 증명은 아니다.**

🔴 **AC-1 은 아직 미결이다** — 이것은 PR 브랜치의 **프리뷰** 배포다.
`kanggle-fan.vercel.app` 은 여전히 **404**(프로덕션 배포는 `main` 에서만 생긴다).
AC-1 의 판정은 *"머지 후 프로덕션이 그 커밋을 서빙하는가"* 이고, 그것은 머지 이후에 잰다.

## ✅ 2026-08-21 15:20 UTC — **무시 규칙이 fan 에서 처음 발화했다. 남은 갈림길이 닫혔다**

`62a43e817` 은 `tasks/**` 만 건드린다 — 이 티켓이 세워 둔 판별 칸이 그대로 돌았다.

```
Vercel – kanggle-fan        success   Canceled by Ignored Build Step
Vercel – kanggle-portfolio  success   Canceled by Ignored Build Step
```

⇒ **"Root Directory 바깥 소스를 빌드 단계에 포함" 은 ON 이다.** 판정자
(`/scripts/vercel-should-build.sh`)와 래퍼가 빌드 컨텍스트에서 도달 가능하다.
**대시보드 변경은 필요 없다.**

🔵 그리고 이것이 원인 진단을 한 번 더 확증한다 — 규칙 자체는 562 때부터 옳았고,
**`vercel.json` 이 거부되어 실행될 기회가 없었을 뿐이다.** 길이를 고치자 같은 규칙이
아무 수정 없이 발화했다.

🔵 `TASK-MONO-562` 의 절감 효과가 이제 **두 프로젝트 모두**에서 실현된다
(562 는 *"fan 쪽은 아직 발화하지 않는다 — 그때까지 절감은 portfolio 쪽만"* 으로 닫혔다).

## ✅ 2026-08-21 15:41 UTC — **AC-1 닫힘.** 프로덕션이 서빙한다

`#3416` 머지(squash `d74c5d56b`) 후 `main` 에서:

```
Vercel – kanggle-fan  pending -> success  Deployment has completed
```

| URL | 직전 | 지금 |
|---|---|---|
| `kanggle-fan.vercel.app/` | 404 `DEPLOYMENT_NOT_FOUND` | **200** (11,842 B) |
| `/artists` | — | **200** (13,107 B) |
| `/login` | — | **200** (10,065 B) |

**`TASK-MONO-557` 이 프로젝트를 나눈 뒤 처음으로 fan 프로덕션이 존재한다.**

🔴 **단 "서빙 중인 판이 그 커밋인가" 는 이 시점에 물을 수 없었다** — 물을 계기판이 없기
때문이다. 그것이 정확히 AC-3 이고, 아래에서 만든다.

## AC-3 — fan 에 신선도 축을 둔다. 🔵 **론처의 판정자를 복사하면 틀린다**

`infra/demo/aws/site` 는 정적 문서라 **서빙 바이트의 md5** 가 판정 축으로 성립한다.
이 앱은 다르다 — `next build` 결과가 라우트 14개 중 대부분 **동적(`ƒ`)** 이라 같은 커밋이라도
응답 바이트가 요청마다 달라질 수 있다. **바이트를 재면 건강한 배포에도 "낡음" 이 나온다.**
⇒ fan 의 축은 **커밋 하나**다.

🔴 그 대신 **잃는 것을 적어 둔다**: 이 판정자는 배포 후 누가 문서를 바꿔치기한 경우를 못 본다
(론처 쪽은 md5 축이 있어 그것을 본다). 여기서 그 축은 성립하지 않는다.

### 만든 것

- **`scripts/write-build-info.mjs`** — `next build` 직전에 `public/build-info.json` 을 쓴다
  (`{commit, ref}`). Vercel 이 주는 `VERCEL_GIT_COMMIT_SHA` 가 1순위, 없으면 git, 둘 다 없으면
  **`unknown`**.
  🔴 **타임스탬프를 넣지 않았다** — 커밋이 말하지 않는 것을 말해 주지 않으면서 빌드마다 값이
  달라지고, 이 저장소는 KST 호스트/UTC CI 날짜 축에서 이미 값을 치렀다.
  🔴 **이 스크립트는 빌드를 깨뜨리지 않는다.** Docker 이미지도 같은 `build` 스크립트를 타는데
  거기엔 `.git` 도 `VERCEL_*` 도 없다 — **실증**: 그 조건에서 `pnpm build` **rc=0** 이고
  `{"commit":"unknown","ref":"unknown"}` 을 썼다. **없는 파일과 모른다고 적힌 파일은 다르고**,
  판정자는 `unknown` 을 신선이 아니라 **판정 불가**로 읽는다.
- **`check-fan-fresh.sh`** — `0 = 신선 / 1 = 낡음·다름 / 2 = 판정 불가`. 양방향 `rev-list` 로
  뒤처짐/앞섬을 구분하고(한 방향만 세면 "앞섬"이 "같음"으로 읽힌다), 정문 `/` 의 HTTP 는
  **판정이 아니라 부수 관측**으로 따로 낸다.
- **`--self-test`** — 같은 오리진에 **기준만 둘**(현재 ref → 0, 이 앱을 바꾼 이전 커밋 → 1).
  🔵 **배포가 건강해도 영원히 성립한다.** 562 의 초판 대조군("오리진 둘이 다른 판정을 내는가")은
  결함의 존재에 의존해 둘 다 고쳐지자 죽었다.
- CI: 두 판정자 모두 `bash -n` 게이트에 넣고 경로 필터에 추가했다. 🔴 **실행되는 곳이 없는
  도구는 조용히 썩고, 필요해지는 바로 그 순간에 발견된다** — 론처 쪽 판정자도 지금까지
  아무 게이트가 없었다(이번에 같이 닫았다).

### 🔵 첫 실행이 "판정 불가" 를 정확히 냈다 — 그것이 이 축의 존재 이유다

배포판은 이 변경 **이전** 빌드라 `build-info.json` 이 없다:

```
[fan-fresh] ✖ .../build-info.json — 최종 HTTP 404
[fan-fresh]   ⇒ **판정 불가**(낡음이 아니다). build-info.json 이전 판이거나 자산이 안 올라갔다.
rc=2
```

**"낡음" 이 아니라 "판정 불가" 로 낸 것이 맞다** — 이 시점에 우리는 서빙판이 어느 커밋인지
모른다. 2 를 0 으로 접었다면 이 축은 태어나자마자 거짓말을 시작했을 것이다.

🔴 **작성 중에 두 번 물렸고 둘 다 이 저장소가 이미 이름 붙인 함정이다**:
① 실패 메시지 안의 백틱이 **명령치환**돼 `build-info.json: command not found` 가 났다 —
   rc 는 2 로 **맞았지만 이유가 틀렸다**.
② 진단이 stdout 으로 나가 `$(served_commit …)` 에 **통째로 삼켜졌다** — rc 만 맞고 화면은 비었다.
   ⇒ 이 판정자의 stdout 은 값 전용이고 사람 출력은 전부 stderr 다.

### 🔵 판정자를 **실행해서** 증명했다 — 6칸, 머지를 기다리지 않고

로컬 HTTP 오리진(`python -m http.server`)에 `build-info.json` 을 원하는 값으로 놓고 실제로 돌렸다.
**설계 검토가 아니라 실행이다.**

| 칸 | 서빙 값 | 기준 | 결과 |
|---|---|---|---|
| A | `HEAD` | `HEAD` | **0** 신선 |
| B | 이전 판 | `HEAD` | **1** 낡음 — "커밋 **1개 뒤처짐**" |
| C | `HEAD` | `--self-test` | **0** — 현재기준=0 · 이전판기준=1, 그리고 **"앞서 있다"** 를 정확히 구분 |
| D | `commit=unknown` | `HEAD` | **2** 판정 불가 |
| E | HTML 200 (인증 벽) | `HEAD` | **2** 판정 불가 + 원인 지목 |
| F | 404 | `HEAD` | **2** 판정 불가 |

🔵 **C 가 양방향 계수의 값어치를 그 자리서 보여줬다.** 이전 판을 기준으로 대면 서빙본이
**앞서** 있고, 한 방향만 셌다면 `behind=0` 이 나와 **"같다" 로 읽혔을** 것이다. 이 저장소가
562 에서 정확히 그렇게 틀렸다.

### 🔴 프리뷰 배포는 잴 수 없다 — **200 이 곧 JSON 은 아니다**

이 PR 의 프리뷰(`kanggle-ql6451snq-…`)로 재려 했더니 `build-info.json` 이 **HTTP 200 으로
`<!DOCTYPE html>`** 을 냈다 — Vercel **Deployment Protection** 의 인증 벽이다
(`data-dpl-id` 지문). 초판은 이것을 *"commit 을 못 뽑았습니다"* 로 뭉뚱그렸고, 그러면 다음
사람이 **빌드를 의심하며** 시간을 쓴다. 실제로는 **읽을 권한이 없는 것**이고 전혀 다른 사건이다.
⇒ 판정자가 그 상태를 **이름으로 부르고** 프로덕션 오리진으로 재라고 말한다(칸 E).

⏳ **남은 한 칸**: 이 PR 이 머지되어 `build-info.json` 을 담은 배포가 생긴 뒤
`bash check-fan-fresh.sh --self-test` 를 돌려 **현재기준=0 · 이전판기준=1** 을 확인한다.
그때까지 AC-3 은 **부분**이다.

## AC 진행 상태

| AC | 상태 |
|---|---|
| AC-0 | **부분** — Root Directory 를 읽었고, **공개 스키마로 위반을 직접 측정**했다(261 > 256). **빌드 로그는 여전히 못 읽었다**(승인 대상) |
| AC-1 | **충족** — `main` 프로덕션 배포 성공(`d74c5d56b`), `/`·`/artists`·`/login` 전부 **200**. 557 분리 이후 처음 |
| AC-2 | **부분** — 원인이 `VERCEL.md` + `vercel-ignore.sh` + 이 절에 남았고, **가드 칸 (5)가 562 판 파일을 `261자` 로 지목하는 대조군**을 통과했다. 그리고 **라이브 되돌림 대조군이 자연스럽게 잡혔다**(`d3877859f` 261자=0초 실패 / `7fcf7d619` 99자=66초 성공, 같은 브랜치·16분 간격) |
| AC-3 | **부분** — 축을 만들었다(`write-build-info.mjs` + `check-fan-fresh.sh` + 자기시험 + CI 문법 게이트). 판정자를 **실행해 6칸 전부 확인**했다(신선/낡음/자기시험/unknown/인증벽/404). 남은 것은 **프로덕션이 실제로 그 파일을 내는지** 한 번 보는 것 |
| AC-4 | **충족** — 이 티켓의 집계가 문구가 아니라 *성공한 배포의 존재* 로 판단하고 있다 |


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
