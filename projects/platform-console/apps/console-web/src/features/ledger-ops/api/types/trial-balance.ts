import { z } from 'zod';
import { MoneySchema } from './money';

// ---------------------------------------------------------------------------
// Trial balance — GET /api/finance/ledger/trial-balance (ledger-api.md § 4)
//   { data: { accounts: [...], grand*Total, inBalance }, meta }
// ---------------------------------------------------------------------------

/** One per-account row of the trial balance. Each carries its original
 *  transaction-currency totals AND its base-currency (KRW) totals
 *  (8th increment multi-currency consolidation). All four are F5 Money. */
export const TrialBalanceAccountSchema = z
  .object({
    ledgerAccountCode: z.string(),
    debitTotal: MoneySchema,
    creditTotal: MoneySchema,
    baseDebitTotal: MoneySchema,
    baseCreditTotal: MoneySchema,
  })
  .passthrough();
export type TrialBalanceAccount = z.infer<typeof TrialBalanceAccountSchema>;

export const TrialBalanceSchema = z
  .object({
    accounts: z.array(TrialBalanceAccountSchema),
    grandDebitTotal: MoneySchema,
    grandCreditTotal: MoneySchema,
    grandBaseDebitTotal: MoneySchema,
    grandBaseCreditTotal: MoneySchema,
    // The live double-entry invariant — surfaced honestly.
    inBalance: z.boolean(),
  })
  .passthrough();
export type TrialBalance = z.infer<typeof TrialBalanceSchema>;
