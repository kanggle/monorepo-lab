/**
 * `features/ledger-ops` public API (Layered-by-Feature — app/ imports
 * only this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). finance ledger operations section, TASK-PC-FE-072 —
 * the SECOND finance-product service section (the `ledger-service`
 * alongside the FE-009 `account-service`). STRICTLY READ-ONLY.
 *
 * Auth (console-integration-contract § 2.4.7.1 — REUSE of the § 2.4.5
 * per-domain credential rule VIA the § 2.4.7 finance binding, NOT
 * re-derived): this feature's server client uses the **domain-facing IAM
 * OIDC access token** (`getDomainFacingToken()`), NEVER the IAM exchanged
 * operator token (`getOperatorToken()`) — the #569 invariant is
 * GAP-domain-scoped. Same outcome as wms / scm / finance.
 *
 * F5 money (§ 2.4.7.1): every money value is parsed / stored / rendered
 * as a precision-exact minor-units **string** + currency; the only
 * sanctioned render path is `formatMoney(...)` (no `Number()` /
 * `parseFloat()` / `parseInt()` is applied to `amount` / `exchangeRate`
 * anywhere).
 *
 * Honest ledger constraint (§ 2.4.7.1): the ledger has NO journal-entry
 * list/search GET — the entry view is entry-id-driven; this barrel
 * intentionally exports NO entry list/search function. (The trial
 * balance / periods / discrepancy queue ARE browsable index reads.)
 */
export { LedgerOpsScreen } from './components/LedgerOpsScreen';
export { TrialBalanceTable } from './components/TrialBalanceTable';
export { PeriodsTable } from './components/PeriodsTable';
export { PeriodDetail } from './components/PeriodDetail';
export { JournalEntryLookup } from './components/JournalEntryLookup';
export { JournalEntryDetail } from './components/JournalEntryDetail';
export { DiscrepancyQueue } from './components/DiscrepancyQueue';
export { DiscrepancyDetail } from './components/DiscrepancyDetail';
export { DiscrepancyResolveDialog } from './components/DiscrepancyResolveDialog';
export { getLedgerSectionState } from './api/ledger-state';
export type { LedgerSectionState } from './api/ledger-state';
export type {
  TrialBalance,
  TrialBalanceAccount,
  Period,
  PeriodsResponse,
  PeriodSnapshot,
  PeriodsQueryParams,
  JournalEntry,
  JournalLine,
  Discrepancy,
  DiscrepanciesResponse,
  DiscrepanciesQueryParams,
  ResolveDiscrepancyBody,
  ResolutionType,
  Money,
  LedgerMeta,
} from './api/types';
export {
  formatMoney,
  discrepancyMoney,
  KNOWN_SOURCE_TYPES,
  KNOWN_DIRECTIONS,
  KNOWN_PERIOD_STATUSES,
  KNOWN_DISCREPANCY_TYPES,
  KNOWN_DISCREPANCY_STATUSES,
  RESOLUTION_TYPES,
  DEFAULT_CURRENCY_SCALES,
} from './api/types';
