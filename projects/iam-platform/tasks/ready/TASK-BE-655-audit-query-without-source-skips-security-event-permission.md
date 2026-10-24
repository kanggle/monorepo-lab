# Task ID

TASK-BE-655

# Title

`GET /api/admin/audit` with an `accountId` and no `source` returns login history and suspicious events without checking `security.event.read` — an `audit.read`-only operator reads security data

# Status

ready

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

- [ ] **AC-0** — the defect re-measured with file:line; the console callers listed with what they send.
- [ ] **AC-1** — an `audit.read`-only operator querying `accountId=X` with no `source` gets no `login_history` / `suspicious` entry, and security-service is **not called** (verify the client mock). Red before the change.
- [ ] **AC-2** — control group: an operator holding both permissions, same query, still gets all three sources. Explicit `source=login_history` without `security.event.read` is still 403 + DENIED row.
- [ ] **AC-3** — `rbac.md` § Conditional Cross-Permission states the no-`source` rule explicitly, if it is not already unambiguous.

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
