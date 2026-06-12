import { NextResponse } from 'next/server';
import { getPeriod } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger accounting-period-by-id read proxy
 * (read-only — GET). The HttpOnly domain-facing IAM OIDC access token is
 * attached server-side in `getPeriod()` (§ 2.4.7.1 reusing the § 2.4.7 /
 * § 2.4.5 rule — NOT the operator token). READ-ONLY: GET only, no mutation
 * branch. The full `Period` (incl. the close `snapshot` when CLOSED, F5
 * minor-units strings) is forwarded untouched. 404
 * `ACCOUNTING_PERIOD_NOT_FOUND` passes through.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ periodId: string }> },
) {
  const requestId = newRequestId();
  const { periodId } = await params;
  try {
    const result = await getPeriod(periodId);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
