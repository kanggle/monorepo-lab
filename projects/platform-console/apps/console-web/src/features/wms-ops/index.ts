/**
 * `features/wms-ops` public API (Layered-by-Feature — app/ imports only
 * this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). wms operations section, TASK-PC-FE-007 — the first
 * NON-IAM federated domain (ADR-MONO-013 Phase 4 slice 1).
 *
 * Auth divergence (console-integration-contract § 2.4.5): this feature's
 * server client uses the **IAM OIDC access token** (`getAccessToken()`),
 * NEVER the IAM exchanged operator token (`getOperatorToken()`) — the
 * #569 invariant is GAP-domain-scoped. Per-domain credential selection is
 * a first-class contract element.
 */
export { WmsOpsScreen } from './components/WmsOpsScreen';
export { WmsInventoryScreen } from './components/WmsInventoryScreen';
export { AcknowledgeAlertDialog } from './components/AcknowledgeAlertDialog';
export { WmsOverview } from './components/WmsOverview';
export { getWmsSectionState } from './api/wms-state';
export type { WmsSectionState } from './api/wms-state';
export { getWmsInventoryState } from './api/inventory-state';
export type { WmsInventorySectionState } from './api/inventory-state';
export { getWmsOverviewState } from './api/overview-state';
export type {
  WmsOverviewState,
  WmsAreaCount,
  WmsAlertStatusCount,
} from './api/overview-state';
export type {
  InventoryPage,
  InventoryRow,
  AlertPage,
  AlertRow,
  ShipmentPage,
  ShipmentRow,
  AckResult,
  Throughput,
  ProjectionStatus,
  InventoryQueryParams,
  AlertQueryParams,
  ShipmentQueryParams,
} from './api/types';
