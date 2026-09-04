# Task ID

TASK-MONO-620

# Title

🔴🔴 **close chore 게이트의 세 축이 전부 «머지됐나»를 잰다** — «AC 가 닫혔나»를 재는 축이 없다

# Status

done

# Owner

monorepo

# Task Tags

- docs
- process

---

# 🔎 어디서 왔나 — `/audit-memory` 2026-09-03

주간 메모리 감사가 «공통 규칙 후보»로 올렸다. 에이전트 메모리
`feedback_post_merge_finalize_and_ask` 가 **두 번 재발한 결함**을 들고 있는데, 그 결함을
막을 문장이 `CLAUDE.md` 에 없다. 메모리는 이 세션에만 로드되므로 **다른 세션·다른 에이전트는
같은 결함을 그대로 밟는다.**

# 📐 결함 — 세 축이 같은 질문을 세 번 묻는다

`CLAUDE.md` § Task Rules 의 **Objective merge verification before any close chore** 는
`review/ → done/` 이동 전에 세 가지를 요구한다:

| 축 | 무엇을 재나 |
|---|---|
| (a) `gh pr view --json state,mergedAt,…` = `MERGED` | **머지됐나** |
| (b) `origin/main` tip = squash 커밋 | **머지됐나** |
| (c) 머지 시점 required check 0 실패 | **머지가 main 을 깼나** |

🔴 **셋 다 PR 의 성질이고, 티켓의 성질이 아니다.** 「이 티켓이 하기로 한 일이 실제로 됐나」를
묻는 축이 하나도 없다. 그런데 `done/` 은 **frozen 단계**라 — 잔여가 있어도 그 뒤로 아무도
안 본다.

## 실측 3건 (전부 이 저장소에 landed)

**① `TASK-BE-582`(iam) — 3-dim 통과인데 AC 가 반쪽이었다.**
`projects/iam-platform/tasks/done/TASK-BE-582-…md:261` 이 자기 입으로 적고 있다:

```
## AC-4 — 🔴 **절반만 충족됐다. 나머지를 적는다.**
| **기존 볼륨** 판정 | ⏳ **미수행** — CI 는 항상 신선 볼륨이라 … 영구히 초록이다 |
```

**② `TASK-MONO-605` AC-3 — AC 의 «동사»가 안 지켜졌는데 ✅ 가 붙었다.**
AC 본문(`:127`)은 *"판정 후 스냅샷 처분을 **명시한다** — 남길지 지울지"* 인데,
결과(`:254-257`)는 `## AC-3 — … ✅` 아래에 *"🙋 스냅샷 처분 = **소유자 결정**. **추천: 삭제**"*
로 끝난다. 🔴 **추천은 선택이 아니다.** 「명시한다」는 닫히지 않았는데 ✅ 가 붙었다.

**③ 역방향 — `TASK-MONO-574` AC-2 의 ⚪ 는 그 AC 의 «답»이었다.**
`tasks/done/TASK-MONO-574-…md:499`: *"AC-2 — 로그아웃 ⚪ **미측정, 그리고 측정 불가였다**"*.
AC-3 이 *"판정하지 못한 것을 함께 적는다"* 를 요구했으므로 **⚪ 가 충족이다.**
🔴 ⚪ 를 기계적으로 «미결»로 읽으면 이번엔 반대 방향으로 틀린다.

# 📋 모집단 — 이 축을 보는 가드가 하나도 없다 (2026-09-03 실측, main `486b889a9`)

- **required check 4종** — `changes` · `INDEX queue drift …` · `Task ID collision …` ·
  `Walkthrough limitation ledger drift …`. 넷 다 **큐 위치와 ID**를 재고, AC 본문은 안 읽는다.
- `scripts/check-index-queue-drift.sh` — `INDEX.md` 행 ↔ 큐 디렉터리. **AC 미확인.**
- 즉 **(d) 축은 사람/에이전트 판단으로만 닫힌다** ⇒ 문서에 없으면 존재하지 않는다.

# Goal

`review/ → done/` 이동의 판정에 **네 번째 축 (d) — 티켓 본문의 AC 절이 실제로 닫혔나** 를
추가한다. 카탈로그 한 줄은 `CLAUDE.md`, 근거·실측·역방향 함정은
`platform/git-workflow-policy.md` § Merge-Verification Worked Incident 에 둔다
(`CLAUDE.md` = 카탈로그, `platform/` = detail — 기존 분업 유지).

# Scope

**In scope**

- `CLAUDE.md` § Task Rules — 3-dim 불릿에 (d) 추가. 「세 dimension」 이라 쓴 문구를 넷으로 정정.
- `platform/git-workflow-policy.md` § Merge-Verification Worked Incident — (d) 의 근거 3건.

**Out of scope**

- 🔴 **새 CI 가드를 만들지 않는다.** AC 충족은 산문 판정이라 기계가 못 읽는다. 못 재는 것을
  재는 척하는 가드는 이 저장소가 이미 여러 번 밟은 함정이다
  (「가드의 술어가 틀림」). 필요하다면 별도 티켓에서 논한다.
- 기존 `done/` 티켓의 소급 재감사.

# Acceptance Criteria

## AC-1 — `CLAUDE.md` 가 (d) 를 **네 번째 dimension 으로** 명시한다

- 「three dimensions」 → 「four dimensions」 로 정정하고 (a)(b)(c) 뒤에 (d) 를 넣는다.
- (d) 의 술어는 **«큐 행» 이 아니라 «티켓 본문 AC 절»** 이라고 적는다 —
  `INDEX.md` 행은 이 축을 못 재기 때문이다.
- 미충족 시 처방을 적는다: **`review/` 에 두고 `## CORRECTION` 으로 남긴다**
  (`done/` 은 frozen 이라 잔여를 아무도 안 본다).
- 🔴 **역방향도 적는다**: ⚪ 가 «측정 불가를 기록함» 이면 그것이 그 AC 의 답일 수 있다.

## AC-2 — `platform/git-workflow-policy.md` 가 근거 3건을 **파일:줄** 로 든다

- BE-582 AC-4(절반 충족) · MONO-605 AC-3(동사 미충족에 ✅) · MONO-574 AC-2(⚪ 가 충족).
- 🔴 **인용은 원문 대조로 적는다** — 요약이 아니라 그 파일이 실제로 쓴 문구.

## AC-3 — 두 문서가 서로를 가리키고, **한 사실이 두 곳에서 갈라지지 않는다**

- `CLAUDE.md` 는 catalog 한 줄 + `platform/` 앵커 링크. 근거 서술은 **`platform/` 한 곳에만**.
- 🔴 같은 문장을 양쪽에 복사하지 않는다(한쪽만 고쳐지는 것이 이 저장소의 기존 실패 모드).

## AC-4 — 앵커가 실제로 해소된다

- `CLAUDE.md` 가 새로 거는 `platform/git-workflow-policy.md#…` 앵커가 실재하는 heading 을
  가리키는지 확인한다(오타 앵커는 조용히 죽는다).
- `scripts/check-claude-reference-integrity.sh` 를 돌려 rc 를 **명시적으로** 읽는다
  (🔴 파이프에 물리지 말 것 — `cmd > log 2>&1; echo rc=$?`).

# Related Specs

- `platform/git-workflow-policy.md` § Merge-Verification Worked Incident
- `tasks/INDEX.md` § lifecycle

# Related Contracts

없음 (문서 전용).

# Edge Cases

- **AC 가 아예 없는 티켓** — (d) 는 «AC 절을 열어 확인» 이므로, AC 절이 없으면 그 자체가
  HARDSTOP-07(acceptance criteria unclear) 쪽 문제다. (d) 가 그것을 대신 판정하지 않는다.
- **AC 가 소유자 대기로 열려 있는 경우** — 닫힌 것이 아니므로 `review/` 유지. 🙋 표시가
  붙었다고 «충족» 이 되지 않는다(MONO-605 가 정확히 그 경우다).
- **⚪ 판정** — 위 역방향. AC 가 «측정 못 한 것도 적어라» 를 요구했다면 ⚪ 는 충족이다.

# Failure Scenarios

| 시나리오 | 결과 | 방어 |
|---|---|---|
| (d) 를 «INDEX 행이 done 인가» 로 구현 | 지금과 똑같이 «머지됐나»만 잰다 | AC-1 이 술어를 «티켓 본문 AC 절» 로 못박음 |
| 근거를 `CLAUDE.md` 에도 복사 | 한쪽만 고쳐져 갈라진다 | AC-3 |
| ⚪ 를 일괄 «미결» 로 읽는 규칙 | MONO-574 류가 영원히 안 닫힌다 | AC-1 의 역방향 문장 |
| 앵커 오타 | 링크가 조용히 죽고 detail 을 아무도 못 찾는다 | AC-4 |

---

# 🔴 Scope 정정 (구현 중 발견) — **2 파일이 아니라 5 파일이다**

원래 Scope 는 `CLAUDE.md` + `platform/git-workflow-policy.md` 둘만 적었다. 구현 시작 시
**「세 dimension」이라는 «개수»를 말하는 자리**를 전수로 세니 **넷**이었다:

| 파일 | 자리 |
|---|---|
| `CLAUDE.md` | `:117` — *"Verify **three dimensions**"* · *"If any of the three fails"* |
| `platform/git-workflow-policy.md` | `:246` — *"The three-dimension objective merge verification"* |
| `.claude/commands/review-task.md` | `:51` — *"all three dimensions"* · `:221` — *"gated on 3-dimension merge verification"* |
| `.claude/commands/process-tasks.md` | `:78` — *"gated on 3-dimension merge verification of each impl PR"* |

🔴 **앞의 둘만 고치면 나머지 둘이 「3」인 채 남는다** — 그건 이 티켓이 막으려는 결함
(한 사실이 여러 집을 갖고 한쪽만 고쳐진다)을 **이 티켓 자신이 실행하는 것**이다.
⇒ 넷을 같은 PR 에서 옮겼다. 근거 서술은 여전히 `platform/` **한 곳에만** 둔다.

---

# ✅ 구현 결과 (2026-09-03 UTC)

## AC-1 — `CLAUDE.md` 가 (d) 를 네 번째 dimension 으로 명시한다 ✅

- *"Verify **three dimensions**"* → *"Verify **four dimensions**"*, *"If any of the three fails"*
  → *"If any of the four fails"*.
- (c) 뒤에 (d) 삽입. 술어를 **«티켓의 `# Acceptance Criteria` 절을 열어서 읽는다 — `INDEX.md`
  행이 아니라»** 로 못박았고, **«AC 가 쓴 동사에 대고»** 판정한다고 적었다
  (*"a recommendation does not close an AC that says «decide»"*).
- 미충족 처방 명시: **파일을 옮기지 않는다** — `review/` 에 두고 `## CORRECTION`.
  이유도 함께 적었다(`done/` 은 frozen 이라 그 뒤로 아무도 안 본다).
- 🔵 역방향 명시: ⚪ 가 *"could not be measured, and why"* 를 기록한 것이면, AC 가 그것을
  요구했을 때 **그게 그 AC 의 답**이다.
- 🔴 required check 이름 4종은 **한 글자도 안 건드렸다** — 스크립트로 재확인했다(아래 AC-4).

## AC-2 — `platform/` 이 근거 3건을 파일:줄로 든다 ✅

`## The Fourth Dimension — (d) Did the AC Actually Close?` 신설(`:262`). 표 3행:

- `TASK-BE-582`(iam) `AC-4` — AC 제목 자신이 *"🔴 절반만 충족됐다. 나머지를 적는다."*
- `TASK-MONO-605` `AC-3` — AC 본문 `:127` *"명시한다 — 남길지 지울지"* ↔ 결과 `:254-257`
  *"🙋 소유자 결정. 추천: 삭제"* 인데 제목엔 ✅.
- `TASK-MONO-574` `AC-2` — ⚪ *"미측정, 그리고 측정 불가였다"* 가 **충족**인 역방향 사례.

🔴 **셋 다 요약이 아니라 해당 파일의 원문과 대조해 인용했다.**

## AC-3 — 두 문서가 갈라지지 않는다 ✅

- **근거 서술(표 3행 + ⚪ 읽는 법)은 `platform/` 에만 있다.** `CLAUDE.md` 는 카탈로그 한 문장
  + *"§ The Fourth Dimension"* 포인터.
- 🔴 **다만 «개수»는 성질상 네 집에 산다** — 그래서 `platform/` 신설 절 말미에
  **네 집을 이름으로 열거하고 «같이 움직여야 한다»** 고 적었다. 개수를 한 집으로 줄이는
  것은 이 티켓 범위 밖(커맨드 파일이 자기 절차를 서술해야 하므로).
- 잔여 검사: `three dimensions|3-dimension|three-dimension` 전수 grep = **0건**.

## AC-4 — 앵커가 실제로 해소된다 ✅

- 🔵 **새 앵커 링크를 만들지 않았다.** (d) 는 `platform/… § The Fourth Dimension` 을
  **산문 § 참조**로 가리킨다 — 오타 앵커가 조용히 죽는 경로를 애초에 만들지 않는 쪽을 골랐다.
  기존 `#merge-verification-worked-incident` 링크는 그대로 유효하다.
- `scripts/check-claude-reference-integrity.sh` → **rc=0** (24 docs, 161 references, all resolve)
- `scripts/check-required-check-names.sh` → **rc=0** (핀 4개가 ci.yml + 문서 3곳과 일치)
- `scripts/check-index-queue-drift.sh` → **rc=0** · `scripts/check-task-id-collision.sh` → **rc=0**
- 🔴 전부 **독립 statement + 명시 `rc=$?`** 로 읽었다(파이프에 안 물렸다).
