import { z } from 'zod';

/**
 * Shared **client-safe** erp `masterdata-service` DEPARTMENT read contract —
 * the department wire shapes + the E2 effective-period primitives they compose
 * (TASK-PC-FE-010/046/109 producer binding / TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * Two features consume the erp department read:
 *   - `features/erp-ops` — the `/erp/masters` department list + detail + the
 *     TASK-PC-FE-046 write pilot;
 *   - `features/operators` — the TASK-PC-FE-050 per-operator **org_scope**
 *     dialog, whose department picker reads the SAME
 *     `/api/erp/masterdata/departments` proxy to offer subtree roots.
 *
 * `features/operators` previously imported these straight out of
 * `@/features/erp-ops/api/types`, which `architecture.md`
 * § Forbidden Dependencies bars —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * and — decisively — the `erp-ops` **barrel** was never an option for that
 * pair: it re-exports server-only state that would drag `next/headers` into
 * the client bundle, which is exactly why the old code deep-imported. So the
 * promotion remedy is the only correct fix here. `features/erp-ops` re-exports
 * everything below through its own `api/types` barrel, so every erp-internal
 * consumer is unchanged.
 *
 * ── CLIENT-SAFE BY CONSTRUCTION (do not break this) ──
 * zod only. **No** `next/headers`, no cookies, no fetch, no server-only
 * import — this module is imported from `'use client'` hooks
 * (`features/operators/hooks/use-operator-assignments.ts`,
 * `use-org-scope-form.ts`). Adding a server-coupled import here would turn
 * those client components into a build error.
 *
 * Authoritative producer contract (do NOT redefine — consume read-only):
 *   `erp-platform/specs/contracts/http/masterdata-api.md` § Department
 *   (`GET /api/erp/masterdata/departments[/{id}]`, `?asOf=` effective-dated).
 * Consumer obligation: `console-integration-contract.md` § 2.4.8.
 *
 * E2 EFFECTIVE-DATING invariant (§ 2.4.8): `effectivePeriod` is a REQUIRED
 * first-class field. `effectiveTo: null` (open-ended / active) vs
 * `effectiveTo: <past>` (retired) MUST both render — retired rows visually
 * distinct but NEVER hidden / filtered.
 *
 * TOLERANCE invariant (§ 2.4.8): the shapes are permissive — unknown / future
 * `status` values parse to a generic string and NEVER throw.
 */

// ---------------------------------------------------------------------------
// EffectivePeriod — E2 first-class field on every master detail.
// ---------------------------------------------------------------------------

/**
 * `EffectivePeriod` — `{ effectiveFrom, effectiveTo }`. `effectiveTo`
 * may be `null` (open-ended / active). Both fields are ISO-8601
 * DATE strings (the producer wire shape from `masterdata-api.md` §
 * Common shapes). The consumer surfaces them HONESTLY — retired
 * rows (`effectiveTo` in the past) are rendered visually distinct
 * but NOT hidden (E2 honesty).
 */
export const EffectivePeriodSchema = z.object({
  effectiveFrom: z.string(),
  effectiveTo: z.string().nullable(),
});
export type EffectivePeriod = z.infer<typeof EffectivePeriodSchema>;

/**
 * E2 retired predicate — `true` when `effectiveTo` is set AND in the past.
 * Never throws on a partial-precision / malformed producer value (tolerant).
 * Used to render retired masters distinctly (never to hide them) and to
 * filter the org_scope picker's SELECTABLE options (a retired department id
 * already present in an operator's `org_scope` is still rendered as a chip).
 */
export function isRetired(
  period: EffectivePeriod,
  now: Date = new Date(),
): boolean {
  if (!period.effectiveTo) return false;
  // String comparison on ISO-8601 DATEs is monotonic — no Date()
  // parse needed when both sides are ISO-8601, but we keep Date()
  // to be robust against partial-precision producer values.
  try {
    return new Date(period.effectiveTo).getTime() < now.getTime();
  } catch {
    return false;
  }
}

// ---------------------------------------------------------------------------
// audit envelope + erp success meta (flat — same wire as scm/finance,
// distinct producer / own parser).
// ---------------------------------------------------------------------------

export const AuditSchema = z
  .object({
    createdAt: z.string().optional(),
    createdBy: z.string().optional(),
    updatedAt: z.string().optional(),
    updatedBy: z.string().optional(),
  })
  .partial()
  .passthrough();
export type Audit = z.infer<typeof AuditSchema>;

/** erp success-meta: `{ timestamp, page?, size?, totalElements? }`.
 *  Producer-specific — kept distinct from finance / scm meta even
 *  though byte-identical (each domain owns its own parser). */
export const ErpMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
  })
  .passthrough();
export type ErpMeta = z.infer<typeof ErpMetaSchema>;

// ---------------------------------------------------------------------------
// Department read shapes
//   GET /api/erp/masterdata/departments (?asOf=&active=&parentId=&page=&size=)
//   GET /api/erp/masterdata/departments/{id} (?asOf=)
// ---------------------------------------------------------------------------

export const DepartmentSchema = z
  .object({
    id: z.string(),
    code: z.string(),
    name: z.string(),
    parentId: z.string().nullable().optional(),
    // tolerated as free string (unknown → generic label).
    status: z.string(),
    effectivePeriod: EffectivePeriodSchema,
    audit: AuditSchema.optional(),
  })
  .passthrough();
export type Department = z.infer<typeof DepartmentSchema>;

export const DepartmentListResponseSchema = z.object({
  data: z.array(DepartmentSchema),
  meta: ErpMetaSchema,
});
export type DepartmentListResponse = z.infer<
  typeof DepartmentListResponseSchema
>;

export const DepartmentDetailResponseSchema = z.object({
  data: DepartmentSchema,
  meta: ErpMetaSchema,
});
export type DepartmentDetailResponse = z.infer<
  typeof DepartmentDetailResponseSchema
>;
