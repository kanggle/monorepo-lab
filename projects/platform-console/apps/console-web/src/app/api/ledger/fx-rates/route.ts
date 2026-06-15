import { NextResponse } from 'next/server';
import { getFxRates } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger FX 환율 피드 read proxy (read-only — GET).
 * TASK-PC-FE-092 — FIN-BE-033 `GET /api/finance/ledger/fx-rates`.
 *
 * The HttpOnly domain-facing IAM OIDC access token is attached server-side
 * in `getFxRates()` (§ 2.4.7.1 reusing the § 2.4.7 / § 2.4.5 rule — NOT
 * the operator token). No path parameters — global list.
 * READ-ONLY: GET only, no mutation branch. An empty cache passes through
 * as a normal `200` (`rates: []`, `feedEnabled: true/false` — NOT a 404).
 *
 * STRICTLY GET — NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id,
 * NO body. NO 429 branch (the ledger has no documented 429).
 *
 * F5: `rate` is a decimal string on the wire and stays a string throughout
 * the proxy — no Number/parseFloat coercion at any layer.
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const result = await getFxRates();
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
