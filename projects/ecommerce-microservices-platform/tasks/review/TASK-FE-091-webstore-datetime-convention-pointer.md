# Task ID

TASK-FE-091

# Title

web-store 는 콘솔의 날짜·시간 규약을 **복제해 쓰면서** 그 규약이 어디 적혀 있는지 모른다 — 포인터 한 줄

# Status

review

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

- [x] **AC-1 (대조)** — **일치. 갈라지지 않았다.** `web-store/src/shared/lib/datetime.ts` 전문 대조: `hourCycle:'h23'` ✔ · `timeZone:'Asia/Seoul'` **pinned** ✔ · placeholder 기본 `'-'` ✔ · 파싱불가 입력 → **원문 verbatim 반환**(throw 안 함) ✔ · `formatDate` = `toLocaleDateString('ko-KR', {timeZone})` ✔. **코드 변경 0** (별건 티켓 제안 없음).
- [x] **AC-2 (도달성)** 포인터 **2곳**: ① `PROJECT.md` — 에이전트 필독 **Source of Truth #1** (`CLAUDE.md` 가 착수 시 읽으라고 규정한 파일) ② `docs/onboarding/local-dev.md` § Project-specific notes — 사람 경로(콘솔이 `frontend-ui.md` 를 링크한 것과 같은 자리). **링크·앵커 실측 검증**: 상대경로 2개 모두 해석됨, 앵커 원본 `## 1. Date/time formatting` 존재 확인.
- [x] **AC-3 (규칙 본문 단일)** 두 포인터 모두 **규칙을 재서술하지 않는다** — "무엇을 쓰라"(헬퍼 이름)와 "왜 여기 없는가"(cross-project import 불가)만 적고, **근거**(자정 quirk·하이드레이션)는 *가리키기만* 한다. 규칙 본문은 여전히 `frontend-ui.md` 한 곳.
- [x] **AC-4 (검증)** **doc-only — 코드/lint 대상 변경 0줄**(`.md` 2개 + task 2개). web-store 로컬 검증은 신뢰도가 낮으므로(workspace + Node24) **CI 가 권위**.

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

---

# 착수 기록 (구현)

## 포인터를 어디에 뒀나 — 후보 대부분이 **빈 파일**이었다

티켓은 *"이미 프런트 개발자가 읽는 파일"* 을 고르라고 했다. `docs/onboarding/` 을 열어보니 **`coding-standards.md`·`build-test-run.md`·`repository-map.md`·`release-process.md` 가 전부 0바이트 스텁**이다. 여기 포인터를 넣었다면 **티켓의 Edge Case 를 그대로 밟는다**("아무도 안 읽는 문서에 넣으면 규칙은 여전히 도달 불가").

살아있는 문서는 `local-dev.md`(49줄) 와 `PROJECT.md`(81줄) 뿐이다. ⇒ **둘 다** 썼다:

- **`PROJECT.md`** — `CLAUDE.md` 가 착수 시 **반드시 읽으라고 규정한 Source of Truth #1**. 에이전트 경로에서 **한 홉**. (같은 레버를 `TASK-PC-FE-242` 가 콘솔 쪽에 적용했다.)
- **`docs/onboarding/local-dev.md` § Project-specific notes** — 사람 경로. 콘솔이 `frontend-ui.md` 를 링크한 것과 **같은 자리**(대칭).

## 🔵 정경 문서의 문장 하나가 정확하지 않다 (기록만, 고치지 않음)

`frontend-ui.md` § 4 는 web-store 의 `datetime.ts` 주석이 *"only says 'mirrors platform-console' without a link"* 라고 적었다. **실제 주석은 근거를 짧게 갖고 있다** — pinned KST(SSR↔hydration byte-identical)와 `hourCycle:'h23'`(ko-KR 자정 quirk)을 3줄로 요약해 둔다. 빠진 것은 **근거가 아니라 *링크*** 다.

**판단: 그 3줄을 지우지 않는다.** ① 코드 편집은 이 task 의 Out of Scope 다. ② 그 3줄은 **호출부에서 읽히는 요약**이지 정경과 경쟁하는 규칙 서술이 아니다 — 오히려 다음 사람이 `h23` 을 "간소화" 하는 것을 막는 마지막 방어선이다. 규칙 **본문**(예외·AC-3 판정·web-store 결정)은 여전히 `frontend-ui.md` 한 곳에만 있다.

**AC-1 결론**: 헬퍼는 갈라지지 않았다 — 정합화 티켓 불필요.
