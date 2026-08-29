# Task ID

TASK-MONO-601

# Title

🔴 **required 4종 중 3종이 코드 전용 PR 에서 `SKIPPED` 로 통과한다.** 규칙은 「넷이 지킨다」고
읽히는데, 실제로 도는 것은 **하나**인 PR 이 있다.

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- rules
- measurement

---

# Goal

`CLAUDE.md` 의 머지 검증 규칙(§ Task Rules, `TASK-MONO-598`)은 `main` 이 **정확히 네 개**의
컨텍스트를 required 로 요구한다고 적고, 차원 (c)를 *"머지 전 실패한 required 체크 0건"* 으로
정의한다. **그 문장은 참이다.** 이 티켓이 다루는 것은 그 문장이 **읽히는 방식**이다 —
*"넷이 실제로 돌았다"* 로 읽기 쉬운데, 코드만 건드리는 PR 에서는 **셋이 안 돈다.**

---

# Context — 실측 (2026-08-29 UTC)

`TASK-MONO-590` AC-0 의 되돌림 PR [#3523](https://github.com/kanggle/monorepo-lab/pull/3523)
은 `projects/fan-platform/web/fan-platform-web/vercel.json` **한 파일만** 바꿨다.

| required 컨텍스트 | #3523 (코드 1파일) | #3522 (같은 시리즈, 태스크 파일 포함) |
|---|---|---|
| `changes` | **SUCCESS** | SUCCESS |
| `INDEX queue drift (…)` | **SKIPPED** | SUCCESS |
| `Task ID collision (…)` | **SKIPPED** | SUCCESS |
| `Walkthrough limitation ledger drift (…)` | **SKIPPED** | SUCCESS |

그리고 **머지는 통과했다** — GitHub 은 `SKIPPED` 를 실패로 보지 않는다.

🔵 **머지는 정당했고 `main` 은 멀쩡하다.** 세 가드는 태스크 큐/문서 드리프트를 보는 것이고,
이 PR 은 그 축을 건드리지 않았으므로 **안 도는 것이 옳다.** 결함은 가드가 아니라
**「required 넷」이 주는 인상**이다.

🔴 왜 지금 적는가: `TASK-MONO-598` 이 그 집합을 고른 근거는 *"24개 PR 중 24개에서 SUCCESS 로
측정됐으므로 영구 pending 으로 교착될 수 없다"* 였다. 그 표본이 **문서 PR 위주**였다면
「항상 SUCCESS」와 「자주 SKIPPED」가 같은 관측으로 보였을 수 있다.
[[feedback_a_census_measures_where_you_looked_not_what_exists]]

---

# Scope

**In:**

- `CLAUDE.md` § Task Rules 의 차원 (c) 문구 — required 집합이 **PR 마다 몇 개 도는지**를
  명시
- 같은 사실을 담은 나머지 두 집(`platform/git-workflow-policy.md`,
  `scripts/required-check-names.txt` 주변) — 🔴 한 사실이 두 절에 있으면 한쪽만 고쳐진다

**Out:**

- required 집합을 **바꾸는 것**(추가/제거) → 그것은 branch protection 재등록이고
  `TASK-MONO-599` 가 이름 하나 틀리면 `main` 이 영구 BLOCKED 가 된다고 못박은 축이다.
  이 티켓은 **문구와 인식**만 다룬다.
- 세 가드의 `if:` 게이팅을 넓히는 것 — `TASK-MONO-389`/`451` 이 각 가드의 도달성을
  **결함의 도착 경로**에 맞춰 일부러 정한 것이라, 넓히면 그 설계를 뒤집는다.

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 잰다**

🔴 오늘 값을 상속하지 마라. 최근 PR 을 **두 종류**(문서 전용 / 코드 전용)로 각각 골라
required 4종의 `conclusion` 을 찍는다. 🔵 **두 종류를 다 봐야** 「항상 SUCCESS」와
「종류에 따라 SKIPPED」가 갈린다 — 한 종류만 보면 598 의 표본과 같은 함정에 빠진다.

## AC-1 — 「몇 개가 실제로 도는가」를 문구에 넣는다

차원 (c)가 *"실패한 required 0건"* 인 것은 그대로 두되, **그것이 무엇을 뜻하지 않는지**를
적는다: `SKIPPED` 는 실패가 아니고 머지를 막지 않으므로, 코드 전용 PR 에서 (c)는
**`changes` 하나만 실제로 돈 상태로도 참**이다.

🔴 이미 그 옆에 있는 문장(*"dimension (c) 통과가 스위트 초록을 뜻하지 않는다"*)과 **같은
계열**이다. 그 문장은 «다른 잡들이 빨강일 수 있다» 를 말하고, 이 티켓은 «required 자신도
안 돌 수 있다» 를 말한다. 둘을 한 자리에 둔다.

## AC-2 — 🔴 **집이 셋이다. 셋 다 고친다**

`TASK-MONO-599` 가 이미 같은 사실이 세 곳에 산다고 적었다. 한 곳만 고치면 나머지 둘이
낡은 채로 남고, 다음 사람은 **먼저 눈에 띈 것**을 읽는다.
[[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

## AC-3 — 가드를 붙일 수 있는가 **묻고, 답을 적는다**

🔵 문구 드리프트를 잡는 가드가 이미 있다 — `scripts/check-required-check-names.sh`.
그것이 **이름**을 지킨다면, 이 티켓의 사실(«몇 개가 도는가»)도 지킬 수 있는지 본다.
🔴 **못 한다면 못 한다고 적어라.** 「가드를 붙였다」는 인상만 남기는 것이 이 저장소가
반복해서 당한 실패다. [[feedback_a_figure_nothing_can_fail_on_will_drift]]

## AC-4 — 검증

- 고친 세 집을 **실제로 열어** 문구가 일치하는지 대조(diff 로 확인, grep 개수 아님).
- `scripts/check-required-check-names.sh` rc=0.
- 🔴 이 PR 자체가 어느 종류인지 적는다(문서 전용이면 세 가드가 돌고, 그것이 AC-0 의
  대조군을 한 번 더 준다).

---

# Related Specs

- `CLAUDE.md` § Task Rules — 차원 (a)(b)(c) 와 required 4종
- `platform/git-workflow-policy.md` § Merge-Verification Worked Incident
- `scripts/required-check-names.txt` · `scripts/check-required-check-names.sh`
- `tasks/done/TASK-MONO-598-…` — required 집합을 비어 있음에서 넷으로 바꾼 티켓
- `tasks/done/TASK-MONO-599-…` — 그 넷의 **이름**이 세 집에서 어긋난 사건
- `tasks/in-progress/TASK-MONO-590-…` — 이 사실이 관측된 자리

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| 착수 시 세 가드가 코드 PR 에서도 돌고 있다 | 🔵 게이팅이 그 사이 바뀐 것이다. **그 사실을 적고** 티켓을 닫는다 — 다만 «왜 바뀌었나» 를 한 줄 남긴다 |
| 「그럼 required 를 늘리자」 | 🔴 Scope Out. 이름 하나 틀리면 `main` 이 영구 BLOCKED 다(`TASK-MONO-599`) |
| `SKIPPED` 가 아니라 아예 컨텍스트가 없는 PR | 그것도 같은 결론이다 — (c)는 여전히 참이고, 실제로 돈 것은 더 적다 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방어 |
|---|---|---|
| 한 집만 고친다 | 나머지 둘이 낡은 채 남고 다음 사람이 그걸 읽는다 | AC-2 |
| 「가드를 붙였다」로 뭉갠다 | 문구는 다시 드리프트한다 | AC-3 — 못 하면 **못 한다고** 적는다 |
| 문서 전용 PR 로만 확인한다 | 세 가드가 돌아서 결함이 안 보인다 | AC-0 — **두 종류** 다 본다 |
| required 집합을 손댄다 | `main` 영구 BLOCKED 위험 | Scope Out |
