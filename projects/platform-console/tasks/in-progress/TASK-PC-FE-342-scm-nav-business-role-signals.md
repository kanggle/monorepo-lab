# Task ID

TASK-PC-FE-342

# Title

`ADR-MONO-083` P5 콘솔 후속 — SCM 메뉴 «업무 권한 없음» · 쓰기 버튼을 SCM 세분 역할로 (`permission-map.ts` `serverRequires` / `writeRequires` · 계약 § 2.4.3.4) · WMS 출고 «발송 재시도» 버튼은 `SCM_LOGISTICS_WRITE`

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (데이터 행 · 계약 표 — 규칙 신설 없음)
> ⏳ **머지 게이트 — «`TASK-MONO-792` 를 실은 AMI 굽기가 라이브 확인된 뒤에만 머지»** (소유자 결정 2026-10-15 UTC, `TASK-MONO-792` § 소유자 결정 «순서»). 콘솔은 머지 즉시 Vercel 로 나가고 백엔드는 굽기에서만 나간다. 굽기 전에 머지하면 라이브 `GET /api/admin/me/domain-access` 가 아직 `SCM_OPERATOR` 만 내므로 **모든 운영자의 SCM 메뉴에 거짓 «업무 권한 없음» + 승인 · 기각 · 정책/매핑 저장 버튼 숨김** — 서버는 열어 주는데 화면이 닫는다. 그 반대(굽기 뒤 · 이 PR 전)는 조회 세트 운영자에게 쓰기 버튼이 보이고 누르면 서버 403(화면 안 «권한 없음») — 안전 쪽 실패.

# Dependency Markers

- **선행**: `TASK-MONO-792`(머지 + 그 커밋을 담은 굽기 + 라이브 확인 — `TASK-MONO-672` 다음 창 목록의 792 항목). 이 티켓의 AC-0 이 그 라이브 확인을 다시 잰다.
- 형제: `TASK-PC-FE-338`(c-2 — 메뉴 단위 `serverRequires` · `writeRequires` 규칙을 만든 티켓, 이 티켓은 **데이터만** 더한다 — 계약 § 2.4.3.4 «Scales with P5–P8»).

# Goal

`TASK-MONO-792` 이 SCM 4 서비스에 세분 서버 인가를 넣었다(scm `iam-integration.md` § 업무 역할). 콘솔의 SCM 메뉴 · 버튼 신호를 서버 판정과 같게 만든다 — 계약이 «그 단계가 `serverRequires`(·`writeRequires`) 를 더한다 — 데이터만» 이라고 미뤄 둔 몫.

# Scope

## In Scope

- 계약 먼저: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4
  - `:281` «발송 재시도 … `authenticated()` only. It carries no requirement and stays visible» → `SCM_LOGISTICS_WRITE` 를 요구, 없으면 숨김(WMS 출고 화면 — 소유자 결정 Q3 «엄격»).
  - `:304` «SCM · ERP · Finance leaves | — | SCM services are `authenticated()` only» → SCM 행을 떼어 아래 표로.
  - `:307` «Scales with P5–P8» 문장에 P5 완료 표시.
- `apps/console-web/src/shared/guide/permission-map.ts` SCM 행(`/scm` · `/scm/procurement` · `/scm/inventory` · `/scm/replenishment` · `/scm/config`, `/scm/guide` 제외):
  - `gate.roles` = SCM 카탈로그 역할(상한) — `SCM_OPERATOR` 단독이 아니라 8개 업무 역할.
  - `serverRequires`(기본 조회) · `writeRequires`(변경 버튼) — 아래 «서버 판정 대조표» 대로.
- WMS 출고 화면의 «발송 재시도» 버튼: `writeRequires: ['SCM_LOGISTICS_WRITE']` 에 해당하는 숨김(배차 조회 칸은 `SCM_LOGISTICS_READ ∨ _WRITE` 가 없으면 화면 안 «권한 없음» — 숨기지 않는다).
- 시험: `permission-map-drift` · 메뉴 신호 · 버튼 숨김 단위 시험 — 조회 세트 → 쓰기 버튼 없음 / 수요계획 세트만 → `/scm/procurement` 배지 · `/scm/replenishment` 승인 버튼 보임.

## Out of Scope

- 서버 판정 변경(792 에서 끝났다) · ERP · Finance · E-commerce(P6 ~ P8 의 콘솔 후속).
- 새 신호 규칙(계약 § 2.4.3.4 의 규칙은 그대로 — 데이터만).

# 서버 판정 대조표 (`TASK-MONO-792` 기준 — AC-0 에서 다시 잰다)

| 화면 | 기본 조회가 부르는 것 | `serverRequires` | 변경 버튼 | `writeRequires` |
|---|---|---|---|---|
| `/scm` 개요 | procurement PO · inventory-visibility snapshot | 둘 중 하나라도 — `SCM_PROCUREMENT_READ/WRITE` · `SCM_INVENTORY_VISIBILITY_READ/WRITE` (카드별 저하는 지금 규칙) | — | — |
| `/scm/procurement` | `GET /api/v1/procurement/po` | `SCM_PROCUREMENT_READ` · `SCM_PROCUREMENT_WRITE` | (READ-ONLY 화면) | — |
| `/scm/inventory` | `GET /api/v1/inventory-visibility/snapshot` | `SCM_INVENTORY_VISIBILITY_READ` · `_WRITE` | (READ-ONLY) | — |
| `/scm/replenishment` | `GET /api/v1/demand-planning/suggestions` | `SCM_DEMAND_PLANNING_READ` · `_WRITE` | 승인 · 기각 | `SCM_DEMAND_PLANNING_WRITE` (승인의 발주 초안 = from-suggestion 이 이 역할을 받는다, Q2) |
| `/scm/config` | `GET …/policies` · `…/sku-supplier-map` | `SCM_DEMAND_PLANNING_READ` · `_WRITE` | 정책 · 매핑 저장 | `SCM_DEMAND_PLANNING_WRITE` — 🔵 공급사 선택 목록은 발주 조회(`GET /procurement/suppliers`)라 수요계획 세트만이면 그 칸만 «권한 없음» |
| `/wms/outbound` «발송 재시도» | `GET /api/v1/logistics/dispatches/by-shipment/{id}` | (화면 전체 = 출고 — 지금 행 유지) | 발송 재시도 | `SCM_LOGISTICS_WRITE` |

# Acceptance Criteria

- [ ] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-792` 를 담은 AMI 가 라이브인가(굽기 기록 · `GET /api/admin/domain-roles` 가 `SCM_PROCUREMENT_READ` 를 내는가 · 라이브 `/me/domain-access` 가 데모 운영자에게 SCM 8 역할을 내는가). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표를 서버 코드(`SecurityConfig` 4개)와 다시 대조.
- [x] **AC-1** — 계약 § 2.4.3.4 개정(`:281` · `:304` · `:307`) 이 코드보다 먼저. → red 커밋 `f17be4ff5` 에 계약 · 시험만(+ 중립 배관), 데이터는 green.
- [x] **AC-2** — 🔴 대조군: 조회 세트(`scm-read`) → SCM 메뉴 배지 없음 · 승인/기각/저장 버튼 없음 / 수요계획 세트만 → `/scm/procurement` · `/scm/inventory` 배지, `/scm/replenishment` 버튼 보임 / SCM 세트 0 → SCM 메뉴 전부 배지 / WMS 출고 세트만 → 출고 화면의 «발송 재시도» 없음. → `console-nav-business-permission.test.ts` · `sidebar-business-permission.test.tsx` · `ReplenishmentScreen` · `SeedConfigScreen` · `OutboundOpsScreen` · `domain-business-roles.test.ts` (§ 구현 기록).
- [x] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음 · 버튼 보임(지금 규칙 그대로). → fail-open 시험(행 12 `undefined` · 레지스트리 `null` · 샘플 방문자 · 테넌트 없음).
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면) 를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P5
- `projects/scm-platform/specs/integration/iam-integration.md` § 업무 역할 (서버 판정 정본)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그 (row 12 `/me/domain-access`)

# Edge Cases

- `SCM_OPERATOR` 는 `/me/domain-access` 에 **나오지 않는다**(카탈로그 밖 · auth 파생) — 신호 입력으로 쓰지 않는다.
- 개요 카드 둘은 서로 다른 서비스 — 한쪽 역할만 있으면 그 카드만 «권한 없음»(카드 단위 저하는 지금 규칙).

# Failure Scenarios

1. 굽기 전에 머지 — 위 머지 게이트의 거짓 배지 · 버튼 숨김.
2. `SCM_OPERATOR` 를 `serverRequires` 에 넣는다 — 진입 역할은 업무 API 를 열지 않으므로 신호가 서버보다 넓어진다.
3. 발송 재시도 버튼을 WMS 역할로 판정 — 서버(물류)는 SCM 역할만 본다(Q3).

# Merge gate — 28th window

- 2026-10-16 UTC — gate met: the bake carrying 792/793/794/795 was live-verified in the 28th window (2026-10-16) — may now be implemented/merged. (AMI `ami-00ef8cc1057cca59b` @ `8905d2c29`; this ticket's source `TASK-MONO-792` → `done/`; results in `TASK-MONO-672` § 28th window — harvest.)

# 구현 기록 — 2026-10-16 UTC (branch `task/pc-fe-342-scm-nav`, PR #4396)

## AC-0 — 게이트 · 서버 대조

- 머지 게이트: 위 절(28차 창 라이브 — `TASK-MONO-792` § 배포 상태 — 28차 창: `demo@demo.com` assume `demo-corp` → `SCM_OPERATOR`(파생) + SCM 8 역할 · `scm-read` → GET 200 · PO 생성/정책 PUT 403 · 출고 세트만 → `:retry` 403). 🔵 이 에이전트는 라이브(`/me/domain-access` · `/domain-roles`)를 **다시 재지 않았다** — 데모 창 밖. 근거는 792 의 28차 창 기록.
- 서버 코드 대조(base `9c780001d`): procurement `SecurityConfig.java:35-37,59-61`(GET = READ ∨ WRITE · `from-suggestion` = PROCUREMENT_WRITE ∨ DEMAND_PLANNING_WRITE · 그 밖 = PROCUREMENT_WRITE) · demand-planning `:25,38-39` · logistics `:24,38-39`(GET = READ ∨ WRITE · retry = WRITE) · inventory-visibility `:28-29,48-49`. 카탈로그 `DomainRoleCatalog.java:52-59` = SCM 8, `SCM_OPERATOR` 없음. ⇒ 대조표 그대로, 차이 없음.

## 설계 판단 (데이터 밖으로 나간 두 곳)

1. **버튼 단위 요구(`buttonWriteRequires`)** — «발송 재시도» 는 `/wms/outbound` 화면의 버튼인데 서버가 다르다(scm logistics). 화면 단위 `writeRequires` 하나로는 `OUTBOUND_WRITE` 와 `SCM_LOGISTICS_WRITE` 를 함께 표현할 수 없어서 게이트에 버튼 키 하나(`'retry-dispatch'`)로 덮어쓰는 칸을 더했고, 계약 § 2.4.3.4 에 그 규칙 한 줄을 적었다(«a button whose server differs …»). 규칙 자체(보유 ∩ 요구 = ∅ 이면 숨김 · fail-open)는 그대로.
2. **역할 입력의 구독 축** — 출고 화면의 입력은 지금까지 row 12 ∩ **wms** 구독이라, 같은 운영자의 `SCM_LOGISTICS_WRITE`(domainKey scm)가 입력에서 떨어져 버튼이 **서버가 열어 주는 운영자에게도** 숨는다. 출고 페이지는 wms + scm 구독을 함께 넘긴다 — `getWmsBusinessRoles` 를 `app/(console)/_lib/domain-business-roles.ts` `getDomainBusinessRoles({ wms, scm })` 로 일반화(WMS 래퍼는 남김). 대조군 시험이 이 낙차를 고정한다(`domain-business-roles.test.ts`).

## Red — 커밋 `f17be4ff5` (시험 + 행동 중립 배관만)

- 🔵 «시험만» 의 예외: tsc 를 통과시키고 실패를 단언으로만 남기려고 **행동이 바뀌지 않는 배관**을 함께 넣었다 — 게이트 타입 칸 `buttonWriteRequires` · `canWriteOn(href, roles, button?)`(데이터가 없으면 화면 요구로 폴백 = 이전과 같음) · `getDomainBusinessRoles`(호출처 없음) · 새 선택 props(`canRetry` · `canWrite`) 선언만. 데이터와 배선은 green 커밋.
- 로컬 대상 6 파일: **17 failed | 111 passed (128)** — 전부 `AssertionError`(처음 3 건의 `getByTestId` 실패는 `queryByTestId(...).not.toBeNull()` 로 바꿔 단언 실패로 맞췄다).
- 로컬 전체: 18 failed | 4559 passed (4577) — 17 = 위 + 1 = `LedgerOpsScreen.test.tsx` «분개 tab» (`ledger-entry-input` 미발견, 이 변경과 무관 — 전체 병렬 실행의 흔들림, green 전체 실행에서 통과).
- CI `Frontend unit tests (… console-web, vitest)` job **`117145942178`**(run `38952128147`): console-web **17 failed | 4560 passed (4577)** · 실패 파일 6 · 실패 17 건 전부 `AssertionError`(로그 «Failed Tests 17» 블록에서 셈). tsc · lint 단계는 시험 단계 실패로 실행 안 됨(로컬 tsc rc=0).

## Green — 데이터 + 배선

- `permission-map.ts`: SCM 5행 `roles` = SCM 8(카탈로그) · `serverRequires`(대조표) · `/scm/replenishment` · `/scm/config` `writeRequires` = `SCM_DEMAND_PLANNING_WRITE` · `/wms/outbound` `buttonWriteRequires['retry-dispatch']` = `SCM_LOGISTICS_WRITE` · 출처 = SecurityConfig 4개 줄.
- 화면: 보충 계획 — `canWrite=false` 면 «작업» 열(승인 · 기각) 통째로 빠지고 «조회 전용» 문구 · 설정 — 두 폼의 저장 버튼 숨김 · «조회 전용» 문구(SKU 조회와 현재 값은 남는다) · 출고 — `canRetry=false` 면 «발송 재시도» 와 그 안내 숨김. 페이지 셋이 `canWriteOn(…)` 으로 계산해 넘긴다.
- 로컬: `npx vitest run` **399 files / 4577 passed, 0 failed** · `npx tsc --noEmit -p .` rc=0 · `next lint` rc=0(경고 1 = 기존 `received-invitations/route.ts:26` `_req`, 이 변경 밖).
- CI 커밋 `18c73c77f` run `38953944252`: `Frontend unit tests (… console-web, vitest)` job **`117152057908`** — console-web **399 files / 4577 passed**(로그에서 셈) · 같은 job 의 `npx tsc --noEmit` · `next lint` 단계 실행 · job pass. 실행된 검사 14 전부 pass(필수 4 포함) · 52 skipped.
- nightly `nightly-e2e.yml` (브랜치 dispatch) run **`38953962267`** = success · 콘솔 job `Platform Console E2E full-stack` **`117152378521`**: **12 passed · 0 flaky · 0 failed**(로그 요약 줄) — 알려진 개요 TIMEOUT flake(`TASK-PC-FE-341`) 이번 런엔 없음. 다른 14 job 전부 success.

## ⚪ 남은 것

- AC-4 배포 상태: `main` 미머지(DRAFT) · Vercel 미배포 · 라이브 화면 미확인 — 머지 후 다음 창.
- 🔴 이 티켓 범위 밖이지만 같은 원인으로 **지금 틀린 문장**: SCM 가이드 `features/scm-guide/data.ts:363-369`(«SCM 운영자(SCM_OPERATOR) 권한 … 이 권한 하나뿐») · IAM 가이드 `features/iam-guide/data.ts:139,683`(도메인 역할 목록에 `SCM_OPERATOR`). 792 뒤로 거짓 — 가이드 문안 후속으로 남긴다(고치지 않음).
