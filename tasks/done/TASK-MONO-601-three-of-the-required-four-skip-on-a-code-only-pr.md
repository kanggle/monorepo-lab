# Task ID

TASK-MONO-601

# Title

🔴 **required 4종 중 3종이 코드 전용 PR 에서 `SKIPPED` 로 통과한다.** 규칙은 「넷이 지킨다」고
읽히는데, 실제로 도는 것은 **하나**인 PR 이 있다.

# Status

done

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

---

# ✅ 구현 결과 (2026-08-29 UTC)

## AC-0 — 다시 쟀다. **두 종류를 다 봤고, 코드 전용 쪽이 n=1 이라 표본을 넓혔다**

첫 스캔(머지 PR 20건)에서 코드 전용은 **1건**이었다. 🔴 그대로 결론을 냈다면 598 과 **같은
함정**이다 — 한쪽 종류의 표본이 하나면 「종류에 따라 갈린다」를 주장할 수 없다. 그래서 코드
전용만 따로 **머지 PR 70건**까지 훑었다. [[feedback_a_census_measures_where_you_looked_not_what_exists]]

| 종류 | 건수 | `changes` | `INDEX queue drift` | `Task ID collision` | `Walkthrough … drift` |
|---|---:|---|---|---|---|
| 태스크 파일 건드림 | **68** | SUCCESS | SUCCESS | SUCCESS | SUCCESS |
| **코드 전용** | **2** | SUCCESS | **SKIPPED** | **SKIPPED** | **SKIPPED** |

코드 전용 2건 = [#3523](https://github.com/kanggle/monorepo-lab/pull/3523)(1파일) ·
[#3479](https://github.com/kanggle/monorepo-lab/pull/3479)(9파일). **둘 다 예외 없이 셋이 SKIPPED.**

🔴🔴 **이 모집단 구성이 598 의 관측을 설명한다** — 코드 전용은 70건 중 2건, 약 **3%** 다.
「항상 SUCCESS」와 「자주 SKIPPED」가 같은 관측으로 보이는 표본이었다. 598 의 결론(이 넷은
영구 pending 으로 교착될 수 없다)은 **여전히 참**이고, 따라 나오지 않는 것은 「넷이 실제로
돈 신호」라는 부분이다.

🔵 **기전도 확인했다** — 세 잡이 각각 `if: needs.changes.outputs.<필터>` 를 달고 있고, 그
필터 셋의 경로 목록은 전부 `tasks/**` 계열이다. 추론이 아니라 `ci.yml` 에서 읽었다.

## AC-1 — 「몇 개가 실제로 도는가」를 넣었다

차원 (c)의 정의(*"실패한 required 0건"*)는 **그대로 두고**, 그 옆에 «무엇을 뜻하지 않는지»를
넣었다: `SKIPPED` 는 실패가 아니고 머지를 막지 않으므로, 코드 전용 PR 에서 (c)는
**`changes` 하나만 실제로 돈 상태로도 참**이다. 기존의 *"(c) 통과가 스위트 초록을 뜻하지
않는다"* 바로 옆자리에 뒀다 — 그 문장은 «다른 잡이 빨강일 수 있다», 이 문장은 «required
자신이 안 돌 수 있다» 로 **같은 계열의 다른 얼굴**이다.

🔵 **건너뛰는 것 자체는 옳다**고 함께 적었다. 그 가드들은 태스크 큐/문서 드리프트를 보고,
코드 전용 PR 은 그 도착 경로가 아니다(`TASK-MONO-389`/`451`). 결함은 가드가 아니라 인상이다.

## AC-2 — 🔴 **집이 셋인 것은 맞았지만, 티켓이 지목한 셋 중 하나가 틀렸다**

이 티켓의 Scope 는 세 번째 집을 `scripts/required-check-names.txt` **주변**으로 적었다.
상속하지 않고 직접 세어 보니 그 파일이 담은 것은 **이름**이지 차원 (c) 사실이 아니고,
실제 세 번째 집은 **`.claude/commands/review-task.md`** 다 — 그리고 그것은
`check-required-check-names.sh` 의 `DOCS` 배열이 이미 지키고 있는 바로 그 셋이다.

| 집 | 고친 자리 |
|---|---|
| `CLAUDE.md` § Task Rules | 「Before 598 the set was empty…」 다음 |
| `platform/git-workflow-policy.md` § Merge-Verification Worked Incident | 598 인용 블록 뒤에 새 인용 블록 |
| `.claude/commands/review-task.md` | 차원 (c) 3번 항목의 「24/24」 문단 다음 |

🔵 세 곳에 **같은 사실을 같은 숫자로** 적었다(70건 중 2건, 나머지 68건). 한 곳만 고치면
다음 사람은 먼저 눈에 띈 것을 읽는다. [[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

## AC-3 — 🟢 **가드를 붙일 수 있었고, 붙였다.** 그리고 못 무는 축을 적는다

물음: `scripts/check-required-check-names.sh` 가 **이름**을 지키듯 이 사실도 지킬 수 있는가.
답은 **가능**이다 — 「몇 개가 조건부 게이팅돼 있는가」는 런타임이 아니라 `ci.yml` 의
**저장소 상태**이기 때문이다. 칸 **(4)** 를 신설했다:

- 핀의 각 이름(`changes` 제외)에 대해 `ci.yml` 의 `name:` 줄을 찾고 그 **다음 4줄 안에**
  `if: needs.changes.outputs.` 가 있는지 본다. 조건부인 개수를 세어 `GATED_EXPECTED=3` 과 비교.
- 문서 세 곳에 `TASK-MONO-601` 앵커가 살아 있는지 본다(산문이라 등호를 못 건다 —
  이 파일이 ③에 대해 이미 쓰는 방식과 같다).
- 통과 메시지에도 숫자를 실었다: *"그중 3개는 조건부 게이팅 — 태스크 파일을 안 건드리는
  PR 에서는 SKIPPED 된다."*

**자기검사 두 칸을 추가**했고 둘 다 주입을 먼저 단언한다:

| 칸 | 주입 | 기대 |
|---|---|---|
| **(h)** | `index-queue` 잡의 `if:` 줄 삭제 | RED |
| **(i)** | `git-workflow-policy.md` 의 `TASK-MONO-601` 앵커 소실 | RED |

🔵 **(h) 가 «옳은 이유로» 무는지 메시지까지 확인했다** — rc=1 만 본 것이 아니라
*"조건부 게이팅된 required 가 2개입니다 — 기대 3개"* + 남은 둘의 이름을 출력했다.

🔴 **(4) 가 못 보는 것 — 적어 둔다:**

1. **개별 PR 의 실제 conclusion.** 그건 런타임이지 저장소 상태가 아니다. 「이 PR 에서 넷 중
   몇 개가 돌았나」는 여전히 사람이 rollup 을 읽어야 한다.
2. **branch protection 쪽 변경.** 이 파일 헤더의 §못 무는 것 그대로다 — 핀은 사본이고,
   사본은 원본이 바뀐 것을 모른다.
3. 필터의 **경로 목록**이 바뀌어 `tasks/**` 아닌 것이 들어오는 경우. (4)는 «조건부인가» 만
   보고 «무엇에 조건부인가» 는 안 본다. 🔵 그래도 개수는 안 변하므로 이 축은 열려 있다 —
   더 좁히려면 필터 경로까지 읽어야 하고, 그건 이 티켓의 범위 밖으로 뒀다.

## 🔵 곁가지 — 스크립트 헤더의 절 번호가 코드와 어긋나 있었다

헤더가 짧은형 검사를 *"아래 (4)"* 라고 불렀는데 코드의 그 절은 **(3)** 이었다. 새 칸을 (4)로
넣기 전에 그 어긋남을 고쳤다(안 고쳤으면 (4)가 둘이 된다).

## AC-4 — 검증

| 항목 | 결과 |
|---|---|
| 세 집을 **열어서** 대조 | ✅ diff 로 확인(grep 개수 아님) — 세 곳 모두 70/2/68 이라는 **같은 숫자** |
| `scripts/check-required-check-names.sh` | ✅ **rc=0** · *"핀 4개가 ci.yml 과 문서 3곳에서 일치. 그중 3개는 조건부 게이팅"* |
| `--self-test` | ✅ **9/9** (기존 7칸 + 신설 (h)(i)) |
| `bash -n` | ✅ |
| **이 PR 의 종류** | 🔵 **태스크 파일을 건드린다**(이 파일의 큐 이동 + `tasks/INDEX.md`) ⇒ 세 가드가 **돈다**. AC-0 의 「태스크 계열 = 넷 다 SUCCESS」에 대한 **69번째 표본**이자 대조군이 한 번 더 생긴다 |

🔴 **required 집합 자체는 손대지 않았다** (Scope Out). branch protection 은 이 PR 이 건드리지
않으므로 `TASK-MONO-599` 가 경고한 영구 BLOCKED 위험은 없다.

## 🔵 착수 중 밟은 것 — 수명주기 순서

`ready → review` 로 **바로** 옮기고 결과를 쓰려다 훅에 막혔다(HARDSTOP-05). 구현 중의 작업
문서는 `in-progress/` 이고 `review/` 는 **동결**이다 — `ready → in-progress`(작업) →
`review`(완료) 가 맞는 순서다. 되돌려서 다시 했다.
