/**
 * `features/finance-overview` public API (Layered-by-Feature — app/
 * imports only this barrel, never feature internals; architecture.md
 * § Allowed Dependencies). Finance domain overview snapshot, TASK-PC-FE-229
 * (the `/finance` landing — supersedes the PARKED TASK-PC-FE-160).
 *
 * Composition (§ overview-state.ts): reuses the EXISTING `features/
 * ledger-ops` browsable index reads (trial balance / periods / OPEN
 * discrepancies / FX rates) + the EXISTING `features/finance-ops` single
 * default-account read — no cross-domain composition leg.
 * The account leg NEVER calls a list/search endpoint (honest constraint,
 * PC-FE-160 non-negotiable).
 *
 * TASK-MONO-812 — plus the «실적» strip read (`GET /api/finance/accounts/
 * metrics`, ADR-MONO-084 P7): tenant-wide settled totals the producer
 * aggregates, no account id, not a list.
 */
export {
  FinanceOverviewScreen,
  FINANCE_PERFORMANCE_LABELS,
  FINANCE_PERFORMANCE_SCOPE_NOTE,
} from './components/FinanceOverviewScreen';
export { getFinanceOverviewState, DEFAULT_OVERVIEW_PERIOD } from './api/overview-state';
export type {
  FinanceOverviewState,
  LedgerOverviewSummary,
  AccountSnapshot,
} from './api/overview-state';
