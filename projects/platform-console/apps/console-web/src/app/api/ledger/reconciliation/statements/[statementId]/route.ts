import { NextResponse } from 'next/server';
import { getStatement } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger reconciliation statement-detail read proxy
 * (read-only — GET). TASK-PC-FE-075 — § 2.4.7.1.
 *
 * The HttpOnly domain-facing IAM OIDC access token is attached server-side
 * in `getStatement()` (§ 2.4.7.1 reusing the § 2.4.7 / § 2.4.5 rule —
 * NOT the operator token). Id-driven (the statementId is `encodeURIComponent`-
 * encoded by the api layer; Next.js URL-decodes the `[statementId]` param
 * before it arrives here — we pass it straight to `getStatement` which
 * re-encodes). READ-ONLY: GET only, no mutation branch.
 * 404 `RECONCILIATION_STATEMENT_NOT_FOUND` passes through.
 *
 * STRICTLY GET — NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id,
 * NO body. NO 429 branch (the ledger has no documented 429).
 *
 * F7: no statementId is ever logged (the api layer sanitises the path to
 * `/api/finance/ledger/reconciliation/statements/{id}`).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ statementId: string }> },
) {
  const requestId = newRequestId();
  const { statementId } = await params;
  try {
    const result = await getStatement(statementId);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
