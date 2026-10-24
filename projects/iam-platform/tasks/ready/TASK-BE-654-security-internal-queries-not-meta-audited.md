# Task ID

TASK-BE-654

# Title

`security-query-api.md` says every internal security query is meta-audited (A5), but security-service has no such audit — for login-history and suspicious-event reads alike

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- docs
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (audit of reads of personal data — what to record, where, and without making every overview load write a row)
> Drafted 2026-10-24 UTC from `TASK-MONO-811`'s implementation report (backend agent). Not re-measured by the drafter: AC-0 is the measurement.

# Dependency Markers

- 출처: `TASK-MONO-811` § 구현 결정 «기존 공백». The new `/internal/security/metrics/daily` endpoint records its own exemption in the contract (aggregates only).
- 관련: admin-service meta-audits `GET /api/admin/audit` on its side (`admin_actions` row, `AUDIT_QUERY`). So the operator-facing read may already be audited one hop earlier. AC-0 decides whether that is the A5 the contract means.

# Goal

The contract and the code agree on who audits a read of login history and suspicious events. Either security-service records it, or the contract says the caller does and names where.

# Scope

## In Scope

- **AC-0:**
  - The A5 sentence in `projects/iam-platform/specs/contracts/http/security-query-api.md`, quoted.
  - The read paths: `GET /internal/security/login-history` · `/suspicious-events`. Find any audit write on them in security-service (expected: none).
  - What admin-service records for the same request: `AuditQueryUseCase` meta-audit row, `source=login_history|suspicious`.
  - Are there other callers of these internal endpoints besides admin-service?
- **Decision (owner):**
  - (a) Contract-only: «audited by the caller (admin-service `admin_actions`)».
  - (b) security-service writes its own audit row.
  - (c) Both.
- Implementation per the decision.

## Out of Scope

- The aggregate metrics endpoint. It returns no personal data, and its exemption is already recorded.

# Acceptance Criteria

- [ ] **AC-0** — the above, file:line.
- [ ] **AC-1** — owner decision recorded.
- [ ] **AC-2** — contract and code agree. If (b) or (c): a test shows that one read writes one audit row, and that a denied read writes none (or a DENIED row, per decision).

# Related Specs

- `projects/iam-platform/specs/contracts/http/security-query-api.md`
- `projects/iam-platform/specs/services/admin-service/rbac.md` (meta-audit)

# Related Contracts

- `GET /internal/security/login-history` · `GET /internal/security/suspicious-events`

# Edge Cases

- A per-account read is personal data. The aggregate is not. Keep the exemption explicit, not implied.

# Failure Scenarios

1. (b) is implemented as an audit row per request, and the console's overview calls it often → audit-table growth. Measure call volume in AC-0.
