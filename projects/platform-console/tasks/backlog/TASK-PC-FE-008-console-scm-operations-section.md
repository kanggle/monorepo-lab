# Task ID

TASK-PC-FE-008

# Title

console-web Phase 4 slice 2 — scm operations console section (read-only: procurement PO + inventory-visibility)

# Status

backlog

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

- **depends on**: **ADR-MONO-013 ACCEPTED** (TASK-MONO-108) § D6 Phase 4; `TASK-PC-FE-007` (wms) — establishes the **non-GAP per-domain client + auth-divergence + per-domain-credential** pattern this slice reuses (do not re-derive it). Must be merged first.
- **BLOCKED ON (cross-project spec-first prerequisite — why this task is in `backlog/`, not `ready/`)**: scm `gateway-public-routes.md` (status: live, 2026-05-11) explicitly states *"scm v1 = backend only … Human-user (PKCE / authorization_code) flow is deferred to v2 when a frontend ships"* and the primary auth shape is `client_credentials`. `platform-console` (Model B) **is** that frontend, calling scm's gateway with a **human operator's GAP OIDC token**. Although the gateway's written auth contract (RS256 GAP JWT, `tenant_id=scm|*`) is *technically* satisfiable by an operator token, **consuming a producer surface its own spec narrates as "v1 backend-only / human-flow deferred to v2" without the producer spec acknowledging the console consumer violates spec-first discipline** (CLAUDE.md "Specs win over tasks. If implementation requires spec or contract changes, update them first."). Required prerequisite = an **scm project-internal spec-first task** (e.g. `TASK-SCM-BE-0xx`, to be authored in `projects/scm-platform/tasks/ready/`) that reconciles `scm-platform/specs/contracts/http/gateway-public-routes.md` (+ `gateway-service/architecture.md` / `specs/integration/gap-integration.md` as needed) to record `platform-console` as a **sanctioned human/operator GAP-token read consumer** of the scm gateway read surface (mirroring how GAP's `TASK-BE-296` was the authored prerequisite for `FE-001`). `backlog → ready` move is allowed only once that scm-side task is authored and linked here (INDEX move rule: "cross-project prerequisite tasks (if any) are identified and linked").
- **part of**: ADR-MONO-013 § D6 **Phase 4** — **slice 2 of 2**: `FE-007 wms` → **FE-008 scm** (this). Completes Phase 4; Phase 5/6 finance/erp console sections inherit the proven non-GAP contract.
- **spec-first**: the console-side contract extension (**new § 2.4.6 scm binding** in `console-integration-contract.md` + `console-web/architecture.md` `features/scm-ops` module) lands before/with the code, **after** the scm-side prerequisite spec change. scm `procurement-api.md` / `inventory-visibility-api.md` are **unchanged** (cross-reference only — scm owns them).
- **contract-extension → Opus**: ADR-MONO-013 § D6 Phase 4 = "Sonnet; **contract ext → Opus**". This slice extends the contract (§ 2.4.6) and depends on a cross-project spec reconciliation → **Opus**.

# Goal

Build the console's **scm operations section** — ADR-MONO-013 Phase 4 slice 2, completing Phase 4 (wms + scm). It is a server-side, tenant-scoped, **read-only** section over scm's existing gateway read surface:

- **procurement** — purchase-order read: `GET /api/v1/procurement/po` (search), `GET /api/v1/procurement/po/{poId}` (detail). PO **write** actions (`/submit`, `/confirm`, `/cancel`) are buyer/business mutations, **not** an operator-parity surface — explicitly out of scope (read-only section).
- **inventory-visibility** — `GET /api/v1/inventory-visibility/snapshot` (cross-node / single-node), `/sku/{sku}` (per-SKU cross-node, Redis-cached, `X-Cache` header), `/staleness` (FRESH/STALE/UNREACHABLE per node), `/nodes` (node list). Every inventory-visibility response carries the producer's `meta.warning: "Not for procurement decisions (S5)"` envelope — the console **must surface that warning in the UI** (it is a deliberate scm trait constraint, not noise).

scm has **no `admin-service`** (deferred to scm v2 — `gateway-public-routes.md` v2 table). There is therefore **no operator-mutation parity** for scm at v1; the section is read-only. Auth follows the FE-007 non-GAP pattern: scm gateway = GAP RS256 JWT (ADR-001), `tenant_id=scm|*` from the JWT claim — the **GAP OIDC access token** (`getAccessToken()`), **not** the GAP operator-token-exchange (§ 2.6). This reuses the FE-007 per-domain-credential rule (no re-derivation).

# Scope

## In Scope

### Cross-project prerequisite (must land first — scm project-internal, separate task)

- An scm-side spec-first change recording `platform-console` as a sanctioned human/operator GAP-token **read** consumer of the scm gateway read surface, reconciling the `gateway-public-routes.md` "v1 backend-only / human-flow deferred to v2" narrative with the Model B reality. **Not implemented by this task** — this task is blocked until it is authored and linked (see Dependency Markers). This task only *consumes* the reconciled scm contract.

### Spec-first (console-side, lands before/with code, same PR — after the prerequisite)

- `projects/platform-console/specs/contracts/console-integration-contract.md` — add **§ 2.4.6 "scm operations surface (TASK-PC-FE-008 — cross-reference, not a redefinition)"**:
  - Authoritative producers = scm [`procurement-api.md`](../../../scm-platform/specs/contracts/http/procurement-api.md) (PO read: list + detail only) and [`inventory-visibility-api.md`](../../../scm-platform/specs/contracts/http/inventory-visibility-api.md) (snapshot/sku/staleness/nodes) — **unchanged, consumed read-only**.
  - **Read-only binding**: no mutation, no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs (carrying FE-007 alert-ack or GAP § 2.4.1 mutation scaffolding here is a defect). PO write actions are explicitly excluded (buyer mutations, not operator parity).
  - **Auth**: reuse the FE-007 per-domain-credential rule — scm = **GAP OIDC access token** (`getAccessToken()`), `tenant_id=scm|*` enforced producer-side from the JWT claim; never the § 2.6 operator-token-exchange. Reference (do not restate) the FE-007 § 2.4.5 per-domain credential rule + the scm-side prerequisite that sanctions the console consumer.
  - **S5 visibility-warning surfacing (scm trait constraint, normative)**: every inventory-visibility view MUST render the producer `meta.warning: "Not for procurement decisions (S5)"` prominently — the console must not strip, hide, or de-emphasise it. This is a contract obligation, not a UX nicety.
  - Resilience (§ 2.5): scm gateway error envelope = flat `{ code, message, timestamp }`; `401 UNAUTHORIZED` → forced GAP re-login; `403 TENANT_FORBIDDEN`/`FORBIDDEN` → inline "not available / not scoped"; `429 RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) → backoff + inline notice (do not hammer); `503 SERVICE_UNAVAILABLE` / timeout → only the scm section degrades (shell intact). Surface the inventory-visibility `X-Cache` header + `/staleness` node status as honest freshness signals.
  - § 3 GAP-parity matrix **not** mutated (scm is additive domain scope, not a GAP `admin-web` parity row).
- `projects/platform-console/specs/services/console-web/architecture.md` — add the `features/scm-ops` module + `(console)/scm` route + `api/scm/**` proxy to the Layered-by-Feature map (canonical Identity table + `### Service Type Composition` H3 untouched; ADR-MONO-012 D3 form preserved).
- scm specs **unchanged by this task** (cross-reference only; the reconciliation is the separate scm-side prerequisite). No GAP-side change.

### Code (`apps/console-web`, follows the spec)

- `src/features/scm-ops/` (Layered-by-Feature, read-only — mirrors FE-003/005 read discipline + FE-007 non-GAP credential):
  - `api/` — server-side scm gateway client. Credential = `getAccessToken()` (GAP OIDC cookie) — never `getOperatorToken()` (asserted, reusing the FE-007 rule). scm base URL from runtime env (`SCM_GATEWAY_BASE_URL` → `http://scm.local`, registry `baseRoute`-aligned). Read fns: PO list/detail; inventory-visibility snapshot/sku/staleness/nodes. AbortController hard timeout; scm flat error-envelope parser; 429 `Retry-After` honoured (bounded backoff, no storm).
  - `api/types.ts` — zod view-models incl. the `meta.warning` (S5) field as a **required, surfaced** element (not optional/discardable); tolerant of unknown node `status`/PO `status` enums (generic, no throw).
  - `hooks/` — TanStack Query read hooks (sane staleTime; respect `X-Cache`; no tight refetch — rate-limited gateway).
  - `components/` — read screens: PO list (filters, pagination) + PO detail; inventory-visibility snapshot table + per-SKU breakdown + node staleness panel — each inventory-visibility view renders the **S5 "Not for procurement decisions" warning** prominently. WCAG AA (axe), keyboard-operable.
  - `route` — `src/app/(console)/scm/…` server component; in-console nav; registry-driven (`productKey=scm` `baseRoute`; `available:false` → existing catalog "coming soon" path).
  - `proxy` — `src/app/api/scm/**` same-origin GET proxies attaching the GAP OIDC token server-side; scm flat error-envelope mapping; no mutation routes (read-only).
- Resilience (§ 2.5): scm down (503/429-storm/timeout) → only the scm section degrades; 401 → forced GAP re-login; never blank the shell.

### Tests (vitest, jsdom, mocked fetch — FE-001..007 lane)

- Auth: GAP-OIDC-token bearer on every scm call; operator-token path **absent** for scm (reuse the FE-007 assertion shape).
- Read-only: **no** mutation artifacts anywhere (no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs, no PO write calls) — asserted.
- S5: every inventory-visibility view test asserts the `meta.warning` is rendered and not stripped.
- scm flat error-envelope parsing; 401/403 `TENANT_FORBIDDEN`/429 `Retry-After`/503/timeout mapping; per-section degrade; `X-Cache` + `/staleness` freshness surfaced.
- Regression: FE-001..007 suites green; GAP path still operator-token, wms path still GAP-OIDC-token (per-domain credential rule holds for 3 domains now); `gap.baseRoute`/wms route unchanged; scm nav/route resolves.

## Out of Scope

- The scm-side spec reconciliation prerequisite itself (separate scm project-internal task — this task is blocked on it, does not perform it).
- PO **write** actions (`/po/{poId}/submit|confirm|cancel`) and procurement webhooks (supplier-ack/asn) — buyer/business mutations + machine ingress, not operator-parity; read-only section.
- scm `admin-service` / suppliers / demand / logistics / settlement (scm v2 deferred surfaces — `gateway-public-routes.md` v2 table).
- finance/erp console sections (Phase 5/6). `console-bff` cross-domain aggregation (Phase 7).
- Any change to scm `procurement-api.md` / `inventory-visibility-api.md` or a new scm producer endpoint (cross-reference only).
- Any GAP-side change; § 3 GAP-parity matrix (finalized by FE-006) not mutated.

# Acceptance Criteria

- [ ] **Prerequisite satisfied first**: the scm-side spec-first reconciliation task is authored, linked here, and merged before this task moves `backlog → ready` and before any code (spec-first; CLAUDE.md "Specs win over tasks").
- [ ] Console renders an scm operations section server-side, tenant-scoped (`tenant_id=scm|*`), **read-only**, authenticated with the **GAP OIDC access token** (test asserts bearer = the GAP-session cookie, never the operator token — reuses the FE-007 per-domain-credential rule), consuming the existing scm PO-read + inventory-visibility-read surface. scm producer specs unchanged.
- [ ] **Read-only discipline**: no `Idempotency-Key`, no `X-Operator-Reason`, no confirm dialogs, no PO write calls anywhere (asserted).
- [ ] **S5 obligation**: every inventory-visibility view renders the producer `meta.warning: "Not for procurement decisions (S5)"` prominently (asserted by test); the console never strips/hides it.
- [ ] **Resilience (§ 2.5)**: scm flat error envelope parsed; 401 → forced GAP re-login; 403 `TENANT_FORBIDDEN`/`FORBIDDEN` → inline; 429 `Retry-After` honoured (bounded backoff, no storm); 503/timeout → only the scm section degrades (shell intact); `X-Cache`/`/staleness` freshness surfaced honestly.
- [ ] Tokens/PII never logged; spec-first § 2.4.6 + `console-web/architecture.md` `features/scm-ops` merged before/with code; scm/GAP specs unchanged by this task; ADR-MONO-012 D3 canonical form intact; § 3 matrix not mutated.
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + FE-001..007; no regression — per-domain credential rule holds across GAP/wms/scm); axe WCAG AA; no bundle/perf regression beyond the FE-001 budget.
- [ ] Scope = `projects/platform-console/` only (scm cross-reference read-only); no churn-clock effect. ADR-MONO-013 Phase 4 **COMPLETE** (both slices); finance/erp (Phase 5/6) inherit the proven non-GAP contract.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D5 / § D6 (Phase 4 — "contract ext → Opus") / § 3.3 (the "zero retrofit" assumption — Phase 4 completes its verification across two non-GAP domains)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.3/§ 2.4/§ 2.4.5 (FE-007 per-domain credential rule — reused) /§ 2.5/§ 5 — this task adds § 2.4.6
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-001..007 patterns; per-domain credential rule)
- `projects/platform-console/tasks/ready/TASK-PC-FE-007-console-wms-operations-section.md` (the non-GAP client + auth-divergence + per-domain-credential pattern reused)
- `projects/scm-platform/specs/contracts/http/procurement-api.md` (authoritative producer — PO read only, consumed unchanged)
- `projects/scm-platform/specs/contracts/http/inventory-visibility-api.md` (authoritative producer — read only; the `meta.warning` S5 envelope)
- `projects/scm-platform/specs/contracts/http/gateway-public-routes.md` (scm gateway auth contract + the "v1 backend-only / human-flow v2-deferred" narrative the prerequisite reconciles)
- `projects/scm-platform/PROJECT.md` (scm domain/traits — S5 visibility constraint context)

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query, HttpOnly cookie auth, per-domain credential reuse, read-only tables, rate-limit backoff), a11y, security review (non-GAP read trust boundary; spec-first cross-project consumption discipline).

---

# Related Contracts

- **Prerequisite (scm project-internal, separate task — must land first)**: scm `gateway-public-routes.md` (+ `gateway-service/architecture.md` / `gap-integration.md` as needed) reconciliation recording `platform-console` as a sanctioned human/operator GAP-token read consumer.
- **Changed (this task, console-side spec-first)**: `console-integration-contract.md` **new § 2.4.6** (scm read-only binding; reuses § 2.4.5 per-domain credential rule; S5 surfacing obligation) + `console-web/architecture.md` (`features/scm-ops` module).
- **Consumed (unchanged, authoritative — scm-owned)**: scm `procurement-api.md` (PO list/detail), `inventory-visibility-api.md` (snapshot/sku/staleness/nodes).
- **Not touched**: GAP `admin-api.md`; § 3 GAP-parity matrix.

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new read-only `features/scm-ops` module (server-side scm gateway read client, GAP-OIDC-token credential) + `(console)/scm` route + `api/scm/**` read proxy + in-console nav + registry-driven resolution.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001..007 established). All scm calls server-side; tokens/PII never to client JS or logs.
- ADR-MONO-013 Model B: the console renders scm operational screens by calling scm's **existing** gateway read API. Second non-GAP federation; reuses the FE-007 per-domain-credential rule (GAP=operator-exchange; wms/scm=GAP OIDC access token).
- Read-only by domain reality (scm has no `admin-service` at v1 — no operator-mutation parity). S5 visibility-warning surfacing is a contract obligation.
- Single-domain section (not the Phase-7 `console-bff`).

---

# Implementation Notes

- **Blocked until the scm-side prerequisite lands** — do not start code (or even the console-side § 2.4.6) until the scm spec reconciliation is merged. Spec-first across projects (CLAUDE.md): consuming a producer whose own spec narrates it as v2-deferred-for-human-flow without the producer acknowledging the consumer is the exact anti-pattern this gating prevents (it is also the concrete instance of the ADR-MONO-013 § 3.3 "zero retrofit unverified" risk surfaced earlier — Phase 4 is where it is resolved honestly, not assumed).
- Reuse FE-007's non-GAP client + per-domain-credential rule verbatim (no re-derivation); the new work is the scm read client (flat envelope, 429 backoff, `X-Cache`/staleness), the S5-warning surfacing, and the read UI. No mutation scaffolding at all.
- Recommend implementation model: **Opus** (contract extension + cross-project spec dependency; ADR-MONO-013 § D6 Phase 4). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`. Dispatcher independently re-verifies (no operator-token path for scm; no mutation artifacts; S5 warning rendered) before any close.
- Branch name must not contain the `master` substring. Use e.g. `task/pc-fe-008-console-scm-ops`.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate; Playwright/Testcontainers E2E is CI/manual.

---

# Edge Cases

- Operator's GAP token not scm-eligible (no `tenant_id=scm` and not SUPER_ADMIN `*`) → section blocks with an actionable "no scm-scoped access" state; scm rejects cross-tenant (`403 TENANT_FORBIDDEN`) producer-side.
- inventory-visibility node `UNREACHABLE`/`STALE` → render the node status honestly (do not hide); the section still renders the reachable nodes; the S5 warning is always shown regardless.
- 429 `RATE_LIMIT_EXCEEDED` (`Retry-After: 1`) → bounded backoff + inline "rate-limited, retrying" notice; no auto-retry storm into the gateway.
- scm section 503/timeout → only the scm section degrades; GAP/wms sections + the console shell stay intact.
- 401 (GAP session expired) on an scm call → whole-session forced GAP re-login (no partial authed state), consistent with FE-002..007.
- Unknown/future PO `status` or node `status` enum → generic label, never a parser throw.
- Registry marks `scm` `available:false` → the data-driven catalog "coming soon" path handles it; scm route/nav must not hard-crash.

# Failure Scenarios

- scm code/contract started before the scm-side prerequisite merges → spec-first violation; the Dependency Marker + AC gate `backlog → ready` on the linked prerequisite.
- scm client uses `getOperatorToken()` instead of `getAccessToken()` → wrong credential + misapplied GAP-domain auth; test asserts GAP-OIDC-token bearer and absence of the operator-token path for scm (reuses FE-007).
- Mutation scaffolding (idempotency/reason/confirm) or a PO write call sneaks into the read-only section → defect; test asserts none present.
- The S5 `meta.warning` stripped/hidden/de-emphasised → violates the contract obligation; test asserts it is rendered prominently.
- scm flat error envelope mis-parsed as wms's nested `{ error: { code } }` → mis-rendered errors; test asserts the scm flat-shape parser (per-domain envelope correctness, like FE-007).
- An scm section failure blanks the whole console shell → violates § 2.5; test asserts scm-only degrade.
- § 3 GAP-parity matrix mutated for scm → wrong (additive domain scope, not a parity row); AC forbids it.

---

# Test Requirements

- vitest (jsdom, mocked fetch): auth (GAP-OIDC-token bearer; operator-token path absent for scm), read-only (no mutation artifacts / no PO write), S5 (`meta.warning` rendered on every inventory-visibility view), scm flat error-envelope + 401/403 `TENANT_FORBIDDEN`/429 `Retry-After`/503/timeout mapping + per-section degrade + `X-Cache`/`/staleness` surfaced, components/hooks (PO list/detail, visibility tables, staleness panel, degraded/permission placeholders), regression (FE-001..007 green; per-domain credential rule holds GAP/wms/scm; gap/wms routes unchanged; scm nav/route resolves).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA); no bundle/perf regression beyond the FE-001 budget.
- Spec internal-link lint clean; `validate-rules` no new inconsistency; ADR-MONO-012 D3 canonical form intact.

---

# Definition of Done

- [ ] scm-side spec-first prerequisite authored, linked, merged (gate for `backlog → ready`)
- [ ] Console-side spec-first reconciliation (`console-integration-contract.md` § 2.4.6 + `console-web/architecture.md` `features/scm-ops`) merged before/with code
- [ ] scm operations section rendered server-side, tenant-scoped, **read-only**, GAP-OIDC-token-authed; S5 warning surfaced; 429/staleness/X-Cache honest
- [ ] § 2.5 resilience (scm flat envelope, per-section degrade, 401 whole-session re-login, bounded 429 backoff) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 4 **COMPLETE** (FE-007 + FE-008); finance/erp inherit the proven non-GAP contract
- [ ] Ready for review
