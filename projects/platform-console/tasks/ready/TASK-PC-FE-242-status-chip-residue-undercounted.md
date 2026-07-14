# Task ID

TASK-PC-FE-242

# Title

정경 문서가 자기 잔여를 과소계수했다 — StatusBadge 미채택은 3건이 아니라 7건이고, 그중 하나는 다크모드 결함이다

# Status

ready

# Owner

frontend

# Task Tags

- code
- refactor

---

# Goal

`TASK-PC-FE-241`(PR #2567)이 console-web 의 UI 컨벤션 3종을 [`docs/conventions/frontend-ui.md`](../../docs/conventions/frontend-ui.md) 로 정경화했다. **선언은 옳았지만 그 문서가 자기 자신에 대해 적은 사실 세 개가 틀렸다.** 이 task 는 문서를 만든 다음 날 문서를 **재측정**해서 나온 셋을 닫는다.

### ① §3 "알려진 잔여 3건" 은 과소계수다 — 상태칩 술어로 다시 세면 **7건**

문서(와 그 출처인 에이전트 메모리)는 미채택을 erp `DelegationFactCard` / `DelegationGrantList` / `EmployeeOrgViewCard` **3개**로 적었다. `StatusBadge`/`statusToneClass` 를 우회해 **팔레트를 손으로 쓴 pill** 이라는 술어로 전수 재측정하면:

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

- [ ] **AC-0 (모집단 재측정)** 상태칩 술어로 `console-web/src` 를 전수 재측정하고 결과를 표로 기록한다. **7 과 다르면 실측이 이긴다** — 이 티켓의 숫자도 가설이다.
- [ ] **AC-1** 재측정된 상태칩이 전부 `StatusBadge` 또는 `statusToneClass` 를 경유한다. 하드코딩 팔레트(`bg-*-100 text-*-800` 류)가 상태칩에서 **0건**.
- [ ] **AC-2 (계약 보존)** `data-testid`·라벨 텍스트·`data-*` 부가 속성이 **전부 보존**된다(테스트가 색이 아니라 testid/텍스트를 단언한다 — 착수 시 확인됨). 구조가 달라 컴포넌트를 못 쓰는 배지는 `statusToneClass` 로 **팔레트만** 주입.
- [ ] **AC-3 (다크모드)** `FxRatesTable` 의 칩이 다크 변형을 갖는다. 마이그레이션 후 **상태칩 중 `dark:` 누락 0건**.
- [ ] **AC-4 (문서 정정)** `frontend-ui.md` §3 의 잔여 목록이 실측과 일치(마이그레이션 후 = 0건) + **의도적 비대상 목록**이 적힌다.
- [ ] **AC-5 (유실 복원)** `frontend-ui.md` §1 에 "같은 필드 = 리스트·상세 동일 포맷" 규칙이 복원된다(근거: `OrdersTable`/`OrderDetail` `createdAt`).
- [ ] **AC-6 (도달성)** `projects/platform-console/PROJECT.md` 가 `docs/conventions/frontend-ui.md` 를 가리킨다. 에이전트 필독 경로(SoT #1)에서 **한 홉**으로 닿는다.
- [ ] **AC-7 (검증)** console-web 로컬 GREEN: `pnpm lint` + `tsc` + `vitest`. **`lint` 필수** — tsc·vitest 가 못 잡는 CI 프런트 RED 가 있다.

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
