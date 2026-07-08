import {
  ShipmentPageSchema,
  type ShipmentPage,
  type ShipmentQueryParams,
  AsnPageSchema,
  type AsnPage,
  type AsnQueryParams,
  InspectionSchema,
  type Inspection,
  AdjustmentPageSchema,
  type AdjustmentPage,
} from './types';
import { callWmsAdmin, pageParams, type WmsResult } from './wms-client';

// ---------------------------------------------------------------------------
// 1.3 shipments — GET /dashboard/shipments
// ---------------------------------------------------------------------------

export function listShipments(
  params: ShipmentQueryParams = {},
): Promise<WmsResult<ShipmentPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.carrierCode) qs.set('carrierCode', params.carrierCode);
  if (params.shippedAtFrom) qs.set('shippedAtFrom', params.shippedAtFrom);
  if (params.shippedAtTo) qs.set('shippedAtTo', params.shippedAtTo);
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/shipments?${qs.toString()}` },
    (json) => ShipmentPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.4 asns — GET /dashboard/asns
// ---------------------------------------------------------------------------

export function listAsns(
  params: AsnQueryParams = {},
): Promise<WmsResult<AsnPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.supplierPartnerId) {
    qs.set('supplierPartnerId', params.supplierPartnerId);
  }
  if (params.status) qs.set('status', params.status);
  if (params.expectedArriveDateFrom) {
    qs.set('expectedArriveDateFrom', params.expectedArriveDateFrom);
  }
  if (params.expectedArriveDateTo) {
    qs.set('expectedArriveDateTo', params.expectedArriveDateTo);
  }
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/asns?${qs.toString()}` },
    (json) => AsnPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.4 asn inspection — GET /dashboard/asns/{asnId}/inspection
// ---------------------------------------------------------------------------

export function getAsnInspection(
  asnId: string,
): Promise<WmsResult<Inspection>> {
  return callWmsAdmin(
    {
      method: 'GET',
      path: `/dashboard/asns/${encodeURIComponent(asnId)}/inspection`,
    },
    (json) => InspectionSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.5 adjustments audit (append-only — READ only, no edit) — GET /dashboard/adjustments
// ---------------------------------------------------------------------------

export function listAdjustments(
  params: { warehouseId?: string; reasonCode?: string; page?: number; size?: number } = {},
): Promise<WmsResult<AdjustmentPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.reasonCode) qs.set('reasonCode', params.reasonCode);
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/adjustments?${qs.toString()}` },
    (json) => AdjustmentPageSchema.parse(json),
  );
}
