# Task ID

TASK-PC-FE-241

# Title

콘솔 UI 컨벤션 3종(날짜·시간 표기 / DetailHeader·dl 순서 / StatusBadge)이 저장소 어디에도 적혀 있지 않다 — 에이전트 메모리에만 산다

# Status

ready

# Owner

frontend

# Task Tags

- code
- onboarding

---

# Goal

console-web 은 세 개의 **실제로 강제되는** UI 컨벤션 위에서 돌아간다:

1. **날짜·시간 표기** — `shared/lib/datetime.ts` 의 `formatDateTime`/`formatDate` 만 쓴다. **`toLocale*` 직접 호출 금지.** 정밀도 규칙(기록 타임스탬프=시각까지 / 일 단위 값=날짜까지), `hourCycle:'h23'`(ko-KR `hour12:false` 가 자정을 `24:00:00` 으로 뱉는 quirk), `timeZone:'Asia/Seoul'` 고정(로컬 존이면 SSR-seed 뷰가 **하이드레이션 불일치**).
2. **상세/폼 헤더 + dl 필드 순서** — 공유 `DetailHeader`(인라인 `<div flex justify-between>+<Link>` 금지), dl 순서 = **명칭 → 상태 → 식별자/속성 → 날짜**(날짜는 항상 마지막).
3. **상태 배지** — 공유 `@/shared/ui/StatusBadge`. **상태칩 재구현 금지**; 도메인은 `status→StatusTone` 맵만 소유. 부가 속성이 필요한 배지는 `statusToneClass(tone)` 로 팔레트만 주입.

**이 규칙들은 지켜지고 있지만 저장소에는 없다.** `projects/platform-console/` 의 specs·docs 어디에도 한 줄이 없고(2026-07-14 grep 0건), 근거는 **닫힌 task 본문과 에이전트 메모리**뿐이다. ⇒ 새로 합류하는 사람·세션은 **코드를 역공학해야만** 알 수 있고, 역공학은 규칙과 우연을 구별하지 못한다. **1곳에만(그것도 저장소 밖에) 있는 규칙은 사실상 없는 규칙이다.**

---

# Scope

## In Scope

- **`projects/platform-console/docs/conventions/frontend-ui.md` 신설** — 위 3종을 규칙 + **왜**(quirk·하이드레이션·정보위계) + **적용법**으로 기술. 각 규칙에 그것을 확립한 task/PR 번호를 각주로.
- **AC-1 재검증** — 문서를 쓰기 전에 **현행 코드로 각 규칙을 다시 확인**한다(메모리는 12일 됐고 그 사이 PC-FE-233~240 리팩토링이 지나갔다). 규칙이 코드와 어긋나면 **문서는 코드를 따르고**, 어긋난 지점을 이 task 에 기록한다.
- **web-store 포인터** — 날짜·시간 규칙은 `web-store`(다른 앱, 별도 헬퍼 복제본)에도 적용된다. **정경 홈을 어디로 할지 결정**하고(권장: 콘솔 문서를 정경으로, web-store 쪽 README/문서에 **포인터 한 줄**), 결정을 문서에 명시. **두 곳에 규칙 본문을 복사하지 말 것**(복사본은 갈라진다 — 이 저장소가 반복해서 치른 대가).

## Out of Scope

- **기계 가드(lint 룰) 신설.** `no-restricted-syntax` 로 `toLocaleString` 직접 호출을 막는 것은 매력적이지만, 헬퍼 자신과 UTC day-edge 예외(`formatPromotionDay`)가 정당하게 호출한다 ⇒ 예외 목록 없이는 **첫날 RED**. 이 task 는 **선언**을 만든다. 가드는 AC-3 의 판정에 따라 별건.
- 컨벤션 **변경**. 지금 코드가 하는 것을 적는 것이지 새로 정하는 게 아니다.
- 미채택 잔여(erp `DelegationFactCard`/`DelegationGrantList`/`EmployeeOrgViewCard` 부차 배지) 마이그레이션 — 문서에 "알려진 잔여"로만 기록.

---

# Acceptance Criteria

- [ ] **AC-1 (모집단 재측정)** 3종 규칙 각각을 **현행 `console-web` 코드로 확인**하고(헬퍼 존재·시그니처, `DetailHeader` props, `StatusBadge` tone 목록), 메모리 기술과 다른 점을 표로 기록. **다르면 코드가 이긴다.**
- [ ] **AC-2** `projects/platform-console/docs/conventions/frontend-ui.md` 신설 — 3종 규칙 + 왜 + 적용법 + 알려진 예외(UTC day-edge, 미채택 배지).
- [ ] **AC-3 (가드 판정 명시)** "`toLocale*` 직접 호출 금지를 오탐 0 으로 기계화할 수 있는가" 에 답을 적는다. 가능=별건 티켓 제안 / 불가능=**왜**를 문서에 한 줄로(다음 사람이 첫날-RED lint 룰을 만들지 않도록).
- [ ] **AC-4** web-store 정경 홈 결정이 문서에 적혀 있고, **규칙 본문은 한 곳에만** 존재한다(다른 쪽은 포인터).
- [ ] **AC-5** console-web 로컬 검증 GREEN: `pnpm lint` + `tsc` + `vitest`(**`lint` 필수** — tsc·vitest 가 못 잡는 CI 프런트 RED 가 있다). doc-only 라면 lint 대상 변경이 없음을 확인.

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md` → `rules/common.md` → 선언된 domain/trait 규칙.

- `projects/platform-console/PROJECT.md`
- `projects/platform-console/docs/onboarding/local-dev.md` (문서 이웃 — 신설 문서에서 상호 링크)
- 규칙을 확립한 닫힌 task: PC-FE-158/159(→166/167 리넘버, StatusBadge) · #2079/#2084/#2085/#2087(DetailHeader·dl) · #2096/#2099/#2110(datetime)
- 메모리 `proj_console_datetime_format_convention` · `proj_console_ecommerce_detail_conventions` (worked detail — 승격 후 "정경 승격됨 (PC-FE-241)" 포인터를 단다)

# Related Skills

- `.claude/skills/INDEX.md` → frontend 계열(문서 작성이라 최소)

---

# Related Contracts

None (doc-only).

---

# Target App

- `apps/console-web` (문서만; 코드 변경 없음)

---

# Implementation Notes

- 문서는 **catalog 가 아니라 정경**이다 — 규칙 본문이 여기 살고, 메모리는 사건 기록으로 강등된다.
- `hourCycle:'h23'` 과 `timeZone:'Asia/Seoul'` 은 **취향이 아니라 버그 픽스**다(자정 `24:00:00` quirk / SSR 하이드레이션 불일치). 이유를 지우면 다음 사람이 "간소화" 한다.

---

# Edge Cases

- **UTC day-edge**: 프로모션 시작/종료는 `00:00:00Z`/`23:59:59Z` 로 저장 → `formatPromotionDay` 가 `timeZone:'UTC'` 로 포맷(KST +9 에서 종료일이 하루 밀리는 것 방지). **일반 `formatDate` 와 구분해서 적어야** 다음 사람이 "일관성" 이라며 통일하다 버그를 만든다.
- 미지/미래/부재 status → `neutral` tone(TOLERANCE, no-crash). 이건 관용이지 누락이 아니다.

---

# Failure Scenarios

- **규칙 본문을 두 앱에 복사** → 갈라진다. 완화 = AC-4(한 곳 + 포인터).
- **메모리를 그대로 옮겨 적는다** → 12일 된 관측이 문서로 승격되어 코드와 어긋난 채 정경이 된다. 완화 = AC-1(코드 재검증이 먼저, 코드가 이긴다).
- **첫날-RED lint 룰** → 꺼지고, 꺼진 가드는 없는 가드보다 나쁘다. 완화 = Out of Scope + AC-3.

---

# Test Requirements

- doc-only ⇒ 신규 테스트 없음. `pnpm lint`/`tsc`/`vitest` 회귀 0.

---

# Definition of Done

- [ ] `docs/conventions/frontend-ui.md` 랜딩(3종 + 예외 + 가드 판정).
- [ ] web-store 포인터 결정 반영, 규칙 본문 단일 소재.
- [ ] 머지 후 두 메모리에 "정경 승격됨 (PC-FE-241)" 포인터.
- [ ] `projects/platform-console/tasks/INDEX.md` done entry(close chore).

---

# Provenance

2026-07-14 `/audit-memory` 감사에서 표면화 — 두 메모리(`proj_console_datetime_format_convention`, `proj_console_ecommerce_detail_conventions`)가 담은 규칙이 **저장소 grep 0건**임을 확인. 사용자 승인으로 티켓팅. 짝 티켓 = `TASK-MONO-408`(공유 라이브러리 판별식 승격, root).

분석=Opus 4.8 / 구현 권장=**Sonnet**(문서 + 코드 재검증. 판단 지점은 AC-3·AC-4 이며 기준은 여기 적혀 있다).
