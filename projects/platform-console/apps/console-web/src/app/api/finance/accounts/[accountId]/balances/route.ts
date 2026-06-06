import { NextResponse } from 'next/server';
import { getBalances } from '@/features/finance-ops/api/finance-api';
import { mapFinanceError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance balances read proxy (read-only — GET). The
 * HttpOnly IAM OIDC access token is attached server-side in
 * `getBalances()` (§ 2.4.7 reusing § 2.4.5 — NOT the operator token).
 * No mutation artifacts. The full `{ data, meta }` envelope is
 * forwarded so the per-currency F5 minor-units strings reach the
 * client untouched (NO `Number()` coercion anywhere).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ accountId: string }> },
) {
  const requestId = newRequestId();
  const { accountId } = await params;
  try {
    const result = await getBalances(accountId);
    return NextResponse.json(result);
  } catch (err) {
    return mapFinanceError(err, requestId);
  }
}
