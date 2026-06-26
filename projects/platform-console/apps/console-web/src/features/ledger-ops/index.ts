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
// ── CODE SPLIT (TASK-PC-FE-134) ── the per-tab leaf components
// (TrialBalanceTable / PeriodsTable / PeriodDetail / JournalEntry* /
// Discrepancy* / Account* / Statement* / PositionLots* / FxRates* /
// FxRateHistory*) are NO LONGER re-exported here. The `(console)/ledger`
// page is a Server Component that imports this barrel; every `'use client'`
// leaf re-exported here was being collected as an EAGER client reference for
// that page and bundled into the page's initial chunk — defeating the
// `LedgerOpsScreen` per-tab `next/dynamic` split (the heavy leaves stayed in
// the page chunk while only the thin panel wrappers split out). The leaves are
// reached at runtime only through the lazy panel boundaries inside
// `LedgerOpsScreen`; the test suite imports them via their direct
// `./components/<Name>` paths. `app/` still imports ONLY this barrel (the
// layering rule holds) — the barrel's public surface is the screen + the
// server state loader + the shared types/helpers below.
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
  // TASK-PC-FE-074 — account-level drill read types
  AccountBalance,
  AccountEntryLine,
  AccountEntriesResponse,
  AccountEntriesQueryParams,
  // TASK-PC-FE-075 — reconciliation statement-detail read types
  Statement,
  StatementMatch,
  // TASK-PC-FE-091 — FX position open-lots drill read types
  PositionLot,
  PositionLotsResponse,
  // TASK-PC-FE-092 — FX 환율 피드 대시보드 types
  FxRate,
  FxRatesResponse,
  // TASK-PC-FE-104 — FX 환율 history 드릴 types
  FxRateHistoryQuote,
  FxRateHistoryResponse,
  FxRateHistoryQueryParams,
} from './api/types';
export {
  formatMoney,
  discrepancyMoney,
  positionLotMoney,
  KNOWN_SOURCE_TYPES,
  KNOWN_DIRECTIONS,
  KNOWN_PERIOD_STATUSES,
  KNOWN_DISCREPANCY_TYPES,
  KNOWN_DISCREPANCY_STATUSES,
  RESOLUTION_TYPES,
  DEFAULT_CURRENCY_SCALES,
  // TASK-PC-FE-104 — FX 환율 history limit bounds
  FX_HISTORY_DEFAULT_LIMIT,
  FX_HISTORY_MAX_LIMIT,
} from './api/types';
