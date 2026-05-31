# Task ID

TASK-MONO-158

# Title

ADR-MONO-020 § 3.3 step 3 (D4) — console-web **active-tenant switcher → assume-tenant flow**: on switcher selection, console-web (server-side) drives the D2 assume-tenant RFC 8693 exchange to mint a tenant-scoped GAP OIDC token for the selected customer, stores it in the operator's HttpOnly session, and uses it as the **domain-facing bearer** for all tenant-scoped reads (BFF stays a verbatim pass-through, ADR-017 D6). **Multi-assignment demo operator seed** (operator assigned to 2 customers) + **federation-e2e A↔B switch spec** proving the switch re-scopes the token and the domain entitlement gates follow.

# Status

done

# Owner

backend

# Task Tags

- code
- security
- multi-tenant
- frontend
- e2e

---

# Dependency Markers

- **depends on**: ADR-MONO-020 ACCEPTED (MONO-157 `de68ab03`) § 2 **D4** + § 3.3 **step 3**; **TASK-BE-326 DONE** (D1 `operator_tenant_assignment` + `TenantScopeResolver`; ConsoleRegistry effective-scope already drives the switcher's selectable tenants) ; **TASK-BE-327 DONE** (`de61856b` — D2+D3 assume-tenant exchange on auth-service SAS `/oauth2/token` + admin `/internal/operator-assignments/check`; this task is the console-web **consumer** of that exchange).
- **builds on**: ADR-MONO-014 (`operator-token-exchange.ts` — the existing server-side RFC 8693 exchange this siblings; the assume-tenant exchange is a *second* server-side exchange) ; ADR-MONO-017 D6 (BFF pass-through — **no console-bff change**, PC-BE-007 proven) ; ADR-MONO-019 runtime activation (`TASK-MONO-154` entitlement-trust spec + `loginAsAcmeOperator` fixture + e2e `seed.sql`/`seed-domains.sql` — this extends them).
- **enables (후속, 별 task)**: **D6 step 4** — retire the legacy single-value `admin_operators.tenant_id` read once operators are fully migrated to assignments.
- **orthogonal to**: ADR-005 / workload identity.
- **model**: 분석=Opus 4.8 / **구현 권장=Opus** (멀티-레이어 vertical slice: server-side 토큰 boundary + session + 5 domain-read surface + 2-service e2e seed + A↔B e2e; 토큰 boundary 보안 임계). Sonnet 가능 부분 = 순수 seed/e2e wiring.

---

# Goal

Make the console **active-tenant switcher** functionally re-scope the operator's domain-facing credential. Today the switcher only sets the `console_active_tenant` cookie (X-Tenant-Id); the signed GAP OIDC token forwarded for domain reads keeps the **login** `tenant_id`/`entitled_domains`, so a multi-assignment operator cannot actually view a *different* customer's data (the domain entitlement gates trust the **signed token claims**, not X-Tenant-Id). D4 closes this: on switcher selection, console-web **server-side** calls the D2 assume-tenant exchange (subject = the operator's base GAP OIDC token, audience = the selected tenant) → receives a short-lived GAP OIDC token scoped to the selected customer (`tenant_id=<selected>` + `entitled_domains=<selected's ACTIVE subs>`) → stores it in the HttpOnly session → uses it as the **domain-facing bearer**. The BFF forwards it verbatim (ADR-017 D6, unchanged). Prove the whole loop with a **multi-assignment demo operator** (assigned to two customers with *complementary* entitlements) and a **federation-e2e A↔B switch** spec: switching customer A↔B flips which domain cards are entitled.

## Architecture (decided; record in PR)

- **Two server-side exchanges, both already specced**: (1) operator-identity exchange (ADR-014, `operator-token-exchange.ts`, JSON `POST /api/admin/auth/token-exchange`, → operator token for `/api/admin/**`); (2) **assume-tenant exchange** (ADR-020 D2, **form-urlencoded `POST ${OIDC_ISSUER_URL}/oauth2/token`**, `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` + `subject_token=<base GAP OIDC access token>` + `subject_token_type=urn:ietf:params:oauth:token-type:access_token` + `audience=<selected tenant>`, → short-lived domain-facing GAP OIDC token). **Note the wire-format difference**: assume-tenant is SAS `/oauth2/token` **form-urlencoded** (auth-api.md), NOT the admin JSON shape.
- **Domain-facing credential resolution (the central change)**: introduce `getDomainFacingToken()` in `shared/lib/session` = **the assumed token if an active-tenant assumption exists, else the base `getAccessToken()`** (net-zero: a non-switched / single-tenant operator keeps using the base token exactly as today). Every tenant-scoped domain read uses `getDomainFacingToken()` for its GAP-OIDC bearer (the per-domain credential rule §2.4.5 unchanged — only WHICH GAP OIDC token).
- **BFF unchanged** (ADR-017 D6): console-bff still forwards `Authorization: Bearer <token>` + `X-Tenant-Id` verbatim; the only change is console-web now puts the **assumed** token + the selected tenant there. **0 byte in console-bff** (PC-BE-007 pass-through proven).
- **GAP admin operations unchanged**: the operator token (`getOperatorToken()`, ADR-014) and its `admin_operators.tenant_id`/effective-scope authorization are NOT re-scoped by this task — D4 is the **domain-facing** (wms/scm/finance/erp + cross-domain overview) credential. GAP `/api/admin/**` keeps using the operator token. (Switching customer changes which domain data the operator sees; their operator-identity authorization is a separate, already-shipped concern.)

---

# Scope

## In scope — console-web (the switcher → assume-tenant flow)

1. **`assume-tenant-exchange.ts`** (`shared/lib/`, server-only, sibling of `operator-token-exchange.ts`): `exchangeForAssumedToken(gapAccessToken, selectedTenant): Promise<AssumedToken>`. Calls `${OIDC_ISSUER_URL}/oauth2/token` **form-urlencoded** per auth-api.md (token-exchange grant + `audience=<selectedTenant>`). Parses the SAS token response (`access_token`, `expires_in`, `token_type=Bearer`, **no `refresh_token`**). **fail-closed mapping** (mirror operator-exchange semantics): `400 invalid_grant` (assignment-denied / subject-invalid — the D2 fail-closed gate) → `AssumeTenantError('denied')` (the switch is rejected, prior selection preserved); `400 invalid_request` (missing/blank audience) → `'invalid'`; 5xx/timeout/network/bad-shape → `'unavailable'`. **Never log the token; never fall back to the base token on the selected-tenant boundary.**
2. **Session** (`shared/lib/session.ts`): new `ASSUMED_TOKEN_COOKIE` (HttpOnly/Secure/Lax, same opts) + `getAssumedToken()` + **`getDomainFacingToken()` = `(await getAssumedToken()) ?? (await getAccessToken())`** (the central resolver). `isAuthenticated()` unchanged (assumed token is NOT required for a usable session — net-zero). Document that the assumed token is scoped to the **current** `console_active_tenant` by construction (the switch sets both atomically; clearing the tenant clears the assumed token).
3. **`/api/tenant` switch route** (`app/api/tenant/route.ts`): after the existing registry-membership allow-check (keep it — defence-in-depth), call `exchangeForAssumedToken(base, tenant)`; on success set BOTH `ASSUMED_TOKEN_COOKIE` (maxAge = `expires_in`) and `TENANT_COOKIE=tenant` atomically; on `'denied'` → `403 TENANT_FORBIDDEN` (no cookie change); on `'invalid'` → `422`; on `'unavailable'` → `503 DOWNSTREAM_ERROR`; on missing base token → `401 TOKEN_INVALID`. `tenant=''` clear-path: delete BOTH `TENANT_COOKIE` and `ASSUMED_TOKEN_COOKIE`.
4. **Domain-facing reads adopt `getDomainFacingToken()`** (mechanical swap of `getAccessToken()` → `getDomainFacingToken()` for the GAP-OIDC **bearer** only): the cross-domain overview proxy (`app/api/console/dashboards/operator-overview/route.ts` `Authorization: Bearer`) + the 4 domain section clients (`features/{wms-ops,scm-ops,finance-ops,erp-ops}/api/*-api.ts`). **GAP-domain clients** (`features/{accounts,audit,operators,dashboards}` → `getOperatorToken()`) are **unchanged** (operator-token path, not domain-facing). Keep the per-domain credential rule §2.4.5 invariant intact (only the GAP-OIDC token source changes).
5. **Refresh** (`app/api/auth/refresh` or equivalent): when an active tenant is set, after re-exchanging the operator token (existing), **re-assume** the active tenant (refresh the assumed token from the refreshed base GAP token). On re-assume failure → drop the assumed token + active tenant (operator falls back to base/no-tenant; never a stale assumed token).
6. **env** (`shared/config/env.ts`): the assume-tenant URL = `${OIDC_ISSUER_URL}/oauth2/token` (derive, or add an explicit `CONSOLE_ASSUME_TENANT_URL` mirroring `CONSOLE_TOKEN_EXCHANGE_URL` — implementer's choice; document). `.env.example` + compose updated if a new var is added.
7. **Unit tests** (vitest): `assume-tenant-exchange.test.ts` (form-urlencoded body verbatim; denied/invalid/unavailable mapping; token never logged; no base-token fallback) + `/api/tenant` route (success sets both cookies; denied→403 no cookie; clear deletes both) + `getDomainFacingToken()` (assumed present → assumed; absent → base = net-zero) + a per-domain-credential regression case (domain bearer = domain-facing token, GAP clients still operator-token). 0 regression to the existing suites.

## In scope — multi-assignment demo seed (GAP + e2e fixtures, one atomic PR)

8. **Second customer tenant + complementary subscriptions**: a customer (e.g. **`globex-corp`**) with ACTIVE subscriptions **complementary** to acme-corp's `[finance,wms]` — e.g. **`[scm,erp]`** — so an A↔B switch flips the entitled set. Seed into `account_db` (`tenants` + `tenant_domain_subscription`) via the e2e fixtures (`tests/federation-hardening-e2e/fixtures/seed-domains.sql` or `seed.sql` — mirror how acme-corp's subscriptions are seeded; acme-corp itself is GAP Flyway V0019/V0020, the 2nd customer is e2e-fixture-scoped, NOT a new production migration unless the implementer justifies otherwise).
9. **Multi-assignment operator**: a new operator (e.g. **`multi-operator@example.com`**) in `auth_db.credentials` + `admin_db.admin_operators` (home tenant = one customer, e.g. `acme-corp`, or a platform value) + **two `operator_tenant_assignment` rows** (D1 table) assigning it to **both** `acme-corp` and `globex-corp` + role binding (console-shell reachable). Add to `tests/federation-hardening-e2e/fixtures/seed.sql` (mirror the acme-corp operator sections 6/7/8). **Confirm** the ConsoleRegistry effective-scope (BE-326) surfaces both customers in this operator's switcher (no console change needed — BE-326 already wired it).
10. **e2e login fixture**: add `loginAsMultiOperator(context)` to `tests/federation-hardening-e2e/fixtures/login.ts` (mirror `loginAsAcmeOperator`; the active-tenant cookie is NOT pre-set — the spec drives the switch).

## In scope — federation-e2e A↔B switch spec (root harness)

11. **`tests/federation-hardening-e2e/specs/tenant-switch-rescope.spec.ts`** (new; mirror `entitlement-trust-crossdomain.spec.ts` structure + selectors): login as the multi-assignment operator → drive the **switcher to customer A (`acme-corp`)** (real `/api/tenant` POST, production-identical — triggers the assume-tenant exchange server-side) → `/dashboards/overview` → assert **finance/wms NOT forbidden, scm/erp forbidden** → **switch to customer B (`globex-corp`)** → assert the **inverse** (scm/erp NOT forbidden, finance/wms forbidden). The A↔B flip is the discriminator that proves the assumed token re-scopes the signed claims (not just X-Tenant-Id). Use the existing `operator-overview-card-{domain}` + `data-status="forbidden"` + `-forbidden` placeholder selectors. **Verification channel = nightly + `workflow_dispatch`** (NOT PR-triggered) — verified **post-merge** via `gh workflow run federation-hardening-e2e.yml` (same as MONO-154; the PR CI gate covers unit/build only).

## In scope — contracts/specs

12. `projects/platform-console/specs/contracts/console-integration-contract.md`: new normative subsection (active-tenant switcher → assume-tenant flow: server-side exchange on selection, assumed token = domain-facing bearer, BFF pass-through preserved, fail-closed switch semantics, clear-path) + the assume-tenant exchange consumer obligation (auth-api.md `/oauth2/token` token-exchange producer cross-ref — consume, don't redefine). Update `projects/platform-console/specs/services/console-web/architecture.md` (the assumed-token session + domain-facing resolver + the two-exchange model).

## Out of scope (do NOT touch)

- **console-bff** — 0 byte (ADR-017 D6 pass-through; PC-BE-007). Touching it fails scope-lock.
- **The D2 auth-service exchange / admin assignment-check** (BE-327) — consume only, no producer change.
- **GAP operator-identity authorization** (operator token scope / `admin_operators.tenant_id` / D1 gating sites) — unchanged; D4 is the domain-facing credential only.
- **The existing `loginAsAcmeOperator` single-tenant entitlement spec** (MONO-154) — keep byte-unchanged (the new A↔B spec is additive; do not break the existing one).
- **D6 step 4** legacy-read cleanup; **production demo seeds** beyond the e2e fixtures (a future nicety, not required for the A↔B proof).
- The `authorization_code`/login path + the ADR-014 operator exchange — byte-unchanged (net-zero to non-switched operators).

---

# Acceptance Criteria

- **AC-1 (switcher → assume-tenant)**: `POST /api/tenant {tenant:T}` for an assigned T → server-side calls `${OIDC_ISSUER_URL}/oauth2/token` (form-urlencoded token-exchange, `audience=T`) → stores the assumed token + `TENANT_COOKIE=T` atomically. The base GAP OIDC token is the `subject_token` only (never logged, never returned).
- **AC-2 (re-scope is real)**: after a switch to T, `getDomainFacingToken()` returns the **assumed** token (tenant_id=T + entitled_domains=T's subs); the overview proxy + the 4 domain section clients send it as the GAP-OIDC bearer. A **non-switched** operator → `getDomainFacingToken()` = base token (**net-zero**, existing behaviour byte-identical).
- **AC-3 (fail-closed switch)**: assume-tenant `denied` (D2 assignment gate / subject-invalid) → `/api/tenant` 403, **no** cookie change (prior tenant + assumed token preserved); `unavailable` → 503; missing base token → 401. No partial/stale assumed-token state. `tenant=''` clears BOTH cookies.
- **AC-4 (multi-assignment seed)**: a `globex-corp` customer (`[scm,erp]` subs) + a multi-assignment operator (assigned to `acme-corp` **and** `globex-corp`) seeded in the e2e fixtures; the operator's ConsoleRegistry switcher lists both customers (BE-326 effective-scope, no console change).
- **AC-5 (federation-e2e A↔B)**: `tenant-switch-rescope.spec.ts` — login as multi-operator → switch A (`acme-corp`) → finance/wms not-forbidden + scm/erp forbidden → switch B (`globex-corp`) → **inverse** (scm/erp not-forbidden + finance/wms forbidden). Verified **post-merge** via `gh workflow run federation-hardening-e2e.yml` (SUCCESS). The existing MONO-154 single-tenant spec still passes.
- **AC-6 (BFF + scope-lock)**: **console-bff 0 byte**; GAP-domain clients (accounts/audit/operators/dashboards) still use `getOperatorToken()`; per-domain credential rule §2.4.5 intact. Changes = console-web (lib/session/route/4 domain clients/env/tests) + e2e fixtures+spec + GAP e2e seed + the 2 contracts only.
- **AC-7 (PR CI)**: console-web vitest GREEN (0 regression) + lint + build; the monorepo PR pipeline GREEN. (The A↔B e2e is workflow_dispatch, verified post-merge per AC-5.)
- **AC-8 (contract-first)**: console-integration-contract + console-web/architecture.md updated before/with the code; implemented shapes match.

---

# Related Specs

- `docs/adr/ADR-MONO-020-...md` § 2 **D4** (console-web drives the exchange, BFF pass-through) + D2/D3 (the exchange consumed) + § 3.1 (invariants) + § 3.3 step 3.
- `docs/adr/ADR-MONO-017-...md` § D6 (BFF pass-through HARD INVARIANT — preserved) ; ADR-MONO-019 § D5 (entitlement-trust gates — the A↔B discriminator).
- `projects/platform-console/specs/contracts/console-integration-contract.md` (§ 2.1/2.6 token boundary, § 2.4.5 per-domain credential, § 2.4.9.1 overview proxy) ; `projects/global-account-platform/specs/contracts/http/auth-api.md` (`/oauth2/token` token-exchange — the producer this consumes) ; `rules/traits/multi-tenant.md` M1-M7.

# Related Contracts

- **Update**: `console-integration-contract.md` (active-tenant switcher → assume-tenant flow) + `console-web/architecture.md`.
- **Consume (unchanged)**: `auth-api.md` § `POST /oauth2/token` token-exchange (BE-327 producer).

# Related Code

- console-web: `shared/lib/operator-token-exchange.ts` (sibling template) ; `shared/lib/session.ts` (cookies + the new `getAssumedToken`/`getDomainFacingToken`) ; `app/api/tenant/route.ts` (switch) ; `app/api/console/dashboards/operator-overview/route.ts` + `features/{wms,scm,finance,erp}-ops/api/*-api.ts` (bearer swap) ; `shared/config/env.ts` ; `app/api/auth/refresh` (re-assume) ; new `shared/lib/assume-tenant-exchange.ts`.
- e2e: `tests/federation-hardening-e2e/fixtures/{seed.sql,seed-domains.sql,login.ts}` + `specs/entitlement-trust-crossdomain.spec.ts` (structure template) + new `specs/tenant-switch-rescope.spec.ts`.
- GAP (read for seed shape): `admin_db.operator_tenant_assignment` (V0030, BE-326) ; `account_db.tenant_domain_subscription` (V0019, BE-322) ; acme-corp seed (account V0020, BE-325).

---

# Edge Cases

- **re-scope requires a new token, not a header** — the domain gates trust the SIGNED `tenant_id`/`entitled_domains`; setting X-Tenant-Id alone does nothing. The whole point of D4 is the assumed token. (This is why the switch MUST call assume-tenant, not just set the cookie.)
- **net-zero for non-switched operators** — `getDomainFacingToken()` falls back to the base token; the existing MONO-154 acme-corp single-tenant spec (no switch) must keep passing unchanged.
- **assumed token ↔ active tenant coupling** — set/cleared atomically; never serve an assumed token scoped to a tenant ≠ `console_active_tenant`. On any mismatch/clear, drop the assumed token.
- **form-urlencoded vs JSON** — assume-tenant is SAS `/oauth2/token` form-urlencoded (`audience` param); the operator exchange is admin JSON. Do not copy the JSON shape.
- **no refresh token for the assumed token** — re-assume on GAP refresh (don't try to refresh the assumed token directly; D2 issues none).
- **switch to home/own tenant** — still goes through assume-tenant (produces an equivalent scoped token); fine.
- **A↔B complementary entitlements** — acme=`[finance,wms]`, globex=`[scm,erp]` so the flip is unambiguous; pick the 2nd customer's subs to be disjoint from acme's.
- **e2e verification channel** — federation-hardening-e2e is workflow_dispatch/nightly, NOT PR-gated; the A↔B proof is post-merge (`gh workflow run`), like MONO-154. The PR must not assume the e2e runs in PR CI.

# Failure Scenarios

- **switch sets cookie without assume-tenant** → operator "switches" but domain reads still use the login-tenant token → silent wrong-tenant view / entitlement mismatch. ⇒ AC-1/AC-2; the switch MUST mint+store the assumed token.
- **assume-tenant fail-soft on denied** → an unassigned switch silently succeeds. ⇒ AC-3 fail-closed (denied→403, no cookie).
- **stale assumed token after clear/refresh** → wrong-tenant or expired credential. ⇒ atomic set/clear + re-assume on refresh.
- **GAP-domain client switched to domain-facing token** → breaks the §2.4.5/§2.6 operator-token boundary (#569 class). ⇒ only the 4 non-GAP domain clients + overview swap; accounts/audit/operators/dashboards untouched.
- **console-bff touched** → ADR-017 D6 violation. ⇒ AC-6 0-byte.
- **existing MONO-154 spec broken** → regression. ⇒ keep it byte-unchanged; the new spec is additive.

---

# Implementation Design Notes

- **Order**: contract-first → `assume-tenant-exchange.ts` + session resolver (unit) → `/api/tenant` switch wiring (unit) → domain-read bearer swap (unit) → refresh re-assume → seeds (globex + multi-operator) → `tenant-switch-rescope.spec.ts`. Logical commits per layer (the PR is broad — keep commits reviewable).
- **Reuse, do not duplicate**: `assume-tenant-exchange.ts` mirrors `operator-token-exchange.ts` (AbortController timeout, no-log, typed error union) — but form-urlencoded + `audience` + the SAS response shape. The bearer swap is mechanical (`getAccessToken()` → `getDomainFacingToken()`).
- **Verification**: console-web `pnpm test` (vitest) + `pnpm lint` + `pnpm build` locally; the monorepo PR CI is authoritative for unit/build. The **A↔B e2e is post-merge** `gh workflow run federation-hardening-e2e.yml` (provide the run id in the close note; MONO-154/155 precedent). If the e2e surfaces a real gap (e.g. registry not listing globex for the multi-operator, or the assumed token not flowing), fix forward.
- **Windows**: Read/Edit/Write tools for files; Bash only for git/pnpm/gradle. e2e seeds are SQL fixtures (no Docker needed to author).
- 구현 = **Opus**.

---

# Notes

- ADR-020 § 3.3 **step 3 (D4)**. console-web active-tenant switcher → assume-tenant flow + multi-assignment demo seed + federation-e2e A↔B. **The single defining proof = the A↔B entitlement flip** (switching customer re-scopes the SIGNED token, so finance/wms ↔ scm/erp entitlement follows). dependency-correct base = current `origin/main` (post BE-327 `de61856b`). **후속**: D6 step 4 (legacy single-value read cleanup) — last ADR-020 step.
- Cross-cutting (console-web + root e2e + GAP e2e seeds) → one atomic PR (CLAUDE.md § Cross-Project; MONO-154 root-task precedent).
