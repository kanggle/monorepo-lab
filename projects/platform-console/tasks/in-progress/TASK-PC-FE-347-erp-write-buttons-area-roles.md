# Task ID

TASK-PC-FE-347

# Title

`ADR-MONO-083` P6 콘솔 후속 (2) — ERP 쓰기 버튼을 `ERP_MASTERDATA_WRITE` / `ERP_APPROVAL_WRITE` 로 숨김 (`permission-map.ts` `writeRequires` · 계약 § 2.4.3.4 «Read-only sets hide write buttons»)

# Status

in-progress

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (규칙 · 데이터는 있다 — 3 화면 배선 + 시험)
> 기안 2026-10-16 UTC — `TASK-PC-FE-343` 에서 분리(그 티켓 § 범위 결정, `TASK-PC-FE-345` → `346` 과 같은 이유). 서버는 이미 라이브(`TASK-MONO-793`, 28차 창) — 머지 게이트 없음.

# Dependency Markers

- **선행**: `TASK-PC-FE-343`(ERP 메뉴 `serverRequires` · 알림 갈래 — 머지 뒤 착수, 같은 `permission-map.ts` 행을 고친다).
- 같은 모양: `TASK-PC-FE-342`(SCM 화면 `canWrite` · «조회 전용») · `TASK-PC-FE-346`(E-commerce 버튼).

# Goal

`TASK-PC-FE-343` 은 ERP 메뉴 신호와 알림 종만 고쳤다. 조회 세트(`erp-read`) · 결재 세트만 · 기준정보 세트만 가진 운영자에게 다른 영역의 쓰기 버튼이 보이고, 누르면 서버 `403` 이다. 계약 규칙대로 화면별 `writeRequires` 를 선언하고 버튼을 숨긴다.

# Scope

## In Scope

- 계약 먼저: § 2.4.3.4 «Declared today» 표에 ERP 3 행 · «Scales with P5–P8» 의 P6 문장에서 «버튼 미선언» 을 지운다.
- `permission-map.ts` ERP 행 `writeRequires`:

  | 화면 | 버튼(컴포넌트) | `writeRequires` |
  |---|---|---|
  | `/erp/masters` | 등록 · 수정 · 폐기 · 부서 이동 · 계정 연결 제안/철회 (`DepartmentList` · `EmployeeList` · `JobGradeList` · `CostCenterList` · `BusinessPartnerList` · `MasterWriteDialog` · `DepartmentWriteDialog` · `EmployeeAccountLinkDialog`) | `ERP_MASTERDATA_WRITE` |
  | `/erp/approval` | 상신 · 승인 · 반려 · 회수 (`ApprovalScreen` · `ApprovalCreateDialog` · `ApprovalDetail`) | `ERP_APPROVAL_WRITE` |
  | `/erp/delegation` | 위임 등록 · 회수 (`DelegationScreen` · `DelegationGrantList`) | `ERP_APPROVAL_WRITE` |

- 각 화면이 `canWrite` 를 받아 버튼을 숨기고 «조회 전용» 문구를 보인다. 페이지는 `getDomainBusinessRoles({ erp })` + `canWriteOn(href, roles)`.
- 🔵 계정 연결 수락 · 거절(`MyAccountLinkProposalsCard` — 개인 엔드포인트, ERP 업무 역할 하나라도)은 **숨기지 않는다** — 그 화면을 연 운영자는 이미 ERP 역할이 있다.

## Out of Scope

- 메뉴 신호 · 알림 종(`TASK-PC-FE-343`) · 서버 판정(793) · 결재 자격(직무 분리 · 단계 승인자 — 역할과 별개, 서버 집합체 가드).

# Acceptance Criteria

- [x] **AC-0** — 3 화면의 쓰기 버튼 전수(컴포넌트 · testid)와 위 표를 서버(masterdata `RoleScopeAuthorizationAdapter` · approval `ActorContext`)와 대조. e2e · smoke · federation 의 해당 testid 사용처 grep. ✅ 2026-10-18 UTC — 아래 «AC-0 record»: 표와 서버가 모든 컨트롤에서 일치, e2e · smoke · federation · nightly 사용처 0건.
- [x] **AC-1** — 계약 개정이 코드보다 먼저. ✅ § 2.4.3.4 «Declared today» ERP 3 행 + «Scales with P5–P8» P6 문장 «done» — red 커밋 `27c7fa4f7` 이 계약을 포함하고 `permission-map.ts` 데이터는 그 뒤 green 커밋.
- [x] **AC-2** — 🔴 대조군: `erp-read` → 3 화면 쓰기 버튼 없음 + «조회 전용» / 결재 세트만 → `/erp/approval` 버튼 보임 · `/erp/masters` 버튼 없음 / 기준정보 세트만 → `/erp/masters` 버튼 보임. ✅ `erp-write-buttons.test.tsx`(화면: masters 17 컨트롤 · 결재 작성 + 상세 4 전이 · 위임 생성/회수) · `erp-write-buttons-pages.test.tsx`(실제 `resolveErpCanWrite → getDomainBusinessRoles → canWriteOn` 사슬: erp-read / 결재 세트 / 기준정보 세트 / 데모 운영자 / 진입 역할 단독 / 타 도메인 역할) · `console-nav-business-permission.test.ts`(행). red 46 실패 → green 0.
- [x] **AC-3** — fail-open: row 12 실패 · 테넌트 없음 · 샘플 방문자 → 버튼 보임. ✅ 3 화면 × (row 12 실패 · 테넌트 없음 · 샘플 방문자 · 레지스트리 tenants 미상) 12 케이스 + `canWriteOn(href, undefined)`.
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다. ⏳ 배포 상태 — 머지 후 기록.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P6
- `projects/erp-platform/specs/integration/iam-integration.md` § 업무 역할

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4

# Edge Cases

- 결재 승인 버튼은 역할이 있어도 자격(단계 승인자)이 없으면 서버가 거절 — 역할 숨김과 별개로 지금의 inline 오류 매핑 유지.
- 데모 운영자(legacy-compat)는 ERP 4 역할 전부 — 데모 · nightly e2e 의 버튼은 그대로 보여야 한다.

# Failure Scenarios

1. 진입 역할(`ERP_OPERATOR`)을 `writeRequires` 에 넣는다 — row 12 에 없어 모든 운영자의 버튼이 숨는다(345 의 가드가 막는다).
2. 결재 대상 확인(`GET /departments/{id}` 등 — `ERP_APPROVAL_WRITE` 도 받는다)을 기준정보 역할로 숨긴다 — 결재자가 대상 표시를 잃는다.
3. 선언만 하고 배선을 빠뜨린 화면 — 계약이 숨는다고 말하는 버튼이 보인다.

---

# AC-0 record (2026-10-18 UTC) — write-control inventory vs server

Server evidence: masterdata `RoleScopeAuthorizationAdapter.java:75-101` (`case WRITE -> actor.hasRole(ERP_MASTERDATA_WRITE) || actor.hasScope(erp.write)`, `:96`; `SUBJECT_READ` `:90`, `SELF` `:93-94`), `RequiredScope.java:9-25`, `MasterdataApplicationService.java`; approval `ActorContext.java:58-60` (`canWriteErp()` = `ERP_APPROVAL_WRITE ∨ erp.write`), `ApprovalApplicationService.java:331-340` (`authorizeWrite`: createDraft :90 · submit :134 · approve :195 · reject :228 · withdraw :251), `DelegationApplicationService.java:170-177` (`authorizeWrite`: createDelegation :56 · revokeDelegation :85).

| Screen | Control(s) (component) | testid | Role | Server |
|---|---|---|---|---|
| `/erp/masters` 부서 | 부서 추가 · 수정 · 이동 · 폐기 (`DepartmentList` → `DepartmentWriteDialog`) | `erp-department-create` · `-edit-N` · `-move-N` · `-retire-N` (dialog `erp-dept-write-*`) | `ERP_MASTERDATA_WRITE` | `MasterdataApplicationService` :130 :162 :182 :210 `authorize(WRITE)` |
| 직원 | 직원 추가 · 수정 · 폐기 · 계정 연결 (`EmployeeList` → `MasterWriteDialog` · `EmployeeAccountLinkDialog`) | `erp-employee-create` · `-edit-N` · `-retire-N` · `-link-N` (dialog `erp-account-link-*`: `-revoke` · `-unlink` · lookup/propose) | `ERP_MASTERDATA_WRITE` | :268 :298 :327 (employee CRUD) · :396-402 `proposeAccountLink` WRITE · :525-533 `revokeAccountLink` WRITE · :548-557 `unlinkAccount` SELF + WRITE unless the account owner |
| 직급 | `JobGradeList` | `erp-jobgrade-create` · `-edit-N` · `-retire-N` | `ERP_MASTERDATA_WRITE` | :586 :611 :629 |
| 비용센터 | `CostCenterList` | `erp-costcenter-create` · `-edit-N` · `-retire-N` | `ERP_MASTERDATA_WRITE` | :670 :696 :718 |
| 거래처 | `BusinessPartnerList` | `erp-businesspartner-create` · `-edit-N` · `-retire-N` | `ERP_MASTERDATA_WRITE` | :759 :788 :814 |
| `/erp/approval` | 결재 요청 작성 (`ApprovalScreen` → `ApprovalCreateDialog`) | `approval-create` | `ERP_APPROVAL_WRITE` | `createDraft` :90 |
| | 상신 · 승인 · 반려 · 회수 (`ApprovalDetail`; reject/withdraw → reason dialog) | `approval-action-submit` · `-approve` · `-reject` · `-withdraw` | `ERP_APPROVAL_WRITE` | :134 :195 :228 :251 |
| `/erp/delegation` | 위임 생성 (`DelegationScreen` → create dialog) | `delegation-create` | `ERP_APPROVAL_WRITE` | `createDelegation` :56 |
| | 위임 회수 (`DelegationGrantList` → revoke dialog) | `delegation-revoke-<id>` | `ERP_APPROVAL_WRITE` | `revokeDelegation` :85 |

**Result: the ticket table and the server agree on every control.** Deviations / notes (none changes a role):

1. **Not hidden, on purpose (ticket 🔵):** `MyAccountLinkProposalsCard` accept / decline (`RequiredScope.SELF`, `MasterdataApplicationService` :443 :470 :512). It lives on the `/erp` overview, not on the three screens.
2. **Not gated by the masterdata role (Failure Scenario 2):** the approval-target lookups `GET /departments/{id}` · `/employees/{id}` = `SUBJECT_READ` (:248 :345), which `ERP_APPROVAL_WRITE` also opens (`RoleScopeAuthorizationAdapter` :90). They are reads; the gate touches none of them (pinned in `erp-write-buttons.test.tsx`).
3. **Account owner self-unlink:** the server lets the linked account's owner unlink without `ERP_MASTERDATA_WRITE` (`unlinkAccount` :553-557). The console only reaches that action through the per-row 「계정 연결」 dialog, which is an HR/masterdata control hidden with the rest of the list, so a self-unlink path is not lost (it has no console entry without the masters list anyway).
4. The old `mastersWritable` page prop (hard-coded `true`) is replaced by the computed `canWrite`; the masters screen's per-list `writable` props already hide every control and dialog, so the masters change is wiring + the note.

## e2e / smoke / federation / nightly usages of these testids

Searched repo-wide (`tests/e2e`, `e2e-smoke`, federation, `.github`, `scripts`, `infra`, `**/*.spec.ts`) for every testid above and the `/erp/{masters,approval,delegation}` routes: **no e2e / smoke / federation / nightly spec references any ERP write testid.** The console e2e (`overview-consolidation.spec.ts`, `operators-admin-profile.spec.ts`) never touches these screens' buttons. Other hits: `infra/demo/seed/seed-erp.sh` and `infra/demo/aws/site/capture-shots.mjs` call the ERP **APIs / take screenshots** only, `scripts/check-capture-route-staleness.mjs` pins route existence only. All testid usages are unit tests under `tests/unit/features/erp-ops/` and `tests/unit/erp-master-ref-names.test.tsx` that render the components directly.

# Implementation record (2026-10-18 UTC)

- `permission-map.ts`: `/erp/masters` `writeRequires: [ERP_MASTERDATA_WRITE]`, `/erp/approval` and `/erp/delegation` `[ERP_APPROVAL_WRITE]`. `/erp`, `/erp/orgview` and the guide declare none. No entry role.
- Pages compute `canWrite` via `app/(console)/erp/_write-access.ts` `resolveErpCanWrite` (`ErpEligibility.tenants`; `null` on a degraded/failed registry => fail-open). `canWrite` is a REQUIRED prop on `ErpMastersScreen` (replaces the hard-coded `mastersWritable`), `ErpApprovalScreen`, `ErpDelegationScreen`, `ApprovalScreen`, `ApprovalDetail`, `DelegationScreen`. Notes (`role="note"`): `masters-read-only-note` · `approval-read-only-note` · `approval-detail-read-only-note` · `delegation-read-only-note`. With `canWrite=false` the approval detail shows neither the buttons nor «완료된 결재입니다» (that sentence is for terminal states only).
- Own `ReadOnlyNote` in `features/erp-ops` (features may not import each other, so 346's is not shared).
- Open doubt (not changed): the 「위임 생성」 dialog picks the delegate from the masterdata employee list (`GET /employees` = masterdata READ), which an approval-only operator (`ERP_APPROVAL_WRITE` without a masterdata role) does not hold — the dialog appears to fall back to its manual-id input (delegateId-fallback testid; not verified live). Out of scope here; a follow-up may decide whether to expose a SELF-class lookup.
- Gates: vitest 403 files / 4823 tests rc=0 · tsc --noEmit rc=0 · next lint rc=0.
