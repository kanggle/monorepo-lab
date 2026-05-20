import { NextResponse } from 'next/server';
import { getBusinessPartnerById } from '@/features/erp-ops/api/erp-api';
import {
  buildDetailParams,
  mapErpError,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp business-partner DETAIL read proxy (read-only —
 * GET). E3 `?asOf=` threaded through verbatim. Confidential
 * financial details never logged.
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const detailParams = buildDetailParams(req);
    const result = await getBusinessPartnerById(id, detailParams);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
