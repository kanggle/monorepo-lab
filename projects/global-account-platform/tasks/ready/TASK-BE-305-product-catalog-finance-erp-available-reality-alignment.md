# Task ID

TASK-BE-305

# Title

GAP `ProductCatalog` finance/erp `available: false → true` reality-alignment — platform-console catalog unblock (BE-302 pattern, post-Phase-5/6 stale drift closure)

# Status

ready

# Owner

backend

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

- **depends on**: nothing. finance v1 live (ADR-MONO-013 § D6 Phase 5 COMPLETE 2026-05-19/20 via FIN-BE-005/FE-009) + erp v1 live (Phase 6 COMPLETE 2026-05-20 via ERP-BE-001/MONO-124/ERP-BE-002/FE-010) — the reality this task aligns to is already on `origin/main`. No producer change required; no console consumer change required (console-web already has `/finance` + `/erp` routes from FE-009/FE-010).
- **origin**: surfaced as a stale spec/code drift during TASK-BE-304 + TASK-PC-FE-014 Phase 2 implementation review (2026-05-21). `ProductCatalog.java:51-52` declares `erp`/`finance` with `available: false`; `console-registry-api.md § Product catalog` (line 188-189) declares the same. Both are stale **pre-Phase-5/6** facts that were never updated when Phase 5 (finance bootstrap, ADR-MONO-008 + FIN-BE-001) and Phase 6 (erp bootstrap, ADR-MONO-016 + ERP-BE-001) merged. Current operator UX: `console-web/src/features/catalog/components/ServiceTile.tsx:11-23` renders `available:false` items as **non-interactive "coming soon"** tiles — operators cannot click into the finance/erp catalog tiles even though `/finance` and `/erp` routes are live (FE-009 + FE-010 merged).
- **prerequisite for**: nothing (this completes the catalog reality alignment).
- **spec-first**: spec PR (this file + `console-registry-api.md` + `multi-tenancy.md` if it duplicates the rule + INDEX entry) → impl PR (`ProductCatalog.java` 2-line boolean flip + Javadoc update + 4 test single-test updates + 2 new IT cases) → close chore PR.
- **no ADR** (BE-302 reality-alignment pattern): the architectural decision ("finance/erp are federated domains in the console catalog") was already made by ADR-MONO-013 (`available: false` was a placeholder for pre-bootstrap state, not a decision against availability); ADR-MONO-008 (finance bootstrap) + ADR-MONO-016 (erp bootstrap) made those domains live. This task aligns `ProductCatalog` to the live state — no competing convention to choose between, no architecture decision open.

---

# Goal

`projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/console/ProductCatalog.java:51-52` declares:

```java
new Entry("erp", "Enterprise Resource Planning", false, false, "erp", "/erp"),
new Entry("finance", "Finance Platform", false, false, "finance", "/finance")
```

But finance v1 + erp v1 are both **live** on `origin/main`:

- finance: `ADR-MONO-013 § D6 Phase 5 COMPLETE 2026-05-19/20`, Additive note recorded in `ADR-MONO-013 § 6` (PR #645).
- erp: `ADR-MONO-013 § D6 Phase 6 COMPLETE 2026-05-20`, Additive note recorded in `ADR-MONO-013 § 6` (PR #661).
- Both backends have `apps/` with v1-live services (finance `account-service`, erp `masterdata-service`).
- Console sections live: FE-009 (`/finance`, PR #644) + FE-010 (`/erp`, PR #660).

The stale `available: false` causes a **broken operator UX**: `ServiceTile.tsx` renders both as non-interactive "coming soon" tiles; operators cannot navigate to the finance/erp screens from the console catalog. The `/finance` + `/erp` routes are reachable by direct URL but unreachable via the catalog entry point — the catalog tile is the documented operator-discovery path.

Flip the catalog entries to `available: true`, matching live reality. This is a pure reality-alignment edit (BE-302 pattern): the architectural decisions were already made elsewhere (ADR-MONO-008 + ADR-MONO-016 + the merged Phase 5/6 bootstrap/section work); this task aligns the declaration to what code already does.

# Decision authority (why available:true, why no ADR / decision-gate)

This is a **factual reality-alignment**, not a convention choice — so it follows the **BE-302 G7 / BE-290 / BE-294 discipline** ("live merged code beats a stale declaration that was a placeholder, never a decision"), and requires **no ADR and no decision-gate**:

- **Live merged code (authoritative)** for finance: `projects/finance-platform/PROJECT.md` exists with finance frontmatter, `projects/finance-platform/apps/account-service/` is a live service (Hexagonal Java 21, FIN-BE-001 + follow-ups), `projects/finance-platform/specs/contracts/http/account-api.md` is the published v1 contract, `console-integration-contract.md § 2.4.7` is the published per-domain credential rule.
- **Live merged code (authoritative)** for erp: `projects/erp-platform/PROJECT.md` exists, `projects/erp-platform/apps/masterdata-service/` is the live service (ERP-BE-001 + MONO-124 + ERP-BE-002), `projects/erp-platform/specs/contracts/http/masterdata-api.md` published, `console-integration-contract.md § 2.4.8` published.
- **Console-side console code (authoritative)**: `projects/platform-console/apps/console-web/src/app/(console)/finance/page.tsx` exists (FE-009), `projects/platform-console/apps/console-web/src/app/(console)/erp/page.tsx` exists (FE-010), `features/finance-ops/` + `features/erp-ops/` exist.
- **Same-document corroboration**: `ProductCatalog.java:49-50` Javadoc itself says *"Not bootstrapped — ADR-MONO-008 / future erp ADR. Rendered as 'coming soon' by the console (available:false, tenants:[])"* — but **both ADRs are now ACCEPTED** (008 ACCEPTED 2026-05-19; 016 ACCEPTED 2026-05-19), the Javadoc forward-ref is itself stale. The Javadoc is corrected as part of the impl PR.

The `available: false` declaration is the **sole outlier** vs. an unbroken Phase 5/6 COMPLETE chain → corrected to fact. Per BE-302 / BE-290 / BE-294: a reality-alignment with no competing convention does **not** open an ADR (ADR-MONO-008 + ADR-MONO-016 are the governing decisions; this task is their consequence, not a new decision).

`console-registry-api.md § Product catalog (static, registry-driven)` rule table (line 184-189) carries the same stale narrative ("`false` (not bootstrapped — ADR-MONO-008 / future erp ADR)") and the Response 200 OK example (line 150-163) shows both items with `available: false`. Both are updated as part of the spec PR for consistency.

`projects/global-account-platform/specs/features/multi-tenancy.md "Platform Console Registry"` (per Change Rule § 3 in `console-registry-api.md`) is checked for the same stale narrative and updated to match.

---

# Scope

## In Scope

**Specs (spec PR)**:

- `projects/global-account-platform/specs/contracts/http/console-registry-api.md`:
  - § **Product catalog (static, registry-driven)** table (lines 184-189): flip the `available rule` cell for `erp` and `finance` rows to `true (bootstrapped — V1 live per ADR-MONO-013 § D6 Phase 5/6 COMPLETE 2026-05-19/20)`.
  - § **Response 200 OK** example (lines 150-163): flip the `available` field to `true` for both `erp` and `finance` product items; populate the `tenants` arrays with the platform-scope operator's view (e.g. `["finance"]` for finance, `["erp"]` for erp) consistent with the existing wms/scm sample shape. This is example-only; the runtime rule is the existing tenant-selection rule (§ Tenant selection rule) which already handles per-operator scoping correctly.
  - § **Per-operator profile attributes** (line ~190+, added in TASK-BE-304): the finance item example moves from "always omitted" subset to "populated when set" subset; the rule narrative is unchanged.
  - § **Erp/finance are representable today as `available:false` placeholders** narrative line at ~line 193-194: **remove** this stale statement entirely. The placeholder language was correct pre-Phase-5/6, stale post-Phase-5/6.
- `projects/global-account-platform/specs/features/multi-tenancy.md "Platform Console Registry"` subsection: check for the same stale "available:false placeholder" narrative for finance/erp; if present, update to match (V1 live). If not present, no edit.
- `projects/global-account-platform/tasks/ready/TASK-BE-305-product-catalog-finance-erp-available-reality-alignment.md`: this task md.
- `projects/global-account-platform/tasks/INDEX.md`: ready entry.

**Code (impl PR)**:

- `projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/console/ProductCatalog.java`:
  - Lines 51-52: flip the 3rd argument (`available`) from `false` to `true` for both `erp` and `finance` `Entry` constructions. The other 4 fields (`tenantSlug`, etc.) stay byte-identical — the per-product `tenantSlug` (`"erp"` / `"finance"`) is already correctly set; once `available` is `true`, the existing `ConsoleRegistryUseCase` tenant-selection logic populates `tenants` correctly per operator scope (`isPlatformScope()` → all active tenants intersected with binding; single-tenant operator → own tenant intersected).
  - Class Javadoc (lines 20-22): update the bullet — `{@code erp}/{@code finance} are not bootstrapped — {@code available = false}, no tenant binding` → `{@code erp}/{@code finance} are V1 live (ADR-MONO-013 § D6 Phase 5/6 COMPLETE 2026-05-19/20); both bind to their own tenant slug.`
  - In-line comment (lines 49-50): `// Not bootstrapped — ADR-MONO-008 / future erp ADR. Rendered as "coming soon" by the console (available:false, tenants:[]).` → remove the whole comment block; the entries are now ordinary live products like wms/scm.

**Tests (impl PR)**:

- `projects/global-account-platform/apps/admin-service/src/test/java/com/example/admin/application/console/ConsoleRegistryUseCaseTest.java`:
  - Find any test that asserts `erp.available == false` or `finance.available == false` (likely 1-2 cases in the existing 9-test suite). Update those single assertions to `true`. **Strengthen-only** — no other assertion change.
  - Find any test that asserts `erp.tenants` or `finance.tenants` is empty/`List.of()`. Update those single assertions to expect the populated tenants array per operator scope (single-tenant operator: `["<own>"]` if own tenant slug matches; platform-scope: all active tenants intersected with the product's tenant binding).
- `projects/global-account-platform/apps/admin-service/src/test/java/com/example/admin/integration/ConsoleRegistryIntegrationTest.java`:
  - Find the existing IT case that exercises the full registry response with the platform-scope operator. Update the body assertion(s) on `erp.available` / `finance.available` from `false` to `true`.
  - Add 1 new IT case: **single-tenant operator with tenant=finance** sees `finance.available=true, tenants=["finance"]` and `erp.available=true, tenants=[]` (own tenant doesn't match erp binding). Mirrors the existing single-tenant operator pattern.
  - Add 1 new IT case: **single-tenant operator with tenant=erp** sees the inverse — `erp.available=true, tenants=["erp"]` and `finance.available=true, tenants=[]`.
  - These 2 new IT cases regression-guard the per-operator `available` × `tenants` interaction now that `available` is `true`.

## Out of Scope

- **Adding new product keys** (e.g. fan-platform as a catalog product). The 5 product catalog membership (`gap` + `wms` + `scm` + `erp` + `finance`) stays fixed per ADR-MONO-013 federated domains. This task only flips the `available` flag for 2 of the 5.
- **Changing `tenantSlug`** for finance/erp entries. Both already correctly carry `"finance"` / `"erp"` (the slugs the Phase 5/6 bootstrap registered). No tenant-binding shape change.
- **Adding `operatorContext` population for non-finance products** (e.g. erp `defaultDepartmentId`). The TASK-BE-304 schema reserves it for future per-operator per-product attributes; this task is not the activation of any of them — finance keeps its `operatorContext` emission rule unchanged.
- **Console-web `ServiceTile.tsx` change**. The component already handles `available:true` (interactive tile) and `available:false` (non-interactive "coming soon") — once the producer flips, the consumer renders correctly with **zero code change**. AC-6 verifies `console-web/` byte-unchanged.
- **`/finance` `/erp` route or `features/finance-ops/` `features/erp-ops/` changes** — already live (FE-009/FE-010), byte-unchanged.
- **5 producer specs outside admin-service** (`wms-platform/`, `scm-platform/`, `finance-platform/`, `erp-platform/`, `fan-platform/`, `ecommerce-platform/`). Byte-unchanged. AC-7.
- **ADR amendment** (ADR-MONO-008 / ADR-MONO-013 / ADR-MONO-016 / ADR-MONO-017). All byte-unchanged — the ADR decisions were already made, this is their consequence (BE-302 reality-alignment pattern).
- **`operatorContext.defaultAccountId` populated example in registry response** — TASK-BE-304 already added that; this task does not regress or modify it.

# Acceptance Criteria

- **AC-1 (surgical impl)**: `git diff origin/main -- projects/global-account-platform/apps/admin-service/src/main/java/com/example/admin/application/console/ProductCatalog.java` shows exactly **2 boolean literals flipped** (`false → true` on the 3rd arg of erp + finance Entry constructions) + Javadoc/comment narrative update. No structural change. No additional Entry, no removed Entry; 5 entries preserved verbatim.
- **AC-2 (operator UX unblock)**: post-merge, `console-web/src/features/catalog/components/ServiceTile.tsx` (byte-unchanged) renders finance/erp tiles as **interactive** (clickable) for any operator whose `tenants` array is non-empty for those products. Verified by the new IT case asserting `available:true` + populated `tenants` for the relevant operator scope; the FE behavior is the consequence of the producer flag (no FE code path change required).
- **AC-3 (existing tests preserved, single-assertion update)**: existing `ConsoleRegistryUseCaseTest` + `ConsoleRegistryIntegrationTest` cases that explicitly asserted `erp/finance.available == false` are updated to `true` (1-2 cases). All other existing assertions byte-identical. **Strengthen-only** — no test weakened.
- **AC-4 (multi-tenant isolation preserved)**: a single-tenant operator with tenant=`wms` continues to see `finance.tenants = []` and `erp.tenants = []` (own tenant doesn't match either binding). Asserted in the existing single-tenant operator IT case (which already runs; the only delta is the `available` flag changing from false to true — `tenants: []` for non-matching products is preserved by the existing tenant-selection rule). No cross-tenant leak introduced.
- **AC-5 (D4 HARD INVARIANT preserved)**: ADR-MONO-017 D4 byte-unchanged (this task does not touch console-bff, the credential dispatch, or any operator-overview composition). `git diff --stat origin/main -- projects/platform-console/apps/console-bff/` = empty.
- **AC-6 (zero retrofit outside admin-service producer + admin-service test)**: `git diff --stat origin/main -- projects/{platform-console,wms-platform,scm-platform,finance-platform,erp-platform,fan-platform,ecommerce-platform}/` = **empty** in impl PR (no consumer change required — the producer flip is the entire trigger). `git diff --stat origin/main -- projects/global-account-platform/apps/` shows only the 1 file in `admin-service/src/main/` + 1-2 files in `admin-service/src/test/`.
- **AC-7 (spec sync — Javadoc / comment / Change Rule)**: `ProductCatalog.java` class Javadoc + inline comment block both updated; no stale "not bootstrapped" / "coming soon by the console" narrative remains in production code. `console-registry-api.md § Product catalog` table + § Response 200 OK example both updated to `true`. `multi-tenancy.md` checked for the same narrative; updated if present.
- **AC-8 (no ADR)**: `git diff --stat origin/main -- docs/adr/` = **empty** (no ADR amendment per BE-302 reality-alignment pattern). The architectural decisions (ADR-MONO-008 finance bootstrap, ADR-MONO-016 erp bootstrap, ADR-MONO-013 § D6 Phase 5/6) are the governing layers; this task is their consequence.
- **AC-9 (CI green)**: self-CI 20/20 GREEN at impl-PR merge time. `Integration (global-account-platform, Testcontainers)` PASS (existing IT updated + 2 new IT cases). `Build & Test (JDK 21)` PASS. **BE-303 3-dim verified at close chore** per [`CLAUDE.md § Task Rules`](../../../../CLAUDE.md).

# Related Specs

- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` § Product catalog (rule table) + § Response 200 OK (example) + § Per-operator profile attributes (cross-reference, unchanged in this task).
- `projects/global-account-platform/specs/features/multi-tenancy.md "Platform Console Registry"` (subsection — checked for sync, updated if it carries the same narrative).
- `docs/adr/ADR-MONO-013-platform-console-foundation.md § D6 Phase 5/6 + § 6 Additive notes` (governing decisions for finance/erp v1 live — byte-unchanged in this task; this task is their consequence).
- `docs/adr/ADR-MONO-008-finance-platform-bootstrap.md` (finance bootstrap ADR — byte-unchanged in this task).
- `docs/adr/ADR-MONO-016-erp-platform-bootstrap.md` (erp bootstrap ADR — byte-unchanged in this task).

# Related Contracts

- `projects/global-account-platform/specs/contracts/http/console-registry-api.md` — the producer contract being updated (rule + example, narrative).
- `projects/platform-console/specs/contracts/console-integration-contract.md § 2.2 Item shape` — consumer-side item shape (cross-reference, **byte-unchanged**; consumer's interpretation of `available` is unchanged — `false → coming soon, true → catalog tile interactive`, both already implemented in FE).

# Edge Cases

- **Operator's tenant slug matches neither finance nor erp** (e.g. `tenant_id = "wms"`): they see `finance.available=true, tenants=[]` and `erp.available=true, tenants=[]`. The `available:true` advertises the product exists but the operator has no tenant scope for it; the console renders the tile as **interactive but with no selectable tenant** (FE behavior already covers this case via the existing tenant-selection UI). AC-4 covers this.
- **Operator is platform-scope (`tenant_id = '*'`)**: they see all 5 products with all active tenants intersected with the product's binding. finance binds to `"finance"`, erp binds to `"erp"` — if those tenants exist in `tenants` table (account-service owned) and are `ACTIVE`, they appear in the respective `tenants` arrays.
- **Finance/erp tenant rows not registered yet** in `tenants` table (theoretical bootstrap edge): the existing `selectableTenants(...)` rule already handles this — `bound = activeTenants.contains(entry.tenantSlug()) ? List.of(entry.tenantSlug()) : List.of()`. If the tenant slug isn't registered + ACTIVE, `tenants: []` even with `available: true`. The console renders an "interactive but no tenants" state — operator sees the tile but cannot select.
- **The 2 new IT cases reuse the existing single-tenant operator seed pattern**. If the existing IT only seeds a `wms` single-tenant operator, the new tests seed `finance` and `erp` single-tenant operators via `@Sql` or direct JDBC insert (mirror the existing seed shape).
- **`operatorContext.defaultAccountId` interaction**: when finance is `available:true` and the operator has `finance_default_account_id` set (TASK-BE-304 emission rule), the finance item carries **both** `available:true` AND `operatorContext.defaultAccountId`. The console FE renders the tile interactive AND the BFF dashboard route uses the defaultAccountId. Two orthogonal flags; no interaction issue.

# Failure Scenarios

- **The impl PR flips additional booleans (e.g. some test fixture's `available:false` for a different product)** → scope violation. **Reject in review** — AC-1 is exactly 2 boolean literal changes on `ProductCatalog.java:51-52`.
- **An IT or unit test weakens an existing assertion to "accommodate" the flag flip** (e.g. `assertThat(item.available()).isIn(true, false)`) → **strengthen-only violation, reject**. The flip is from `false → true`, and the new expected value is `true` (narrow assertion).
- **A reviewer requests an ADR for the flip** → **reject** per § Decision authority; BE-302 / BE-290 / BE-294 precedent (reality-alignment with no competing convention does not open an ADR). The architectural decisions are ADR-MONO-008 + ADR-MONO-016 + ADR-MONO-013 § D6 Phase 5/6 — all already ACCEPTED.
- **A reviewer requests adding a "deprecated" marker for the old `available:false` declaration** → **reject** — the declaration was a placeholder, not a public API contract that needs migration; the producer contract (`console-registry-api.md`) just declares the live state. No deprecation marker.
- **`tenants` arrays for finance/erp are populated cross-tenant** for a single-tenant operator (regression — see AC-4). **Reject** — the existing `selectableTenants(...)` rule preserves single-tenant isolation; if a reviewer's change accidentally widens scope, AC-4 IT catches it.
- **`console-web` `ServiceTile.tsx` or `features/catalog/` modified in this PR** → AC-6 fail (no consumer change is required; the producer flip is sufficient). **Reject in review.**
- **5 producer specs outside admin-service modified** → AC-6 fail. **Reject.**
- **A test (existing or new) hard-codes `tenants: ["finance", "erp"]` for a non-platform-scope operator** → cross-tenant leak. The selectable rule restricts per-operator scope — only platform-scope sees multiple tenants per product (intersected with active registered slugs). **Reject.**

# Verification

1. Spec PR diff: exactly 2-3 files (`console-registry-api.md` + task md + optionally `multi-tenancy.md`) + 1 INDEX entry. No production code, no test code.
2. Impl PR diff: 1 production code file (`ProductCatalog.java`) + 1-2 test files (unit + IT) + 0 spec edits + 0 INDEX/task md edits.
3. `./gradlew :admin-service:test` → green; updated 1-2 unit cases pass; existing tests pass.
4. `./gradlew :admin-service:integrationTest` → green; 2 new IT cases pass; updated existing IT case passes.
5. Self-CI 20/20 GREEN at impl-PR merge time. BE-303 3-dim verified at close chore start.
6. Post-merge, console-web operator login: `/dashboards/overview` catalog renders finance + erp tiles **as interactive** (clickable, not "coming soon"). Operator can click into `/finance` or `/erp` from the catalog.

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical 2-line boolean flip + Javadoc/comment update + single-assertion test updates + 2 new IT cases following the existing single-tenant operator seed pattern; BE-302 reality-alignment, no judgement beyond the already-decided fact) / 리뷰=Opus 4.7 (inline self-review + AC-1 surgical-diff + AC-3 strengthen-only + AC-4 multi-tenant isolation + AC-6 zero retrofit + BE-303 3-dim).
