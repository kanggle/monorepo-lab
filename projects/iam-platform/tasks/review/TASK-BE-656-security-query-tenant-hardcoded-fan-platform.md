# Task ID

TASK-BE-656

# Title

security-service login-history and suspicious-event reads filter by a hard-coded `fan-platform` tenant — an operator's audit view of any account outside `fan-platform` comes back empty

# Status

review

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

- [x] **AC-0** — the measurements above, file:line, with the observed empty result (or ⚪ + reason).
- [x] **AC-1** — contract first: `security-query-api.md` states the tenant parameter, `*` semantics, and the pool-account rule.
- [x] **AC-2** — a non-`fan-platform` account's login history and suspicious events are returned. Red before the change.
- [x] **AC-3** — isolation: a query scoped to tenant A never returns tenant B's rows. Control group: a `fan-platform` account's result is unchanged.

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

---

# AC-0 — measured 2026-10-25 UTC on origin/main `6c5462e38`

| # | Question | Finding |
|---|---|---|
| 1 | The defect | `SecurityQueryService.java:41` (suspicious) and `:72` (login history) set `tenantId = Tenants.DEFAULT_TENANT_ID` (`fan-platform`). Neither controller takes a tenant (`LoginHistoryQueryController.java:22-28`, `SuspiciousEventQueryController.java:27-33`). |
| 2 | What admin-service knows | `AuditQueryUseCase.java:59-62` — `QueryTenantScopeGate.resolve(…, "audit.read")` gives `requestedTenantId` (`*` for an omitted tenant under a `'*'` grant, `QueryTenantScopeGate.java:138-139`). It is used for the `admin` source only; `SecurityServiceClient.java:115-122` sends `accountId · from · to` and nothing else. The same client already sends a gated `tenantId` (`*` = every tenant) for `metrics/daily` (`:72-88`) — the precedent this fix follows. |
| 3 | Contract today | `security-query-api.md` § login-history (`:17-26`) and § suspicious-events (`:70-79`) say nothing about tenant. |
| 4 | Pool rows | Login rows: `tenant_id = consumer-pool` + `site_tenant_id` = the site (`AuthEventMapper.java:42-56`; only rows after TASK-BE-653's deploy, no backfill). Suspicious rows: `tenant_id` = the event's tenant, **no site column** (`AuthEventMapper.java:112-118` → `SuspiciousEventPersistenceService.java:37`). |
| 5 | Observed empty result | ⚪ not measured live — no Docker on the host and no demo window (owner: not needed). Stood in for by tests that fail on the pre-change behaviour (mutation, below). |
| 6 | 🔴 Found while measuring | admin-service reads a suspicious event as `eventId · signalType · ipMasked · occurredAt` (`SecurityServiceClient.java:162-168`), but security-service answers per contract `id · ruleCode · riskScore · actionTaken · evidence · detectedAt` (`SuspiciousEventView.java`). Unknown fields are ignored, so every suspicious row reaches the audit view with a null id, type and time. `SecurityServiceClientUnitTest` stubbed the wrong shape (`signalType`), so it stayed green. |

# Owner decisions — 2026-10-25 UTC (AskUserQuestion)

- **Pool accounts → «그 사이트 것만» (recommended).** Login history = `tenant_id = X OR site_tenant_id = X` (the TASK-BE-653 metrics predicate). Suspicious events = `tenant_id = X` only — a pool account's suspicious events are account-level, like its account locks (TASK-BE-653 AC-1), so they show under `consumer-pool` and `*`, not under a site. Accepted cost: a pool login recorded before TASK-BE-653's deploy has no `site_tenant_id` and does not show in a site's view.
- **DTO mismatch → in this ticket (recommended).** admin-service's suspicious-event DTO is aligned to the contract (`id` · `ruleCode` · `detectedAt`); the stub is changed to the contract shape.

# Red before the change — measured by mutation (2026-10-25 UTC)

The fix changes signatures, so «stash the fix, run the tests» only fails to compile — not a verdict. Each mutation below puts one old behaviour back into commit `450b7ae4f`, runs the named tests (`--rerun`), then restores the file (`git status` clean after each).

**Local (unit · slice · WireMock — scratchpad `mutate.py`):**

| Mutant | What it puts back | Red | Stayed green (control) |
|---|---|---|---|
| L1 | security service hard-codes `fan-platform` (the original defect) | `SecurityQueryServiceTest` — tenant pass-through ×2, `'*'` ×2 | blank tenant refused |
| L2 | security service always uses the all-tenant finder (failure scenario 2) | tenant pass-through ×2 | `'*'` ×2, blank refused |
| L3 | admin use case sends `fan-platform` instead of the gated tenant (failure scenario 1) | `AuditQueryUseCaseTest` — gated tenant reaches both reads · platform scope | the other 15, incl. the suspicious-mapping test |
| L4 | admin client sends no `tenantId` | `SecurityServiceClientUnitTest` — tenantId sent · `'*'` sent | the other 6 |
| L5 | admin DTO reads the old wire names (`eventId` · `signalType` · `occurredAt`) | contract-shape suspicious test | the other 7 |

**CI only (MySQL IT `SecurityQueryTenantIntegrationTest` — no Docker on this host):** draft PRs, closed unmerged and their branches deleted. Read from each `Integration (iam A, Testcontainers)` job log (stack-trace lines of the class):

| Mutant | What it puts back | Red | Stayed green (control) |
|---|---|---|---|
| A #4538 | the original defect (= L1) | AC-2 login · AC-2 suspicious · login isolation · suspicious isolation · pool · `'*'` | `fanPlatform_control` · blank refused |
| B #4539 | no tenant filter (= L2, failure scenario 2) | login isolation · suspicious isolation · pool · `fanPlatform_control` | AC-2 ×2 · `'*'` · blank refused |
| C #4540 | login-history JPQL without `OR site_tenant_id = :tenantId` | pool only | the other 7 |

Each mutant reddens exactly the tests that name its behaviour. On the fix (#4537 `450b7ae4f`) the same lane is SUCCESS with `security-service:integrationTest` 42 · 0 fail · 0 error · 0 skip (TASK-BE-653 had 34; +8 = this class).

🔵 Unrelated, recorded only: `SecurityServiceClientCircuitBreakerTest.recovers_to_closed_after_half_open_success` is red on **this host** on unchanged `main` too (3/3 runs, `CancellationException` → `DownstreamFailureException`). On the branch 1 of 3 local runs passed. CI `Build & Test (JDK 21, Linux)` is SUCCESS on #4537, so it is host-local, not this change.

# Acceptance Criteria — closed 2026-10-25 UTC

- AC-0 ✅ — § AC-0 above (file:line). The live «observed empty result» is ⚪: no Docker, no demo window (owner: not needed). It is replaced by mutant A, which brings the hard-coded tenant back and turns both AC-2 tests red on MySQL.
- AC-1 ✅ — `security-query-api.md` § login-history / § suspicious-events: required `tenantId`, `*` = no filter, no default (missing → 400), «테넌트» paragraphs with the pool rule. `admin-api.md` § GET /api/admin/audit `tenantId` row + suspicious example. Service specs' endpoint lines carry `tenantId=`.
- AC-2 ✅ — security: `loginHistory_nonFanPlatformAccount_returned` · `suspicious_nonFanPlatformAccount_returned` (MySQL; red under A). End to end through admin-service (failure scenario 1): `AuditQueryUseCaseTest.securitySources_receiveGatedTenant` (red under L3) + `SecurityServiceClientUnitTest.queries_sendTenantId` (red under L4). Suspicious rows now carry id · type · time (`querySuspiciousEvents_200_contractShape_returnsList`, red under L5).
- AC-3 ✅ — isolation `loginHistory_isolation` · `suspicious_isolation` (red under A and B). Control `fanPlatform_control` (green under A — the old behaviour served fan-platform; red under B — no filter leaks the other tenant's row). Pool rule `poolAccount_siteSeesOnlyItsOwnLogins` (red under A, B, C). Platform-scope `allTenants_noFilter`. The existing full-stack `SecurityServiceIntegrationTest` (Kafka → login_history → query as fan-platform) is SUCCESS in the same lane.
