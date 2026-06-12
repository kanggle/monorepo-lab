import { NextResponse } from 'next/server';
import { getTrialBalance } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger trial-balance read proxy for client
 * components (the typed API client's single backend entry point — no
 * browser-direct ledger call, architecture.md § Forbidden Dependencies /
 * contract § 2.3). The HttpOnly **domain-facing IAM OIDC access token** is
 * attached server-side in `getTrialBalance()` (NOT the GAP operator
 * token — § 2.4.7.1 reusing the § 2.4.7 / § 2.4.5 per-domain credential
 * rule). READ-ONLY: GET only, no mutation branch, no Idempotency-Key, no
 * X-Operator-Reason, no ledger write. The full `TrialBalance` (per-account
 * + grand F5 minor-units strings) is forwarded untouched (NO `Number()`
 * coercion).
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const result = await getTrialBalance();
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
