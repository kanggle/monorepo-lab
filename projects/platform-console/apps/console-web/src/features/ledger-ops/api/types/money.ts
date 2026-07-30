/**
 * ledger-ops money / meta / pagination leaf module (TASK-PC-FE-233 split).
 *
 * TASK-PC-FE-259 — the values themselves were promoted out of the feature:
 *   - the **F5 `Money` primitive** (`MoneySchema` / `Money` /
 *     `DEFAULT_CURRENCY_SCALES` / `formatMoney`) → `shared/lib/money.ts`.
 *     It was declared here AND, character-for-character identically, in
 *     `features/finance-ops/api/types.ts`, and a third feature
 *     (`features/finance-overview`) cross-imported one of the two copies —
 *     which `architecture.md` § Forbidden Dependencies forbids
 *     ("공유 가치는 `shared/` 로 승격").
 *   - the ledger envelope meta + page bounds + `ExchangeRateSchema` →
 *     `shared/api/ledger-types/common.ts`, because the four ledger concept
 *     type modules that compose them (`trial-balance` · `period` ·
 *     `reconciliation` · `fx`) moved to `shared/api/ledger-types/` for the
 *     same reason, and a `shared/` module may not import a feature.
 *
 * This module stays the ledger-ops leaf import path so every existing
 * `../api/types` consumer is unchanged. 0 behavior change — the promoted
 * implementations are byte-identical.
 *
 * F5 MONEY INVARIANT — see `api/types/index.ts` for the full contract
 * narrative (§ 2.4.7.1). `Money` is `{ amount: "<string-integer-minor-
 * units>", currency }` — `amount` is a **string-encoded integer in minor
 * units**, NEVER a JS `Number` / float. `formatMoney(...)` is the only
 * sanctioned way to render a Money value.
 */

export {
  MoneySchema,
  DEFAULT_CURRENCY_SCALES,
  formatMoney,
} from '@/shared/lib/money';
export type { Money } from '@/shared/lib/money';

export {
  ExchangeRateSchema,
  LedgerMetaSchema,
  LEDGER_DEFAULT_PAGE_SIZE,
  LEDGER_MAX_PAGE_SIZE,
} from '@/shared/api/ledger-types/common';
export type { LedgerMeta } from '@/shared/api/ledger-types/common';
