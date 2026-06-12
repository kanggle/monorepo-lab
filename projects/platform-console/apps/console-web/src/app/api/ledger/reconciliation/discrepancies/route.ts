import { NextResponse } from 'next/server';
import { listDiscrepancies } from '@/features/ledger-ops/api/ledger-api';
import type { DiscrepanciesQueryParams } from '@/features/ledger-ops/api/types';
import { mapLedgerError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger reconciliation discrepancy queue read proxy
 * (read-only — GET). The HttpOnly domain-facing IAM OIDC access token is
 * attached server-side in `listDiscrepancies()` (§ 2.4.7.1 reusing the
 * § 2.4.7 / § 2.4.5 rule — NOT the operator token). Paginated; forwards the
 * optional `status` (OPEN / RESOLVED) filter + `page` + `size`. No mutation
 * artifacts. The full `{ data, meta }` envelope is forwarded so each
 * discrepancy's expected / actual minor-units strings reach the client
 * untouched (NO `Number()` coercion).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const sp = new URL(req.url).searchParams;
  const q: DiscrepanciesQueryParams = {
    status: sp.get('status') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listDiscrepancies(q);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
