import { NextResponse } from 'next/server';
import { listShipments } from '@/features/wms-ops/api/wms-api';
import type { ShipmentQueryParams } from '@/features/wms-ops/api/types';
import { mapWmsError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms shipments read proxy for client components (TASK-PC-FE-079 —
 * surfaces the already-contracted § 2.4.5 row 5 read; the client
 * `listShipments` existed since FE-007 but was never wired to UI). The typed
 * API client's single backend entry point — no browser-direct wms call
 * (architecture.md § Forbidden Dependencies / contract § 2.3). The HttpOnly
 * **IAM OIDC access token** is attached server-side in `listShipments()` (NOT
 * the IAM operator token — § 2.4.5 per-domain credential divergence).
 * READ-ONLY: GET only, no mutation branch, no Idempotency-Key, no
 * X-Operator-Reason.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: ShipmentQueryParams = {
    warehouseId: sp.get('warehouseId') ?? undefined,
    carrierCode: sp.get('carrierCode') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listShipments(params);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
