import { FinanceUnavailableError } from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping for the finance-ops same-origin proxy
 * routes (console-integration-contract § 2.4.7 / § 2.5). The HttpOnly
 * **GAP OIDC access token** is attached server-side in
 * `finance-api.ts` — NOT the GAP exchanged operator token (finance
 * requires the GAP OIDC token; the #569 invariant is
 * GAP-domain-scoped — the § 2.4.5 rule reused, NOT re-derived).
 * Mirrors the FE-008 scm `_proxy` shape for the flat envelope.
 *
 * STRICTLY READ-ONLY: these proxies expose ONLY GET routes — there
 * is NO mutation route (no finance write, no Idempotency-Key, no
 * X-Operator-Reason, no request body, no v2 admin-service surface).
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION
 *     re-login; no partial authed state — NOT a per-section degrade).
 *   - 403 → 403 (token not finance-scoped → inline "not available /
 *     not scoped").
 *   - 404 → passthrough (inline actionable — typically
 *     `ACCOUNT_NOT_FOUND`, no crash).
 *   - 400 / 422 → passthrough (inline actionable, no crash).
 *   - **NO 429 handling** (§ 2.4.7): finance has no documented
 *     rate-limit response; a stray 429 lands here as an `ApiError`
 *     → passthrough to the client (NOT a Retry-After branch, NOT a
 *     bounded backoff — a fabricated backoff would be cargo-culted
 *     from scm § 2.4.6, asserted absent by test).
 *   - 503 / timeout / network → 503 (ONLY the finance section
 *     degrades; the console shell + GAP / wms / scm sections stay
 *     intact).
 *
 * No token / finance data is ever logged (confidential + F7).
 */
export const mapFinanceError = makeProxyErrorMapper('finance', FinanceUnavailableError);

export { newRequestId };
