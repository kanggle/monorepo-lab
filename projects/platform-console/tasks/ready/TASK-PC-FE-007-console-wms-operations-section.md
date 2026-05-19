# Task ID

TASK-PC-FE-007

# Title

console-web Phase 4 slice 1 — wms operations console section (read surface + alert-ack)

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

- **depends on**: **ADR-MONO-013 ACCEPTED** (TASK-MONO-108) — this is its § D6 **Phase 4** (wms/scm sections; gate = Phase 2 proven contract, satisfied: FE-002..006 done, parity matrix 16/16 verified). The `features/*` Layered-by-Feature patterns + resilience discipline from FE-002 (mutation/confirm) and FE-003/005 (read-only) — all merged (PR #582 closed).
- **part of**: ADR-MONO-013 § D6 **Phase 4** — **slice 1 of 2**: **FE-007 wms** (this) → `FE-008 scm` (backlog — blocked on an scm-side spec cross-reference prerequisite). Phase 4 proves the integration contract generalises beyond the GAP operator surface (the first **non-GAP** domain binding) before finance/erp console sections (Phase 5/6) are built to it.
- **prerequisite for**: `FE-008` (scm) reuses the non-GAP per-domain client + auth-divergence pattern this task establishes; the Phase-7 `console-bff` cross-domain aggregation tier composes this section's read surface.
- **spec-first**: the console-side contract extension (**new § 2.4.5 wms binding** in `console-integration-contract.md` + `console-web/architecture.md` `features/wms-ops` module) lands **before/with** the code in the same PR (HARDSTOP-06; contract § 5 Change Rule; ADR-MONO-013 D5). wms `admin-service-api.md` is **unchanged** (cross-reference only — wms owns it; the console consumes the existing surface).
- **contract-extension → Opus**: ADR-MONO-013 § D6 Phase 4 model = "Sonnet; **contract ext → Opus**". This slice **is** a contract extension (the first non-GAP § 2.4.x binding, surfacing a genuine **auth-model divergence**) → implement with **Opus**.

# Goal

Build the console's **wms operations section** — ADR-MONO-013 Phase 4 slice 1, the first **non-GAP** domain federated by `platform-console` (Model B: the console renders wms operational screens by calling wms's existing gateway/admin REST API). It is a server-side, tenant-scoped section over the wms `admin-service` **dashboard read-model** surface, plus the single operational mutation wms exposes on that surface (alert acknowledge).

This slice deliberately surfaces and binds the **auth-model divergence** that Phase 4 exists to validate (the "zero retrofit" assumption ADR-MONO-013 § 3.3 makes for finance/erp is *unverified* until a non-GAP domain is actually bound — this is that verification):

- The GAP operator surface (FE-002..006) authenticates with a **server-side RFC 8693 exchanged operator token** (`token_type=admin`, `iss=admin-service`, contract § 2.6); the **GAP OIDC access token must never reach GAP `/api/admin/**`** (the #569 trust-boundary invariant).
- **wms `admin-service-api.md` is the opposite**: it authenticates with `Authorization: Bearer <GAP OIDC access token>` directly (RS256 JWT issued by GAP per ADR-001, validated against GAP JWKS by the wms gateway + admin-service, `tenant_id=wms` enforced **from the JWT claim itself**). wms has **no** operator-token-exchange and **requires** the GAP OIDC token.

→ The console's wms client uses the **GAP OIDC access token** (`getAccessToken()`), **not** `getOperatorToken()`. The #569 invariant is **GAP-domain-specific** (it forbids the GAP token on **GAP's** `/api/admin/**` boundary because GAP requires the exchanged token there); it does **not** generalise to wms, whose gateway *requires* the GAP token. § 2.4.5 must state this divergence precisely so an implementer neither (a) wrongly carries the operator-token-exchange to wms, nor (b) wrongly treats "GAP token on an admin path" as a universal #569 violation. **Per-domain credential selection is now a first-class contract element.**

After this task the integration contract has its first non-GAP binding; `FE-008` (scm) and the finance/erp console sections (Phase 5/6) inherit the established per-domain auth-divergence pattern instead of retrofitting it.

# Scope

## In Scope

### Spec-first (lands before/with code, same PR)

- `projects/platform-console/specs/contracts/console-integration-contract.md` — add **§ 2.4.5 "wms operations surface (TASK-PC-FE-007 — cross-reference, not a redefinition)"**, mirroring the § 2.4.1–2.4.4 binding style:
  - Authoritative producer = wms [`admin-service-api.md`](../../../wms-platform/specs/contracts/http/admin-service-api.md) — **unchanged, consumed only**. The console consumes the **§ 1 Dashboard / Read-Model** reads (`GET /api/v1/admin/dashboard/inventory`, `/inventory/by-key`, `/throughput`, `/orders`, `/shipments`, `/asns`, `/asns/{id}/inspection`, `/adjustments`, `/alerts`, `/refs/{type}`, plus `GET /api/v1/admin/operations/projection-status`) and the one operational mutation on that surface (`POST /api/v1/admin/dashboard/alerts/{alertId}/acknowledge`).
  - **Auth-model divergence (the key correctness element — make it explicit and normative)**: wms credential = the **GAP OIDC access token** (`getAccessToken()`, the existing GAP-session HttpOnly cookie from FE-001), `Authorization: Bearer <token>`, RS256/ADR-001, `tenant_id=wms` enforced producer-side from the JWT claim. **Not** the § 2.6 exchanged operator token. State that the #569 invariant is GAP-domain-scoped and does **not** extend to wms; introduce a normative **"per-domain credential selection"** rule (each § 2.4.x binding declares which credential it uses; GAP=exchanged operator token, wms=GAP OIDC access token; an implementer must not blanket-apply one domain's auth to another).
  - **Tenant model divergence**: wms resolves tenant from the JWT `tenant_id` claim (`=wms`), not an `X-Tenant-Id` header and not a producer-side `admin_operators.tenant_id` lookup. Record how the console presents a wms session: the data-driven registry (§ 2.2) `tenants[]` for `productKey=wms` drives which tenant the operator may select; the console blocks the section with an actionable "no wms-scoped access" state when the operator's GAP token is not wms-eligible (no cross-tenant call ever fabricated). wms rejects cross-tenant producer-side (never weakened here). Send wms's required `X-Request-Id` (gateway echoes/generates); `X-Actor-Id` is set by the wms gateway from the JWT — the console does not forge it.
  - **Mutation discipline (alert-ack only)**: `POST .../alerts/{alertId}/acknowledge` requires `Idempotency-Key` (UUID; producer scope `(Idempotency-Key, method, path)`, TTL 24h) and `WMS_OPERATOR`+ role; empty body. It is reason-free (wms does **not** define `X-Operator-Reason` on this surface — do **not** carry GAP's § 2.4.1 `X-Operator-Reason` over; carrying it is a header-matrix-drift defect) but **confirm-gated** in the UI. All § 1 dashboard endpoints are pure reads — assert **no** mutation artifacts on them.
  - Resilience (§ 2.5): wms error envelope is `{ error: { code, message, timestamp, … } }` (note: nested `error` object — distinct from GAP's flat `{ code, message, timestamp }`; the wms client must parse the wms shape, not assume GAP's). `401`/`UNAUTHORIZED` → forced re-login (GAP session); `403 FORBIDDEN`/role-insufficient → inline "not available to your role"; `503 SERVICE_UNAVAILABLE` / timeout → only the wms section degrades (console shell intact); `404`/`VALIDATION_ERROR`/`STATE_TRANSITION_INVALID` (e.g. alert already acknowledged) / `409 DUPLICATE_REQUEST` → inline actionable (no crash). Read-model lag: surface `X-Read-Model-Lag-Seconds` (when present) as a non-blocking "data may lag" hint (eventual-consistency honesty).
  - Update the § 3 matrix area / a § 2.4.5 closing note: this is a **domain section** (not a GAP `admin-web` parity row — § 3 is the GAP-parity matrix, finalized by FE-006 and **not** mutated here; wms is additive scope, not a parity-gate row).
- `projects/platform-console/specs/services/console-web/architecture.md` — add the `features/wms-ops` module + `(console)/wms` route + `api/wms/**` proxy to the Layered-by-Feature map (the canonical Identity table + `### Service Type Composition` H3 untouched; ADR-MONO-012 D3 canonical form preserved). Record the per-domain credential rule in § Auth Flow / § Integration Rules (wms = GAP OIDC token, distinct from the GAP-section operator token).
- wms specs **unchanged** (cross-reference only; wms owns `admin-service-api.md`). No GAP-side change.

### Code (`apps/console-web`, follows the spec)

- `src/features/wms-ops/` (Layered-by-Feature, mirrors the FE-002 read+single-mutation shape):
  - `api/` — server-side wms `admin-service` client. Credential = `getAccessToken()` (GAP OIDC cookie) — **never** `getOperatorToken()` (asserted). wms base URL from runtime env (`WMS_ADMIN_BASE_URL` → `http://wms.local/api/v1/admin`, registry `baseRoute`-aligned). Attach `Authorization: Bearer <GAP token>`, `X-Request-Id` (generated), `Content-Type` on body. Read fns: inventory snapshot (+by-key), throughput, orders, shipments, asns (+inspection), adjustments, alerts, refs, projection-status. One mutation fn: `acknowledgeAlert(alertId)` with `Idempotency-Key: crypto.randomUUID()` (stable across one confirmed action, fresh per new attempt), empty body. AbortController hard timeout (no unbounded default); wms error-envelope parser (`{ error: { code … } }`).
  - `api/types.ts` — zod view-models for each read shape + the alert row; tolerant of unknown enum/`source` values (generic row, never throw).
  - `hooks/` — TanStack Query read hooks (sane staleTime; no tight auto-refetch loop — projection lag is surfaced, not polled-around) + the alert-ack mutation hook (invalidate the alerts list on success).
  - `components/` — operations screen: inventory snapshot table (warehouse/sku/lot/low-stock filters, pagination), throughput summary, orders/shipments lists, ASN list + inspection drill, adjustments audit (append-only — read-only, no edit affordance), alerts list with a **confirm-gated** acknowledge action (`WMS_OPERATOR`+; confirm dialog; idempotency key per confirmed action), refs lookups for filter dropdowns. Read-model-lag hint banner when `X-Read-Model-Lag-Seconds` present. WCAG AA (axe), keyboard-operable.
  - `route` — `src/app/(console)/wms/…` server component; in-console nav entry; resolves from the registry `productKey=wms` `baseRoute` (data-driven — no hardcoded gating; `available:false` → the existing catalog "coming soon" path already handles it).
  - `proxy` — `src/app/api/wms/**` same-origin route handlers attaching the **GAP OIDC token** server-side (mirrors FE-002 `_proxy` error-mapping but for the wms envelope + GAP-token credential).
- Resilience (§ 2.5): one wms source down (503/timeout) → only the wms section degrades, shell intact; 401 → forced GAP re-login (no partial authed state); 403/role → inline "not available to your role"; never blank the console shell.

### Tests (vitest, jsdom, mocked fetch — FE-001..006 lane)

- Auth-divergence (the central assertion): every wms call's bearer is the **GAP OIDC access token** (the GAP-session cookie), **never** the exchanged operator token; assert the operator-token path is *not* used for wms (the inverse of the FE-002..006 assertion — pins the per-domain credential rule and prevents a future refactor from blanket-applying one auth to all domains).
- Tenant: `tenant_id=wms` path; operator without wms-eligible tenant → blocked actionable state (no cross-tenant call fabricated); cross-tenant rejection handled inline.
- Read endpoints: no `Idempotency-Key`, no `X-Operator-Reason`, no mutation artifacts (asserted). Alert-ack: `Idempotency-Key` present + stable across one confirmed action + fresh per new attempt; **no** `X-Operator-Reason` (wms surface does not define it — assert absent); confirm-gated (no one-click ack); `STATE_TRANSITION_INVALID` (already acknowledged) / `409 DUPLICATE_REQUEST` handled inline.
- wms error-envelope parsing (`{ error: { code } }` — not GAP's flat shape); 401/403/404/422/503/timeout mapping; per-section degrade (wms down ≠ shell down); read-model-lag hint surfaced.
- Regression: existing FE-001/002a/002/003/004/005/006 suites green; GAP section still uses the **operator** token (unchanged — the divergence is additive, not a regression of the GAP path); `gap.baseRoute` unchanged; new wms nav/route resolves.

## Out of Scope

- `FE-008` scm section (slice 2/2 — its own task; backlog, blocked on the scm-side spec cross-reference prerequisite).
- finance/erp console sections (ADR-MONO-013 Phase 5/6 — governed by ADR-MONO-008 / ADR-MONO-016; not this task).
- `console-bff` cross-domain aggregation (Phase 7 — this is a single-domain section, not the BFF).
- wms **User / Role / Assignment / Settings write** admin surface (`admin-service-api.md` §§ 2–5, `WMS_ADMIN`+ heavy writes): out of v1 console scope — console v1 wms = operational **read** + the single `alerts/{id}/acknowledge` operational mutation. (A later slice may add the write-admin surface; explicitly deferred, not silently dropped.)
- Any change to wms specs / a new wms producer endpoint (cross-reference only; wms owns `admin-service-api.md`).
- Any GAP-side change; the GAP operator surface and its operator-token-exchange are untouched (the wms credential divergence is additive).
- `admin-web` retirement (Phase 3 — done, TASK-BE-299; out of scope here).

# Acceptance Criteria

- [ ] Console renders a wms operations section server-side, tenant-scoped (`tenant_id=wms`), authenticated with the **GAP OIDC access token** (test asserts bearer = the GAP-session cookie, **never** the exchanged operator token), consuming the **existing** wms `admin-service-api.md` § 1 read surface + the single `alerts/{id}/acknowledge` mutation. wms `admin-service-api.md` unchanged.
- [ ] **Auth-model divergence bound in spec**: `console-integration-contract.md` § 2.4.5 states the per-domain credential rule (GAP = exchanged operator token / § 2.6 / #569; wms = GAP OIDC access token), explicitly scopes the #569 invariant to the GAP domain, and forbids blanket-applying one domain's auth model to another. Tenant-model divergence (JWT claim vs `X-Tenant-Id` vs producer-resolved) recorded.
- [ ] **Read vs mutation discipline**: all § 1 dashboard reads carry no mutation artifacts (asserted); `alerts/{id}/acknowledge` carries `Idempotency-Key` (stable per confirmed action, fresh per attempt) and is confirm-gated, with **no** `X-Operator-Reason` (wms surface does not define it — carrying GAP's § 2.4.1 reason header is a drift defect, asserted absent).
- [ ] **Resilience (§ 2.5)**: wms error envelope (`{ error: { code … } }`) parsed correctly (not GAP's flat shape); 401 → forced GAP re-login (no partial authed state); 403/role → inline "not available"; 503/timeout → only the wms section degrades (shell intact, never blank); 404/422/`STATE_TRANSITION_INVALID`/`409` → inline actionable; `X-Read-Model-Lag-Seconds` surfaced as a non-blocking eventual-consistency hint.
- [ ] GAP/wms tokens and any PII are never logged (redacted; reuse the FE-002..005 logging discipline).
- [ ] Spec-first: § 2.4.5 binding + `console-web/architecture.md` `features/wms-ops` module merged before/with code in the same PR; wms + GAP specs unchanged; ADR-MONO-012 D3 canonical form intact; § 3 GAP-parity matrix **not** mutated (wms is additive scope, not a parity row).
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + existing FE-001..006; no regression — GAP path still uses the operator token); axe WCAG AA on the new screen; no bundle/perf regression beyond the FE-001 budget (`(console)/*` ≤ 250 KB gz).
- [ ] Scope = `projects/platform-console/` only (cross-reference to wms specs is read-only; no wms/GAP file changed); no churn-clock effect. ADR-MONO-013 Phase 4 slice-1 satisfied; first non-GAP contract binding established; `FE-008` pattern unblocked.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D5 (integration contract skeleton) / § D6 (Phase 4 — wms/scm, "contract ext → Opus") / § 3.3 (the "zero retrofit" assumption this slice begins to verify)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (GAP-domain operator-token exchange — the contrast point; wms does **not** use it)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.1/§ 2.3/§ 2.4 (per-domain surface; tenant scope)/§ 2.5 (resilience)/§ 2.6 (operator token — GAP-only)/§ 5 (Change Rule) — this task adds § 2.4.5
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-001..006 patterns; Auth Flow — extended with the per-domain credential rule)
- `projects/wms-platform/specs/contracts/http/admin-service-api.md` (authoritative producer — **consumed unchanged**: § 1 read-model + `alerts/{id}/acknowledge`; auth = GAP OIDC token, `tenant_id=wms`; wms error envelope; idempotency semantics)
- `projects/wms-platform/specs/services/gateway-service/public-routes.md` + `specs/integration/gap-integration.md` (wms gateway auth contract: GAP RS256 JWT, `tenant_id=wms`)
- `projects/wms-platform/PROJECT.md` (wms domain/traits — context for the consumed surface)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query, HttpOnly cookie auth, **per-domain credential selection**, read tables + single confirm-gated mutation), a11y, security review (a non-GAP domain trust boundary — the auth-divergence is the security-critical review focus).

---

# Related Contracts

- **Changed (this task, spec-first)**: `console-integration-contract.md` **new § 2.4.5** (wms binding + per-domain credential rule + tenant-model divergence) + `console-web/architecture.md` (`features/wms-ops` module, Auth Flow per-domain credential note).
- **Consumed (unchanged, authoritative — wms-owned)**: wms `admin-service-api.md` § 1 Dashboard/Read-Model reads + `POST /api/v1/admin/dashboard/alerts/{alertId}/acknowledge` + `GET /api/v1/admin/operations/projection-status`.
- **Not touched**: GAP `admin-api.md` (the GAP operator surface + § 2.6 exchange are unchanged; the wms credential divergence is additive). § 3 GAP-parity matrix (finalized by FE-006) not mutated.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new `features/wms-ops` module (server-side wms `admin-service` read client + single alert-ack mutation, GAP-OIDC-token credential) + `(console)/wms` route + `api/wms/**` proxy + in-console nav + registry-driven resolution.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001..006 established). All wms calls server-side; tokens/PII never to client JS or logs.
- ADR-MONO-013 Model B: the console renders wms operational screens by calling wms's **existing** gateway/admin REST API. First non-GAP federation.
- **Per-domain credential selection** (the architectural addition): GAP `/api/admin/**` = exchanged operator token (§ 2.6, #569 invariant — GAP-domain-scoped); wms `/api/v1/admin/**` = GAP OIDC access token (RS256, ADR-001, `tenant_id=wms` claim). The console's `shared/api`/feature client must select the credential per the domain binding — this is now an explicit contract element (§ 2.4.5), not an implementation detail.
- Single-domain section (not the Phase-7 `console-bff` cross-domain aggregation tier).

---

# Implementation Notes

- Spec-first hard gate (HARDSTOP-06 / contract § 5): reconcile `console-integration-contract.md` § 2.4.5 + `console-web/architecture.md` before/with code, same PR. wms `admin-service-api.md` is the producer authority — cross-reference, never redefine.
- **The auth-model divergence is the crux** — do NOT carry the GAP operator-token-exchange (§ 2.6) or the GAP `X-Operator-Reason` header to wms. wms = GAP OIDC access token (`getAccessToken()`), `Idempotency-Key`-only mutation on alert-ack, wms-shaped error envelope. The #569 invariant is GAP-domain-scoped (it forbids the GAP token on **GAP's** admin boundary specifically); wms's gateway *requires* the GAP token — these are not in conflict, they are different per-domain bindings. Make the rule explicit in § 2.4.5 so finance/erp (Phase 5/6) inherit a stated pattern, not a guess.
- Reuse the FE-002 read+mutation feature shape and the FE-002..005 logging/resilience discipline; the **new** work is the non-GAP client (credential + envelope), the per-domain credential contract rule, and the wms read/ack UI.
- Recommend implementation model: **Opus** (ADR-MONO-013 § D6 Phase 4 = "contract ext → Opus"; this is the first non-GAP binding and surfaces the auth-divergence — interpretive contract judgement, security-sensitive). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`. Dispatcher independently re-verifies the auth-divergence assertions (grep the wms client for `getOperatorToken` → must be absent; `getAccessToken` → present) before any close — agent report not trusted (CLAUDE.md dispatcher re-verification rule).
- Branch name must not contain the `master` substring (sandbox `--force` regex). Use e.g. `task/pc-fe-007-console-wms-ops`.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate (FE-001..006 precedent); Playwright/Testcontainers E2E is CI/manual.

---

# Edge Cases

- Operator's GAP token not wms-eligible (no `tenant_id=wms` and not SUPER_ADMIN `*`) → the section blocks with an actionable "no wms-scoped access" state; no cross-tenant call fabricated; wms rejects cross-tenant producer-side anyway.
- Alert already acknowledged → `STATE_TRANSITION_INVALID` (422) inline, no crash; the row reflects acknowledged state on refetch.
- Same alert acknowledged twice (double-click / retry) → idempotency key stable across one confirmed action → producer replays cached response; a *new* confirmed attempt gets a fresh key; `409 DUPLICATE_REQUEST` (same key, different body — should not happen for empty-body ack, but) handled inline.
- Read-model lag (`X-Read-Model-Lag-Seconds` > 5) → a non-blocking "data may lag ~Ns" hint; the section still renders (eventual-consistency honesty, not an error).
- wms section 503/timeout → only the wms card/section degrades; GAP/other sections + the console shell stay intact.
- 401 (GAP session expired) on a wms call → whole-session forced GAP re-login (not a per-section degrade — no partial authed state), consistent with the FE-002..005 401 discipline.
- Unknown/future enum (e.g. a new `alertType`, `bucket`, ref `{type}`) → generic row/label, never a parser throw.
- Registry marks `wms` `available:false` → the existing data-driven catalog "coming soon" path handles it; the wms route/nav must not hard-crash when unavailable.

# Failure Scenarios

- wms client uses `getOperatorToken()` (the GAP exchanged token) instead of `getAccessToken()` → wms rejects it (wrong issuer/type) AND it misapplies the GAP-domain auth model → AC + test assert the GAP-OIDC-token bearer and the **absence** of the operator-token path for wms.
- GAP `X-Operator-Reason` header carried over to the wms alert-ack (header-matrix drift from § 2.4.1) → wms does not define it; test asserts it is absent on the wms mutation.
- wms error envelope parsed as GAP's flat `{ code, message }` (it is nested `{ error: { code … } }`) → mis-rendered errors / crash; test asserts the wms-shape parser.
- A wms section failure blanks the whole console shell → violates § 2.5 per-section isolation; test asserts wms-only degrade.
- A 401 on a wms call silently degraded as a per-section error (stale partial authed state) → must be whole-session GAP re-login; test asserts it.
- Spec not reconciled before code (no § 2.4.5 / no architecture module) → HARDSTOP-06; AC binds the contract extension + architecture into the same PR ahead of code.
- The § 3 GAP-parity matrix mutated to add a wms row → wrong; § 3 is the GAP `admin-web` parity gate finalized by FE-006; wms is additive domain scope, not a parity row. AC forbids mutating § 3.
- wms write-admin (`admin-service-api.md` §§ 2–5) silently pulled into scope → out of v1; explicitly deferred, not dropped — keep the slice to read + alert-ack.

---

# Test Requirements

- vitest (jsdom, mocked fetch): auth-divergence (GAP-OIDC-token bearer on every wms call, operator-token path **absent** for wms — the inverse of FE-002..006), tenant (`tenant_id=wms`; non-eligible → blocked, no cross-tenant call), read-vs-mutation discipline (reads = no mutation artifacts; alert-ack = `Idempotency-Key` stable/fresh + confirm-gated + **no** `X-Operator-Reason`), wms error-envelope parsing + 401/403/404/422/503/timeout mapping + per-section degrade + read-model-lag hint, components/hooks (read tables/filters/pagination, confirm-gated ack, degraded/permission placeholders), regression (FE-001..006 suites green incl. GAP still operator-token; `gap.baseRoute` unchanged; wms nav/route resolves).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA) on the new screen; no bundle/perf regression beyond the FE-001 budget.
- Spec internal-link lint clean; `validate-rules` no new inconsistency; ADR-MONO-012 D3 canonical form (`console-web/architecture.md` Identity table + `### Service Type Composition` H3) intact.

---

# Definition of Done

- [ ] Spec-first reconciliation (`console-integration-contract.md` § 2.4.5 wms binding + per-domain credential rule + tenant-model divergence + `console-web/architecture.md` `features/wms-ops` module & Auth Flow note) merged before/with code
- [ ] wms operations section rendered server-side, tenant-scoped, **GAP-OIDC-token**-authed (never the operator token), over the existing wms read surface + confirm-gated alert-ack (idempotency-only, no reason header)
- [ ] § 2.5 resilience (wms-shape envelope, per-section degrade, 401 whole-session re-login, read-model-lag hint) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression (GAP path still operator-token)
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 4 slice-1 closed; first non-GAP contract binding established; § 3 GAP-parity matrix untouched
- [ ] Ready for review
