import { NextResponse } from 'next/server';
import { getAccountBalance } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger account-balance read proxy (read-only — GET).
 * TASK-PC-FE-074 — § 2.4.7.1 account-level drill reads.
 *
 * The HttpOnly domain-facing IAM OIDC access token is attached server-side
 * in `getAccountBalance()` (§ 2.4.7.1 reusing the § 2.4.7 / § 2.4.5 rule
 * — NOT the operator token). Id-driven (the colon-form code is
 * `encodeURIComponent`-encoded by the api layer; Next.js URL-decodes the
 * `[ledgerAccountCode]` param before it arrives here — we pass it straight
 * to `getAccountBalance` which re-encodes). READ-ONLY: GET only, no
 * mutation branch. 404 `LEDGER_ACCOUNT_NOT_FOUND` passes through.
 *
 * STRICTLY GET — NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id,
 * NO body. NO 429 branch (the ledger has no documented 429).
 *
 * F7: no account code is ever logged (the api layer sanitises the path).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ ledgerAccountCode: string }> },
) {
  const requestId = newRequestId();
  const { ledgerAccountCode } = await params;
  try {
    const result = await getAccountBalance(ledgerAccountCode);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
