# Task ID

TASK-PC-FE-057

# Title

console-web — wms **outbound operations** section: list outbound orders + drive pick → pack → ship on screen (ADR-MONO-022 §D7 operator leg)

# Status

ready

# Owner

frontend (Opus 4.8 analysis / Opus impl recommended — contract-binding + compound mutation orchestration + security-critical credential discipline)

# Task Tags

- api
- code
- test

---

# Dependency Markers

- **depends on**: **TASK-BE-343** (DONE, merged #1191) — `GET /api/v1/outbound/orders/{id}/picking-requests` (picking requests + planned lines). The Pick action reads it to build the §2.3 confirmation body.
- **depends on**: **TASK-PC-FE-007** (DONE) — the existing `features/wms-ops` (inventory/alerts) establishes the wms credential/tenant/envelope/resilience pattern this section reuses. The new section is a **second wms surface** (outbound-service via the gateway), NOT a change to wms-ops.
- **part of**: ADR-MONO-022 §D7 — the **on-screen operator leg** of the ecommerce↔wms fulfillment loop. The cross-project integration (193/195/196/197/198) + BE-343 are done; this makes the warehouse-side pick/pack/ship operable from a console menu so the end-to-end loop is visible on screen.
- **spec-first**: `console-integration-contract.md` **§2.4.5.1** (already drafted) + `console-web/architecture.md` `features/wms-outbound-ops` module land before/with code in the same PR (HARDSTOP-06; contract §5). wms `outbound-service-api.md` is **unchanged** (cross-reference only).
- **contract-ext → Opus**: a new non-IAM domain surface binding with compound (multi-call) mutations + optimistic-lock + idempotency discipline → implement with **Opus**. Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`.

# Goal

Add a console **wms outbound operations** section — a server-side, tenant-scoped screen over the wms `outbound-service` order lifecycle. It lists outbound orders, drills into an order (lines + saga state), and exposes **confirm-gated** lifecycle-advance actions — **Pick** (confirm-as-planned), **Pack** (create unit + seal), **Ship** — so an operator drives an outbound order `PICKING → PICKED → PACKING/PACKED → SHIPPED` from the console. This is the operator-visible leg of ADR-MONO-022 §D7: it advances the ecommerce-originated outbound order to `SHIPPED`, which flips the ecommerce order to `SHIPPED` via the existing return-leg events.

The section **reuses the §2.4.5 wms binding's credential/tenant/envelope/resilience rules verbatim** (domain-facing IAM OIDC token via `getDomainFacingToken()`, never the operator token; no `X-Tenant-Id`; nested `{error:{…}}` envelope; per-section degrade). The **new** work is: the outbound producer client (a second base URL + the outbound endpoints), the compound "confirm-as-planned" mutation orchestration, optimistic-lock-aware retries, and the list/detail/action UI.

# Scope

## In Scope

### Spec-first (lands before/with code, same PR)

- `projects/platform-console/specs/contracts/console-integration-contract.md` **§2.4.5.1** — **already drafted** (wms outbound operations surface). Verify the code matches it exactly.
- `projects/platform-console/specs/services/console-web/architecture.md` — add the `features/wms-outbound-ops` module + `(console)/wms/outbound` route + `api/wms/outbound/**` proxy to the Layered-by-Feature map. Note the second wms base URL (`WMS_OUTBOUND_BASE_URL`) in the Auth Flow / Integration Rules (same domain-facing credential as §2.4.5; distinct path prefix). Keep the ADR-MONO-012 D3 canonical Identity table + `### Service Type Composition` H3 intact.

### Code (`apps/console-web`)

- `src/shared/config/env.ts` — add **`WMS_OUTBOUND_BASE_URL`** (zod, default `http://wms.local/api/v1/outbound`), mirroring the existing `WMS_ADMIN_BASE_URL` entry (incl. the `process.env` wiring block).
- `src/features/wms-outbound-ops/` (mirrors the `features/wms-ops` shape):
  - `api/types.ts` — zod view-models: outbound order summary (list row), order detail (lines + status + sagaState + version), saga, picking-request + lines, packing-unit + ship responses. Tolerant of unknown enum/status values (generic row, never throw).
  - `api/outbound-api.ts` — server-only outbound client. **Credential = `getDomainFacingToken()` (asserted; NEVER `getOperatorToken()`)**, `Authorization: Bearer`, `X-Request-Id` generated, **no `X-Tenant-Id`**, AbortController hard timeout, **nested wms error-envelope parser** (reuse/share the §2.4.5 envelope+resilience helper if cleanly factorable — otherwise mirror it). Reads: `listOrders`, `getOrder`, `getSaga`, `listPickingRequests`. Mutations (each `Idempotency-Key: crypto.randomUUID()`, empty/typed body, **no `X-Operator-Reason`**): `confirmPick`, `createPackingUnit`, `sealPackingUnit`, `confirmShipping`.
  - `api/outbound-state.ts` — `getOutboundSectionState(eligible)` mirroring `getWmsSectionState` (eligibility/forbidden/degraded/401-redirect); seeds the initial order list server-side.
  - **Compound action orchestration** (server-side, in the proxy route handlers or an api fn): **Pick** = `listPickingRequests(orderId)` → map planned lines (`actualLocationId=locationId`, `qtyConfirmed=qtyToPick`) → `confirmPick(pickingRequestId, lines)`. **Pack** = `createPackingUnit(orderId, lines from order detail, qty=orderedQty)` → `sealPackingUnit(packingUnitId, version from create response)`. **Ship** = `confirmShipping(orderId, version from order detail)`. Each producer call gets its own stable idempotency key; on `409 CONFLICT` refetch + surface "state changed, retry" (no silent auto-retry).
  - `components/OutboundOpsScreen.tsx` — `'use client'`; orders table (status/sagaState/orderNo/lineCount/createdAt; status filter; pagination) + an order drill (lines + saga timeline) + the three **confirm-gated** action buttons, each enabled only for the valid current status/saga (Pick: `PICKING`+saga `RESERVED`; Pack: `PICKED`/`PACKING`; Ship: `PACKED`). Inline degraded/forbidden/empty states. `422 STATE_TRANSITION_INVALID` / `409` → inline actionable. WCAG AA (axe), keyboard-operable. `data-testid` on all interactive elements.
  - `hooks/use-outbound-ops.ts` — TanStack Query read hooks (sane staleTime, no tight refetch loop) + the pick/pack/ship mutation hooks (invalidate the orders query + the drilled order on success).
  - `index.ts` — barrel (screen, state fn, types).
  - `src/app/(console)/wms/outbound/page.tsx` — server component; eligibility from registry `productKey=wms` (reuse the §2.4.5 catalog eligibility); waterfall of registryDegraded/notEligible/forbidden/degraded states like `wms/page.tsx`; happy path renders `<OutboundOpsScreen … />`.
  - `src/app/api/wms/outbound/**` — same-origin route handlers attaching the domain-facing token server-side: `route.ts` (GET list), `[orderId]/route.ts` (GET detail+saga), `[orderId]/pick/route.ts`, `[orderId]/pack/route.ts`, `[orderId]/ship/route.ts` (POST compound actions). Mirror the existing `api/wms/_proxy.ts` error-mapping for the wms envelope.
  - `src/shared/ui/ConsoleSidebarNav.tsx` — add a nav item `{ href: '/wms/outbound', label: 'WMS 출고', testid: 'nav-wms-outbound' }` under the `도메인 운영` group (the existing `isActive` `startsWith` highlights `/wms` for `/wms/*`).

### Tests (vitest, jsdom, mocked fetch — the FE lane)

- **Credential divergence** (central): every outbound call's bearer is the **domain-facing IAM OIDC token** (`getDomainFacingToken`), **never** `getOperatorToken` (asserted absent — the inverse of the IAM-section assertion).
- **Tenant**: no `X-Tenant-Id`; non-eligible operator → blocked actionable state (no cross-tenant call).
- **Read vs mutation discipline**: reads carry no `Idempotency-Key`/no `X-Operator-Reason`/no body; each mutation carries `Idempotency-Key` (stable per confirmed action, fresh per attempt) and **no `X-Operator-Reason`** (asserted absent). Compound Pick maps planned `locationId`→`actualLocationId` and `qtyToPick`→`qtyConfirmed` (asserted — the console does not fabricate location/qty). Pack issues create-then-seal with the create-response `version`.
- **Resilience**: nested wms envelope parsed (not flat); 401 → whole-session re-login; 403 → inline; 503/timeout → per-section degrade (shell intact); `409 CONFLICT` → refetch + retry-prompt (no silent auto-retry); `422 STATE_TRANSITION_INVALID` → inline.
- **Action gating**: Pick disabled unless `PICKING`+`RESERVED`; Pack unless `PICKED`/`PACKING`; Ship unless `PACKED` (confirm-gated; no one-click advance).
- **Regression**: existing wms-ops + IAM suites green; `gap`/`wms` catalog routing unchanged; new `/wms/outbound` nav/route resolves; existing `/wms` page unaffected.
- `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` green; axe WCAG AA on the new screen.

## Out of Scope

- wms outbound manual order-create (§1.1), cancel (§1.4), TMS retry (§4.3) — deferred (not v1 console scope).
- Changes to `features/wms-ops` (inventory/alerts) or to any wms/IAM producer spec.
- The live ~43-container demo run (documented separately as the demo runbook; this task ships the production console feature + tests, CI-gated).
- finance/erp/scm console sections.

# Acceptance Criteria

- [ ] Console renders a wms **outbound operations** section (server-side, tenant-scoped) listing outbound orders + an order drill (lines + saga) + **confirm-gated** Pick/Pack/Ship actions that advance an order to `SHIPPED`, consuming the **existing** wms `outbound-service-api.md` surface (incl. §2.4 BE-343). wms specs unchanged.
- [ ] **Credential**: every outbound call's bearer is the domain-facing IAM OIDC token (`getDomainFacingToken`), **never** `getOperatorToken` (asserted). No `X-Tenant-Id`. (Inherits §2.4.5; restated as the central security assertion.)
- [ ] **Confirm-as-planned**: Pick builds the §2.3 body from `GET /orders/{id}/picking-requests` planned lines (`actualLocationId=locationId`, `qtyConfirmed=qtyToPick`) — the console fabricates no location/qty (asserted). Pack = create-unit-then-seal (own idempotency key each; seal uses the create-response version). Ship uses the order version.
- [ ] **Mutation discipline**: each POST/PATCH carries a stable-per-confirmed-action `Idempotency-Key` and **no `X-Operator-Reason`** (asserted absent); reads carry no mutation artifacts. `409 CONFLICT` → refetch + retry-prompt (no silent auto-retry); `422` → inline actionable.
- [ ] **Resilience (§2.5)**: nested wms envelope parsed; 401 → whole-session re-login; 403 → inline "not available to your role"; 503/timeout → only this section degrades (shell intact). Tokens/PII never logged.
- [ ] Spec-first: §2.4.5.1 (already drafted) + `console-web/architecture.md` `features/wms-outbound-ops` module merged with code; wms specs unchanged; ADR-MONO-012 D3 canonical form intact; §3 parity matrix not mutated.
- [ ] `pnpm build`/`lint`(0)/`vitest` green (new + existing, no regression); axe WCAG AA; scope = `projects/platform-console/` only.

# Related Specs

> Target project = `platform-console`. Target service = `console-web` (`frontend-app`). Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-022-*` §D7 (the fulfillment loop — this is its on-screen operator leg)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` §D1 (Model B) / §D6 (per-domain federation)
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.5 (wms credential/tenant/envelope/resilience — inherited) + **§2.4.5.1** (this surface) + §2.5 (resilience)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; the `features/wms-ops` pattern to mirror)
- `projects/wms-platform/specs/contracts/http/outbound-service-api.md` (authoritative producer — consumed unchanged: §1.2/1.3 reads, §2.3 pick, §2.4 picking-requests [BE-343], §3.1/3.2 pack, §4.1 ship, §5.1 saga)
- `projects/wms-platform/specs/services/gateway-service/*` (wms gateway: IAM RS256 JWT, `tenant_id=wms`, routes `/api/v1/outbound/**`)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query, HttpOnly cookie auth, per-domain credential selection, confirm-gated compound mutations, optimistic lock), a11y, security review (the credential discipline + idempotency are the review focus).

---

# Related Contracts

- **Changed (this task, spec-first)**: `console-integration-contract.md` **§2.4.5.1** (already drafted) + `console-web/architecture.md` (`features/wms-outbound-ops` module, second wms base URL).
- **Consumed (unchanged, wms-owned)**: `outbound-service-api.md` §1.2/§1.3/§2.3/§2.4/§3.1/§3.2/§4.1/§5.1.
- **Not touched**: IAM specs; §3 IAM-parity matrix; `features/wms-ops` and its §2.4.5.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new `features/wms-outbound-ops` module + `(console)/wms/outbound` route + `api/wms/outbound/**` proxy + nav item + `WMS_OUTBOUND_BASE_URL` env.

---

# Architecture

- `console-web` Layered-by-Feature; all outbound calls server-side; tokens/PII never to client JS or logs.
- ADR-MONO-013 Model B: the console renders the wms outbound operational screen by calling wms's existing gateway/outbound REST API. Second wms surface (after §2.4.5 admin/dashboard).
- **Per-domain credential selection (inherited)**: wms = domain-facing IAM OIDC token; the outbound surface uses the SAME credential as §2.4.5, a DIFFERENT base URL/path prefix (`/api/v1/outbound` vs `/api/v1/admin`).
- Compound mutations (Pick/Pack) are orchestrated **server-side** (route handler / api fn), each producer call idempotency-keyed; optimistic-lock 409 surfaced, never silently retried.

---

# Implementation Notes

- Reuse the `features/wms-ops` client envelope/resilience/logging helper where cleanly factorable (factor a shared `shared/api` wms helper, or mirror it) — do NOT duplicate the credential logic divergently. The credential is `getDomainFacingToken()`; grep the new client for `getOperatorToken` → must be ABSENT (dispatcher re-verifies before close).
- The §2.3 pick confirmation requires ALL order lines in one call (v1 no per-line/short-pick); build the lines from `listPickingRequests` `content[0].lines`. Pack `qty` = ordered qty from order detail.
- Packing has NO bulk "/packing/confirm" endpoint — it is `POST packing-units` then `PATCH packing-units/{id}` `seal:true` (two calls). Do not invent a one-call pack.
- Recommend model: **Opus**. Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`. Local Docker not needed — vitest jsdom/mocked-fetch is the gate (the live loop is the demo runbook, separate).
- Branch name must not contain `master`. Use e.g. `task/pc-fe-057-console-wms-outbound-ops`.

# Edge Cases

- Order `PICKING` but saga still `REQUESTED` (not yet `RESERVED`) → Pick disabled, saga state shown; `listPickingRequests` may return `{content:[]}` (handle gracefully — no crash).
- Operator's IAM token lacks `OUTBOUND_WRITE` (read-only) → reads render; action attempt → `403` inline "not available to your role" (and ideally actions disabled when role absent if discoverable).
- `409 CONFLICT` on seal/ship (someone else advanced it) → refetch, show new state, prompt retry.
- `422 STATE_TRANSITION_INVALID` (pack before pick-confirm) → inline; action gating should prevent it but the server is authoritative.
- Unknown/future status/sagaState enum → generic label, no parser throw.
- wms outbound section 503/timeout → only this section degrades; the `(console)` shell + other sections intact.
- 401 on an outbound call → whole-session IAM re-login (no partial authed state).

# Failure Scenarios

- Client uses `getOperatorToken()` → wms rejects (wrong issuer/type) + misapplies IAM auth model → AC/test assert the domain-facing token and the ABSENCE of the operator-token path.
- `X-Operator-Reason` carried to an outbound mutation (header drift from §2.4.1) → wms does not define it; test asserts absent.
- Pick fabricates a `locationId`/`qtyConfirmed` instead of using the planned picking-request lines → wrong/invalid pick → AC/test assert the planned-line mapping.
- Pack issued as a single call / seal omitted → order never reaches `PACKED` → ship 422 → test asserts create-then-seal.
- `409` silently auto-retried with a bumped version → lost-update hazard → must refetch + prompt; test asserts no silent retry.
- wms envelope parsed as flat `{code,message}` → mis-render/crash → test asserts nested parser.
- A section failure blanks the console shell → §2.5 per-section isolation violated → test asserts section-only degrade.
- Spec not reconciled with code (no §2.4.5.1 / no architecture module) → HARDSTOP-06; §2.4.5.1 already drafted, architecture module must land in the same PR.

---

# Test Requirements

- vitest (jsdom, mocked fetch): credential divergence (domain-facing token; operator-token absent), tenant (no `X-Tenant-Id`; non-eligible → blocked), confirm-as-planned mapping (planned `locationId`/`qtyToPick` → `actualLocationId`/`qtyConfirmed`; pack create-then-seal with create-version), mutation discipline (stable/fresh idempotency key; no `X-Operator-Reason`; reads clean), resilience (nested envelope; 401 re-login; 403 inline; 503/timeout per-section degrade; 409 refetch-retry; 422 inline), action gating (Pick/Pack/Ship status/saga gates; confirm-gated), regression (wms-ops + IAM suites green; catalog routing unchanged; new nav/route resolves).
- `pnpm build` + `pnpm lint` (0) green; axe WCAG AA on the new screen; no bundle/perf regression beyond the FE-001 budget.
- Spec internal-link lint clean; ADR-MONO-012 D3 canonical form intact.

---

# Definition of Done

- [ ] Spec-first (§2.4.5.1 already drafted + `console-web/architecture.md` `features/wms-outbound-ops` module) merged with code
- [ ] Outbound operations section renders server-side, tenant-scoped, domain-facing-token-authed; list + drill + confirm-gated Pick/Pack/Ship advancing to `SHIPPED`
- [ ] Confirm-as-planned (planned-line mapping; pack create-then-seal) + mutation discipline (idempotency, no reason header) + optimistic-lock 409 handling implemented + tested
- [ ] §2.5 resilience (nested envelope, per-section degrade, 401 whole-session re-login) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression
- [ ] Acceptance Criteria satisfied; ADR-MONO-022 §D7 on-screen operator leg delivered
- [ ] Ready for review
