# Task ID

TASK-PC-FE-077

# Title

console-web — scm replenishment-suggestions operator screen (demand-planning: suggestion list + approve/dismiss operator gate)

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

- **depends on (spec-first cross-project gate — MUST merge first)**: `TASK-SCM-BE-027` (scm-platform — *platform-console operator **action** consumer reconciliation*). The existing scm acknowledgment (SCM-BE-015 ⊃ FE-008) is explicitly **read-only**; this screen consumes demand-planning **operator-action** routes (`approve`/`dismiss`), which SCM-BE-027 must sanction on the producer side **before** this code lands (CLAUDE.md "Specs win over tasks"; the exact gate that bound FE-008 → SCM-BE-015). **Do not start code (or § 2.4.6.1) until SCM-BE-027 is merged.**
- **reuses (do NOT re-derive)**: `TASK-PC-FE-008` (`features/scm-ops`) — the scm read client, flat-error-envelope parser, 429 `Retry-After` bounded backoff, S5 surfacing, and the **per-domain credential rule** (§ 2.4.5/§ 2.4.6: scm = IAM `platform-console-web` OIDC access token via `getAccessToken()`, **never** the IAM operator-token exchange). This screen adds the **first scm operator-mutation** (approve/dismiss) on top of that read foundation.
- **first scm write surface — mutation discipline applies**: unlike FE-008 (read-only, scm had no admin-service), demand-planning exposes operator actions. This task introduces scm's mutation UX (confirm dialog, optimistic-safe invalidation, idempotency-aware error handling) — but the **credential stays the IAM OIDC token** (scm has no operator-token-exchange; the gate is server-side `tenant_id=scm` validation + the producer's DRAFT-PO-only invariant, not a stronger credential). Carrying IAM § 2.4.1 `X-Operator-Reason`/`Idempotency-Key` scaffolding over verbatim is a defect — follow what `demand-planning-api.md` actually defines (see Scope).
- **part of**: closes the ADR-MONO-027 loop's **operator-facing surface** in the console — the human gate that turns a wms-alert-driven `SUGGESTED` reorder into a DRAFT PO. Backend (SCM-BE-024) + read console (FE-008) exist; this is the missing approve/dismiss screen.
- **contract-extension + first scm write → Opus**: adds console-side § 2.4.6.1 and establishes scm mutation discipline; depends on a cross-project spec reconciliation. Per ADR-MONO-013 § D6 ("contract ext → Opus") → **Opus**.

# Goal

Build the console's **scm replenishment-suggestions operator screen** — the human operator gate of the ADR-MONO-027 wms→scm replenishment loop. A server-side, tenant-scoped surface over demand-planning's existing gateway operator routes:

- **read** — `GET /api/v1/demand-planning/suggestions` (list; filter by `status` ∈ `SUGGESTED|APPROVED|MATERIALIZED|DISMISSED`, `skuCode`; paginated) and `GET /api/v1/demand-planning/suggestions/{id}` (detail).
- **operator actions** — `POST /api/v1/demand-planning/suggestions/{id}/approve` (→ resolves `sku_supplier_map` → procurement creates a **DRAFT** PO → suggestion `MATERIALIZED` with `materializedPoId`; **idempotent** — re-approve returns the existing `poId`, no duplicate PO; optional body `{ note }`) and `POST .../dismiss` (`* → DISMISSED`, releases the open-suggestion guard; optional body `{ reason }`; idempotent re-dismiss = no-op).

This is **read + two operator actions**, not full CRUD: the `policies` / `sku-supplier-map` **seed** routes are out (admin-seed, not the operator gate). Auth reuses the FE-008 per-domain credential rule (IAM OIDC access token, `tenant_id=scm|*`, never the operator-token exchange). The created PO is **DRAFT only** — the screen must make clear the operator still dispatches it via procurement's existing `DRAFT → SUBMITTED` flow (this screen does not submit POs).

# Scope

## In Scope

### Spec-first (console-side, lands before/with code, same PR — after SCM-BE-027 merges)

- `projects/platform-console/specs/contracts/console-integration-contract.md` — add **§ 2.4.6.1 "scm demand-planning replenishment-suggestions operator surface (TASK-PC-FE-077 — cross-reference, not a redefinition)"**, exactly mirroring how § 2.4.5.1 (wms outbound) and § 2.4.7.1 (finance ledger) bind a **second service** under an established domain section:
  - Authoritative producer = scm [`demand-planning-api.md`](../../../scm-platform/specs/contracts/http/demand-planning-api.md) (consumed unchanged). Binds the operator read (`suggestions` list/detail) + the two operator **actions** (`approve`/`dismiss`). `policies`/`sku-supplier-map` seed routes **excluded**.
  - **Auth**: reuse (do NOT re-derive, do NOT diverge) the § 2.4.5/§ 2.4.6 per-domain credential rule — scm = IAM `platform-console-web` OIDC access token (`getAccessToken()`), `tenant_id=scm|*` enforced producer-side; never the § 2.6 operator-token exchange (scm has none). Same credential for reads **and** actions.
  - **Mutation discipline (the net-new part — record what `demand-planning-api.md` actually requires, do NOT cargo-cult IAM § 2.4.1)**: approve/dismiss are `POST` with an **optional** JSON body (`{ note }` / `{ reason }`). The producer is **server-side idempotent by suggestion state** (re-approve returns existing `poId`; re-dismiss no-op) — so a client `Idempotency-Key` header is **not** required by the contract; do **not** invent one, and do **not** attach IAM's `X-Operator-Reason` header (the reason rides in the optional body, not a header). A confirm dialog is required UX for both actions (they mutate domain state), but it must not fabricate header scaffolding the producer never defined.
  - **Operator-gate semantics surfaced in UI (normative)**: approve materialises a **DRAFT** PO only — the screen must show the resulting `poId` + `poStatus: DRAFT` and make explicit that submission is a **separate** procurement step (this screen never SUBMITs). This is the ADR-MONO-027 D5 human-gate invariant made visible.
  - **Resilience (§ 2.5)**: scm flat error envelope `{ code, message, timestamp|details? }`; action-specific producer errors mapped to actionable inline states — `SKU_SUPPLIER_UNMAPPED` (422 → "no supplier mapping; cannot reorder", suggestion stays `SUGGESTED`), `INVALID_SUGGESTION_STATE` (422 → e.g. cannot approve a `DISMISSED` one), `SUGGESTION_ALREADY_MATERIALIZED` (409/200-idempotent → treat the idempotent 200 as success showing existing `poId`; a hard 409 as a benign "already materialized" notice), `SUGGESTION_NOT_FOUND` (404). Plus the shared § 2.4.6 mappings: `401 UNAUTHORIZED` → forced IAM re-login; `403 TENANT_FORBIDDEN`/`FORBIDDEN` → inline "not available / not scoped"; `429 RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) → bounded backoff (no storm); `503`/timeout → only this section degrades (shell intact).
  - § 3 GAP-parity matrix **not** mutated (additive non-IAM domain surface; like § 2.4.5.1/§ 2.4.6/§ 2.4.7.1, this has **no** § 3 line).
- `projects/platform-console/specs/services/console-web/architecture.md` — add the replenishment module + route + proxy to the Layered-by-Feature map (canonical Identity table + `### Service Type Composition` H3 untouched; ADR-MONO-012 D3 form preserved).
- scm specs **unchanged by this task** (cross-reference only; the producer-side acknowledgment is SCM-BE-027).

### Code (`apps/console-web`, follows the spec)

- New feature module `src/features/scm-replenishment/` (Layered-by-Feature; reuses the `features/scm-ops` client/auth/envelope/backoff primitives — import/share, do not fork):
  - `api/` — server-side demand-planning client. Credential = `getAccessToken()` (IAM OIDC cookie), asserted never `getOperatorToken()`. Base URL from the scm gateway env (reuse FE-008's `SCM_GATEWAY_BASE_URL` → `http://scm.local`). Read fns (suggestions list/detail) + **action** fns (approve/dismiss, optional body). AbortController hard timeout; scm flat-envelope parser (reuse FE-008); 429 `Retry-After` bounded backoff.
  - `api/types.ts` — zod view-models for the suggestion (`id, skuCode, warehouseId, supplierId, suggestedQty, status, source, triggerAvailableQty, materializedPoId, createdAt`) + the approve result (`{ id, status, poId, poStatus }`) + dismiss result; tolerant of unknown `status`/`source` enums (generic, no throw — future-proof).
  - `hooks/` — TanStack Query: a read query (suggestions list, sane staleTime, respect rate limit — no tight refetch) + **mutations** (approve/dismiss) that invalidate the list + detail on success (status transitions `SUGGESTED → MATERIALIZED|DISMISSED` must reflect without a manual reload).
  - `components/` — suggestion list (status/skuCode filters, pagination, the `triggerAvailableQty` that explains *why* it was suggested) + detail + **approve/dismiss action UI** with a confirm dialog and the optional note/reason input. On approve success, surface the materialised **DRAFT** `poId`/`poStatus` with an explicit "PO created as DRAFT — submit it in Procurement" affordance (link to / mention the FE-008 scm-ops PO surface; this screen does not SUBMIT). WCAG AA (axe), keyboard-operable; disabled/idempotent-aware action buttons (cannot approve a `DISMISSED`/already-`MATERIALIZED` one — reflect producer state).
  - `route` — `src/app/(console)/scm/replenishment/…` server component (a sub-route of the existing `/scm` section) **or** a tab within the scm operations screen; registry-driven (`productKey=scm`); in-console nav under the scm group.
  - `proxy` — `src/app/api/scm/demand-planning/**` same-origin routes attaching the IAM OIDC token server-side: `GET suggestions`, `GET suggestions/{id}`, `POST suggestions/{id}/approve`, `POST suggestions/{id}/dismiss`. scm flat error-envelope mapping. **No** proxy routes for `policies`/`sku-supplier-map` (out of scope).
- Resilience (§ 2.5): demand-planning down (503/429-storm/timeout) → only this section degrades; 401 → forced IAM re-login; never blank the shell.

### Tests (vitest, jsdom, mocked fetch — FE-001..076 lane)

- Auth: IAM-OIDC-token bearer on every demand-planning call (read **and** action); operator-token path **absent** (reuse FE-008 assertion shape).
- Actions: approve calls `POST .../approve` and on success shows the DRAFT `poId`/`poStatus`; dismiss calls `POST .../dismiss`; both gated by a confirm dialog; optional note/reason passed in the **body** (asserted) — **no** `Idempotency-Key`, **no** `X-Operator-Reason` header invented (asserted absent).
- Idempotency/state: re-approve / approving a `MATERIALIZED` suggestion → idempotent 200 handled as success (existing `poId`, no duplicate-PO assumption); approving a `DISMISSED` one → `INVALID_SUGGESTION_STATE` inline; `SKU_SUPPLIER_UNMAPPED` inline + suggestion stays `SUGGESTED`.
- DRAFT-only invariant surfaced (test asserts the "submit separately in Procurement" affordance; the screen issues no PO submit/confirm call).
- Resilience: scm flat error-envelope parsing; 401/403/429 `Retry-After`/503/timeout mapping; per-section degrade; mutation invalidation refreshes list/detail.
- Regression: FE-001..076 suites green; GAP path still operator-token; wms/scm/finance read paths unchanged (per-domain credential rule holds); existing FE-008 `features/scm-ops` `/scm` read section + route unchanged; new replenishment nav/route resolves.

## Out of Scope

- `TASK-SCM-BE-027` itself (the scm-side producer acknowledgment — separate scm project task; this task is blocked on it, does not perform it).
- demand-planning `policies` / `sku-supplier-map` **seed** routes (admin-seed surface, not the operator gate) — no console surface in this task.
- PO **submit/confirm/cancel** (procurement buyer mutations — the DRAFT PO is dispatched via the existing procurement flow, not this screen). Any change to FE-008's read-only scm-ops PO section beyond linking to it.
- Any change to scm `demand-planning-api.md` / `procurement-api.md` / `inventory-visibility-api.md` or a new scm producer endpoint (cross-reference only).
- Any GAP/IAM-side change; § 3 GAP-parity matrix not mutated; finance/erp/wms sections untouched.

# Acceptance Criteria

- [ ] **Prerequisite satisfied first**: `TASK-SCM-BE-027` (scm operator-action consumer reconciliation) merged before this task's code (spec-first; linked in Dependency Markers).
- [ ] Console renders an scm replenishment-suggestions operator screen server-side, tenant-scoped (`tenant_id=scm|*`), authenticated with the **IAM OIDC access token** (asserted bearer = the IAM-session cookie, never the operator token — reuses the § 2.4.5/§ 2.4.6 per-domain credential rule), consuming demand-planning suggestions read + approve/dismiss actions. scm producer specs unchanged.
- [ ] **Mutation discipline**: approve/dismiss gated by a confirm dialog; optional note/reason sent in the request **body**; **no** invented `Idempotency-Key`, **no** IAM `X-Operator-Reason` header (asserted absent — server-side idempotency by suggestion state is relied upon).
- [ ] **Operator-gate invariant surfaced**: approve success shows the materialised **DRAFT** `poId`/`poStatus` and makes explicit that submission is a separate Procurement step; the screen issues no PO submit/confirm/cancel call.
- [ ] **Idempotency/state handling**: idempotent re-approve handled as success (existing `poId`, no duplicate); `SKU_SUPPLIER_UNMAPPED`/`INVALID_SUGGESTION_STATE`/`SUGGESTION_NOT_FOUND`/`SUGGESTION_ALREADY_MATERIALIZED` mapped to actionable inline states.
- [ ] **Resilience (§ 2.5)**: scm flat error envelope parsed; 401 → forced IAM re-login; 403 `TENANT_FORBIDDEN` → inline; 429 `Retry-After` honoured (bounded backoff, no storm); 503/timeout → only this section degrades (shell intact); successful mutations invalidate list/detail.
- [ ] Tokens/PII never logged; § 2.4.6.1 + `console-web/architecture.md` module merged before/with code; scm specs unchanged by this task; ADR-MONO-012 D3 canonical form intact; § 3 matrix not mutated.
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + FE-001..076; no regression); axe WCAG AA; no bundle/perf regression beyond the FE-001 budget.
- [ ] Scope = `projects/platform-console/` only (scm cross-reference); no churn-clock effect; the ADR-MONO-027 loop's console operator gate is **complete**.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-027-wms-scm-replenishment-loop.md` § D2/D5 (the operator gate — approve → DRAFT PO; the surface this screen builds)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 Model B / § D6 (Phase 4 — "contract ext → Opus"; the governing console ADR)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5/§ 2.4.5.1/§ 2.4.6/§ 2.4.7.1 (per-domain credential rule + second-service-binding pattern reused) — this task adds § 2.4.6.1
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-008 `features/scm-ops` patterns reused)
- `projects/platform-console/tasks/done/TASK-PC-FE-008-console-scm-operations-section.md` (the scm read client + per-domain credential + flat-envelope + S5 + 429-backoff foundation reused)
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` (authoritative producer — suggestions read + approve/dismiss, idempotency, error codes; consumed unchanged)
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (the operator-action consumer acknowledgment SCM-BE-027 lands; auth chain + error envelope)
- `projects/scm-platform/tasks/ready/TASK-SCM-BE-027-platform-console-operator-action-consumer-reconciliation.md` (the spec-first prerequisite)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query read + **mutation** with invalidation, HttpOnly cookie auth, per-domain credential reuse, confirm-dialog mutation UX, rate-limit backoff), a11y, security review (non-GAP write trust boundary; idempotent-action handling; spec-first cross-project consumption discipline).

---

# Related Contracts

- **Prerequisite (scm project-internal, separate task — must land first)**: scm `gateway-public-routes.md` operator-**action** consumer acknowledgment (`TASK-SCM-BE-027`).
- **Changed (this task, console-side spec-first)**: `console-integration-contract.md` **new § 2.4.6.1** (scm demand-planning replenishment operator binding; reuses § 2.4.5/§ 2.4.6 credential rule; records actual mutation discipline — body-not-header reason, no invented idempotency key) + `console-web/architecture.md` (`features/scm-replenishment` module).
- **Consumed (unchanged, authoritative — scm-owned)**: `demand-planning-api.md` (suggestions list/detail, approve/dismiss).
- **Not touched**: scm `procurement-api.md` / `inventory-visibility-api.md`; GAP `admin-api.md`; § 3 GAP-parity matrix.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new `features/scm-replenishment` module (server-side demand-planning client, IAM-OIDC-token credential, read + approve/dismiss mutations) + `(console)/scm/replenishment` route (or scm-section tab) + `api/scm/demand-planning/**` proxy (read + 2 action routes) + in-console nav.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001..076 established). All demand-planning calls server-side; tokens/PII never to client JS or logs.
- ADR-MONO-013 Model B: the console renders the scm replenishment operator gate by calling demand-planning's **existing** gateway routes. Reuses the FE-008 per-domain credential rule (scm = IAM OIDC access token direct, not the operator-token exchange) — the **first scm mutation** rides the same credential as the reads.
- First scm operator-mutation: mutation UX (confirm dialog, body-carried reason, idempotency-aware invalidation) layered on FE-008's read foundation — but **no** privileged credential and **no** invented header scaffolding (the gate is server-side `tenant_id` validation + the producer's DRAFT-PO-only invariant).
- Single-domain section (not the Phase-7 `console-bff`).

---

# Implementation Notes

- **Blocked until SCM-BE-027 lands** — do not start code (or § 2.4.6.1) until the scm operator-action consumer acknowledgment is merged. Consuming an operator-**write** surface whose producer spec sanctions only **reads** is the exact spec-first anti-pattern this gate prevents (the FE-008 → SCM-BE-015 precedent, now for actions).
- Reuse FE-008's `features/scm-ops` client/auth/envelope/backoff primitives verbatim (share, do not fork); the net-new work is the demand-planning read+action client, the approve/dismiss mutation UX (confirm + body-carried reason + idempotency-aware state), and surfacing the DRAFT-PO-only invariant.
- **Do not** carry IAM § 2.4.1 mutation scaffolding (`Idempotency-Key`, `X-Operator-Reason` header) — `demand-planning-api.md` defines neither; the reason is an optional **body** field and idempotency is server-side by suggestion state. Inventing them is a defect (assert their absence).
- Recommend implementation model: **Opus** (contract extension § 2.4.6.1 + first scm mutation discipline + cross-project spec dependency). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`. Dispatcher independently re-verifies (IAM-OIDC bearer not operator token; no invented headers; DRAFT-PO invariant surfaced; no PO submit call) before any close.
- Branch name must not contain the `master` substring. Use e.g. `task/pc-fe-077-scm-replenishment-ops`.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate; Playwright/Testcontainers E2E is CI/manual.

---

# Edge Cases

- Operator's IAM token not scm-eligible (no `tenant_id=scm` and not SUPER_ADMIN `*`) → section blocks with an actionable "no scm-scoped access" state; demand-planning rejects cross-tenant (`403 TENANT_FORBIDDEN`) producer-side.
- Approve on a suggestion with no `sku_supplier_map` → `SKU_SUPPLIER_UNMAPPED` (422); inline "no supplier mapping; cannot reorder"; suggestion stays `SUGGESTED` (no optimistic transition).
- Re-approve / approve an already-`MATERIALIZED` suggestion → idempotent 200 returning the existing `poId` handled as success (no duplicate-PO assumption, no error toast); a hard 409 `SUGGESTION_ALREADY_MATERIALIZED` → benign "already materialized" notice showing the existing `poId`.
- Approve a `DISMISSED` suggestion (or dismiss a `MATERIALIZED` one) → `INVALID_SUGGESTION_STATE` (422); the action button should already be disabled by producer state, but the inline error is the backstop.
- 429 `RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) on read or action → bounded backoff + inline "rate-limited, retrying"; no auto-retry storm into the gateway.
- demand-planning 503/timeout → only this section degrades; the FE-008 scm read section + the console shell stay intact.
- 401 (IAM session expired) on a call → whole-session forced IAM re-login (no partial authed state), consistent with FE-002..076.
- Unknown/future suggestion `status` or `source` enum → generic label, never a parser throw; action buttons gate conservatively on recognised states only.

# Failure Scenarios

- Code started before SCM-BE-027 merges → spec-first violation; the Dependency Marker + AC gate it on the linked prerequisite.
- Client uses `getOperatorToken()` instead of `getAccessToken()` → wrong credential; test asserts IAM-OIDC bearer + absence of the operator-token path for scm (reuses FE-008).
- Invented `Idempotency-Key` / `X-Operator-Reason` header attached to approve/dismiss → defect (producer defines neither; reason is a body field); test asserts neither header is sent.
- Screen issues a PO submit/confirm to "complete" the reorder → violates the DRAFT-PO operator-gate invariant (ADR-MONO-027 D5); test asserts no procurement submit/confirm/cancel call.
- Idempotent re-approve treated as an error (duplicate-PO toast) → wrong; test asserts the idempotent 200 path is a success showing the existing `poId`.
- scm flat error envelope mis-parsed as wms's nested `{ error: { code } }` → mis-rendered errors; test asserts the scm flat-shape parser (per-domain envelope correctness, reused from FE-008).
- This section's failure blanks the whole console shell → violates § 2.5; test asserts section-only degrade.
- § 3 GAP-parity matrix mutated for scm → wrong (additive domain scope); AC forbids it.

---

# Test Requirements

- vitest (jsdom, mocked fetch): auth (IAM-OIDC bearer on read + action; operator-token path absent), mutation (approve/dismiss call the right routes, confirm-dialog-gated, optional reason in body, no invented headers), idempotency/state (idempotent re-approve = success with existing `poId`; `SKU_SUPPLIER_UNMAPPED`/`INVALID_SUGGESTION_STATE`/`SUGGESTION_NOT_FOUND` inline), DRAFT-PO invariant surfaced + no PO submit call, scm flat error-envelope + 401/403/429 `Retry-After`/503/timeout mapping + per-section degrade + mutation invalidation, components/hooks (list filters/pagination, detail, approve/dismiss UX, disabled-by-state buttons, degraded/permission placeholders), regression (FE-001..076 green; per-domain credential rule holds; existing FE-008 scm read section + route unchanged; new replenishment nav/route resolves).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA); no bundle/perf regression beyond the FE-001 budget.
- Spec internal-link lint clean; `validate-rules` no new inconsistency; ADR-MONO-012 D3 canonical form intact.

---

# Definition of Done

- [ ] `TASK-SCM-BE-027` (scm operator-action consumer acknowledgment) authored, linked, merged (gate for code)
- [ ] Console-side spec-first (`console-integration-contract.md` § 2.4.6.1 + `console-web/architecture.md` `features/scm-replenishment`) merged before/with code
- [ ] scm replenishment-suggestions operator screen rendered server-side, tenant-scoped, IAM-OIDC-token-authed; approve/dismiss with confirm dialog + body-carried reason; DRAFT-PO invariant surfaced; idempotency-aware
- [ ] § 2.5 resilience (scm flat envelope, per-section degrade, 401 whole-session re-login, bounded 429 backoff, mutation invalidation) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression
- [ ] Acceptance Criteria all satisfied; the ADR-MONO-027 loop's console operator gate **complete**
- [ ] Ready for review
