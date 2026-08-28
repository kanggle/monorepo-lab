# Task ID

TASK-MONO-592

# Title

🔴 **선언한 라이프사이클 단계가 «비면 사라진다»** — `projects/*/tasks/<stage>` 글롭이 그
프로젝트를 **조용히 건너뛰고**, 그러면 모집단이 줄어든 것을 아무도 모른다.

# Status

done

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

---

# ✅ 구현 결과 (2026-08-28 UTC · 브랜치 `task/mono-592-lifecycle-stage-dirs`)

## AC-0 — 다시 셌다. ② 의 표와 **일치한다**

모집단은 `git ls-files 'projects/*/PROJECT.md'` → 8개. 여섯 단계 전부, 루트 4단계까지 세었다.
🔵 **양성 대조군 전부 «없음» 으로 잡혔다** — `wms/ready` · `fan`/`erp`/`finance`/`iam` 의
`backlog`, 그리고 ② 가 잠복이라고 지목한 `fan/ready` 까지 **6개**. 술어가 틀리지 않았다.

🔵 상속하지 않았다는 증거: 이 표는 티켓 본문을 옮겨 적은 것이 아니라 새 가드가 스스로 낸
출력이고(아래 §AC-2), 그 출력이 ② 와 같은 6칸을 지목했다.

## AC-1 — keeper 6개

`erp`·`fan`·`finance`·`iam` 의 `backlog/`, `fan`·`wms` 의 `ready/`.
형태는 기존과 동일 — 빈 `.gitkeep` (blob `e69de29`, 기존 keeper 와 **같은 해시**).
새 관행을 만들지 않았다.

## AC-2 — `scripts/check-lifecycle-stage-dirs.sh` (본체)

**술어 = «추적되는 non-`.md` 파일이 있는가».** 세 후보 중 둘을 명시적으로 버렸다:

| 후보 | 왜 안 되는가 |
|---|---|
| `git ls-files "<dir>/*.md" \| wc -l` | ④ 가 지적한 그것. «없음» 과 «비었음» 이 **똑같이 0** — 구조적으로 못 본다 |
| `[ -d "<dir>" ]` | 재려는 것은 «신선한 체크아웃에서 살아남나» 인데 이건 «내 디스크에 지금 있나» 다. 커밋 안 된 빈 디렉터리 → **로컬 초록 / CI 빨강** |
| **추적되는 non-`.md`** | `.md` 는 설계상 떠난다. 큐가 비었을 때 디렉터리를 붙잡는 건 keeper 뿐 |

🔵 keeper **이름은 안 박았다** — `.keep` 같은 다른 관행이 조용히 거짓 빨강이 되지 않도록.

- **모집단**: `projects/*/PROJECT.md`. 글롭 금지 — 글롭이면 디렉터리 없는 프로젝트를 스스로
  건너뛰어 **가드가 이 결함 자신에게 당한다**. 부수 효과로 `projects/bin/` 자동 제외(Edge 2행).
- **단계 목록의 출처 = 각 INDEX 의 `# Lifecycle` 선언** (박지 않고 파싱). 루트는 4단계,
  프로젝트는 6단계라 박으면 **루트가 거짓 RED** → 가드가 꺼진다(Edge 1행).
  파싱의 대가는 «파서가 죽으면 0단계 → 초록» 이므로 **0 은 판정 불가로 실패**:
  INDEX 수 하한(9) + INDEX 당 단계 수 하한(2) + 총 검사 단계 0 시 실패, 셋 다 걸었다.
- **범위 밖(명시)**: 선언 안 된 여분 디렉터리는 안 본다 — 루트 `tasks/templates/` 가 그
  모양이라 거짓 RED 가 된다.

### 무망가 상태 (음성 대조군)

```
[lifecycle-dirs] ok — INDEX 9개 · 선언 단계 52개 전부 keeper 보유
  (1) tasks  4단계 — ○ready ○in-progress ○review ○done
  (1) projects/wms-platform/tasks  6단계 — ○backlog ○ready ○in-progress ○review ○done ○archive
  …  (8 프로젝트 × 6) + 루트 4 = 52
```

### bite — `--self-test` 5칸, **주입 착지를 먼저 단언한 뒤** 무는지 읽는다

| 칸 | 주입 | 기대 | 결과 |
|---|---|---|---|
| (0) | 없음. 사본에 keeper 만 담아 **모든 큐를 빈 상태로** 만든다 | rc=0 | ok |
| (a) | keeper 하나 `git rm` | rc=1 | ok |
| (b) | **잠복** — keeper 를 지우고 그 자리에 `.md` 를 넣는다 | rc=1 | ok |
| (c) | `# Lifecycle` 화살표 줄 파괴 | rc=1 (판정 불가) | ok |
| (d) | `PROJECT.md` 제거 → 모집단 축소 | rc=1 (하한) | ok |
| (e) | 없음 — 루트가 6단계를 강요당하지 않는지 | 4단계만 검사 | ok |

🔴🔴 **(b) 가 (a) 와 다른 것을 시험한다 — 이 칸이 «새 축을 재고 있다» 의 증거다.**
(b) 의 상태에서 세 술어를 **실측**했다(스크래치 리포):

```
디렉터리 존재 = yes                       ← [ -d ] 였다면 초록
git ls-files 'tasks/ready/*.md' | wc -l = 1  ← 옛 술어였다면 초록
추적되는 non-.md keeper = ''                 ← 새 술어 → 빨강
```

🔵 (0) 은 본 검사의 중복이 아니다. 본 검사는 `.md` 가 큐를 붙잡고 있는 실제 트리를 보지만
(0) 은 **전부 비워 놓고** 본다 — 잠복은 거기서만 드러난다. 개발 중 실측으로 확인했다:
keeper 6개를 넣기 **전에는 (0) 칸이 정확히 빨강이었다.**

## AC-3 — `ci.yml` 잡 `lifecycle-stage-dirs` + 필터 확인

- 필터 `lifecycle-dirs` = `tasks/INDEX.md` · `tasks/*/.gitkeep` ·
  `projects/*/PROJECT.md` · `projects/*/tasks/INDEX.md` · `projects/*/tasks/*/.gitkeep` ·
  `scripts/check-lifecycle-stage-dirs.sh`. 순수 positive(negation 없음).
- 🔴 **`code-changed` 와 AND 하지 않았다** — 두 도착 경로(부트스트랩·keeper 삭제)가 전부
  markdown/빈 파일 diff 라, AND 하면 **자기 결함 클래스의 100%에서 초록 skip** 이 된다.
- 🔴 **`PROJECT.md` 를 필터에 넣은 것이 AC-3 의 요점이다.** 새 프로젝트 부트스트랩 PR 이
  이 가드가 물어야 할 바로 그 순간이고(③), `PROJECT.md` 없이는 그 커밋에서 안 돈다.
- 🔵 **글롭을 추측하지 않고 실측했다** — `dorny/paths-filter` 가 쓰는 매처
  `picomatch@4.0.4` 로 13 케이스(양성 9 + 음성 4) 대조, `dot:true`/`dot:false` **양쪽 동일**,
  불일치 0. 음성에 «평범한 태스크 이동» 을 넣어 close chore 마다 깨어나지 않음을 확인했다
  (가드의 술어가 `.md` 를 안 보므로 그때 돌 이유가 없다).

## AC-4 — 재발 방지 자리 = `TEMPLATE.md` § Option A 1단계

③ 이 말한 원인은 «부트스트랩 산출물의 차이» 인데, **`touch …/.gitkeep` 줄은 이미 거기
있었다.** 없었던 것은 «그게 왜 필요한가» 와 «무엇이 그걸 강제하는가» 다. 둘 다 적었다:
없으면 무엇이 깨지는지(7 vs 8 실측), `lifecycle-stage-dirs` 잡이 `PROJECT.md` 를 추가하는
그 커밋에서 RED 가 된다는 것, **`.md` 만 있는 단계도 실패한다**는 것, 그리고 3단계가 목록에
없는 단계를 선언하면 keeper 도 같이 추가하라는 것.

## Edge Cases 처리

| 케이스 | 결과 |
|---|---|
| 루트 `tasks/` 4단계 | ✅ 각 INDEX 선언을 읽으므로 4단계만 검사. self-test (e) 가 회귀를 막는다 |
| `bin` 은 프로젝트가 아니다 | ✅ `PROJECT.md` 기준이라 자동 제외 |
| keeper 때문에 큐가 «안 비었다» 로 세어진다 | ✅ **소비자 전수 확인**: `check-index-queue-drift.sh` 는 `"…/*.md"` 글롭, `check-task-id-collision.sh` 는 `grep -E '…\.md$'`. **`.md` 아닌 것을 세는 소비자는 없다** |
| AC-0 이 «전부 있음» 을 낸다 | 해당 없음 — 6개 결손이 잡혔고 `wms/ready` 양성 대조군도 잡혔다 |
| 새 단계 추가 | ✅ INDEX 선언을 읽으므로 자동으로 따라간다 |

## 로컬 게이트 (스테이지 **후** 실행 — 각각 독립 실행, `&&` 체인 아님)

```
lifecycle-stage-dirs               rc=0
lifecycle-stage-dirs --self-test   rc=0   (5칸 전부 ok)
index-queue-drift --selftest       rc=0
index-queue-drift                  rc=0   ← keeper 추가가 이웃 가드를 안 깨뜨림
task-id-collision                  rc=0
public-domains --self-test / 본체  rc=0   ← TEMPLATE.md 를 건드렸으므로
ci-baseline-reachable              rc=0
ci.yml YAML 파싱 + 잡/필터/outputs 배선  확인
```

🔴 `git ls-files` 술어이므로 **스테이지 후에 돌렸다** — 스테이지 전 실행은 이 저장소에서
3회 재발한 가짜 판정이다. [[env_a_guard_reading_git_ls_files_is_blind_to_unstaged_work]]

## 🔵 범위 밖으로 남긴 것

- 선언되지 않은 여분 `tasks/<something>/` 디렉터리 검사 — 루트 `tasks/templates/` 가 거짓
  RED 가 된다. 스크립트 헤더에 «범위 밖(의도적)» 로 명시했다.
- ① 이 언급한 `iam-platform` `review/` 3건은 이 티켓의 축이 아니다(모집단이 줄어 가려졌던
  항목일 뿐). 별도 큐 정리 사안으로 남긴다.
