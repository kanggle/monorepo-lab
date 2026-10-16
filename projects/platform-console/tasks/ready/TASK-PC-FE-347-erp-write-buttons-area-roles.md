# Task ID

TASK-PC-FE-347

# Title

`ADR-MONO-083` P6 콘솔 후속 (2) — ERP 쓰기 버튼을 `ERP_MASTERDATA_WRITE` / `ERP_APPROVAL_WRITE` 로 숨김 (`permission-map.ts` `writeRequires` · 계약 § 2.4.3.4 «Read-only sets hide write buttons»)

# Status

ready

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

- [ ] **AC-0** — 3 화면의 쓰기 버튼 전수(컴포넌트 · testid)와 위 표를 서버(masterdata `RoleScopeAuthorizationAdapter` · approval `ActorContext`)와 대조. e2e · smoke · federation 의 해당 testid 사용처 grep.
- [ ] **AC-1** — 계약 개정이 코드보다 먼저.
- [ ] **AC-2** — 🔴 대조군: `erp-read` → 3 화면 쓰기 버튼 없음 + «조회 전용» / 결재 세트만 → `/erp/approval` 버튼 보임 · `/erp/masters` 버튼 없음 / 기준정보 세트만 → `/erp/masters` 버튼 보임.
- [ ] **AC-3** — fail-open: row 12 실패 · 테넌트 없음 · 샘플 방문자 → 버튼 보임.
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다.

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
