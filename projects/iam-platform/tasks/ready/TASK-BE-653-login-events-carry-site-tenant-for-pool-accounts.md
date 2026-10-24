# Task ID

TASK-BE-653

# Title

Login and lock events of a consumer-pool account are recorded under `consumer-pool`, not the site the consumer logged in to — so a site tenant's IAM metrics and security rules never see its own consumers

# Status

ready

# Owner

backend

# Task Tags

- code
- event
- iam

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (event-contract change across auth-service → security-service, with a data question about rows written before the change)
> Drafted 2026-10-24 UTC from `TASK-MONO-811` AC-0. Owner decision 2026-10-24 (selection window): 811 counts by the tenant already recorded and states the pool exclusion; **site attribution is this ticket**.

# Dependency Markers

- 출처: `TASK-MONO-811` AC-0 (IAM overview metrics). 811 의 `loginSuccess` · `loginFailure` · `accountLocked` 는 이 티켓이 닫힐 때까지 소비자 풀 계정을 사이트 테넌트에서 뺀다.
- 관련: ADR-MONO-078 (one consumer login across fan and store) · `TASK-MONO-744` (데모 소비자 전원 풀 계정).

# Goal

When a consumer-pool account logs in to a site (or is locked), the security record says **which site**, so per-site views (metrics, and any per-tenant security rule) can count it there.

# Scope

## In Scope

- **AC-0 measurement:**
  - `CredentialAuthenticationProvider.java:401` and `OAuthLoginUseCase` publish `tenantId = credential.getTenantId()`. That value is `consumer-pool` for a pool account. List every publisher of `auth.login.*` and `account.locked` with the tenant it puts on the event.
  - Which security-service rules read `tenant_id` (VelocityRule, DeviceChangeRule, …)? What would a site tenant change for each?
  - Is a site-level lock (`consumer_site_memberships.status=LOCKED`) emitted as any event? Today it is not shown to be.
  - The spec conflict 811 found: `auth-events.md:50` (account's tenant) vs `:158` (token's tenant). 811 aligned `:158` to the code.
- **Design (owner decision after AC-0):** an additive field (e.g. `siteTenantId`, the initiating client's tenant) vs re-defining `tenantId` for pool accounts. Re-defining changes what every consumer already relies on, so it needs a stated reason.
- Implementation in auth-service (publish), security-service (store + migration), and `TASK-MONO-811`'s metrics query (count by site).

## Out of Scope

- Back-filling rows written before the change. The old rows have no site, and the contract must say from when the counts are by site.

# Acceptance Criteria

- [ ] **AC-0** — the list above, file:line.
- [ ] **AC-1** — owner decision recorded (field shape · what `*`-tenant views count).
- [ ] **AC-2** — a pool account's login to site X is counted in X's `loginSuccess`. A non-pool account's count is unchanged (control group). Another site's login is not counted (isolation).
- [ ] **AC-3** — the IAM metrics contract (`admin-api.md` § GET /api/admin/metrics) drops the «pool excluded» line and states the date from which pool logins count by site.

# Related Specs

- `projects/iam-platform/specs/contracts/events/auth-events.md` · `account-events.md`
- `projects/iam-platform/specs/features/multi-tenancy.md` § consumer pool
- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md`

# Related Contracts

- `auth.login.attempted` / `auth.login.failed` / `auth.login.succeeded` · `account.locked`
- `projects/iam-platform/specs/contracts/http/admin-api.md` § GET /api/admin/metrics

# Edge Cases

- A pool consumer who belongs to two sites logs in once. Which site gets it? Probably the site of the initiating client; AC-0 confirms that every login has one.
- A login with no initiating client (direct API, tests) has no site. Fall back to the account's tenant, as today.

# Failure Scenarios

1. The new field is added to the event but security-service keeps counting `tenant_id`. Nothing changes, and the tests pass on non-pool fixtures. AC-2 uses a pool fixture for this reason.
2. `tenantId` is re-defined in place. VelocityRule then groups a pool consumer's failures per site instead of per account tenant, and its thresholds change meaning silently.
