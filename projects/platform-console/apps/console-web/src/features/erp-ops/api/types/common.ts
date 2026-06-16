import { z } from 'zod';

/**
 * erp-ops shared types — common building blocks (TASK-PC-FE-109 split of the
 * former `api/types.ts` god-file). Holds the cross-entity primitives every
 * masters / read-model module reuses: the E2 `EffectivePeriod`, the honest
 * tolerant enum vocabularies, the `Audit` envelope, the success-meta shapes
 * (`ErpMeta` / `ReadModelMeta`), the shared list/detail query params + page
 * bounds, the tolerant `labelForUnknownEnum` / `isRetired` helpers, and the
 * uniform `ErpRetireBodySchema`. Feature-internal; re-exported verbatim via
 * the `types` barrel. 0 behavior change.
 *
 * E2 EFFECTIVE-DATING invariant (§ 2.4.8): every master detail surfaces
 * `effectivePeriod` as a REQUIRED first-class field. `effectiveTo: null`
 * (open-ended / active) vs `effectiveTo: <past>` (retired) MUST both render —
 * retired rows visually distinct but NEVER hidden / filtered.
 *
 * TOLERANCE invariant (§ 2.4.8): every read shape is permissive — unknown /
 * future enum values parse to a generic string and NEVER throw.
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

/** Producer master `status` enum surfaced HONESTLY (a `RETIRED`
 *  master is shown as such, never hidden — § 2.4.8). Stored as a
 *  free string so unknown / future values render generically (no
 *  parser throw, tolerant-parser discipline). */
export const KNOWN_MASTER_STATUSES = ['ACTIVE', 'RETIRED'] as const;
export type KnownMasterStatus = (typeof KNOWN_MASTER_STATUSES)[number];

/** Producer employee `employmentStatus` enum surfaced HONESTLY (a
 *  `SEPARATED` employee is shown as such, never filtered out —
 *  § 2.4.8). Free string for tolerance. */
export const KNOWN_EMPLOYMENT_STATUSES = [
  'EMPLOYED',
  'ON_LEAVE',
  'SEPARATED',
] as const;
export type KnownEmploymentStatus =
  (typeof KNOWN_EMPLOYMENT_STATUSES)[number];

/** Producer business-partner `partnerType` enum. Free string for
 *  tolerance — unknown / future values render with a generic
 *  label. */
export const KNOWN_PARTNER_TYPES = [
  'CUSTOMER',
  'SUPPLIER',
  'BOTH',
] as const;
export type KnownPartnerType = (typeof KNOWN_PARTNER_TYPES)[number];

// ---------------------------------------------------------------------------
// Audit — append-only audit (E8) surfaced on detail responses.
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

// ---------------------------------------------------------------------------
// erp success envelope shapes — flat (same wire as scm/finance,
// distinct producer / own parser).
// ---------------------------------------------------------------------------

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
// read-model meta — extends the base meta with `warning` (required,
// always present) and optional `unresolved` (only when ≥ 1 reference
// is not yet projected). Both fields MUST be tolerated by the consumer
// regardless of presence (eventually-consistent, E5).
// ---------------------------------------------------------------------------

/** erp read-model success-meta (TASK-PC-FE-049 — `read-model-api.md`):
 *  extends `ErpMetaSchema` with the required `warning` (always
 *  "Eventually-consistent read-model") and the optional `unresolved`
 *  array (field names whose master event has not yet been consumed).
 *  Schema is TOLERANT: unknown fields pass through; `warning` is
 *  optional at the schema level so an absent value does not throw. */
export const ReadModelMetaSchema = z
  .object({
    timestamp: z.string().optional(),
    page: z.number().int().nonnegative().optional(),
    size: z.number().int().positive().optional(),
    totalElements: z.number().int().nonnegative().optional(),
    warning: z.string().optional(),
    unresolved: z.array(z.string()).optional(),
  })
  .passthrough();
export type ReadModelMeta = z.infer<typeof ReadModelMetaSchema>;

// ---------------------------------------------------------------------------
// query params
// ---------------------------------------------------------------------------

export const ERP_DEFAULT_PAGE_SIZE = 20;
export const ERP_MAX_PAGE_SIZE = 100;

/** Common query params for every erp list endpoint. `asOf` is the
 *  E3 first-class point-in-time read — when supplied it threads
 *  through to the producer verbatim and the producer returns the
 *  state-at-that-instant (NOT the current state). */
export interface ErpListQueryParams {
  /** E3 — ISO-8601 DATE for point-in-time read. */
  asOf?: string;
  /** Optional producer filter — when omitted producer default
   *  applies (typically active = true). The console exposes this
   *  honestly so retired masters can be browsed. */
  active?: boolean;
  page?: number;
  size?: number;
  /** Master-specific filters — tolerated as a passthrough record
   *  so per-master query params (`parentId` / `departmentId` /
   *  `costCenterId` / `partnerType`) can be supplied without
   *  proliferating per-master interfaces. */
  filters?: Record<string, string>;
}

/** Detail query — only `asOf` is producer-defined. */
export interface ErpDetailQueryParams {
  asOf?: string;
}

// ---------------------------------------------------------------------------
// labelForUnknown — tolerant rendering helper for master / employment
// status enums (used by the components; co-located with the schemas
// because the known-enum sets live here).
// ---------------------------------------------------------------------------

export function labelForUnknownEnum<T extends string>(
  value: string | undefined | null,
  known: readonly T[],
): string {
  if (!value) return '—';
  return (known as readonly string[]).includes(value)
    ? value
    : `${value} (unknown)`;
}

/** True if `effectiveTo` is in the past relative to `now` (default
 *  = `new Date()`). Used by E2 rendering to mark retired rows
 *  visually distinct without HIDING them. */
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
// Shared retire body — identical across every master (TASK-PC-FE-048).
// `reason` rides in the body only on retire (the producer's only reason
// slot for these masters). Every mutation carries an `Idempotency-Key`.
// ---------------------------------------------------------------------------

/** Shared retire body — identical across every master. */
export const ErpRetireBodySchema = z.object({
  reason: z.string().min(1).max(256),
  idempotencyKey: z.string().min(1),
});
