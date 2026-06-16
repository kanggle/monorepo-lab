# TASK-PC-FE-113 — Promote the throwaway `verify-*.mjs` console demo scripts to committed Playwright specs

**Status:** ready

**Type:** TASK-PC-FE (project-internal — `projects/platform-console/apps/console-web/tests/e2e/` only)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (test authoring; needs the local federation demo stack to verify, plus new operator seed data)

---

## Goal

Four **uncommitted** ad-hoc browser scripts sit in `apps/console-web/tests/e2e/` — `verify-ecommerce-sellers.mjs`, `verify-ecommerce-sellers-multi.mjs`, `verify-ecommerce-all-sections.mjs`, `verify-omni-all-domains.mjs` (per project memory `env_console_demo_local_redeploy`, the demo tree is **not to be committed as-is**). They cover flows the committed suite does **not**: multi-tenant operator switching via `POST /api/tenant`, the ecommerce console sections, and the omni-corp 5-domain path. They are investigation scripts (raw `chromium.launch()`, `document.body.innerText` substring asserts, `waitUntil:'networkidle'`, and a `waitForTimeout(3000)` hard sleep in the omni script) — promotable, but only after they are rewritten to real-spec quality.

## Scope

**In scope** — `apps/console-web/tests/e2e/` only:

1. Convert the valuable, non-duplicative flows into committed `*.spec.ts` under the e2e suite:
   - multi-operator tenant switch → ecommerce sellers visible (the `verify-ecommerce-sellers-multi` flow);
   - omni-corp 5-domain reachability (the `verify-omni-all-domains` flow).
2. Replace `document.body.innerText` substring matching with `getByTestId`/`getByRole`; remove `waitUntil:'networkidle'` and the `waitForTimeout(3000)` hard sleep (use web-first assertions / explicit waits).
3. Reuse the committed `fixtures/login.ts` (`loginAsSuperAdmin` / a new seeded multi-operator helper) instead of the copy-pasted inline login block; the landing predicate must match the committed fixture's (`startsWith('/dashboards')`).
4. Add the operator-persona seed (the `ecommerce-operator@example.com` / `omni-operator@example.com` rows the demo scripts assume) to the suite's committed seed path so the new specs are reproducible in CI, not just on a hand-seeded local stack.
5. Delete the `.mjs` scripts + their `*.png` screenshots from the working tree once their coverage is committed (they are untracked demo artifacts).

**Out of scope:** the shared cross-suite OIDC-PKCE login extraction (TASK-MONO-282 gate); committing the demo seed SQL / compose variants that live under `tests/federation-hardening-e2e/` (separate, root-scoped, and currently intentionally uncommitted).

## Acceptance Criteria

- **AC-1** — the multi-operator-tenant-switch and omni-5-domain flows exist as committed `*.spec.ts` using `getByTestId`/`getByRole` (no `innerText` substring asserts, no `networkidle`, no `waitForTimeout`).
- **AC-2** — they reuse `fixtures/login.ts`; no inline copy-pasted login block; landing predicate matches the committed fixture.
- **AC-3 (reproducible)** — the operator personas the specs need are seeded by a committed fixture, so the specs run on a clean stack (verified locally against the federation demo stack per `env_console_demo_local_redeploy`).
- **AC-4** — `pnpm lint` + `tsc` + `vitest` (console-web) green before push (memory `env_console_web_local_verify_needs_lint`); `playwright test --list` shows the new specs collected.
- **AC-5** — the four `verify-*.mjs` + their `.png` outputs are removed from the working tree.

## Related Specs

- `projects/platform-console/specs/` console operator-overview + ecommerce-section + tenant-switch features (the flows being committed).
- project memory pointers: `env_console_demo_local_redeploy` (redeploy + verify recipe), `project_ecommerce_console_bringup_plan` (the 7-section bring-up these scripts came from).

## Related Contracts

None — exercises existing `/api/tenant` + console routes unchanged.

## Edge Cases

- **`POST /api/tenant` assumed-token reuse** — two consecutive switches to the same tenant reuse the cached assumed token (RFC 8693 idempotent exchange; see `subscription-plane-separation.spec.ts` workaround). A multi-switch spec must switch away-and-back to force a fresh exchange.
- **Secure-cookie / curl gap** — these flows cannot be asserted with curl (HttpOnly Secure cookies); Playwright is required (memory `project_ecommerce_console_bringup_plan`).

## Failure Scenarios

- Committing the `.mjs` scripts as-is (substring asserts, hard sleeps) → fails AC-1; demo-grade code in the committed suite.
- Promoting without committing the persona seed → green locally on a hand-seeded stack, red in CI (the MONO-250/251 nightly-drift class of failure).
