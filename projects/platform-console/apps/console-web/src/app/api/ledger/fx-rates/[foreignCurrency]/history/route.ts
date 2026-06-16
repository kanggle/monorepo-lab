import { NextResponse } from 'next/server';
import { getFxRateHistory } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger per-pair FX 환율 history read proxy (read-only — GET).
 * TASK-PC-FE-104 — FIN-BE-040 `GET /api/finance/ledger/fx-rates/{foreignCurrency}/history`.
 *
 * The HttpOnly domain-facing IAM OIDC access token is attached server-side in
 * `getFxRateHistory()` (§ 2.4.7.1 reusing the § 2.4.7 / § 2.4.5 rule — NOT the
 * operator token). Per-pair: the `[foreignCurrency]` path param is URL-decoded
 * by Next.js before it arrives here; we pass it straight to `getFxRateHistory`,
 * which re-encodes. The optional `?limit=` query is parsed to a number (NaN /
 * absent → undefined, so the producer applies its default of 50; the producer
 * also floors `≤0` to 1 and caps at 500 — double-defended by the client hook).
 *
 * READ-ONLY: GET only, no mutation branch. An unknown / never-polled foreign
 * code passes through as a normal `200` (`quotes: []` — NOT a 404).
 *
 * STRICTLY GET — NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id,
 * NO body. NO 429 branch (the ledger has no documented 429).
 *
 * F5: `rate` is a decimal string on the wire and stays a string throughout the
 * proxy — no Number/parseFloat coercion at any layer.
 * F7: the foreign currency is never logged (the api layer sanitises the path).
 */
export async function GET(
  req: Request,
  { params }: { params: Promise<{ foreignCurrency: string }> },
) {
  const requestId = newRequestId();
  const { foreignCurrency } = await params;
  const limitRaw = new URL(req.url).searchParams.get('limit');
  // `limit` is a row count (NOT money — the F5 invariant is amount/rate-only);
  // a non-numeric / absent value degrades to `undefined` (producer default 50).
  const parsed = limitRaw !== null ? Number(limitRaw) : NaN;
  const limit = Number.isFinite(parsed) ? parsed : undefined;
  try {
    const result = await getFxRateHistory(foreignCurrency, limit);
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
