# Task ID

TASK-PC-FE-080

# Title

console-web — scm replenishment seed/config operator screen (demand-planning: reorder-policy + sku-supplier-map per-SKU upsert)

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

- **depends on (spec-first cross-project gate — MUST merge first)**: `TASK-SCM-BE-028` (scm-platform — *platform-console operator **config (seed)** consumer reconciliation*). SCM-BE-027 explicitly fenced the `policies` / `sku-supplier-map` seed routes **out** of the console acknowledgment; this screen consumes them, so SCM-BE-028 must sanction them on the producer side **before** this code lands (CLAUDE.md "Specs win over tasks"; the exact gate that bound FE-077 → SCM-BE-027). **Do not start code (or § 2.4.6.2) until SCM-BE-028 is merged.**
- **reuses (do NOT re-derive)**: `TASK-PC-FE-008` (`features/scm-ops`) + `TASK-PC-FE-077` (`features/scm-replenishment`) — the scm client, **per-domain credential rule** (scm = IAM `platform-console-web` OIDC access token via `getDomainFacingToken()`, **never** the IAM operator-token exchange), flat-error-envelope parser, 429 `Retry-After` bounded backoff, the same-origin proxy pattern (`api/scm/demand-planning/**`), and the confirm-dialog mutation UX. This screen adds the **per-SKU seed upsert** surface on top of that foundation.
- **closes the loop UX gap surfaced by FE-077**: when approve fails `SKU_SUPPLIER_UNMAPPED` (422), the operator today has **no console way to add the mapping**. This screen is that fix — the operator can inspect/edit the reorder policy + SKU→supplier mapping that drive evaluation, then return to 보충 and approve.
- **part of**: the ADR-MONO-027 loop's **operator config surface** in the console — the third SCM drill-in tab (운영 + 보충 + **설정**).
- **contract-extension + first scm config-mutation → Opus**: adds console-side § 2.4.6.2 and a PUT-upsert mutation surface; depends on a cross-project spec reconciliation. Per ADR-MONO-013 § D6 ("contract ext → Opus") → **Opus**.

# Goal

Build the console's **scm replenishment seed/config operator screen** — a server-side, tenant-scoped surface over demand-planning's existing gateway seed routes, so an operator can inspect and upsert the per-SKU data that drives reorder evaluation:

- **reorder policy** — `GET /api/v1/demand-planning/policies/{skuCode}` (inspect; `200` or `404 POLICY_NOT_FOUND`) and `PUT .../policies/{skuCode}` (upsert body `{ reorderPoint, safetyStock, reorderQty }` → `200` upserted policy).
- **SKU→supplier mapping** — `GET /api/v1/demand-planning/sku-supplier-map/{skuCode}` (inspect; `200` or `404 MAPPING_NOT_FOUND`) and `PUT .../sku-supplier-map/{skuCode}` (upsert body `{ supplierId, defaultOrderQty, leadTimeDays, currency }` → `200` upserted mapping).

**Design constraint — no list route**: the producer exposes only per-`{skuCode}` GET/PUT (no "list all policies/mappings"). The screen is therefore **SKU-code-driven**: the operator enters a SKU code, the screen GETs both rows (policy + mapping), shows them (or an actionable "not set yet" empty state on 404), and lets them upsert each via PUT. Auth reuses the per-domain credential rule (IAM OIDC access token, `tenant_id=scm|*`, never the operator-token exchange). Editing seed rows changes **future** evaluation only — the screen must make clear it does **not** retroactively change existing suggestions or POs and does **not** dispatch anything.

# Scope

## In Scope

### Spec-first (console-side, lands before/with code, same PR — after SCM-BE-028 merges)

- `projects/platform-console/specs/contracts/console-integration-contract.md` — add **§ 2.4.6.2 "scm demand-planning reorder-policy + sku-supplier-map seed/config operator surface (TASK-PC-FE-080 — cross-reference, not a redefinition)"**, mirroring § 2.4.6.1 (FE-077):
  - Authoritative producer = scm [`demand-planning-api.md`](../../../scm-platform/specs/contracts/http/demand-planning-api.md) (consumed unchanged). Binds the per-SKU `policies` + `sku-supplier-map` GET (inspect) + PUT (upsert).
  - **Auth**: reuse (do NOT re-derive, do NOT diverge) the § 2.4.5/§ 2.4.6 per-domain credential rule — scm = IAM `platform-console-web` OIDC access token (`getDomainFacingToken()`), `tenant_id=scm|*`; never the § 2.6 operator-token exchange. Same credential as the read + action surfaces.
  - **Mutation discipline**: PUT is idempotent **upsert** (full-row replace) — a confirm step is required UX (it mutates seed state) but **no** invented `Idempotency-Key`, **no** IAM `X-Operator-Reason` header (the producer defines neither; the body IS the full row). Record what `demand-planning-api.md` actually defines.
  - **Config-surface semantics surfaced in UI (normative)**: editing affects **future** suggestion evaluation only; the screen must not imply it changes existing suggestions/POs or dispatches anything. 404 on GET = "not configured yet", a first-time **create** via PUT (not an error toast).
  - **Resilience (§ 2.5)**: scm flat error envelope `{ code, message, timestamp|details? }`; `POLICY_NOT_FOUND`/`MAPPING_NOT_FOUND` (404 → empty/create state, **not** an error), `VALIDATION_ERROR` (422 → inline field errors), plus the shared § 2.4.6 mappings: `401` → forced IAM re-login; `403 TENANT_FORBIDDEN`/`FORBIDDEN` → inline; `429 RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) → bounded backoff; `503`/timeout → only this section degrades.
  - § 3 GAP-parity matrix **not** mutated (additive non-IAM domain surface; no § 3 line — like § 2.4.6.1).
- `projects/platform-console/specs/services/console-web/architecture.md` — extend the `features/scm-replenishment` module entry (or add a sibling `features/scm-config`) + the `/scm/config` route + the `policies`/`sku-supplier-map` proxy to the Layered-by-Feature map (canonical Identity table + `### Service Type Composition` H3 untouched; ADR-MONO-012 D3 form preserved).
- scm specs **unchanged by this task** (cross-reference only; the producer-side acknowledgment is SCM-BE-028).

### Code (`apps/console-web`, follows the spec)

- New surface under `src/features/scm-replenishment/` (reuse the module; or a sibling `features/scm-config/` — share the `features/scm-ops`/`scm-replenishment` client/auth/envelope/backoff primitives, do not fork):
  - `api/` — server-side demand-planning seed client. Credential = `getDomainFacingToken()` (IAM OIDC), asserted never `getOperatorToken()`. Base URL reuse `SCM_GATEWAY_BASE_URL`. `getPolicy(skuCode)` / `putPolicy(skuCode, body)` / `getSupplierMap(skuCode)` / `putSupplierMap(skuCode, body)`. AbortController hard timeout; scm flat-envelope parser; 429 `Retry-After` bounded backoff; **404 surfaced as a typed "not found" result, not a thrown error**.
  - `api/types.ts` — zod view-models for the policy (`{ reorderPoint, safetyStock, reorderQty }`) + mapping (`{ supplierId, defaultOrderQty, leadTimeDays, currency }`); tolerant/forward-compatible.
  - `hooks/` — TanStack Query: per-SKU read queries (policy + mapping, enabled only once a SKU code is entered) + **PUT upsert mutations** that invalidate the corresponding read on success.
  - `components/` — a SKU-code lookup input + two upsert forms (policy, supplier-mapping) with validation, a confirm step on PUT, "not configured yet → create" empty states on 404, and a clear note that edits affect **future** evaluation only. WCAG AA (axe), keyboard-operable; currency as a constrained input.
  - `route` — `src/app/(console)/scm/config/…` server component (a third sub-route of the `/scm` section) **or** a 설정 tab; registry-driven (`productKey=scm`); in-console nav under the scm group (운영 + 보충 + **설정**).
  - `proxy` — `src/app/api/scm/demand-planning/policies/[skuCode]` + `src/app/api/scm/demand-planning/sku-supplier-map/[skuCode]` same-origin routes (GET + PUT) attaching the IAM OIDC token server-side; scm flat error-envelope mapping.
- Resilience (§ 2.5): demand-planning down (503/429-storm/timeout) → only this section degrades; 401 → forced IAM re-login; never blank the shell.

### Tests (vitest, jsdom, mocked fetch — FE-001..079 lane)

- Auth: IAM-OIDC-token bearer on every policy/mapping call (GET **and** PUT); operator-token path **absent** (reuse FE-077 assertion shape).
- Upsert: PUT calls the right route with the full-row body; gated by a confirm step; **no** `Idempotency-Key`, **no** `X-Operator-Reason` header (asserted absent); success invalidates the read.
- 404 handling: GET 404 (`POLICY_NOT_FOUND`/`MAPPING_NOT_FOUND`) renders a "not configured yet / create" state, **not** an error toast; a subsequent PUT creates it.
- Validation: `VALIDATION_ERROR` (422) maps to inline field errors; the screen does not lose the entered values.
- Config-surface invariant surfaced (test asserts the "affects future evaluation only" affordance; the screen issues no suggestion/PO/dispatch call — only policies/sku-supplier-map GET/PUT).
- Resilience: scm flat error-envelope parsing; 401/403/429 `Retry-After`/503/timeout mapping; per-section degrade; mutation invalidation.
- Regression: FE-001..079 suites green; GAP path still operator-token; existing `features/scm-ops` (운영) + `features/scm-replenishment` (보충) sections + routes unchanged; new 설정 nav/route resolves; the `tsc --noEmit` typecheck step is green (run it explicitly).

## Out of Scope

- `TASK-SCM-BE-028` itself (the scm-side producer acknowledgment — separate scm task; this task is blocked on it, does not perform it).
- A "list all policies / mappings" surface (no producer list route exists; SKU-code-driven only).
- PO **submit/confirm/cancel** and the approve/dismiss surface (FE-077, unchanged beyond linking).
- Any change to scm `demand-planning-api.md` / other scm producer endpoints (cross-reference only).
- Any GAP/IAM-side change; § 3 GAP-parity matrix not mutated; finance/erp/wms sections untouched.
- A bootstrap of `supplier-service` or a supplier picker — `supplierId` is a free-text/uuid input in v1 (the `sku_supplier_map` is the deliberate minimal stand-in per ADR-MONO-027 D3; no supplier master to pick from yet).

# Acceptance Criteria

- [ ] **Prerequisite satisfied first**: `TASK-SCM-BE-028` merged before this task's code (spec-first; linked in Dependency Markers).
- [ ] Console renders an scm seed/config operator screen server-side, tenant-scoped (`tenant_id=scm|*`), authenticated with the **IAM OIDC access token** (asserted bearer = the IAM-session cookie, never the operator token), consuming demand-planning `policies` + `sku-supplier-map` GET/PUT. scm producer specs unchanged.
- [ ] **SKU-code-driven**: operator enters a SKU code; both rows are GET; 404 renders a "not configured yet / create" state (not an error); PUT upserts each row.
- [ ] **Mutation discipline**: PUT gated by a confirm step; full-row body; **no** invented `Idempotency-Key`, **no** IAM `X-Operator-Reason` header (asserted absent); success invalidates the read.
- [ ] **Config-surface invariant surfaced**: edits affect **future** evaluation only; the screen issues no suggestion/PO/dispatch call (only policies/sku-supplier-map GET/PUT).
- [ ] **Resilience (§ 2.5)**: scm flat envelope parsed; `VALIDATION_ERROR` inline; 401 → re-login; 403 → inline; 429 `Retry-After` bounded backoff; 503/timeout → section-only degrade.
- [ ] Tokens/PII never logged; § 2.4.6.2 + `console-web/architecture.md` module merged before/with code; scm specs unchanged; ADR-MONO-012 D3 canonical form intact; § 3 matrix not mutated.
- [ ] `pnpm build` + `pnpm lint` (0) + **`npx tsc --noEmit` (0)** + `pnpm exec vitest run` all green (new + FE-001..079; no regression); axe WCAG AA.
- [ ] Scope = `projects/platform-console/` only (scm cross-reference); the ADR-MONO-027 loop's console operator surface now lets the operator **fix** the `SKU_SUPPLIER_UNMAPPED` gap without leaving the console.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md` § D3 (`sku_supplier_map`) / § D4 (`reorder_policy`) / rest-api facet ("CRUD the seed")
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 Model B / § D6 ("contract ext → Opus")
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.6/§ 2.4.6.1 (per-domain credential rule + the FE-077 binding this extends) — this task adds § 2.4.6.2
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; `features/scm-replenishment` reused)
- `projects/platform-console/tasks/done/TASK-PC-FE-077-console-scm-replenishment-suggestions-operator-screen.md` (the approve/dismiss + credential + flat-envelope + proxy foundation reused)
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` (authoritative producer — policies + sku-supplier-map GET/PUT, error codes; consumed unchanged)
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (the operator-config consumer acknowledgment SCM-BE-028 lands)
- `projects/scm-platform/tasks/ready/TASK-SCM-BE-028-platform-console-seed-config-consumer-reconciliation.md` (the spec-first prerequisite)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query read + PUT-upsert mutation with invalidation, HttpOnly cookie auth, per-domain credential reuse, confirm-dialog mutation UX, 404-as-empty-state, rate-limit backoff), a11y, security review (non-GAP write trust boundary; spec-first cross-project consumption discipline).

---

# Related Contracts

- **Prerequisite (scm project-internal, separate task — must land first)**: scm `gateway-public-routes.md` operator-**config** consumer acknowledgment (`TASK-SCM-BE-028`).
- **Changed (this task, console-side spec-first)**: `console-integration-contract.md` **new § 2.4.6.2** (scm seed/config binding; reuses § 2.4.6 credential rule) + `console-web/architecture.md` (config surface).
- **Consumed (unchanged, authoritative — scm-owned)**: `demand-planning-api.md` (policies + sku-supplier-map GET/PUT).
- **Not touched**: scm `procurement-api.md` / `inventory-visibility-api.md`; GAP `admin-api.md`; § 3 GAP-parity matrix.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — scm seed/config surface (server-side demand-planning policies + sku-supplier-map client, IAM-OIDC credential, GET inspect + PUT upsert) + `(console)/scm/config` route (or 설정 tab) + `api/scm/demand-planning/{policies,sku-supplier-map}/[skuCode]` proxy + in-console nav (운영 + 보충 + 설정).

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001..079 established). All demand-planning calls server-side; tokens/PII never to client JS or logs.
- ADR-MONO-013 Model B: the console renders the scm config surface by calling demand-planning's **existing** gateway seed routes. Reuses the FE-077 per-domain credential rule (scm = IAM OIDC access token direct, not the operator-token exchange).
- The seed surface is **per-SKU GET/PUT** (no list); the config mutation rides the same operator credential as the action surface — the gate is server-side `tenant_id` validation, not a stronger credential.
- Single-domain section (not the Phase-7 `console-bff`).

---

# Implementation Notes

- **Blocked until SCM-BE-028 lands** — do not start code (or § 2.4.6.2) until the scm operator-config consumer acknowledgment is merged (the FE-077 → SCM-BE-027 precedent, now for the seed routes).
- Reuse FE-077's `features/scm-replenishment` client/auth/envelope/backoff/proxy primitives verbatim (share, do not fork); the net-new work is the policy + mapping GET/PUT client, the SKU-code-driven lookup + upsert forms, the 404-as-empty-state handling, and surfacing the future-evaluation-only invariant.
- **Do not** carry IAM § 2.4.1 mutation scaffolding (`Idempotency-Key`, `X-Operator-Reason` header) — `demand-planning-api.md` defines neither (PUT body IS the full row, idempotent upsert). Inventing them is a defect (assert their absence).
- ⚠️ **Run `npx tsc --noEmit` explicitly** as a local gate — the console-web CI "Frontend unit tests" job runs typecheck as a separate step that `pnpm exec vitest run` does **not** catch (FE-077 CI-RED precedent: a `vi.fn((url)=>…)` single-arg mock made `mock.calls[i][1]` a TS error that vitest passed locally but CI failed).
- Recommend implementation model: **Opus** (contract extension § 2.4.6.2 + config-mutation discipline + cross-project spec dependency). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)` with absolute worktree paths. Dispatcher independently re-verifies (IAM-OIDC bearer not operator token; no invented headers; future-evaluation-only; 404-as-empty-state; no suggestion/PO call) + runs `tsc --noEmit` before any close.
- Branch name must not contain the `master` substring.
- Local Docker unavailable → vitest jsdom/mocked-fetch + `tsc --noEmit` is the local gate; Playwright/Testcontainers E2E is CI/manual.

---

# Edge Cases

- Operator's IAM token not scm-eligible (no `tenant_id=scm` and not SUPER_ADMIN `*`) → section blocks with an actionable "no scm-scoped access" state; demand-planning rejects cross-tenant (`403 TENANT_FORBIDDEN`) producer-side.
- GET on an unconfigured SKU → `404 POLICY_NOT_FOUND` / `MAPPING_NOT_FOUND` → render a "not configured yet" create state, **not** an error toast.
- PUT with invalid body (e.g. negative qty) → `VALIDATION_ERROR` (422) inline field errors; entered values preserved.
- 429 `RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) on GET or PUT → bounded backoff + inline "rate-limited, retrying"; no auto-retry storm.
- demand-planning 503/timeout → only this section degrades; the 운영 + 보충 sections + the console shell stay intact.
- 401 (IAM session expired) → whole-session forced IAM re-login.
- `supplierId` is free-text/uuid (no supplier master in v1) → validate shape only, do not resolve against a non-existent supplier-service.

# Failure Scenarios

- Code started before SCM-BE-028 merges → spec-first violation; the Dependency Marker + AC gate it on the linked prerequisite.
- Client uses `getOperatorToken()` instead of `getDomainFacingToken()` → wrong credential; test asserts IAM-OIDC bearer + absence of the operator-token path for scm.
- Invented `Idempotency-Key` / `X-Operator-Reason` header attached to PUT → defect (producer defines neither); test asserts neither header is sent.
- GET 404 treated as a hard error (error toast, no create path) → wrong; test asserts the not-configured-yet create state.
- Screen issues a suggestion/PO/dispatch call to "apply" a config edit → violates the config-surface invariant; test asserts only policies/sku-supplier-map GET/PUT are called.
- scm flat error envelope mis-parsed as wms's nested `{ error: { code } }` → mis-rendered errors; test asserts the scm flat-shape parser.
- This section's failure blanks the whole console shell → violates § 2.5; test asserts section-only degrade.
- § 3 GAP-parity matrix mutated for scm → wrong; AC forbids it.
- `tsc --noEmit` left unrun → CI-RED risk (FE-077 precedent); AC requires it green.

---

# Test Requirements

- vitest (jsdom, mocked fetch): auth (IAM-OIDC bearer on GET + PUT; operator-token path absent), upsert (PUT right route + full-row body, confirm-gated, no invented headers), 404-as-empty-state (+ subsequent create), validation inline, config-invariant surfaced + no suggestion/PO/dispatch call, scm flat error-envelope + 401/403/429 `Retry-After`/503/timeout mapping + per-section degrade + mutation invalidation, components/hooks (SKU lookup, two upsert forms, empty/permission/degraded placeholders), regression (FE-001..079 green; per-domain credential rule holds; existing 운영 + 보충 sections/routes unchanged; new 설정 nav/route resolves).
- `pnpm build` + `pnpm lint` (0) + **`npx tsc --noEmit` (0)** green; axe (WCAG AA).
- Spec internal-link lint clean; `validate-rules` no new inconsistency; ADR-MONO-012 D3 canonical form intact.

---

# Definition of Done

- [ ] `TASK-SCM-BE-028` (scm operator-config consumer acknowledgment) authored, linked, merged (gate for code)
- [ ] Console-side spec-first (`console-integration-contract.md` § 2.4.6.2 + `console-web/architecture.md` config surface) merged before/with code
- [ ] scm seed/config operator screen rendered server-side, tenant-scoped, IAM-OIDC-token-authed; SKU-code-driven GET inspect + PUT upsert with confirm; 404-as-empty-state; config-surface invariant surfaced
- [ ] § 2.5 resilience (scm flat envelope, per-section degrade, 401 whole-session re-login, bounded 429 backoff, mutation invalidation) implemented + tested
- [ ] `pnpm build`/`lint`/`tsc --noEmit`/`vitest` green + axe AA; scope = platform-console only; no regression
- [ ] Acceptance Criteria all satisfied; the operator can fix the `SKU_SUPPLIER_UNMAPPED` gap in-console
- [ ] Ready for review
