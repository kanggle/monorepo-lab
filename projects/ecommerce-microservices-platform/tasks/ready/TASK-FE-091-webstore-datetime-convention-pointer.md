# Task ID

TASK-FE-091

# Title

web-store 는 콘솔의 날짜·시간 규약을 **복제해 쓰면서** 그 규약이 어디 적혀 있는지 모른다 — 포인터 한 줄

# Status

ready

# Owner

frontend

# Task Tags

- code
- onboarding

---

# Goal

`TASK-PC-FE-241`(2026-07-14 머지)이 콘솔의 날짜·시간 규약을 정경화했다:
**`projects/platform-console/docs/conventions/frontend-ui.md`**.

그 규약은 **web-store 에도 적용된다** — `web-store` 는 `console-web` 의 헬퍼를 import 할 수 없어(다른 앱·다른 프로젝트) **동일 규약의 헬퍼 복제본**(`web-store/src/shared/lib/datetime.ts`)을 갖고 있다. 즉 **구현은 복제됐는데 규칙은 복제되지 않았다.**

PC-FE-241 은 AC-4 에서 **규칙 본문은 한 곳에만 산다**고 결정했다(복사본은 갈라진다). 그러면 web-store 쪽에는 **포인터**가 필요하다. 그 포인터 한 줄은 **cross-project 편집**이라 PC-FE-241 의 Target App(`console-web`) 범위 밖이었고, 그래서 이 task 로 분리됐다.

**지금 상태 = web-store 개발자에게 그 규칙은 존재하지 않는다.** 헬퍼는 있지만 *왜* 그렇게 생겼는지(자정 `24:00:00` quirk, pinned KST 와 SSR 하이드레이션)는 어디에도 없어서, 다음 사람이 "간소화" 할 수 있다.

---

# Scope

## In Scope

- **web-store 쪽 문서에 포인터 한 줄** 추가 — 규약의 정경 홈이 `projects/platform-console/docs/conventions/frontend-ui.md` § datetime 임을 가리키고, **web-store 는 그 규약의 헬퍼 복제본을 유지한다**는 사실(그리고 왜 import 가 불가능한지)을 적는다. 위치는 착수 시 결정: web-store 앱의 README 또는 `projects/ecommerce-microservices-platform/docs/` — **이미 프런트 개발자가 읽는 파일**을 고른다(새 파일을 만드는 것보다 낫다).
- **AC-1 재검증**: web-store 의 `shared/lib/datetime.ts` 가 **오늘도 콘솔 규약과 같은지** 확인(`hourCycle:'h23'` · `timeZone:'Asia/Seoul'` · placeholder 폴백). **갈라져 있으면 그 사실을 기록하고 티켓을 제안한다 — 이 task 에서 코드를 고치지 않는다**(복제본 정합화는 자기 티켓과 검증을 가져야 한다).

## Out of Scope

- **규칙 본문을 web-store 쪽에 복사하는 것.** 그게 정확히 PC-FE-241 AC-4 가 금지한 것이다 — 복사본은 갈라지고, 갈라진 규칙은 규칙이 아니다.
- 두 헬퍼를 **공유 패키지로 통합**하는 것 — 매력적이지만 별개 결정(모노레포 FE 패키지 경계). 갭이 관측되면 그때 티켓.
- 코드 변경 일체(포인터는 문서다).

---

# Acceptance Criteria

- [ ] **AC-1** web-store 헬퍼가 콘솔 규약과 실제로 일치하는지 코드로 확인하고 결과를 기록. 불일치가 있으면 **기록만 하고 고치지 않는다**(별건 티켓 제안).
- [ ] **AC-2** web-store 프런트 개발자가 실제로 읽는 문서에 포인터가 있고, 그 포인터가 **정경 홈의 정확한 경로**를 가리킨다(링크가 깨지지 않는지 확인).
- [ ] **AC-3** 규칙 본문은 여전히 **한 곳에만** 있다 — 이 PR 이 규칙을 재서술하지 않았음을 diff 로 확인.
- [ ] **AC-4** 검증: doc-only 면 lint 대상 변경 0. web-store 로컬 검증은 신뢰도가 낮다(workspace + Node24 제약) — **CI 가 권위**임을 알고 결과를 읽는다.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` → 선언된 domain/trait 규칙.

- `projects/platform-console/docs/conventions/frontend-ui.md` (**정경 — 이 task 가 가리킬 대상**)
- `projects/platform-console/tasks/done/TASK-PC-FE-241-console-ui-conventions-have-no-canonical-home.md` (이 task 를 낳은 결정, AC-4)
- `projects/ecommerce-microservices-platform/PROJECT.md`

# Related Skills

N/A — 문서 한 줄 + 코드 대조.

---

# Related Contracts

None (doc-only).

---

# Target App

- `apps/web-store` (문서만; 코드 변경 없음)

---

# Implementation Notes

- **포인터는 "여기 규칙 있음" 이 아니라 "왜 여기 없는지" 까지 말해야 쓸모가 있다** — web-store 가 헬퍼를 복제한 이유(cross-project import 불가)를 함께 적지 않으면, 다음 사람은 복제본을 보고 *"콘솔 것을 import 하면 되는데 왜 복사했지"* 라 생각하고 통합을 시도한다.

---

# Edge Cases

- 포인터를 넣을 파일이 없다고 판단되면 **새 파일을 만들기 전에** 정말 없는지 확인 — 아무도 안 읽는 새 문서에 포인터를 넣으면 **규칙은 여전히 도달 불가**다(포인터의 목적은 도달성이다).

---

# Failure Scenarios

- **규칙을 복사해 버린다** → 두 곳이 갈라지고, PC-FE-241 이 막으려던 바로 그 상태가 된다. 완화 = AC-3.
- **헬퍼가 이미 갈라져 있는데 이 task 에서 "겸사겸사" 고친다** → 검증 없는 프런트 동작 변경. 완화 = Out of Scope + AC-1(기록만).

---

# Test Requirements

- doc-only ⇒ 신규 테스트 없음. CI GREEN 확인.

---

# Definition of Done

- [ ] 포인터 랜딩, 규칙 본문 단일 소재 유지.
- [ ] AC-1 대조 결과 기록(일치 / 불일치 + 별건 제안).
- [ ] `projects/ecommerce-microservices-platform/tasks/INDEX.md` done entry(close chore).

---

# Provenance

`TASK-PC-FE-241`(2026-07-14, `/audit-memory` 산물)의 **AC-4 잔여**. 구현 에이전트가 포인터 편집이 자기 Target App 범위 밖(cross-project)임을 정확히 판단해 **넣지 않고 후속으로 남겼다** — 그 판단이 옳았고, 이 task 가 그 잔여다.

분석=Opus 4.8 / 구현 권장=**Haiku 또는 Sonnet**(문서 한 줄 + 코드 대조).
