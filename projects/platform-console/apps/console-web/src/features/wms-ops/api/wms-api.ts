/**
 * Public surface barrel for wms-ops API client (TASK-PC-FE-146).
 *
 * The monolithic implementation has been split into cohesive domain modules:
 *   - `wms-client.ts`       — HTTP core (callWmsAdmin, pageParams, WmsResult)
 *   - `wms-inventory-api.ts`— inventory snapshot, by-key, throughput, orders
 *   - `wms-shipments-api.ts`— shipments, ASNs, ASN inspection, adjustments
 *   - `wms-alerts-api.ts`   — alerts list + alert acknowledge (the only mutation)
 *   - `wms-refs-api.ts`     — master refs, projection-status
 *
 * This file re-exports the public surface unchanged so all importers
 * (`app/api/wms/**` route handlers, `wms-state.ts`, and tests) resolve
 * identically — 0 import-site edits required.
 *
 * Internal helpers (`callWmsAdmin`, `pageParams`, `CallOptions`) are NOT
 * re-exported here (they were never part of the public surface).
 */
export type { WmsResult } from './wms-client';
export {
  listInventory,
  getInventoryByKey,
  getThroughput,
  listOrders,
} from './wms-inventory-api';
export {
  listShipments,
  listAsns,
  getAsnInspection,
  listAdjustments,
} from './wms-shipments-api';
export { listAlerts, acknowledgeAlert } from './wms-alerts-api';
export { listRefs, getProjectionStatus } from './wms-refs-api';
