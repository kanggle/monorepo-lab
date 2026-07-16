# Task ID

TASK-MONO-409

# Title

`CLAUDE.md` 의 dispatch 규칙이 dispatch 에 도달하지 않는다 — `model=` 0건, 그리고 contract 작업은 전부 Sonnet 으로 돌고 있다

# Status

done

# Owner

monorepo

# Task Tags

- code
- adr

---

# Goal

`/validate-rules`(2026-07-15) 가 **규칙은 있는데 도달하지 않는** 세 지점을 찾았다. 전부 `.claude/` 배선이다.

**① `model=` 이 한 번도 전달되지 않는다.** `CLAUDE.md § Recommending Tasks and Dispatching Agents` 는 못박는다 — *"When dispatching via the Agent tool, **always pass `model=` explicitly** — do not rely on session inheritance."* 그런데 **11개 커맨드 전체에 `model=` 이 0건**이다(실측). dispatch 하는 커맨드 4개(`implement-task`·`process-tasks`·`refactor-code`·`review-task`)가 전부 agent frontmatter 기본값으로 조용히 떨어진다.

**② 그 기본값이 contract 작업을 Sonnet 으로 보낸다.** `api-designer`(`model: sonnet`)·`event-architect`(`model: sonnet`) — 그런데 CLAUDE.md 의 모델 표는 ***"contract design"* 과 *"event-driven outbox"* 를 명시적으로 Opus 티어**로 지정한다. ①과 합치면 **모든 contract-change dispatch 가 실제로는 Sonnet 으로 실행된다.** 규칙은 문서에 있고, 실행 경로에는 없다.

**③ 커맨드가 에이전트의 `Does NOT` 를 어기라고 지시한다.**
- `implement-task.md` 의 `contract-change` 템플릿이 **api-designer/event-architect 에게 *"Then implement against the updated contracts"*** 를 주입한다. 두 에이전트의 `## Does NOT` 첫 줄은 ***"Write implementation code"* 금지**다.
- 같은 파일의 `simple-refactor` 경로는 **green baseline 없이** 리팩토링시킨다(템플릿이 *구현 → 테스트* 순). `refactoring-engineer.md` 의 `Does NOT` 은 ***"Refactor without a green test baseline"*** 다. **같은 일을 하는 `refactor-code.md` 는 올바르게 baseline → refactor → retest 순서**라, 이건 한계가 아니라 **누락**이다.

**실패 모드는 "잘못한다" 가 아니라 "규칙을 적어 뒀으니 지켜지는 줄 안다" 이다** — `MONO-402`(훅이 호출조차 안 됨) · `MONO-405`(픽스처를 아무도 안 돌림) · `MONO-407`(필터가 아무것도 안 거름)과 **같은 축**이다. 선언과 배선이 갈라졌다.

---

# ⚠️ 착수 전 필독 — `.claude/` 는 에이전트에게 하드블록이다

이 task 의 대상 파일은 **`.claude/commands/**` 와 `.claude/agents/**`** 이고, 하네스 classifier 가 **에이전트의 이 경로 편집·커밋을 승인이 있어도 차단**한다(실측 지도: `hooks/`·`agents/`·`commands/` 차단). ⇒ **구현자는 패치를 만들어 사용자에게 넘기거나**, 사용자가 auto mode 를 벗어난 상태(`Shift+Tab`)에서 적용해야 한다. **차단을 우회하려 명령을 재구성하지 말 것.** 이 제약을 모른 채 착수하면 마지막 단계에서 막힌다.

---

# Scope

## In Scope

1. **`.claude/commands/{implement-task,process-tasks,refactor-code,review-task}.md`** — 각 Agent dispatch 지점에 **`model=` 명시 단계**를 추가. 선택 기준은 CLAUDE.md 의 표를 인용한다(복잡 도메인=Opus / CI·docs·단순 fix=Sonnet~Haiku). **규칙을 재서술하지 말고 CLAUDE.md 를 가리켜라**(정경은 하나).
2. **contract-change 분기** — `implement-task.md` 의 `categoryInstructions` 를 **두 갈래로 쪼갠다**: api-designer/event-architect 에게는 *계약만*, 뒤따르는 backend/frontend-engineer 에게는 *구현만*. 지금은 한 블록이 둘 다에게 간다.
3. **simple-refactor 분기** — `refactor-code.md` 와 동일하게 **baseline 테스트 → 리팩토링 → 재테스트** 순서를 `implement-task.md` 의 `simple-refactor` 전용 블록으로 명시.
4. **모델 티어 결정(AC-3)** — `api-designer`/`event-architect` frontmatter 를 `opus` 로 올릴지, 아니면 커맨드가 contract-change 때만 `model="opus"` 를 넘길지 **하나를 골라 기록**한다. 둘 다 하면 이중 관리다.

## Out of Scope

- 새 커맨드·새 에이전트 신설.
- `model=` 값을 **자동 추론**하는 로직(휴리스틱을 심으면 그게 또 다른 미검증 선언이 된다). 사람이 읽고 고르는 명시 단계면 충분하다.
- `qa-engineer` 를 어떤 커맨드도 dispatch 하지 않는 문제(별건 — `MONO-413` 후보. 이건 배선이 아니라 **역할 경계** 질문이다).

---

# Acceptance Criteria

- [ ] **AC-0 (재측정 — 이 티켓의 숫자를 믿지 마라)** 착수 시 `grep -rn "model=" .claude/commands/` 를 **직접 다시 돌려** 0건임을 확인하고, agent frontmatter 의 현재 `model:` 값을 전부 나열한다. **감사 보고서는 출처가 아니라 가설이다** — 이 티켓을 쓴 감사가 같은 주에 자기 티켓에 틀린 문장 3개를 실었다.
- [ ] **AC-1** dispatch 하는 커맨드 4개 전부에 `model=` 명시 단계 존재. **CLAUDE.md 를 가리키되 규칙 본문을 복사하지 않는다.**
- [ ] **AC-2** `implement-task.md` 의 contract-change 가 **계약 에이전트에게 구현을 지시하지 않는다** — 두 에이전트의 `Does NOT` 와 충돌 0. simple-refactor 는 **baseline 이 먼저**다.
- [ ] **AC-3** 모델 티어 결정(frontmatter vs 커맨드 인자) 하나를 골라 **이유와 함께** 기록. contract 작업이 Opus 로 가는 경로가 **실제로 하나 이상 존재**함을 문서로 보인다.
- [ ] **AC-4 (도달성 — 이 티켓의 요점)** 수정이 **실행 경로에 있는지** 확인한다. *"커맨드 파일에 문장을 넣었다" ≠ "dispatch 가 그 모델로 간다."* 최소한 **한 번 실제로 dispatch 해 보고**(예: 사소한 review-task) 사용된 모델을 확인하거나, 그것이 불가능하면 **왜 확인 불가인지**를 적는다. **"고쳤다"를 문서로만 증명하지 말 것 — 그게 이 티켓이 고발하는 결함 그 자체다.**
- [ ] **AC-5** `.claude/` 하드블록 때문에 적용을 사용자에게 넘긴 경우, **무엇을 넘겼고 사용자가 무엇을 적용했는지** 기록(적용 안 된 패치가 "완료"로 닫히는 것이 이 결함의 재발이다).

---

# Related Specs

- `CLAUDE.md` § Recommending Tasks and Dispatching Agents (**정경 — 모델 선택 규칙**)
- `.claude/agents/common/{api-designer,event-architect,refactoring-engineer}.md` (각 `## Does NOT`)
- `.claude/commands/{implement-task,process-tasks,refactor-code,review-task}.md`
- 같은 실패 모드의 선례: `tasks/done/TASK-MONO-402-*`(훅 배선) · `TASK-MONO-405-*`(픽스처 미실행) · `TASK-MONO-407-*`(필터 무력)

# Related Skills

N/A.

---

# Related Contracts

None.

---

# Target Service

N/A — `.claude/` 에이전트 배선.

---

# Implementation Notes

- **`.claude/` 편집이 막히면 그건 버그가 아니라 설계다** — 패치를 프로즈로만 넘기지 말고 **적용 가능한 정확한 텍스트**(파일·앵커·전후 문맥)로 넘겨라. 프로즈로 넘기면 사용자가 옮겨 적다 빠뜨린다(선례 있음).
- 커맨드는 **읽는 사람이 사람이자 에이전트**다 — "적절한 모델을 고르라" 는 지시는 도달하지 않는다. **표를 가리키고, 기본값을 명시**하라.

---

# Edge Cases

- **process-tasks 의 dry-run 발산** — `implement-task` 는 *"`--dry-run` 이면 여기서 멈춘다"* 인데 `process-tasks` 는 *"dry-run 계속 진행"* 이다. **의도된 확장일 수 있다** — 그렇다면 그렇게 적어라(지금은 아무 말이 없어 드리프트로 읽힌다). 고치는 게 아니라 **선언하는 것**이 답일 수 있다.
- `implement-task.md` 의 subagent 매핑이 `simple-code`·`code-with-event`·`cross-service` 카테고리에 대해 **아무 말도 안 한다**(Target Service 접두사로 추론하라는 규칙이 어디에도 없다). 명시할 것.

---

# Failure Scenarios

- **문서만 고치고 도달성을 확인 안 한다** → 이 티켓이 고발한 결함을 그대로 재현한다. 완화 = AC-4.
- **모델 규칙을 커맨드에 복사** → 정경이 둘이 되고 갈라진다. 완화 = AC-1(포인터만).
- **`.claude/` 블록을 우회하려 시도** → 분류기 정면 충돌. 완화 = 위 § 필독 + AC-5.

---

# Test Requirements

- CI 로는 증명 불가(`.claude/` 는 코드 잡이 안 본다). **증명은 실제 dispatch 관측**(AC-4)이다.

---

# Definition of Done

- [ ] 커맨드 4개 + 에이전트 2개(또는 커맨드 인자) 수정 적용 확인.
- [ ] AC-4 도달성 관측 기록.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-15 `/validate-rules` 전수 스캔(141 파일). Critical 22건 중 **직접 실측으로 확인한 6건**에 속한다(`grep -rn "model=" .claude/commands/` = 0건 · `api-designer`/`event-architect` = `model: sonnet`).

분석=Opus 4.8 / 구현 권장=**Opus**(에이전트 계약·모델 티어 결정이 걸려 있고, `.claude/` 하드블록 협상이 필요하다. 단순 문서 편집이 아니다).
