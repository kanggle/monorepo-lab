# Task ID

TASK-PC-FE-002

# Title

console-web Phase 2 slice 1 — GAP accounts operator parity (search/detail/lock/unlock/bulk-lock/revoke-session/gdpr-delete/export)

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

- **depends on**: `TASK-PC-FE-002a` (operator token-exchange wiring + contract reconciliation) — **merged** (PR #574, main `aa7282b4`); ADR-MONO-014 § D5 fully closed. The operator token (via `getOperatorToken()` / §2.6 exchange) is the credential this slice uses for `/api/admin/**`.
- **part of**: ADR-MONO-013 § D6 **Phase 2** (console operator-parity). This is **slice 1 of 5**: FE-002 accounts → `FE-003` audit+security → `FE-004` operators → `FE-005` dashboards → `FE-006` parity-verify (= ADR-MONO-013 Phase 3 admin-web-retirement gate).
- **prerequisite for**: `TASK-PC-FE-003` (next slice) and ultimately `FE-006` parity verification against `console-integration-contract.md` § 3.
- **spec-first**: the console-side `specs` cross-reference of the GAP accounts surface lands **before/with** the code (HARDSTOP-06). The GAP producer contract (`admin-api.md`) already exists on `main` and is **unchanged** by this task.

# Goal

Build the console's **GAP accounts operator surface** — the first Phase 2 operator-parity slice (ADR-MONO-013 D6 Phase 2 / `console-integration-contract.md` § 3 parity checklist "accounts" line). The console renders, server-side and tenant-scoped, the eight GAP admin-service account operations using the **exchanged operator token** (never the GAP OIDC token — the FE-002a trust-boundary invariant):

1. **search/list** — `GET /api/admin/accounts` (email single-lookup OR paginated list)
2. **detail** — account detail view (from the search/list item + per-account reads)
3. **lock** — `POST /api/admin/accounts/{id}/lock`
4. **unlock** — `POST /api/admin/accounts/{id}/unlock`
5. **bulk-lock** — `POST /api/admin/accounts/bulk-lock`
6. **revoke-session** — `POST /api/admin/sessions/{accountId}/revoke`
7. **gdpr-delete** — `POST /api/admin/accounts/{id}/gdpr-delete`
8. **export** — `GET /api/admin/accounts/{id}/export`

After this task the accounts row of the Phase 3 parity checklist is satisfiable; `FE-006` will formally verify it.

# Scope

## In Scope

### Spec-first (lands before/with code)
- `projects/platform-console/specs/services/console-web/architecture.md` — add the **accounts feature module** to the Layered-by-Feature map (`features/accounts`: api client / hooks / components / route), per the FE-001/FE-002a established structure. Canonical form intact.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4 — record the **GAP accounts console-facing API surface** as a per-domain cross-reference to the authoritative producer `global-account-platform/specs/contracts/http/admin-api.md` (§ `GET /api/admin/accounts`, `…/lock`, `bulk-lock`, `…/unlock`, `POST /api/admin/sessions/{accountId}/revoke`, `…/gdpr-delete`, `GET …/export`) + the tenant-scoping + idempotency obligations (§ 2.4 already states the rule; this makes the GAP-accounts binding concrete). **Do not redefine** the GAP contract — cross-reference only.

### Code (`apps/console-web`, follows the spec)
- `src/features/accounts/` (Layered-by-Feature, mirrors `features/catalog`):
  - **server-side API client** (`api/`): one function per operation. Auth = `Authorization: Bearer <operator token>` via `getOperatorToken()` (NOT `getAccessToken()` — FE-002a invariant; absent operator token → 401 → re-login). Tenant scope = `X-Tenant-Id` from the active-tenant cookie (`getActiveTenant()`); the domain MUST reject cross-tenant (producer-enforced) — console always sends the selected tenant. Mutations send `X-Operator-Reason` (operator-entered audit reason, required) **and** a client-generated `Idempotency-Key` (UUID; console owns no domain txn — `platform-console` is not `transactional`). AbortController hard timeout; structured logging via `shared/lib/logger` (never log tokens / PII bodies).
  - **hooks** (`hooks/`): TanStack Query read hooks (search/list pagination, detail) + mutation hooks (lock/unlock/bulk-lock/revoke-session/gdpr-delete) with optimistic-safe invalidation; export = server action / download.
  - **components** (`components/`): accounts search + paginated table, account detail panel, lock/unlock + bulk-lock + revoke-session + gdpr-delete action affordances each behind a **reason-capture + confirm** dialog (destructive ops require explicit operator reason → `X-Operator-Reason`; gdpr-delete double-confirm), export trigger. WCAG AA (axe), keyboard-operable dialogs.
  - **route**: `src/app/(console)/accounts/…` (server components fetch via the API client; the catalog `baseRoute` for `gap` points here). Wire it into the existing console shell/nav.
- `src/shared/api/` — extend the shared error taxonomy/types only if a new shape is genuinely needed (prefer reuse of `errors.ts` / the registry-client resilience pattern); add accounts response/zod types under `features/accounts` (feature-local) unless cross-feature.
- Resilience (§ 2.5 / integration-heavy): 401/403 → forced re-login (no partial authed state); 503 `DOWNSTREAM_ERROR`/`CIRCUIT_OPEN` → that section degrades only (never blank the shell); timeout → degraded; `STATE_TRANSITION_INVALID`/`REASON_REQUIRED`/`ACCOUNT_NOT_FOUND` → inline actionable error, not a crash.

### Tests (vitest, jsdom, mocked fetch — FE-001/FE-002a lane)
- API client: each operation sends the operator-token bearer (assert it is the operator cookie, **not** the GAP access token), `X-Tenant-Id` from active tenant, and for mutations `X-Operator-Reason` + a non-empty `Idempotency-Key`; error mapping (401/403/400/404/503) → the resilience contract.
- Hooks/components: search pagination, detail render, each mutation requires a reason before firing (no reason → request not sent), gdpr-delete double-confirm, bulk-lock multi-select, export download path, degrade rendering on 503/timeout, re-login on 401.
- Regression: existing FE-001/FE-002a suites still green; catalog `gap.baseRoute` resolves to the accounts route.

## Out of Scope

- The other Phase 2 slices: audit/security (`FE-003`), operators mgmt (`FE-004`), dashboards (`FE-005`), parity-verify (`FE-006`).
- Any GAP-side change (admin-api.md / admin-service / account-service) — the producer contract is authoritative and **unchanged**; cross-reference only.
- `admin-web` retirement (ADR-MONO-013 Phase 3 — gated on `FE-006`, GAP-internal spec change, not here).
- Token-exchange mechanics (done — FE-002a). Re-deciding auth (ADR-MONO-014 fixed).
- `console-bff` aggregation (Phase 7).
- wms/scm/finance/erp sections (Phase 4–6).

# Acceptance Criteria

- [ ] Console renders all 8 accounts operations server-side, tenant-scoped (`X-Tenant-Id` from active tenant), authenticated with the **operator token** (test asserts bearer = operator cookie, never the GAP access token).
- [ ] Every mutation (lock/unlock/bulk-lock/revoke-session/gdpr-delete) sends a required operator-entered `X-Operator-Reason` and a client-generated `Idempotency-Key`; UI blocks the call until a reason is entered; gdpr-delete double-confirms.
- [ ] Resilience per § 2.5: 401/403 → re-login (no partial authed state); 503 `DOWNSTREAM_ERROR`/`CIRCUIT_OPEN` / timeout → accounts section degrades only (shell intact); `400 STATE_TRANSITION_INVALID`/`REASON_REQUIRED` / `404 ACCOUNT_NOT_FOUND` → inline actionable error.
- [ ] Spec-first: `console-web/architecture.md` (accounts feature module) + `console-integration-contract.md` § 2.4 (GAP accounts surface cross-ref) merged in the same PR before/with code; GAP `admin-api.md` unchanged; canonical form intact.
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + existing suites; no regression); WCAG AA (axe) on new screens; perf budget honored (no bundle regression beyond the FE-001 budget headroom).
- [ ] Scope = `projects/platform-console/` only; no churn-clock effect.
- [ ] ADR-MONO-013 Phase 2 slice-1 satisfied; catalog `gap.baseRoute` routes to accounts; task references ADR-MONO-013 D6 + FE-002a + the § 3 parity "accounts" line.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D6 (Phase 2 roadmap)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (operator token = exchange output; the credential this slice uses)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.3 (server-side routing) / § 2.4 (per-domain console-facing API, tenant-scope + idempotency) / § 2.5 (resilience) / § 2.6 (operator token) / § 3 (parity checklist — "accounts" line)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; auth flow established by FE-001/FE-002a)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` §§ `GET /api/admin/accounts`, `POST /api/admin/accounts/{id}/lock`, `POST /api/admin/accounts/bulk-lock`, `POST /api/admin/accounts/{id}/unlock`, `POST /api/admin/sessions/{accountId}/revoke`, `POST /api/admin/accounts/{id}/gdpr-delete`, `GET /api/admin/accounts/{id}/export` (authoritative producer — request/response/error/headers; **unchanged**)
- `platform/service-types/frontend-app.md` (HttpOnly cookie auth; server-side calls; forbidden patterns)
- `projects/platform-console/tasks/review/TASK-PC-FE-001-...` + `tasks/ready/TASK-PC-FE-002a-...` (predecessors — feature-module pattern, operator-token usage, resilience pattern, task-shape parity)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components/routes, TanStack Query, HttpOnly cookie auth), api integration, a11y, security review (destructive operator actions).

---

# Related Contracts

- **Changed (this task, spec-first)**: `console-web/architecture.md` (accounts feature module), `console-integration-contract.md` § 2.4 (GAP accounts cross-reference).
- **Consumed (unchanged, authoritative)**: GAP `admin-api.md` account/session endpoints; `console-registry-api.md` (catalog `gap.baseRoute`).

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new `features/accounts` module + `(console)/accounts` route + shared resilience reuse.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001/FE-002a established). All GAP calls are **server-side** with the operator token; tokens/PII never reach client JS or logs.
- ADR-MONO-013 Model B: the console renders the accounts screens itself by calling GAP `/api/admin/**`; it owns no domain transaction (idempotency-key + producer-side idempotency).
- ADR-MONO-014: the `/api/admin/**` credential is the exchanged operator token (`getOperatorToken()`), tenant scope resolved producer-side from `admin_operators.tenant_id`; the console additionally sends the selected `X-Tenant-Id` for the per-domain tenant scoping (§ 2.4) and the producer rejects cross-tenant.

---

# Implementation Notes

- Spec-first hard gate (HARDSTOP-06): reconcile `console-web/architecture.md` + `console-integration-contract.md` § 2.4 before/with code, same PR.
- Reuse, do not reinvent: `getOperatorToken()`/`getActiveTenant()` (session.ts), the `errors.ts` taxonomy + `registry-client.ts` AbortController/timeout/degrade pattern, the FE-001 `features/catalog` module shape, the FE-002a fail-closed posture. Idempotency-Key = `crypto.randomUUID()` per mutation attempt (stable across a single user-confirmed action; new key per fresh attempt).
- Destructive-action UX is security UX: lock/unlock/bulk-lock/revoke-session/gdpr-delete each require an explicit operator reason (→ `X-Operator-Reason` + body `reason`); gdpr-delete is irreversible → double-confirm + typed confirmation. No silent/one-click destructive calls.
- Never log tokens or account PII (emails) at info level; redact in structured logs.
- Recommend implementation model: **Opus** (destructive operator actions across a tenant boundary + security UX + multi-endpoint contract fidelity). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`.
- Branch name must not contain the `master` substring.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate (FE-001/FE-002a precedent); Playwright/Testcontainers E2E is CI/manual.

---

# Edge Cases

- Operator token expired mid-session → 401 → forced re-login (FE-002a refresh re-exchanges on the GAP refresh path; a hard 401 here drops to login).
- No active tenant selected → block tenant-scoped account calls with an actionable "select a tenant" state (do not send an empty `X-Tenant-Id`).
- `account.read` not granted (operator role) → `GET /api/admin/accounts` returns empty list (producer behavior, not 403) → render an empty/"insufficient permission" state, not an error crash.
- Permission-denied on a mutation (`403 PERMISSION_DENIED`, role lacks `account.lock` etc.) → inline "not permitted" affordance; do not offer the action if the operator clearly lacks it where derivable.
- `400 STATE_TRANSITION_INVALID` (lock an already-LOCKED/DELETED account) → inline state-aware message; refresh the row status.
- bulk-lock partial failure (per-account outcomes) → render per-account result; do not imply all-or-nothing.
- gdpr-delete is irreversible → double-confirm + explicit typed confirmation; never a single click.
- Idempotency: a retried confirmed action reuses its `Idempotency-Key`; a fresh user action gets a new key (no accidental double-mutation, no accidental dedupe of a genuine second action).
- export returns a (potentially large) payload → stream/download server-side; do not buffer PII into client state.

# Failure Scenarios

- GAP token leaks onto `/api/admin/**` (using `getAccessToken()` instead of `getOperatorToken()`) → re-opens the #569 defect; AC + test assert the operator-cookie bearer.
- Missing `X-Operator-Reason`/`Idempotency-Key` on a mutation → producer `400`; UI must collect the reason and generate the key before the call (test: no-reason → request not sent).
- Cross-tenant leak (wrong/empty `X-Tenant-Id`) → producer rejects; console must always send the selected tenant and block when none is selected.
- 503/timeout blanks the whole console → violates § 2.5; test asserts only the accounts section degrades.
- Destructive action with no confirm/reason gate → security regression; test asserts the reason+confirm gate (double-confirm for gdpr-delete).
- Spec not reconciled before code → HARDSTOP-06; AC binds the spec cross-ref into the same PR ahead of code.
- Scope creep into FE-003+ (audit/operators/dashboards) → explicitly Out of Scope; this slice is accounts only.

---

# Test Requirements

- vitest (jsdom, mocked fetch): API client per-operation (operator-token bearer, `X-Tenant-Id`, mutation `X-Operator-Reason`+`Idempotency-Key`, 401/403/400/404/503 mapping), hooks/components (pagination, detail, reason-gated mutations, gdpr double-confirm, bulk-lock multi-select, export, degrade on 503/timeout, re-login on 401), regression (FE-001/FE-002a suites green; `gap.baseRoute` → accounts).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA) on new screens; no bundle/perf regression beyond FE-001 budget.
- Spec internal-link lint clean; `validate-rules` no new inconsistency.

---

# Definition of Done

- [ ] Spec-first reconciliation (`console-web/architecture.md` accounts module + `console-integration-contract.md` § 2.4 GAP-accounts cross-ref) merged before/with code
- [ ] All 8 accounts operations rendered server-side, tenant-scoped, operator-token-authed, reason+idempotency on mutations, destructive-action confirm gates
- [ ] § 2.5 resilience (re-login / degrade / inline errors) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 2 slice-1 closed; `FE-003` unblocked
- [ ] Ready for review
