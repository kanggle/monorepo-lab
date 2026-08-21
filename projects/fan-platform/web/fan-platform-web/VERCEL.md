# Vercel 배선 — `kanggle-fan` (TASK-MONO-562 · 563)

`vercel.json` 옆의 이 파일이 그 JSON 이 담을 수 없는 것을 담는다. **JSON 에는 주석이 없고,
`vercel.json` 은 스키마가 엄격하다.**

| | |
|---|---|
| Vercel 프로젝트 | **`kanggle-fan`** |
| Root Directory | **`projects/fan-platform/web/fan-platform-web`** — 2026-08-21 소유자가 대시보드에서 확인 |
| 빌드 | Next.js 15 App Router. 프레임워크 감지는 대시보드에 맡긴다 |
| `vercel.json` 이 선언하는 것 | `installCommand` + `ignoreCommand` 둘뿐 |
| 무시 규칙의 경로 목록 | **[`vercel-ignore.sh`](./vercel-ignore.sh)** — JSON 안이 아니다. 이유는 아래 § |

## 🔴🔴 스키마는 **길이도** 검사한다 — 561자가 아니라 **261자**가 이 프로젝트를 죽였다

이 파일의 이전 판은 첫 문단에서 이렇게 경고했다: *"`vercel.json` 은 스키마가 엄격해 모르는
최상위 키를 거부한다 — `TASK-MONO-557` 이 설명을 `"//installCommand"` 키로 끼워 넣었다가
배포를 연속으로 깼다."* **그리고 `TASK-MONO-562` 는 다른 문으로 같은 방에 들어갔다.**

공개 스키마(`https://openapi.vercel.sh/vercel.json`, 2026-08-21 실측 — 최상위 property **40개**,
`additionalProperties: false`)는 명령 문자열에 **`maxLength: 256`** 을 건다.

| 파일 | `ignoreCommand` 값 길이 | 한도 256 |
|---|---|---|
| `infra/demo/aws/site/vercel.json` (배포가 **되는** 쪽) | 129 | ✅ |
| 이 파일의 `vercel.json` (562 판) | **261** | ❌ **+5** |

562 는 pathspec 5개를 `ignoreCommand` 문자열에 **직접** 넣었고 5자가 넘쳤다. 결과:

- `vercel.json` 이 거부되어 **배포가 0초에 죽는다** — 빌드 로그조차 생기지 않는다.
- 커밋 상태 문구는 `Deployment failed.` + **project-configuration 문서 링크** — 557 이
  모르는 키로 받았던 **바로 그 링크**다.
- **`ignoreCommand` 는 영원히 실행되지 않는다.** 그래서 fan 은 무관한 커밋마다 배포를
  구웠고(`#3413`·`#3414` 실측), 같은 커밋에서 portfolio 는 `Canceled by Ignored Build Step`
  을 정상으로 냈다. **비대칭의 정체가 이것이다.**

🔵 **그래서 목록을 문자열 밖으로 뺐다.** [`vercel-ignore.sh`](./vercel-ignore.sh) 에는 길이
제한이 없고, 경로를 하나 더해도 `vercel.json` 은 길어지지 않는다(261 → **99자**).
제한 자체는 이제 `scripts/check-vercel-build-triggers.sh` 의 **칸 (5)** 가 지킨다 —
그 칸은 원문이 아니라 **디코드된 값**의 길이를 잰다(`\"` 는 원문 2자 · 값 1자라
grep 으로 세면 틀린다). 대조군: 562 판 파일을 그대로 물려 **`261자`** 라고 지목하는 것을 확인했다.

## Root Directory 는 확인됐다 — 후보 두 자리를 하나로 줄였다

`TASK-MONO-562` 는 이 값을 몰라서 **후보 두 자리에 같은 규칙을 뒀다**
(`projects/fan-platform/vercel.json` 과 이 파일). 2026-08-21 소유자가 확정했고, 읽히는 자리는
**이 디렉터리** 다. 얕은 쪽 사본은 삭제했다.

🔴 **사본을 남기지 않은 이유**: 읽히지 않는 설정 파일은 *무해* 가 아니라 **거짓 증거**다 —
규칙이 두 벌이면 다음 사람은 어느 쪽이 실제로 행사되는지 모르는 채 둘 다 고쳐야 하고,
한쪽만 고치면 그 사실이 아무 데서도 드러나지 않는다.

## 🔴 왜 `installCommand` 가 있는가 — 설치 지점이 CI 와 다르다

이 앱은 pnpm 워크스페이스의 **멤버**이고, `pnpm-lock.yaml` 과 `pnpm-workspace.yaml` 은
한 단계 위 `projects/fan-platform/` 에 있다. **Root Directory 는 그보다 깊다** ⇒ Vercel 이
설치를 시작하는 디렉터리에는 lockfile 이 **없다.**

2026-08-21 실측(두 컨텍스트를 각각 만들어 실제로 돌렸다):

| 설치 지점 | `CI=1 pnpm install --frozen-lockfile` | `pnpm build` |
|---|---|---|
| 워크스페이스 루트가 **보일 때** | ✅ rc=0 (39.6s) | ✅ rc=0 · 14 라우트 |
| Root Directory **만** (lockfile 없음) | ❌ `ERR_PNPM_NO_LOCKFILE` — **첫 명령에서 사망** | 도달 못 함 |
| Root Directory 만 + `--no-frozen-lockfile` | ✅ rc=0 (48.2s) | ✅ rc=0 · 14 라우트 |

`--frozen-lockfile` 은 **CI 환경에서 pnpm 이 기본으로 켜는 값**이다(pnpm 이 직접 그렇게
안내한다). Vercel 은 `CI=1` 이므로 아무도 요청하지 않아도 켜진다.

🔵 그래서 `installCommand` 는 새 정책이 아니라 **CI 가 fan 에 쓰는 것과 정확히 같은 명령**이다
— `.github/workflows/ci.yml` 의 fan 스텝 3곳이 전부 `pnpm install --no-frozen-lockfile` 이다
(ecommerce · console-web 은 `--frozen-lockfile`; fan 만 예외).

🔴 **이 두 결함은 서로 다른 층에 있다.** 길이 위반은 배포 **생성** 시점에, 설치 컨텍스트는
**빌드** 시점에 죽인다. 앞의 것을 고치기 전에는 뒤의 것이 실행조차 되지 않으므로,
길이만 고쳤을 때 설치가 다시 문제가 될 수 있다 — 그래서 둘 다 고쳤다.

### 남은 갈림길을 다음 커밋이 스스로 가른다

Vercel 에는 *"Root Directory 바깥 소스를 빌드 단계에 포함"* 설정이 있고 그 값도 대시보드에만 있다.

- **포함 ON** — pnpm 이 위로 올라가 워크스페이스를 찾는다(위 표 1행). `installCommand` 는
  lockfile 을 그대로 쓰되 갱신을 허용할 뿐이라 **해가 없다.**
- **포함 OFF** — 위 표 2행이 재현된다. `installCommand` 가 그것을 3행으로 바꾼다.
  🔴 다만 이 경우 `ignoreCommand` 는 판정자·래퍼가 빌드 컨텍스트에 없어 **발화하지 못한다.**

🔵 **관측으로 가른다.** 배포가 성공하기 시작한 뒤 **`tasks/**` 만 바꾼 커밋**을 보라:

| 그 커밋에서 `Vercel – kanggle-fan` 이 | 결론 |
|---|---|
| `Canceled by Ignored Build Step` | 포함 **ON** — 규칙이 행사된다. 끝 |
| 빌드하고 (성공이든 실패든) 돌았다 | 포함 **OFF** — 대시보드에서 켜야 한다 |

## 왜 `ignoreCommand` 가 필요한가

이 저장소는 Vercel 프로젝트가 **둘**이다(`kanggle-fan` + 론처 `kanggle-portfolio`). 그래서
**커밋 하나가 배포 둘을 굽는다.** 2026-08-19 의 티켓 파일링 러시(문서 전용 PR 13건)가 그대로
배포로 번역되어 무료 플랜 한도에 닿았고, 24시간 동안 모든 PR 이 빨개졌다.

🔴 **진짜 피해는 빨간 체크가 아니었다** — 그동안 **론처가 낡은 판을 계속 서빙했다.**
`TASK-MONO-560` 의 방문자 화면 링크가 머지된 뒤에도 방문자는 그 링크가 없는 페이지를 봤고,
URL 은 200 을 냈다. 배포가 죽은 것과 사이트가 죽은 것은 다른 사건이다.

## 트리거 경로

경로 목록과 각 항목의 근거는 [`vercel-ignore.sh`](./vercel-ignore.sh) 안에 있다 —
목록 옆에 두는 편이 여기 복사해 두는 것보다 낫다(같은 사실이 두 곳에 있으면 한쪽만 고쳐진다).

판정 규약과 fail-open 설계는
[`scripts/vercel-should-build.sh`](../../../../scripts/vercel-should-build.sh) 헤더에 있다.
