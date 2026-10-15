# Task ID

TASK-BE-639

# Title

A platform (`'*'`) operator can assume a tenant that is not `ACTIVE` (`PENDING` since TASK-MONO-773, and `SUSPENDED` before it) — the assume path never reads the tenant status

# Status

ready

# Owner

backend

# Task Tags

- code
- test
- security

---

> **Analysis model:** Opus 5.5 / **Recommended implementation model:** Opus (assume path · a new cross-service read on the token path)
> Source: `TASK-MONO-773` S4 measurement Q4 (2026-10-15 UTC, code reading + an IT that pins today's behaviour). Owner decision 2026-10-15: record it in 773 and handle it here.

# Dependency Markers

- After `TASK-MONO-773` S4 (the `PENDING` state exists and the S4 IT pins `assigned=true` for a `'*'` row as a measurement).

# Goal

- admin `OperatorAssignmentCheckUseCase.java:199-203` answers `assigned=true` for a `'*'` operator without reading the tenant.
- auth-service's assume path (`AssumeTenantAuthenticationProvider` and the rest of `infrastructure/oauth2`) has no tenant-status check.
- ⇒ A `'*'` operator can get an assumed token for a `PENDING` tenant (no admin yet) or a `SUSPENDED` tenant.
- The console switcher lists only `ACTIVE` tenants, so there is no screen path today; a direct token exchange works.

Decide whether assuming into a non-`ACTIVE` tenant should be refused, and for whom.

# Scope

## In Scope

- **AC-0:** measure live or in an IT.
  - Which identities can assume a non-`ACTIVE` tenant: `'*'`; and a company operator whose tenant became `SUSPENDED` while their assignment row stayed.
  - What an assumed token can then do (domain APIs, admin APIs).
- **Design:** where the status is read, and the failure mode if account-service is unavailable (fail-closed vs the current availability). Options:
  - the assignment check reads account-service;
  - the token carries the tenant status from an existing read;
  - leave `'*'` allowed on purpose and only refuse company operators.
- An IT per decided row, plus a control (`ACTIVE` unchanged).

## Out of Scope

- The console switcher (it already lists only `ACTIVE`).

# Acceptance Criteria

- [ ] **AC-0**: the measured matrix (identity × tenant status → assume result → what the token can call), and the chosen design with why.
- [ ] **AC-1**: each refused row is refused, and nothing is issued.
- [ ] **AC-2**: control — `ACTIVE` tenant assume byte-unchanged; the 773 S4 IT's Q4 pin is updated to the decision.

# Related Specs

- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` (operator-assignments check)
- `projects/iam-platform/specs/contracts/http/auth-api.md` § Assume-Tenant Exchange

# Related Contracts

- Same as above.

# Edge Cases

- A tenant suspended while an assumed token is live — it lives to `exp` + 60 s (BE-632 AC-6 measurement), the same as any revocation.

# Failure Scenarios

1. Adding a synchronous account-service read on every assume without a failure-mode decision — an account-service outage then blocks every console tenant switch.
