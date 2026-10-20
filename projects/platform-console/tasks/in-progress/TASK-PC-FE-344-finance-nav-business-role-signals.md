# Task ID

TASK-PC-FE-344

# Title

`ADR-MONO-083` P7 콘솔 후속 — Finance 메뉴 «업무 권한 없음» · 쓰기 버튼을 Finance 세분 역할로 (`permission-map.ts` `serverRequires` / `writeRequires` · 계약 § 2.4.3.4) · 운영 개요 Finance 카드

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (데이터 행 · 계약 표 — 규칙 신설 없음)
> ⏳ **머지 게이트 — «`TASK-MONO-794` 를 실은 AMI 굽기가 라이브 확인된 뒤에만 머지»** (소유자 결정 2026-10-16 UTC, `TASK-MONO-794` § 소유자 결정 · 순서 — 792 · 793 과 같다). 콘솔은 머지 즉시 Vercel 로 나가고 백엔드는 굽기에서만 나간다. 굽기 전에 머지하면 라이브 `GET /api/admin/me/domain-access` 가 아직 `FINANCE_OPERATOR` 만 내므로 **모든 운영자의 Finance · 원장 메뉴에 거짓 «업무 권한 없음» + 대사 해소 · 환율 갱신 버튼 숨김** — 서버는 열어 주는데 화면이 닫는다. 반대(굽기 뒤 · 이 PR 전)는 조회 세트 운영자에게 쓰기 버튼이 보이고 누르면 서버 403(화면 안 «권한 없음») — 안전 쪽 실패.

# Dependency Markers

- **선행**: `TASK-MONO-794`(머지 + 그 커밋을 담은 굽기 + 라이브 확인 — `TASK-MONO-672` § Next window — TASK-MONO-794). 이 티켓의 AC-0 이 그 라이브 확인을 다시 잰다.
- 형제: `TASK-PC-FE-342`(P5 SCM) · `TASK-PC-FE-343`(P6 ERP) — 같은 모양 · `TASK-PC-FE-338`(c-2 — 메뉴 단위 `serverRequires` · `writeRequires` 규칙, 이 티켓은 **데이터만** 더한다 — 계약 § 2.4.3.4 «Scales with P5–P8»).

# Goal

`TASK-MONO-794` 가 Finance 두 서비스(account · ledger)의 업무 인가를 세분했다(finance `iam-integration.md` § 업무 역할 — 정본). 콘솔의 Finance 메뉴 · 버튼 신호와 운영 개요 Finance 카드를 서버 판정과 같게 만든다.

# Scope

## In Scope

- 계약 먼저: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 — «SCM · ERP · Finance leaves» 줄에서 Finance 행을 떼어 아래 대조표로 · «Scales with P5–P8» 에 P7 완료 표시.
- `apps/console-web/src/shared/guide/permission-map.ts` Finance 행(`/finance` · `/finance/accounts` · `/ledger`, `/finance/guide` 제외 — 지금 `roles: ['FINANCE_OPERATOR']`, `:743-775`):
  - `gate.roles` = Finance 카탈로그 역할(상한) — `FINANCE_OPERATOR` 단독이 아니라 5개 업무 역할.
  - `serverRequires`(기본 조회) · `writeRequires`(변경 버튼) — 아래 대조표 대로.
- 운영 개요(`/dashboards/overview`)의 Finance 카드: 원장 조회 역할이 없는 운영자에게 `forbidden` 으로 정직하게 보이는지(지금 규칙) — 카드가 부르는 갈래(계좌 · 원장 시산표)를 AC-0 에서 재고 대조표에 적는다.
- 시험: `permission-map-drift` · 메뉴 신호 · 버튼 숨김 단위 시험.

## Out of Scope

- 서버 판정 변경(794 에서 끝났다) · E-commerce(P8 의 콘솔 후속).
- 원장 **통제 쓰기 7**(기간 개시 · 마감 · 환율 덮어쓰기 · 허용오차 · 원가흐름 설정)의 화면 — 콘솔에 호출자가 없다(`TASK-MONO-794` AC-0 § 3). 새 화면을 만들지 않는다.
- 새 신호 규칙(계약 § 2.4.3.4 의 규칙은 그대로 — 데이터만).

# 서버 판정 대조표 (`TASK-MONO-794` 기준 — AC-0 에서 다시 잰다)

| 화면 | 기본 조회가 부르는 것 | `serverRequires` | 변경 버튼 | `writeRequires` |
|---|---|---|---|---|
| `/finance` 개요 | (데이터 없이 렌더 — AC-0 에서 재확인) | — | — | — |
| `/finance/accounts` | account `GET /accounts/{id}` · `/balances` · `/transactions` | `FINANCE_ACCOUNT_READ` · `FINANCE_ACCOUNT_WRITE` | (READ-ONLY — 콘솔은 계좌 쓰기를 부르지 않는다) | — |
| `/ledger` | ledger 조회(시산표 · 기간 · 대사 · 환율 · 로트 …) | `FINANCE_LEDGER_READ` · `FINANCE_LEDGER_WRITE` · `FINANCE_LEDGER_CONTROL` | 대사 해소 · 환율 갱신 | `FINANCE_LEDGER_WRITE` (🔴 `FINANCE_LEDGER_CONTROL` 은 이 두 버튼을 열지 **않는다** — 독립) |
| 운영 개요 Finance 카드 | AC-0 에서 확정 | AC-0 | — | — |

# Acceptance Criteria

- [ ] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-794` 를 담은 AMI 가 라이브인가(굽기 기록 · `GET /api/admin/domain-roles` 가 `FINANCE_ACCOUNT_READ` 를 내는가 · 라이브 `/me/domain-access` 가 데모 운영자에게 Finance 5 역할을 내는가). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표를 서버 코드(account · ledger `SecurityConfig`)와 다시 대조.
- [x] **AC-1** — 계약 § 2.4.3.4 개정이 코드보다 먼저. → red 커밋 `ee65d15b2`.
- [x] **AC-2** — 🔴 대조군: 조회 세트(`finance-read`) → Finance 메뉴 배지 없음 · 쓰기 버튼 없음 / 계좌 세트만(`finance-account-operate`) → `/ledger` 배지 / 원장 운영 세트(`finance-ledger-operate`) → `/finance/accounts` 배지, `/ledger` 버튼 보임 / 원장 통제만 → `/ledger` 배지 없음 · 버튼 없음 / Finance 세트 0 → Finance 메뉴 전부 배지. → `console-nav-business-permission.test.ts` § TASK-PC-FE-344(+ 진입 역할 0 가드 · 통제 역할 미선언 가드) · `LedgerOpsScreen.test.tsx`(`DiscrepancyDetail` · 두 패널) · `LedgerFxRates.test.tsx`.
- [x] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음 · 버튼 보임(지금 규칙 그대로). → row 12 `undefined` → 배지 없음 · `canWriteOn` true · `canWrite` 생략 = 보임(대조군 시험).
- [x] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다. → § 32차 창 라이브.
  - 🔵 Vercel 절반 ✅ (2026-10-18 UTC 측정): 머지 커밋 `465b39a94` → `Production – kanggle-console` 배포 7130932170 state=success (2026-10-16T22:34:17Z). 🔴 라이브 화면 절반(로그인 운영자 · 세트별 배지/버튼)은 백엔드가 필요 — 다음 데모 창(`TASK-MONO-672` § Order «Still open» 10). 이 줄만으로 AC-4 를 닫지 않는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P7
- `projects/finance-platform/specs/integration/iam-integration.md` § 업무 역할 (서버 판정 정본)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그 (`/me/domain-access`)

# Edge Cases

- `FINANCE_OPERATOR` 는 `/me/domain-access` 에 **나오지 않는다**(카탈로그 밖 · auth 파생) — 신호 입력으로 쓰지 않는다.
- `FINANCE_LEDGER_CONTROL` 의 카탈로그 `access` 는 `WRITE` 다(와이어 값 셋 — `READ`/`WRITE`/`OPERATE`). «쓰기 역할이 하나라도 있으면 버튼» 같은 `access` 기반 추론을 쓰면 통제 보유자에게 대사 해소 버튼이 거짓으로 보인다 — 역할 이름으로 판정한다.
- 테넌트를 고르지 않은 운영자(기본 토큰 — 역할 0)는 Finance 전 화면이 서버 403 이다(794 — `ROLE_FINANCE_VIEWER` 제거, SCM · ERP 와 같음) — 신호도 «업무 권한 없음» 이 맞다.

# Failure Scenarios

1. 굽기 전에 머지 — 위 머지 게이트의 거짓 배지 · 버튼 숨김.
2. `FINANCE_OPERATOR` 를 `serverRequires` 에 넣는다 — 진입 역할은 업무 API 를 열지 않으므로 신호가 서버보다 넓어진다.
3. `/ledger` 의 `writeRequires` 에 `FINANCE_LEDGER_CONTROL` 을 넣는다 — 통제 보유자에게 서버가 403 할 버튼이 보인다.

# Merge gate — 28th window

- 2026-10-16 UTC — gate met: the bake carrying 792/793/794/795 was live-verified in the 28th window (2026-10-16) — may now be implemented/merged. (AMI `ami-00ef8cc1057cca59b` @ `8905d2c29`; this ticket's source `TASK-MONO-794` → `done/`; results in `TASK-MONO-672` § 28th window — harvest.)

# 구현 기록 — 2026-10-16 UTC (branch `task/pc-fe-344-finance-nav`, base `82d857680`)

## AC-0 — 오늘 거짓 배지 · 숨김이 있는가 (코디네이터 질문) · 서버 대조

**판정: 없다 — 343(ERP) 과 같은 «덜 알림» 쪽뿐.** c-2 의 Finance 3 행은 `roles: ['FINANCE_OPERATOR']` 만 있고 `serverRequires` · `writeRequires` 가 없다 → 배지 · 숨김이 생길 수 없다. row 12 는 V0058 뒤 `FINANCE_OPERATOR` 를 내지 않는다(V0059 · V0057 과 같은 «넓힌 뒤 지움»). 남은 것: 794 가 `ROLE_FINANCE_VIEWER`(구독 합성)를 걷어 Finance 역할 없는 운영자는 Finance 화면 전부 서버 `403` 인데 메뉴에 배지가 없고, 조회 세트 운영자에게 `/ledger` 쓰기 버튼이 보인다(누르면 `403`).

서버 대조(base `82d857680`, finance `iam-integration.md:102-132` 정본): account `SecurityConfig:19-22,62-63`(GET = AC_READ ∨ AC_WRITE ∨ `finance.*` ∨ `'*'`) · ledger `SecurityConfig:19-37,66-68`(GET = LG_READ ∨ LG_WRITE ∨ LG_CONTROL · 통제 쓰기 7 = CONTROL · 그 밖의 쓰기(대사 해소 · 환율 갱신 포함) = LG_WRITE — CONTROL 은 열지 않는다). ⇒ 티켓 대조표 그대로 + 두 칸 확정:
- **`/finance` 개요**(티켓의 «데이터 없이 렌더» 는 **틀렸다**): `features/finance-overview/api/overview-state.ts:156-235` 가 원장 4 조회(시산표 · 기간 · 대사 OPEN · 환율)를 **매번**, 기본 계좌가 설정돼 있으면 계좌 2 조회를 한다. `:280-286` — 어느 한 갈래라도 `403` 이면 **화면 전체** `forbidden`. ⇒ `serverRequires` = 원장 3 역할(항상 부르는 갈래). 🔵 원장만 가진 운영자 + 기본 계좌 설정 → 배지 없음 · 화면 `forbidden`(덜 알림 — 안전 쪽). 갈래별 저하로 바꾸는 것은 화면 행동 변경이라 이 티켓 밖 — ⚪ 기록.
- **운영 개요(`/dashboards/overview`) Finance 카드**(티켓의 «원장 조회 역할이 없는 운영자에게 forbidden» 은 **틀렸다**): `shared/composition/console-composition.ts:341-348` 는 기본 계좌의 `GET /accounts/{id}/balances` 하나를 부른다 → 필요한 것은 **계좌** 역할(`AC_READ ∨ AC_WRITE`). 기본 계좌가 없으면 카드는 호출 없이 `forbidden · MISSING_PREREQUISITE`. 콘솔 메뉴 신호의 대상이 아니므로 데이터 변경 없음 — ⚪ 라이브(다음 창: 계좌 세트 없는 운영자의 카드 = `forbidden`).

## 범위 — 버튼은 이 PR 에서 배선 (분리 안 함)

`/ledger` 의 쓰기 버튼은 둘뿐이다: 대사 해소(`DiscrepancyDetail` «해소») · FX «새로고침»(`FxRatesTable` — `POST /fx-rates/refresh`, 이름은 새로고침이지만 쓰기). 두 패널(`ReconciliationPanel` · `FxRatesPanel`)을 지나는 배선 하나라 345 · 343 처럼 나눌 크기가 아니다. 🔴 판정은 역할 **이름**(`FINANCE_LEDGER_WRITE`) — 카탈로그 `access`(CONTROL 도 `WRITE`)로 추론하지 않는다(Edge Case 2).

## Red — 커밋 `ee65d15b2` (계약 · 시험 · 타입 전용 선택 props · 티켓만)

- 🔵 «시험만» 의 예외: tsc 를 통과시키려고 `canWrite?` 를 `DiscrepancyDetail` · `FxRatesTable` · `LedgerOpsScreenProps` 에 **선언만** 했다(읽지 않음 — 행동 불변).
- 로컬 전체: **11 failed | 4611 passed (4622)** — 전부 `AssertionError`(`console-nav-business-permission` 7 · `LedgerOpsScreen` 3 · `LedgerFxRates` 1). `tsc` rc=0.
- CI run `38973227139`, `Frontend unit tests (… console-web, vitest)` job **`117216438725`**: console-web **11 failed | 4611 passed (4622)** · 같은 3 파일 · 11 건 전부 `AssertionError`(로그에서 셈).
- 🔵 버린 시험 하나: `LedgerOpsScreen` 을 그려 FX 탭으로 가는 화면 단위 시험 — jsdom 에서 FX 질의는 `success` 인데 `FxRatesTable` 이 그려지지 않아(이 변경 전 코드에서도 같음, 원인은 쫓지 않음) **대조군까지 빨갛다** → 화면 단위 대신 두 패널을 직접 그리는 시험 + 패널의 `canWrite` 를 **필수 prop** 으로 만들어 화면이 빠뜨리면 tsc 가 빨개지게 했다.

## Green

- `permission-map.ts`: `FINANCE_ROLES`(5) · `FINANCE_GET` · `FINANCE_LEDGER_BUSINESS_WRITE` · 3 행 `roles` = 5 · `serverRequires`(개요 · 원장 = 원장 3 · 계좌 = 계좌 2) · `/ledger` `writeRequires` = `FINANCE_LEDGER_WRITE`.
- 배선: `ledger/page.tsx` → `canWriteOn('/ledger', getDomainBusinessRoles({ finance }))` → `LedgerOpsScreen.canWrite` → `ReconciliationPanel.canWrite`(필수) → `DiscrepancyDetail`(«해소» 숨김 + «조회 전용» 문구) · `FxRatesPanel.canWrite`(필수) → `FxRatesTable`(«새로고침» 숨김).
- 로컬: `npx vitest run` **399 files / 4622 passed** · `tsc` rc=0 · `next lint` rc=0(경고 1 = 기존 `received-invitations/route.ts:26`). e2e · smoke · federation 의 `ledger-fx-rates-refresh` · `ledger-recon-resolve` · `nav-finance` 사용처 0(grep).
- CI 커밋 `cf56865bd` run `38974262414`: `Frontend unit tests (… console-web, vitest)` job **`117219861462`** — **399 files / 4622 passed**(로그에서 셈) · 실행된 검사 14 전부 pass · 52 skipped.
- nightly(브랜치 dispatch) run **`38974258717`** = success(15 job) · 콘솔 job **`117220069959`**: **12 passed · 0 flaky · 0 failed**.
- ⚪ AC-4: `main` 미머지(DRAFT) · Vercel 미배포 · 라이브 화면 미확인. 백엔드 변경 없음 — 다음 창: `finance-read` 운영자의 `/ledger` 에 «해소» · «새로고침» 없음 · 원장 통제만 준 운영자는 `/ledger` 배지 없음 + 버튼 없음 · 운영 개요 Finance 카드(계좌 역할 없는 운영자 = `forbidden`).
- ⚪ 따로 남는 것: `/finance` 개요의 «한 갈래 403 = 화면 전체 forbidden»(`overview-state.ts:280-286`) — 794 뒤 원장만 가진 운영자가 기본 계좌 때문에 원장 타일까지 잃는다. 화면 행동 변경이라 이 티켓 밖 — 필요하면 후속 티켓.

## 함께 적는 것

- 가이드 문안 후속 `TASK-PC-FE-348`(ready) 을 이 PR 에서 기안 — 코디네이터 지시(SCM · E-commerce · IAM · WMS 가이드의 «`*_OPERATOR` 하나 · 자동 부여» 문장).
- `TASK-PC-FE-341` 에 `overview-consolidation.spec.ts:133` 첫 시도 flaky 증거 한 줄 추가(343 브랜치 nightly run 38965748396 / job 117192025297 — 원인 가설은 미확정).

## 32차 창 라이브 (2026-10-20 UTC · `console` + 콘솔 묶음 다섯 · 소유자 브라우저 + 에이전트 세트 전환)

주체 `requester@demo.com`(`demo-corp`)의 세트를 한 조합씩 바꿨다(에이전트 `swap.sh` — 스냅샷 = `<domain>-full` 다섯 DIRECT, 끝에 복원 → assume 역할 42 개 스냅샷과 동일 · PASS 9 FAIL 0). 비교 기준 `demo@demo.com`(`legacy-compat`).
- 배포: Vercel `7130932170`(`465b39a94`) success(10-18 기록).
- `/ledger` FX «새로고침»: demo@ **보임** → `finance-read` **없음** → `finance-ledger-operate`(`FINANCE_LEDGER_WRITE`) **다시 보임** — 양방향 ✅. `finance-ledger-operate` 에서 «계좌» 메뉴 배지 ✅(계좌 역할 없음).
- ⚪ «해소»: OPEN 대사 차이 행이 없어 판정 불가. ⚪ CONTROL 단독 세트는 프리셋에 없어(`admin-api.md:3840`) 안 쟀다.
- 🔴 `in-progress` 유지 — AC-0 체크박스가 비어 있다.
