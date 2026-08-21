# Vercel 배선 — `kanggle-fan` (TASK-MONO-562)

`vercel.json` 옆의 이 파일이 그 JSON 이 담을 수 없는 것을 담는다. **JSON 에는 주석이 없고,
`vercel.json` 은 스키마가 엄격해 모르는 최상위 키를 거부한다** — `TASK-MONO-557` 이 설명을
`"//installCommand"` 키로 끼워 넣었다가 배포를 연속으로 깼다(`45bdca743` 성공 →
`ea9d5e79c`·`2679b8e41` 실패). 설명의 집은 설정 파일이 아니다.

| | |
|---|---|
| Vercel 프로젝트 | **`kanggle-fan`** |
| Root Directory | **대시보드 값이라 저장소에서 확인할 수 없다** — 아래 § 참조 |
| 빌드 | Next.js 15 App Router. 대시보드 기본 감지에 맡긴다 — `vercel.json` 은 `ignoreCommand` **하나만** 선언한다 |

## 🔴 Root Directory 를 모른다 — 그래서 두 자리 다 덮었다

Vercel 은 `ignoreCommand` 를 **Root Directory 의 `vercel.json` 에서만** 읽는다. 그런데 그 값은
**대시보드에만 있고 저장소에 흔적이 없다** — 이 티켓이 AC-2 에서 문제 삼은 바로 그 상태다.
후보는 둘이다:

| 후보 | 이 저장소의 설정 파일 |
|---|---|
| `projects/fan-platform/web/fan-platform-web` (Next 앱) | `./vercel.json` (이 디렉터리) |
| `projects/fan-platform` (pnpm 워크스페이스 루트) | `../../vercel.json` |

**둘 다 같은 `ignoreCommand` 를 선언한다.** 어느 쪽이 Root Directory 든 하나가 읽히고 나머지는
그냥 무시된다(무해). 🔴 **하나만 두고 찍었다면**, 틀렸을 때의 증상은 "배포 실패" 가 아니라
**"규칙이 있는데 아무 일도 안 일어난다"** 이고, 규칙 파일이 저장소에 있으니 다음 사람은
고쳐진 줄 안다. 이 결함군 전체가 그 모양이다.

🔵 pathspec 을 `:/` (저장소 루트 기준)으로 쓴 것도 같은 이유다 — 상대경로였다면 Root Directory
가 어디냐에 따라 다르게 해석된다. 지금은 **어디서 실행돼도 같은 것을 가리킨다.**

⏳ 대시보드에서 실제 값을 확인하면 이 절을 지우고 한 자리만 남겨라.

## 왜 `ignoreCommand` 가 필요한가

이 저장소는 Vercel 프로젝트가 **둘**이다(`kanggle-fan` + 론처 `kanggle-portfolio`). 그래서
**커밋 하나가 배포 둘을 굽는다.** 2026-08-19 의 티켓 파일링 러시(문서 전용 PR 13건)가 그대로
배포로 번역되어 무료 플랜 한도에 닿았고, 24시간 동안 모든 PR 이 빨개졌다.

🔴 **진짜 피해는 빨간 체크가 아니었다** — 그동안 **론처가 낡은 판을 계속 서빙했다.**
`TASK-MONO-560` 의 방문자 화면 링크가 머지된 뒤에도 방문자는 그 링크가 없는 페이지를 봤고,
URL 은 200 을 냈다. 배포가 죽은 것과 사이트가 죽은 것은 다른 사건이다.

## 트리거 경로 — 왜 이 목록인가

`ignoreCommand` 가 넘기는 pathspec 은 **이 앱의 빌드에 실제로 들어가는 것**이다:

- `:/projects/fan-platform/web` — 이 앱 + pnpm 워크스페이스의 `web/*` 멤버 전부
- `:/projects/fan-platform/package.json` · `pnpm-lock.yaml` · `pnpm-workspace.yaml`
  — 워크스페이스 루트가 `projects/fan-platform` 이라 install 이 여기서 해석된다
- `:/scripts/vercel-should-build.sh` — 판정자 자신. 판정자를 고쳤으면 그 고침이
  **한 번은 행사돼야** 한다. 빼면 판정자 수정이 영원히 배포되지 않는다.

**`projects/fan-platform/apps/**` 는 일부러 뺐다** — Java 서비스라 Next 빌드에 들어가지 않는다.

🔴 목록을 좁힐 때는 **좁히는 쪽이 위험하다.** 빠뜨린 경로는 "배포 실패" 가 아니라
"조용히 건너뜀" 으로 나타난다. 판정 규약과 fail-open 설계는
[`scripts/vercel-should-build.sh`](../../../../scripts/vercel-should-build.sh) 헤더에 있다.
