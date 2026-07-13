# Task ID

TASK-MONO-396

# Title

`/review-task` 와 `/implement-task` 는 **`CLAUDE.md` 가 금지하는 두 가지를 지시한다** — 검증 없는 close 와 `main` 직접 머지. **커맨드 파일이 규칙보다 오래됐고, 규칙을 모르는 세션은 커맨드를 믿는다**

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- onboarding
- cleanup

---

# Dependency Markers

- **선행**: 없음.
- **후속**: 없음.
- **연관**: `TASK-MONO-323`(task ID 할당 규율) — 같은 클래스. 규칙은 `CLAUDE.md`/`tasks/INDEX.md` 에 있었고, **그것을 집행하는 자리에는 반영되지 않았다.**

---

# Goal

**`.claude/commands/review-task.md` 와 `.claude/commands/implement-task.md` 를 `CLAUDE.md` § Task Rules · `tasks/INDEX.md` § PR Separation Rule · main 보호 훅에 정합화한다.**

두 커맨드는 각각 **규칙이 명시적으로 금지하는 절차를 지시한다.** 지금 라이브 결함은 아니다 — 우리는 커맨드가 아니라 규칙을 따라 작업해 왔기 때문이다. **문제는 그 방어선이 "사람이 규칙을 기억하는 것" 하나뿐이라는 것이다.** 커맨드 파일은 컨텍스트가 없는 세션이 읽는 **집행 지점**이고, 거기에 틀린 절차가 적혀 있다.

---

# 실측 (2026-07-14, `origin/main` `db82d7b6b` — 착수 시 **다시 확인할 것**)

## 실측 1 — `/review-task` 는 **머지 검증 없이 close 한다**

`.claude/commands/review-task.md`:

| 위치 | 지시 |
|---|---|
| L40–41 (single mode) | *"If no issues: move task from `tasks/review/` → `tasks/done/`"* |
| L100–102 (agent prompt) | 같은 지시를 서브에이전트에게 |
| **L148 (Rules)** | ***"After review, original task always moves to `tasks/done/` regardless of result"*** |

**close 의 조건이 "테스트가 통과했는가" 다.** 그런데 `CLAUDE.md` § Task Rules 는 close 전에 **3차원 객관 검증**을 요구한다 — (a) PR `state=MERGED` (b) `origin/main` tip = 그 squash 커밋 (c) 머지 직전 스냅샷의 failing required check = **0**.

**커맨드는 셋 중 어느 것도 확인하지 않는다.** 즉 이 커맨드를 성실히 따르면 **머지되지도 않은 task 를, 혹은 CI-RED 로 머지되어 main 을 깨뜨린 task 를 `done/` 으로 옮긴다.** `CLAUDE.md` 가 그 규칙을 갖게 된 이유가 바로 그 사건이다([`platform/git-workflow-policy.md` § Merge-Verification Worked Incident](../../platform/git-workflow-policy.md)).

**⚠️ 실측 1-b — 그런데 훅은 이걸 안 막는다.** `hardstop-detect.ps1:116–139` 는 **lifecycle Status-field move 를 명시적으로 허용**한다(`$isLifecycleMove`). 즉 `review → done` 은 훅을 **정당하게 통과**한다. **훅이 막아줄 것이라 기대하면 안 된다 — 훅은 "frozen 파일의 임의 편집" 을 막지 close 의 정당성을 묻지 않는다.**

## 실측 2 — 두 커맨드 모두 **`main` 에 직접 머지하라고 한다**

| 위치 | 지시 |
|---|---|
| `implement-task.md` L150–158 | *"For successful agents: **merge their worktree branch into main**"* → `git merge <worktree-branch> --no-ff` · *"Verify **main branch** builds after merge"* |
| `implement-task.md` L261 | *"In batch mode, always merge worktree branches between rounds"* |
| `review-task.md` L152–155 | *"merge each successful agent's worktree branch **into main** … (mirrors implement-task.md Phase 5 step 3)"* |

**`protect-main-branch.ps1` 은 `git merge` 를 막지 않는다** — 막는 것은 `git push` 계열이다(L46–64, L83–106). 그래서 이 절차는 **조용히 성공하고, 그 결과는 영원히 push 할 수 없다**:

- 로컬 `main` 이 `origin/main` 앞으로 나간다 → `git push` 는 훅이 차단 → **작업이 stranded.**
- 게다가 그 머지는 **공유 메인 체크아웃**에서 일어난다. 지금 이 저장소에는 **동시 워크트리가 상시 3~4개** 떠 있다(착수 시점 실측: `wt-389`·`wt-391`·`wt-393`·`wt-394`). 공유 체크아웃의 HEAD 를 움직이는 것은 **BE-463 인시던트 그 자체**다 — 병행 세션의 미스테이지드 변경이 남의 커밋에 쓸려 들어간다.

**즉 이 지시는 "막히지 않는다" 는 의미에서 더 위험하다.** 훅이 잡아주는 실수가 아니라, **성공한 것처럼 보이고 나중에 값을 치르는** 실수다.

## 실측 3 — fix task 파일이 **spec PR 을 우회한다**

`review-task.md` 는 리뷰 서브에이전트가 워크트리 안에서 **fix task 파일을 `tasks/ready/` 에 생성**하고, 그 브랜치를 `main` 에 머지하게 한다(L152). fix task 파일은 **task 스펙**이고, `tasks/INDEX.md` § PR Separation Rule 은 스펙을 **spec PR** 로만 랜딩하라고 한다(impl/chore 와 번들 금지 — *"spec + impl must NOT share one"*).

**이 경로는 spec 을 impl PR 도 chore PR 도 아닌 곳으로 흘려보낸다.**

## 실측 4 — 왜 지금까지 안 터졌는가 (그리고 왜 그게 위안이 아닌가)

**우리는 커맨드를 문자 그대로 실행한 적이 없다.** close-chore 는 항상 `CLAUDE.md` 의 3-dim 을 밟았고, 머지는 항상 PR 을 경유했다 — **메모리와 규칙이 세션 컨텍스트에 살아 있었기 때문**이다.

**그것이 정확히 이 티켓의 논지다.** 방어선이 *"에이전트가 커맨드 파일 대신 `CLAUDE.md` 를 기억한다"* 라면, **커맨드 파일은 방어선이 아니라 함정이다.** 컨텍스트가 얕은 세션·서브에이전트·새 사람은 **커맨드를 믿는다** — 그러라고 있는 파일이니까.

---

# Scope

**포함:**

1. `.claude/commands/review-task.md` — close 를 리뷰에서 **분리**. 리뷰는 **판정만** 낸다(`approved` / `fix_needed`), task 파일을 옮기지 않는다. `review → done` 은 3-dim 검증을 게이트로 하는 **별도 close-chore 섹션**으로 이관.
2. `.claude/commands/implement-task.md` — 라운드 간 통합을 **`main` 이 아닌 코디네이터 소유 integration 브랜치**로. `main` 직접 머지·push 금지를 Rules 에 명시.
3. 두 커맨드에 **lifecycle Status 편집 순서** 명시(`in-progress/` 에서 Status 를 먼저 고치고 옮긴다 — `review/` 안에서의 임의 편집은 훅이 막는다).
4. `review-task.md` 의 fix task 산출물을 **spec PR** 로 라우팅.

**제외:**

- **`CLAUDE.md` · `tasks/INDEX.md` · 훅은 건드리지 않는다.** 그쪽이 진실 소스이고, 커맨드가 그쪽에 맞춰야 한다. **규칙을 커맨드에 맞추는 방향의 수정은 이 task 의 범위가 아니다**(그건 별개 판단이고, 하려면 ADR 이다).
- **`/process-tasks`** — 두 커맨드를 호출하는 상위 커맨드다. **AC-5 가 오염 여부만 확인**하고, 실제 수정이 필요하면 별도 티켓.

---

# Acceptance Criteria

- **AC-1 (실측 재확인)** — 착수 시 § 실측 1~3 의 줄 번호를 **다시 확인**한다. 커맨드 파일이 그사이 바뀌었을 수 있다.
- **AC-2 (`/review-task` 가 close 하지 않는다)** — 수정 후 `review-task.md` 에 **`done/` 으로 파일을 옮기라는 지시가 0건**이어야 한다. 단, **close-chore 절차 섹션은 예외**(거기서는 3-dim 게이트와 함께 등장한다). **탐지식을 아는 답에 먼저 돌려 자기검증할 것** — 같은 술어가 수정 *전* 파일에서는 **3건(L40·L101·L148)** 을 잡아야 한다. **빈 결과는 부재가 아니다.**
- **AC-3 (3-dim 게이트가 커맨드 안에 있다)** — close-chore 섹션이 `CLAUDE.md` § Task Rules 의 세 검증을 **모두** 적고, **`gh pr checks` 의 종료 코드를 게이트로 쓰지 말라**는 경고를 포함한다(pending 이 non-zero 로 나와 "실패" 로 오독된다).
- **AC-4 (`main` 머지 지시가 사라졌다)** — 두 커맨드에서 `merge … into main` / `git merge <branch>` 계열 지시가 **0건**. 대체 절차(integration 브랜치)가 있고, **"`main` 을 push 하지 말 것 — 훅이 막는다"** 가 Rules 에 명시된다.
- **AC-5 (호출자 오염 확인)** — `.claude/commands/process-tasks.md` 와 `.claude/agents/` 가 같은 틀린 절차를 **재진술하고 있지 않은지** grep. **재진술자가 있으면 그것도 고치거나(같은 PR) 별도 티켓으로 남긴다.** *(D5 가 반복해서 배운 것: 정책은 두 번째·세 번째 집을 갖는다.)*
- **AC-6 (문서 정합)** — 수정된 커맨드가 `tasks/INDEX.md` § PR Separation Rule 의 3-PR 모양(spec / impl / chore)과 **모순되지 않는다.**
- **AC-7 (검증 = 문서 task 이므로 "무엇이 verification 인가" 를 여기서 선언한다)** — 코드 변경이 없으므로 테스트가 없다. **검증은 (a) AC-2·AC-4 의 grep 이 0건 (b) 수정된 절차를 `TASK-MONO-396` 자신의 close-chore 에 실제로 적용해 본다** — 즉 **이 task 가 자기가 쓴 절차의 첫 사용자다.** 절차가 틀렸으면 여기서 걸린다.

---

# Related Specs

- **`CLAUDE.md` § Task Rules** — 3-dim 머지 검증 · `git mv` 재-stage 검사. **진실 소스.**
- **`tasks/INDEX.md` § PR Separation Rule** (L97–116) — spec / impl / chore 3-PR 분리. **진실 소스.**
- `.claude/hooks/protect-main-branch.ps1` — `main` push 차단(merge 는 **안** 막는다).
- `.claude/hooks/hardstop-detect.ps1` L113–144 — HARDSTOP-05. **lifecycle Status move 는 허용**한다.
- `.claude/commands/start-task.md` — worktree 격리(이미 정합).
- `platform/git-workflow-policy.md` § Merge-Verification Worked Incident — 3-dim 규칙이 생긴 이유.

# Related Contracts

**없다** — 커맨드 문서 변경. API/이벤트 표면 없음.

---

# Edge Cases

- **🔴 `.claude/commands/` 는 classifier 하드블록이다.** 에이전트가 이 경로를 편집·커밋하려 하면 차단된다(메모리 `env_classifier_claude_self_mod_block`). **⇒ 구현 시 편집이 막히면 우회하지 말고 패치를 사람에게 넘긴다.** 재구성해서 뚫으려는 시도 금지.
- **`review-task.md` 를 고치면 리뷰가 아무것도 안 옮긴다** — task 가 `review/` 에 쌓인다. **그건 결함이 아니라 의도다**(close 는 머지 후 chore PR). 다만 `CLAUDE.md` § Recommending Tasks 가 이미 *"`review/` 큐를 먼저 비워라"* 를 요구하므로 큐가 방치되지는 않는다. **그 연결을 커맨드에 한 줄 적어 둘 것.**
- **batch 모드의 integration 브랜치는 새로 도입하는 패턴이다.** 라운드 간 의존성(라운드 2가 라운드 1의 산출물을 봐야 함)을 PR 머지 대기로 풀면 배치 실행이 성립하지 않는다. **이 부분은 발명이고, 리뷰에서 이견이 나올 수 있는 유일한 지점이다.**
- **`done/` 은 HARDSTOP-05 정규식에 없다** (`(in-progress|review)` 만) — 메시지는 `done/` 도 frozen 이라 말하지만 정규식은 안 잡는다. **이 티켓의 범위가 아니다. 발견 사실로만 기록**하고, 고칠 거면 별도 티켓.

# Failure Scenarios

- **커맨드를 규칙에 맞추는 대신 규칙을 커맨드에 맞춘다** → 3-dim 검증이 사라지고, `CLAUDE.md` 가 인시던트를 통해 배운 것을 되돌린다. Guard: § Scope 제외 + AC-6.
- **classifier 블록을 우회하려 든다**(다른 도구로 쓰기, 커밋 분해) → 메모리가 명시적으로 금지. Guard: Edge Case 1 — **패치를 사람에게 넘기고 멈춘다.**
- **AC-2 의 grep 이 0건을 내는데 그게 "고쳐졌다" 가 아니라 "술어가 틀렸다" 이다** → 이 저장소가 **다섯 번** 대가를 치른 함정. Guard: AC-2 의 자기검증(수정 전 파일에서 **3건**을 잡아야 한다).
- **`process-tasks.md` 가 같은 절차를 재진술하고 있는데 안 본다** → 고친 커맨드 옆에 안 고친 사본이 남는다. Guard: AC-5.
- **이 task 자신의 close 를 옛 절차로 한다** → 아이러니 이상의 문제: 새 절차가 실제로 동작하는지 **아무도 시험하지 않은 채** 랜딩된다. Guard: AC-7(b).

---

# Provenance

**사용자가 *"작업 흐름이 어떻게 진행되는지, 흐름별로 어떤 메모리/규칙이 적용되는지"* 를 물었고, 그 지도를 그리려고 `CLAUDE.md` → `tasks/INDEX.md` → `platform/entrypoint.md` → `rules/README.md` → `.claude/commands/*` 를 나란히 놓자 마지막 층이 어긋나 있었다.**

**아무도 이 파일들을 최근에 읽지 않았다** — 우리는 `CLAUDE.md` 와 메모리로 일해 왔고, 커맨드는 그동안 조용히 stale 해졌다. **`/review-task` 의 L148(*"regardless of result"*)은 3-dim 검증 규칙이 존재하기 전에 쓰인 문장으로 보인다** — 규칙이 인시던트를 통해 갱신됐을 때 **그것을 집행하는 커맨드는 갱신되지 않았다.**

**이건 이 저장소가 반복해서 만나는 그 축이다 — 선언↔진실.** 다만 이번엔 방향이 반대다: 보통은 문서가 코드보다 낙관적이었다. 이번엔 **문서(커맨드)가 문서(규칙)보다 오래됐고, 그 사이에서 집행되는 것은 아무것도 없다.** *관측할 수 없는 성질은 아무도 지키고 있지 않은 성질이다* — 여기서는 **아무도 실행하지 않는 커맨드는 아무도 그것이 틀렸다는 것을 모른다.**

분석=Opus 4.8 / 구현 권장=**Sonnet** (문서 정합성 수정. 다만 **classifier 블록에 걸리면 패치를 사람에게 넘기고 멈출 것** — 우회 금지. batch 모드 integration 브랜치 설계만 판단이 필요하다.)
