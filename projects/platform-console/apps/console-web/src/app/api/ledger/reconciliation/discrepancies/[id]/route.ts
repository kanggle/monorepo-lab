import { NextResponse } from 'next/server';
import { getDiscrepancy } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger reconciliation discrepancy-by-id read proxy
 * (read-only — GET). The HttpOnly domain-facing IAM OIDC access token is
 * attached server-side in `getDiscrepancy()` (§ 2.4.7.1 reusing the
 * § 2.4.7 / § 2.4.5 rule — NOT the operator token). READ-ONLY: GET only,
 * no mutation branch (no resolve endpoint). The full `Discrepancy` (incl.
 * the `resolution` when RESOLVED, F5 minor-units strings) is forwarded
 * untouched. 404 `RECONCILIATION_DISCREPANCY_NOT_FOUND` passes through.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getDiscrepancy(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
