import { NextResponse } from 'next/server';
import { listTransactions } from '@/features/finance-ops/api/finance-api';
import type { TransactionsQueryParams } from '@/features/finance-ops/api/types';
import { mapFinanceError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance transactions read proxy (read-only — GET). The
 * HttpOnly IAM OIDC access token is attached server-side in
 * `listTransactions()` (§ 2.4.7 reusing § 2.4.5 — NOT the operator
 * token). Paginated; forwards the optional `type` + `status` filters.
 * No mutation artifacts. The full `{ data, meta }` envelope is
 * forwarded so each txn's F5 `money` reaches the client as the
 * producer's minor-units string (NO `Number()` coercion).
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ accountId: string }> },
) {
  const requestId = newRequestId();
  const { accountId } = await params;
  const sp = new URL(req.url).searchParams;
  const q: TransactionsQueryParams = {
    type: sp.get('type') ?? undefined,
    status: sp.get('status') ?? undefined,
    page: sp.has('page') ? Number(sp.get('page')) : undefined,
    size: sp.has('size') ? Number(sp.get('size')) : undefined,
  };
  try {
    const result = await listTransactions(accountId, q);
    return NextResponse.json(result);
  } catch (err) {
    return mapFinanceError(err, requestId);
  }
}
