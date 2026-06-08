/**
 * `features/wms-outbound-ops` public API (Layered-by-Feature — app/ imports
 * only this barrel, never feature internals; architecture.md § Allowed
 * Dependencies). wms outbound operations section, TASK-PC-FE-057 — the
 * SECOND wms surface (after `features/wms-ops` § 2.4.5), the on-screen
 * operator leg of ADR-MONO-022 § D7 (ecommerce↔wms fulfillment loop).
 *
 * Auth (console-integration-contract § 2.4.5.1, inherited from § 2.4.5):
 * this feature's server client uses the **domain-facing IAM OIDC token**
 * (`getDomainFacingToken()`), NEVER the IAM exchanged operator token
 * (`getOperatorToken()`) — the #569 invariant is GAP-domain-scoped.
 */
export { OutboundOpsScreen } from './components/OutboundOpsScreen';
export { OutboundActionDialog } from './components/OutboundActionDialog';
export { getOutboundSectionState } from './api/outbound-state';
export type { OutboundSectionState } from './api/outbound-state';
export type {
  OutboundOrderPage,
  OutboundOrderSummary,
  OutboundOrderDetail,
  OutboundOrderLine,
  OutboundSaga,
  PickingRequest,
  PickingRequestLine,
  PickingRequestList,
  PackingUnit,
  Shipment,
  OutboundListParams,
} from './api/types';
