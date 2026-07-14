# Task ID

TASK-MONO-402

# Title

🔴 **`main` 브랜치 보호에 문이 두 개인데 하나만 잠겨 있다** — 세 개의 git 안전 훅이 `matcher: "Bash"` 뿐이라 **PowerShell 도구로는 전부 통과한다**(실측). 그리고 HARDSTOP-05 는 `done/` 을 frozen 이라 *말하지만* 정규식이 안 잡는다

# Status

ready

# Owner

monorepo

# Task Tags

- security
- adr
- cleanup

---

# Dependency Markers

- **발굴**: `TASK-MONO-396` 종결 후, *"커맨드 층에서 드리프트가 3곳 나왔다면 다른 집행 층은?"* 이라는 질문으로 `agents/`·`skills/`·`platform/`·`hooks/` 전수 스윕 중.
- **선행**: 없음.
- **연관**: `TASK-MONO-360`(가드 도달성 — *가드는 무는가뿐 아니라 **물 기회를 얻는가***). 이 task 는 그 명제의 **훅 판**이다.

---

# Goal

**훅이 감시한다고 선언한 것을 실제로 감시하게 만든다.**

두 갭이다. 하나는 **도달성**(가드가 문 하나만 지킨다), 하나는 **선언↔진실**(메시지가 정규식보다 넓다).

---

# 실측 (2026-07-14, `origin/main` `460f95228` — 착수 시 **다시 확인할 것**)

## 🔴 F1 — `main` 보호가 PowerShell 도구를 못 본다 (**라이브 실측**)

`.claude/settings.json` 의 `PreToolUse` 배선:

```json
{ "matcher": "Bash", "hooks": [
    protect-main-branch.ps1,
    warn-shared-checkout-switch.ps1,
    block-task-checkout-in-main.ps1 ] }
```

**matcher 가 `"Bash"` 하나다.** 이 하네스에는 **`PowerShell` 도구가 별개로 존재**하고, 그 도구 설명은 *"This tool is for terminal operations via PowerShell: **git**, npm, docker…"* 라고 **명시적으로 git 을 권한다.**

**같은 명령 문자열, 두 도구 — 실측:**

| 도구 | `git push --dry-run origin main` |
|---|---|
| **Bash** | **차단** — `main/master branch protection: direct push, force push, or hard reset to main/master is blocked.` |
| **PowerShell** | **실행됨** — `Everything up-to-date` |

**정규식이 틀린 게 아니다. 훅이 호출되지도 않는다.**

⇒ 같은 이유로 **세 훅 모두** PowerShell 경로에서 무력:

| 훅 | 못 막는 것 |
|---|---|
| `protect-main-branch.ps1` | `main` 직접 push · force push · `reset --hard origin/main` |
| `warn-shared-checkout-switch.ps1` | 더러운 공유 체크아웃에서의 브랜치 전환 |
| `block-task-checkout-in-main.ps1` | 메인 체크아웃에서 task 브랜치 checkout |

**🎯 이것이 심각한 이유는 "악의적 우회" 가 아니라는 데 있다.** 우회를 시도해야 뚫리는 구멍이 아니라, **정상적으로 도구를 고르면 그냥 새어 나간다.** `protect-main-branch.ps1` 주석이 스스로 적어 둔 그 인시던트 —

> *"the 2026-05-25 fan-platform 8-project sweep had exactly this leak: one agent's worktree ended up on main HEAD, `git push -u origin HEAD` passed the regex, and **origin/main was force-rolled back to 11a3d9b0 to recover**."*

— 를 막으려고 존재하는 방어선인데, **그 방어선을 우회하는 데 필요한 것은 도구 드롭다운에서 다른 항목을 고르는 것뿐이다.**

**⚠️ 그리고 이 갭은 사람 규율로 가려져 있었다**: 유저 전역 `CLAUDE.md` 가 *"Bash 는 git/npm/npx 에만"* 이라 적어 둬서 **실제로는 git 이 늘 Bash 로 갔다.** 규약이 메커니즘의 구멍을 덮고 있었을 뿐, **메커니즘은 처음부터 반쪽이었다.**

## F2 — HARDSTOP-05 는 `done/` 을 frozen 이라 *말하지만* 안 잡는다

`.claude/hooks/hardstop-detect.ps1`:

- **L139 정규식**: `'(?:^|/)tasks/(in-progress|review)/[^/]+\.md$'` — **`done` 없음.**
- **L143 메시지**: *"``in-progress`` / ``review`` / **``done``** files must not be modified…"*
- **L144 WHY**: *"``in-progress/`` / ``review/`` / **``done/``** tasks are frozen"*

**`tasks/done/` 아래 task 파일은 아무 제약 없이 편집된다** — 훅은 자기가 지킨다고 두 번 말한 것을 지키지 않는다. `done/` 은 **무슨 일이 있었는지에 대한 저장소의 기록**이고, 그걸 조용히 고쳐 쓰는 것을 아무도 안 본다.

## F3 (기록만) — Edit/Write 훅은 셸 경유 파일 변경을 못 본다

`hardstop-detect`·`spec-check`·`rule-consistency-check`·`verify-worktree-isolation` 은 `matcher: Edit|Write` 다. **Bash/PowerShell 로 파일을 쓰면(`Set-Content`, `>` 리다이렉트) 전부 우회된다.**

**이건 F1 과 성격이 다르다** — 유저 전역 규칙이 *"파일 읽기/쓰기는 Read/Edit/Write 도구로"* 라고 이미 못 박고 있고, 메모리도 *"Edit 블록을 셸로 우회하지 말라"* 를 금지로 명시한다. **즉 규약이 이 경로를 이미 닫았다.** F1 은 반대로 **규약이 그 경로를 권한다.** ⇒ **F3 은 이 task 에서 고치지 않는다. § 결정 지점에 남긴다.**

---

# Scope

**포함:**

1. **F1** — `.claude/settings.json` 의 세 git 훅 matcher 를 **Bash + PowerShell 둘 다** 매칭하도록 확장.
2. **F1 검증** — 두 도구 **모두**에서 차단됨을 **라이브 실측**(양성 대조 포함).
3. **F2** — `hardstop-detect.ps1` L139 정규식에 `done` 추가(메시지와 일치시킨다).
4. **F2 검증** — `done/` 파일 임의 편집이 차단되고, **lifecycle Status move 는 여전히 통과**함을 실측(close-chore 를 깨뜨리면 안 된다 — 이게 이 수정의 유일한 위험이다).

**제외:**

- **F3(Edit/Write 훅의 셸 우회)** — 별개 판단. § 결정 지점.
- **훅 로직 자체의 정규식 개선**(`protect-main-branch.ps1` 의 push 패턴 등) — 지금 것은 Bash 에서 정상 작동한다. **도달성만 고친다.**

---

# Acceptance Criteria

- **AC-1 (F1 실측 재확인)** — 착수 시 **두 도구로 같은 문자열**(`git push --dry-run origin main` — 실행돼도 무해)을 돌려 **Bash=차단 / PowerShell=실행** 을 재현한다. **재현되지 않으면 STOP 하고 보고**(하네스가 그사이 바뀐 것이다).
- **AC-2 (F1 수정)** — matcher 가 `PowerShell` 도구도 잡는다. Claude Code hook matcher 는 정규식이므로 `"Bash|PowerShell"` 형태를 쓰되, **실제로 발화하는지 문서가 아니라 실측으로 확인**한다.
- **AC-3 (F1 무손실)** — 수정 후 **Bash 경로는 여전히 차단**(회귀 없음), **PowerShell 경로도 차단**. **그리고 정상 명령은 여전히 통과**해야 한다(`git status`, feature 브랜치 push) — **오탐 0 이 무는 것만큼 중요하다**(`MONO-360`: 첫날 RED 인 가드는 꺼지고, 꺼진 가드는 없는 가드보다 나쁘다).
- **AC-4 (F2 수정)** — 정규식이 `done` 을 포함하고, 메시지와 정규식이 **같은 집합**을 말한다.
- **AC-5 (F2 의 유일한 위험 — close-chore 를 깨뜨리지 말 것)** — `done/` 이 frozen 이 되면 **close chore 의 `Status: review → done` 편집이 `done/` 안에서 일어난다**(`git mv` 후 편집이 정식 절차 — `/review-task` § Close Chore). ⇒ **`$isLifecycleMove` 예외가 그 편집을 계속 통과시키는지 반드시 실측**한다. **통과하지 못하면 이 수정은 close-chore 를 전면 차단하고, 그건 F2 를 고치는 대신 파이프라인을 부수는 것이다.**
- **AC-6 (mutation 으로 증명)** — 각 수정에 대해 **가드가 없던 시절의 행위를 재현**해 실제로 막히는지 본다. 단언이 아니라 **관측**으로.
- **AC-7 (검증 선언 — 문서/설정 task)** — 코드 테스트는 없다. **verification = AC-1·AC-3·AC-5 의 라이브 프로브**(각각 양성 대조 포함). **훅은 CI 가 돌리지 않는다 — CI-GREEN 은 이 task 의 증거가 아니다.**

---

# Related Specs

- `.claude/settings.json` — 훅 배선(**F1 의 수정 지점**)
- `.claude/hooks/protect-main-branch.ps1` — 주석에 2026-05-25 인시던트 기록(이 방어선이 왜 있는가)
- `.claude/hooks/hardstop-detect.ps1` L113–144 — **F2 의 수정 지점**
- `.claude/hooks/warn-shared-checkout-switch.ps1` · `block-task-checkout-in-main.ps1` — F1 의 나머지 두 피해자
- `CLAUDE.md` § Task Rules — `git mv review/ → done/` 재-stage 규칙(**AC-5 가 지켜야 할 것**)
- `.claude/commands/review-task.md` § Close Chore — `TASK-MONO-396` 이 신설. **`done/` 안에서의 Status 편집을 정식 절차로 규정한다** ⇒ AC-5 의 근거

# Related Contracts

**없다** — 에이전트 하네스 설정. 런타임 API/이벤트 표면 없음.

---

# Edge Cases

- **⚠️ `.claude/hooks/` 는 classifier 하드블록 가능성이 남아 있다.** `TASK-MONO-396` 에서 **`.claude/commands/` 는 통과**했지만(메모리 `env_classifier_claude_self_mod_block` 의 commands 부분은 반증됨), **`hooks/` 는 아직 반증되지 않았다** — 오히려 그 메모리가 *"hook 은 intent-resistant, 사용자가 명령해도 안 풀린다"* 고 두 번 관찰했다. **막히면 우회하지 말고 패치를 사람에게 넘긴다.** `settings.json` 은 hook **파일**이 아니므로 통과할 수도 있다(별개로 확인).
- **matcher 문법을 문서로 추측하지 말 것.** `"Bash|PowerShell"` 이 정규식으로 먹는지, 아니면 배열/별도 블록이 필요한지 — **실측으로 확정**한다. 안 먹으면 블록을 하나 더 두면 된다.
- **`git push --dry-run origin main` 은 프로브로 안전**하다(실행돼도 push 안 함). **다른 프로브를 고르지 말 것** — `reset --hard` 나 진짜 push 는 훅이 안 잡으면 **실제로 실행된다**.
- **`done/` frozen 이 과하게 물면 close-chore 가 죽는다**(AC-5). 이게 이 task 에서 **유일하게 무언가를 부술 수 있는 지점**이다.

# Failure Scenarios

- **matcher 를 고쳤는데 실제로는 발화하지 않는다** → 설정 파일은 "고쳐진 것처럼 보이고" 구멍은 그대로다. **문서·직관이 아니라 프로브로 확인.** Guard: AC-2·AC-3 (양성 대조 포함).
- **`done` 을 정규식에 넣었더니 close-chore 가 전부 막힌다** → F2 를 고치고 파이프라인을 부순다. Guard: **AC-5 를 먼저 실측**, 안 되면 STOP.
- **가드가 오탐을 낸다** → 꺼진다. 꺼진 가드는 **skip 이 초록으로 보고되므로** 없는 가드보다 나쁘다(`MONO-360`). Guard: AC-3 의 "정상 명령은 통과" 절반.
- **F3 까지 손대서 범위가 터진다** → Edit/Write 훅의 셸 우회는 **규약이 이미 닫은 경로**다. Guard: § Scope 제외.
- **훅 수정이 classifier 에 막히자 셸로 파일을 쓴다** → **그것이 정확히 F3 이 설명하는 구멍이고, 명시적 금지다.** Guard: Edge Case 1 — **패치를 넘기고 멈춘다.**

---

# 🔴 결정 지점 (사람) — F3

**Edit/Write 매칭 훅(`hardstop-detect`·`spec-check`·`rule-consistency-check`·`verify-worktree-isolation`)을 셸 도구까지 확장할 것인가?**

- **(A) 하지 않는다 — 기본 권고.** 규약(유저 전역 `CLAUDE.md` + 메모리)이 *"파일은 Read/Edit/Write 도구로, 셸로 우회 금지"* 를 이미 닫아 두었다. 셸 명령 문자열에서 "어떤 파일을 쓰려는지" 를 정확히 파싱하는 것은 **취약하고 오탐을 낳는다** — 그리고 **오탐 나는 가드는 꺼진다.**
- **(B) 확장한다.** F1 과 같은 논리("규약이 아니라 메커니즘으로 막아라")를 끝까지 밀면 여기까지 온다. **비용**: 셸 파싱은 F1 의 matcher 한 줄과 달리 **진짜 엔지니어링**이고 오탐 위험이 크다.

> **F1 과 F3 의 차이가 이 결정의 전부다**: F1 은 **규약이 그 경로를 *권한다*** (도구 설명이 git 을 PowerShell 로 안내). F3 은 **규약이 그 경로를 *금지한다*.** 규약이 권하는 구멍은 메커니즘으로 막아야 하고, 규약이 금지하는 구멍은 규약으로 충분할 수 있다.

---

# Provenance

`TASK-MONO-396`(커맨드 4개가 `CLAUDE.md` 가 금지하는 것을 지시하고 있었다)을 종결한 뒤, **"한 집행 층에서 3곳이 나왔다면 나머지 층은?"** 이라는 질문으로 `agents/`(15) · `skills/`(80+) · `platform/`(30+) · `hooks/`(12) 를 전수 스윕했다.

**문서 층은 깨끗했다** — `agents/` 는 lifecycle/git 기계를 아예 언급하지 않고(양성 대조로 도달성 확인: task/review/spec 105건 잡힘, git/merge 0건), `skills/`·`platform/` 의 히트는 전부 정당했다. **드리프트는 문서가 아니라 *배선* 에 있었다.**

**그리고 그것을 찾은 방법이 요점이다**: `settings.json` 을 *읽고* matcher 가 `"Bash"` 하나인 것을 보았을 때 그것은 **가설**이었다. **같은 명령을 두 도구로 실제로 돌려 본 뒤에야 사실이 됐다** — Bash 차단 / PowerShell 실행. **읽어서 의심하고, 돌려서 확정했다.**

**이 저장소가 `MONO-359`·`360`·`368` 에서 배운 명제의 훅 판이다**: *가드는 무는가뿐 아니라 **물 기회를 얻는가**.* 거기서는 paths-filter 가 가드를 도달불가로 만들었다. 여기서는 **matcher 가 그렇게 한다.** 그리고 **둘 다 "가드가 있다" 는 사실만 보면 초록으로 보인다.**

분석=Opus 4.8 / 구현 권장=**Opus** (훅/보안 배선이고, **AC-5 를 잘못 다루면 close-chore 파이프라인을 전면 차단한다.** `hooks/` 가 classifier 에 막히면 **우회 금지, 패치를 사람에게**.)
