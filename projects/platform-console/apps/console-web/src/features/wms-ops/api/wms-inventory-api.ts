import {
  InventoryPageSchema,
  type InventoryPage,
  type InventoryQueryParams,
  InventoryRowSchema,
  type InventoryRow,
  ThroughputSchema,
  type Throughput,
  OrderPageSchema,
  type OrderPage,
} from './types';
import { callWmsAdmin, pageParams, type WmsResult } from './wms-client';

// ---------------------------------------------------------------------------
// 1.1 inventory snapshot — GET /dashboard/inventory
// ---------------------------------------------------------------------------

export function listInventory(
  params: InventoryQueryParams = {},
): Promise<WmsResult<InventoryPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.locationId) qs.set('locationId', params.locationId);
  if (params.skuId) qs.set('skuId', params.skuId);
  if (params.lotId) qs.set('lotId', params.lotId);
  if (params.lowStockOnly) qs.set('lowStockOnly', 'true');
  if (params.minOnHand !== undefined) {
    qs.set('minOnHand', String(params.minOnHand));
  }
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/inventory?${qs.toString()}` },
    (json) => InventoryPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.1 inventory by-key — GET /dashboard/inventory/by-key
// ---------------------------------------------------------------------------

export function getInventoryByKey(key: {
  locationId: string;
  skuId: string;
  lotId?: string;
}): Promise<WmsResult<InventoryRow>> {
  const qs = new URLSearchParams();
  qs.set('locationId', key.locationId);
  qs.set('skuId', key.skuId);
  if (key.lotId) qs.set('lotId', key.lotId);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/inventory/by-key?${qs.toString()}` },
    (json) => InventoryRowSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.2 throughput — GET /dashboard/throughput
// ---------------------------------------------------------------------------

export function getThroughput(args: {
  warehouseId: string;
  from: string;
  to: string;
}): Promise<WmsResult<Throughput>> {
  const qs = new URLSearchParams({
    warehouseId: args.warehouseId,
    from: args.from,
    to: args.to,
  });
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/throughput?${qs.toString()}` },
    (json) => ThroughputSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 1.3 orders — GET /dashboard/orders
// ---------------------------------------------------------------------------

export function listOrders(
  params: { warehouseId?: string; status?: string; page?: number; size?: number } = {},
): Promise<WmsResult<OrderPage>> {
  const qs = new URLSearchParams();
  if (params.warehouseId) qs.set('warehouseId', params.warehouseId);
  if (params.status) qs.set('status', params.status);
  pageParams(qs, params.page, params.size);
  return callWmsAdmin(
    { method: 'GET', path: `/dashboard/orders?${qs.toString()}` },
    (json) => OrderPageSchema.parse(json),
  );
}
