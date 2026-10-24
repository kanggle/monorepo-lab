# Task ID

TASK-BE-656

# Title

security-service login-history and suspicious-event reads filter by a hard-coded `fan-platform` tenant — an operator's audit view of any account outside `fan-platform` comes back empty

# Status

ready

# Owner

backend

# Task Tags

- code
- bug
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (one tenant parameter threaded from admin-service to two finders, plus tests)
> Drafted 2026-10-24 UTC. First seen in `TASK-BE-653` AC-0 (Explore, read-only); confirmed in source by the drafter on origin/main `3cea93ad0`. Owner said «진행» to the recommendation «기안» on 2026-10-24 UTC.

# Dependency Markers

- 출처: `TASK-BE-653` AC-0 («기존 결함, 기록만»).
- 관련: `TASK-BE-248` (done) — the placeholder comment names a «Phase 2» that no ticket owns. `TASK-BE-654` (done) — the caller (admin-service `AUDIT_QUERY`) now audits these reads; this ticket changes what they return, not who audits them.
- 관련: `TASK-BE-653` — pool logins are `tenant_id = consumer-pool` (+ `site_tenant_id`), so a pool account is exactly the case that reads empty today.

# Goal

An operator who may see an account's audit trail sees that account's login history and suspicious events, whatever tenant the account belongs to — and never another tenant's rows.

# Scope

## In Scope

- **The defect (confirmed in source):** `SecurityQueryService.findSuspiciousEvents` and `findLoginHistory` set `String tenantId = Tenants.DEFAULT_TENANT_ID;` with the comment «TASK-BE-248 Phase 1 … Phase 2 will thread X-Tenant-Id». The finders filter `tenant_id = fan-platform`. Any account whose rows carry another tenant (`consumer-pool`, `ecommerce`, `demo-corp`, …) returns an empty page.
- **AC-0 (measure before changing):**
  - Is the empty result real today? Query the rows by `tenant_id` for a non-`fan-platform` account (fixture or demo DB), and call `GET /api/admin/audit?accountId=<that>&source=login_history` as an operator with both permissions.
  - What admin-service knows at call time: the gated query tenant (`QueryTenantScopeGate` → `requestedTenantId`), and whether `*` (platform scope) must mean «no tenant filter».
  - The two internal endpoints' contract (`security-query-api.md`) — what it says about tenant today.
  - Pool accounts: the account's own tenant is `consumer-pool`. Which tenant should a site operator's query match — `tenant_id`, `site_tenant_id`, or the account id alone? Owner decision if not already settled by the contract.
- **Fix (after AC-0):** admin-service passes the gated tenant to security-service (query param or header, per contract). security-service filters by it (`*` → no tenant filter). The hard-coded default is removed.

## Out of Scope

- Who audits the read (`TASK-BE-654`, done).
- The `source` permission rule (`TASK-BE-655`, done).
- The metrics endpoint (`/internal/security/metrics/daily`), which already takes `tenantId`.

# Acceptance Criteria

- [ ] **AC-0** — the measurements above, file:line, with the observed empty result (or ⚪ + reason).
- [ ] **AC-1** — contract first: `security-query-api.md` states the tenant parameter, `*` semantics, and the pool-account rule.
- [ ] **AC-2** — a non-`fan-platform` account's login history and suspicious events are returned. Red before the change.
- [ ] **AC-3** — isolation: a query scoped to tenant A never returns tenant B's rows. Control group: a `fan-platform` account's result is unchanged.

# Related Specs

- `projects/iam-platform/specs/contracts/http/security-query-api.md`
- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/audit
- `projects/iam-platform/specs/features/multi-tenancy.md` § consumer pool

# Related Contracts

- `GET /internal/security/login-history` · `GET /internal/security/suspicious-events`

# Edge Cases

- `accountId` is globally unique, so dropping the tenant filter would also "work" — but it would let a tenant-A-scoped query read a tenant-B account's rows. Keep a tenant predicate.
- `*` (platform scope) — no tenant filter.

# Failure Scenarios

1. The parameter is added to security-service but admin-service keeps not sending it, so the default still applies. AC-2 goes end to end through admin-service.
2. The default is replaced by «no filter» with no parameter → cross-tenant read. AC-3.
