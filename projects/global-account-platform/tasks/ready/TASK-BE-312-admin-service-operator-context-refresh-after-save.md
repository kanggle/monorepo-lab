# Task ID

TASK-BE-312

# Title

admin-service operator-context refresh after MyProfileForm save — investigate + fix why the `/api/admin/console/registry` endpoint's `productItem[finance].operatorContext.defaultAccountId` does NOT reflect a value just saved via `PATCH /api/admin/operators/me/profile`. TASK-PC-FE-030 iter 3 (dispatch run `26336142592`) confirmed the overview page renders `domain-card-finance` correctly but the card's `data-status='forbidden'` instead of `'ok'` — matching the operators-profile.spec.ts documented Failure mode #2: "finance card stays `forbidden` after Save → the GAP registry's `productItem[finance].operatorContext.defaultAccountId` is not being refreshed (consumer caching surface)". Investigation must determine whether the staleness is at the admin-service save vs registry read level, the console-bff fan-out level, or the producer-side data consistency between save and read.

# Status

ready

# Owner

backend

# Task Tags

- code
- fix

---

# Dependency Markers

- **depends on**: TASK-PC-FE-030 (close `0dbf0ab5` — `/dashboards/overview` finance card rendering chain complete; DomainCard NOW resolves but `data-status` shows `forbidden`). TASK-PC-FE-029 / TASK-BE-311 / TASK-PC-FE-028 (closed predecessors in the cycle chain).
- **prerequisite of**: nightly main GREEN restoration. AC-2 of PC-FE-028, AC-1 of BE-311, AC-1 of PC-FE-029, AC-1 of PC-FE-030 all still deferred to this task's closure (this is the 12th-layer terminal candidate, though sibling spec `operators-admin-profile.spec.ts:89` may surface as 13th).

---

# Goal

After this fix lands, `operators-profile.spec.ts:54` passes — the `getByTestId('operator-overview-card-finance')` element's `data-status` attribute equals `'ok'` (NOT `'forbidden'`). The flow `MyProfileForm save → /dashboards/overview navigate → finance card 'ok' with balance data` reproducibly works end-to-end on the e2e harness.

## Root cause evidence (PC-FE-030 iter 3 dispatch `26336142592`)

- **Playwright failure** (`operators-profile.spec.ts:59` after testid rename):

  ```
  Error: expect(locator).toHaveAttribute(expected) failed
    Locator:  getByTestId('operator-overview-card-finance')
    Expected: "ok"
    Received: "forbidden"
       14 × locator resolved to <section data-domain="finance"
            data-status="forbidden"
            data-testid="operator-overview-card-finance"
            aria-labelledby="domain-card-finance-heading"
            class="...">…</section>
  ```

- **PC-FE-030 fix verified**: server-side fetch now forwards cookies → overview page successfully fetches the 5-domain composition. Diagnostic emitted `cardDomains:["gap","wms","scm","finance","erp"]`.

- **Significance**: the finance card IS being rendered (testid resolves 14×). The `data-status='forbidden'` value comes from the OperatorOverview response payload's `cards[finance].status` field. console-bff computes this status based on whether the operator has a `defaultAccountId` set in their `operatorContext` (TASK-PC-FE-016 / TASK-BE-304 chain established this semantic). If `defaultAccountId` is null/absent, status=`forbidden` with reason `MISSING_PREREQUISITE`.

- **Save endpoint verified working** (`my-profile-success` testid visible at spec line 47). The DB UPDATE succeeded (admin-service `admin_operators.finance_default_account_id` column written). But the subsequent read does not surface the new value.

## Hypothesis pool (to narrow during impl)

1. **admin-service registry endpoint reads from cache that's NOT invalidated on save** — `/api/admin/console/registry` may cache the operator's catalog response (per-operator-token or per-tenant). The save endpoint updates DB but doesn't invalidate the cache. Investigation: search admin-service for `@Cacheable` / explicit cache layers on the registry path; check save handler for cache eviction calls.

2. **admin-service save endpoint persists to DIFFERENT column than registry reads** — `PATCH /api/admin/operators/me/profile` writes column A; registry endpoint reads column B. Less likely given BE-304/BE-308 chain explicitly added `finance_default_account_id` to both read paths, but possible if `me/profile` is older code that wasn't updated.

3. **console-bff caches the registry response** — console-bff calls admin-service `/api/admin/console/registry` on each `/api/console/dashboards/operator-overview` call. If console-bff has a short-TTL cache (e.g., 30s), the save→read window may catch the stale value. Investigation: search console-bff for cache layers on the registry fetch.

4. **Eventual consistency between save and read** — admin-service uses event-driven flow (outbox + Kafka) for some writes. If `me/profile` save is async (write to outbox → consumer updates a read view), the spec's immediate read sees the OLD view. Investigation: trace the save handler's persistence path.

5. **Hidden 5th case** — DomainCard's `forbidden` may come from a finance-account-service authorization check (not the operatorContext.defaultAccountId at all). The operator's saved defaultAccountId might be correctly persisted + read but finance-account-service rejects the operator. Diagnostic must distinguish.

## Decision authority — defer

Spec does NOT pre-select an implementation option. Mirroring TASK-BE-311 / TASK-PC-FE-029 / TASK-PC-FE-030 investigation-first pattern:

The impl PR's first commit adds focused diagnostic instrumentation:
- admin-service `me/profile` save handler — log row written + column values (logger `TASK-BE-312`).
- admin-service registry endpoint — log row read + column values returned for the operator (logger `TASK-BE-312`).
- console-bff — log the operatorContext.defaultAccountId received from registry (logger `TASK-BE-312`).

The next dispatch's compose log dump confirms which layer surfaces the staleness. Subsequent commits apply the targeted fix.

---

# Scope

## In Scope

- `projects/global-account-platform/apps/admin-service/src/main/java/.../{me-profile-handler, registry-endpoint}` — diagnostic + targeted fix (whichever hypothesis lands).
- `projects/platform-console/apps/console-bff/src/main/java/.../{registry-fan-out, cache}` — diagnostic + fix only if hypothesis 3 lands (honest AC-5 violation per ADR-MONO-017 D4 HARD INVARIANT pattern).
- This task md + project `tasks/INDEX.md` ready entry.

## Out of Scope

- PC-FE-028/BE-311/PC-FE-029/PC-FE-030 fixes (closed; do not re-touch).
- `operators-admin-profile.spec.ts:89` — its `operator-profile-edit-value` empty issue may be a sibling 13th-layer task (next-cycle).

---

# Acceptance Criteria

- [ ] **AC-1 (functional, primary)** — Next dispatch / push `Platform Console E2E full-stack` job's step 17 `Run Playwright e2e (2 specs)` **SUCCESS** for the operators-profile spec (operators-admin-profile may still fail as 13th layer; that's AC-3 alternative). Verified via `gh run view <id>` Playwright spec count = 1 of 2 PASS.
- [ ] **AC-2 (functional, secondary)** — `operators-profile.spec.ts:54` passes the `toHaveAttribute('data-status', 'ok')` assertion against `operator-overview-card-finance`.
- [ ] **AC-3 (functional, tertiary)** — `operators-admin-profile.spec.ts` either passes (full GREEN) OR surfaces a DIFFERENT error class. If still failing, name next-cycle task in close chore.
- [ ] **AC-4 (hard invariant — auth-service byte-unchanged)** — `git diff --stat origin/main -- projects/global-account-platform/apps/auth-service/` = empty.
- [ ] **AC-5 (hard invariant — console-bff byte-unchanged, ADR-MONO-017 D4 preserved)** — `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty (default; honest scope adjustment if hypothesis 3 lands).
- [ ] **AC-6 (hard invariant — workflow + docker-compose byte-unchanged)** — `git diff --stat origin/main -- .github/workflows/ projects/platform-console/docker-compose.e2e.yml` = empty.
- [ ] **AC-7 (hard invariant — 5 other producers byte-unchanged)** — `git diff --stat origin/main -- 'projects/wms-platform/' 'projects/scm-platform/' 'projects/erp-platform/' 'projects/fan-platform/' 'projects/ecommerce-microservices-platform/' 'projects/finance-platform/'` = empty (**27회째 zero-retrofit**).
- [ ] **AC-8 (diagnostic cleanup)** — `git grep -n 'TASK-BE-312.*diagnostic' projects/global-account-platform/apps/admin-service/src/main/` returns 0 lines post-merge.
- [ ] **AC-9 (regression check)** — push CI `Integration (global-account-platform, Testcontainers)` GREEN — existing `OperatorAdminIntegrationTest` + `OperatorQueryServiceTest` continue to pass; a new IT case anchors the save→read consistency invariant if the fix is in admin-service.
- [ ] **AC-10 (BE-303 3-dim merge verification)** — close chore PR authored only after impl PR's 3-dim verification passes.

---

# Related Specs

- [`projects/global-account-platform/PROJECT.md`](../../PROJECT.md).
- [`projects/platform-console/apps/console-web/tests/e2e/operators-profile.spec.ts`](../../../platform-console/apps/console-web/tests/e2e/operators-profile.spec.ts) — failing spec at line 59 (post-testid rename).
- [`projects/global-account-platform/specs/services/admin-service/admin-api.md`](../../specs/services/admin-service/admin-api.md) — admin-service operators + registry endpoints contract.
- [`projects/global-account-platform/apps/admin-service/src/...`](../../apps/admin-service/src/) — investigation surface (save handler + registry endpoint).
- [`projects/platform-console/tasks/done/TASK-PC-FE-030-overview-page-finance-card-rendering.md`](../../../platform-console/tasks/done/TASK-PC-FE-030-overview-page-finance-card-rendering.md) — predecessor closure narrative.
- [`projects/global-account-platform/tasks/done/TASK-BE-304-admin-operators-registry-operator-context-extension.md`](../done/TASK-BE-304-admin-operators-registry-operator-context-extension.md) (or whichever BE-304 task name) — original `finance_default_account_id` column + registry surface.
- [`projects/global-account-platform/tasks/done/TASK-BE-306-admin-operators-me-profile-update.md`](../done/TASK-BE-306-admin-operators-me-profile-update.md) (or similar) — `me/profile` save endpoint.

# Related Contracts

- `projects/global-account-platform/specs/services/admin-service/admin-api.md` — verify save vs registry read consistency contract.

# Related Skills

- None additional.

---

# Edge Cases

- **Hypothesis 1 fix (cache invalidation)** — if admin-service has a `@Cacheable` on the registry endpoint, the save handler should call the cache manager's evict for the operator's key. Add a regression IT.
- **Hypothesis 3 fix (console-bff cache, AC-5 violation)** — would require console-bff change. Honest scope adjustment with explicit cache invalidation strategy (TTL alone is risk-prone for e2e timing).
- **operators-admin-profile spec sibling failure** — if it fails with `operator-profile-edit-value` empty, may also be cache-related (the dialog pre-populate reads stale `operatorContext` from somewhere). Same root cause potentially.

# Failure Scenarios

- **AC-1 still fails after fix with a DIFFERENT error class** — 13th cycle layer surfaced. Author next-cycle task.
- **AC-5 violation needed** — honest scope adjustment with evidence link.

---

# Test Requirements

- Existing IT (`OperatorAdminIntegrationTest` / `OperatorQueryServiceTest`) continue to pass.
- If admin-service fix lands: add a new IT case asserting save→read consistency (the same hermetic UUID pattern as BE-304/BE-308).
- AC-1 verification = `workflow_dispatch` on impl branch (≤30 min signal).

---

# Definition of Done

- [ ] Three PRs landed in order: spec PR, impl PR, close-chore PR.
- [ ] AC-1 through AC-10 all checked off in close-chore PR description.
- [ ] If AC-1 still fails with different error, name next-cycle task in close chore.

---

# 메타 (intended)

① **12th cycle layer in TASK-MONO-014 chain** — PC-FE-023 → 024 → MONO-132 → 025 → 026 → MONO-133 → PC-FE-027 → PC-FE-028 → BE-311 → PC-FE-029 → PC-FE-030 → **BE-312 (this — operator-context refresh-after-save 12th layer)**. Cycle pattern operates at 12-layer scale; progressive-surface principle continues.

② **Investigation-first cycle pattern, 4th consecutive task** — PC-FE-027/028/BE-311/PC-FE-029/PC-FE-030/BE-312 all use the diagnostic-first → fix → cleanup shape. The pattern is the default for cycle-pattern layers where the root cause is non-obvious.

③ **Producer-side data consistency as a hypothesis class** — PC-FE-030 cycle established "infrastructure env var missing" + "API misuse / convention mismatch" hypothesis classes. BE-312 adds "producer-side cache invalidation / save-read consistency" as the 3rd class for cycle-pattern hypothesis pool authoring.

④ **3-iter floor when failing tier has observability** (PC-FE-029 lesson) — admin-service emits structured JSON logs; diagnostic instrumentation just routes the save/read column values into the dump. Expected 3-iter (diagnostic + fix + cleanup).

⑤ **27회째 zero-retrofit invariant** — fix lives entirely within `projects/global-account-platform/`. ADR-MONO-017 D4 HARD INVARIANT continued (default AC-5 says console-bff byte-unchanged; honest scope adjustment only if hypothesis 3 forces).

⑥ **memory update post-cycle** — after BE-312 close, audit-memory cycle to capture: layer 12 closure, save-read consistency diagnostic pattern, cycle pattern 12+ layer realization across 4 days.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (diagnostic-first impl pattern; admin-service is well-instrumented).
