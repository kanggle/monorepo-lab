import { NextResponse } from 'next/server';
import { getAccount } from '@/features/finance-ops/api/finance-api';
import { mapFinanceError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance account-by-id read proxy for client components
 * (the typed API client's single backend entry point — no
 * browser-direct finance call, architecture.md § Forbidden
 * Dependencies / contract § 2.3). The HttpOnly **GAP OIDC access
 * token** is attached server-side in `getAccount()` (NOT the GAP
 * operator token — § 2.4.7 reusing the § 2.4.5 per-domain credential
 * rule). READ-ONLY: GET only, no mutation branch, no Idempotency-Key,
 * no X-Operator-Reason, no finance write.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ accountId: string }> },
) {
  const requestId = newRequestId();
  const { accountId } = await params;
  try {
    const result = await getAccount(accountId);
    return NextResponse.json(result);
  } catch (err) {
    return mapFinanceError(err, requestId);
  }
}
