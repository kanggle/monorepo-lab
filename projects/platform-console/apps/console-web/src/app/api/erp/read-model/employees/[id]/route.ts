import { NextResponse } from 'next/server';
import { getEmployeeOrgView } from '@/features/erp-ops/api/erp-api';
import {
  buildDetailParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp read-model employee org-view DETAIL proxy (GET only —
 * TASK-PC-FE-049). READ-ONLY (E5). IAM OIDC domain-facing token
 * attached server-side. E3 `?asOf=` threaded through verbatim.
 *
 * No POST / PATCH handler — this route file intentionally exports only
 * `GET` (a test pins that absence).
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const detailParams = buildDetailParams(req);
    const result = await getEmployeeOrgView(id, detailParams);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
