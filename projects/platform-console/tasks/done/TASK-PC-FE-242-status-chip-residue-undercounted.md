# Task ID

TASK-PC-FE-242

# Title

정경 문서가 자기 잔여를 과소계수했다 — StatusBadge 미채택은 3건이 아니라 15건(그리고 내가 티켓에 쓴 7건도 과소계수였다). 롤아웃이 리스트에서 멈추고 상세를 두고 갔다

# Status

done

# Owner

frontend

# Task Tags

- code
- refactor

---

# Goal

`TASK-PC-FE-241`(PR #2567)이 console-web 의 UI 컨벤션 3종을 [`docs/conventions/frontend-ui.md`](../../docs/conventions/frontend-ui.md) 로 정경화했다. **선언은 옳았지만 그 문서가 자기 자신에 대해 적은 사실 세 개가 틀렸다.** 이 task 는 문서를 만든 다음 날 문서를 **재측정**해서 나온 셋을 닫는다.

### ① §3 "알려진 잔여 3건" 은 과소계수다 — 상태칩 술어로 다시 세면 **7건**

> **⚠️ 착수 재측정 결과 이 절의 "7건" 도 과소계수였다 — 실측 15건.** 아래 표는 **티켓을 쓸 당시의 술어**("팔레트를 손으로 쓴 pill")가 본 것이고, 그 술어는 **색이 아니라 *톤 매핑이 통째로 없는*** 칩(상수 `bg-muted`)을 못 본다. 최종 실측은 AC-0 / Implementation Notes 참조. **이 절을 고쳐 쓰지 않고 남겨 둔다** — 물려받은 숫자를 다시 세라는 규칙이 *내가 어제 쓴 숫자* 에도 적용된다는 증거이기 때문이다.

| # | 컴포넌트 | 지문 | 문서가 적었나 |
|---|---|---|---|
| 1 | `erp-ops/DelegationFactCard.tsx` | 자체 배지 함수, `bg-blue-100` / `bg-green-100` 하드코딩 | ✅ |
| 2 | `erp-ops/DelegationGrantList.tsx` | 자체 `DelegationStatusBadge`, `bg-red-100` / `bg-green-100` | ✅ |
| 3 | `erp-ops/EmployeeOrgViewCard.tsx` | 인라인 `bg-amber-100` 상태 셀 | ✅ |
| 4 | `erp-ops/EffectivePeriodBadge.tsx` | **자체 톤 어휘** `normal\|warn\|danger` 를 `StatusTone` 과 병행 운영, `warn` 이 `bg-amber-100` 하드코딩 | ❌ |
| 5 | `erp-ops/EmployeeDetail.tsx` | `employmentStatus` 를 삼항으로 색칠 — **`employmentStatusTone()` 헬퍼가 이미 있는데도** | ❌ |
| 6 | `ledger-ops/FxRatesTable.tsx` | 피드 상태칩 + 행 배지 4곳, `bg-green-100`/`bg-yellow-100` | ❌ (erp 밖) |
| 7 | `wms-ops/WmsAlertsTable.tsx` | `확인/미확인` 칩, `bg-amber-100` | ❌ (erp 밖) |

문서의 3건은 **erp-ops 만 들여다본 결과**였다. 규칙(§3)은 콘솔 전역인데 잔여는 한 피처에서만 셌다.

### ② 6번은 "미채택"이 아니라 **결함**이다

`FxRatesTable` 의 네 칩에는 **`dark:` 변형이 하나도 없다**(`bg-green-100 text-green-800` 만). 콘솔은 다크모드를 지원하고 `StatusBadge` 는 톤마다 dark 변형을 내장한다 ⇒ 이 칩들은 **다크 테마에서 흰 배경 위 밝은 글자**로 깨진다. 나머지 6건은 dark 변형을 손으로 갖고 있어서 "일관성" 문제지만, 이건 **버그**다. 잔여를 스타일 이슈로만 적어 두면 이 사실이 보이지 않는다.

### ③ 승격이 무손실이 아니었다 — §1 이 규칙 하나를 흘렸다

옛 메모리가 갖고 있던 **"같은 필드는 리스트·상세에서 동일 포맷"**(리스트라고 날짜만 쓰지 말 것)이 `frontend-ui.md` §1 에 전사되지 않았다. 이 규칙은 **코드가 실제로 지킨다** — `OrdersTable.tsx:81` 과 `OrderDetail.tsx:83` 둘 다 같은 `createdAt` 에 `formatDateTime` 을 쓴다. 정경 문서에만 없다.

### ④ 도달성 — 아무도 안 여는 정경 문서는 정경이 아니다

`frontend-ui.md` 를 가리키는 링크는 저장소 전체에서 **`docs/onboarding/local-dev.md` 한 곳뿐**이고, 그건 **사람용 온보딩 문서**다. `PROJECT.md`(= `CLAUDE.md` 가 규정한 **Source of Truth #1**, 에이전트가 착수 시 반드시 읽는 파일)에는 포인터가 없다. 새 세션이 콘솔 FE 를 만질 때 이 문서에 **도달할 경로가 없다** — PC-FE-241 이 고치려던 바로 그 실패("규칙이 저장소 밖에 산다")가 한 겹 얕아진 채 재현된다.

---

# Scope

## In Scope

- **AC-0 재측정 우선** — 위 7건은 **인계된 가설**이다. 착수 시 다시 센다.
- 재측정된 상태칩 전부를 `StatusBadge` 또는 `statusToneClass(tone)` 로 이관. **도메인 톤 맵이 없으면 만든다**(예: `EffectivePeriodBadge` → `effectivePeriodTone`), 있으면 **쓴다**(`employmentStatusTone`).
- `FxRatesTable` 다크모드 결함 해소(톤 팔레트가 dark 변형을 가져온다).
- `frontend-ui.md` §3 "Known residue" 갱신 + **"잔여가 아닌 것"(의도적 비대상) 명시**.
- `frontend-ui.md` §1 에 유실된 규칙 복원.
- `PROJECT.md` → `docs/conventions/frontend-ui.md` 포인터 한 줄(도달성).

## Out of Scope

- **`toLocale*` 직접 호출 lint 가드** — PC-FE-241 AC-3 이 "오탐 0 불가"로 판정했다(`Number#toLocaleString` 40+ 호출부). 그 판정을 뒤집지 않는다.
- **컨벤션 변경.** 코드가 하는 것을 통일하는 것이지 새 규칙을 만들지 않는다.
- **상태칩이 아닌 색상 사용부.** 서비스 헬스 **dot**(`ErpOverviewScreen` `bg-green-500`), 현재 단계 **행 하이라이트**(`ApprovalDetail`), `환산(revaluation)` **주석 태그**(`JournalEntryDetail`), 알림 **카운트 배지**(`NotificationBell`) 등은 pill 상태칩이 아니다 — 건드리지 않되 **문서에 "비대상" 으로 적어** 다음 사람이 또 "발견" 하지 않게 한다.

---

# Acceptance Criteria

- [x] **AC-0 (모집단 재측정)** — **7 이 아니라 15 다.** 술어를 "팔레트가 하드코딩된 pill" 로만 잡으면 **색이 아니라 *톤 매핑 자체가 없는*** 칩(상수 `bg-muted`)을 놓친다. 술어를 **"`StatusBadge` 를 우회한 상태칩"** 으로 넓혀 전수 재측정 → **15 칩 / 12 컴포넌트**. 상세는 아래 Implementation Notes.
- [x] **AC-1** 재측정된 상태칩 15개가 전부 `StatusBadge` / `statusToneClass` / `statusToneColorClass` 를 경유한다. 상태칩의 하드코딩 팔레트 **0건**.
- [x] **AC-2 (계약 보존)** `data-testid`·라벨·`data-status`·`data-retired`·`title` 전부 보존. **증거: 테스트 2804/2804 GREEN, 테스트 수정 0줄.** (색을 단언하는 테스트는 처음부터 없었고, 그래서 이 드리프트가 5개월간 아무 게이트에도 안 걸렸다.)
- [x] **AC-3 (다크모드)** `FxRatesTable` 4칩이 톤 팔레트를 경유 → `dark:` 변형 확보. 상태칩 중 `dark:` 누락 **0건**.
- [x] **AC-4 (문서 정정)** `frontend-ui.md` §3 = "잔여 0" + **의도적 비대상 4범주**(헬스 dot / 속성칩 / 행 하이라이트·주석 태그 / 시맨틱 토큰 지표) + **escape hatch 3단계 표**.
- [x] **AC-5 (유실 복원)** §1 에 "같은 필드 = 어디서나 동일 포맷" 복원 + §3 잔여가 **왜 이 규칙의 문제였는지** 연결(같은 `ACTIVE` 가 리스트=초록/상세=회색이었다).
- [x] **AC-6 (도달성)** `PROJECT.md` 에 「Frontend UI Conventions (필독)」 절 신설 → SoT #1 에서 **한 홉**.
- [x] **AC-7 (검증)** `tsc --noEmit` 0 · `next lint` 0 · `vitest run` **2804/2804 (270 files)**.

---

# Related Specs

- [`projects/platform-console/docs/conventions/frontend-ui.md`](../../docs/conventions/frontend-ui.md) — §1(날짜·시간) / §3(StatusBadge) 이 이 task 의 대상.
- `TASK-PC-FE-241` (done) — 정경 홈 신설. 이 task 는 그 문서의 **사실 정정 + 잔여 청산**이다.
- `TASK-PC-FE-158` / `TASK-PC-FE-159` (done) — `StatusBadge` 추출 + 전면 롤아웃. 이 task 는 그 롤아웃이 **놓친 꼬리**를 닫는다.

# Related Contracts

없음. **UI 전용 리팩토링 — API 계약·이벤트 계약 변경 0.** 상태 문자열 자체(producer enum)는 손대지 않는다: 배지의 라벨은 **원본 status 문자열을 그대로** 유지한다(a11y 텍스트 + 테스트 단언 + 필터 정합).

# Edge Cases

- **미지/미래/absent status** — `tone` 기본값 `neutral`(TOLERANCE 불변식: 인식 못 하는 producer status 가 콘솔을 죽이면 안 된다). 마이그레이션이 이 관용성을 **줄이면 안 된다**.
- **`EffectivePeriodBadge` 의 3상태**(`active`/`retired`/`scheduled`)는 status enum 이 아니라 **기간 계산 결과**다. `StatusTone` 으로 사상하되(`retired`→`warning`, 나머지→`neutral`) `data-retired`·`data-testid` 계약(`erp-effective-retired`/`erp-effective-active`)은 **그대로**. E2 정직성 불변식(은퇴 행을 숨기지 않고 시각적으로만 구분)을 깨지 않는다.
- **`EmployeeOrgViewCard`·`DelegationFactCard` 의 배지 중 일부는 status 가 아니라 *사실 표시***(예: "위임 중")일 수 있다. 재측정에서 status 가 아니면 **비대상으로 분류하고 문서에 적는다** — 억지로 톤에 사상하지 않는다.
- **`bg-muted`/`bg-destructive` 등 시맨틱 토큰**을 쓰는 자리는 하드코딩이 아니다. 술어에 걸리지 않게 주의(오탐).

# Failure Scenarios

- **색만 바꾸고 계약을 깬다** — testid 나 라벨이 바뀌면 콘솔 테스트가 아니라 **E2E** 에서 늦게 터진다. AC-2 가 게이트.
- **술어가 너무 넓어 비상태칩까지 끌고 온다** — 헬스 dot·행 하이라이트·카운트 배지를 톤에 사상하면 의미가 뭉개진다(dot 은 pill 이 아니다). Out of Scope 가 게이트.
- **다크모드 회귀를 "스타일 통일" 로 덮는다** — `FxRatesTable` 을 톤으로 옮기면 색이 *바뀐다*(dark 변형이 생기므로). 이건 의도된 변경이며 **AC-3 에 명시**한다. 라이트 테마 스크린샷만 보고 "색이 달라졌다" 로 롤백하지 말 것.
- **문서를 고치면서 또 흘린다** — §3 을 "0건" 으로만 바꾸고 **비대상 목록을 안 적으면**, 다음 세션이 `ErpOverviewScreen` 의 `bg-green-500` dot 을 보고 "잔여 발견" 이라고 티켓을 또 연다. AC-4 의 후반부가 그것을 막는다.

---

# Implementation Notes

## AC-0 — 술어를 두 번 틀렸다

**1차 술어(= 이 티켓을 쓸 때 쓴 것): "하드코딩 팔레트를 쓴 pill"** → 7건. 이 술어는 **색이 틀린 칩만** 본다.

**2차 술어(= 착수 후): "`StatusBadge` 를 우회한 상태칩"** → **15건 / 12 컴포넌트.** 1차가 놓친 것은 **색이 아니라 *톤 매핑이 없는*** 칩이다 — `EmployeeDetail:96` 같은 자리는 상수 `bg-muted` 를 쓴다. **시맨틱 토큰이라 "하드코딩" 필터에 안 걸리는데, 정작 `ACTIVE` 든 `RETIRED` 든 언제나 회색이다.** 즉 위반의 본체는 팔레트가 아니라 **`status → tone` 사상이 통째로 없다는 것**이었고, 내 첫 탐지식은 그걸 볼 수 없었다.

### 실측 (15 chips / 12 components)

| 파일 | 칩 | 지문 |
|---|---|---|
| `erp-ops/DepartmentDetail` | 상태 | 상수 `bg-muted` — 톤 매핑 없음 |
| `erp-ops/JobGradeDetail` | 상태 | 〃 |
| `erp-ops/CostCenterDetail` | 상태 | 〃 |
| `erp-ops/BusinessPartnerDetail` | 상태 | 〃 |
| `erp-ops/EmployeeDetail` | 상태 · 고용상태 | 상수 `bg-muted` / SEPARATED 만 amber 삼항 |
| `erp-ops/EmployeeOrgViewCard` | 상태 · 동기화중 | 상수 `bg-muted` / amber 하드코딩 |
| `erp-ops/EffectivePeriodBadge` | 기간상태 | 자체 어휘 `normal\|warn\|danger` (+ 쓰이지 않는 `danger`) |
| `erp-ops/DelegationGrantList` | 위임상태 | 자체 배지, red/green 하드코딩 |
| `erp-ops/DelegationFactCard` | 위임상태 | **공유 컴포넌트와 이름이 같은 로컬 `StatusBadge`** |
| `ledger-ops/FxRatesTable` | 피드상태 · STALE/최신 | green/yellow 하드코딩, **`dark:` 전무** |
| `wms-ops/WmsAlertsTable` | 확인/미확인 | amber 하드코딩 |
| `accounts/AccountStatusBadge` | 계정상태 | **`StatusBadge` 의 두 번째 구현체** |

## 🔴 진짜 지문 — 이건 "잔여" 가 아니라 **롤아웃이 절반에서 멈춘 것**이다

`PC-FE-159` 는 "전 콘솔 status 컬럼 통일" 로 종결됐다. 실제로 통일된 것은 **리스트/테이블 표면**이고 **상세 표면은 손으로 그린 칩을 그대로 뒀다.** 결과는 스타일 불일치가 아니라 **같은 값이 화면마다 다른 색**이다:

- ERP 마스터 5종: `ACTIVE` 가 **리스트=초록 / 상세=회색**.
- `EmployeeDetail`: `SEPARATED` 가 **리스트=회색 / 상세=amber**(헬퍼 `employmentStatusTone` 은 **이미 있었다**).
- 위임: 같은 `REVOKED` 가 **GrantList=빨강 / FactCard=회색**.

**이것은 §1 의 "같은 필드는 어디서나 같은 포맷" 규칙의 *상태* 판(版)이다.** 그래서 §1 에 그 규칙을 복원하면서 §3 과 연결했다(AC-5).

### 색이 실제로 바뀌는 곳 (의도된 변경)

| 대상 | before | after | 근거 |
|---|---|---|---|
| ERP 마스터 상세 `ACTIVE` | 회색 | **초록** | 리스트와 일치(`masterStatusTone`) |
| `EmployeeDetail` `SEPARATED` | amber | **회색** | 리스트와 일치. `ON_LEAVE` 는 회색→amber |
| `DelegationFactCard` `REVOKED` | 회색 | **빨강** | GrantList 와 일치. 회수(능동)와 만료(수동)를 구별하는 쪽이 정보량이 많다 |
| `accounts` `ACTIVE` | 회색 | **초록** | 다른 모든 도메인과 일치 |
| `FxRatesTable` 4칩 | dark 없음 | **dark 대응** | 다크 테마에서 흰 배경+밝은 글자로 깨지던 **버그** |

라이트 테마 스크린샷만 보고 "색이 바뀌었다" 로 되돌리지 말 것 — **되돌리면 리스트↔상세 불일치가 복구된다.**

## 계약 보존 — 무엇을 건드리지 않았나

- `data-status` 는 **producer enum 이 아니다.** `DelegationGrantList` 는 `validTo` 가 지난 `ACTIVE` 를 `ACTIVE_EXPIRED` 로 보고하고, **`status` 가 자유 문자열(`z.string()`)이라 미지값도 같은 가지로 떨어진다.** 첫 리팩토링 초안이 이 tolerant 분기를 미지값→`활성` 으로 바꿔놨다가 스키마를 읽고 되돌렸다 — **세 라벨과 세 `data-status` 값 전부 원본 그대로.**
- `EffectivePeriodBadge` 는 `data-retired`·`title` 을, `DelegationFactCard` 는 `data-status` 를 갖는데 `<StatusBadge>` 는 이 속성들을 전달하지 않는다 → **`statusToneClass` escape hatch** 로 자체 `<span>` 유지.
- `FxRatesTable` 의 피드 배지는 문장을 담은 페이지 레벨 표시라 `text-sm rounded-full` 기하학이 의미가 있다 → **팔레트만** 필요 → `statusToneColorClass` **신설**(공유 모듈에 색만 주는 3단계 escape hatch. `statusToneClass` 가 이를 합성하도록 해서 팔레트 정의는 여전히 한 곳).

## 비대상 (문서에 명시 — 다시 "발견" 되지 않도록)

헬스 **dot**(`bg-green-500`, 5곳) · **속성칩**(`partnerType` 유형, `ScopeCell` GLOBAL/REQUEST) · **행 하이라이트/주석 태그**(`ApprovalDetail` 현재단계, `JournalEntryDetail` 환산, `NotificationBell` 카운트) · **시맨틱 토큰 지표**(`TrialBalanceTable`/`PeriodDetail`/`WmsInventoryDataTable`/`OperatorBadges` 의 `bg-destructive/15` — 테마 토큰이라 다크 대응이 이미 되고, producer status enum 이 아니다).

## 검증

- `npx tsc --noEmit` → **0**
- `npx next lint` → **0 warnings / 0 errors**
- `npx vitest run` → **270 files / 2804 tests, 2804 passed** — **테스트 수정 0줄**
- 검증 환경: worktree 에 메인 체크아웃 `node_modules` **junction**(메인 미접촉; worktree 제거 전 junction 선-rmdir 필요).

**초록이 증명하지 못하는 것**: 색을 단언하는 테스트는 **처음부터 없었다.** 그래서 2804 GREEN 은 "계약이 보존됐다" 는 증거이지 "색이 옳다" 는 증거가 아니다 — 색 판단의 근거는 위 표(리스트↔상세 일치)와 팔레트의 시맨틱 정의다.
