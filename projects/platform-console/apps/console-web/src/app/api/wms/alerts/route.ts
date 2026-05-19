import { NextResponse } from 'next/server';
import { listAlerts } from '@/features/wms-ops/api/wms-api';
import type { AlertQueryParams } from '@/features/wms-ops/api/types';
import { mapWmsError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin wms alerts-list read proxy (read-only — GET). The HttpOnly
 * GAP OIDC access token is attached server-side in `listAlerts()`
 * (§ 2.4.5 per-domain credential divergence — NOT the operator token).
 * No mutation artifacts.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const params: AlertQueryParams = {
    alertType: sp.get('alertType') ?? undefined,
    warehouseId: sp.get('warehouseId') ?? undefined,
    acknowledged:
      sp.get('acknowledged') === 'true'
        ? true
        : sp.get('acknowledged') === 'false'
          ? false
          : undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listAlerts(params);
    return NextResponse.json(result.data);
  } catch (err) {
    return mapWmsError(err, requestId);
  }
}
