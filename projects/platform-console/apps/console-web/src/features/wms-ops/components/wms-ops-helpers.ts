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
  /** TASK-PC-FE-173 — dedicated `/wms/inventory` screen only: 위치 ID filter
   *  (producer/proxy already supported this param; no form field existed). */
  locationId: string;
  /** TASK-PC-FE-173 — 로트 ID filter. */
  lotId: string;
  /** TASK-PC-FE-173 — 최소 보유 filter; form input is a STRING (parsed to a
   *  number on submit — empty/non-numeric → undefined, `0` is a valid
   *  value). */
  minOnHand: string;
}

export const EMPTY_INV_FILTERS: InvFilterState = {
  warehouseId: '',
  skuId: '',
  lowStockOnly: false,
  locationId: '',
  lotId: '',
  minOnHand: '',
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
