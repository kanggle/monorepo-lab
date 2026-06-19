import { NextResponse } from 'next/server';
import { refreshFxRates } from '@/features/ledger-ops/api/ledger-api';
import { mapLedgerError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin finance ledger FX 환율 수동 refresh proxy (mutation — POST).
 * TASK-MONO-300 — `POST /api/finance/ledger/fx-rates/refresh` (Scope B).
 *
 * The HttpOnly domain-facing IAM OIDC access token is attached server-side
 * in `refreshFxRates()` (§ 2.4.7.1 reusing the § 2.4.7 / § 2.4.5 rule — NOT
 * the operator token). No request body — the refresh is unconditional;
 * `refreshFxRates()` sends a bodyless POST.
 *
 * Feed-disabled → 200 `{feedEnabled:false, refreshed:0}` (no-op consistent
 * with GET `feedEnabled:false, rates:[]` — NOT an error). Provider failure →
 * 200 with the count that succeeded (best-effort; never 500s).
 *
 * STRICTLY POST — NO Idempotency-Key, NO X-Operator-Reason, NO X-Tenant-Id.
 * No GET / PUT / PATCH / DELETE handler on this route.
 *
 * F5: `refreshed` is a plain integer count (NOT money — the F5 invariant is
 * amount/rate-only). No `rate` string on this path.
 *
 * Error mapping via `mapLedgerError` (same as all ledger proxy routes):
 *   401 → 401 (WHOLE-SESSION re-login; no partial authed state)
 *   403 → 403 (token not finance-scoped; inline actionable)
 *   503/timeout → 503 (ONLY ledger section degrades; shell intact)
 *   other → pass-through status code (inline actionable, no crash)
 */
export async function POST() {
  const requestId = newRequestId();
  try {
    const result = await refreshFxRates();
    return NextResponse.json(result);
  } catch (err) {
    return mapLedgerError(err, requestId);
  }
}
