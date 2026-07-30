# Task ID

TASK-PC-FE-259

# Title

console-web: eliminate all forbidden `features/A → features/B` imports (17 edges / 8 feature pairs) — promote genuinely-shared values to `shared/`, relocate the mis-layered erp eligibility pre-flight to `app/`, and consolidate the duplicated F5 `Money` primitive

# Status

review

# Owner

frontend

# Task Tags

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

# Goal

`console-web` currently carries **17 forbidden same-layer imports** across **8 feature pairs**, in direct violation of
[`specs/services/console-web/architecture.md`](../../specs/services/console-web/architecture.md)
§ Allowed Dependencies / § Forbidden Dependencies / § Boundary Rules:

> 같은 계층 `features/A → features/B` 상호 참조 금지(공유 가치는 `shared/` 로 승격).

**14 of the 17 are additionally "deep" imports** that bypass the target feature's public `index.ts` barrel and reach
straight into its internals (`@/features/accounts/api/accounts-api`, `@/features/audit/api/types`, …), so they violate
the barrel rule as well as the layer rule.

Eliminate **all 17** so that a grep for a cross-feature import inside `src/features/` returns **0**, by applying the
rule's **own remedy** per edge:

- genuinely-shared values → **promote to `shared/`** (the precedent already in the codebase:
  `shared/api/rbac-catalog.ts`, whose header cites this exact rule as the reason it does not live inside
  `features/permissions` or `features/permission-sets`);
- mis-layered values → **relocate to the layer that owns them** (`app/` is explicitly allowed to compose
  `features/*` + `shared/*`);
- producer wire types leaking across a feature boundary purely as an *aggregator view-model* → give the aggregator
  its **own view-model** instead.

The single largest concrete piece of drift this exposes is a **character-for-character duplicated F5 money
primitive** — `MoneySchema` / `Money` / `DEFAULT_CURRENCY_SCALES` / `formatMoney` exist twice, in
`features/finance-ops/api/types.ts` and `features/ledger-ops/api/types/money.ts`. That duplicate is exactly the
"shared value" the rule's remedy is meant to give a home to, and consolidating it is a precondition for untangling
the `finance-overview` / `finance-ops` / `ledger-ops` triangle.

**No user-visible behavior change.** This is a pure structural refactor: every producer call, credential, header
matrix, error taxonomy, degrade posture, testid and rendered string is preserved byte-for-byte.

---

# Scope

## In Scope

### The 17 violating edges (baseline, measured 2026-07-29 on `origin/main`)

| # | file:line | violating edge | deep? |
|---|---|---|---|
| 1 | `features/dashboards/api/overview-api.ts:1,2,3` | → `accounts`, `audit`, `operators` | deep ×3 |
| 2 | `features/iam-overview/api/overview-state.ts:4,5,6,7` | → `operators`, `accounts`, `audit` ×2 | deep ×4 |
| 3 | `features/iam-overview/components/IamOverviewAuditCard.tsx:4` | → `audit` (`AuditRow`) | deep |
| 4 | `features/finance-overview/api/overview-state.ts:8,9,10` | → `ledger-ops`, `finance-ops` ×2 | deep ×3 |
| 5 | `features/finance-overview/components/FinanceOverviewScreen.tsx:10` | → `finance-ops` | deep |
| 6 | `features/operators/hooks/use-operator-assignments.ts:24,31` | → `erp-ops` | deep ×2 |
| 7 | `features/operators/hooks/use-org-scope-form.ts:8` | → `erp-ops` | deep |
| 8 | `features/catalog/components/CatalogGrid.tsx:3` | → `tenant` | barrel |
| 9 | `features/erp-ops/api/erp-eligibility.ts:1` | → `catalog` | barrel |

### Per-pair resolution (judged individually — NOT one mechanical rule)

**(A) `catalog` → `tenant` (`useTenantSwitch`) — `shared/` promotion.**
The active-tenant switch is a console-wide concern, not a `tenant`-feature private: it is driven from the top-bar
`TenantSwitcher` (feature `tenant`) *and* from every product card in `CatalogGrid` (feature `catalog`). Two features,
one value → `shared/api/use-tenant-switch.ts`. `features/tenant` keeps exporting it from its barrel (it remains part
of that feature's public surface); both call sites import the shared module.

**(B) `erp-ops` → `catalog` (`getCatalog`) — layer relocation to `app/`, NOT a `shared/` promotion.**
`resolveErpEligibility()` is a **registry pre-flight**, and every sibling domain already performs that pre-flight
**in the page** and passes the resolved `eligible` boolean into the feature's state loader — `wms-ops`,
`scm-ops`, `finance-ops`, `ledger-ops`, `ecommerce-ops` state modules all document exactly that
("the page resolves … from the data-driven registry (§ 2.2, `getCatalog()`) and passes it in here"). `erp-ops` is
the lone straggler that pulled the pre-flight *inside* the feature and therefore had to import another feature.
`app/` is explicitly allowed to import `features/*`, and the codebase already has this exact file under `app/`:
`app/(console)/ecommerce/products/_eligibility.ts`. So the fix is to move the module to
`app/(console)/erp/_eligibility.ts` — no new `shared/` surface, and erp gains parity with its 5 siblings.

**(C) `operators` → `erp-ops` (`ERP_KEY`; `Department` / `DepartmentListResponse(Schema)` / `isRetired`) —
`shared/` promotion.**
The org_scope dialog renders an **erp department picker** inside the IAM operators feature and must invalidate the
erp read-model query tree after a successful `org_scope` write. The existing code comments already document *why*
the erp-ops **barrel** cannot be used here (it re-exports server-only state that would drag `next/headers` into the
client bundle) — i.e. barrel-routing is not available for this pair, which is precisely the situation the promotion
remedy exists for. Promote:
- `ERP_KEY` → `shared/api/query-keys.ts` (the registry of React-Query key roots that **more than one feature**
  must reference for cross-feature invalidation);
- the **client-safe** erp masterdata read shapes actually shared (`EffectivePeriod`, `isRetired`, `Department`,
  `DepartmentListResponse(Schema)` and the department read schema they compose) → `shared/api/erp-masterdata-types.ts`.

`features/erp-ops` re-exports both from its existing public type barrel (`api/types.ts`) and `api/erp-keys.ts`, so
every erp-internal consumer and the erp public surface are untouched.

**(D) `dashboards` / `iam-overview` → `accounts` + `audit` + `operators` — `shared/` promotion of the three
cross-consumed READ clients only (mutations stay in their features).**
`searchAccounts`, `queryAudit` and `listOperators` each have **three** consumers (their own feature + `dashboards`
+ `iam-overview`) — the textbook "shared value". They are promoted to `shared/api/`, mirroring
`shared/api/rbac-catalog.ts` verbatim (same IAM `admin-service`, same `callAdminGateway` core, same operator-token
+ `X-Tenant-Id` invariants).

**Constraint that shapes this edge (do not violate):** `specs/contracts/console-integration-contract.md`
§ 2.4.4 + § 3.1 (a **contract** — Source-of-Truth layer 6, which *outranks* `architecture.md` at layer 7) pins the
parity rows to a **feature module + client export name**:
row 1 `features/accounts` `searchAccounts`, rows 9–11 `features/audit` `queryAudit`, § 2.4.4 leg 3
`features/operators` `listOperators`, row 16 `features/dashboards` `getOperatorOverview` ("no new producer —
bounded fan-out composing the EXISTING reads"). `tests/unit/parity-matrix.ts` + `parity-verification.test.ts`
mechanically attest those 16 rows and architecture.md § 3.1 requires the marker count to stay exactly **16**.

Therefore:
- the **implementation** moves to `shared/api/iam-{accounts,audit,operators}-read.ts` (so no feature imports
  another feature), **and**
- each feature **re-exports the promoted read on its contract-pinned public path**
  (`features/accounts/api/accounts-api.ts` still exports `searchAccounts`, etc.), so § 3.1 rows stay literally
  true, the parity attestation stays at 16 rows and passes unchanged, and every existing consumer (route handlers,
  hooks, pages, tests) is untouched.
- `getOperatorOverview` **stays in `features/dashboards`** (contract row 16) and `getIamOverviewState` stays in
  `features/iam-overview`; only their import targets change to `@/shared/api/…`.
- Only the genuinely-shared **reads** move. The privilege-sensitive **mutations** (lock / unlock / bulk-lock /
  revoke-session / gdpr-delete / export / create-operator / edit-roles / change-status / password / profile) stay
  in their features, where § 3.1 rows 3–8, 12–15, 17–18 place them.

**(E) `iam-overview` → `audit` (`AuditRow`) — aggregator-owned view-model, no promotion.**
`IamOverviewAuditCard` never actually consumes the `AuditRow` discriminated union: its own comment says it reads
fields "off one permissive record view instead of per-variant narrowing", and it immediately casts
`row as { source; auditId?; eventId?; actionCode?; outcome?; occurredAt? }`. Leaking a producer wire type across a
feature boundary to express *that* is the defect. Give `iam-overview` its own tolerant
`IamOverviewAuditRow` view-model (identical permissive shape, so the parse tolerance and the rendered output are
unchanged) — the correct boundary design, and no `shared/` surface is created for a type only one feature needs.

**(F) `finance-overview` → `finance-ops` + `ledger-ops` — `shared/lib/money.ts` + `shared/` promotion of the
cross-consumed finance/ledger reads.**
1. **The duplicate**: `MoneySchema` / `Money` / `DEFAULT_CURRENCY_SCALES` / `formatMoney` are byte-identical in
   `features/finance-ops/api/types.ts` and `features/ledger-ops/api/types/money.ts`. Consolidate into
   **`shared/lib/money.ts`** (the F5 precision-exact minor-units-string primitive; framework-agnostic, no fetch, no
   feature coupling → `shared/lib/`, alongside `datetime.ts` / `tolerant-label.ts`). Both feature type modules
   re-export it, so their many internal consumers (`TrialBalanceTable`, `BalancesTable`, `PositionLotsTable`,
   `AccountDetail`, …) are untouched; `finance-overview` consumes `shared/lib/money.ts` directly.
   The F5 "never `Number()` / `parseFloat()` / `parseInt()` on `amount`" grep-assertion tests keep passing —
   extend their scanned roots to include the shared module so the invariant is still mechanically enforced at its
   new home (a guard that stops covering the code it guards is worse than no guard).
2. **The reads**: promote `getAccount` / `getBalances` + the account/balance read shapes + `accountStatusTone` /
   `KNOWN_ACCOUNT_STATUSES` / `balanceMoney` → `shared/api/finance-accounts-read.ts`, and
   `getTrialBalance` / `listPeriods` / `listDiscrepancies` / `getFxRates` + the `callLedger` hardened core + the
   four result-type modules they need → `shared/api/ledger-*`. `finance-ops` / `ledger-ops` re-export them on
   their existing public paths (`api/finance-api.ts`, `api/ledger-api.ts`, `api/types(/index).ts`), so the 19
   `app/api/ledger/**` route handlers, the ledger/finance feature screens and their tests are untouched.
   `getFinanceOverviewState` stays in `features/finance-overview`; only its import targets change.

### Spec correction (§ 5 of the workflow)

`architecture.md` line ~192 currently documents this violation **as if it were the intended design**:

> `api/overview-state.ts` `getFinanceOverviewState(eligible)` — **console-web DIRECT 재사용**(`features/ledger-ops`
> +`features/finance-ops` 의 EXISTING server client …)

and line ~28-34 of `finance-overview/api/overview-state.ts` goes further, inventing a licence that does not exist:
"the same cross-feature 'overview aggregator' **exception** already established by `iam-overview` importing
`features/{operators,accounts,audit}/api/*`". There is no such exception in the spec — that prose is **describing
drift, not licensing it**, and the § Forbidden Dependencies rule (the actual architecture constraint) outranks a
feature-level descriptive note. Rewrite both to describe the post-fix reality (reuse of the EXISTING reads via
`shared/api/`, still "no new producer / no console-bff leg" — the substantive contract claim is unchanged), and
delete the invented exception. Also refresh the `features/dashboards` (line ~147) and
`features/permissions` (line ~141, the promotion precedent) tree notes so the spec is self-consistent for the next
reader.

## Out of Scope

- Any producer/API change, new endpoint, new env var, new error code, or console-bff change.
- Any change to `specs/contracts/console-integration-contract.md` — the § 3.1 parity rows and the § 2.4.4 leg
  table stay literally true by construction (see (D)); the 16-row attestation count is **not** mutated.
- Merging / deleting `features/dashboards` vs `features/iam-overview` (two aggregators over the same three IAM
  legs — real duplication, but a separate design question and a separate task).
- Moving the IAM/finance/ledger **mutation** surfaces to `shared/`.
- Any UI, copy, testid, route, nav or a11y change.

---

# Acceptance Criteria

- [ ] `grep -rnE "from '@/features/" apps/console-web/src/features` returns **0** matches (baseline: 17).
- [ ] No file under `src/features/<A>/` imports from `@/features/<B>/…` for any `B ≠ A`, deep **or** barrel.
- [ ] `src/shared/**` imports nothing from `@/features/` (the promotion must not invert the violation) —
      `grep -rn "@/features/" apps/console-web/src/shared` returns 0.
- [ ] `shared/lib/money.ts` is the **only** definition of `MoneySchema` / `formatMoney` /
      `DEFAULT_CURRENCY_SCALES` in `src/` (the finance-ops ↔ ledger-ops byte-identical duplicate is gone;
      `grep -rn "export const MoneySchema" apps/console-web/src` returns exactly 1).
- [ ] The F5 "no `Number()` / `parseFloat()` / `parseInt()` on `amount`" grep-assertion tests still cover the
      promoted money module (guard reachability preserved, not silently narrowed).
- [ ] `tests/unit/parity-verification.test.ts` passes **unchanged**, still attesting **16** § 3.1 rows;
      `features/accounts` still exports `searchAccounts`, `features/audit` still exports `queryAudit`,
      `features/operators` still exports `listOperators`, `features/dashboards` still exports
      `getOperatorOverview`.
- [ ] `getOperatorOverview` / `getIamOverviewState` / `getFinanceOverviewState` keep their module locations,
      signatures and per-leg degrade semantics (401 → whole-session re-login, 403 → per-card/leg forbidden,
      503/timeout → per-card/leg degrade, independent finance ledger/account legs).
- [ ] `architecture.md` no longer documents a cross-feature import as intentional design; the
      `finance-overview` note and the invented "aggregator exception" comment are corrected.
- [ ] `pnpm tsc --noEmit` → 0 errors.
- [ ] `pnpm next lint` → 0 errors, 0 warnings.
- [ ] `pnpm vitest run` → all green, **test count identical to the pre-change baseline** (or any delta explicitly
      enumerated and justified in the PR body).
- [ ] `pnpm next build` → succeeds.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load
> `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared
> classification. Unknown tags are a Hard Stop per `CLAUDE.md`.

- `projects/platform-console/PROJECT.md` — domain `saas`; traits `multi-tenant`, `integration-heavy`, `audit-heavy`
- `specs/services/console-web/architecture.md` — § Internal Structure Rule, **§ Allowed Dependencies**,
  **§ Forbidden Dependencies**, § Boundary Rules (the rule being enforced), § 3.1 marker-count invariant
- `platform/service-types/frontend-app.md`
- `platform/dependency-rules.md` — module dependency direction
- `platform/refactoring-policy.md` — behaviour-preserving refactor discipline; grep the consumers of anything a
  deletion leaves behind
- `docs/conventions/frontend-ui.md` — the three console UI conventions (untouched by this task, but the promoted
  modules must keep honouring `shared/ui/StatusBadge` tones and `shared/lib/datetime`)

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`
- `.claude/skills/frontend/testing-frontend` (baseline-preserving test discipline)

---

# Related Contracts

- `specs/contracts/console-integration-contract.md`
  - § 2.4.1 accounts · § 2.4.2 audit · § 2.4.3 operators · **§ 2.4.4 composed operator overview (the 3-leg table
    that names `features/accounts` `searchAccounts` / `features/audit` `queryAudit` / `features/operators`
    `listOperators`)**
  - § 2.4.7 finance `account-service` · § 2.4.7.1 finance `ledger-service` · § 2.4.8 erp
  - **§ 3.1 the 16-row IAM parity matrix** — rows 1, 9–11, 16 constrain the design of edge (D); the row set,
    row count and per-row `featureModule` + `clientExport` values MUST NOT change.

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- **Direction of the fix is one-way**: `features/ → shared/` only. A promotion that makes `shared/` import a
  feature is a strictly worse violation (`shared/` → 자체만) and is an automatic reject — assert it with the
  grep in the AC.
- **Re-export ≠ shim-for-convenience here.** The re-exports left at `features/*/api/*.ts` are the
  **contract-pinned public surface** (§ 3.1 `featureModule` + `clientExport`). Each one carries a comment saying
  so, naming the § 3.1 row(s) it preserves and the architecture.md rule that moved the implementation. A future
  reader must not "clean them up" without a contract change.
- **Pure move, no rewrite.** Promoted modules keep their doc headers, log event names, profile constants,
  `AbortController` timeouts, error taxonomies and tolerant-parser posture verbatim. If a diff line changes
  behaviour, it does not belong in this task.
- **Client-safety**: `use-tenant-switch.ts` is a `'use client'` hook and the erp masterdata shapes promoted for
  the org_scope picker must stay zod-only / server-import-free — the whole reason `features/operators` could not
  route through the `erp-ops` barrel. Do not let `next/headers` (or anything that transitively imports it) reach
  those shared modules.
- **Test-mock re-pointing**: tests that `vi.mock()` a module whose *importer* changed must be re-pointed to the
  module the importer now resolves (a mock on a path nobody imports is a silently dead mock, and the test then
  asserts nothing). After re-pointing, confirm the assertion still fails when the production change is reverted.
- Run the INDEX queue-drift check locally before committing (`ready/`, `in-progress/`, `review/` file set must
  equal the INDEX rows; keep the `_(없음)_` marker when a queue empties).

---

# Edge Cases

- **Empty / degraded overview**: with every IAM leg down, the composed overview must still render the full shell
  with three per-card placeholders (unchanged).
- **`403` on one leg only** → that card/leg only is `forbidden`; the others render (unchanged).
- **`401` on any leg** → whole-overview / whole-session re-login, never a per-card degrade (unchanged).
- **finance independent degrade**: a `503` in the ledger leg must not blank the account snapshot and vice versa
  (unchanged) — assert both directions survive the promotion.
- **finance default account missing / `404 ACCOUNT_NOT_FOUND`** → "not set up" / inline not-found states, not a
  degrade (unchanged).
- **Unknown / future enum** (audit `source`, account `status`, txn `status`/`type`, erp master `status`,
  ledger `sourceType`) → generic label, never a parser throw. The tolerant `.passthrough()` shapes must survive
  the move byte-for-byte.
- **F5 money edge values**: negative amounts, scale-0 (KRW) vs scale-2 (USD), unknown currency → scale fallback 0,
  amounts longer than the currency scale. Consolidating two identical implementations must not change one output
  character.
- **erp department picker degrade**: `503` / not-erp-entitled → `deptsFailed` → manual id-entry fallback
  (unchanged); a **retired** department already in `org_scope` still renders as a chip.
- **erp eligibility**: registry `401` → `redirect('/login')`; any other registry failure → `registryDegraded`
  notice, never a crash — semantics must survive the move to `app/(console)/erp/_eligibility.ts` for **all five**
  erp routes (`/erp`, `/erp/masters`, `/erp/approval`, `/erp/orgview`, `/erp/delegation`).
- **Tenant switch from the catalog grid**: still a HARD `window.location.assign` navigation after the mutation
  settles (not `router.push`) — the promotion must not touch that.

---

# Failure Scenarios

- **Silent behaviour drift during the move** — a profile constant, timeout, header, log event or degrade branch
  altered while relocating. Mitigation: promoted modules are moved verbatim; `vitest` count and per-test
  assertions are compared against the pre-change baseline.
- **Inverted violation** — `shared/` ends up importing `@/features/`. Mitigation: explicit AC grep.
- **Contract/parity regression** — a promoted read stops being reachable on its § 3.1 `featureModule` path,
  breaking the 16-row attestation and the ADR-MONO-013 Phase 3 `admin-web`-retirement gate. Mitigation: the
  re-export requirement in (D) + `parity-verification.test.ts` passing **unchanged**.
- **Guard narrowed by the refactor** — the F5 `Number()`-free grep test keeps scanning
  `features/finance-ops/**` after the primitive moved to `shared/lib/money.ts`, so it passes while guarding an
  empty set. Mitigation: explicit AC that the guard covers the promoted module.
- **Dead `vi.mock`** — a test mocks the old feature path while the SUT now imports the shared path; the test goes
  green having exercised the real network-less client instead of the intended fixture. Mitigation: re-point mocks
  and verify each re-pointed test still fails on a reverted production change.
- **Client bundle regression** — a promoted "client-safe" shared module transitively pulls `next/headers`, turning
  a client component into a build error (or dragging server code into the browser bundle). Mitigation: `next
  build` in the AC; keep the erp org_scope shapes zod-only.
- **Barrel cycle** — a feature barrel re-exporting a shared module that re-exports back through the feature.
  Mitigation: re-exports are strictly `feature → shared`, never the reverse.

---

# Test Requirements

- No new behavioural test is required — this is a behaviour-preserving refactor and the **existing suite is the
  oracle**. The bar is: same tests, same count, same assertions, all green.
- Update only what the move mechanically requires: `vi.mock()` target paths and `import` paths in tests whose SUT
  changed its dependency module.
- Extend the F5 money grep-assertion test's scanned roots to include `shared/lib/money.ts` (guard reachability).
- Add a **structural guard test** asserting the rule itself, so this drift cannot silently return: scan
  `src/features/**` for `from '@/features/'` and assert the match set is empty. (This is the cheap mechanical
  guard the rule has never had — the reason 17 edges accumulated.)

---

# Definition of Done

- [ ] All 17 forbidden edges removed; cross-feature grep = 0
- [ ] `shared/` imports no feature (grep = 0)
- [ ] Money primitive de-duplicated into `shared/lib/money.ts`; single definition in `src/`
- [ ] erp eligibility pre-flight relocated to `app/(console)/erp/_eligibility.ts`; all 5 erp routes behave identically
- [ ] `architecture.md` drift-documenting prose corrected; no self-contradiction left for the next reader
- [ ] Structural guard test added
- [ ] `tsc --noEmit` · `next lint` · `vitest` · `next build` all green; vitest count identical to baseline
      (or delta explicitly justified)
- [ ] Ready for review
