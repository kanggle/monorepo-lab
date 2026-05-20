import { NextResponse } from 'next/server';
import { getDepartmentById } from '@/features/erp-ops/api/erp-api';
import {
  buildDetailParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp department DETAIL read proxy (read-only — GET).
 * E3 `?asOf=` threaded through verbatim. GAP OIDC access token
 * attached server-side. No mutation artifacts.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const detailParams = buildDetailParams(req);
    const result = await getDepartmentById(id, detailParams);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
