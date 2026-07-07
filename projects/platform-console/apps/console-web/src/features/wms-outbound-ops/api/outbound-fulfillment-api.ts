import {
  PickConfirmationSchema,
  type PickConfirmation,
  PackingUnitSchema,
  type PackingUnit,
  ShipmentSchema,
  type Shipment,
} from './types';
import { callOutbound } from './outbound-client';

/**
 * FULFILLMENT domain — wms outbound-service picking, packing, and shipping
 * mutations (TASK-PC-FE-147 sub-module; behavior-preserving split of
 * `outbound-api.ts`).
 *
 * Covers: § 2.3 confirmPick, § 3.1 createPackingUnit, § 3.2 sealPackingUnit,
 * § 4.1 confirmShipping.
 *
 * External code must import from `outbound-api.ts` (the barrel), not directly
 * from this file.
 */

// ===========================================================================
// PICKING
// ===========================================================================

/** 2.3 line shape for the pick confirmation body (built from planned lines). */
export interface ConfirmPickLine {
  orderLineId: string;
  skuId: string;
  lotId?: string | null;
  actualLocationId: string;
  qtyConfirmed: number;
}

/** 2.3 — POST /picking-requests/{id}/confirmations. */
export function confirmPick(
  pickingRequestId: string,
  lines: ConfirmPickLine[],
  idempotencyKey: string,
  notes?: string,
): Promise<PickConfirmation> {
  return callOutbound(
    {
      method: 'POST',
      path: `/picking-requests/${encodeURIComponent(pickingRequestId)}/confirmations`,
      idempotencyKey,
      body: notes ? { notes, lines } : { lines },
    },
    (j) => PickConfirmationSchema.parse(j),
  );
}

// ===========================================================================
// PACKING
// ===========================================================================

/** 3.1 line shape for the packing-unit create body. */
export interface PackingUnitLine {
  orderLineId: string;
  skuId: string;
  lotId?: string | null;
  qty: number;
}

/** 3.1 — POST /orders/{id}/packing-units (create unit). */
export function createPackingUnit(
  orderId: string,
  cartonNo: string,
  lines: PackingUnitLine[],
  idempotencyKey: string,
): Promise<PackingUnit> {
  return callOutbound(
    {
      method: 'POST',
      path: `/orders/${encodeURIComponent(orderId)}/packing-units`,
      idempotencyKey,
      body: { cartonNo, packingType: 'BOX', lines },
    },
    (j) => PackingUnitSchema.parse(j),
  );
}

/** 3.2 — PATCH /packing-units/{id} (seal, version-checked). */
export function sealPackingUnit(
  packingUnitId: string,
  version: number,
  idempotencyKey: string,
): Promise<PackingUnit> {
  return callOutbound(
    {
      method: 'PATCH',
      path: `/packing-units/${encodeURIComponent(packingUnitId)}`,
      idempotencyKey,
      body: { seal: true, version },
    },
    (j) => PackingUnitSchema.parse(j),
  );
}

// ===========================================================================
// SHIPPING
// ===========================================================================

/** 4.1 — POST /orders/{id}/shipments (confirm shipping, version-checked). */
export function confirmShipping(
  orderId: string,
  version: number,
  idempotencyKey: string,
  carrierCode = 'DEMO-CARRIER',
): Promise<Shipment> {
  return callOutbound(
    {
      method: 'POST',
      path: `/orders/${encodeURIComponent(orderId)}/shipments`,
      idempotencyKey,
      body: { carrierCode, version },
    },
    (j) => ShipmentSchema.parse(j),
  );
}
