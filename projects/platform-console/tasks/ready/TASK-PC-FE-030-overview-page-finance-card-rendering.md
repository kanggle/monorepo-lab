# Task ID

TASK-PC-FE-030

# Title

`/dashboards/overview` page finance card rendering — investigate + fix why `getByTestId('domain-card-finance')` is not found within Playwright timeout. TASK-PC-FE-029 iter 2 (dispatch run `26334952453`) advanced the failure boundary from `/operators` page (now rendering correctly) to `/dashboards/overview`'s domain cards: `operators-profile.spec.ts:54` expects `domain-card-finance` testid with `data-status='ok'`, but the locator times out (element not found). The overview page is the operator-overview composition consumed via `console-bff /api/console/dashboards/operator-overview` (ADR-MONO-017 D6 fan-out). Investigation must determine whether the failure is at the page state level (similar to PC-FE-029's degraded branch), the console-bff composition response level, or the DomainCard rendering condition level.

# Status

ready

# Owner

frontend

# Task Tags

- code
- fix

---

# Dependency Markers

- **depends on**: TASK-PC-FE-029 (close `0ed4cafb` — `/operators` page rendering chain complete via `GAP_ADMIN_API_BASE` compose env). TASK-BE-311 (close — OIDC PKCE login chain end-to-end). TASK-PC-FE-028 (close — DNS layer cleared).
- **prerequisite of**: nightly main GREEN restoration. AC-2 of PC-FE-028, AC-1 of BE-311, AC-1 of PC-FE-029 all still deferred to this task's closure.

---

# Goal

Make the next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` SUCCESS (or surface the NEXT layer per cycle pattern). After this fix lands, the spec's `getByTestId('domain-card-finance')` resolves to a visible element with `data-status='ok'` (or any data-status value — the toHaveAttribute assertion is a follow-up after the locator resolves), confirming the overview composition flows end-to-end.

## Root cause evidence (PC-FE-029 iter 2 dispatch `26334952453`)

- **Playwright failure** (`operators-profile.spec.ts:54`):

  ```
  Error: expect(locator).toHaveAttribute(expected) failed
    Locator: getByTestId('domain-card-finance')
    Expected: "ok"
    Timeout: 5000ms
    Error: element(s) not found

       54 |     await expect(financeCard).toHaveAttribute('data-status', 'ok');
          |                               ^
  ```

- **PC-FE-029 fix verified**: `/operators` page now renders MyProfileForm (`hasPage:true, pageContent:{items:4, total:4}` from PC-FE-029 iter 2 diagnostic). Spec lines 42-47 pass (input + fill + save + success). Failure is on the NEXT page (`/dashboards/overview` after line 49 `page.goto`).

- **Significance**: similar architecture seam to PC-FE-029. `OperatorOverviewPage` server component branches on `state` (visible inline at the page top: `unauthorized` → `/login`, `noTenant` → notice). The DomainCard component owns the `domain-card-<domain>` testid (`features/operator-overview/components/DomainCard.tsx:231`); it's only mounted when the overview response contains a card entry for the `finance` domain.

## Hypothesis pool (to narrow during impl)

1. **`/dashboards/overview` page in degraded / error state** — analogous to PC-FE-029 hypothesis 3: the page falls into a degraded branch (e.g. `state.unauthorized` / a `state.degraded` / `state.cards.length === 0`) and never mounts DomainCard. Root cause candidates:
   - console-bff `/api/console/dashboards/operator-overview` 5xx / timeout (similar `GAP_ADMIN_API_BASE` env var miss pattern).
   - console-web's overview fetch authorization gate.

2. **DomainCard render condition gates the finance domain out** — the operator-overview response includes 6 domains (gap, finance, wms, scm, erp, fan) but the finance card data status causes the component to skip rendering (e.g. `status: 'down'` + `display: 'hidden'`). Root cause candidates:
   - finance domain's `operatorContext.defaultAccountId` is null (TASK-PC-FE-016's "missing prerequisite" condition); DomainCard renders a `forbidden` state with the SAME testid (NOT a separate testid). The spec's `toHaveAttribute('data-status', 'ok')` expects the value `ok`; the locator resolution should still succeed even if the value is wrong. Look up at the Playwright error: "Error: element(s) not found" — locator did NOT find the element. So the DomainCard is NOT being mounted at all.
   - DomainCard's mount condition filters by some other attribute (visibility, feature flag).

3. **console-bff composition response missing finance entirely** — the operator-overview API response includes only the 5 non-finance domains (or some subset). Root cause candidates:
   - `CONSOLE_BFF_OUTBOUND_FINANCE_BASE_URL` is misconfigured (docker-compose has `http://finance-account-service:8080`; verify).
   - The fan-out adapter's null/empty composition strips finance silently.
   - The `MyProfileForm` save succeeded but the operatorContext refresh didn't propagate to the overview cache (TASK-PC-FE-029 mentioned this as a possible failure mode in spec).

4. **Hidden 4th case** — DOM-level rendering issue (React error boundary, hydration mismatch, layout component swallowing the card). Less likely; the diagnostic step should capture the actual rendered HTML.

## Decision authority — defer

Mirroring TASK-PC-FE-029's investigation-first pattern (which converged from a 4-state hypothesis pool to root cause in a single ~14-min dispatch + 1 fix dispatch):

The impl PR's first commit adds a focused diagnostic at the `/dashboards/overview` server component:
- Log the resolved overview state shape (which branch hit) at server-render time.
- Log the cards array length + each card's `domain` + `status` if the OK branch is hit.
- Logger name `TASK-PC-FE-030`.

Subsequent commits apply the targeted fix. Diagnostic cleanup follows in the final commit.

---

# Scope

## In Scope

- `projects/platform-console/apps/console-web/src/app/(console)/dashboards/overview/page.tsx` — add diagnostic log (iter 1); remove in cleanup iter.
- Whichever of these the diagnostic narrows to:
  - `projects/platform-console/docker-compose.e2e.yml` — env var addition (hypothesis 3, if console-bff outbound URL needs a tweak).
  - `projects/platform-console/apps/console-bff/src/...` — composition logic (hypothesis 3, less likely given ADR-MONO-017 D4 zero-retrofit invariant).
  - `projects/platform-console/apps/console-web/src/features/operator-overview/` — DomainCard mount condition (hypothesis 2/4).
  - `projects/platform-console/apps/console-web/src/features/operator-overview/api/...` — overview-state catch path (hypothesis 1).
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- PC-FE-028 / BE-311 / PC-FE-029 fixes (closed; do not re-touch).
- `operators-admin-profile.spec.ts` — runs separately; once this task lands, any-remaining failure becomes a separate cycle layer.

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` **SUCCESS** (full job GREEN). Verified by `gh run view <id>` step 17 conclusion = success + overall conclusion = success.
- [ ] **AC-2 (functional, secondary)** — `operators-profile.spec.ts` passes all 5 assertion lines (input visible → fill → save → success → finance card 'ok'). Lines 42-47 ALREADY pass post-PC-FE-029; this task targets line 54.
- [ ] **AC-3 (functional, tertiary)** — `operators-admin-profile.spec.ts` either passes (full GREEN) OR surfaces a DIFFERENT error class that's NOT a regression of this task's fix.
- [ ] **AC-4 (hard invariant — auth-service byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/apps/auth-service/` = empty.
- [ ] **AC-5 (hard invariant — console-bff byte-unchanged, ADR-MONO-017 D4 preserved)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty (default; honest scope adjustment if root cause forces otherwise, mirror PC-FE-029 AC-6 pattern).
- [ ] **AC-6 (hard invariant — workflow byte-unchanged)** — `git diff --stat origin/main -- .github/workflows/` = empty.
- [ ] **AC-7 (hard invariant — 5 other producers byte-unchanged)** — `git diff --stat origin/main -- 'projects/wms-platform/' 'projects/scm-platform/' 'projects/erp-platform/' 'projects/fan-platform/' 'projects/ecommerce-microservices-platform/' 'projects/finance-platform/'` = empty (**26회째 zero-retrofit invariant**).
- [ ] **AC-8 (diagnostic cleanup)** — `git grep -n 'TASK-PC-FE-030.*diagnostic' projects/platform-console/apps/console-web/src/` returns 0 lines post-merge.
- [ ] **AC-9 (regression check)** — push CI `Frontend unit tests` + `Frontend lint & build` + `Integration (platform-console console-bff, Testcontainers + WireMock JWKS)` GREEN.
- [ ] **AC-10 (BE-303 3-dim objective merge verification)** — close-chore PR authored only after impl PR's 3-dim verification passes.

---

# Related Specs

- [`projects/platform-console/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/apps/console-web/tests/e2e/operators-profile.spec.ts`](../../apps/console-web/tests/e2e/operators-profile.spec.ts) — failing spec at line 54.
- [`projects/platform-console/apps/console-web/src/app/(console)/dashboards/overview/page.tsx`](../../apps/console-web/src/app/(console)/dashboards/overview/page.tsx) — server component.
- [`projects/platform-console/apps/console-web/src/features/operator-overview/components/DomainCard.tsx`](../../apps/console-web/src/features/operator-overview/components/DomainCard.tsx) — owner of `domain-card-finance` testid.
- [`projects/platform-console/specs/console-integration-contract.md`](../../specs/console-integration-contract.md) — console-bff fan-out contract.
- [`projects/platform-console/tasks/done/TASK-PC-FE-029-operators-page-e2e-rendering.md`](../done/TASK-PC-FE-029-operators-page-e2e-rendering.md) — predecessor closure narrative (same diagnostic-first pattern).

# Related Contracts

- `projects/platform-console/specs/console-integration-contract.md` — verify `/api/console/dashboards/operator-overview` response shape.

# Related Skills

- None additional.

---

# Edge Cases

- **Compose env var missing (mirror PC-FE-029)** — if console-bff outbound URLs default to dev-only Traefik paths that don't resolve in the e2e overlay, fix the docker-compose env block. Same pattern as PC-FE-029's `GAP_ADMIN_API_BASE` fix.
- **operatorContext refresh lag (hypothesis 3 sub-case)** — the spec sets the finance default account at line 45 then immediately navigates to overview at line 49. If the registry catalog cache (server-side) caches the operatorContext for a short window, the overview at line 49 may still see `defaultAccountId: null` and render the finance card as `forbidden / MISSING_PREREQUISITE`. The spec assertion expects `data-status='ok'`, but the locator failure suggests the card isn't even rendered (forbidden state has the same testid). Investigate the DomainCard component's render gate.
- **Spec assertion vs response semantic** — `toHaveAttribute(data-status, 'ok')` requires both the element existence AND the attribute match. Even if the card is mounted with `data-status='forbidden'`, the locator should still resolve and only the attribute check fails. "element(s) not found" suggests the card itself isn't rendered (or the testid is different).

# Failure Scenarios

- **AC-1 still fails after fix with a DIFFERENT error class** — 12th cycle layer surfaced. Author next-cycle task.
- **AC-5 violation needed** — if root cause requires console-bff change, honest scope adjustment with evidence link.
- **operators-admin-profile.spec.ts fails differently** — its line 57 `getByTestId('action-edit-profile-e2e-target-operator')` not found could be a sibling 11th-layer issue OR a parallel symptom of the same root cause. Investigate during impl.

---

# Test Requirements

- Existing vitest unit + slice tests for overview page / DomainCard continue to pass.
- AC-1 verification = `workflow_dispatch` on impl branch (≤30 min signal) or post-merge nightly push.

---

# Definition of Done

- [ ] Three PRs landed in order: spec PR, impl PR, close-chore PR.
- [ ] AC-1 through AC-10 all checked off in close-chore PR description.
- [ ] If AC-1 still fails with different error class, name next-cycle task in close chore narrative.

---

# 메타 (intended)

① **11th cycle layer in TASK-MONO-014 chain** — PC-FE-023 → 024 → MONO-132 → 025 → 026 → MONO-133 → PC-FE-027 → PC-FE-028 → BE-311 → PC-FE-029 → **PC-FE-030 (this — overview page finance card 11th layer)**. Cycle pattern operates at the 11-layer scale; progressive-surface principle continues.

② **Investigation-first cycle pattern, 3rd consecutive task** — PC-FE-027/028/BE-311/PC-FE-029 all used the same diagnostic-first → fix → cleanup shape. PC-FE-030 mirrors. The investigation-first pattern has become the default for cycle-pattern layers where the root cause is non-obvious.

③ **PC-FE-029 lesson applied** — "infrastructure env var missing" is now a known hypothesis class for compose-based e2e harness tasks. PC-FE-030's hypothesis 1/3 includes this candidate explicitly.

④ **3-iter floor maintained** — when observability is high (the failing tier emits structured logs with dimensional attributes), the cycle compresses to diagnostic + fix + cleanup. console-web should already log overview-fetch failures; if not, the diagnostic step adds the missing observability.

⑤ **26회째 zero-retrofit invariant** — fix lives entirely within `projects/platform-console/`. ADR-MONO-017 D4 HARD INVARIANT continued; default AC-5 says console-bff byte-unchanged (honest scope adjustment only if root cause forces otherwise, mirror PC-FE-029 AC-6).

⑥ **memory update post-cycle** — after PC-FE-030 close, audit-memory cycle to capture: layer 11 closure, overview page rendering / console-bff fan-out diagnostic pattern, cycle pattern 11+ layer realization.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (diagnostic-first impl pattern).
