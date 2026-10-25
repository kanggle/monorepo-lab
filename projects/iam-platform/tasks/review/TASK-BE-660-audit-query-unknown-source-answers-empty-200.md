# Task ID

TASK-BE-660

# Title

`GET /api/admin/audit` answers an unknown `source` value with an empty 200 — reject it with 400 `VALIDATION_ERROR` at the boundary

# Status

review

# Owner

backend

# Task Tags

- code
- bug
- contract
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (allow-list check in the controller + contract row + slice tests)
> Drafted 2026-10-25 UTC. Owner decision 2026-10-25 UTC (AskUserQuestion): «400 VALIDATION_ERROR 로 (Recommended)». Facts read on origin/main `031210ecb`.

# Dependency Markers

- 출처: `TASK-BE-658` (done) — its slice test sent `source=admin_actions` and was green for the wrong reason; this is the behaviour behind it.
- 선례: `TASK-BE-475` (done) — `GET /api/admin/accounts` rejects a `status` outside its allow-list at the admin-service boundary with 400 `VALIDATION_ERROR` (`admin-api.md:206,244`).

# Goal

A typo in `source` is an error the caller sees, not an audit trail that silently reads «no records».

# Scope

## In Scope

- **AC-0 facts (measured while drafting):**
  - `AuditController.java:63` takes `source` as a free string; `:119-120` checks the extra permission only for `login_history` / `suspicious` (`equalsIgnoreCase`).
  - `AuditQueryUseCase.java:68-70` reads a source only on an exact match (`"admin".equals(source)` …) — any other value reads nothing → 200 with an empty page.
  - Contract values: `admin` / `login_history` / `suspicious` (`admin-api.md` § GET /api/admin/audit, `source` row).
  - Console sends only those three (`shared/api/iam-audit-types.ts:27` `AUDIT_SOURCES`; `iam-audit-read.ts:114`, `features/audit/hooks/use-audit.ts:60`).
- **Fix:** in `AuditController`, a `source` that is non-blank and not one of the three → 400 `VALIDATION_ERROR` before any read or permission evaluation of the security sources. Decide and record whether the check is case-sensitive (the use case is; the permission check is not) — recommend case-sensitive, matching what the use case reads.
- **Contract:** the `source` row says unknown → 400; the Errors table gains the row (the existing 422 `VALIDATION_ERROR` row for `from > to` is left as is — not this ticket).

## Out of Scope

- The audit meta-row (`TASK-BE-654`) — a 400 here is a validation refusal, written or not per the existing rule; AC-0 of the impl states which.

# Acceptance Criteria

- [x] **AC-1** — contract first.
- [x] **AC-2** — `source=admin_actions` → 400 `VALIDATION_ERROR`, use case not called; red before the change.
- [x] **AC-3** — control: `admin` · `login_history` · `suspicious` and no `source` behave exactly as before (existing `AuditControllerSliceTest` cells green).

# Related Specs

- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/audit
- `projects/iam-platform/specs/services/admin-service/rbac.md` (source permission rule)

# Related Contracts

- `GET /api/admin/audit`

# Edge Cases

- `source=` (blank) stays «no source».
- `LOGIN_HISTORY` (upper case): today permission-checked but reads nothing. With a case-sensitive allow-list it becomes 400 — consistent.

# Failure Scenarios

1. The check sits after the permission aspect, so an unknown value from an operator without `security.event.read` still returns 200/403 inconsistently — the check runs first. AC-2.

---

# Implementation — 2026-10-25 UTC

- Contract: `admin-api.md` § GET /api/admin/audit — `source` row (outside the three values, case-sensitive → 400; blank = omitted) and an Errors row `400 VALIDATION_ERROR`.
- `AuditController`: blank `source` → `null`; a value outside `{admin, login_history, suspicious}` → `IllegalArgumentException` → `AdminExceptionHandler` 400 `VALIDATION_ERROR` (the TASK-BE-475 path), before the composite-permission check and before the use case. Case-sensitive, matching `AuditQueryUseCase`'s exact `equals`.
- Meta-audit: the refusal throws before the use case, so no `AUDIT_QUERY` row is written — same as any other 400 parameter refusal on this endpoint (TASK-BE-654 writes rows for gated queries only).

# Verification

- Red before (new tests on the old controller): `audit_query_unknown_source_is_400_and_reads_nothing` · `audit_query_source_in_wrong_case_is_400` · `audit_query_blank_source_is_no_source` — 3 `FAILED`.
- After: `AuditControllerSliceTest` 13 · `AuditQueryUseCaseTest` 17 · `AuditDeniedCrossPermissionTest` 2 — 0 failures (`--rerun`).
- Census: every other test and the console send only the three values (`AUDIT_SOURCES`, integration tests use `admin`).
