# TASK-MONO-280 — Extract the duplicated federation-hardening-e2e spec helpers into shared fixtures

**Status:** ready

**Type:** TASK-MONO (monorepo-level — shared root `tests/federation-hardening-e2e/` harness; no project paths touched)

**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (behavior-preserving TS extraction within one package boundary; no contract/spec change)

---

## Goal

The `tests/federation-hardening-e2e/` Playwright suite carries several **byte-identical private copies** of the same admin-surface + console-route helpers, one per spec. A cross-suite e2e diagnosis (three sibling suites: this one, `console-web`, `web-store`) flagged this suite's *internal* duplication as the densest and the safest first consolidation, because it lives entirely inside one pnpm package (no shared-lib package needed) and is purely test code.

Extracted helpers (each previously duplicated verbatim):

- **admin-surface** (used by the 3 admin RBAC / access-condition specs, constants also by the plane-separation spec): `operatorToken`, `codeOf`, `send` (transient-5xx retry), `headers` (the SOURCE_IP-superset 3-arg form), the `beforeAll` outbox warm-up gate, and the `ADMIN_BASE` / `STORAGE_STATE` / `OPERATOR_COOKIE` constants.
- **console-route** (used by the 3 overview / tenant-switch specs): `gotoOverview`, `switchTenant`.

## Scope

**In scope** — one PR, all under `tests/federation-hardening-e2e/`:

1. **New `fixtures/admin-helpers.ts`** — `export`s `ADMIN_BASE`, `STORAGE_STATE`, `OPERATOR_COOKIE`, `operatorToken`, `headers(token, reason, sourceIp?)`, `codeOf`, `send`, and `warmUpAdminOutbox(browser, { probe, accept, logPrefix, cleanup? })`. The warm-up gate generalizes the triplicated 12×/4s loop: each spec passes its own `probe` (the audit→outbox write it uses to detect a writable stack), `accept` codes, `logPrefix`, and optional `cleanup`.
2. **New `fixtures/console-helpers.ts`** — `export`s `gotoOverview(page)` and `switchTenant(ctx, tenant)`.
3. **`specs/tenant-admin-delegation.spec.ts`** (MONO-210), **`specs/iam-admin-source-ip-condition.spec.ts`** (MONO-221), **`specs/iam-admin-resource-tag-condition.spec.ts`** (MONO-228) — delete the local `operatorToken`/`headers`/`codeOf`/`send` + the three constants; import from `admin-helpers`; replace each `beforeAll` warm-up body with a `warmUpAdminOutbox(...)` call. The spec-specific request builders (`assign`/`unassign`/`patchRoles`/`setOrgScope`/`changeSub`/`listAssignments`/`quietUnassign`/…) stay in their specs and call the shared `send`/`headers`.
4. **`specs/subscription-plane-separation.spec.ts`** (MONO-207) — import `ADMIN_BASE`/`STORAGE_STATE`/`OPERATOR_COOKIE` from `admin-helpers`; replace the local `switchTenant` with the `console-helpers` import. Its bespoke `setStatus`/`tryResume`/`readCookie`/`assumedClaims` (which carry custom `X-Tenant-Id:'*'` + JWT-decode logic) stay local.
5. **`specs/entitlement-trust-crossdomain.spec.ts`** (MONO-154), **`specs/tenant-switch-rescope.spec.ts`** (MONO-158) — replace the local `gotoOverview` (and, in tenant-switch, `switchTenant`) with the `console-helpers` imports. The per-customer entitlement assertions (`assertEntitlement` and the inlined card loops) stay local.

**Out of scope (explicitly deferred, not silent):**

- **Cross-suite consolidation** of the OIDC login flow / `devpassword123!` constant / config presets shared with `console-web` and `web-store`. Those live in three separate pnpm packages, so true code sharing needs a new shared lib package (e.g. `libs/e2e-toolkit`) — a larger ADR-level decision tracked separately.
- **Tier-A golden-path body** parameterization (the `goto → networkidle → heading` shape repeated in 5 specs) — deferred; lower risk/value and would merge per-spec test titles.
- **The uncommitted demo scripts** (`verify-*.mjs`, `seed-omni-*.sql`, compose variants) — demo tree, not committed.

## Acceptance Criteria

- **AC-1** — `fixtures/admin-helpers.ts` and `fixtures/console-helpers.ts` exist with the exports above; no spec re-declares `operatorToken`/`codeOf`/`send`/`headers`/`gotoOverview`/`switchTenant` or the `ADMIN_BASE`/`STORAGE_STATE`/`OPERATOR_COOKIE` constants locally.
- **AC-2 (behavior preserving)** — the set of collected tests is byte-identical before/after: `npx playwright test --list` yields the same spec files, describe/test titles, and total count. No test logic, assertion, timeout (15_000/20_000/30_000/240_000), retry, or HTTP-status expectation changed.
- **AC-3 (the one intentional non-byte diff, documented)** — `headers` is unified on the 3-arg SOURCE_IP superset (2-arg callers pass `sourceIp=undefined` → no `X-Forwarded-For` → identical output); the `switchTenant` failure-**diagnostic message** is unified (the 200-or-bust outcome is unchanged); the warm-up per-attempt `console.log` text is genericized to `probe http=<status>` (diagnostic only). None affect pass/fail.
- **AC-4** — `npx tsc --noEmit -p tsconfig.json` is clean (no new errors; no unused imports/locals).
- **AC-5** — federation-hardening-e2e is nightly + `workflow_dispatch` (NOT PR-gated); a green collection (`--list`) + clean `tsc` is the pre-merge gate. A full run is verified post-merge via `gh workflow run federation-hardening-e2e.yml` when the stack is exercised.

## Related Specs

None. This is test-harness refactoring only — no `platform/`, `rules/`, or `projects/**/specs/` change. The specs' authority-model rationale (in each spec's header comment) is preserved verbatim.

## Related Contracts

None. No HTTP or event contract is touched; the `/api/admin/**` and `/api/tenant` calls are byte-identical (same URLs, headers, bodies).

## Edge Cases

- **Warm-up `cleanup` ordering** — `warmUpAdminOutbox` runs `cleanup` (e.g. `quietUnassign`) **before** the final `expect(warm).toBe(true)`, exactly as each original `beforeAll` did, so a throwaway warm-up assignment is always undone even though the assert may fail.
- **`resolve` of `STORAGE_STATE`** — the constant moved from `specs/*` (where `__dirname` = `specs/`, path `../fixtures/.storage-state.json`) into `fixtures/admin-helpers.ts` (where `__dirname` = `fixtures/`, path `.storage-state.json`) — same absolute file.
- **`res.json()` single-read** — `switchTenant` reads the response body once (`(await res.json()).activeTenant`), matching the originals (no double-consume).

## Failure Scenarios

- **A spec keeps a stale local helper** → duplicate-identifier or unused-import → caught by AC-4 (`tsc`).
- **An import path typo** (`../fixtures/...`) → `tsc` module-resolution error → AC-4.
- **Accidental test-title or count change** (e.g. a describe edited) → AC-2 `--list` diff is non-empty → STOP.
