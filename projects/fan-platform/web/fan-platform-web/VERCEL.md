# Vercel 배선 — `kanggle-fan` (TASK-MONO-562 · 563)

`vercel.json` 옆의 이 파일이 그 JSON 이 담을 수 없는 것을 담는다. **JSON 에는 주석이 없고,
`vercel.json` 은 스키마가 엄격해 모르는 최상위 키를 거부한다** — `TASK-MONO-557` 이 설명을
`"//installCommand"` 키로 끼워 넣었다가 배포를 연속으로 깼다(`45bdca743` 성공 →
`ea9d5e79c`·`2679b8e41` 실패). 설명의 집은 설정 파일이 아니다.

| | |
|---|---|
| Vercel 프로젝트 | **`kanggle-fan`** |
| Root Directory | **`projects/fan-platform/web/fan-platform-web`** — 2026-08-21 소유자가 대시보드에서 확인 |
| 빌드 | Next.js 15 App Router. 프레임워크 감지는 대시보드에 맡긴다 |
| `vercel.json` 이 선언하는 것 | `installCommand` + `ignoreCommand` **둘뿐** |

## Root Directory 는 확인됐다 — 후보 두 자리를 하나로 줄였다

`TASK-MONO-562` 는 이 값을 몰라서 **후보 두 자리에 같은 규칙을 뒀다**
(`projects/fan-platform/vercel.json` 과 이 파일). 2026-08-21 소유자가 대시보드 값을 읽어
확정했고, 규칙이 읽히는 자리는 **이 디렉터리** 다. 그래서 얕은 쪽 사본은 삭제했다.

🔴 **사본을 남기지 않은 이유**가 중요하다. 읽히지 않는 설정 파일은 *무해* 가 아니라
**거짓 증거**다 — 저장소에 규칙이 두 벌 있으면 다음 사람은 어느 쪽이 실제로 행사되는지
모르는 채 둘 다 고쳐야 하고, 한쪽만 고치면 그 사실이 아무 데서도 드러나지 않는다.
(이 저장소가 이미 이름 붙인 실패 모드: 한 사실이 두 곳에 있으면 한쪽만 고쳐진다.)

## 🔴 왜 `installCommand` 가 있는가 — **설치 지점이 CI 와 다르다**

이 앱은 pnpm 워크스페이스의 **멤버**이고, `pnpm-lock.yaml` 과 `pnpm-workspace.yaml` 은
한 단계 위 `projects/fan-platform/` 에 있다. **Root Directory 는 그보다 깊다.**
⇒ Vercel 이 설치를 시작하는 디렉터리에는 lockfile 이 **없다.**

2026-08-21 실측(두 컨텍스트를 각각 만들어 실제로 돌렸다):

| 설치 지점 | `CI=1 pnpm install --frozen-lockfile` | `pnpm build` |
|---|---|---|
| 워크스페이스 루트가 **보일 때** (`projects/fan-platform` 포함) | ✅ rc=0 (39.6s) | ✅ rc=0 · 14 라우트 |
| Root Directory **만** (lockfile 없음) | ❌ `ERR_PNPM_NO_LOCKFILE` — **첫 명령에서 사망** | 도달 못 함 |
| Root Directory 만 + `--no-frozen-lockfile` | ✅ rc=0 (48.2s) | ✅ rc=0 · 14 라우트 |

`--frozen-lockfile` 은 **CI 환경에서 pnpm 이 기본으로 켜는 값**이다(pnpm 이 직접 그렇게
안내한다). Vercel 은 `CI=1` 이므로 아무도 그걸 요청하지 않아도 켜진다.

🔵 **그래서 `installCommand` 는 CI 가 fan 에 쓰는 것과 정확히 같은 명령이다.**
`.github/workflows/ci.yml` 의 fan 스텝 3곳이 전부 `pnpm install --no-frozen-lockfile` 이다
(ecommerce·console-web 은 `--frozen-lockfile`; fan 만 예외). 즉 이 한 줄은 새 정책이 아니라
**이미 CI 가 서 있는 자리를 Vercel 에도 적용한 것**이다.

🔴 **이것이 원인이라고 확정된 것은 아니다.** 확정된 것은 *"이 기전은 실재하고, 이 한 줄이
그것을 없앤다"* 까지다. Vercel 빌드 로그는 아직 아무도 읽지 못했다(인증이 소유자 승인 대상).
`TASK-MONO-563` AC-0 이 요구하는 것은 로그이고, 이 표는 그 대체물이 아니다.

### 남은 갈림길을 다음 커밋이 스스로 가른다

Vercel 에는 *"Root Directory 바깥 소스를 빌드 단계에 포함"* 설정이 있고 그 값도 대시보드에만 있다.

- **포함 ON** — pnpm 이 위로 올라가 워크스페이스를 찾는다. 위 표 1행. `installCommand` 는
  lockfile 을 그대로 쓰되 갱신을 허용할 뿐이라 **해가 없다.**
- **포함 OFF** — 위 표 2행이 그대로 재현된다. `installCommand` 가 그것을 3행으로 바꾼다.
  🔴 **다만 이 경우 `ignoreCommand` 는 영원히 발화하지 못한다** — 판정자
  (`/scripts/vercel-should-build.sh`)가 빌드 컨텍스트에 없기 때문이다. 그러면 fan 은
  **모든 커밋에 배포를 굽는다**(`TASK-MONO-562` 가 없애려던 상태).

🔵 **관측으로 가를 수 있다.** 배포가 성공하기 시작한 뒤 **`tasks/**` 만 바꾼 커밋**을 보라:

| 그 커밋에서 `Vercel – kanggle-fan` 이 | 결론 |
|---|---|
| `Canceled by Ignored Build Step` | 포함 **ON** — 규칙이 행사된다. 끝 |
| 빌드하고 (성공이든 실패든) 돌았다 | 포함 **OFF** — 대시보드에서 켜야 한다 |

`kanggle-portfolio` 는 이미 그 칸에서 `Canceled by Ignored Build Step` 을 냈다
(`#3414`, `#3415`) — 그러니 **portfolio 쪽은 판정자가 도달 가능하다**는 것이 실측돼 있고,
설정은 프로젝트별이므로 fan 에 그대로 옮겨 쓸 수는 없다.

## 왜 `ignoreCommand` 가 필요한가

이 저장소는 Vercel 프로젝트가 **둘**이다(`kanggle-fan` + 론처 `kanggle-portfolio`). 그래서
**커밋 하나가 배포 둘을 굽는다.** 2026-08-19 의 티켓 파일링 러시(문서 전용 PR 13건)가 그대로
배포로 번역되어 무료 플랜 한도에 닿았고, 24시간 동안 모든 PR 이 빨개졌다.

🔴 **진짜 피해는 빨간 체크가 아니었다** — 그동안 **론처가 낡은 판을 계속 서빙했다.**
`TASK-MONO-560` 의 방문자 화면 링크가 머지된 뒤에도 방문자는 그 링크가 없는 페이지를 봤고,
URL 은 200 을 냈다. 배포가 죽은 것과 사이트가 죽은 것은 다른 사건이다.

## 트리거 경로 — 왜 이 목록인가

`ignoreCommand` 가 넘기는 pathspec 은 **이 앱의 빌드에 실제로 들어가는 것**이다:

- `:/projects/fan-platform/web` — 이 앱(+ 이 `vercel.json` 자신) + pnpm 워크스페이스의 `web/*` 멤버 전부
- `:/projects/fan-platform/package.json` · `pnpm-lock.yaml` · `pnpm-workspace.yaml`
  — 워크스페이스 루트가 `projects/fan-platform` 이라 install 이 여기서 해석된다
- `:/scripts/vercel-should-build.sh` — 판정자 자신. 판정자를 고쳤으면 그 고침이
  **한 번은 행사돼야** 한다. 빼면 판정자 수정이 영원히 배포되지 않는다.

**`projects/fan-platform/apps/**` 는 일부러 뺐다** — Java 서비스라 Next 빌드에 들어가지 않는다.

🔵 pathspec 을 `:/` (저장소 루트 기준)으로 쓴 것은 **어디서 실행돼도 같은 것을 가리키게**
하기 위해서다 — 상대경로였다면 Root Directory 가 어디냐에 따라 다르게 해석된다.

🔴 목록을 좁힐 때는 **좁히는 쪽이 위험하다.** 빠뜨린 경로는 "배포 실패" 가 아니라
"조용히 건너뜀" 으로 나타난다. 판정 규약과 fail-open 설계는
[`scripts/vercel-should-build.sh`](../../../../scripts/vercel-should-build.sh) 헤더에 있다.
