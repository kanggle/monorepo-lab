import { z } from 'zod';
import type { StatusTone } from '@/shared/ui/StatusBadge';
import { MoneySchema, LedgerMetaSchema } from './money';

// ---------------------------------------------------------------------------
// Accounting periods — GET /api/finance/ledger/periods[/{periodId}]
//   ledger-api.md § 7 (list), § 8 (detail + snapshot when CLOSED)
// ---------------------------------------------------------------------------

/** Producer period statuses surfaced HONESTLY (OPEN / CLOSED shown as-is
 *  — § 2.4.7.1). Free string for tolerance. */
export const KNOWN_PERIOD_STATUSES = ['OPEN', 'CLOSED'] as const;
export type KnownPeriodStatus = (typeof KNOWN_PERIOD_STATUSES)[number];

/**
 * Period status → shared semantic {@link StatusTone} (rendered via the shared
 * `<StatusBadge>` — TASK-PC-FE-159). OPEN is the live accounting period
 * (in-progress); CLOSED is finalised/reconciled (success). An unknown/future
 * status → `neutral` (tolerant — never a throw).
 */
const PERIOD_STATUS_TONE: Record<KnownPeriodStatus, StatusTone> = {
  OPEN: 'progress',
  CLOSED: 'success',
};

export function periodStatusTone(status: string): StatusTone {
  return PERIOD_STATUS_TONE[status as KnownPeriodStatus] ?? 'neutral';
}

/** One close-snapshot account row (per-account debit/credit, no base
 *  totals — the close snapshot is the simpler shape per ledger-api.md
 *  § 6/§ 8). F5 Money. */
export const PeriodSnapshotAccountSchema = z
  .object({
    ledgerAccountCode: z.string(),
    debitTotal: MoneySchema,
    creditTotal: MoneySchema,
  })
  .passthrough();
export type PeriodSnapshotAccount = z.infer<
  typeof PeriodSnapshotAccountSchema
>;

/** The immutable close snapshot — present only for a CLOSED period
 *  (`null` while OPEN; `snapshot: null` is NOT an error — § 2.4.7.1). */
export const PeriodSnapshotSchema = z
  .object({
    accounts: z.array(PeriodSnapshotAccountSchema),
    grandDebitTotal: MoneySchema,
    grandCreditTotal: MoneySchema,
    inBalance: z.boolean(),
  })
  .passthrough();
export type PeriodSnapshot = z.infer<typeof PeriodSnapshotSchema>;

export const PeriodSchema = z
  .object({
    periodId: z.string(),
    // tolerated as free string (unknown → generic label).
    status: z.string(),
    from: z.string().optional(),
    to: z.string().optional(),
    closedAt: z.string().nullable().optional(),
    closedBy: z.string().nullable().optional(),
    entryCount: z.number().int().nullable().optional(),
    // present (+ non-null) only on the detail read of a CLOSED period.
    snapshot: PeriodSnapshotSchema.nullable().optional(),
  })
  .passthrough();
export type Period = z.infer<typeof PeriodSchema>;

export const PeriodsResponseSchema = z.object({
  data: z.array(PeriodSchema),
  meta: LedgerMetaSchema,
});
export type PeriodsResponse = z.infer<typeof PeriodsResponseSchema>;

export interface PeriodsQueryParams {
  page?: number;
  size?: number;
}
