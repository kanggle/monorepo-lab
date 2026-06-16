import type { AlertRow } from '../api/types';

/**
 * Pure helpers for the wms operations screen (TASK-PC-FE-103 split). The
 * inventory + shipment client-side filter shapes (+ their empty defaults) and
 * the alert label formatter. No hooks, no JSX.
 */

export interface InvFilterState {
  warehouseId: string;
  skuId: string;
  lowStockOnly: boolean;
}

export const EMPTY_INV_FILTERS: InvFilterState = {
  warehouseId: '',
  skuId: '',
  lowStockOnly: false,
};

export interface ShipFilterState {
  warehouseId: string;
  carrierCode: string;
}

export const EMPTY_SHIP_FILTERS: ShipFilterState = {
  warehouseId: '',
  carrierCode: '',
};

export function alertLabel(a: AlertRow): string {
  return a.alertType ? `${a.alertType} (${a.alertId})` : a.alertId;
}
