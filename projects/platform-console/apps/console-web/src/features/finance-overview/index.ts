/**
 * `features/finance-overview` public API (Layered-by-Feature — app/
 * imports only this barrel, never feature internals; architecture.md
 * § Allowed Dependencies). Finance domain overview snapshot, TASK-PC-FE-229
 * (the `/finance` landing — supersedes the PARKED TASK-PC-FE-160).
 *
 * Composition (§ overview-state.ts): reuses the EXISTING `features/
 * ledger-ops` browsable index reads (trial balance / periods / OPEN
 * discrepancies / FX rates) + the EXISTING `features/finance-ops` single
 * default-account read — no new producer endpoint, no console-bff leg.
 * The account leg NEVER calls a list/search endpoint (honest constraint,
 * PC-FE-160 non-negotiable).
 */
export { FinanceOverviewScreen } from './components/FinanceOverviewScreen';
export { getFinanceOverviewState } from './api/overview-state';
export type {
  FinanceOverviewState,
  LedgerOverviewSummary,
  AccountSnapshot,
} from './api/overview-state';
