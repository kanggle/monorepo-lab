# Task ID

TASK-MONO-692

# Title

🔴 빨간 nightly 이슈가 **아무 ref 의 초록 런 하나로** 닫힌다 — 브랜치 dispatch 가 「main 이 초록」을 대신 말했다 (+ HARDSTOP-05 훅이 자기 remediation 이 시키는 append 를 거절한다)

# Status

done (2026-09-18 UTC)

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

- [x] **AC-0 재측정** — ①은 잡 11 의 코드를 읽어 «어떤 런을 초록으로 세는가» 를 확인하고(이 티켓의 표는 이슈/런 API 관측이다), ②는 `---` 있는 판과 없는 판을 각각 시도해 **재현**한다. 둘 중 하나가 재현되지 않으면 그 항목은 **닫지 말고 사유와 함께 기록**한다.
- [x] **AC-1 ①의 술어** — 이슈를 닫는 초록 런은 **기본 브랜치의 런**이어야 한다(`github.ref == 'refs/heads/main'` 또는 그에 준하는 판정). 🔵 `main` 에서의 `workflow_dispatch` 는 **닫아도 된다**(그 런이 잰 트리가 main 이다) — 판정 축은 event 가 아니라 **ref** 다.
- [x] **AC-2 ①의 비공허성** — 브랜치 런이 닫지 **못한다**는 것을 증명한다(bite: 브랜치 dispatch 초록 뒤 이슈 상태 불변). 열린 이슈가 없을 때 조용히 no-op 하는 기존 동작은 유지.
- [x] **AC-3 ②의 결정과 집행** — 훅이 `---` 를 허용하도록 고치거나, remediation 문구가 **정확한 형태**(구분선 금지)를 말하도록 고친다. 어느 쪽이든 **훅 픽스처**(`tests/hooks/**` 또는 그 등가물)에 «허용되는 append» 와 «거절되는 편집» 두 칸을 남긴다.
- [x] **AC-4 회귀 없음** — 여는 쪽(빨강 → 이슈)과 `review/` 동결의 나머지 축은 변하지 않는다. 훅 픽스처 잡(`Hook fixtures (Windows PowerShell …)`)이 초록.

---

# 🟢 AC-0 — 재측정 (2026-09-18 UTC)

## ① `nightly-red-arrives` 의 닫기 술어가 실제로 무엇을 초록으로 세는가

`.github/workflows/nightly-e2e.yml` 잡 `nightly-red-arrives`, 스텝 "빨간 잡을 세고, 이슈를 열거나 닫는다"(수정 전, `origin/main` 5edf53592 기준):

```bash
existing=$(gh issue list ... --search "in:title \"자동 감시 — TASK-MONO-655\"" ...)
if [ -z "$red" ]; then
  if [ -n "$existing" ]; then
    prev=$(gh issue view "$existing" ... | sed -n '1s/^\*\*빨간 잡\*\*: //p')
    verdict=$(node scripts/nightly-close-predicate.mjs --needs "$NEEDS" --prev "$prev")
    if [ "$verdict" = "close" ]; then gh issue close "$existing"; fi
  fi
fi
```

`scripts/nightly-close-predicate.mjs` (수정 전) `decide(needs, prev)` 는 `needs`(= `toJSON(needs)`, 이 워크플로 런 자신의 잡 결과)와 이슈 본문이 기억하는 `prev`(그때 빨갰던 잡 이름들)만 인자로 받는다. `ref`/`event_name`/`sha` 는 함수에 **전달되지도 않는다** — `decide()` 시그니처 자체에 ref 파라미터가 없었다. 닫기 조건은 순수하게 "**이 런에서** `prev` 에 있던 잡들이 전부 `success` 였는가" 뿐이고, "이 런" 이 `main` 위였는지 브랜치 위였는지는 **아무 데서도 보지 않는다**. `workflow_dispatch` 는 `on:` 트리거에 이미 등록돼 있어 브랜치에서 실행 가능하고(`gh workflow run nightly-e2e.yml --ref <아무 브랜치>`), 그 런의 `needs` 도 동일한 `toJSON(needs)` 형태로 스크립트에 들어간다 ⇒ **재현됨**: 실측 표(§ Goal ①)가 기록한 #3846 이 정확히 이 경로로 닫혔다 — 이슈 코멘트가 인용한 `1ab6bd9e301f…(workflow_dispatch)` 커밋은 수정 브랜치의 것이지 `main` 의 것이 아니다.

## ② HARDSTOP-05 훅의 `---` 판/무판 재현

사전-수정 훅(`origin/main` 5edf53592 의 `.claude/hooks/hardstop-detect.ps1`, `git show`로 추출해 스크래치패드에 보존)에 동일한 `old_string`/`new_string` 쌍 두 개를 그대로 흘렸다(`tasks/review/…` 파일 대상, `Edit` 페이로드):

```
node -equivalent: powershell -NoProfile -ExecutionPolicy Bypass -File <원본 훅> < <payload.json>

(A) new_string = old_string + "\n\n## CORRECTION (post-close)\n\n appended, no separator."
(B) new_string = old_string + "\n\n---\n\n## CORRECTION (post-close)\n\n appended, WITH separator."
```

결과 (스크래치패드 `ac0-repro-out.txt`):

```
=== (A) no --- separator ===
rc=0
output=                                              <- 빈 출력 = 허용(silent allow)

=== (B) with --- separator ===
rc=0
output={"decision":"block","reason":"[VIOLATION] HARDSTOP-05: ... [REMEDIATION] ... 4. ... append a correction section — a heading matching `## CORRECTION` at the END of the file, adding only, deleting nothing ..."}
```

**재현됨** — (A) 통과, (B) 거절. 거절된 (B)의 `reason` 이 인용하는 remediation #4 는 정확히 (B) 모양(구분선 + heading)을 하라고 말하는 문구이며, `---` 를 금지한다는 말은 어디에도 없다. rc 는 두 경우 모두 0(훅이 JSON 을 stdout 에 찍고 exit 0 하는 것이 정상 동작 — `decision` 필드로 판정한다).

# 🟢 AC-1 — ①의 술어를 ref 로 좁힘 (구현)

`scripts/nightly-close-predicate.mjs`: `decide(needs, prev, ref)` 로 시그니처 확장. `ref !== 'refs/heads/main'` 이면 `needs` 를 보기도 전에 `hold` — 판정 축이 event 가 아니라 ref 임을 코드로 고정(`main` 위의 `workflow_dispatch` 도 ref 는 `refs/heads/main` 이므로 통과). `.github/workflows/nightly-e2e.yml`: `REF: ${{ github.ref }}` env 추가, `node scripts/nightly-close-predicate.mjs --needs "$NEEDS" --prev "$prev" --ref "$REF"` 로 전달. 닫힘 코멘트 본문에도 `${REF}` 를 실어 감사 흔적을 남김.

# 🟢 AC-2 — 비공허성 bite (CLI, 워크플로가 쓰는 그 인터페이스로 직접)

```
$ node scripts/nightly-close-predicate.mjs --needs '{"a":{"result":"success"}}' --prev "a" --ref "refs/heads/task/pc-fe-289-fix"
rc=0
stdout: hold
stderr: 이 런의 ref 가 기본 브랜치가 아니다(`refs/heads/task/pc-fe-289-fix`, 기대 `refs/heads/main`) ⇒ 이 런은 «main 이 초록인가» 를 말할 수 없다.

$ node scripts/nightly-close-predicate.mjs --needs '{"a":{"result":"success"}}' --prev "a" --ref "refs/heads/main"
rc=0
stdout: close
stderr: ref 는 기본 브랜치(`refs/heads/main`)이고, 그때 빨갰던 잡이 전부 이번 런에서 돌았고 success 다: ✔ a

$ node scripts/nightly-close-predicate.mjs --needs '{"a":{"result":"failure"}}' --prev "a" --ref "refs/heads/main"
rc=0
stdout: hold
stderr: 그때 빨갰던 잡이 전부 회수됐다고 말할 수 없다: ✖ a — failure ⇒ 아직 빨강
```

같은 `needs`/`prev`(고정) 로 `ref` 만 바꿔 **branch=hold / main=close** 로 갈라짐을 직접 증명 — 브랜치 dispatch 초록은 이슈 상태를 바꾸지 못한다(hold = 워크플로가 `gh issue close` 를 호출하지 않는 경로). `main` 이 여전히 빨간 경우(bite 3) hold 유지도 확인 — 회귀 없음. `node scripts/nightly-close-predicate.mjs --self-test`: **10/10 통과, rc=0**(케이스 (8)(9)(10) 이 ref 축 전용, needs/prev 고정하고 ref 만 바꿔 갈라지는지 별도로 재단언). "열린 이슈가 없을 때 조용히 no-op" 은 워크플로 구조상 `decide()` 호출 자체가 `[ -n "$existing" ]` 안에서만 일어나므로 이번 변경으로 건드리지 않았다(코드 diff 확인 — 그 분기 바깥 구조 무변경).

# 🟢 AC-3 — ②의 결정과 집행

**결정: 훅을 느슨하게 고쳤다(훅이 옳아지는 쪽).** 이유: 이 저장소의 모든 task 파일이 섹션을 `---` 로 구분하는 것이 기존 관행이고(이 파일 자체가 그 예), 훅 자신의 remediation #4 가 "append a correction section — 파일 끝에 `## CORRECTION`" 이라고 말하는 것은 "기존 섹션 모양을 따르라"로 읽히지 "구분선을 생략하라"로 읽히지 않는다. remediation 쪽을 고쳐 구분선을 금지하는 방향은 이 저장소의 보편적 섹션 관행에 예외를 하나 새로 만드는 셈이라 더 나쁘다.

구현: `(R2)` 블록에서 `appended` 를 줄 단위로 나눈 뒤, 첫 non-blank 줄이 `^-{3,}$`(구분선 단독 줄)이면 그다음 non-blank 줄을 heading 검사 대상으로 삼도록 확장. append-only 불변식(`new_string.StartsWith(old_string)`)은 그대로 — 구분선 한 줄만 추가로 허용했을 뿐, old_string 과 heading 사이에 다른 내용이 끼어들 수는 없다. remediation 문구(훅 + `platform/hardstop-rules.md` 양쪽, 드리프트 방지)에 "optionally preceded by a `---` section-separator … TASK-MONO-692" 를 명시.

픽스처 (`.claude/hooks/__tests__/hardstop-05-task-not-ready.ps1`, `tasks/done/` 대상 — Edge Cases 가 요구한 "done/ 에도 같은 판정" 을 만족):
- **allow-6**: `---` + `## CORRECTION` append → `Assert-Allowed` (accepted).
- **positive-8**: 같은 `---`+`## CORRECTION` 모양이지만 원문을 REWRITTEN 으로 바꾼 **편집** → `Assert-Stanza HARDSTOP-05 block` (rejected) — 구분선 허용이 rewrite 채널을 열지 않음을 별도로 증명.

```
$ powershell -NoProfile -ExecutionPolicy Bypass -File .claude/hooks/__tests__/run-all.ps1
rc=0
...
PASS: HARDSTOP-05 allow-6 (--- separator + ## CORRECTION append accepted) — MONO-692
PASS: HARDSTOP-05 positive-8 (--- separator cannot rescue a mutate-shaped edit) — MONO-692
...
All fixtures PASS
```

수정된 훅에 대해 AC-0 의 (A)/(B)/(C) 재실행(스크래치패드 `ac0-repro-fixed-out.txt`): (A) 허용(불변) · (B) **이제 허용**(고쳐짐) · (C, `---`+`## CORRECTION` 이지만 원문 변경) **여전히 거절**(rewrite 채널 아님 확인).

# 🟢 AC-4 — 회귀 없음

- 여는 쪽(빨강 → 이슈): 이번 diff 에서 미변경 — `red=$(...)` 계산, `gh issue create`/`gh issue edit`/코멘트 로직 손대지 않음(git diff 로 확인).
- `review/`/`done/` 동결의 나머지 축(비-correction 편집은 여전히 block, 라이프사이클 이동은 여전히 allow, conflict-marker 복구는 여전히 allow): `run-all.ps1` 전체 60+ 단언 **rc=0, All fixtures PASS**(위 로그) — `hardstop-05-task-not-ready.ps1` 의 기존 18칸(allow-1~5, positive-1~7 등)이 전부 그대로 PASS.
- 세 필수 게이트: `bash scripts/check-index-queue-drift.sh` rc=0 · `bash scripts/check-task-id-collision.sh` rc=0 · `bash scripts/check-walkthrough-ledger-drift.sh` rc=0.
- `scripts/` 에 파일 추가/삭제 없음(기존 `nightly-close-predicate.mjs` 수정만) ⇒ 전체 가드 스윕 불필요(`git status --porcelain` 로 확인 — `M scripts/nightly-close-predicate.mjs` 뿐).

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

- [x] AC-0~4
- [x] 이슈를 닫는 것이 **main 의 초록**일 때뿐임을 bite 로 증명 — § AC-2 CLI bite (branch=hold, main=close, still-red=hold)

분석=Opus 5 / 구현 권장=Sonnet 5 (워크플로 조건 + 훅 픽스처).
