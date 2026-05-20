import { NextResponse } from 'next/server';
import { listCostCenters } from '@/features/erp-ops/api/erp-api';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp cost-centers LIST read proxy (read-only — GET).
 * GAP OIDC access token attached server-side. E3 `?asOf=` threaded
 * through verbatim. Sensitive attrs never logged.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    const result = await listCostCenters(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
