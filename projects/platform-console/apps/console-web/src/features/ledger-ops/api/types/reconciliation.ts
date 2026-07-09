import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';
import { MoneySchema, LedgerMetaSchema, type Money } from './money';

// ---------------------------------------------------------------------------
// Reconciliation discrepancies — reconciliation-api.md § 4 (queue), § 5 (detail)
//   GET /api/finance/ledger/reconciliation/discrepancies[/{id}]
// ---------------------------------------------------------------------------

/** Producer discrepancy types surfaced HONESTLY (UNMATCHED_EXTERNAL /
 *  UNMATCHED_INTERNAL / AMOUNT_MISMATCH shown as-is — § 2.4.7.1; the
 *  11th-increment FX-difference `AMOUNT_MISMATCH` carries both
 *  `externalRef` and `journalEntryId`). Free string for tolerance —
 *  unknown / future values render with a generic label. */
export const KNOWN_DISCREPANCY_TYPES = [
  'UNMATCHED_EXTERNAL',
  'UNMATCHED_INTERNAL',
  'AMOUNT_MISMATCH',
] as const;
export type KnownDiscrepancyType = (typeof KNOWN_DISCREPANCY_TYPES)[number];

export const KNOWN_DISCREPANCY_STATUSES = ['OPEN', 'RESOLVED'] as const;
export type KnownDiscrepancyStatus =
  (typeof KNOWN_DISCREPANCY_STATUSES)[number];

/**
 * Discrepancy status → shared semantic {@link StatusTone} (rendered via the
 * shared `<StatusBadge>` — TASK-PC-FE-159). OPEN is an unreconciled item
 * needing attention (warning); RESOLVED is cleared (success). An unknown/future
 * status → `neutral` (tolerant — never a throw).
 */
const DISCREPANCY_STATUS_TONE: Record<KnownDiscrepancyStatus, StatusTone> = {
  OPEN: 'warning',
  RESOLVED: 'success',
};

export function discrepancyStatusTone(status: string): StatusTone {
  return DISCREPANCY_STATUS_TONE[status as KnownDiscrepancyStatus] ?? 'neutral';
}

/** The resolution sub-object — present only when `status === 'RESOLVED'`
 *  (reconciliation-api.md § 5). The `resolutionType` is surfaced honestly
 *  (free string for tolerance). */
export const DiscrepancyResolutionSchema = z
  .object({
    resolutionType: z.string(),
    note: z.string().nullable().optional(),
    resolvedBy: z.string().nullable().optional(),
    resolvedAt: z.string().nullable().optional(),
  })
  .passthrough();
export type DiscrepancyResolution = z.infer<
  typeof DiscrepancyResolutionSchema
>;

export const DiscrepancySchema = z
  .object({
    discrepancyId: z.string(),
    // tolerated as free string (unknown → generic label).
    type: z.string(),
    // present on BOTH for the 11th-increment FX-difference AMOUNT_MISMATCH
    // (the matched pair); nullable for the unmatched types.
    externalRef: z.string().nullable().optional(),
    journalEntryId: z.string().nullable().optional(),
    // F5 — the expected (internal carrying base) and actual (bank base)
    // amounts are minor-units STRINGS, never floats. They are flat string
    // fields (NOT wrapped Money) per reconciliation-api.md § 1/§ 4.
    expectedMinor: z
      .string()
      .regex(/^-?\d+$/, 'expectedMinor must be an integer string (F5)'),
    actualMinor: z
      .string()
      .regex(/^-?\d+$/, 'actualMinor must be an integer string (F5)'),
    currency: z.string().min(3).max(3),
    // tolerated as free string (unknown → generic label).
    status: z.string(),
    resolution: DiscrepancyResolutionSchema.nullable().optional(),
  })
  .passthrough();
export type Discrepancy = z.infer<typeof DiscrepancySchema>;

export const DiscrepanciesResponseSchema = z.object({
  data: z.array(DiscrepancySchema),
  meta: LedgerMetaSchema,
});
export type DiscrepanciesResponse = z.infer<
  typeof DiscrepanciesResponseSchema
>;

/** Producer discrepancy status filter (`OPEN` | `RESOLVED`; absent = all). */
export interface DiscrepanciesQueryParams {
  status?: string;
  page?: number;
  size?: number;
}

// ---------------------------------------------------------------------------
// Reconciliation discrepancy RESOLVE mutation — reconciliation-api.md § 2
//   POST /api/finance/ledger/reconciliation/discrepancies/{id}/resolve
//   (TASK-PC-FE-073 — the ledger surface's FIRST and ONLY operator mutation)
// ---------------------------------------------------------------------------

/**
 * The three producer-sanctioned resolution types (`reconciliation-api.md`
 * § 2 — `resolutionType ∈ { MATCHED_MANUALLY, WRITTEN_OFF, ACCEPTED }`).
 * Unlike the READ enum vocabularies (which are tolerant free strings so an
 * unknown/future producer value never throws), the RESOLVE request is a
 * console-ORIGINATED value: the operator picks exactly one of these three —
 * a closed `z.enum` is correct here (we must never send a value the producer
 * does not define).
 */
export const RESOLUTION_TYPES = [
  'MATCHED_MANUALLY',
  'WRITTEN_OFF',
  'ACCEPTED',
] as const;
export type ResolutionType = (typeof RESOLUTION_TYPES)[number];

/**
 * The resolve request body — `{ resolutionType, note }`.
 *
 * `note` is a **required**, non-empty operator narrative (the audit record);
 * the producer's `409 RECONCILIATION_ALREADY_RESOLVED` state guard is the
 * double-submit defence, so there is deliberately **NO `idempotencyKey`
 * field** — `reconciliation-api.md` § 2 defines none for resolve (unlike the
 * ledger `POST /entries`, which requires one). Fabricating a header the
 * producer ignores is forbidden (the same honesty discipline as the no-429
 * rule).
 */
export const ResolveDiscrepancyBodySchema = z.object({
  resolutionType: z.enum(RESOLUTION_TYPES),
  // A non-empty operator narrative — the audit record. A whitespace-only
  // value is rejected (the reason is required); the proxy maps a parse
  // failure to `400 VALIDATION_ERROR` with NO upstream call.
  note: z
    .string()
    .min(1)
    .max(512)
    .refine((s) => s.trim() !== '', { message: 'note must not be blank' }),
});
export type ResolveDiscrepancyBody = z.infer<
  typeof ResolveDiscrepancyBodySchema
>;

/**
 * Materialises a discrepancy's `expectedMinor` / `actualMinor` strings as
 * Money objects for the same `currency` (the reconciliation amounts are
 * KRW base values). Pure string transformation (no `Number(...)`).
 */
export function discrepancyMoney(d: Discrepancy): {
  expected: Money;
  actual: Money;
} {
  return {
    expected: { amount: d.expectedMinor, currency: d.currency },
    actual: { amount: d.actualMinor, currency: d.currency },
  };
}

// ---------------------------------------------------------------------------
// Reconciliation statement-detail — TASK-PC-FE-075
//   § 3 GET /api/finance/ledger/reconciliation/statements/{id}
//   (reconciliation-api.md § 1 + § 3). STRICTLY READ-ONLY.
//   No list/search GET over statements (id-driven — the honest ledger
//   constraint, same as entries + accounts; statement ids come from the
//   ingest the operator's integration ran — ingest is out of console scope).
//
// F5 MONEY: the `money` field on each `StatementMatch` is the SAME F5 Money
// shape — string minor-units, NEVER a number. `formatMoney(...)` is the
// only sanctioned render path.
//
// TOLERANCE: `source` is a free string (no closed enum in the producer
// contract — new sources render generically, never throw).
//
// The `discrepancies` array reuses the EXISTING `DiscrepancySchema`
// verbatim (the statement's discrepancies are the same shape as the § 4/§ 5
// discrepancy reads — do NOT redefine).
// ---------------------------------------------------------------------------

/**
 * StatementMatch — one matched line in a reconciliation statement
 * (`reconciliation-api.md` § 1 `matches` array element).
 *
 * `statementLineExternalRef` — the external ref the source system supplied
 * for this line (links back to the bank/external statement).
 * `journalEntryId` — the ledger journal entry matched to this line; used by
 * the console to drill into the Journal Entry tab.
 * `money` — the matched amount (F5 — string minor-units, NEVER a float).
 */
export const StatementMatchSchema = z
  .object({
    statementLineExternalRef: z.string(),
    journalEntryId: z.string(),
    money: MoneySchema, // F5 — REQUIRED, precision-preserving
  })
  .passthrough();
export type StatementMatch = z.infer<typeof StatementMatchSchema>;

/**
 * Statement — `reconciliation-api.md` § 3 `GET .../statements/{id}` 200
 * body `data` sub-object.
 *
 * `source` is a free string (tolerant — no closed enum; new values render
 * generically, no throw). `statementDate` is optional (nullable from the
 * producer). `matchedCount` + `discrepancyCount` are non-negative integers
 * (the summary; the actual rows are in `matches` / `discrepancies`).
 * `discrepancies` reuses the existing `DiscrepancySchema` (the statement's
 * discrepancies are the SAME shape as the § 4/§ 5 discrepancy reads).
 */
export const StatementSchema = z
  .object({
    statementId: z.string(),
    ledgerAccountCode: z.string(),
    // tolerant free string — no closed enum in the producer contract.
    source: z.string(),
    statementDate: z.string().optional(),
    matchedCount: z.number().int().nonnegative(),
    discrepancyCount: z.number().int().nonnegative(),
    matches: z.array(StatementMatchSchema),
    discrepancies: z.array(DiscrepancySchema), // reuse — do NOT redefine
  })
  .passthrough();
export type Statement = z.infer<typeof StatementSchema>;
