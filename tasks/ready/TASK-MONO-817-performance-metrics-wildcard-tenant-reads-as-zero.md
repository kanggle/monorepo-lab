# Task ID

TASK-MONO-817

# Title

A performance-metrics call made with a wildcard tenant (`tenant_id='*'`) answers all zeros — and the console shows «0», which reads as «nothing happened»

# Status

ready

# Owner

backend / platform-console

# Task Tags

- code
- docs
- api

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (a cross-domain rule for the shared metrics contract, applied to each domain endpoint)
> Drafted 2026-10-24 UTC from `TASK-MONO-812` AC-4 (🔵). A shared-contract question across the ADR-MONO-084 series, not one domain's bug.

# Dependency Markers

- 출처: `TASK-MONO-812` AC-4 · `ADR-MONO-084` · `platform/contracts/performance-metrics-contract.md` § 4 («A failed strip is never shown as zero»).
- 관련: `TASK-MONO-807`~`812` (the six domain endpoints) · `TASK-PC-FE-368` (the home cards read the same endpoints).

# Goal

Every domain's metrics endpoint does one defined thing for a wildcard tenant, and the console never shows a wildcard's empty result as a real «0».

# Scope

## In Scope

- **AC-0 measurement, per domain endpoint** (ecommerce order · wms admin · erp approval · scm procurement · iam admin · finance account): what a `*` tenant gets today. The options are zeros, the sum over all tenants, 403, or 400.
  - Finance: zeros, the same as its other reads (TASK-MONO-812 contract note).
  - IAM: `tenantId=*` = all tenants for SUPER_ADMIN (TASK-MONO-811).
  - The rest: measure.
  - Also: which console operators can actually call with `*` (platform super-admin, console session tenant `*`)? If none can, the defect is latent, and the ticket says so instead of changing code.
- **Decision (owner selection window after AC-0):**
  - (a) `*` = sum over all tenants, where the domain can do that safely.
  - (b) `*` = 400 / 403, which the console strip shows as «권한 없음» or «불러올 수 없음».
  - (c) Per-domain, documented.

  The rule goes into `platform/contracts/performance-metrics-contract.md` (shared, project-agnostic wording).
- Implementation in one atomic PR if more than one domain changes.

## Out of Scope

- Per-tenant time zones (ADR-MONO-084 D3 placeholder).

# Acceptance Criteria

- [ ] **AC-0** — the per-domain table above, file:line, plus who can reach it with `*`.
- [ ] **AC-1** — owner decision recorded. The shared contract states the rule.
- [ ] **AC-2** — each endpoint follows the rule, with a test per domain. Control group: a concrete tenant's values are unchanged.
- [ ] **AC-3** — console: a wildcard response never renders as «0» in a strip or a home card.

# Related Specs

- `platform/contracts/performance-metrics-contract.md`
- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md`

# Related Contracts

- Each domain's `…/metrics` section (ecommerce `order-api.md` · wms `admin-service-api.md` · erp approval · scm `procurement-api.md` · iam `admin-api.md` · finance `account-api.md`).

# Edge Cases

- (a) for IAM already includes `consumer-pool` (TASK-MONO-811). A «sum» elsewhere may need the same note.

# Failure Scenarios

1. Only the shared contract text changes and an endpoint keeps returning zeros. AC-2 is per domain for this reason.
