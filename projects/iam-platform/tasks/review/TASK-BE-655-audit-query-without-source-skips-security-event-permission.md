# Task ID

TASK-BE-655

# Title

`GET /api/admin/audit` with an `accountId` and no `source` returns login history and suspicious events without checking `security.event.read` — an `audit.read`-only operator reads security data

# Status

review

# Owner

backend

# Task Tags

- code
- security
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (one permission branch in a controller + a persona test)
> Drafted 2026-10-24 UTC from `TASK-BE-654` AC-0 (read-only measurement, confirmed in source by the drafter). Owner decision 2026-10-24 (selection window): «별도 티켓 · 먼저» — before `TASK-BE-654` and `TASK-BE-653`.

# Dependency Markers

- 출처: `TASK-BE-654` AC-0.
- 후속 관계: `TASK-BE-654` (같은 `AuditController` · `AuditQueryUseCase` 를 건드린다) — **이 티켓이 먼저 머지**된 뒤 착수한다. 순서 제약이지 기능 의존은 아니다.

# Goal

An operator who lacks `security.event.read` never receives login-history or suspicious-event rows from `GET /api/admin/audit`, whatever the `source` parameter says.

# Scope

## In Scope

- **The defect (confirmed in source, origin/main `60d4d96bd`):**
  - `AuditQueryUseCase`: `includeLogin = source == null || "login_history".equals(source)` and `includeSuspicious = source == null || …`. With `cmd.accountId() != null`, both security-service reads run.
  - `AuditController`: the composite check `audit.read + security.event.read` runs only `if (isSecurityEventSource(source))`, so it is skipped when `source` is absent.
  - Result: `audit.read` alone (e.g. `SUPPORT_LOCK`, `rbac.md:112`) + `accountId` + no `source` → security data.
  - `rbac.md:534` already says that with no `source` the query is limited to the admin source. The code contradicts the spec; the spec stays as it is.
- **Fix:** with no `source`, the security sources are included **only if** the operator holds `security.event.read`. Otherwise the response holds the admin source only. An explicit `source=login_history|suspicious` without the permission stays a 403 with a DENIED row (unchanged).
- AC-0 re-measures before changing: the exact lines, whether any console caller relies on the no-`source` mixed answer (`queryAudit` call sites), and whether the response says which sources it contains.

## Out of Scope

- Who writes the meta-audit row, and what it records → `TASK-BE-654`.
- security-service's hard-coded `fan-platform` tenant in `SecurityQueryService` (recorded in `TASK-BE-653` AC-0).

# Acceptance Criteria

- [x] **AC-0** — the defect re-measured with file:line; the console callers listed with what they send.
  - → **닫힘 2026-10-24 UTC** (origin/main `4fb141a20`). 결함은 위 In Scope 그대로 재확인했다.
    - `AuditQueryUseCase`: `includeLogin` · `includeSuspicious` = `source == null || …`. 둘 다 `cmd.accountId() != null` 일 때 security-service 를 부른다.
    - `AuditController`: 합성 검사는 `if (isSecurityEventSource(source))` 일 때만 돈다.
  - **콘솔 호출부:**
    - 개요 두 곳(`dashboards/api/overview-api.ts:206` · `iam-overview/api/overview-state.ts:212`)과 `/audit` 첫 화면(`page.tsx:25`)은 `accountId` 없이 부른다 → 결함 경로가 아니다.
    - 🔴 **감사 화면 필터**(`use-audit-screen.ts:125-132` → `iam-audit-read.ts:106-114`)는 source «전체»(`''` → 보내지 않음)와 `accountId` 를 함께 보낼 수 있다 → **콘솔에서 실제로 닿는 경로**다.
  - 응답은 행마다 `source` 를 싣는다(`AuditRowCells.tsx:41-67`). 그래서 좁혀진 응답은 admin 행만으로 정상 렌더된다.
  - 표기 차이(기록만): 계약 값은 `admin`(`admin-api.md:492`)인데 rbac.md 의사코드는 `"admin_actions"` 였다(고쳤다). 기존 슬라이스 시험 `audit_query_admin_source_only_requires_audit_read` 도 `admin_actions` 를 보낸다. use-case 가 그 값을 «어느 소스도 아님» 으로 읽으니 빈 결과다. 목 use-case 라 시험은 초록이고, 범위 밖이라 그대로 뒀다.
- [x] **AC-1** — an `audit.read`-only operator querying `accountId=X` with no `source` gets no `login_history` / `suspicious` entry, and security-service is **not called** (verify the client mock). Red before the change.
  - → `AuditController`: `source == null && accountId != null` 이고 `hasAllPermissions(audit.read, security.event.read)` 가 false 면 실효 source 를 `"admin"` 으로 바꾼다. 403 이 아니고 DENIED 행도 없다(거절된 요청이 아니다).
  - 증명은 두 단계다.
    - (1) 슬라이스 `…without_security_event_read_narrows_to_admin`: use-case 로 가는 command 의 `source == "admin"` · DENIED 0.
    - (2) 기존 `AuditQueryUseCaseTest.query_sourceAdmin_securityClientNotCalled`(`accountId=acc-1`): `source=admin` 이면 `queryLoginHistory` · `querySuspiciousEvents` 를 `never()` 로 고정한다.
  - **무는가:** `src/main` 만 되돌리면 (1) 하나만 빨강이다(20 중 1, rc=1). 복구 뒤 20/20 이다.
- [x] **AC-2** — control group: an operator holding both permissions, same query, still gets all three sources. Explicit `source=login_history` without `security.event.read` is still 403 + DENIED row.
  - → 슬라이스 `…with_both_permissions_keeps_all_sources`: source 는 null 그대로다(use-case 가 세 소스를 읽는다 — `query_sourceNullWithAccountId_queriesAllThreeSources`).
  - `…no_source_no_account_is_unchanged`: 개요 경로는 권한 평가조차 하지 않는다.
  - 명시 `source=login_history` 의 403 시험은 무변경으로 초록이다.
- [x] **AC-3** — `rbac.md` § Conditional Cross-Permission states the no-`source` rule explicitly, if it is not already unambiguous.
  - → 모호했다(«null = admin 기본 소스로 제한» 이 권한과 무관한 것처럼 읽혔고, 값 이름도 계약과 달랐다).
  - `rbac.md` 의사코드와 `admin-api.md` § GET /api/admin/audit `source` 행에 «생략 시: 권한 있으면 통합 · 없으면 `admin` 으로 좁힘(200)» 을 적었다.
  - 배포 = 다음 굽기(admin-service, 이미 굽기 목록에 있다).

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` § Conditional Cross-Permission (`:520-562`, `:534`)
- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/audit

# Related Contracts

- `GET /api/admin/audit`

# Edge Cases

- A 403 for the no-`source` case would break the console overview, which calls without `source` for every operator. Narrow the sources instead.
- `source=admin` explicitly: unchanged.

# Failure Scenarios

1. The fix filters the **response** but still calls security-service. Personal data is read and then dropped, and the read is not audited. AC-1 asserts no call.
2. The fix lands in the controller as a 403 for missing `source`. Every `audit.read`-only overview load turns red. Edge case 1.
