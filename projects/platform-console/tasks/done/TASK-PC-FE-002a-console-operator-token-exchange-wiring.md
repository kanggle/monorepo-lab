# Task ID

TASK-PC-FE-002a

# Title

console-web operator token-exchange wiring + console-integration-contract §2.1/§2.2 reconciliation

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- api
- code
- test

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **depends on**: GAP `TASK-BE-298` (admin-service `POST /api/admin/auth/token-exchange`) — **merged** (PR #573, main `c18fcdd0`). ADR-MONO-014 (ACCEPTED) § D5 **step 2**.
- **prerequisite for**: ADR-MONO-013 Phase 2 operator-parity slices — `TASK-PC-FE-002` (accounts) → `FE-003` (audit/security) → `FE-004` (operators) → `FE-005` (dashboards) → `FE-006` (parity-verify = Phase 3 admin-web-retirement gate). **Phase 2 stays PAUSED until this task is merged** (ADR-MONO-014 Consequences "Future-self").
- **spec-first**: the `console-integration-contract.md` reconciliation lands **before/with** the wiring code in the same PR (ADR-MONO-014 § D4; HARDSTOP-06 discipline).

# Goal

Realise the operator-auth bridge end-to-end on the console side (ADR-MONO-014 § D5 step 2) and **fix the `console-integration-contract.md` §2.1↔§2.2 self-contradiction shipped latent in #569**.

Today the console sends the **GAP OIDC access token** straight to `/api/admin/**` (see `shared/api/registry-client.ts:34/48` — `getAccessToken()` → `Authorization: Bearer <gap token>`), but those endpoints require an **operator token** (`token_type=admin`, `iss=admin-service`); this would `401` in any live environment (latent only because console-web has no live deployment / no Phase-2 operator screens yet). `console-integration-contract.md` §2.1 (GAP OIDC token, HttpOnly cookie) directly contradicts §2.2:L29 ("calls this server-side with the logged-in operator's GAP access token" while the auth model line above it demands an operator token).

After this task:

- The console, **server-side only**, exchanges the GAP OIDC access token for an operator token via the now-merged GAP `POST /api/admin/auth/token-exchange` (RFC 8693) on session establish (callback) and on GAP refresh (re-exchange model — ADR-MONO-014 D2; no operator-refresh state).
- The operator token is stored in its own HttpOnly·Secure·SameSite=strict cookie (never client-readable); `registry-client.ts` (and the future operator API clients) use the **operator** token, not the GAP token.
- Fail-closed: exchange `401 TOKEN_INVALID` (operator not provisioned / subject invalid) → forced re-login with a clear reason; exchange unreachable/timeout → treated as session-unavailable (never a silently broken authed state, never the GAP token leaking onto `/api/admin/**`).
- `console-integration-contract.md` §2.1/§2.2 is internally consistent and matches the GAP producer contract; the misleading `registry-client.ts` comment is corrected.

# Scope

## In Scope

### Spec-first (lands before/with code, ADR-MONO-014 § D4)
- `projects/platform-console/specs/contracts/console-integration-contract.md`:
  - §2.1 Identity — add the **server-side operator-token exchange step** (after OIDC login, the console exchanges the GAP access token for an operator token; both are HttpOnly-cookie, server-only).
  - §2.2 — rewrite the auth-model line (L29): the registry/`/api/admin/**` call uses the **operator token obtained via the exchange**, NOT "the logged-in operator's GAP access token". Keep the producer requirement (`token_type=admin`, `iss=admin-service`) unchanged.
  - Add a normative element (e.g. **§2.6 Operator Token Exchange**): console server-side calls GAP admin-service `POST /api/admin/auth/token-exchange` (RFC 8693 — `grant_type`/`subject_token`=GAP access token/`subject_token_type`), per `global-account-platform/specs/contracts/http/admin-api.md` (authoritative producer); re-exchange model (no operator-refresh); fail-closed mapping (`401 TOKEN_INVALID` → re-login); resilience parity with §2.5.
- `projects/platform-console/specs/services/console-web/architecture.md` — if it states the auth/registry flow, reconcile to include the exchange step (canonical form intact). Cross-reference GAP `admin-api.md` §`POST /api/admin/auth/token-exchange` + `console-registry-api.md` § Authentication.

### Code (`apps/console-web`, follows the reconciled spec)
- `shared/lib/session.ts` — add `OPERATOR_COOKIE` + `getOperatorToken()` (same HttpOnly·Secure·SameSite=strict opts); `isAuthenticated()` semantics reviewed (operator token presence is what gates `/api/admin/**`).
- New `shared/lib/operator-token-exchange.ts` (server-only) — `exchangeForOperatorToken(gapAccessToken)`: POST the RFC 8693 JSON body to `CONSOLE_TOKEN_EXCHANGE_URL`, parse `{ accessToken, expiresIn, tokenType }` (zod), AbortController hard timeout; map `401`→a fail-closed error, network/timeout/5xx→an unavailable error (mirror `registry-client.ts`/`errors.ts` patterns; structured logging via `shared/lib/logger`).
- `app/api/auth/callback/route.ts` — after storing the GAP tokens, exchange → set the operator cookie (maxAge = `expiresIn`); exchange `401` → `loginRedirect(..., 'not_provisioned')`; exchange unavailable → `loginRedirect(..., 'operator_exchange_unavailable')` (no partial authed state; GAP token MUST NOT be usable as the `/api/admin/**` credential).
- `app/api/auth/refresh/route.ts` — after the GAP refresh rotates the access token, **re-exchange** and update the operator cookie (re-exchange model); failure handling consistent with callback.
- `app/api/auth/logout/route.ts` — also clear `OPERATOR_COOKIE`.
- `shared/api/registry-client.ts` — use `getOperatorToken()` (not `getAccessToken()`); correct the misleading comment block (lines ~16–18) to state the operator token is obtained via the exchange.
- `shared/config/env.ts` — add `CONSOLE_TOKEN_EXCHANGE_URL` (default `http://gap.local/api/admin/auth/token-exchange`) + a timeout (reuse/extend the registry timeout convention).
- `.env.example`, `.env.local.example`, `docker-compose.yml` — add the exchange URL (Local Network Convention; `gap.local`).

### Tests (vitest, same lane as FE-001)
- `operator-token-exchange`: success → `{accessToken,expiresIn}`; `401` → fail-closed error; timeout/network/5xx → unavailable error; request body is exactly the RFC 8693 shape; subject token never logged.
- `callback`: GAP token stored → exchange invoked → operator cookie set; exchange 401 → redirect `not_provisioned`, **no operator cookie, GAP token not left as an admin credential**; exchange unavailable → redirect, no partial authed state.
- `refresh`: GAP refresh success → re-exchange → operator cookie updated; exchange failure handled.
- `registry-client`: sends the **operator** token (assert the bearer value is the operator cookie, not the GAP access cookie); existing 401/503/timeout degrade paths still hold.
- `logout`: clears operator cookie too.

## Out of Scope

- GAP-side endpoint (already done — `TASK-BE-298`, merged). Do not modify GAP code/specs except cross-referencing.
- Phase 2 operator screens (`TASK-PC-FE-002`+ accounts/audit/operators/dashboards) — this task only wires the auth bridge + fixes the contract; no domain screens.
- Re-deciding the model (ADR-MONO-014 D1 = B; Option A/C/D rejected). Do not call `/api/admin/**` with the GAP token; do not add an admin credential login.
- `console-bff` (ADR-MONO-013 Phase 7).
- GAP `admin-web` retirement (Phase 3, parity-gated).

# Acceptance Criteria

- [ ] `console-integration-contract.md` §2.1/§2.2 internally consistent + a normative exchange element added, **merged in the same PR before/with the code**; matches GAP `admin-api.md` producer contract; misleading `registry-client.ts` comment corrected.
- [ ] Console exchanges server-side at session establish (callback) and re-exchanges on GAP refresh; operator token in its own HttpOnly·Secure·SameSite=strict cookie; never client-readable; GAP token never sent to `/api/admin/**`.
- [ ] `registry-client.ts` authenticates with the **operator** token (test asserts the bearer is the operator cookie value, not the GAP access token).
- [ ] Fail-closed: exchange `401` → forced re-login (`not_provisioned`); exchange timeout/unreachable/5xx → no partial authed state, console does not fall back to the GAP token on the operator boundary.
- [ ] RFC 8693 request body exactly per `admin-api.md` (`grant_type`/`subject_token`/`subject_token_type`); `{accessToken,expiresIn,tokenType}` parsed; operator cookie maxAge = `expiresIn`.
- [ ] `logout` clears the operator cookie; subject/operator tokens never logged.
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + existing suites); scope = `projects/platform-console/` only; no churn-clock effect.
- [ ] ADR-MONO-014 § D5 step 2 satisfied; task references ADR-MONO-014 + dependency markers; on merge, Phase 2 (`TASK-PC-FE-002`) is unblocked.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (§ D2/D3/D4/D5 — authoritative)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` (§ D1 Model B, § D5)
- `projects/platform-console/specs/contracts/console-integration-contract.md` (§2.1/§2.2 — the file being reconciled)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; auth flow)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` § `POST /api/admin/auth/token-exchange` (authoritative producer — request/200/401/400 shapes, re-exchange model, fail-closed)
- `projects/global-account-platform/specs/services/admin-service/security.md` § GAP OIDC Subject-Token Validation (producer validation policy — informs the console's error handling)
- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` § Authentication (operator token now via exchange; producer requirement unchanged)
- `platform/service-types/frontend-app.md` (HttpOnly cookie auth; forbidden patterns)
- `TEMPLATE.md` § Local Network Convention (`gap.local` / `console.local`)
- `projects/platform-console/tasks/review/TASK-PC-FE-001-console-web-shell-gap-sso.md` (predecessor — auth routes/session lib this task extends; task-shape parity)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server routes, HttpOnly cookie auth), api integration, security review (token boundary).

---

# Related Contracts

- **Changed (this task, spec-first)**: `projects/platform-console/specs/contracts/console-integration-contract.md` §2.1/§2.2 + new exchange element.
- **Consumed (unchanged, authoritative)**: GAP `admin-api.md` §`POST /api/admin/auth/token-exchange`, `console-registry-api.md` §Authentication.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — server routes + shared auth/session libs + registry client.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + the project's `console-web/architecture.md` (Layered-by-Feature, established by FE-001). All token handling stays **server-side** (server routes / server-only libs); tokens only in HttpOnly cookies.
- ADR-MONO-013 Model B: console is the only UI; it reaches GAP server-side. ADR-MONO-014 Model B: the operator credential for `/api/admin/**` is the **exchanged** operator token, obtained server-side; the GAP OIDC token is only ever the `subject_token` input to the exchange, never an `/api/admin/**` credential.

---

# Implementation Notes

- Spec-first hard gate (ADR-MONO-014 § D4 / HARDSTOP-06): reconcile `console-integration-contract.md` (+ console-web architecture.md if it states the flow) **before/with** the wiring code, in one PR.
- Reuse FE-001 patterns: `session.ts` cookie contract, `errors.ts` error taxonomy, `registry-client.ts` AbortController/timeout/structured-logging, the `loginRedirect` reason pattern in `callback/route.ts`. Do not invent a parallel mechanism.
- Re-exchange model (ADR-MONO-014 D2): no operator-refresh token/state — every GAP refresh triggers a fresh exchange. Operator cookie maxAge tracks `expiresIn` from the response.
- Security: never log `subject_token`/operator token; never expose either to client JS; the GAP access token must be impossible to use as the `/api/admin/**` credential after this change (registry-client uses the operator cookie exclusively).
- Recommend implementation model: **Opus** (security-critical token boundary + cross-service contract reconciliation). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`.
- Branch name must not contain the `master` substring (sandbox push regex).
- Local Docker is unavailable on this Windows host — vitest (jsdom, mocked fetch) is the local lane; any Playwright/Testcontainers E2E is CI/manual (same as FE-001).

---

# Edge Cases

- Exchange `401` because the operator has no `admin_operators.oidc_subject` mapping (not yet provisioned) → re-login with a distinct reason (`not_provisioned`), not a generic auth error — operator can be told to request provisioning.
- GAP refresh succeeds but re-exchange `401` (operator deactivated/locked since last login) → drop the operator session (clear operator cookie), force re-login; do not keep a stale operator token.
- Exchange endpoint times out / `gap.local` unreachable → unavailable path: no operator cookie set; the console must not fall back to the GAP token on `/api/admin/**` (that is the exact #569 defect).
- Concurrent requests during refresh → operator cookie update must not leave a window where `registry-client` reads a token mid-rotation (follow FE-001's existing refresh handling; keep set-cookie atomic per response).
- `tokenType` in the response is `"admin"` — validate it; an unexpected value → treat as fail-closed (do not store).

# Failure Scenarios

- Contract left self-contradictory (spec not reconciled before code) → HARDSTOP-06; AC binds the §2.1/§2.2 fix into the same PR ahead of code.
- GAP token leaks onto `/api/admin/**` (registry-client still uses `getAccessToken()`) → the #569 defect persists; AC + test assert the bearer is the operator cookie.
- Silent partial authed state (GAP session established, exchange failed, screens half-work) → fail-closed redirect; test asserts no operator cookie + redirect on exchange failure.
- Re-exchange skipped on refresh → operator token expires mid-session, `/api/admin/**` starts 401ing → test asserts refresh re-exchanges.
- Scope creep into Phase 2 screens → explicitly Out of Scope; this PR is the auth bridge + contract fix only.

---

# Test Requirements

- vitest (jsdom, mocked `fetch`): `operator-token-exchange` (success/401/timeout/5xx/body-shape/no-token-logging), `callback` (exchange wired, fail-closed redirects, no GAP-token-as-admin-credential), `refresh` (re-exchange), `registry-client` (operator-token bearer + existing degrade paths), `logout` (operator cookie cleared).
- `pnpm build` + `pnpm lint` (0 warnings) green; existing FE-001 suites still pass (no regression).
- Spec internal-link lint clean; `validate-rules` no new inconsistency.

---

# Definition of Done

- [ ] `console-integration-contract.md` §2.1/§2.2 reconciled + exchange element (spec-first, same PR, before/with code)
- [ ] Server-side exchange wired (callback + refresh re-exchange), operator token in its own HttpOnly cookie, registry-client uses it, logout clears it
- [ ] Fail-closed behaviour implemented + tested; GAP token never an `/api/admin/**` credential
- [ ] `pnpm build`/`lint`/`vitest` green; scope = platform-console only
- [ ] Acceptance Criteria all satisfied; ADR-MONO-014 § D5 step 2 closed
- [ ] Ready for review (on merge → ADR-MONO-013 Phase 2 / `TASK-PC-FE-002` unblocked)
