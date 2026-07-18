# TASK-FIN-BE-048 — finance READ entitlement-trust authority parity (account + ledger)

- **Type**: TASK-FIN-BE (backend — Spring Security authorization gate)
- **Status**: review
- **Service**: finance-platform `account-service` + `ledger-service` (identical gate in each)
- **Domain/traits**: saas / [transactional]
- **Analysis model**: Opus 4.8 · **Impl model**: Opus (security-critical authorization gate)

## Goal

Close the finance-side authority-layer straggler that leaves an **entitled customer operator** with a
`forbidden` finance overview card. Grant a READ-only `ROLE_FINANCE_VIEWER` authority to a token whose
RS256/JWKS-verified `entitled_domains` claim contains `finance`, so its READS pass the `/api/finance/**`
authorization gate — while WRITES stay gated on `finance.write` scope / an operator role. This is the
finance analogue of the WMS `ROLE_WMS_VIEWER` synthesis (**TASK-MONO-162**, ADR-MONO-019 §D5 /
ADR-MONO-020 D4). It pins the runtime cause of iam-platform **TASK-BE-518** candidate 2 (finance-side
scope/role gate) and closes the FIN-BE-046/047 read-scope straggler.

## Runtime evidence (federation run `29632072800`, DIAG logging, now discarded)

The federation `entitlement-trust-crossdomain` E2E asserts an entitled acme-corp operator
(`entitled_domains=[finance,wms]`) can READ the finance overview card. It failed — finance card
`forbidden`:

- acme operator's OIDC domain-facing token: `tenant_id=acme-corp`, `entitled_domains=[finance, wms]`,
  `scope=[openid,profile,email,tenant.read]`, **NO roles claim** (`resolvedRoles=[]`).
- console-bff finance leg: `financeDefaultAccountId present=true` → HTTP GET made →
  `leg=FINANCE received HTTP 403; responseBody={"code":"PERMISSION_DENIED"}`.
- Same token to WMS leg: **passes** (admin-service grants `ROLE_WMS_VIEWER` on `entitled_domains ∋ wms`).
- ERP/SCM legs: `TENANT_FORBIDDEN` at the tenant gate — correct, acme is not entitled to those.

**Why finance 403s:** finance `account-service` + `ledger-service` `SecurityConfig` gate
`/api/finance/**` reads on `readAuthorities = [SCOPE_finance.read, SCOPE_finance.write, ROLE_OPERATOR,
ROLE_ADMIN, ROLE_SUPER_ADMIN, ROLE_FINANCE_OPERATOR]`. The entitled operator's base token holds NONE of
these → 403 at layer-2 (authorization), even though it PASSED layer-1 (the tenant gate
`TenantClaimValidator.forTenant("finance").trustEntitledDomains()` admits it because `entitled_domains`
contains `finance`). Finance applied entitlement-trust at the TENANT layer only (TASK-FIN-BE-006 pilot)
but never at the AUTHORITY layer — so when the read-scope hardening landed (FIN-BE-046 account /
FIN-BE-047 ledger) finance became the straggler WMS never was.

## Scope

- **In**: `ActorContextJwtAuthenticationConverter` (both services) — synthesise `ROLE_FINANCE_VIEWER`
  when `TenantClaimValidator.isEntitled(jwt, "finance")`; `SecurityConfig` (both services) — add
  `ROLE_FINANCE_VIEWER` to `readAuthorities` **only** (never `writeAuthorities`); class Javadoc updated
  to document the entitlement-trust read path; converter unit tests + HTTP scope-enforcement ITs
  extended to prove read-passes / write-denied for the entitled-scopeless token.
- **Out**: the tenant gate / layer-1 (`TenantClaimValidator.forTenant("finance")` wiring — unchanged);
  IAM token issuance / `entitled_domains` population (that is upstream, already working per the runtime
  evidence); the console-bff / federation e2e seed; write-authority changes; new endpoints/contracts.

## Acceptance Criteria

- **AC-1**: a finance-entitled token (`entitled_domains ∋ finance`) with NO finance scope and NO role →
  the converter grants exactly `ROLE_FINANCE_VIEWER` (and no `SCOPE_*`), in BOTH services.
- **AC-2**: same token → GET `/api/finance/**` PASSES the authorization gate (not 403), in BOTH services.
- **AC-3**: same token → POST/PUT/PATCH/DELETE `/api/finance/**` → **403 `PERMISSION_DENIED`** (write gate
  intact — `ROLE_FINANCE_VIEWER` is in `readAuthorities` only), in BOTH services.
- **AC-4**: a non-finance-entitled token gets no `ROLE_FINANCE_VIEWER` (entitled-elsewhere `[wms]` and
  absent-claim both yield no VIEWER); the tenant gate (layer-1) is unchanged.
- **AC-5**: mutation-check — removing the `isEntitled → grant VIEWER` line turns the read-allowed
  assertion RED in each service (verified: account 2 assertions RED, ledger RED; restored via Edit).
- **AC-6**: `:account-service:check` and `:ledger-service:check` GREEN.

## Related Specs

- `projects/finance-platform/apps/account-service` architecture (Hexagonal, ADR-MONO-012) — security
  layer in `infrastructure/security`.
- ADR-MONO-019 §D5 (entitlement-trust dual-accept), ADR-MONO-020 D4 (viewer synthesis), TASK-MONO-162
  (WMS `ROLE_WMS_VIEWER` precedent — the exact pattern mirrored).
- TASK-FIN-BE-006 (finance tenant-gate entitlement-trust pilot — layer-1 only, the gap this closes at
  layer-2), TASK-FIN-BE-046 / TASK-FIN-BE-047 (the read-scope hardening that made finance a straggler).

## Related Contracts

- None changed. `iam-integration.md § Token 검증 규칙 #5` (downstream `finance.read`/`finance.write`
  enforcement) is unaffected — role/scope callers behave identically; only an entitled-but-scopeless
  token's READ visibility widens. No new error codes (`PERMISSION_DENIED` already registered).

## Edge Cases

- Malformed `entitled_domains` (non-list / non-string element / empty) → `isEntitled` fail-closed →
  no VIEWER (covered by `TenantClaimValidator`'s own suite; the converter inherits it).
- A token entitled to `[wms]` only reaching a finance gate is rejected at layer-1 already; even if it
  reached layer-2 it would get no finance VIEWER (AC-4).
- A SUPER_ADMIN / operator-role / scope-bearing token is unaffected — VIEWER is additive and read-only.

## Failure Scenarios

- **VIEWER leaks into writes**: guarded by AC-3 + a dedicated write-403 assertion; `writeAuthorities`
  deliberately excludes `ROLE_FINANCE_VIEWER`. If a future edit adds VIEWER to writes, the write-403 IT
  goes RED.
- **Unconditional VIEWER grant**: guarded by AC-4 (non-entitled → no VIEWER); the grant is gated strictly
  on `entitled_domains ∋ finance`, exactly as WMS gates on `∋ wms`.

## Verification note

BE-518 (iam-platform, stays in `ready/`) closes only when the federation `entitlement-trust-crossdomain`
finance card goes green in CI post-merge — this task is the finance-side fix for its candidate 2.
