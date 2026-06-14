import { NextResponse } from 'next/server';
import { getPositionLots } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger FX-position open-lots read proxy (read-only — GET).
 * TASK-PC-FE-091 — § 2.4.7.1 ledger read consumer (row 10, FIN-BE-028 / § 12).
 *
 * The HttpOnly domain-facing IAM OIDC access token is attached server-side in
 * `getPositionLots()` (§ 2.4.7.1 reusing the § 2.4.7 / § 2.4.5 rule — NOT the
 * operator token). id-driven by `(ledgerAccountCode, currency)`: the colon-form
 * account code is `encodeURIComponent`-encoded by the api layer; Next.js
 * URL-decodes both `[ledgerAccountCode]` + `[currency]` params before they
 * arrive here — we pass them straight to `getPositionLots`, which re-encodes.
 * READ-ONLY: GET only, no mutation branch. An empty position passes through as
 * a normal `200` (`lots: []`, totals `"0"`, `lotCount: 0` — NOT a 404).
 * `400 VALIDATION_ERROR` (unsupported currency) passes through inline.
 *
 * STRICTLY GET — NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id,
 * NO body. NO 429 branch (the ledger has no documented 429).
 *
 * F7: neither the account code nor the currency is ever logged (the api layer
 * sanitises the path).
 */
export async function GET(
  _req: Request,
  {
    params,
  }: { params: Promise<{ ledgerAccountCode: string; currency: string }> },
) {
  const requestId = newRequestId();
  const { ledgerAccountCode, currency } = await params;
  try {
    const result = await getPositionLots(ledgerAccountCode, currency);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
