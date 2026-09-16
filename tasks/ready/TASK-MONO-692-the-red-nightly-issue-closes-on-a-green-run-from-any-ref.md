# Task ID

TASK-MONO-692

# Title

🔴 빨간 nightly 이슈가 **아무 ref 의 초록 런 하나로** 닫힌다 — 브랜치 dispatch 가 「main 이 초록」을 대신 말했다 (+ HARDSTOP-05 훅이 자기 remediation 이 시키는 append 를 거절한다)

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- guard
- hooks

---

# Goal

두 관측을 고친다(둘 다 `TASK-PC-FE-282`·`289` 진행 중 실측, 2026-09-15 UTC). 공통점은 **술어가 자기가 재려던 것을 안 잰다**는 것이다.

## ① `nightly-red-arrives` 의 닫기 술어가 ref 를 안 본다

`TASK-MONO-655` 가 만든 잡 11(`nightly-e2e.yml`)은 빨간 런에 이슈를 열고 초록에 닫는다. 실측:

| 시각(UTC) | 사건 |
|---|---|
| 12:53:25 | `main` `35bd9d293` 런 34971551224 — `platform-console-e2e-fullstack` **failure** |
| 13:06 | 자동 이슈 **#3846 열림** (정상 동작) |
| 13:23~13:37 | 수정 브랜치 `task/pc-fe-289-…` 에서 **`workflow_dispatch`** 런 34974736858 — 같은 잡 **success** |
| **13:37:27** | **이슈 #3846 자동 닫힘** — 코멘트가 `1ab6bd9e301f…(workflow_dispatch)` 를 인용 |
| 13:44:01 | 수정이 **그제서야** `main` 에 머지(`46fae6fe7`) |

⇒ **7분 동안 「이슈는 닫혔는데 `main` 은 아직 빨간」 상태**였다. 이번엔 결과가 옳았지만(뒤이어 main nightly 34976921564 초록) 그 잡이 잰 것은 **「main 이 초록」이 아니라 「어딘가에서 그 잡이 초록이었다」** 다.

🔵 브랜치 dispatch 자체는 **권장 행위**다(`project_nightly_only_spec_merges_green_then_main_reds` 규율 — 머지 전에 증명). 그러니 dispatch 를 막는 게 아니라 **닫기 술어가 ref 를 봐야** 한다.

## ② HARDSTOP-05 훅이 `---` 뒤의 `## CORRECTION` append 를 거절한다

같은 세션에서 두 번 관측: `review/` 티켓 끝에 `---` 구분선 + `## CORRECTION` 을 붙이면 훅이 **거절**하고, 구분선을 빼면 **통과**한다. 🔴 훅 자신의 remediation 4번은 *"append a correction section — a heading matching `## CORRECTION` at the END of the file"* 라고 **시키고 있다** ⇒ 시키는 형태가 거절되면 다음 사람은 «append 가 금지된다» 로 읽는다(실제로 구현 에이전트가 그렇게 보고했다).

---

# Scope

## In Scope

- `.github/workflows/nightly-e2e.yml` 잡 11 의 **닫기** 술어
- `.claude/hooks/hardstop-detect.ps1` 의 CORRECTION append 판정 **또는** 그 remediation 문구(둘 중 무엇이 참이어야 하는지를 정하는 것이 이 티켓의 판단)

## Out of Scope

- 여는 쪽 동작(빨강 → 이슈 생성) — 정상 작동 중
- 브랜치 dispatch 를 막는 것 — 규율상 권장 행위다
- `review/`/`done/` 동결 자체를 느슨하게 하는 것

---

# Acceptance Criteria

- [ ] **AC-0 재측정** — ①은 잡 11 의 코드를 읽어 «어떤 런을 초록으로 세는가» 를 확인하고(이 티켓의 표는 이슈/런 API 관측이다), ②는 `---` 있는 판과 없는 판을 각각 시도해 **재현**한다. 둘 중 하나가 재현되지 않으면 그 항목은 **닫지 말고 사유와 함께 기록**한다.
- [ ] **AC-1 ①의 술어** — 이슈를 닫는 초록 런은 **기본 브랜치의 런**이어야 한다(`github.ref == 'refs/heads/main'` 또는 그에 준하는 판정). 🔵 `main` 에서의 `workflow_dispatch` 는 **닫아도 된다**(그 런이 잰 트리가 main 이다) — 판정 축은 event 가 아니라 **ref** 다.
- [ ] **AC-2 ①의 비공허성** — 브랜치 런이 닫지 **못한다**는 것을 증명한다(bite: 브랜치 dispatch 초록 뒤 이슈 상태 불변). 열린 이슈가 없을 때 조용히 no-op 하는 기존 동작은 유지.
- [ ] **AC-3 ②의 결정과 집행** — 훅이 `---` 를 허용하도록 고치거나, remediation 문구가 **정확한 형태**(구분선 금지)를 말하도록 고친다. 어느 쪽이든 **훅 픽스처**(`tests/hooks/**` 또는 그 등가물)에 «허용되는 append» 와 «거절되는 편집» 두 칸을 남긴다.
- [ ] **AC-4 회귀 없음** — 여는 쪽(빨강 → 이슈)과 `review/` 동결의 나머지 축은 변하지 않는다. 훅 픽스처 잡(`Hook fixtures (Windows PowerShell …)`)이 초록.

---

# Related Specs

- `.github/workflows/nightly-e2e.yml` (잡 11 `nightly-red-arrives`, `TASK-MONO-655`)
- `platform/hardstop-rules.md` HARDSTOP-05 · `tasks/INDEX.md` § Move Rules (동결 규칙)
- `platform/git-workflow-policy.md` § Post-Merge Nightly Check
- `projects/platform-console/tasks/done/TASK-PC-FE-289-the-login-fixture-starts-tracing-the-runner-already-started.md` § CORRECTION (①의 인계)

# Related Contracts

- 없음

---

# Edge Cases

- `main` 의 nightly 가 한동안 안 돌면(스케줄 18:00 UTC + push 트리거) 이슈가 **더 오래 열려 있게** 된다 — 그것이 옳은 상태다(빨간 것은 main 이다).
- 같은 이슈가 두 번 열리지 않도록 하는 기존 중복 방지는 유지.
- 훅: `done/` 파일에도 같은 판정이 적용되는지 확인(둘 다 동결).

# Failure Scenarios

- 술어를 ref 로 좁혔더니 **닫는 경로가 영영 없다**(예: main 런이 항상 다른 이름으로 돈다) ⇒ AC-2 의 bite 가 그것을 먼저 드러낸다.
- 훅을 느슨하게 고쳤더니 **중간 편집까지 통과** ⇒ AC-3 의 «거절되는 편집» 칸이 막는다.

# Test Requirements

- 훅 픽스처(PowerShell) 2칸 + 기존 잡 초록
- ①은 실제 dispatch 런으로 bite(브랜치에서 초록 → 이슈 불변)

# Definition of Done

- [ ] AC-0~4
- [ ] 이슈를 닫는 것이 **main 의 초록**일 때뿐임을 bite 로 증명

분석=Opus 5 / 구현 권장=Sonnet 5 (워크플로 조건 + 훅 픽스처).
