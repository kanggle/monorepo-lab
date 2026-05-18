# Task ID

TASK-PC-FE-005

# Title

console-web Phase 2 slice 4 — GAP operator overview (composed dashboards, read-only)

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

- **depends on**: **ADR-MONO-015 ACCEPTED** (TASK-MONO-112) — this is its § D5 step 1; `TASK-PC-FE-004` (operators slice + the `features/*` patterns) — merged (PR #577, main `865c1916`); the read-only client + resilience pattern from `TASK-PC-FE-003` (audit). Operator-auth bridge (FE-002a / ADR-MONO-014) complete.
- **part of**: ADR-MONO-013 § D6 **Phase 2** — **slice 4 of 5**: FE-002 accounts ✅ → FE-003 audit/security ✅ → FE-004 operators ✅ → **FE-005 operator overview** (this) → `FE-006` parity-verify (= ADR-MONO-013 Phase 3 admin-web-retirement gate).
- **prerequisite for**: `TASK-PC-FE-006`; satisfies the ADR-MONO-015-**refined** `console-integration-contract.md` § 3 `dashboards` parity line (composed operator overview, NOT Grafana — ADR-MONO-015 D2). FE-006 formally verifies.
- **spec-first**: the console-side `specs` reconciliation (§ 2.4.4 binding + architecture module) lands **before/with** the code (ADR-MONO-015 § D4; HARDSTOP-06). GAP `admin-api.md` is **unchanged** (no new producer — composition of existing reads only).
- **task-file**: the FE-005 task file is committed by `TASK-MONO-112` (authoring) — do not re-author. Do not touch the still-untracked FE-002/FE-002a task files (separate lifecycle chore).

# Goal

Build the console's **GAP operator overview** — Phase 2 slice 4, the realization of the ADR-MONO-015 decision (Model B: console "dashboards" = a composed operator overview from the **existing** read endpoints, **not** a Grafana embed). It is a server-side, tenant-scoped, **read-only** screen authenticated with the exchanged operator token (never the GAP OIDC token — FE-002a #569 invariant), composed by a **bounded fan-out** over endpoints already integrated by FE-002/003/004:

- accounts summary — `GET /api/admin/accounts` (total / page snapshot; reuse FE-002 client)
- recent audit + security activity — `GET /api/admin/audit` (recent rows; reuse FE-003 client; respect the producer meta-audit — one overview load = one bounded set, no aggressive auto-refetch)
- operators summary — `GET /api/admin/operators` (count / status mix; reuse FE-004 list client, permission-aware)

No new GAP producer endpoint, no Grafana, no observability-auth boundary (ADR-MONO-015 D1/D3). After this task the ADR-MONO-015-refined § 3 `dashboards` parity line is satisfiable; `FE-006` verifies the full refined checklist.

# Scope

## In Scope

### Spec-first (lands before/with code)
- `projects/platform-console/specs/contracts/console-integration-contract.md` — bind the § 3 `dashboards` line to a new **§ 2.4.4 "GAP operator overview (composed)"**: cross-reference to the **existing** `admin-api.md` read endpoints it composes (`GET /api/admin/accounts`, `GET /api/admin/audit`, `GET /api/admin/operators`); **explicitly no new producer**; restate the ADR-MONO-015 D2 parity-redefinition (overview, not Grafana); the operator-token / `X-Tenant-Id` / per-card-degrade / bounded-fan-out / meta-audit-respecting obligations. Update the § 3 `dashboards` line to "implemented by TASK-PC-FE-005 (`features/dashboards`, § 2.4.4); ADR-MONO-015-refined (composed overview, not Grafana); formal parity verification is FE-006" (matching the accounts/audit/operators line style).
- `projects/platform-console/specs/services/console-web/architecture.md` — add the `features/dashboards` (operator-overview) module to the Layered-by-Feature map (canonical Identity table + `### Service Type Composition` H3 untouched).
- GAP specs **unchanged** (composition of existing reads — cross-reference only).

### Code (`apps/console-web`, follows the spec)
- `src/features/dashboards/` (Layered-by-Feature, mirrors `features/audit` read pattern):
  - **server-side composition** (`api/`): an `getOperatorOverview()` that performs a **bounded fan-out** over the existing feature clients (reuse `features/accounts` / `features/audit` / `features/operators` server-side API functions — do NOT duplicate their hardened call sites). Each leg: operator token via `getOperatorToken()` (never `getAccessToken()`; absent → 401 → re-login), `X-Tenant-Id` from `getActiveTenant()` (block, never empty), AbortController timeout. **Per-source isolation**: one leg failing (401 propagates as auth; 403/503/timeout) must NOT fail the whole overview — collect per-card `{ok|degraded}` results. **No mutations** (read-only — no `X-Operator-Reason`/`Idempotency-Key`/confirm scaffolding; carrying FE-002/004 mutation patterns here is a defect, mirroring FE-003). Respect producer meta-audit: one overview load issues one bounded set of calls; no aggressive polling/auto-refetch.
  - **types** (`api/types.ts`): a zod overview view-model aggregating the per-card summaries + per-card status; tolerate a degraded card (no throw).
  - **hooks** (`hooks/`): TanStack Query read hook for the overview (single bounded query; sane staleTime; no tight refetch loop).
  - **components** (`components/`): an overview screen — summary cards (accounts / audit-security / operators) each rendering its data OR its own degraded/permission-denied placeholder (operators card respects `operator.manage`/SUPER_ADMIN — non-privileged → that card shows "not available to your role", not a crash). Quick links into the existing `/accounts` `/audit` `/operators` routes. WCAG AA (axe), keyboard-operable.
  - **route**: `src/app/(console)/dashboards/…` (or `/overview`) server component; add an in-console nav entry + make it the console landing/home target where appropriate. Do NOT change the catalog `gap.baseRoute` (stays `/accounts`).
  - **proxy** (`src/app/api/dashboards/route.ts` or reuse): same-origin GET proxy attaching the operator token server-side; reuse the FE-002 `_proxy` read error-mapping.
- Resilience (§ 2.5 + ADR-015 D3): 401 → forced re-login (no partial authed state); per-card 403 `PERMISSION_DENIED` / 503 `DOWNSTREAM_ERROR`/`CIRCUIT_OPEN` / timeout → **that card** degrades only (the overview + shell stay intact — never blank); the overview never hard-fails because one source is down.

### Tests (vitest, jsdom, mocked fetch — FE-001/002/002a/003/004 lane)
- Composition: operator-token bearer on every leg (assert it is the operator cookie, **not** the GAP access token); `X-Tenant-Id` present; **no `X-Operator-Reason`/`Idempotency-Key`** anywhere (read-only — assert absent); the fan-out is bounded (each leg has a timeout; no unbounded default) and issues one set per load (no auto-refetch storm).
- Per-source isolation: accounts leg 503 → accounts card degraded, audit + operators cards still render; operators leg 403 → operators card "not available to your role", others fine; 401 on any leg → whole-overview forced re-login (auth is not a per-card degrade).
- Components/hooks: overview renders all cards; degraded/permission placeholders; quick-links resolve to `/accounts` `/audit` `/operators`; empty/zero-state.
- Regression: existing FE-001/002a/002/003/004 suites green; `gap.baseRoute` (accounts) unchanged; the new nav/route resolves.

## Out of Scope

- `FE-006` parity-verify (slice 5/5 — its own task).
- Any GAP-side change / new producer endpoint (ADR-MONO-015 D1: compose existing reads only).
- Grafana / observability embed (ADR-MONO-015 D1-A explicitly rejected; D2: out of console-parity scope).
- Any mutation/write (read-only slice).
- Changing the catalog `gap.baseRoute`.
- `admin-web` retirement (Phase 3, gated on FE-006). `console-bff` (Phase 7 — the cross-domain aggregation tier; this is a single-domain composed overview, not the BFF). wms/scm/finance/erp (Phase 4–6).
- The FE-002/FE-002a untracked task-file lifecycle gap (separate chore).

# Acceptance Criteria

- [ ] Console renders a composed operator overview server-side, tenant-scoped, authed with the **operator token** (test asserts bearer = operator cookie, never the GAP access token), composed from the **existing** accounts/audit/operators read endpoints (no new GAP producer; GAP `admin-api.md` unchanged).
- [ ] **Read-only**: no `X-Operator-Reason`, no `Idempotency-Key`, no destructive/confirm dialogs anywhere (asserted by test).
- [ ] **Per-source isolation**: one source down (403/503/timeout) degrades only that card; the overview + shell stay intact; a 401 on any leg forces a clean whole-overview re-login (no partial authed state). The operators card respects `operator.manage`/SUPER_ADMIN (non-privileged → inline "not available", not a crash).
- [ ] Bounded fan-out: every leg has an explicit timeout (no unbounded default); one overview load = one bounded set of calls (no aggressive auto-refetch — producer meta-audit respected).
- [ ] Tokens and source PII are never logged (redacted; reuse the FE-002/003/004 logging discipline).
- [ ] Spec-first: `console-integration-contract.md` § 2.4.4 binding + § 3 `dashboards` line update (ADR-MONO-015-refined) + `console-web/architecture.md` `features/dashboards` module merged in the same PR before/with code; GAP `admin-api.md` unchanged; canonical form intact.
- [ ] `pnpm build` + `pnpm lint` (0) + `pnpm exec vitest run` all green (new + existing; no regression); axe WCAG AA on the new screen; no bundle/perf regression beyond the FE-001 budget.
- [ ] Scope = `projects/platform-console/` only; no churn-clock effect. ADR-MONO-013 Phase 2 slice-4 satisfied; ADR-MONO-015-refined § 3 `dashboards` line satisfiable; task references ADR-MONO-015 D1/D2/D3 + FE-002a trust boundary; `FE-006` unblocked.

# Related Specs

> Target project = `platform-console`. Target service = `console-web`. Governing service-type = `platform/service-types/frontend-app.md`. Follow `platform/entrypoint.md`.

- `docs/adr/ADR-MONO-015-platform-console-dashboards-model.md` (§ D1/D2/D3/D4/D5 — authoritative decision: composed overview, not Grafana)
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1 (Model B) / § D6 (Phase 2) / § 3 (refined parity checklist)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (operator token = the `/api/admin/**` credential)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.3/§ 2.4 (per-domain surface; tenant scope)/§ 2.5 (resilience)/§ 2.6 (operator token)/§ 3 (`dashboards` parity line, ADR-015-refined)
- `projects/platform-console/specs/services/console-web/architecture.md` (Layered-by-Feature; FE-001/002a/002/003/004 patterns)
- `projects/global-account-platform/specs/contracts/http/admin-api.md` §§ `GET /api/admin/accounts`, `GET /api/admin/audit`, `GET /api/admin/operators` (authoritative producers — composed, **unchanged**)
- `projects/platform-console/tasks/review/TASK-PC-FE-003-...` (read-only client + per-source-degrade pattern; task-shape parity) + FE-002/004 feature clients reused + FE-002a #569 trust boundary

# Related Skills

- `.claude/skills/` — frontend-engineer (Next.js App Router server components, TanStack Query, HttpOnly cookie auth, bounded fan-out/composition), a11y, security review (read aggregation across the operator boundary).

---

# Related Contracts

- **Changed (this task, spec-first)**: `console-integration-contract.md` § 2.4.4 + § 3 `dashboards` line; `console-web/architecture.md` (`features/dashboards` module).
- **Consumed (unchanged, authoritative)**: GAP `admin-api.md` `GET /api/admin/accounts` + `GET /api/admin/audit` + `GET /api/admin/operators` (composed via the existing FE-002/003/004 server clients).

---

# Target Service

- `platform-console` / `apps/console-web` (`frontend-app`) — new `features/dashboards` (operator-overview) module composing the existing accounts/audit/operators feature clients + `(console)/dashboards` route + proxy + shared resilience reuse.

---

# Architecture

- `console-web` follows `platform/service-types/frontend-app.md` + `console-web/architecture.md` Layered-by-Feature (FE-001..004 established). All GAP calls server-side with the operator token; tokens/PII never to client JS or logs.
- ADR-MONO-013 Model B + ADR-MONO-015 D1/D3: the console renders the overview itself by **composing existing** GAP read APIs (no new producer, no Grafana). Single-domain composition (not the Phase-7 `console-bff` cross-domain aggregation tier).
- ADR-MONO-014: `/api/admin/**` credential = exchanged operator token; tenant scope producer-resolved + the selected `X-Tenant-Id` sent on every leg; per-source failure isolation per ADR-015 D3 / contract § 2.5.

---

# Implementation Notes

- Spec-first hard gate (ADR-MONO-015 § D4 / HARDSTOP-06): reconcile `console-integration-contract.md` § 2.4.4 + § 3 line + `console-web/architecture.md` before/with code, same PR.
- **Reuse the existing feature clients** (`features/accounts`/`features/audit`/`features/operators` server-side API fns) for the fan-out legs — do NOT duplicate their hardened call sites or invent a new GAP client. The new code is the *composition* + per-card isolation + the overview view-model/UI.
- Read-only discipline (FE-003 precedent): no mutation scaffolding. Per-source isolation is the key design point — auth (401) is whole-overview re-login; everything else (403/503/timeout) is a per-card degrade.
- Bounded + meta-audit-respecting: explicit timeouts; one load = one bounded call set; no tight auto-refetch (the audit query is producer meta-audited).
- Recommend implementation model: **Opus** (read aggregation across the operator trust boundary + per-source isolation + the ADR-015 parity-refinement realization — security-sensitive though read-only). Dispatch `Agent(subagent_type="frontend-engineer", model="opus", ...)`.
- Branch name must not contain the `master` substring.
- Local Docker unavailable → vitest jsdom/mocked-fetch is the local gate (FE-001..004 precedent); Playwright/Testcontainers E2E is CI/manual.

---

# Edge Cases

- One source 503/timeout → only that card degrades; the other cards + the shell render (per-source isolation).
- Operator lacks `operator.manage` (operators card 403) → that card shows "not available to your role"; accounts/audit cards still render (the overview is not SUPER_ADMIN-only).
- Operator lacks `security.event.read` (audit card security signals) → the audit card degrades to the permitted subset / an explanation, not a crash (FE-003 intersection-permission behavior reused).
- 401 on any leg (operator token expired) → whole-overview forced re-login (auth is not a per-card degrade — no partial authed state).
- No active tenant selected → block with an actionable "select a tenant" state (never empty `X-Tenant-Id` on any leg).
- All sources down → the overview renders all-degraded with retry affordance; never a blank shell, never a hard crash.
- Producer meta-audit: the audit leg is meta-audited — a single overview load must not fan into repeated audit calls (no aggressive refetch / no N+1).

# Failure Scenarios

- GAP token leaks onto `/api/admin/**` on any leg (using `getAccessToken()`) → re-opens #569; AC + test assert the operator-cookie bearer on every leg.
- Mutation scaffolding carried over (reason/idempotency/confirm on a read overview) → wrong; AC + test assert none present.
- One source failure blanks the whole overview/shell → violates ADR-015 D3 / § 2.5; test asserts per-source isolation.
- A 401 silently degraded as a per-card error (stale partial authed state) → must be whole-overview re-login; test asserts it.
- Auto-refetch storm into the meta-audited audit endpoint → test asserts one bounded call set per load.
- New GAP producer endpoint invented (instead of composing existing reads) → violates ADR-015 D1; spec-first + AC forbid it (GAP `admin-api.md` unchanged).
- Grafana/observability embed sneaks in → ADR-015 D1-A explicitly rejected; out of scope.
- Spec not reconciled before code → HARDSTOP-06; AC binds § 2.4.4 + § 3 line + architecture into the same PR ahead of code.

---

# Test Requirements

- vitest (jsdom, mocked fetch): composition (operator-token bearer not GAP on every leg, `X-Tenant-Id`, **no reason/idempotency**, bounded timeouts, one-call-set-per-load), per-source isolation (accounts/audit/operators independent degrade; 401 = whole re-login; operators 403 = role placeholder), components/hooks (cards render, degraded/permission placeholders, quick-links, zero-state), regression (FE-001..004 suites green; `gap.baseRoute` unchanged; nav/route resolves).
- `pnpm build` + `pnpm lint` (0) green; axe (WCAG AA) on the new screen; no bundle/perf regression beyond FE-001 budget.
- Spec internal-link lint clean; `validate-rules` no new inconsistency.

---

# Definition of Done

- [ ] Spec-first reconciliation (`console-integration-contract.md` § 2.4.4 + § 3 `dashboards` line + `console-web/architecture.md` `features/dashboards`) merged before/with code
- [ ] Composed operator overview rendered server-side, tenant-scoped, operator-token-authed, **read-only**, per-source-isolated, bounded + meta-audit-respecting
- [ ] § 2.5 / ADR-015 D3 resilience (per-card degrade / whole-overview re-login on 401) implemented + tested
- [ ] `pnpm build`/`lint`/`vitest` green + axe AA; scope = platform-console only; no regression
- [ ] Acceptance Criteria all satisfied; ADR-MONO-013 Phase 2 slice-4 closed; ADR-MONO-015-refined § 3 `dashboards` satisfiable; `FE-006` unblocked
- [ ] Ready for review
