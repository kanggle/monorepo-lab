import { z } from 'zod';
import { MoneySchema } from './money';

// ---------------------------------------------------------------------------
// Journal entry — GET /api/finance/ledger/entries/{entryId} (ledger-api.md § 1)
//   lines carry money + exchangeRate + baseAmount; source.sourceType; balanced
// ---------------------------------------------------------------------------

/** Producer journal-entry source types surfaced HONESTLY (TRANSACTION /
 *  MANUAL / REVALUATION / SETTLEMENT shown as-is — § 2.4.7.1). Stored as a
 *  free string so unknown / future values render generically (no parser
 *  throw, tolerant-parser discipline). */
export const KNOWN_SOURCE_TYPES = [
  'TRANSACTION',
  'MANUAL',
  'REVALUATION',
  'SETTLEMENT',
] as const;
export type KnownSourceType = (typeof KNOWN_SOURCE_TYPES)[number];

export const KNOWN_DIRECTIONS = ['DEBIT', 'CREDIT'] as const;
export type KnownDirection = (typeof KNOWN_DIRECTIONS)[number];

export const JournalSourceSchema = z
  .object({
    // tolerated as free string (unknown → generic label).
    sourceType: z.string(),
    sourceTransactionId: z.string().nullable().optional(),
    sourceEventId: z.string().nullable().optional(),
  })
  .passthrough();
export type JournalSource = z.infer<typeof JournalSourceSchema>;

/** A journal line — the F5 triple: `money` (transaction currency) +
 *  `exchangeRate` (decimal string, minor-to-minor) + `baseAmount` (the
 *  balance-authoritative KRW value). A base-currency KRW line has
 *  `exchangeRate "1"` and `baseAmount == money`; a 9th-increment
 *  revaluation line has `money.amount "0"` (foreign) with a non-zero KRW
 *  `baseAmount`. `direction` is tolerated as a free string. */
export const JournalLineSchema = z
  .object({
    ledgerAccountCode: z.string(),
    // tolerated as free string (unknown → generic label).
    direction: z.string(),
    money: MoneySchema, // F5 — REQUIRED, precision-preserving
    // F5 — REQUIRED decimal string, surfaced verbatim (never floated).
    exchangeRate: z.string(),
    baseAmount: MoneySchema, // F5 — REQUIRED, balance-authoritative KRW
  })
  .passthrough();
export type JournalLine = z.infer<typeof JournalLineSchema>;

export const JournalEntrySchema = z
  .object({
    entryId: z.string(),
    postedAt: z.string().optional(),
    source: JournalSourceSchema,
    reversalOfEntryId: z.string().nullable().optional(),
    lines: z.array(JournalLineSchema),
    balanced: z.boolean(),
  })
  .passthrough();
export type JournalEntry = z.infer<typeof JournalEntrySchema>;
