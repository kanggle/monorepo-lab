# Task ID

TASK-BE-658

# Title

`AuditControllerSliceTest` «admin source only requires audit.read» sends `source=admin_actions` — a value the API does not know, which reads nothing and returns an empty 200

# Status

in-progress

# Owner

backend

# Task Tags

- test
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Haiku or Sonnet (one test value + one assertion)
> Drafted 2026-10-25 UTC. Owner said «진행» to the recommendation on 2026-10-25 UTC. Facts read on origin/main `53a9227fb`.

# Dependency Markers

- 출처: 2026-10-24 session handoff («슬라이스 시험이 `source=admin_actions`(계약 값은 `admin`)를 보냄»).
- 관련: `TASK-BE-655` (done) — the same controller's source/permission rule.

# Goal

The slice test that claims «the admin source needs only `audit.read`» exercises the admin source.

# Scope

## In Scope

- **AC-0 facts (measured while drafting):**
  - `AuditControllerSliceTest.java:136-141` sends `source=admin_actions` and expects 200.
  - The contract's values are `admin` / `login_history` / `suspicious` (`admin-api.md` § GET /api/admin/audit, `source` row).
  - `AuditQueryUseCase.java:68-70` reads a source only on an exact match (`"admin".equals(source)` …), and `AuditController.java:119-120` checks the extra permission only for `login_history` / `suspicious`. So `admin_actions` passes the controller, reads **no source**, and returns an empty 200 — the test is green for a reason unrelated to its name.
- **Fix:** send `admin`, and assert the command the use case receives carries `source=admin` (the test already captures `QueryAuditCommand` elsewhere in the file).

## Out of Scope — recorded for an owner decision

- **An unknown `source` value is answered 200 with an empty page, not 400.** Rejecting it would be an API behaviour change (contract Errors table lists 422 `VALIDATION_ERROR` for other bad parameters). Not changed here; raised to the owner with this ticket.

# Acceptance Criteria

- [ ] **AC-1** — the test sends `source=admin` and asserts the captured command's `source` is `admin`; 200.
- [ ] **AC-2** — mutation: with the test's value put back to `admin_actions`, the new assertion is red.
- [ ] **AC-3** — `:projects:iam-platform:apps:admin-service:test --tests '*AuditControllerSliceTest' --rerun` green.

# Related Specs

- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/audit

# Related Contracts

- None changed.

# Edge Cases

- Case: `AuditController.isSecurityEventSource` uses `equalsIgnoreCase`, the use case `equals` — `LOGIN_HISTORY` is permission-checked but reads nothing. Not a bypass (stricter on the permission side); noted only.

# Failure Scenarios

1. The value is fixed but nothing asserts what reached the use case — the next typo is green again. AC-1's capture.
