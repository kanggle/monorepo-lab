import {
  FxRatesRefreshResponseSchema,
  type FxRatesRefreshResponse,
} from './types';
import { callLedger } from './ledger-client';

// ---------------------------------------------------------------------------
// FX 환율 수동 refresh — TASK-MONO-300 (Scope B)
//   POST /api/finance/ledger/fx-rates/refresh
//   Producer envelope = { data: { feedEnabled, refreshed }, meta }.
//
//   Header matrix (producer-faithful — § 2.4.7.1):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - NO request body (the POST carries no input — the refresh is
//       unconditional; `callLedger` will NOT add Content-Type: application/json
//       when `body` is undefined);
//     - NO `X-Tenant-Id` (tenant from the JWT claim — same as all ledger calls);
//     - NO `Idempotency-Key` (the use case is idempotent upsert last-write-wins;
//       the producer defines no idempotency key for this endpoint);
//     - NO `X-Operator-Reason` (no body, no reason field).
//
//   Feed-disabled: a 200 no-op (`feedEnabled:false, refreshed:0`); the
//   producer never 4xx/5xx a disabled-feed refresh (best-effort invariant).
//
//   Errors map via the SAME taxonomy as the reads: 401 → ApiError (whole-
//   session re-login); 403 → ApiError (token not finance-scoped); 503/timeout
//   → LedgerUnavailableError (ONLY the ledger section degrades); any other
//   !ok → ApiError (inline actionable, no crash).
//
//   F5: `refreshed` is a plain integer count (NOT money — z.number() is
//   correct; F5 is amount/rate-only). No `rate` string on this path.
// ---------------------------------------------------------------------------

/**
 * `refreshFxRates()` — triggers a manual on-demand refresh of the FX rate
 * feed cache via the finance ledger-service. Server-only (same posture as
 * `getFxRates()` + `resolveDiscrepancy()`). The domain-facing IAM OIDC token
 * is attached server-side in `callLedger`; NEVER `getOperatorToken()`.
 *
 * Returns `{ feedEnabled, refreshed }` — the feed-enabled flag + the count
 * of pairs upserted. A feed-disabled result (`feedEnabled:false, refreshed:0`)
 * is a normal 200 (not an error).
 *
 * No request body — the POST is unconditional (no input required); callLedger
 * will not add Content-Type when `body` is undefined.
 */
export async function refreshFxRates(): Promise<FxRatesRefreshResponse> {
  return callLedger(
    {
      path: '/api/finance/ledger/fx-rates/refresh',
      logPath: '/api/finance/ledger/fx-rates/refresh',
      method: 'POST',
      // No body — the refresh is unconditional; Content-Type omitted.
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return FxRatesRefreshResponseSchema.parse(env.data);
    },
  );
}
