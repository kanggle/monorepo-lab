/**
 * `features/scm-ops` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). scm operations section, TASK-PC-FE-008 — the SECOND
 * NON-IAM federated domain (ADR-MONO-013 Phase 4 slice 2; completes
 * Phase 4). STRICTLY READ-ONLY.
 *
 * Auth (console-integration-contract § 2.4.6 — REUSE of the § 2.4.5
 * per-domain credential rule, NOT re-derived): this feature's server
 * client uses the **IAM OIDC access token** (`getAccessToken()`), NEVER
 * the IAM exchanged operator token (`getOperatorToken()`) — the #569
 * invariant is GAP-domain-scoped. Same outcome as wms.
 *
 * S5 (§ 2.4.6): every inventory-visibility view surfaces the producer
 * `meta.warning` prominently (a contract obligation; required, surfaced
 * view-model field — never stripped).
 */
export { ScmProcurementScreen } from './components/ScmProcurementScreen';
export { ScmInventoryScreen } from './components/ScmInventoryScreen';
export { S5Warning } from './components/S5Warning';
export { ScmOverview } from './components/ScmOverview';
export { PoDetailDialog } from './components/PoDetailDialog';
export {
  getScmProcurementState,
  getScmInventoryState,
} from './api/scm-state';
export type {
  ScmProcurementState,
  ScmInventoryState,
} from './api/scm-state';
export { getScmOverviewState } from './api/overview-state';
export type {
  ScmOverviewState,
  ScmAreaCount,
  ScmPoStatusCount,
} from './api/overview-state';
export type {
  PoPage,
  PurchaseOrder,
  PoLine,
  SnapshotResponse,
  SnapshotRow,
  SkuBreakdown,
  StalenessResponse,
  NodesResponse,
  PoQueryParams,
  SnapshotQueryParams,
} from './api/types';
