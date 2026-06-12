import { LedgerUnavailableError } from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the finance ledger-ops same-origin proxy
 * routes (console-integration-contract § 2.4.7.1 / § 2.5). The HttpOnly
 * **domain-facing IAM OIDC access token** is attached server-side in
 * `ledger-api.ts` — NOT the IAM exchanged operator token (the ledger
 * requires the IAM OIDC token; the #569 invariant is GAP-domain-scoped —
 * the § 2.4.5 rule reused via the § 2.4.7 finance binding, NOT
 * re-derived). Mirrors the FE-009 finance `_proxy` shape for the flat
 * envelope.
 *
 * STRICTLY READ-ONLY: these proxies expose ONLY GET routes — there is NO
 * mutation route (no ledger write, no Idempotency-Key, no
 * X-Operator-Reason, no request body, no manual-posting / revaluation /
 * settlement / statement-ingest / discrepancy-resolve surface).
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION re-login;
 *     no partial authed state — NOT a per-section degrade).
 *   - 403 → 403 (token not finance-scoped → inline "not available / not
 *     scoped").
 *   - 404 → passthrough (inline actionable — typically
 *     `JOURNAL_ENTRY_NOT_FOUND` / `ACCOUNTING_PERIOD_NOT_FOUND` /
 *     `RECONCILIATION_DISCREPANCY_NOT_FOUND`, no crash).
 *   - 400 / 422 → passthrough (inline actionable, no crash).
 *   - **NO 429 handling** (§ 2.4.7.1): the ledger has no documented
 *     rate-limit response; a stray 429 lands here as an `ApiError` →
 *     passthrough to the client (NOT a Retry-After branch, NOT a bounded
 *     backoff — a fabricated backoff would be cargo-culted from scm
 *     § 2.4.6, asserted absent by test).
 *   - 503 / timeout / network → 503 (ONLY the ledger section degrades; the
 *     console shell + IAM / wms / scm / finance-account / erp sections stay
 *     intact).
 *
 * No token / ledger data is ever logged (confidential + F7).
 */
export const mapLedgerError = makeProxyErrorMapper('ledger', LedgerUnavailableError);

export { newRequestId };
