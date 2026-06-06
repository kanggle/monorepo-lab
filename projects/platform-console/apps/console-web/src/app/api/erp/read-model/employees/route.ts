import { NextResponse } from 'next/server';
import { listEmployeeOrgViews } from '@/features/erp-ops/api/erp-api';
import {
  buildListParams,
  mapErpError,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin erp read-model employee org-view LIST proxy (GET only —
 * TASK-PC-FE-049). READ-ONLY (E5 — the read-model holds no domain
 * logic and has NO mutation surface). IAM OIDC domain-facing token
 * attached server-side (UNCHANGED from the masterdata read binding;
 * never `getOperatorToken()`). E3 `?asOf=` + `departmentId` +
 * `status` threaded through via `buildListParams` / `listEmployeeOrgViews`.
 *
 * No POST / PATCH handler — this route file intentionally exports only
 * `GET` (a test pins that absence: read-model write surface = 0).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  try {
    const params = buildListParams(req);
    // Also forward `status` (read-model-specific filter not in the
    // masterdata buildListParams filter-key list — forward manually).
    const sp = new URL(req.url).searchParams;
    const statusVal = sp.get('status');
    const result = await listEmployeeOrgViews({
      asOf: params.asOf,
      page: params.page,
      size: params.size,
      departmentId: params.filters?.departmentId,
      ...(statusVal ? { status: statusVal } : {}),
    });
    return NextResponse.json(result);
  } catch (err) {
    return mapErpError(err, requestId);
  }
}
