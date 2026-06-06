/**
 * `features/dashboards` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). IAM composed operator overview, TASK-PC-FE-005 —
 * READ-ONLY, no mutation surface, NO new IAM producer (a bounded fan-out
 * over the existing FE-002/003/004 read clients; ADR-MONO-015 D1-B).
 */
export { OperatorOverviewScreen } from './components/OperatorOverviewScreen';
export { getOverviewState } from './api/overview-state';
export type { OverviewState } from './api/overview-state';
export { getOperatorOverview } from './api/overview-api';
export type {
  OperatorOverview,
  AccountsSummary,
  AuditActivitySummary,
  OperatorsSummary,
  CardStatus,
} from './api/types';
