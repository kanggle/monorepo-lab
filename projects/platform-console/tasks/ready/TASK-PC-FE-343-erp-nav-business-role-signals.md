# Task ID

TASK-PC-FE-343

# Title

`ADR-MONO-083` P6 콘솔 후속 — ERP 메뉴 «업무 권한 없음» · 쓰기 버튼을 ERP 세분 역할로 (`permission-map.ts` `serverRequires` / `writeRequires` · 계약 § 2.4.3.4) · 알림 종이 ERP 역할 없는 사용자에게 ERP 갈래를 부르지 않기

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (데이터 행 · 계약 표 · 알림 집계의 갈래 선택 하나 — 규칙 신설 없음)
> ⏳ **머지 게이트 — «`TASK-MONO-793` 을 실은 AMI 굽기가 라이브 확인된 뒤에만 머지»** (소유자 결정 2026-10-15 UTC, `TASK-MONO-793` § 소유자 결정 Q5 · 순서). 콘솔은 머지 즉시 Vercel 로 나가고 백엔드는 굽기에서만 나간다. 굽기 전에 머지하면 라이브 `GET /api/admin/me/domain-access` 가 아직 `ERP_OPERATOR` 만 내므로 **모든 운영자의 ERP 메뉴에 거짓 «업무 권한 없음» + 등록 · 수정 · 폐기 · 상신 · 승인 버튼 숨김 + 알림 종의 ERP 갈래 사라짐** — 서버는 열어 주는데 화면이 닫는다. 반대(굽기 뒤 · 이 PR 전)는 조회 세트 운영자에게 쓰기 버튼이 보이고 누르면 서버 403(화면 안 «권한 없음»), ERP 역할 없는 사용자의 알림 종이 `degradedDomains ∋ erp` — 안전 쪽 실패.

# Dependency Markers

- **선행**: `TASK-MONO-793`(머지 + 그 커밋을 담은 굽기 + 라이브 확인 — `TASK-MONO-672` § Next window — TASK-MONO-793). 이 티켓의 AC-0 이 그 라이브 확인을 다시 잰다.
- 형제: `TASK-PC-FE-342`(P5 SCM — 같은 모양) · `TASK-PC-FE-338`(c-2 — 메뉴 단위 `serverRequires` · `writeRequires` 규칙을 만든 티켓, 이 티켓은 **데이터만** 더한다 — 계약 § 2.4.3.4 «Scales with P5–P8»).

# Goal

`TASK-MONO-793` 이 ERP 4 서비스의 업무 인가를 세분했다(erp `iam-integration.md` § 업무 역할 — 정본). 콘솔의 ERP 메뉴 · 버튼 신호와 알림 종의 ERP 갈래를 서버 판정과 같게 만든다.

# Scope

## In Scope

- 계약 먼저: `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 — «SCM · ERP · Finance leaves | — | … `authenticated()` only» 줄에서 ERP 행을 떼어 아래 대조표로 · «Scales with P5–P8» 에 P6 완료 표시 · 알림 종(§ 2.4.8 알림 집계) 의 ERP 갈래 선택 규칙 한 줄.
- `apps/console-web/src/shared/guide/permission-map.ts` ERP 행(`/erp` · `/erp/masters` · `/erp/orgview` · `/erp/approval` · `/erp/delegation`, `/erp/guide` 제외):
  - `gate.roles` = ERP 카탈로그 역할(상한) — `ERP_OPERATOR` 단독이 아니라 4개 업무 역할.
  - `serverRequires`(기본 조회) · `writeRequires`(변경 버튼) — 아래 대조표 대로.
- 알림 종(`shared/composition/notification-inbox.ts`): `/me/domain-access` 가 ERP 업무 역할 0 을 말하면 ERP 갈래를 **부르지 않는다**(지금은 부르고 403 → `degradedDomains ∋ erp`). `/me/domain-access` 실패 · 시간 초과면 지금처럼 부른다.
- 시험: `permission-map-drift` · 메뉴 신호 · 버튼 숨김 · 알림 갈래 선택 단위 시험.

## Out of Scope

- 서버 판정 변경(793 에서 끝났다) · Finance · E-commerce(P7 ~ P8 의 콘솔 후속).
- IAM 운영자 화면의 `org_scope` 부서 선택기 — 기준정보 조회 역할 없는 관리자의 403 → 수동 id 입력 저하는 **소유자가 수용**(793 Q5). 바꾸지 않는다.
- 새 신호 규칙(계약 § 2.4.3.4 의 규칙은 그대로 — 데이터만).

# 서버 판정 대조표 (`TASK-MONO-793` 기준 — AC-0 에서 다시 잰다)

| 화면 | 기본 조회가 부르는 것 | `serverRequires` | 변경 버튼 | `writeRequires` |
|---|---|---|---|---|
| `/erp` 개요 | masterdata 목록 5 · approval 결재함 · 위임 | 둘 중 하나라도 — `ERP_MASTERDATA_READ/WRITE` · `ERP_APPROVAL_READ/WRITE` (타일별 저하는 지금 규칙) | — | — |
| `/erp/masters` | masterdata 목록 | `ERP_MASTERDATA_READ` · `ERP_MASTERDATA_WRITE` | 등록 · 수정 · 폐기 · 부서 이동 · 계정 연결 제안/철회 | `ERP_MASTERDATA_WRITE` |
| `/erp/orgview` | read-model `employees` (+ 위임 사실 카드) | `ERP_MASTERDATA_READ` · `_WRITE` — 🔵 위임 사실 카드는 `ERP_APPROVAL_READ/WRITE` 라 기준정보 세트만이면 그 카드만 «권한 없음» | (READ-ONLY) | — |
| `/erp/approval` | approval `requests` · `inbox` | `ERP_APPROVAL_READ` · `_WRITE` | 상신 · 승인 · 반려 · 회수 | `ERP_APPROVAL_WRITE` |
| `/erp/delegation` | approval `delegations` | `ERP_APPROVAL_READ` · `_WRITE` | 위임 등록 · 회수 | `ERP_APPROVAL_WRITE` |
| 알림 종 | notification inbox | ERP 업무 역할 하나라도(개인 엔드포인트) | (읽음 표시 = 개인) | — |
| 계정 연결 수락 · 거절(내 제안) | masterdata `/account-link-proposals/mine` | ERP 업무 역할 하나라도(개인 엔드포인트) | 수락 · 거절 | 같은 조건 |

# Acceptance Criteria

- [ ] **AC-0** — 🔴 머지 게이트 실측: `TASK-MONO-793` 을 담은 AMI 가 라이브인가(굽기 기록 · `GET /api/admin/domain-roles` 가 `ERP_MASTERDATA_READ` 를 내는가 · 라이브 `/me/domain-access` 가 데모 운영자에게 ERP 4 역할을 내는가). 아니면 **이 티켓은 시작하지 않는다**. 위 대조표를 서버 코드(masterdata `RoleScopeAuthorizationAdapter` · approval `ActorContext` · read-model / notification `ReadAuthorizationGate`)와 다시 대조.
- [ ] **AC-1** — 계약 § 2.4.3.4 · § 2.4.8 개정이 코드보다 먼저.
- [ ] **AC-2** — 🔴 대조군: 조회 세트(`erp-read`) → ERP 메뉴 배지 없음 · 쓰기 버튼 없음 / 결재 세트만 → `/erp/masters` · `/erp/orgview` 배지, `/erp/approval` 버튼 보임 / 기준정보 세트만 → `/erp/approval` · `/erp/delegation` 배지 / ERP 세트 0 → ERP 메뉴 전부 배지 · 알림 종이 ERP 갈래를 부르지 않음(`degradedDomains` 에 erp 없음).
- [ ] **AC-3** — 실패 경로: `/me/domain-access` 실패 · 시간 초과 → 배지 없음 · 버튼 보임 · 알림 종은 ERP 갈래를 부른다(지금 규칙 그대로).
- [ ] **AC-4** — 배포 상태(Vercel · 라이브 화면)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-083-domain-permission-sets-per-tenant.md` D9 · P6
- `projects/erp-platform/specs/integration/iam-integration.md` § 업무 역할 (서버 판정 정본)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.3.4 · § 2.4.8
- `projects/iam-platform/specs/contracts/http/admin-api.md` § 카탈로그 (`/me/domain-access`)

# Edge Cases

- `ERP_OPERATOR` 는 `/me/domain-access` 에 **나오지 않는다**(카탈로그 밖 · auth 파생) — 신호 입력으로 쓰지 않는다.
- 테넌트를 고르지 않은 운영자(기본 토큰 — 역할 0)는 ERP 전 화면이 서버 403 이다(793 Q1, SCM 과 같음) — 신호도 «업무 권한 없음» 이 맞다.
- 결재 대상 확인(`GET /departments/{id}` · `/employees/{id}`)은 `ERP_APPROVAL_WRITE` 도 받는다 — 결재 화면의 대상 표시가 기준정보 세트 없이도 뜬다.

# Failure Scenarios

1. 굽기 전에 머지 — 위 머지 게이트의 거짓 배지 · 버튼 숨김 · 알림 갈래 소실.
2. `ERP_OPERATOR` 를 `serverRequires` 에 넣는다 — 진입 역할은 업무 API 를 열지 않으므로 신호가 서버보다 넓어진다.
3. 알림 종이 `/me/domain-access` 실패를 «역할 0» 으로 읽는다 — ERP 역할 보유자의 알림이 사라진다(실패는 지금처럼 부른다).

# Merge gate — 28th window

- 2026-10-16 UTC — gate met: the bake carrying 792/793/794/795 was live-verified in the 28th window (2026-10-16) — may now be implemented/merged. (AMI `ami-00ef8cc1057cca59b` @ `8905d2c29`; this ticket's source `TASK-MONO-793` → `done/`; results in `TASK-MONO-672` § 28th window — harvest.)
