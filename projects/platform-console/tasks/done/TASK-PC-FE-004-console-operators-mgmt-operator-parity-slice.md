# Task ID

TASK-PC-FE-004

# Title

console-web Phase 2 slice 3 — GAP operators management operator parity (list/create/edit-roles/change-status/change-password)

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

- **depends on**: `TASK-PC-FE-003` (audit slice + the read-only `features/*` pattern) — **merged** (PR #576, main `f0b26b14`); `TASK-PC-FE-002` (mutation pattern: reason/idempotency/confirm) — merged (#575). Operator-auth bridge (FE-002a / ADR-MONO-014 § D5) complete.
- **part of**: ADR-MONO-013 § D6 **Phase 2** — **slice 3 of 5**: FE-002 accounts ✅ → FE-003 audit/security ✅ → **FE-004 operators** (this) → `FE-005` dashboards → `FE-006` parity-verify (= ADR-MONO-013 Phase 3 admin-web-retirement gate).
- **prerequisite for**: `TASK-PC-FE-005`; satisfies the `console-integration-contract.md` § 3 parity line **"operators: create, edit-roles, change-status, change-password"** (FE-006 formally verifies).
- **spec-first**: the console-side `specs` cross-reference of the GAP operators surface lands **before/with** the code (HARDSTOP-06). GAP `admin-api.md` is **unchanged** (authoritative producer).
- **task-file**: the FE-004 task file is committed in `tasks/ready/` in this PR's first (spec) commit (apply the FE-003 gap-prevention rule: `git add` the task md explicitly — do NOT leave it untracked, and do NOT touch the still-untracked FE-002/FE-002a task files; those belong to the separate lifecycle chore).

# Goal

Build the console's **GAP operators-management surface** — the third Phase 2 operator-parity slice (`console-integration-contract.md` § 3 "operators" line). This is the **most privilege-sensitive** slice: creating operators and changing roles/status is the operator-privilege-escalation surface. The console renders, server-side and tenant-scoped, with the **exchanged operator token** (never the GAP OIDC token — FE-002a #569 invariant), the five GAP operators operations:

1. **list** — `GET /api/admin/operators` (status filter + pagination)
2. **create** — `POST /api/admin/operators` (mutation; **`X-Operator-Reason` + `Idempotency-Key`** both required per producer)
3. **edit-roles** — `PATCH /api/admin/operators/{operatorId}/roles` (mutation; **`X-Operator-Reason` required, NO `Idempotency-Key`** — full-replace PATCH, per producer)
4. **change-status** — `PATCH /api/admin/operators/{operatorId}/status` (mutation; **`X-Operator-Reason` required**, ACTIVE/SUSPENDED)
5. **change-password** — `PATCH /api/admin/operators/me/password` (self; mutation)

All require permission `operator.manage` (granted to `SUPER_ADMIN` only). After this task the § 3 "operators" line is satisfiable; `FE-006` verifies.

# Scope

## In Scope

### Spec-first (lands before/with code, same PR commit 1)
- `projects/platform-console/specs/services/console-web/architecture.md` — add the **operators feature module** (`features/operators`: api / hooks / components / route) to the Layered-by-Feature map, mirroring `features/accounts`/`features/audit`. Canonical form intact.
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4 (new sub-section, e.g. **§ 2.4.3 "GAP operators surface"**) — per-domain **cross-reference** to the authoritative producer `global-account-platform/specs/contracts/http/admin-api.md` §§ `GET /api/admin/operators`, `POST /api/admin/operators`, `PATCH …/{operatorId}/roles`, `PATCH …/{operatorId}/status`, `PATCH …/me/password` + the consumer obligations: operator-token auth, `X-Tenant-Id` tenant scoping (+ `tenantId` body field on create; `tenantId='*'` platform-scope is SUPER_ADMIN-only → `403 TENANT_SCOPE_DENIED`), the **per-endpoint header matrix** (create ⇒ `X-Operator-Reason`+`Idempotency-Key`; roles/status ⇒ `X-Operator-Reason` only; password ⇒ self), `operator.manage`/SUPER_ADMIN gating, privilege-sensitive confirm UX, password-policy mirroring. **Do not redefine** the GAP contract — cross-reference only. § 3 "operators" line noted satisfiable.

### Code (`apps/console-web`, follows the spec)
- `src/features/operators/` (Layered-by-Feature, mirrors `features/accounts`):
  - **server-side API client** (`api/`): one function per the 5 ops. Auth = `Authorization: Bearer <operator token>` via `getOperatorToken()` (NOT `getAccessToken()`; absent → `ApiError(401)` before any fetch — no GAP fallback). Tenant = `X-Tenant-Id` from `getActiveTenant()` (block, never empty). **Per-endpoint header fidelity (do NOT blanket-apply FE-002's mutation headers)**: `create` sends `X-Operator-Reason` **and** `Idempotency-Key` (`crypto.randomUUID()`); `roles` and `status` send `X-Operator-Reason` **only** (no `Idempotency-Key` — the producer does not list it; sending it is a contract deviation); `me/password` is the self path. `reason` non-empty fail-safe before any mutating fetch. AbortController timeout; structured logging — **never log tokens, operator emails, or passwords** (redact; passwords never logged or echoed at all).
  - **types** (`api/types.ts`): zod request/response per endpoint (list page envelope; create 201; roles/status 200). Role names are an enum from the producer (`SUPER_ADMIN`/`SUPPORT_LOCK`/`SUPPORT_READONLY`/`SECURITY_ANALYST`/…); tolerate unknown future roles in list rendering (generic chip, no crash).
  - **hooks** (`hooks/`): TanStack Query — list read hook (status filter, pagination) + mutation hooks (create / edit-roles / change-status / change-password) with invalidation of the operators list + (roles change) an awareness note that the producer invalidates its own perm cache.
  - **components** (`components/`): operators list table (status filter, pagination), a **create-operator form** (email, displayName, password, roles multi-select, tenantId — client-side password-policy mirror: ≥10 chars, ≥1 letter/digit/special; never echo the password in logs/state beyond the field), an **edit-roles** editor (role multi-select, empty allowed = remove all → strong confirm), a **change-status** toggle (ACTIVE↔SUSPENDED), a **self change-password** form (current + new + confirm). Every mutating action behind a **reason-capture + confirm dialog** (privilege-sensitive); creating an operator and granting `SUPER_ADMIN` / suspending an operator are high-impact → explicit confirm copy. WCAG AA (axe), keyboard-operable dialogs.
  - **route**: `src/app/(console)/operators/…` server components; add an in-console nav entry to `/operators` (do NOT change catalog `gap.baseRoute` — stays `/accounts`).
  - **proxy** (`src/app/api/operators/**/route.ts`): same-origin proxies attaching the operator token server-side; reuse the FE-002 `_proxy` error-mapping; per-route method (POST create, PATCH roles/status/password) and per-route header set per the matrix above.
- Resilience / permission UX (§ 2.5 + `operator.manage` gating): 401 → forced re-login (no partial authed state); **403 `PERMISSION_DENIED`** (not SUPER_ADMIN / lacks `operator.manage`) → the whole operators section is an inline "not permitted" state (ideally the nav entry itself is gated when derivable; always handle the server 403); **403 `TENANT_SCOPE_DENIED`** (non-platform operator creating a `tenantId='*'` operator) → inline actionable; `409 OPERATOR_EMAIL_CONFLICT` → inline field error on email; `400 ROLE_NOT_FOUND`/`VALIDATION_ERROR` → inline field-level; `404 OPERATOR_NOT_FOUND` → inline; 503/timeout → only the operators section degrades (shell intact).

### Tests (vitest, jsdom, mocked fetch — FE-001/002/002a/003 lane)
- API client per op: operator-token bearer (assert it is the operator cookie, **not** the GAP access token); `X-Tenant-Id` from active tenant; **per-endpoint header matrix asserted**: `create` has BOTH `X-Operator-Reason` + `Idempotency-Key`; `roles`/`status` have `X-Operator-Reason` and **NO `Idempotency-Key`**; password is the self path; reason-empty → request not sent; password never logged. Error mapping (401/403 PERMISSION_DENIED/403 TENANT_SCOPE_DENIED/409/400/404/503) → the resilience contract.
- Components/hooks: list + status filter + pagination; create-form password-policy validation (client mirror) + roles select; edit-roles incl. empty-array strong confirm; change-status confirm; self change-password (current+new+confirm match); reason-gated mutations (no reason → no call); permission-denied inline (non-SUPER_ADMIN); tenant-scope-denied inline; 409 email-conflict inline; degrade on 503/timeout; re-login on 401.
- Regression: existing FE-001/002a/002/003 suites green; `/operators` nav resolves; `gap.baseRoute` (accounts) unchanged.

## Out of Scope

- Other Phase 2 slices: dashboards (`FE-005`), parity-verify (`FE-006`).
- Any GAP-side change (`admin-api.md` / admin-service) — producer authoritative & **unchanged**; cross-reference only.
- TOTP enroll/verify operator flows (not in the § 3 "operators" parity line; separate concern).
- `admin-web` retirement (Phase 3, gated on FE-006). `console-bff` (Phase 7). wms/scm/finance/erp (Phase 4–6).
- Changing the catalog `gap.baseRoute` (operators is an in-console nav destination).
- The still-untracked FE-002/FE-002a task files (separate lifecycle chore — do not touch).

# Acceptance Criteria

- [ ] Console renders all 5 operators operations server-side, tenant-scoped, authed with the **operator token** (test asserts bearer = operator cookie, never the GAP access token).
- [ ] **Per-endpoint header fidelity**: `create` sends `X-Operator-Reason` + `Idempotency-Key`; `edit-roles` and `change-status` send `X-Operator-Reason` and **no `Idempotency-Key`**; no blanket-applied FE-002 headers — asserted by test against the producer contract.
- [ ] Every mutating action is reason-gated + confirm-gated; privilege-high actions (create operator, grant `SUPER_ADMIN`, suspend, remove-all-roles) have explicit confirm copy; the call does not fire until a non-empty reason is entered.
- [ ] `operator.manage`/SUPER_ADMIN gating: `403 PERMISSION_DENIED` → inline "not permitted" (no crash, no re-login loop); `403 TENANT_SCOPE_DENIED` on `tenantId='*'` create by a non-platform operator → inline actionable; `409 OPERATOR_EMAIL_CONFLICT`/`400 ROLE_NOT_FOUND`/`VALIDATION_ERROR`/`404 OPERATOR_NOT_FOUND` → inline field-level.
- [ ] Passwords are never logged, never echoed into structured logs/state beyond the input; client-side password-policy mirror (≥10, letter+digit+special) pre-validates create + self-change.
- [ ] § 2.5 resilience: 401 → re-login (no partial authed state); 503/timeout → operators section degrades only (shell intact).
- [ ] Spec-first: `console-web/architecture.md` (operators module) + `console-integration-contract.md` § 2.4.3 (GAP operators cross-ref) + the FE-004 task file committed in the same PR's first commit, before/with code; GAP `admin-api.md` unchanged; canonical form intact.
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + existing; no regression); axe WCAG AA on new screens; no bundle/perf regression beyond the FE-001 budget.
- [ ] Scope = `projects/platform-console/` only; no churn-clock effect. ADR-MONO-013 Phase 2 slice-3 satisfied; § 3 "operators" satisfiable; `FE-005` unblocked.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D6 (Phase 2)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (operator token = the `/api/admin/**` credential)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.3/§ 2.4 (per-domain surface; tenant scope + idempotency)/§ 2.5 (resilience)/§ 2.6 (operator token)/§ 3 ("operators" parity line)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-001/002a/002/003 patterns)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` §§ `GET /api/admin/operators`, `POST /api/admin/operators`, `PATCH /api/admin/operators/{operatorId}/roles`, `PATCH /api/admin/operators/{operatorId}/status`, `PATCH /api/admin/operators/me/password` (authoritative producer — request/response/**per-endpoint headers**/error tables; **unchanged**)
- `platform/service-types/frontend-app.md` (HttpOnly cookie auth; server-side calls)
- `projects/platform-console/tasks/review/TASK-PC-FE-003-...` + merged FE-002 (`features/accounts` mutation pattern, `_proxy` error-mapping, reason/confirm UX; task-shape parity) + FE-002a (#569 trust boundary)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query, HttpOnly cookie auth, forms), api integration, a11y, security review (privilege management — operator creation/role/status).

---

# Related Contracts

- **Changed (this task, spec-first)**: `console-web/architecture.md` (operators feature module), `console-integration-contract.md` § 2.4.3 (GAP operators cross-reference).
- **Consumed (unchanged, authoritative)**: GAP `admin-api.md` operators endpoints.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new `features/operators` module + `(console)/operators` route + `api/operators` proxies + shared resilience reuse.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001/002a/002/003 established). All GAP calls server-side with the operator token; tokens/PII/passwords never to client JS or logs.
- ADR-MONO-013 Model B: the console renders operators screens itself by calling GAP `/api/admin/operators*`; it owns no domain transaction (create uses the producer-required Idempotency-Key; roles/status are idempotent PATCH per the producer — do not add a key the contract omits).
- ADR-MONO-014: `/api/admin/**` credential = exchanged operator token; producer resolves operator tenant scope from `admin_operators.tenant_id`; the console sends the selected `X-Tenant-Id` and (create) the `tenantId` body field; `tenantId='*'` is SUPER_ADMIN-only producer-enforced.

---

# Implementation Notes

- Spec-first hard gate (HARDSTOP-06): reconcile `console-web/architecture.md` + `console-integration-contract.md` § 2.4.3 + commit the FE-004 task file, before/with code, in commit 1.
- **Per-endpoint header fidelity is the key correctness risk** here: FE-002 applied `X-Operator-Reason`+`Idempotency-Key` to every mutation because every FE-002 mutation required both. FE-004 is NOT uniform — only `create` takes `Idempotency-Key`; `roles`/`status` take reason only. Read each `admin-api.md` operators endpoint's Headers block and implement exactly; a test must pin this matrix.
- Reuse, do not reinvent: `getOperatorToken()`/`getActiveTenant()`, `errors.ts` taxonomy, the FE-002 `_proxy` mapping + `accounts-api.ts` hardened call site (parameterize the header set per endpoint rather than hardcoding the FE-002 pair), the `ConfirmActionDialog` reason-capture component, `features/accounts` module shape.
- Privilege-management UX is security UX: creating an operator, granting `SUPER_ADMIN`, suspending, or removing all roles are high-impact → explicit confirm copy + required reason. Never one-click.
- Passwords: client-side policy mirror is a UX pre-check only (producer is authoritative); never log/echo a password; never place it in query/log/structured-event; clear it from memory on submit where practical.
- Recommend implementation model: **Opus** (operator-privilege-escalation surface + per-endpoint contract fidelity + security UX — highest-sensitivity Phase-2 slice). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`.
- Branch name must not contain the `master` substring.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate (FE-001/002a/002/003 precedent); Playwright/Testcontainers E2E is CI/manual.

---

# Edge Cases

- Operator is not `SUPER_ADMIN` (no `operator.manage`) → entire operators section is an inline "not permitted" state; ideally the `/operators` nav entry is hidden/disabled when derivable, but always handle the server `403` defensively (no crash, no re-login loop).
- Non-platform-scope operator attempts to create a `tenantId='*'` operator → `403 TENANT_SCOPE_DENIED` → inline actionable; the create form must not offer `*` as a tenant option to non-platform operators.
- `409 OPERATOR_EMAIL_CONFLICT` (same tenant+email exists) → inline email-field error, preserve the rest of the form.
- edit-roles with `[]` (remove all roles) is allowed by the producer but high-impact → explicit strong confirm ("this operator will have no roles").
- Granting `SUPER_ADMIN` via edit-roles/create → explicit elevated-privilege confirm copy.
- change-password is **self only** (`/me/`) — there is no admin-set-other-password endpoint in the parity line; do not invent one; the form is the logged-in operator's own password (current + new + confirm; new must satisfy the policy).
- `400 ROLE_NOT_FOUND` (stale role name) → inline; refresh the role list source if it is client-cached.
- Idempotency-Key reuse: a retried confirmed *create* reuses its key; a fresh create attempt gets a new key. roles/status carry no key (idempotent PATCH per producer) — do not add one.
- No active tenant selected → block with an actionable "select a tenant" state (never empty `X-Tenant-Id`).

# Failure Scenarios

- GAP token leaks onto `/api/admin/**` (using `getAccessToken()`) → re-opens #569; AC + test assert the operator-cookie bearer.
- Header-matrix drift (adding `Idempotency-Key` to roles/status, or omitting it on create) → contract violation; AC + test pin the exact per-endpoint matrix.
- Password leaked into logs/structured events/state → security regression; test asserts no password in any logged payload.
- Privilege action with no confirm/reason gate (one-click create / role-grant / suspend) → security regression; test asserts the reason+confirm gate (elevated copy for SUPER_ADMIN grant / remove-all-roles).
- Offering `tenantId='*'` to a non-platform operator → producer 403 anyway, but the UI must not present it.
- 503/timeout blanks the whole console → violates § 2.5; test asserts only the operators section degrades.
- Spec not reconciled / FE-004 task file left untracked → HARDSTOP-06 / the FE-002/002a gap repeats; AC binds spec + task file into commit 1 ahead of code.
- Scope creep into FE-005+ (dashboards) → explicitly Out of Scope.

---

# Test Requirements

- vitest (jsdom, mocked fetch): API client per op (operator-token bearer not GAP, `X-Tenant-Id`, **per-endpoint header matrix**, reason-gating, password never logged, 401/403 PERMISSION_DENIED/403 TENANT_SCOPE_DENIED/409/400/404/503 mapping), components/hooks (list+filter+pagination, create-form policy validation + roles select, edit-roles incl. empty strong-confirm, change-status confirm, self change-password match, reason-gated mutations, permission-denied + tenant-scope-denied + email-conflict inline, degrade on 503/timeout, re-login on 401), regression (FE-001/002a/002/003 suites green; `/operators` nav resolves; `gap.baseRoute` unchanged).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA) on new screens; no bundle/perf regression beyond FE-001 budget.
- Spec internal-link lint clean; `validate-rules` no new inconsistency.

---

# Definition of Done

- [ ] Spec-first reconciliation (`console-web/architecture.md` operators module + `console-integration-contract.md` § 2.4.3 + FE-004 task file) merged in commit 1, before/with code
- [ ] All 5 operators operations rendered server-side, tenant-scoped, operator-token-authed, **per-endpoint header fidelity**, reason+confirm on mutations, password-safe
- [ ] `operator.manage`/SUPER_ADMIN gating + § 2.5 resilience (re-login / degrade / inline errors) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 2 slice-3 closed; § 3 "operators" satisfiable; `FE-005` unblocked
- [ ] Ready for review
