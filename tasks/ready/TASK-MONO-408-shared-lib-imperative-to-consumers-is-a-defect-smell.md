# Task ID

TASK-MONO-408

# Title

공유 라이브러리가 소비자에게 지우는 "MUST" 는 문서가 아니라 결함 냄새다 — `MONO-406` 이 값을 치르고 배운 판별식에 정경 홈이 없다

# Status

ready

# Owner

monorepo

# Task Tags

- code
- test

---

# Goal

`TASK-MONO-406` 은 `libs/java-messaging` 의 `OutboxAutoConfiguration` javadoc 이 *"수많은 서비스가 이름으로 exclude 하기 **때문에** 이 클래스를 남긴다"* 라고 적고 있는 것을 발견했다. **우회가 문서화되자 그 문서가 덫의 존치 사유가 됐다** — 같은 결함으로 서비스가 **네 번**(BE-333 · BE-432 · BE-461 · BE-489) 부팅에 실패하는 동안 아무도 *"라이브러리가 왜 덫을 놓는가"* 를 묻지 않았다.

그 사건이 남긴 **판별식**은 저장소 어디에도 없다:

> **공유 라이브러리의 javadoc/README 가 소비자에게 *행동 의무*(`MUST`/`IMPORTANT`/"be sure to"/"각 서비스는 …해야 한다")를 지우고 있다면, 그건 문서가 아니라 라이브러리가 자기 결함을 소비자에게 떠넘기고 있다는 신호다.** 그 문장을 지우는 방법을 묻지 말고, **그 문장이 필요 없게 만드는 방법**을 물어라.

이 규칙은 `libs/` 를 만지는 **모든** 세션·개발자에게 적용된다. 지금은 에이전트 메모리에만 있다 ⇒ 정경(`platform/shared-library-policy.md`)으로 승격한다.

---

# Scope

## In Scope

1. **`platform/shared-library-policy.md`** — 위 판별식을 리뷰 규칙으로 additive 추가. 기존 **Forbidden 열 옆**에 두되, Forbidden 이 *산출물*(금지된 클래스 종류)을 이름 붙이는 것과 달리 이것은 **냄새(smell)** 를 이름 붙인다는 점을 명시. `MONO-406` 을 낳은 인시던트로 인용.
2. **현행 baseline 삼각측량 (AC-2)** — `libs/**/src/main/**/*.java` 의 `MUST`/`IMPORTANT`/`be sure to` 8건(2026-07-14 실측)을 **전수 triage** 해 각각 (a) 인터페이스 구현자를 향한 정당한 계약 서술인지 (b) 라이브러리가 소비자에게 떠넘기는 의무인지 판정하고, 결과를 이 task 와 PR 본문에 표로 남긴다.
3. 메모리 `feedback_workaround_becomes_the_contract` 에 "정경 승격됨 (MONO-408)" 포인터를 단다(머지 후).

## Out of Scope

- **기계 가드(grep 잡) 신설 — 기본은 "만들지 않는다".** 실측 8건 중 다수가 (a) 부류로 보인다 ⇒ 순진한 `grep MUST` 는 **태어난 날 RED** 이고, 그런 가드는 꺼지고 **꺼진 가드는 없는 가드보다 나쁘다**(`TASK-MONO-360`). AC-2 의 triage 가 **오탐 0 인 술어**를 실제로 산출한 경우에만 별건 티켓으로 제안한다(이 task 에서 구현하지 않는다).
- 8건 중 (b) 로 판정된 것이 있어도 **이 task 에서 고치지 않는다** — 라이브러리 API 변경은 자기 티켓과 통합 레인 증거를 가져야 한다(`MONO-406` 이 그랬듯). 발견은 기록하고 티켓을 제안만 한다.
- `libs/` 밖의 문서.

---

# Acceptance Criteria

- [ ] **AC-1** `platform/shared-library-policy.md` 에 판별식이 additive 로 추가됨. **project-agnostic**(서비스명·API 경로·도메인 엔티티 0개 — HARDSTOP-03). catalog 형태(한 문단 + 인시던트 포인터), worked-detail 은 메모리에 남긴다.
- [ ] **AC-2 (baseline, 숫자를 물려받지 말 것)** 착수 시점에 `libs/**/src/main/` 를 **다시 세고**(2026-07-14 의 8건은 **인계된 가설**이다) 전건을 (a)/(b) 로 triage 한 표를 PR 본문에 싣는다. **(b) 가 0건이면 0건이라고 적는다 — 0건도 결과다.**
- [ ] **AC-3 (가드 판정을 명시적으로)** AC-2 결과로부터 "오탐 0 인 기계 술어가 가능한가" 에 **명시적으로 답한다**. 가능=별건 티켓 제안 / 불가능=**왜 불가능한지**를 정책 문서에 한 줄로 적는다(다음 사람이 같은 유혹에 빠져 첫날-RED 가드를 만들지 않도록).
- [ ] **AC-4** 기존 규칙 무변경(추가만). `platform/shared-library-policy.md § Change Rule` 준수.
- [ ] **AC-5** CI GREEN (doc-only ⇒ 대부분 skip 예상 — **SKIPPED 를 통과로 읽지 말 것**: 이 diff 가 코드를 안 건드리므로 skip 이 정상이라는 것을 확인만 한다).

---

# Related Specs

- `platform/shared-library-policy.md` (승격 대상 — 정경)
- `docs/adr/ADR-MONO-004-*.md` (Forbidden 열의 출처)
- `tasks/done/TASK-MONO-406-messaging-lib-still-ships-the-v1-dedupe-entity-adr-004-forbids.md` (이 규칙을 낳은 인시던트)
- 선례 승격: `tasks/done/TASK-MONO-309-*` · `tasks/done/TASK-MONO-315-promote-memory-rules-to-canonical-docs.md` (메모리 → 정경 승격의 형태)
- 메모리 `feedback_workaround_becomes_the_contract` (worked detail)

# Related Skills

N/A — 문서 편집 + grep triage.

---

# Related Contracts

None (doc-only).

---

# Target Service

N/A — shared `platform/` 문서.

---

# Implementation Notes

- **탐지식을 아는 답에 먼저 돌려라**(`MONO-404` AC-5): triage grep 을 `OutboxAutoConfiguration`(이미 삭제됨 — `git show` 로 부모 커밋의 blob) 에 돌려 **실제로 잡히는지** 먼저 확인한 뒤 현행 트리의 0/8 을 읽어라. **빈 출력은 부재가 아니다.**
- `grep -c` 는 Javadoc 이 규칙을 *설명하는* 줄까지 센다(`MONO-388`·`MONO-392` 오탐). triage 는 **줄을 눈으로 읽고** 판정한다 — 세는 것으로 끝내지 말 것.

---

# Edge Cases

- **정당한 (a) 부류**: 인터페이스/추상클래스가 **구현자**에게 계약을 서술하는 `MUST`(예: "구현체는 멱등이어야 한다"). 이건 결함이 아니라 계약이다 — 판별식이 이걸 (b) 로 몰면 규칙 자체가 오탐이 된다. **문서에 두 부류의 예시를 나란히 실어라.**
- **auto-config 은 opt-in 이 아니다** — 소비자가 아무것도 안 해도 도는 클래스가 의무를 요구하면 그건 항상 (b) 다. 이 비대칭이 판별의 핵심축(`MONO-406` AC-5 가드가 정확히 이 선을 그었다).

---

# Failure Scenarios

- **첫날-RED 가드를 만들어 버린다** → 꺼지고, 꺼진 가드는 초록으로 보고된다(`MONO-360`). 완화 = Out of Scope + AC-3.
- **규칙만 쓰고 baseline 을 안 잰다** → 다음 사람이 "위반 0" 이라고 믿는다. 완화 = AC-2(전수 triage, 0건도 명시).
- **`platform/` 에 프로젝트 냄새가 들어간다**(서비스명 인용) → HARDSTOP-03. 완화 = AC-1 명시.

---

# Test Requirements

- doc-only. CI GREEN 확인(코드 잡 skip 정상).

---

# Definition of Done

- [ ] `platform/shared-library-policy.md` 에 판별식 additive 랜딩.
- [ ] AC-2 triage 표 PR 본문 게재, AC-3 가드 판정 기록.
- [ ] 머지(3-dim verify) + 메모리 포인터.
- [ ] `tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-14 `/audit-memory` 감사에서 표면화. 메모리 `feedback_workaround_becomes_the_contract` 가 담고 있는 판별식이 **저장소 전체에 적용되는데 메모리에만 존재**함을 확인(`platform/`·`rules/` grep 0건). 사용자 승인으로 티켓팅. **`MONO-407` 은 동시 세션이 선점 → 408.**

분석=Opus 4.8 / 구현 권장=**Sonnet**(문서 + grep triage. 판단은 티켓에서 끝났고, 유일한 판단 지점은 AC-3 이며 그 기준도 여기 적혀 있다).
