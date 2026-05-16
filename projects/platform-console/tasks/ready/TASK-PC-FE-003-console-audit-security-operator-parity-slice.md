# Task ID

TASK-PC-FE-003

# Title

console-web Phase 2 slice 2 — GAP audit + security read operator parity (unified audit query / login-history / suspicious)

# Status

ready

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

- **depends on**: `TASK-PC-FE-002` (accounts slice + the `features/*` + operator-token + resilience patterns) — **merged** (PR #575, main `ad12ec4b`). Operator-auth bridge (`TASK-PC-FE-002a` / ADR-MONO-014 § D5) complete.
- **part of**: ADR-MONO-013 § D6 **Phase 2** — **slice 2 of 5**: FE-002 accounts ✅ → **FE-003 audit+security** (this) → `FE-004` operators → `FE-005` dashboards → `FE-006` parity-verify (= ADR-MONO-013 Phase 3 admin-web-retirement gate).
- **prerequisite for**: `TASK-PC-FE-004`; satisfies the `console-integration-contract.md` § 3 parity lines **"audit: query"** and **"security: login-history, suspicious"** (FE-006 formally verifies).
- **spec-first**: the console-side `specs` cross-reference of the GAP audit surface lands **before/with** the code (HARDSTOP-06). GAP `admin-api.md` is **unchanged** (authoritative producer).

# Goal

Build the console's **GAP audit + security read surface** — the second Phase 2 operator-parity slice. The single GAP producer endpoint `GET /api/admin/audit` is a **unified view** over `admin_actions` + `login_history` + `suspicious_events`, discriminated by a `source` filter:

- **audit query** — `source=admin` (or unfiltered), permission `audit.read`
- **security: login-history** — `source=login_history`, requires `audit.read` **and** `security.event.read` (intersection — both)
- **security: suspicious** — `source=suspicious`, requires `audit.read` **and** `security.event.read`

The console renders this server-side, tenant-scoped, with the **exchanged operator token** (never the GAP OIDC token — the FE-002a #569 trust boundary). It is a **read-only** slice — no mutations, therefore **no** `X-Operator-Reason` / `Idempotency-Key` / destructive-confirm gates (FE-002 concerns that do **not** apply here). After this task the § 3 parity "audit"/"security" lines are satisfiable; `FE-006` will verify.

# Scope

## In Scope

### Spec-first (lands before/with code)
- `projects/platform-console/specs/services/console-web/architecture.md` — add the **audit feature module** (`features/audit`: api / hooks / components / route) to the Layered-by-Feature map, mirroring `features/accounts`. Canonical form intact.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4 (new sub-section, e.g. **§ 2.4.2 "GAP audit + security surface"**) — per-domain **cross-reference** to the authoritative producer `global-account-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/audit` + the consumer obligations: operator-token auth, `X-Tenant-Id` tenant scoping (+ optional `tenantId` query for SUPER_ADMIN cross-tenant; non-super → `403 TENANT_SCOPE_DENIED`), the **intersection permission** rule (`source=login_history|suspicious` ⇒ `security.event.read` also required), read-query meta-audit awareness, producer-masked PII. **Do not redefine** the GAP contract — cross-reference only. § 3 parity "audit"/"security" lines noted satisfiable.

### Code (`apps/console-web`, follows the spec)
- `src/features/audit/` (Layered-by-Feature, mirrors `features/accounts`):
  - **server-side API client** (`api/`): `queryAudit(params)` → `GET /api/admin/audit`. Auth = `Authorization: Bearer <operator token>` via `getOperatorToken()` (NOT `getAccessToken()`; absent → 401 → re-login, no fetch). Tenant = `X-Tenant-Id` from `getActiveTenant()` (block, don't send empty, when none selected). Query params: `accountId`, `actionCode`, `from`, `to`, `source` (`admin|login_history|suspicious`), `tenantId` (only when the operator explicitly selects cross-tenant; SUPER_ADMIN), `page`, `size` (client-cap ≤ 100 to pre-empt `422`). AbortController hard timeout; structured logging — **never log tokens or audit-row PII** (account ids / masked IPs / geo) at info level (redact).
  - **types** (`api/types.ts`): a zod **discriminated union** on `source` (`admin`: auditId/actionCode/operatorId/targetId/reason/outcome/occurredAt; `login_history`: eventId/accountId/outcome/ipMasked/geoCountry/occurredAt; `suspicious`: analogous) + the page envelope. Be tolerant of unknown future `source` values (render a generic row, don't crash).
  - **hooks** (`hooks/`): TanStack Query read hooks — filter+paginated query, keyed by the full filter set; no mutations.
  - **components** (`components/`): a filter bar (accountId, actionCode, from/to datetime, source selector, tenant selector for SUPER_ADMIN), a paginated unified table that **renders each row by its `source` discriminant** (admin vs login_history vs suspicious columns), empty/permission-denied/degraded states, a11y (WCAG AA, axe; keyboard-operable filter + table).
  - **route**: `src/app/(console)/audit/…` server component fetching via the API client; wired into the console shell/nav. (`gap.baseRoute` already → `/accounts` from FE-002; add an in-console nav entry to `/audit` — do not change the catalog `baseRoute` contract.)
  - **proxy** (`src/app/api/audit/route.ts`): same-origin GET proxy attaching the operator token server-side (mirror the FE-002 `_proxy` error-mapping; read-only, no body schema).
- Resilience / permission UX (§ 2.5 + the intersection rule): 401 → forced re-login (no partial authed state); **403 `PERMISSION_DENIED`** (lacks `audit.read`, or `security.event.read` for a security `source`) → inline "insufficient permission for this view" (NOT a crash; if the operator clearly lacks `security.event.read`, the login-history/suspicious source options degrade to a disabled/explained state rather than erroring on click); **403 `TENANT_SCOPE_DENIED`** → inline "not permitted for that tenant"; `422 VALIDATION_ERROR` (from > to, size > 100) → inline field-level validation (and client-side guard to avoid sending it); 503 `DOWNSTREAM_ERROR`/`CIRCUIT_OPEN`/timeout → only the audit section degrades (shell intact).

### Tests (vitest, jsdom, mocked fetch — FE-001/002a/002 lane)
- API client: operator-token bearer (assert it is the operator cookie value, **not** the GAP access token); `X-Tenant-Id` from active tenant; filter/`source`/pagination params correctly serialized; `size` client-capped ≤ 100; **no** `X-Operator-Reason`/`Idempotency-Key` sent (read-only — assert absence); 401/403 PERMISSION_DENIED/403 TENANT_SCOPE_DENIED/422/503 mapped to the resilience contract.
- Components/hooks: filter submission, `source` switching, **discriminated row rendering** per source, pagination, permission-denied inline state for a security source without `security.event.read`, tenant-scope-denied inline state, degrade on 503/timeout, re-login on 401, empty state.
- Regression: existing FE-001/002a/002 suites green; the `/audit` nav entry resolves; `gap.baseRoute` (accounts) unchanged.

## Out of Scope

- Other Phase 2 slices: operators mgmt (`FE-004`), dashboards (`FE-005`), parity-verify (`FE-006`).
- Any GAP-side change (`admin-api.md` / admin-service) — producer authoritative & **unchanged**; cross-reference only.
- Any **mutation** / write path (this slice is read-only) — no `X-Operator-Reason`, no `Idempotency-Key`, no destructive-confirm dialogs (those are FE-002 concerns; introducing them here would be wrong).
- Changing the catalog `gap.baseRoute` (stays `/accounts`; audit is an in-console nav destination, not a catalog product).
- `admin-web` retirement (Phase 3, gated on FE-006). `console-bff` (Phase 7). wms/scm/finance/erp (Phase 4–6).

# Acceptance Criteria

- [ ] Console renders the unified audit view server-side, tenant-scoped (`X-Tenant-Id` from active tenant; optional `tenantId` query only on explicit SUPER_ADMIN cross-tenant), authed with the **operator token** (test asserts bearer = operator cookie, never the GAP access token).
- [ ] `source` filter drives `admin` / `login_history` / `suspicious`; rows render **discriminated by `source`**; the intersection-permission rule is respected in the UX (a security `source` without `security.event.read` → inline insufficient-permission, not a crash, ideally a pre-disabled affordance).
- [ ] **No mutation artifacts**: the request carries no `X-Operator-Reason` and no `Idempotency-Key`; there are no destructive-action dialogs (read-only slice) — asserted by test.
- [ ] Resilience per § 2.5: 401 → re-login (no partial authed state); 403 `PERMISSION_DENIED` / 403 `TENANT_SCOPE_DENIED` / 422 `VALIDATION_ERROR` → inline actionable (no crash; `size` client-capped ≤ 100, from/to guarded); 503/timeout → audit section degrades only (shell intact).
- [ ] Tokens and audit-row PII (account ids / masked IPs / geo) are never logged (redacted in structured logs).
- [ ] Spec-first: `console-web/architecture.md` (audit module) + `console-integration-contract.md` § 2.4.2 (GAP audit cross-ref) merged in the same PR before/with code; GAP `admin-api.md` unchanged; canonical form intact.
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + existing; no regression); axe WCAG AA on the new screen; no bundle/perf regression beyond the FE-001 budget.
- [ ] Scope = `projects/platform-console/` only; no churn-clock effect. ADR-MONO-013 Phase 2 slice-2 satisfied; § 3 "audit"/"security" lines satisfiable; task references ADR-MONO-013 D6 + FE-002a trust boundary + the § 3 lines; `FE-004` unblocked.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D6 (Phase 2)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (operator token = the `/api/admin/**` credential)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.3/§ 2.4 (per-domain surface; tenant scope)/§ 2.5 (resilience)/§ 2.6 (operator token)/§ 3 ("audit"/"security" parity lines)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-001/002a/002 patterns)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` § `GET /api/admin/audit` (authoritative producer — query params, discriminated response, intersection permission, `403 PERMISSION_DENIED`/`403 TENANT_SCOPE_DENIED`/`422`; **unchanged**)
- `platform/service-types/frontend-app.md` (HttpOnly cookie auth; server-side calls)
- `projects/platform-console/tasks/review/TASK-PC-FE-002-...` (predecessor — `features/accounts` shape, operator-token usage, `_proxy` error-mapping, resilience pattern; task-shape parity) + `TASK-PC-FE-002a-...` (trust boundary)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query, HttpOnly cookie auth), api integration, a11y, security review (audit/PII surface).

---

# Related Contracts

- **Changed (this task, spec-first)**: `console-web/architecture.md` (audit feature module), `console-integration-contract.md` § 2.4.2 (GAP audit cross-reference).
- **Consumed (unchanged, authoritative)**: GAP `admin-api.md` § `GET /api/admin/audit`.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new `features/audit` module + `(console)/audit` route + `api/audit` proxy + shared resilience reuse.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001/002a/002 established). All GAP calls server-side with the operator token; tokens/PII never to client JS or logs.
- ADR-MONO-013 Model B: the console renders the audit screen itself by calling GAP `GET /api/admin/audit`. Read-only — the console performs no write; no idempotency/transaction concern.
- ADR-MONO-014: `/api/admin/**` credential = exchanged operator token (`getOperatorToken()`); producer resolves operator tenant scope from `admin_operators.tenant_id`; the console additionally sends the selected `X-Tenant-Id` and (only on explicit SUPER_ADMIN cross-tenant) the `tenantId` query param.

---

# Implementation Notes

- Spec-first hard gate (HARDSTOP-06): reconcile `console-web/architecture.md` + `console-integration-contract.md` § 2.4.2 before/with code, same PR.
- Reuse, do not reinvent: `getOperatorToken()`/`getActiveTenant()` (session.ts), `errors.ts` taxonomy + the FE-002 `_proxy` error-mapping + `accounts-api.ts` `callGapAdmin`-style hardened call site (read-only variant: no reason/idempotency branch), `features/accounts` module shape. The new audit error type may reuse `AccountsUnavailableError`'s shape or add a sibling — prefer reuse.
- This is **read-only**: do not carry over FE-002's mutation/destructive scaffolding. The discriminated-union row rendering + the intersection-permission UX are the real complexity here.
- Permission-aware UX: if the operator's role/permissions are derivable client-side (e.g. from session/claims already available), pre-disable the login-history/suspicious source options with an explanation rather than letting the click 403; always still handle the 403 server response defensively.
- Never log tokens or audit PII (account ids, masked IPs, geo) at info level; redact in structured logs (the producer already masks PII — do not un-mask or buffer it into client state beyond render).
- Recommend implementation model: **Opus** (security/audit data surface, intersection-permission + tenant-scope UX, discriminated rendering — interpretive, security-sensitive even though read-only). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`.
- Branch name must not contain the `master` substring.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate (FE-001/002a/002 precedent); Playwright/Testcontainers E2E is CI/manual.

---

# Edge Cases

- Operator has `audit.read` but **not** `security.event.read` → `source=admin` works; selecting `login_history`/`suspicious` → `403 PERMISSION_DENIED` → inline explanation; ideally those source options are pre-disabled with a tooltip rather than erroring on click.
- `SUPPORT_LOCK` role (audit.read only) → admin source OK, security source 403 (per producer role matrix) — handled as above.
- Non-SUPER_ADMIN sets a foreign `tenantId` → `403 TENANT_SCOPE_DENIED` → inline "not permitted for that tenant"; the console should not offer a free-text tenant override to non-super operators (only the standard tenant selector).
- No active tenant selected → block the query with an actionable "select a tenant" state (never send empty `X-Tenant-Id`).
- `from > to` or `size > 100` → client-side guard prevents the call; if the producer still returns `422 VALIDATION_ERROR`, render field-level inline errors (no crash).
- Unknown/future `source` value in a row → render a generic row (do not throw on the discriminated union).
- Large result set → server-side pagination only; never buffer the full audit log into client state.
- The audit query itself is meta-audited producer-side (A5) — the console must not suppress/retry in a way that floods meta-audit; a single user-initiated query = one call (no aggressive auto-refetch loop).

# Failure Scenarios

- GAP token leaks onto `/api/admin/**` (using `getAccessToken()` instead of `getOperatorToken()`) → re-opens the #569 defect; AC + test assert the operator-cookie bearer.
- Mutation scaffolding carried over from FE-002 (reason/idempotency/destructive dialog on a read-only slice) → wrong; AC + test assert no `X-Operator-Reason`/`Idempotency-Key` and no destructive dialogs.
- Cross-tenant leak (wrong/empty `X-Tenant-Id`, or offering a tenant override to a non-super operator) → producer rejects; console must send the selected tenant and gate when none selected.
- Discriminated union crash on an unexpected/missing `source` → must degrade to a generic row, never throw.
- 503/timeout blanks the whole console → violates § 2.5; test asserts only the audit section degrades.
- Spec not reconciled before code → HARDSTOP-06; AC binds the spec cross-ref into the same PR ahead of code.
- Scope creep into FE-004+ (operators/dashboards) → explicitly Out of Scope; this slice is audit/security read only.

---

# Test Requirements

- vitest (jsdom, mocked fetch): API client (operator-token bearer not GAP token, `X-Tenant-Id`, filter/source/pagination serialization, size cap, **no reason/idempotency headers**, 401/403 PERMISSION_DENIED/403 TENANT_SCOPE_DENIED/422/503 mapping), components/hooks (filter submit, source switch, discriminated row rendering, permission-denied + tenant-denied inline states, pagination, degrade on 503/timeout, re-login on 401, empty state), regression (FE-001/002a/002 suites green; `/audit` nav resolves; `gap.baseRoute` unchanged).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA) on the new screen; no bundle/perf regression beyond FE-001 budget.
- Spec internal-link lint clean; `validate-rules` no new inconsistency.

---

# Definition of Done

- [ ] Spec-first reconciliation (`console-web/architecture.md` audit module + `console-integration-contract.md` § 2.4.2 GAP-audit cross-ref) merged before/with code
- [ ] Unified audit view rendered server-side, tenant-scoped, operator-token-authed, discriminated by `source`, intersection-permission UX, read-only (no mutation artifacts)
- [ ] § 2.5 resilience + permission/tenant-scope inline states implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 2 slice-2 closed; § 3 "audit"/"security" satisfiable; `FE-004` unblocked
- [ ] Ready for review
