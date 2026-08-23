# Task ID

TASK-MONO-572

# Title

`vercel-should-build.sh` 의 판정 창이 **한 커밋**이라, 여러 커밋을 한 번에 push 하면 앱 변경이 **조용히 건너뛰어진다**. 그 스크립트가 스스로 경고한 고장 모양인데 다른 문으로 들어왔다.

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- infra
- guard

---

# Goal

Vercel "Ignored Build Step" 판정이 **푸시된 범위 전체**를 보게 만든다. 지금은 `HEAD^..HEAD`,
즉 **마지막 커밋 하나**만 본다.

---

# Context — 관측된 사례

2026-08-23, `TASK-MONO-571` 작업 중 실제로 발생했다.

| 커밋 | 내용 |
|---|---|
| `d4822737b` | 프로브 라우트 + 미들웨어 (**fan 앱 변경**) |
| `1205b2898` | `tasks/INDEX.md` 한 줄 (앱 무관) |

둘을 **한 번에 push** 했다. Vercel 은 head(`1205b2898`)에서 ignoreCommand 를 돌렸고,
`git diff HEAD^ HEAD -- :/projects/fan-platform/web` 는 **비어 있었다**.

결과: `Canceled by Ignored Build Step` → **프리뷰 배포에 프로브가 없었다.** 배포는 "실패" 가
아니라 **성공으로 표시**됐고, PR 체크는 초록이었다.

## 🔴 스크립트가 스스로 경고한 모양이다

`scripts/vercel-should-build.sh` 헤더:

> **고장은 반드시 "더 굽는" 쪽으로 나야 한다.** 무시 규칙은 과하게 무시하는 방향으로 고장 난다.
> 그 고장의 증상은 "배포가 실패했다" 가 아니라 **"배포가 조용히 건너뛰어졌다"** 이고, CI 는
> 초록이며, 사이트는 마지막 성공 배포를 계속 서빙하므로 URL 을 찔러도 200 이 나온다. 아무도 안 본다.

작성자는 **판정 불가**(얕은 clone / git 없음 / 인자 없음)를 전부 fail-open 으로 막아 뒀다.
그런데 이 경우는 판정 불가가 아니다 — 스크립트는 **자신 있게 "변경 없음"이라고 답했다.**
🔵 **틀린 것은 fail-open 처리가 아니라 창의 크기다.**

## 범위 — 프로덕션은 안전하다

`main` 은 squash 머지라 커밋 하나에 전부 담긴다 ⇒ `HEAD^..HEAD` 가 정확하다.
**뚫리는 것은 프리뷰**(그리고 squash 가 아닌 모든 push)다. 그래서 심각도는 중간이지만,
프리뷰에서 재는 모든 측정이 **틀린 산출물을 재게 된다** — 이번에 정확히 그랬다.

---

# Acceptance Criteria

### AC-0 — 재측정: Vercel 이 실제로 주는 범위를 확인한다

착수 전에 **Vercel 이 ignoreCommand 실행 시점에 무엇을 주는지** 확인한다.
`VERCEL_GIT_PREVIOUS_SHA` 같은 환경변수가 있는지, 클론 깊이가 얼마인지가 해법을 가른다.

🔴 **문서를 읽고 결론짓지 말고 찍어라** — 한 배포에서 `env | sort` 를 로그로 남기는 것이
가장 싸다. 깊이가 1이면 `HEAD^` 조차 없고(스크립트가 이미 fail-open 으로 처리한다),
그렇다면 해법은 **범위 확대가 아니라 다른 신호**여야 한다.

### AC-1 — 판정이 푸시된 범위를 본다

AC-0 이 준 사실 위에서, 판정 범위를 **직전 배포된 커밋 ~ HEAD** 로 넓힌다.
후보(AC-0 이 고른다): `VERCEL_GIT_PREVIOUS_SHA..HEAD` · 얕은 clone 이면 `--unshallow` 없이
쓸 수 있는 신호.

### AC-2 — bite: 이번에 관측된 배치가 실제로 빌드를 튼다

🔴 **"고쳤다"의 증거는 술어가 아니라 배치다.** 다음 두 커밋 배치를 합성해 판정기에 먹인다:

| 칸 | 배치 | 기대 |
|---|---|---|
| bite | `[앱 변경, 문서 변경]` (앱이 **앞**) | **빌드**(exit 1) |
| 대조군 ① | `[문서 변경, 문서 변경]` | 건너뜀(exit 0) — 넓히기가 **전부 빌드**로 퇴화하지 않았다 |
| 대조군 ② | `[앱 변경]` 단일 | 빌드(exit 1) — 기존 동작 보존 |

🔴 대조군 ①이 없으면 "항상 빌드"라는 자명한 오답이 통과한다. 그건 rate limit 을 다시 부른다
(그 한도가 `TASK-MONO-562` 를 낳은 이유다).

### AC-3 — 두 소비자를 함께 고친다

`vercel-ignore.sh` 헤더가 명시한다: 경로 목록의 소비자는 **둘**이다 — Vercel 의 ignoreCommand,
그리고 `check-fan-fresh.sh`(*"서빙 중인 판이 최신인가"* 의 **기대값**을 같은 목록으로 계산).
🔴 **둘의 범위가 어긋나면** 판정기가 건강한 배포에 빨간불을 켜거나 **죽은 배포를 신선하다고**
한다. 창을 넓히면 신선도 판정기의 창도 같이 봐야 한다. 어느 쪽이든 **읽어서 확인하고 기록한다**
(변경 불필요면 "불필요"가 산출물이다).

### AC-4 — `scripts/check-vercel-build-triggers.sh` 에 칸을 추가한다

그 가드가 이미 이 스크립트 계열을 지킨다(칸 5 = `ignoreCommand` 256자 한도).
**창 크기**에 대한 칸을 더해, 판정이 다시 한 커밋으로 좁아지면 CI 가 발화하게 한다.

---

# Related Specs

- `scripts/vercel-should-build.sh` (헤더에 설계 의도 + 이 고장 클래스 경고)
- `projects/fan-platform/web/fan-platform-web/vercel-ignore.sh` (경로 목록 + 소비자 둘)
- `TASK-MONO-562` / `TASK-MONO-563` / `TASK-MONO-564` (이 계열의 선행)
- `tasks/in-progress/TASK-MONO-571-...` § 부수 발견 (관측 사례)

# Related Contracts

없음.

---

# Edge Cases

- **Vercel 클론이 얕아 `HEAD^` 도 없다** → 스크립트가 이미 fail-open(빌드)한다. 그 경로는
  이 티켓이 **더 나쁘게 만들면 안 된다** — 넓히기가 실패했을 때도 fail-open 이어야 한다.
- **`VERCEL_GIT_PREVIOUS_SHA` 가 없거나 unreachable** → 판정 불가 ⇒ **빌드**. 스크립트 헤더의
  방향을 유지한다.
- **직전 배포가 아주 오래됐다** → 범위가 커져 거의 항상 빌드가 된다. AC-2 의 대조군 ①이 이것을
  잡지 못할 수 있으므로, 범위 상한(예: 최근 N커밋)을 둘지 AC-0 의 사실로 결정한다.
- **force-push 로 직전 SHA 가 조상이 아니다** → `A..B` 가 조용히 이상한 집합을 준다.
  `git merge-base --is-ancestor` 로 확인하고 아니면 **빌드**.

---

# Failure Scenarios

- **넓히기가 "항상 빌드"로 퇴화한다** → rate limit 이 다시 온다. 그 한도가 이 스크립트를 낳은
  이유이고, 이번 세션에도 두 번 물렸다. AC-2 대조군 ①이 이것을 막는다.
- **고쳤는데 프리뷰에서만 안 돈다** → 판정기는 **Vercel 런타임에서만** 그 환경변수를 본다.
  로컬 초록이 증명하지 않는다 ⇒ 실제 배포 로그에서 판정 줄을 읽어 확인한다.
- **`check-fan-fresh.sh` 와 어긋난 채로 넘어간다** → 죽은 배포를 신선하다고 보고한다.
  AC-3 이 이것을 막는다.
