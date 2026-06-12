import { NextResponse } from 'next/server';
import { listPeriods } from '@/features/ledger-ops/api/ledger-api';
import type { PeriodsQueryParams } from '@/features/ledger-ops/api/types';
import { mapLedgerError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger accounting-periods list read proxy
 * (read-only — GET). The HttpOnly domain-facing IAM OIDC access token is
 * attached server-side in `listPeriods()` (§ 2.4.7.1 reusing the § 2.4.7 /
 * § 2.4.5 rule — NOT the operator token). Paginated; forwards the optional
 * `page` + `size`. No mutation artifacts. The full `{ data, meta }`
 * envelope is forwarded.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const q: PeriodsQueryParams = {
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listPeriods(q);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
