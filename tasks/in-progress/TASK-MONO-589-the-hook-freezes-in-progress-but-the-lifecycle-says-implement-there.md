# Task ID

TASK-MONO-589

# Title

🔴 **훅과 `tasks/INDEX.md` 가 `in-progress/` 에 대해 서로 다른 말을 한다** — 훅은 동결이라 막고,
INDEX 는 *"구현이 시작될 때 거기로 옮긴다"* 고 한다. 머지된 커밋은 INDEX 쪽으로 실행됐다.

# Status

in-progress

# Owner

monorepo

# Task Tags

- rules
- hook
- lifecycle

---

# Goal

`.claude/hooks/hardstop-detect.ps1` 의 HARDSTOP-05 경로 정규식은 `in-progress/` 를 **동결**로
보고 본문 편집을 차단한다. `tasks/INDEX.md` § Review Rules 는 동결 대상을 **`review/` 와
`done/` 둘만** 이라고 적는다. **둘 다 이 저장소의 권위 문서**이고, 지금 서로를 반증한다.

어느 쪽이 옳은지 **정하고**, 훅·stanza 문구·INDEX 를 **한 커밋에서** 한쪽으로 맞춘다.

🔴 **이 티켓은 «훅을 느슨하게 해 달라» 가 아니다.** 판정이 「INDEX 가 틀렸다」로 나면 고칠 것은
INDEX 이고 훅은 그대로다. **어느 쪽이든, 갈라진 채로 두는 것만은 답이 아니다** — 갈라진
규칙은 매번 «이번엔 어느 쪽이 이기나» 를 사람이 다시 판단하게 만들고, 08-27 에 그 비용을
두 번 냈다.

---

# Context — 실측 (2026-08-27 UTC, `origin/main` = `c63af4552`)

## ① 훅: `in-progress/` 는 동결이다

[`.claude/hooks/hardstop-detect.ps1:149`](../../.claude/hooks/hardstop-detect.ps1)

```powershell
if (-not $isLifecycleMove -and $relFromRoot -match '(?:^|/)tasks/(in-progress|review|done)/[^/]+\.md$') {
```

stanza 본문도 같은 말을 한다:

> `in-progress` / `review` / `done` files must not be modified except via lifecycle Status-field moves

`$isLifecycleMove` 예외는 **Status 필드 한 토큰 치환만** 통과시킨다. 본문 편집은 전부 막힌다.

## ② INDEX: 동결은 `review/` 와 `done/` 둘뿐이다

[`tasks/INDEX.md:107`](../INDEX.md) § Review Rules

> - Do not modify a task file after it moves to `review/` or `done/`.

**`in-progress/` 가 없다.** 그리고 이것은 누락으로 보기 어렵다 — 바로 위 세 줄이 전부
`review/` 를 명시적으로 다루고 있고, `done/` 은 뒤늦게 추가된 흔적
(`TASK-MONO-402`)이 훅 픽스처에 남아 있는데 그때도 `in-progress` 는 **손대지 않았다.**

## ③ 그리고 INDEX 는 `in-progress/` 를 **작업 중인 자리**로 정의한다

| 위치 | 문장 |
|---|---|
| `INDEX.md:87-88` § `ready → in-progress` | *"Allowed only when **implementation starts**."* |
| `INDEX.md:115` § PR Separation Rule | *"impl PR — **moves the task file through `in-progress/`** to `review/` and lands the implementation"* |

⇒ 구현이 진행되는 **동안** 태스크 파일은 `in-progress/` 에 있다. 그 기간 내내 본문 편집이
차단된다면, 구현 중에 알게 된 것(측정값·정정·범위 변화)을 **태스크에 적을 자리가 없다.**

## ④ 머지된 커밋이 INDEX 쪽으로 실행됐다

```
51c0cff53  docs(tasks): TASK-MONO-577/575 — the option nobody listed (#3452)
M  tasks/in-progress/TASK-MONO-575-ac0-vercel-free-plan-limits-at-four-projects.md
M  tasks/ready/TASK-MONO-577-adr-where-the-runtime-backend-resolver-lives-proposed.md
```

`in-progress/` 파일의 **본문 수정**이 리뷰를 통과해 `main` 에 있다. 🔴 **이것이 예외적
일탈인지 통상 실무인지는 이 티켓이 판정할 것**이지만, 최소한 «훅이 막는 편집이 실제로
머지되고 있다» 는 사실이다.

## ⑤ 08-27 에 두 번 차단됐다

같은 세션에서 훅이 두 번 발화했고, 두 번 다 **셸 우회를 하지 않고 멈췄다**(올바른 대응).
그 대가로 두 건의 기록 갱신이 미완으로 남았다.

## 🔴 ⑥ 더 깊은 모순 — INDEX 가 자기 자신과도 안 맞는다

| 위치 | 문장 |
|---|---|
| `INDEX.md:87-88` | `ready → in-progress` 는 **구현이 시작될 때** |
| `INDEX.md:150` § Rule | *"Tasks must not be **implemented from** `in-progress/`, `review/`, or `done/`."* |

두 문장을 동시에 참으로 만들 수 있는 상태가 **없다**. 구현이 시작되면 파일은 `in-progress/`
로 가는데, 거기서는 구현하면 안 된다고 한다. `CLAUDE.md` § Core Principles 의
*"Only tasks in the target project's `tasks/ready/` may be implemented"* 도 같은 자리에 선다.

🔵 **가장 그럴듯한 독해**는 `:150` 의 *"implemented from"* 이 **«착수(=구현의 시작)를 그 큐에서
하지 마라»** 를 뜻하고, 이미 착수해서 `in-progress/` 로 옮긴 자기 태스크를 이어서 하는 것은
막지 않는다는 것이다. **그러나 그것은 추론이고, 문서는 그렇게 적혀 있지 않다.**
🔴 **이 티켓이 훅만 고치고 ⑥ 을 남기면, 같은 모순이 다른 얼굴로 다시 온다.**
[[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

---

# 선택지 — 둘이고, 대가가 다르다

| # | 안 | 무엇을 고치나 | 대가 |
|---|---|---|---|
| **A** | **훅을 좁힌다** — 정규식을 `(review\|done)` 로 | 훅 정규식 + stanza 문구 + 픽스처 | `in-progress/` 본문 편집에 대한 기계적 방어가 **사라진다**. 다만 그 방어는 INDEX 가 요구한 적이 없다 |
| **B** | **INDEX 를 넓힌다** — § Review Rules 에 `in-progress/` 추가 | INDEX + ③의 두 문장 + PR Separation Rule | 구현 중 태스크 갱신 경로가 **없어진다**. ④ 의 커밋이 소급 위반이 되고, 앞으로 그 일을 하려면 매번 fix 태스크를 판다 |

🔵 **현재 증거는 A 쪽으로 기운다** — ③(INDEX 가 `in-progress/` 를 작업 중인 자리로 정의) ·
④(머지된 실무) · 그리고 훅 stanza 자신의 `[WHY]` 문구(*"only tasks in `ready/` may be
implemented … the **ready-queue signal** is the public surface external observers read"*)가
말하는 보호 대상이 **`ready/` 큐의 신호**이지 `in-progress/` 파일의 불변성이 아니라는 점.
🔴 **그러나 기울기는 판정이 아니다.** AC-1 이 근거를 적어 정한다.

🔴 **B 를 고른다면 «그럼 구현 중 알게 된 것은 어디에 적는가» 를 같은 문서에서 답해야 한다.**
답 없이 B 를 고르면 규칙은 정합해지고 실무는 규칙 밖에서 계속된다 — 그게 지금 상태다.

---

# Acceptance Criteria

## AC-0 — 착수 시 **다시 잰다** (verify-then-act)

🔴 **오늘 값을 상속하지 마라.** 다음 넷을 그대로 다시 확인한다. 하나라도 달라져 있으면
STOP 하고 티켓을 갱신한다.

- `hardstop-detect.ps1` 의 HARDSTOP-05 정규식이 여전히 `(in-progress|review|done)` 인가.
- `tasks/INDEX.md` § Review Rules 가 여전히 `review/` · `done/` **둘만** 적는가.
- `51c0cff53` 이 여전히 `origin/main` 조상이고 `in-progress/` 파일을 수정하는가.
- **프로젝트 레벨 INDEX 도 본다** — `projects/*/tasks/INDEX.md` 가 같은 문장을 갖는지.
  🔴 훅 정규식은 `(?:^|/)tasks/` 라 **프로젝트 태스크에도 똑같이 문다.** 루트만 고치면
  같은 결함이 8개 프로젝트에 남는다. [[feedback_recount_population_dont_inherit_scope]]

## AC-1 — **정하고, 왜 그렇게 정했는지 적는다**

A 또는 B 중 하나를 고른다. 티켓/커밋 본문에 **근거를 열거**한다 — 조건 없는 결정은 나중에
왜 골랐는지 알 수 없다. 🔵 이 결정은 규칙 문서 정합 수선이지 아키텍처 결정이 아니므로
**ADR 게이트 대상이 아니다**(`platform/architecture-decision-rule.md` 를 열어 확인하고,
해당하면 STOP 한 뒤 ADR 로 승격한다).

## AC-2 — 고른 쪽을 **한 커밋에서** 전부 맞춘다

🔴 **정규식만 고치고 stanza 문구를 남기지 마라.** `TASK-MONO-402` 가 정확히 그 반대
방향에서 같은 갈라짐을 고쳤다 — 정규식은 `(in-progress|review)` 인데 stanza 문구는
`done` 을 동결이라 적고 있었고, **문구가 옳고 정규식이 틀렸다**는 판정이 나오기까지
`done/` 편집이 조용히 통과했다. 같은 파일 안에서 두 번 갈라지게 두지 마라.

한 커밋에 들어가야 하는 것 (A 를 고른 경우):

- `hardstop-detect.ps1` 정규식
- 같은 함수의 `[VIOLATION]` / `[WHY]` / `[REMEDIATION]` 문구
- `tasks/INDEX.md` ⑥ 의 두 문장 (`:87-88` ↔ `:150`)
- 필요하면 `CLAUDE.md` § Core Principles / § Task Rules
- 각 `projects/*/tasks/INDEX.md` 중 같은 문장을 가진 것 (AC-0 에서 센 모집단 전부)

## AC-3 — 픽스처가 **양쪽을 다 잰다**

[`.claude/hooks/__tests__/hardstop-05-task-not-ready.ps1`](../../.claude/hooks/__tests__/hardstop-05-task-not-ready.ps1)
에 케이스를 추가한다. A 를 고른 경우:

| 케이스 | 기대 | 왜 |
|---|---|---|
| `tasks/in-progress/X.md` 본문 편집 | **allow** | 이 티켓이 바꾸는 것 |
| `projects/<p>/tasks/in-progress/X.md` 본문 편집 | **allow** | 훅 정규식이 프로젝트 경로도 문다 |
| `tasks/review/X.md` 본문 편집 | **block** | 🔵 **음성 대조군** — 이게 같이 통과하면 정규식을 너무 넓게 풀었다 |
| `tasks/done/X.md` 본문 편집 | **block** | 🔵 음성 대조군 (`MONO-402` 가 지킨 것) |
| `done/` Status 이동 | **allow** | 기존 negative-3, 마감 chore 가 여기 걸리면 안 된다 |

🔴 **음성 대조군 두 줄을 빼지 마라.** 그것 없이는 «전부 allow» 라는 잘못된 고침이 초록으로
보인다. [[project_guard_design_requirements]]

## AC-4 — **실제로 막혔던 편집으로 bite 를 증명한다**

픽스처가 초록인 것과 훅이 실제 호출 경로에서 다르게 동작하는 것은 **다른 질문**이다.
⑤ 에서 막혔던 것과 **같은 모양의 편집**(=`in-progress/` 태스크 본문에 한 절 추가)을
고친 훅에 태워 통과하는지 찍는다. 🔴 **주입과 판정을 먼저 단언하라** — 대상 파일이 실제로
`in-progress/` 경로이고 편집이 `$isLifecycleMove` 예외에 **안 걸리는** 본문 편집인지
확인한 다음에 결과를 읽는다. [[feedback_assert_the_injection_before_reading_the_bite]]

## AC-5 — 훅 스위트가 **어디서 도는지** 확인하고 적는다

`.claude/hooks/__tests__/run-all.ps1` 이 CI 에서 실제로 실행되는 잡을 찾아 티켓에 적는다.
🔴 **러너가 없으면 이 픽스처는 썩는다** — `TASK-MONO-405`(*"hook fixtures run nowhere"*)가
이 저장소에서 이미 일어난 일이다. 안 돌고 있으면 그 사실을 **판정으로 기록**하고
(고치는 것은 이 티켓 범위 밖이면 후속 티켓) 넘어간다.
[[feedback_two_correct_exclusions_compose_into_a_hole]]

---

# 🔴 이 티켓이 **안** 푸는 것

`tasks/done/TASK-MONO-575-*.md` 의 헤더 blockquote 가 같은 파일 본문과 모순인 채로 남아
있고, HARDSTOP-05 가 `done/` 편집을 막아 08-27 에 고치지 못했다.

🔴 **A 를 골라도 이건 안 풀린다** — `done/` 은 **양쪽 안 모두에서 동결로 남는다**.
«이미 `done/` 에 들어간 기록에 오류가 있을 때 무엇을 하는가» 는 규칙이 답하지 않는
**별개의 질문**이고, 별도 티켓이다. 🔵 여기 적는 이유는 하나 — AC-1 의 결정이 그 문제까지
푼 것처럼 읽히면 안 되기 때문이다. [[feedback_a_partial_deletion_reads_as_a_total_one]]

---

# Related Specs

- `.claude/hooks/hardstop-detect.ps1` — HARDSTOP-05 경로 정규식 + stanza
- `.claude/hooks/__tests__/hardstop-05-task-not-ready.ps1` — 픽스처
- `tasks/INDEX.md` § Move Rules · § Review Rules · § PR Separation Rule · § Rule
- `projects/*/tasks/INDEX.md` — 같은 문장의 프로젝트 판 (AC-0 에서 센다)
- `platform/hardstop-rules.md` § HARDSTOP-05 — 정경 stanza 본문
- `CLAUDE.md` § Core Principles · § Task Rules
- `tasks/done/TASK-MONO-402-hook-reachability-powershell-door.md` — 반대 방향의 선례

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| `platform/hardstop-rules.md` 의 정경 stanza 와 훅 문구가 갈린다 | 🔴 **정경이 먼저다.** `hardstop-body-canonical-sync.ps1` 픽스처가 이 동기화를 검사하므로 두 파일을 같은 커밋에서 고친다 |
| 프로젝트 INDEX 중 일부만 같은 문장을 갖는다 | 🔵 **가진 것만 고친다.** 없는 파일에 문장을 새로 만들지 마라 — 그건 다른 티켓의 결정이다 |
| AC-0 에서 훅이 이미 좁혀져 있다 | 🔵 **닫지 마라** — INDEX ⑥ 의 자기 모순(`:87-88` ↔ `:150`)은 그대로 남아 있다. AC-2 의 INDEX 부분만 남기고 진행 |
| B 를 골랐는데 ④ 의 커밋이 소급 위반이 된다 | 🔵 **소급 적용하지 않는다.** 규칙 변경은 앞으로에만 적용하고, 그 사실을 INDEX 에 한 줄로 적는다 |
| 훅을 고쳤는데 로컬에서 여전히 막힌다 | 🔴 훅은 **세션 시작 시 로드**될 수 있다. 새 세션에서 재확인하고, 그게 원인이면 티켓에 적는다 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방지 |
|---|---|---|
| 정규식만 고치고 stanza 문구를 남긴다 | 훅은 통과시키는데 메시지는 여전히 `in-progress` 를 동결이라 말한다 → 다음 사람이 어느 쪽을 믿을지 모른다 | AC-2 의 한-커밋 목록 |
| 루트 INDEX 만 고친다 | 8개 프로젝트에 같은 모순이 남고, 프로젝트 태스크에서 다시 밟는다 | AC-0 의 모집단 세기 |
| 음성 대조군 없이 픽스처를 통과시킨다 | 정규식을 통째로 지워도 초록 | AC-3 의 `review/`·`done/` block 두 줄 |
| 픽스처만 초록이고 실제 호출 경로는 다르다 | 다음 세션에서 또 막힌다 | AC-4 (실제 편집 모양으로 bite) |
| 훅 스위트가 CI 에서 안 돈다 | 이 커밋 이후 정규식이 되돌아가도 아무도 모른다 | AC-5 (러너 확인, `MONO-405` 선례) |
| ⑥ 을 안 고치고 닫는다 | *"ready 에서만 구현"* ↔ *"구현 시작 시 in-progress 로"* 가 그대로 남아 다음 티켓에서 같은 질문이 반복된다 | AC-2 의 INDEX 항목 |
