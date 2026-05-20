import { NextResponse } from 'next/server';
import { listDepartments } from '@/features/erp-ops/api/erp-api';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp departments LIST read proxy (read-only — GET).
 * The HttpOnly GAP OIDC access token is attached server-side in
 * `listDepartments()` (§ 2.4.8 reusing § 2.4.5 — NOT the operator
 * token). E3 `?asOf=` threaded through to the producer verbatim.
 * No mutation artifacts.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    const result = await listDepartments(params);
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
