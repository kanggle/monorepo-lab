import type { StatusTone } from '@/shared/ui/StatusBadge';
import type { AlertRow } from '../api/types';

/**
 * Pure helpers for the wms operations screen (TASK-PC-FE-103 split). The
 * inventory + shipment + inbound (TASK-PC-FE-222) client-side filter shapes
 * (+ their empty defaults), the alert label formatter, and the ASN status →
 * semantic tone map. No hooks, no JSX.
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

// --- 입고 / ASN (TASK-PC-FE-222) ------------------------------------------

export interface InboundFilterState {
  status: string;
  warehouseId: string;
  supplierPartnerId: string;
  expectedArriveDateFrom: string;
  expectedArriveDateTo: string;
}

export const EMPTY_INBOUND_FILTERS: InboundFilterState = {
  status: '',
  warehouseId: '',
  supplierPartnerId: '',
  expectedArriveDateFrom: '',
  expectedArriveDateTo: '',
};

/** ASN status vocabulary (wms-platform `inbound-service` asn-status.md). */
export const ASN_STATUS_FILTER_OPTIONS = [
  '',
  'CREATED',
  'INSPECTING',
  'INSPECTED',
  'IN_PUTAWAY',
  'PUTAWAY_DONE',
  'CLOSED',
  'CANCELLED',
] as const;

/**
 * Maps an ASN status to a shared semantic {@link StatusTone} (rendered via
 * `<StatusBadge>`, TASK-PC-FE-158). Unknown / absent / future status →
 * `neutral`, so the console never crashes on a producer enum it does not
 * know (TOLERANCE invariant, mirrors `outboundStatusTone`).
 */
const ASN_STATUS_TONE: Record<string, StatusTone> = {
  CREATED: 'warning',
  INSPECTING: 'progress',
  INSPECTED: 'progress',
  IN_PUTAWAY: 'progress',
  PUTAWAY_DONE: 'progress',
  CLOSED: 'success',
  CANCELLED: 'danger',
};

export function asnStatusTone(status: string | undefined): StatusTone {
  return status ? (ASN_STATUS_TONE[status] ?? 'neutral') : 'neutral';
}
