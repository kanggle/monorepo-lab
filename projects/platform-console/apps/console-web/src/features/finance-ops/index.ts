/**
 * `features/finance-ops` public API (Layered-by-Feature — app/ imports
 * only this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). finance operations section, TASK-PC-FE-009 — the
 * THIRD NON-GAP federated domain (ADR-MONO-013 Phase 5; closes the
 * non-GAP federation cycle). STRICTLY READ-ONLY.
 *
 * Auth (console-integration-contract § 2.4.7 — REUSE of the § 2.4.5
 * per-domain credential rule, NOT re-derived): this feature's server
 * client uses the **GAP OIDC access token** (`getAccessToken()`),
 * NEVER the GAP exchanged operator token (`getOperatorToken()`) — the
 * #569 invariant is GAP-domain-scoped. Same outcome as wms / scm.
 *
 * F5 money (§ 2.4.7): every money value is parsed / stored / rendered
 * as a precision-exact minor-units **string** + currency; the only
 * sanctioned render path is `formatMoney(...)` (no `Number()` /
 * `parseFloat()` / `parseInt()` is applied to `amount` anywhere).
 *
 * Honest finance constraint (§ 2.4.7): finance v1 has NO account
 * list/search GET — the section is account-id-driven; this barrel
 * intentionally exports NO list/search function.
 */
export { FinanceOpsScreen } from './components/FinanceOpsScreen';
export { AccountLookup } from './components/AccountLookup';
export { AccountDetail } from './components/AccountDetail';
export { BalancesTable } from './components/BalancesTable';
export { TransactionsTable } from './components/TransactionsTable';
export { getFinanceSectionState } from './api/finance-state';
export type { FinanceSectionState } from './api/finance-state';
export type {
  Account,
  Balance,
  BalancesResponse,
  Transaction,
  TransactionsResponse,
  TransactionsQueryParams,
  Money,
  FinanceMeta,
} from './api/types';
export {
  formatMoney,
  balanceMoney,
  KNOWN_ACCOUNT_STATUSES,
  KNOWN_TXN_STATUSES,
  KNOWN_TXN_TYPES,
  DEFAULT_CURRENCY_SCALES,
} from './api/types';
