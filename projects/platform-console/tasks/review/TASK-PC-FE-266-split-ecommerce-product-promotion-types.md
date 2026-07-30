# Task ID

TASK-PC-FE-266

# Title

console-web split ecommerce-ops `api/types.ts` into `product-types.ts`/`promotion-types.ts`

# Status

review

# Owner

frontend

# Task Tags

- code

---

# Required Sections (must exist)

- Goal
- Scope
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

The last of 5 findings from the fresh `console-web` naming-convention re-scan (2026-07-30) that produced TASK-PC-FE-263/264/265, finding #5 — lowest urgency, pure rename/move. Within `features/ecommerce-ops`, 7 other sub-domains already split their types into a dedicated `<domain>-types.ts` file (`order-types.ts`, `seller-types.ts`, `settlement-types.ts`, `shipping-types.ts`, `user-types.ts`, `image-types.ts`, `notification-types.ts`), but **products** and **promotions** are still bundled together in the generic `api/types.ts` (365 lines, two clearly-delineated sections separated by an explicit `// PROMOTIONS` comment divider at line 227 — lines 1–225 are products, 227–365 are promotions).

Pre-implementation import audit (22 importers, grep-verified) found every single one is either exclusively product-scoped or exclusively promotion-scoped — none imports both groups from `'../api/types'` in production code, so this is a pure file-split + import-path rename, not a symbol reshuffle. The one exception is a test file (`tests/unit/ecommerce-status-tone.test.ts`) which imports both `productStatusTone` and `promotionStatusTone` in a single statement and needs that one import split into two.

---

# Scope

## In Scope

1. **New `features/ecommerce-ops/api/product-types.ts`** — everything currently in `api/types.ts` lines 1–225 (products section), moved verbatim: `PRODUCT_STATUS_VALUES`, `ProductStatus`, `productStatusTone`, `ProductSummarySchema`/`ProductSummary`, `ProductListSchema`/`ProductList`, `VariantSchema`/`Variant`, `ProductImageSchema`/`ProductImage`, `ProductDetailSchema`/`ProductDetail`, `RegisterProductResponseSchema`/`RegisterProductResponse`, `AdjustStockResponseSchema`/`AdjustStockResponse`, `RegisterVariantBodySchema`/`RegisterVariantBody`, `RegisterProductBodySchema`/`RegisterProductBody`, `UpdateProductBodySchema`/`UpdateProductBody`, `AddVariantBodySchema`/`AddVariantBody`, `UpdateVariantBodySchema`/`UpdateVariantBody`, `AdjustStockBodySchema`/`AdjustStockBody`, `ProductAreaSummarySchema`/`ProductAreaSummary`, `PRODUCT_DEFAULT_PAGE_SIZE`, `PRODUCT_MAX_PAGE_SIZE`, `ProductListParams`.

2. **New `features/ecommerce-ops/api/promotion-types.ts`** — everything currently in `api/types.ts` lines 227–365 (promotions section), moved verbatim: `PROMOTION_STATUS_VALUES`, `PromotionStatus`, `promotionStatusTone`, `DISCOUNT_TYPE_VALUES`, `DiscountType`, `PromotionSummarySchema`/`PromotionSummary`, `PromotionListSchema`/`PromotionList`, `PromotionDetailSchema`/`PromotionDetail`, `PromotionMutationResponseSchema`/`PromotionMutationResponse`, `IssueCouponResponseSchema`/`IssueCouponResponse`, `CreatePromotionBodySchema`/`CreatePromotionBody`, `UpdatePromotionBodySchema`/`UpdatePromotionBody`, `IssueCouponBodySchema`/`IssueCouponBody`, `PromotionAreaSummarySchema`/`PromotionAreaSummary`, `PROMOTION_DEFAULT_PAGE_SIZE`, `PROMOTION_MAX_PAGE_SIZE`, `PromotionListParams`.

3. **Delete `features/ecommerce-ops/api/types.ts`** — no re-export shim (this task's goal is to eliminate the monolith, not paper over it — matches TASK-PC-FE-260's item 4 precedent for `erp-ops/api/types.ts`).

4. **Update all 21 production importers** (grep-verified list below) to import from `'../api/product-types'`/`'./product-types'` or `'../api/promotion-types'`/`'./promotion-types'` (relative depth matches each file's existing `'../api/types'` vs `'./types'` usage) instead of `'../api/types'`/`'./types'`:
   - Product-only (import path → `product-types`): `hooks/use-ecommerce-products.ts`, `hooks/use-product-form.ts`, `hooks/use-variant-editor.ts`, `api/products-api.ts`, `api/products-state.ts`, `components/ProductForm.tsx`, `components/ProductsScreen.tsx`, `components/ProductsTable.tsx`, `components/ProductDetail.tsx`, `components/VariantTable.tsx`, `components/StockAdjustDialog.tsx`, `components/VariantEditor.tsx`.
   - Promotion-only (import path → `promotion-types`): `hooks/use-ecommerce-promotions.ts`, `hooks/use-promotion-form.ts`, `api/promotions-api.ts`, `api/promotions-state.ts`, `components/PromotionDetail.tsx`, `components/PromotionForm.tsx`, `components/PromotionDetailFields.tsx`, `components/PromotionsScreen.tsx`, `components/PromotionFormFields.tsx`, `components/PromotionsTable.tsx`.

5. **Update the one test file needing a genuine two-way split**: `tests/unit/ecommerce-status-tone.test.ts` — its single `import { productStatusTone, promotionStatusTone } from '@/features/ecommerce-ops/api/types';` becomes two imports, one from `@/features/ecommerce-ops/api/product-types` and one from `@/features/ecommerce-ops/api/promotion-types`. No assertion values change.

## Out of Scope

- Any reorganization of the other 7 already-split `<domain>-types.ts` files.
- Any behavior, schema, or validation change — every zod schema, type, and function body must be byte-identical after the move, only file location and import paths change.
- The `periodStatusTone` collision noted during TASK-PC-FE-265's investigation (`shared/api/ledger-types/period.ts` vs `features/ecommerce-ops/api/settlement-types.ts`) — not part of the original 5-finding re-scan, a separate future candidate.

---

# Acceptance Criteria

- [ ] `features/ecommerce-ops/api/product-types.ts` exists with all product-section exports; `features/ecommerce-ops/api/promotion-types.ts` exists with all promotion-section exports.
- [ ] `features/ecommerce-ops/api/types.ts` no longer exists.
- [ ] All 21 production importers updated to the correct new path; `tests/unit/ecommerce-status-tone.test.ts` updated with a genuine two-import split.
- [ ] Grep for `from '../api/types'` / `from './types'` / `from '@/features/ecommerce-ops/api/types'` across `apps/console-web/src` and `apps/console-web/tests` returns zero matches after the split (scoped to `ecommerce-ops` — do not false-positive on an unrelated feature's own `'../api/types'`, which several other features also have; verify by directory).
- [ ] `tsc --noEmit` passes with zero new errors.
- [ ] `pnpm lint` passes with zero new errors.
- [ ] Full `console-web` unit test suite (vitest) passes unchanged — same pass count, zero assertion values modified.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md`, then load `rules/common.md` plus any `rules/domains/<domain>.md` and `rules/traits/<trait>.md` matching the declared classification (platform-console: domain=saas, traits=[multi-tenant, integration-heavy, audit-heavy]). Unknown tags are a Hard Stop per `CLAUDE.md`.

- `platform/refactoring-policy.md` § Allowed Refactoring Categories → **Rename** (Low risk); this task performs a file split/move (Rename category), no behavior change.
- `projects/platform-console/specs/services/console-web/architecture.md` — Layered by Feature architecture; this task aligns `ecommerce-ops` to the same per-sub-domain `<domain>-types.ts` shape its 7 sibling sub-domains already use.
- TASK-PC-FE-260 item 4 (`erp-ops/api/types.ts` → `types/index.ts` folder-barrel) — the direct precedent for "delete the monolith `types.ts`, no re-export shim, update every importer."

# Related Skills

- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`

---

# Related Contracts

None — no API or event contract touched; pure internal file-split with zero schema/behavior change.

---

# Target App

- `apps/console-web`

---

# Implementation Notes

- Move code verbatim — copy each section's exact text into its new file, do not "clean up" or reformat while moving (a rename task changes location, not content).
- Every one of the 21 production importers is single-domain (grep-confirmed during task authoring — no file imports both product and promotion symbols from `'../api/types'`), so each importer needs only its import specifier string changed, not its import list restructured.
- The ONE exception is the test file `tests/unit/ecommerce-status-tone.test.ts`, which needs its single combined import split into two.
- Grep BEFORE starting and AFTER finishing for the old paths, scoped to avoid false-positives from unrelated features that also have their own `'../api/types'` (e.g. `finance-ops/api/types.ts`, `ledger-ops/api/types/`) — always scope the grep to files under `features/ecommerce-ops` and to test files that reference `ecommerce-ops/api/types` specifically.

---

# Edge Cases

- `Variant`/`VariantSchema` conceptually belongs to products (variant of a product) — confirmed via `RegisterProductBodySchema`'s `variants: z.array(RegisterVariantBodySchema)` field and every importer of `Variant` (`ProductDetail.tsx`, `VariantTable.tsx`, `StockAdjustDialog.tsx`, `VariantEditor.tsx`) being product-side components — goes to `product-types.ts`, not a third file.
- `DISCOUNT_TYPE_VALUES`/`DiscountType` belongs to promotions (`discountType` field of `CreatePromotionBodySchema`) — goes to `promotion-types.ts`.

---

# Failure Scenarios

- `tsc --noEmit` fails after the split → fix the missed import path, do not leave `api/types.ts` around as a fallback re-export.
- `pnpm lint` flags an import-order violation from the new relative paths → fix inline, in-scope for a rename task.
- Vitest suite fails post-split → indicates a missed import path, not a false positive; revert and re-diagnose (per `platform/refactoring-policy.md` Prohibited: "Refactoring production code and test code in the same change" — only the import-path split is being made in the test file, no assertion changes).

---

# Test Requirements

- No new tests required — pure move/split with zero behavior change; existing test coverage (in particular `tests/unit/ecommerce-status-tone.test.ts`) is the verification mechanism and must pass unmodified in its assertions.

---

# Definition of Done

- [ ] All 5 scope items completed
- [ ] `tsc --noEmit` clean
- [ ] `pnpm lint` clean
- [ ] Full `console-web` vitest suite passing, same pass count, zero assertion values modified
- [ ] Ready for review
