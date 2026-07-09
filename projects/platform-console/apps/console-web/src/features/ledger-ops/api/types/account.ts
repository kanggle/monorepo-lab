import { z } from 'zod';
import { MoneySchema, LedgerMetaSchema } from './money';

// ---------------------------------------------------------------------------
// Account-level drill reads — TASK-PC-FE-074
//   § 2 GET /api/finance/ledger/accounts/{ledgerAccountCode}/entries
//   § 3 GET /api/finance/ledger/accounts/{ledgerAccountCode}/balance
//   STRICTLY READ-ONLY — these two reads add NO mutation. The account code
//   in the colon form (e.g. `CUSTOMER_WALLET:acc-1`) MUST be
//   `encodeURIComponent`-encoded on the producer path.
//
// F5 MONEY: all four money fields (`debitTotal`, `creditTotal`, `balance`,
// `money` in entries) are the SAME F5 Money shape — string minor-units,
// NEVER a number. `formatMoney(...)` is the only sanctioned render path.
//
// TOLERANCE: `type` / `normalSide` / `balanceSide` / `direction` are
// surfaced as free strings (tolerant-parser discipline — unknown / future
// values render with a generic label, NEVER throw).
// ---------------------------------------------------------------------------

/**
 * AccountBalance — § 3 `GET /api/finance/ledger/accounts/{code}/balance`.
 *
 * `balance = |debitTotal − creditTotal|` (the net, computed by the producer).
 * `type` / `normalSide` / `balanceSide` are free strings (LIABILITY / ASSET /
 * …, DEBIT / CREDIT / NONE — tolerant, new values render generically, no
 * throw). All four money fields are F5 — string minor-units.
 */
export const AccountBalanceSchema = z
  .object({
    ledgerAccountCode: z.string(),
    // tolerant free strings — unknown / future account type / side values
    // parse without throwing (tolerant-parser discipline).
    type: z.string(),
    normalSide: z.string(),
    debitTotal: MoneySchema, // F5 — REQUIRED, precision-preserving
    creditTotal: MoneySchema, // F5 — REQUIRED, precision-preserving
    balance: MoneySchema, // F5 — REQUIRED, |debitTotal − creditTotal|
    balanceSide: z.string(),
  })
  .passthrough();
export type AccountBalance = z.infer<typeof AccountBalanceSchema>;

/**
 * AccountEntryLine — one journal line posted to an account (§ 2 entries
 * array element). `entryId` is the journal-entry id for the drill-back into
 * the Journal Entry tab. `direction` is a free string (DEBIT / CREDIT —
 * tolerant). `money` is F5. `counterpartyLines` is an optional convenience
 * field (the other legs of the journal entry the account is part of) — kept
 * permissive (`z.any()`) because the producer treats it as an optional
 * advisory field and its shape is not the primary contract surface here.
 */
export const AccountEntryLineSchema = z
  .object({
    entryId: z.string(),
    postedAt: z.string().optional(),
    // tolerant free string — unknown direction renders generically.
    direction: z.string(),
    money: MoneySchema, // F5 — REQUIRED, precision-preserving
    counterpartyLines: z.array(z.any()).nullable().optional(),
  })
  .passthrough();
export type AccountEntryLine = z.infer<typeof AccountEntryLineSchema>;

export const AccountEntriesResponseSchema = z.object({
  data: z.array(AccountEntryLineSchema),
  meta: LedgerMetaSchema,
});
export type AccountEntriesResponse = z.infer<
  typeof AccountEntriesResponseSchema
>;

/** AccountEntriesQueryParams — pagination for the account entries read
 *  (§ 2 GET `.../accounts/{code}/entries?page=&size=`). */
export interface AccountEntriesQueryParams {
  page?: number;
  size?: number;
}
