# Task ID

TASK-BE-639

# Title

A platform (`'*'`) operator can assume a tenant that is not `ACTIVE` (`PENDING` since TASK-MONO-773, and `SUSPENDED` before it) — the assume path never reads the tenant status

# Status

done

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

- [x] **AC-0**: the measured matrix (identity × tenant status → assume result → what the token can call), and the chosen design with why. — § AC-0 + § Owner decisions (B).
- [x] **AC-1**: each refused row is refused, and nothing is issued. — `'*'`×PENDING · `'*'`×SUSPENDED · company×SUSPENDED · partnership host×SUSPENDED → 400 `invalid_grant` `tenant_not_active`, no `access_token` (IT, red 4 → green).
- [x] **AC-2**: control — `ACTIVE` tenant assume byte-unchanged; the 773 S4 IT's Q4 pin is updated to the decision. — ACTIVE control green on red and fix; the Q4 pin renamed (`gate_starOperatorPendingTenant_assigned_statusJudgedByAuth`, gate unchanged under B); outage + no-`tenantStatus` controls green.

# Related Specs

- `projects/iam-platform/specs/contracts/http/internal/auth-to-admin.md` (operator-assignments check)
- `projects/iam-platform/specs/contracts/http/auth-api.md` § Assume-Tenant Exchange

# Related Contracts

- Same as above.

# Edge Cases

- A tenant suspended while an assumed token is live — it lives to `exp` + 60 s (BE-632 AC-6 measurement), the same as any revocation.

# Failure Scenarios

1. Adding a synchronous account-service read on every assume without a failure-mode decision — an account-service outage then blocks every console tenant switch.

---

# AC-0 — measurement (2026-10-16 UTC, Opus 5.5)

Measured by IT pins (new ones marked 🆕, CI lanes `Integration (iam A)` / `(iam B)` on this PR) and by reading the code. Not measured live. **No behaviour is changed by this commit**; the pins are measurement pins that flip with the decision.

## The assume path, step by step: where a tenant status could be read, and isn't

| step | service · code | reads tenant status? | on account-service outage |
|---|---|---|---|
| 1. subject token validation | auth `AssumeTenantAuthenticationProvider` (operator branch) | — | unaffected |
| 2. assignment gate | admin `OperatorAssignmentCheckUseCase.resolve` (`:157-242`) — operator row · confinement · `'*'` (`:199-203` → `assigned=true`) · fan-platform rule · memberships · partnership | **no**. It makes no account-service call at all: WireMock `GET /internal/tenants/{slug}` count = 0, asserted in both pins | unaffected (admin-service outage → `invalid_grant`, fail-closed, already) |
| 2c. second factor | admin `OperatorSecondFactorRequirement.requiredForAssume` → `tenant_entry_policy` (admin DB, V0047) | no (admin-local table) | unaffected |
| 3. mint | auth provider: `tenant_type` = constant `CUSTOMER_TENANT_TYPE`, so it does **not** call `GET /internal/tenants/{id}` (the read that returns `status`) | no | — |
| 3a. `entitled_domains` + role ceiling | auth `TenantClaimTokenCustomizer.fetchEntitledDomains` (`:1066-1081`) → account `GET /internal/tenants/{t}/entitled-domains` → `TenantEntitledDomainsQueryUseCase.effectiveEntitledDomains` (`:59-72`): `existsById` · ceiling · ACTIVE **subscriptions** | **no**. Tenant existence only, never the tenant's status (🆕 pin: PENDING / SUSPENDED / ACTIVE all return `[wms]`) | **fail-soft**: the claim is omitted and roles are omitted, but the **token is still issued** |

Downstream: domain gateways and backends authorize on the token (`tenant_id`, `entitled_domains`, `roles`) and do not read tenant status. admin-service's `QueryTenantScopeGate` has no status read either (grep: 0).

## Matrix: identity × tenant status → assume → what the token can call

| identity | tenant | gate (`assigned`) | token issued | what it can call |
|---|---|---|---|---|
| `'*'` operator (SUPER_ADMIN, unconfined) | `PENDING` | **true**. Pinned by `TenantFirstAdminIntegrationTest#measure_starOperatorAssumesPendingTenant` (773 S4) | yes | `entitled_domains` = the tenant's ACTIVE subscriptions (🆕 pin). `roles` = its (op,T) ∪ (op,`'*'`) set rows ∩ that ceiling (BE-632). So it gets the domain APIs of whatever the PENDING tenant is subscribed to, with the `'*'` operator's assigned sets |
| `'*'` operator | `SUSPENDED` | **true** (same step 2, no status input) | yes | same as above: a suspended customer's subscriptions stay ACTIVE rows, so the ceiling is unchanged |
| company operator whose home/member tenant was suspended, membership row kept | `SUSPENDED` | **true**. 🆕 `TenantFirstAdminIntegrationTest#measure_companyOperatorAssumesSuspendedTenant_membershipKept`. `UpdateTenantUseCase` (suspend) never touches `operator_tenant_assignment` (`:130-180`) | yes | **everything it had before the suspension** — its (op,T) sets ∩ unchanged ceiling. 🔴 This is the worse row: the suspended customer's own staff keep working |
| company operator | `PENDING` | true only if a membership row exists. 773's PENDING tenant gets its first admin's rows at acceptance, which activates it in the same flow, so this row is normally empty | (yes if a row exists) | as above |
| confined `'*'` (demo, `confined_tenant_id=fan-platform`) | any other | false (step 1b) | no | — |
| workload (`product-service-client` → `ecommerce`/`demo-corp`) | non-ACTIVE | static `WorkloadTenantCatalog`, no status read | yes | no `entitled_domains`/`roles` (workload branch). Provisioning into a SUSPENDED tenant is refused by account-service `ActiveTenantGuard` (409 `TENANT_SUSPENDED`); `entitled-domains` / `consumer-members` reads still answer |
| any | `ACTIVE` (control) | unchanged | yes | unchanged |

Edge case: a tenant suspended while an assumed token is live keeps working until `exp` + 60 s (≤ ~360 s, BE-632 AC-6).

## The failure mode, as it is today

An account-service outage never blocks a console tenant switch. The gate does not call account-service. The entitlement read is fail-soft, so the switch succeeds with a token that carries no `entitled_domains` and no `roles`, and domain sections 403. Every option below has to say what it does to this.

## Options (for the owner)

- **A. The admin assignment gate reads account-service** (`AccountServiceTenantClient.get`, circuit-breaker `accountService`, already in admin-service). Refuse `assigned=false` unless `status == ACTIVE`.
  - It sits inside the gate, which is already fail-closed: an outage, an open breaker or a timeout means no switch.
  - 🔴 This is exactly Failure Scenario 1: an account-service outage would then block every console tenant switch.
  - It adds one synchronous call per assume, admin → account.
- **B. Use the read the token path already makes** (recommended). Account-service's `entitled-domains` response gains an additive `tenantStatus` field. When the tenant is not `ACTIVE`, it also returns `domainKeys: []`.
  - auth-service refuses the exchange with `invalid_grant` (nothing issued) when it **positively** reads `tenantStatus != ACTIVE`.
  - On outage it keeps today's fail-soft (issued, no entitlement, no roles), so availability is unchanged.
  - Implementation note: the fetch currently runs inside the token customizer (at mint). B moves it into the provider before minting, or lets the customizer throw. One call either way, no new hop.
  - The `domainKeys: []` half is defence in depth: even an auth image that ignores `tenantStatus` gets a token that can call no domain API.
- **C. Leave `'*'` allowed and refuse company operators only.** This doesn't remove the read-location question, because company operators still need a status read (A or B). It only changes who is refused.
- (Rejected: **an admin-local tenant-status mirror.** admin-service performs every status write, so it could keep one, but it needs a migration, a backfill of every existing tenant's status from account-service, and a drift guard. That costs more than B for the same answer.)

## Owner questions + recommendations

1. **Where is the status read, and what happens on account-service outage?**
   - → **B**: refuse when the existing entitlement read positively says non-ACTIVE; on outage stay fail-soft (token issued with no entitlement, so domain APIs 403).
   - Why: it is the only option that closes both refused rows without changing switch availability, and it adds no call.
   - A is simpler code but makes the console's tenant switch depend on account-service.
2. **Is `'*'` refused too?**
   - → **Yes, for both PENDING and SUSPENDED.** A PENDING tenant is managed through admin APIs with the operator token (create, invite, activate — 773). Nothing in that flow needs an assumed domain token, so refusing loses no function.
   - If you want `'*'` to be able to *look at* a SUSPENDED tenant's domain data (support/investigation), say so. Then the rule becomes "SUSPENDED: `'*'` allowed, company refused" (option C for that one status).
3. **Is the company operator of a SUSPENDED tenant refused?**
   - → **Yes.** This is the worse row measured above.
   - Note: the read-location answer covers it. The membership row does not need to change (reactivation restores access without re-granting).
4. **Workload assume into a non-ACTIVE tenant** (`product-service-client`)?
   - → **Out of 639.** Writes are already refused by `ActiveTenantGuard`, and the workload token carries no entitlement. Fold it in only if you want the reads (`entitled-domains`, `consumer-members`) refused as well.
5. **The 773 S4 pin** `measure_starOperatorAssumesPendingTenant`, and the two 🆕 pins.
   - → If you choose B, the admin gate is untouched, so the two admin pins stay `assigned=true` as gate facts. Their names and comments become "the gate does not judge status — auth-service does (BE-639)", and the refusal is pinned in an auth-service IT instead.
   - If you choose A, both admin pins flip to `false` (and the "0 account-service reads" assertion inverts).
   - The account-service 🆕 pin flips under B (`domainKeys: []` for non-ACTIVE).

---

# Owner decisions (2026-10-16 UTC)

| Q | Decision | Why (as recorded) |
|---|---|---|
| Q1 | **B.** account-service `GET /internal/tenants/{id}/entitled-domains` gains an additive `tenantStatus` field and returns `domainKeys: []` when the tenant is not `ACTIVE`. auth-service refuses the operator assume exchange (`400 invalid_grant`, `error_description=tenant_not_active`, nothing issued) **only** when that read positively says not `ACTIVE`. On account-service outage it stays fail-soft (token issued, no `entitled_domains`, no `roles`). No new call. | Closes both refused rows without tying the console tenant switch to account-service availability (Failure Scenario 1). The `[]` half is the old-auth-image safety: an auth image that ignores `tenantStatus` still mints zero domain reach for a non-ACTIVE tenant. |
| Q2 | `'*'` operators are refused too, for both `PENDING` and `SUSPENDED`. | A PENDING tenant is set up through admin APIs with the operator token (773). No flow needs an assumed domain token there. |
| Q3 | The company operator of a `SUSPENDED` tenant is refused. Membership rows are kept. | Reactivation restores access without re-granting. |
| Q4 | Workload client assume stays **out** of 639. | The workload token carries no `entitled_domains` or `roles`, and writes into a non-ACTIVE tenant are already refused by account-service `ActiveTenantGuard` (409 `TENANT_SUSPENDED`). The workload branch does not make the entitled-domains read, so the new gate does not apply to it. Re-open only if the workload reads (`entitled-domains`, `consumer-members`) must be refused too. |
| Q5 | Tests "under B": the two admin pins are renamed to say auth-service judges status (the admin gate is unchanged). The account-service pin flips to `domainKeys: []` for non-ACTIVE (ACTIVE control unchanged). A new auth-service IT pins the refusal for `'*'`×PENDING, `'*'`×SUSPENDED, company×SUSPENDED (+ partnership host reach×SUSPENDED), plus the controls: ACTIVE unchanged, account-service unavailable → issued with no roles, response without `tenantStatus` → unchanged. | — |

Consequences recorded with the decision (contract `account-tenant-domain-subscriptions.md`):
- **User tokens.** `authorization_code` / `refresh_token` tokens of a non-ACTIVE tenant's users also lose `entitled_domains`. That path reads the same endpoint, and it does not refuse.
- **admin-service writes.** admin-service's work-permission-set write gate (`DomainSetEntitlementGuard`) answers `422 DOMAIN_NOT_SUBSCRIBED` for set create, edit or grant into a non-ACTIVE tenant until it is reactivated.
- **Deploy order.** account first, then auth; both ride the same bake.
  - Old account image + new auth: unchanged behaviour (no `tenantStatus` → not refused).
  - New account image + old auth: issued, but with no reach.

---

# Implementation (2026-10-16 UTC, PR #4392)

- **account-service.**
  - `TenantEntitledDomainsQueryUseCase.entitlement(tenantId)` returns `{domainKeys, tenantStatus}`. A non-ACTIVE tenant answers `[]` before any subscription or ceiling read.
  - `TenantEntitledDomainsController` adds `tenantStatus` to the response.
  - `effectiveEntitledDomains` delegates to it, so it is consistent for every in-process caller.
- **auth-service.**
  - `AccountServiceClient.listEntitledDomains` parses `tenantStatus`. It throws the new `TenantNotActiveException` **after** the resilient call, so the answer is not a circuit-breaker failure, is not retried, and is not re-wrapped as "unavailable". A missing field counts as not-a-no.
  - `TenantClaimTokenCustomizer`: the operator assume branch makes **one** read (`fetchEntitledDomainsForOperatorAssume`) for both the membership/`'*'` path and the partnership host-reach path.
    - On `TenantNotActiveException` it throws `invalid_grant` + `tenant_not_active` before the JWT is encoded, so nothing is issued and no authorization is saved.
    - On any other failure it stays fail-soft.
  - The other callers of the read (`authorization_code` / `refresh_token` / pool principal) catch it as "no entitled domains".
  - The workload branch is untouched (Q4).
- **admin-service.** No production change; the two gate pins are renamed (Q5).
- **Contracts.**
  - `account-tenant-domain-subscriptions.md` § entitled-domains: `tenantStatus`, the `[]` rule, the old-auth-image note, and the consequences for other callers.
  - `auth-api.md` § Assume-Tenant Exchange: the tenant status gate and the Errors row `tenant_not_active`.
  - `platform/error-handling.md` `OAUTH_INVALID_GRANT`: the constant is registered.

## Red → green (counted from CI lane logs)

| commit | lane / job | result |
|---|---|---|
| `756b0393a` (tests only) | iam B `116987571776` | `auth-service:integrationTest` 165 run, **4 failed**, all `AssertionFailedError expected: 400 but was: 200` — `'*'`×PENDING, `'*'`×SUSPENDED, company×SUSPENDED, partnership host×SUSPENDED (a token was issued). The 3 controls passed. account-service did not run (Gradle stops at the first failing module) |
| `756b0393a` | iam A `116987572283` | the 2 renamed admin gate pins passed (admin gate unchanged) |
| `01ac282b0` (auth fix only — pushed alone so the lane reaches account) | iam B `116992202662` | auth 165 / 0 failed. `account-service:integrationTest` 160 run, **3 failed**, all assertion failures: `Expected an empty value at JSON path "$.domainKeys" but found: ["wms"]` ×2 (PENDING, SUSPENDED) and `No value at JSON path "$.tenantStatus"` ×1 (the ACTIVE control's additive field) |
| `a3731ad5e` (account fix) | iam B `116995775518` | auth 165 · account 160 · gateway 43, **0 failed** (10 × BE-639 `PASSED`) |
| `a3731ad5e` | iam A `116995775591` | admin 333 · security 24, 0 failed |
| `a3731ad5e` | Build & Test `116995770826` | 0 failed (iam auth/account/admin unit tests executed) |

All 35 checks on `a3731ad5e` that were not skipped passed.

## Branch e2e (assume path changed)

- **federation-hardening-e2e**, run `38907749786`, job `116996176977`: **20 passed, 1 flaky**. The flaky one is `tenant-switch-rescope.spec.ts:110` — the known TASK-BE-516 flake. Its first-try error was `scm leg must be HEALTHY (ok) on globex…`, and `tenant_not_active` appears 0 times in the log.
- **nightly-e2e**, run `38907755999`: all jobs success.
  - Platform Console full-stack (`116995904604`): 12 passed.
  - web-store full-stack (`116996183879`): 14 passed.
  - iam full (`116995551612`): 3/3.
  - scm (`116995551800`): 11/11.
  - fan (`116995551750`): 4/4.
  - ecommerce×wms (`116995551832`): 2/2.
  - wms gateway-master (`116995551572`): 2/2.

## ⚪ Open

- **Partnership host reach — old auth with new account.** The delegated slice's roles are carried verbatim even when `entitled_domains` is empty, so "old auth image + new account image" does **not** zero a partnership token's roles for a SUSPENDED host. The new auth refuses it. This is the same as today's outage behaviour for that path. Both images ride the same bake, so the window does not exist on the demo host.
- **Not live.** Live verification → next demo window (bake row in the report). → done in the 28th window, below.

## 28th window live (2026-10-16 UTC)

- AMI `ami-00ef8cc1057cca59b` (`RepoCommit` `8905d2c29`; `0f544dd64` is an ancestor) — `iam-auth-service` and `iam-account-service` from the same bake (no split).
- **Company operator × `SUSPENDED`** ✅ → `400 invalid_grant`, `error_description` `tenant_not_active`, no token; the membership / grant snapshot is unchanged. Two samples: `w28-pending`'s first admin and `w28-shellco`'s creator (both throwaway tenants, suspended in the window — never `demo-corp`).
- **Reactivate** ✅ → the next exchange is `200`, roles byte-identical, nothing re-granted.
- **account-service direct** ✅ `GET /internal/tenants/<suspended>/entitled-domains` → `{"domainKeys":[],"tenantStatus":"SUSPENDED"}`; `demo-corp` `ACTIVE` with its keys. The gateway response carries `tenantStatus` too.
- **Controls** ✅ the workload `product-service-client` assumed into `ecommerce` is unchanged (`entitled-domains` 200 with `tenantStatus":"ACTIVE"`, `TASK-BE-638` live); `demo@demo.com` assumes `ACTIVE` `demo-corp` with all its roles (`TASK-MONO-792`~`795` AC-2 live).
- ⚪ **`'*'` × `PENDING` / `SUSPENDED` (plan check 1)** — the demo has no unconfined `'*'` operator with an IdP login. `admin` / `dev1` are `'*'` with a placeholder `oidc_subject` and no credential; `platform@demo.com` is confined to `fan-platform` and is refused at the assignment gate **before** the status read, so its refusal is the confinement, not `tenant_not_active`. These rows stay covered by the auth IT (AC-1); the live check is carried by `TASK-MONO-672` § Order «Still open» 1.
- ⚪ Optional failure mode (account-service stopped) — not run.
- Recorded also in `TASK-MONO-672` § 28th window — harvest.

## Close (2026-10-16 UTC) — review → done

- (a) `gh pr view 4392`: `state=MERGED`, `mergedAt` 2026-10-16T13:54:00Z, merge commit `0f544dd64`. (b) An ancestor of `origin/main` (`1c8ee22cb`). (c) 0 failing checks at merge (35 pass / 34 skipped).
- (d) AC section read against each verb: AC-0 «measured matrix + chosen design» ✅ · AC-1 «each refused row is refused, nothing issued» — IT for all four rows (red 4 → green), and live for company × `SUSPENDED` ✅ · AC-2 «control — `ACTIVE` byte-unchanged; Q4 pin updated» — IT, plus live reactivation byte-identical ✅. None of the ACs asks for the live `'*'` row, so its ⚪ does not hold the ticket open.
