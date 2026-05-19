import { NextResponse } from 'next/server';
import { listInventory } from '@/features/wms-ops/api/wms-api';
import type { InventoryQueryParams } from '@/features/wms-ops/api/types';
import { mapWmsError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms inventory-snapshot read proxy for client components (the
 * typed API client's single backend entry point — no browser-direct wms
 * call, architecture.md § Forbidden Dependencies / contract § 2.3). The
 * HttpOnly **GAP OIDC access token** is attached server-side in
 * `listInventory()` (NOT the GAP operator token — § 2.4.5 per-domain
 * credential divergence). READ-ONLY: GET only, no mutation branch, no
 * Idempotency-Key, no X-Operator-Reason.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: InventoryQueryParams = {
    warehouseId: sp.get('warehouseId') ?? undefined,
    locationId: sp.get('locationId') ?? undefined,
    skuId: sp.get('skuId') ?? undefined,
    lotId: sp.get('lotId') ?? undefined,
    lowStockOnly: sp.get('lowStockOnly') === 'true' ? true : undefined,
    minOnHand: sp.has('minOnHand') ? Number(sp.get('minOnHand')) : undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listInventory(params);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
