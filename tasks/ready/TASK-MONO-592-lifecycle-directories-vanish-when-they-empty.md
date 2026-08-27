# Task ID

TASK-MONO-592

# Title

🔴 **선언한 라이프사이클 단계가 «비면 사라진다»** — `projects/*/tasks/<stage>` 글롭이 그
프로젝트를 **조용히 건너뛰고**, 그러면 모집단이 줄어든 것을 아무도 모른다.

# Status

ready

# Owner

monorepo

# Task Tags

- lifecycle
- guard
- cross-project

---

# Goal

git 은 **빈 디렉터리를 추적하지 않는다.** 그래서 태스크 큐가 비는 순간 그 디렉터리는
체크아웃에서 사라지고, `projects/*/tasks/<stage>` 로 순회하는 모든 것 — 스크립트, 에이전트,
사람 — 이 그 프로젝트를 **목록에서 통째로 뺀 채** 정상 종료한다.

6개 단계 중 4개는 `.gitkeep` 으로 보호돼 있고 **2개는 아니다.** 그 둘을 맞춘다.

🔴 **이건 «파일 하나 추가» 가 아니다.** 결함의 성질은 *"모집단이 조용히 줄어든다"* 이고,
그래서 **가드가 본체**다. `.gitkeep` 만 넣으면 다음에 새 프로젝트가 부트스트랩될 때 같은
구멍이 다시 생긴다.

---

# Context — 실측 (2026-08-27 UTC, `origin/main` = `4e750c183`)

## ① 오늘 실제로 이것에 당했다

세션 중 *"재배포 전까지 할 수 있는 일이 남았나"* 를 답하려고
`for d in projects/*/tasks/ready` 로 전 프로젝트를 훑었다. 출력에 **7개 프로젝트**가 나왔고
나는 그것을 **전수라고 보고했다.**

**8개다.** `wms-platform` 은 `tasks/ready/` 디렉터리가 없어서 글롭에 안 걸렸고,
**에러도 경고도 없었다.** 🔴 «7» 과 «8» 을 구별해 줄 것이 아무것도 없었다.

🔵 그리고 그 누락이 **다른 누락을 가렸다** — 같은 스윕에서 `iam-platform` 의 `review/` 3건을
그제서야 봤다. 모집단이 줄면 그 안의 항목도 같이 사라진다.

## ② 선언과 실재가 어긋난다 — 전수

**8개 프로젝트의 `tasks/INDEX.md` 가 전부** 같은 라이프사이클을 선언한다:

```
backlog → ready → in-progress → review → done → archive
```

`.gitkeep` 보유 실측 (`○`=있음 · `·`=없음, 괄호 = 추적 중인 `.md` 수):

| project | ready | in-progress | review | done | backlog | archive |
|---|---|---|---|---|---|---|
| ecommerce | ○(0) | ○(0) | ○(0) | ○(500) | ○(0) | ○(0) |
| erp | ○(0) | ○(0) | ○(0) | ○(43) | **·(0)** | ○(0) |
| **fan** | **·(1)** | ○(0) | ○(0) | ○(73) | **·(0)** | ○(0) |
| finance | ○(0) | ○(0) | ○(0) | ○(68) | **·(0)** | ○(0) |
| iam | ○(0) | ○(0) | ○(3) | ○(502) | **·(0)** | ○(0) |
| platform-console | ○(0) | ○(0) | ○(0) | ○(288) | ○(1) | ○(0) |
| scm | ○(0) | ○(0) | ○(0) | ○(68) | ○(1) | ○(0) |
| **wms** | **·(0)** | ○(0) | ○(0) | ○(173) | ○(1) | ○(0) |

- `in-progress` · `review` · `done` · `archive` — **8/8 보호됨**
- `ready` — **6/8**. `wms` 는 `.md` 가 0이라 **이미 디렉터리가 없다**
- `backlog` — **4/8**

🔴 **`fan` 은 잠복이다.** 지금은 `TASK-FAN-FE-018` 하나가 `ready/` 를 붙잡고 있어서 존재한다.
그 티켓이 `ready/` 를 떠나는 순간 fan 도 wms 처럼 사라진다 — **결함이 «앞으로 생길 것» 이
아니라 이미 장전돼 있다.**

## ③ `wms` 의 것은 삭제된 게 아니라 **처음부터 없었다**

`git log --diff-filter=D -- projects/wms-platform/tasks/ready/.gitkeep` → **0건**.
`.gitkeep` 들은 각 프로젝트 부트스트랩 커밋에 들어왔고(`#188` scm · `#567` console ·
`#595` finance · `#620` erp …), **fan 과 wms 의 부트스트랩에는 그 파일이 없었다.**
⇒ 원인은 «누가 지웠나» 가 아니라 **부트스트랩 산출물이 서로 다르다**는 것이다.
🔴 **그래서 다음 프로젝트도 같은 복권을 뽑는다.**

## ④ 🔴 기존 가드는 이것을 **볼 수 없다** — 고장이 아니라 범위 밖이다

`scripts/check-index-queue-drift.sh:461` 이 큐를 읽는 방법:

```bash
git ls-files "${base_dir}/${sect}/*.md"
```

**`git ls-files` 로 센다.** 그러니 «디렉터리가 없다» 와 «디렉터리가 비었다» 가 **똑같이 0** 이고,
INDEX 행도 0이면 집합 동등성이 성립해 **초록**이다. 가드는 정확히 자기가 하기로 한 일을
하고 있다 — **디렉터리의 존재는 그 술어에 들어 있지 않다.**
[[feedback_why_a_guard_does_not_bite]]

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 세고, 단계를 상속하지 마라**

- ② 의 표를 다시 만든다. 🔴 **`ready` 와 `backlog` 만 보지 마라** — 이 티켓이 처음에 `ready`
  만 보고 «두 프로젝트 문제» 라고 적었다가, 전 단계를 세고 나서야 `backlog` 4건을 찾았다.
  **여섯 단계 전부** 센다.
- 🔴 **프로젝트 목록을 글롭으로 만들지 마라** — 그것이 이 결함의 발현 경로다.
  `projects/*/PROJECT.md` 처럼 **반드시 존재하는 파일**로 모집단을 잡고, 거기서 단계를 확인한다.
  [[feedback_declaration_files_are_not_the_runtime_state]]
- 🔵 **양성 대조군**: `wms` 의 `ready` 와 `fan`/`erp`/`finance`/`iam` 의 `backlog` 가 반드시
  «없음» 으로 잡혀야 한다. 안 잡히면 술어가 틀렸다.

## AC-1 — 빠진 것을 채운다

AC-0 이 센 모집단 전부에 keeper 를 넣는다. 🔵 형태는 기존과 맞춘다(`.gitkeep`, 빈 파일) —
**새 관행을 만들 자리가 아니다.**

## AC-2 — 🔴 **가드가 본체다**

*"선언된 단계마다 디렉터리가 존재한다"* 를 검사하는 것을 만든다.

- 🔴 **`git ls-files … *.md` 로 세지 마라** — ④ 가 보여주듯 그 술어는 이 성질을 **구조적으로
  못 본다.** keeper 파일 자체를 보거나 디렉터리 존재를 봐야 한다.
- **모집단은 `PROJECT.md` 에서** 잡는다(AC-0 과 같은 이유).
- 🔴 **단계 목록의 출처를 정한다** — INDEX 의 라이프사이클 줄에서 파싱할지, 스크립트에
  박을지. 박으면 **INDEX 가 바뀌어도 안 따라간다**; 파싱하면 파서가 죽었을 때
  **조용히 0단계**가 된다 ⇒ 어느 쪽이든 **0이 나오면 판정 불가로 실패**해야 한다.
  [[feedback_a_runner_that_matches_no_package_exits_zero]]
- 🔴 **bite 를 증명한다** — keeper 하나를 지우고 RED 가 나오는지 찍는다. 초록만 보고
  «작동한다» 로 적지 마라. [[feedback_assert_the_injection_before_reading_the_bite]]
- 🔵 **음성 대조군**: 정상 상태에서 초록이어야 한다(전부 RED 로 만드는 고장 방지).

## AC-3 — **어디서 도는가**

`ci.yml` 에 잡을 붙이고 **path-filter 를 확인한다.** 🔴 이 검사는 `projects/**` 어디가
바뀌어도, **그리고 새 프로젝트가 추가될 때** 돌아야 한다 — 필터가 좁으면 정확히 그
순간(새 부트스트랩)에 안 돈다. `TASK-MONO-589` 가 `platform/hardstop-rules.md` 를
`hooks` 필터에 넣어야 했던 것과 같은 종류의 실수다.
🔴 러너 없는 가드는 썩는다. [[feedback_two_correct_exclusions_compose_into_a_hole]]

## AC-4 — **재발을 막는다**

③ 이 말하듯 원인은 부트스트랩 산출물의 차이다. 새 프로젝트가 이 구멍을 다시 뚫지 않게
할 자리를 **하나 정하고 적는다** — `TEMPLATE.md` 의 프로젝트 부트스트랩 절, 또는 AC-2 의
가드가 새 프로젝트에서 자동으로 물게 하는 것(후자면 AC-3 의 필터가 그것을 보장해야 한다).

---

# Related Specs

- `scripts/check-index-queue-drift.sh` — ④ 의 `git ls-files` 술어
- `projects/*/tasks/INDEX.md` — 6단계 라이프사이클 선언 (8/8 동일)
- `tasks/INDEX.md` § Move Rules — 루트 라이프사이클(4단계, `backlog`/`archive` 없음)
- `TEMPLATE.md` — 프로젝트 부트스트랩 (AC-4 후보 자리)
- `.github/workflows/ci.yml` — AC-3

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 루트 `tasks/` 는 4단계뿐이다 | 🔵 **정상이다.** 루트는 `backlog`/`archive` 를 안 쓴다. 가드가 «모든 큐는 6단계» 를 강요하면 루트가 거짓 RED — **단계 목록은 그 INDEX 가 선언한 것**이어야 한다 |
| `bin` 은 프로젝트가 아니다 | `projects/bin/` 에는 `PROJECT.md` 도 `tasks/` 도 없다. `PROJECT.md` 로 모집단을 잡으면 자동으로 빠진다 — 🔵 글롭을 쓰면 안 되는 이유가 하나 더 |
| keeper 를 넣었더니 큐가 «비어 있지 않다» 로 세어진다 | 🔴 **`.md` 만 세는 소비자가 있는지 확인**하라. `check-index-queue-drift.sh` 는 `*.md` 글롭이라 안전하지만 다른 소비자는 미확인 |
| AC-0 이 «전부 있음» 을 낸다 | 🔴 **술어를 의심하라** — `wms/ready` 가 양성 대조군이다 |
| 새 단계가 나중에 추가된다 | AC-2 가 INDEX 선언을 읽으면 자동으로 따라간다. 박아 두면 안 따라간다 — AC-2 가 그 선택을 명시한다 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방지 |
|---|---|---|
| `.gitkeep` 만 넣고 닫는다 | 다음 프로젝트 부트스트랩에서 같은 구멍이 다시 뚫린다 | AC-2 + AC-4 |
| 가드를 `git ls-files *.md` 로 만든다 | 이 결함을 **구조적으로 못 본다**. 첫날부터 영원히 초록 | AC-2 첫 항목 + bite 증명 |
| 모집단을 `projects/*/tasks/...` 글롭으로 잡는다 | 🔴 **가드가 이 결함 자신에게 당한다** — 디렉터리 없는 프로젝트를 스스로 건너뛴다 | AC-0/AC-2 의 `PROJECT.md` 기준 |
| 단계 목록 파서가 죽는다 | 0단계 검사 → 초록 | AC-2 — 0이면 **판정 불가로 실패** |
| 루트 `tasks/` 에 6단계를 강요한다 | 루트가 거짓 RED → 가드를 끈다 | Edge Cases 1행 |
| 필터가 좁아 새 프로젝트에서 안 돈다 | 부트스트랩 시점에 정확히 안 잡힘 | AC-3 |
