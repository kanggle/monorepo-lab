/**
 * `features/accounts` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). IAM accounts operator parity, TASK-PC-FE-002.
 */
export { AccountsScreen } from './components/AccountsScreen';
export { ConfirmActionDialog } from './components/ConfirmActionDialog';
export {
  getAccountsListState,
  getAccountsAccessTier,
  getAccountsSearchOnlyState,
} from './api/accounts-state';
export type {
  AccountsListState,
  AccountsSearchOnlyState,
} from './api/accounts-state';
export type { AccountsAccessTier } from '@/shared/guide/permission-map';
export type {
  AccountPage,
  AccountSummary,
  AccountSearchParams,
} from './api/types';
