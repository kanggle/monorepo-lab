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
export { WmsShipmentsScreen } from './components/WmsShipmentsScreen';
export { WmsInboundScreen } from './components/WmsInboundScreen';
export { WmsMasterScreen } from './components/WmsMasterScreen';
export { AcknowledgeAlertDialog } from './components/AcknowledgeAlertDialog';
export { WmsOverview } from './components/WmsOverview';
export { WmsRecentShipments } from './components/WmsRecentShipments';
export { WmsRecentAdjustments } from './components/WmsRecentAdjustments';
export { getWmsSectionState } from './api/wms-state';
export type { WmsSectionState } from './api/wms-state';
export { getWmsInventoryState } from './api/inventory-state';
export type { WmsInventorySectionState } from './api/inventory-state';
export { getWmsShipmentsState } from './api/shipments-state';
export type { WmsShipmentsSectionState } from './api/shipments-state';
export { getWmsInboundState } from './api/inbound-state';
export type { WmsInboundSectionState } from './api/inbound-state';
export { getWmsMasterState } from './api/master-state';
export type { WmsMasterSectionState } from './api/master-state';
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
  AsnPage,
  AsnRow,
  Inspection,
  AckResult,
  Throughput,
  ProjectionStatus,
  InventoryQueryParams,
  AlertQueryParams,
  ShipmentQueryParams,
  AsnQueryParams,
  RefPage,
  RefType,
  RefQueryParams,
} from './api/types';
export { REF_TYPES, DEFAULT_REF_TYPE } from './api/types';
