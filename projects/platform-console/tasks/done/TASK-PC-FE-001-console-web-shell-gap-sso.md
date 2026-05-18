# Task ID

TASK-PC-FE-001

# Title

console-web shell — GAP OIDC SSO + data-driven service catalog + tenant switcher

# Status

done

# Owner

frontend

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
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

# Goal

Turn the Phase-1 `console-web` skeleton into the real console shell (ADR-MONO-013 Phase 1→2 bridge):

- Operator authenticates **once** via GAP OIDC Authorization Code + PKCE (public client `platform-console-web`); tokens in HttpOnly cookies only.
- A **data-driven** service catalog rendered from the GAP product/tenant registry — `gap`/`wms`/`scm` available, `erp`/`finance` shown as `available:false` "coming soon" with zero code change when later enabled.
- A **tenant switcher** that scopes the session `tenant_id`; cross-tenant access is rejected.

This is the shell only. Federated domain operational screens (starting with GAP operator parity) are the next task(s); `admin-web` retirement (Phase 3) is gated on that parity, not on this task.

---

# Scope

## In Scope

- GAP OIDC Auth Code + PKCE login/logout via a server route (HttpOnly cookie session; no token in localStorage/sessionStorage), per `platform/service-types/frontend-app.md` Authentication.
- Typed API client under `src/shared/api/` consuming the GAP product/tenant registry (no direct `fetch()` in components).
- Data-driven catalog UI (tiles from registry; `available` flag drives enabled/"coming soon").
- Tenant switcher (sets session tenant context; passed as `X-Tenant-Id` / honored per integration contract).
- Resilience: registry call wrapped with timeout + graceful degraded state (catalog unavailable ≠ blank crash).
- a11y (WCAG 2.1 AA) + web-vitals reporting wiring per frontend-app.md.
- E2E happy-path: login → catalog visible → tenant switch.

## Out of Scope

- Domain operational screens (GAP operator parity = TASK-PC-FE-002; wms/scm sections = Phase 4).
- `console-bff` aggregation (Phase 7).
- `admin-web` removal (Phase 3, parity-gated, GAP-side).
- GAP-side OIDC client / registry implementation — that is `TASK-BE-296` (prerequisite, GAP project-internal).

---

# Acceptance Criteria

- [ ] Operator completes GAP Auth Code + PKCE login; session token is in an HttpOnly cookie (verified: not present in localStorage/sessionStorage).
- [ ] Catalog renders strictly from the registry response; toggling a product's `available` flag in the registry flips the tile with no `console-web` code change.
- [ ] `erp`/`finance` render as disabled "coming soon" when absent/`available:false`.
- [ ] Tenant switch changes the active `tenant_id`; a cross-tenant registry/API request is rejected (regression test).
- [ ] Registry timeout → degraded catalog state shown, app does not crash.
- [ ] a11y CI check ≥ 90; web-vitals reported; first-load JS within frontend-app.md budget.
- [ ] E2E (login → catalog → tenant switch) passes.

---

# Related Specs

> Follow `platform/entrypoint.md` Step 0 — read `projects/platform-console/PROJECT.md`, then `rules/common.md` + `rules/traits/{multi-tenant,integration-heavy,audit-heavy}.md` (if present). Unknown tags = Hard Stop per `CLAUDE.md`.

- `platform/service-types/frontend-app.md` (governing contract — auth, perf, a11y, observability)
- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/platform-console/PROJECT.md`
- `docs/adr/ADR-MONO-013-platform-console-foundation.md` § D1, D5, D6

# Related Skills

- `.claude/skills/frontend/...` — implementation-workflow, matched architecture skill (Feature-Sliced Design), api-client, auth-client, state-management, loading-error-handling, testing-frontend, bundling-perf, cross-cutting/observability-setup

---

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` (BFF/registry/OIDC integration contract)
- GAP-side: `TASK-BE-296` registers the OIDC public client + exposes the product/tenant registry surface (**prerequisite**)

---

# Target Service

- `console-web` (frontend-app)

---

# Architecture

Follow `projects/platform-console/specs/services/console-web/architecture.md` (Feature-Sliced Design; server components default; HttpOnly cookie auth; typed API client).

---

# Implementation Notes

- **Prerequisite gate**: do not start until `TASK-BE-296` (GAP OIDC client + registry surface) is merged — without it there is no client to authenticate against nor a registry to read.
- Model B: the console renders; it does not redirect to per-product UIs.
- Reuse GAP `admin-web` patterns where they fit (OIDC callback route, HttpOnly cookie handling) — `admin-web` is the parity reference and is retired only in Phase 3.

---

# Edge Cases

- Registry returns empty / partial product list → render only what is present; never hard-fail the shell.
- Operator has no tenant / single tenant → switcher hidden or no-op, not an error.
- PKCE/callback state mismatch → safe re-login, no token leak.
- `erp`/`finance` absent vs present-but-`available:false` → both render "coming soon" identically.

---

# Failure Scenarios

- GAP unreachable at login → clear error + retry, no partial authenticated state.
- Registry timeout/5xx → degraded catalog (cached/empty state), app shell still usable.
- Token refresh failure → forced re-login via server route (no client-side token juggling).
- Cross-tenant request slips through → must be rejected and covered by an isolation regression test (multi-tenant trait).

---

# Test Requirements

- Component tests (Vitest + Testing Library) for catalog + tenant switcher.
- Hook tests for the registry query hook.
- a11y (axe-core) for primitives.
- E2E happy-path: login → catalog → tenant switch.
- Multi-tenant isolation regression test.

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added & passing
- [ ] Contract (`console-integration-contract.md`) honored; updated first if it had to change
- [ ] Specs updated first if required
- [ ] a11y + perf budget CI green
- [ ] Ready for review
