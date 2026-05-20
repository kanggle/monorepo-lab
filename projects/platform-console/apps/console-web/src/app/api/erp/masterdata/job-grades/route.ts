import { NextResponse } from 'next/server';
import { listJobGrades } from '@/features/erp-ops/api/erp-api';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp job-grades LIST read proxy (read-only — GET).
 * GAP OIDC access token attached server-side. Producer orders by
 * `displayOrder` asc — the proxy forwards verbatim.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    const result = await listJobGrades(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
