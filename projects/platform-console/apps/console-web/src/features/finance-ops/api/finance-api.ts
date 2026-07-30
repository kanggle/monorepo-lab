import { callFinance } from '@/shared/api/finance-accounts-read';
import {
  TransactionsResponseSchema,
  type TransactionsResponse,
  type TransactionsQueryParams,
  FINANCE_DEFAULT_PAGE_SIZE,
  FINANCE_MAX_PAGE_SIZE,
} from './types';

/**
 * TASK-PC-FE-259 — `getAccount` / `getBalances` (and the hardened
 * {@link callFinance} call site + the FINANCE profile they use) were promoted
 * to `shared/api/finance-accounts-read.ts` because `features/finance-overview`
 * consumes the same two reads for the `/finance` landing snapshot, and
 * `architecture.md` § Forbidden Dependencies bars a `features/A → features/B`
 * import ("공유 가치는 `shared/` 로 승격"). They are re-exported here so this
 * module remains the finance-ops public client surface documented by
 * console-integration-contract § 2.4.7 — no consumer, proxy route or test
 * changed. `listTransactions` below has a single consumer and stays
 * feature-local, using the SAME shared call site.
 */
export { getAccount, getBalances } from '@/shared/api/finance-accounts-read';

/**
 * Server-side finance `account-service` operations client (TASK-PC-FE-009 —
 * ADR-MONO-013 Phase 5, the THIRD non-IAM federated domain). STRICTLY
 * READ-ONLY. As of TASK-PC-FE-243 the hardened call site is the shared
 * {@link callFlatEnvelopeGateway} FLAT-envelope core; this file supplies the
 * {@link FINANCE_PROFILE}. Behaviour is IDENTICAL to the pre-consolidation
 * per-client copy.
 *
 * Server-only by construction (same posture as `scm-api.ts` / `wms-api.ts` /
 * `accounts-api.ts`). The token + any data never reach client JS — client
 * components call the same-origin `/api/finance/**` proxy routes.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.5 ── the DOMAIN-FACING IAM OIDC
 * token (`getDomainFacingToken()`), NEVER `getOperatorToken()` (the #569
 * invariant is GAP-domain-scoped). finance resolves the tenant from the JWT
 * `tenant_id ∈ {finance,*}` claim producer-side — the console sends NO
 * `X-Tenant-Id`.
 *
 * READ-ONLY (§ 2.4.7, NORMATIVE): every call is a pure GET — NO
 * `Idempotency-Key`, NO `X-Operator-Reason`, NO body, NO finance write, NO v2
 * `admin-service` surface (tests assert their absence). finance v1 has NO
 * account list/search GET — this client exposes none (account-id-driven).
 *
 * Error envelope (§ 2.4.7 / § 2.5): finance uses the FLAT shape
 * `{ code, message, details?, timestamp }` (DISTINCT from wms's NESTED
 * `{ error: { code } }`); the shared parser reads the flat shape against the
 * finance vocabulary (`ACCOUNT_NOT_FOUND`, `TENANT_FORBIDDEN`). A wms-nested
 * body is NOT mis-parsed.
 *
 * NO rate-limit handling (§ 2.4.7, honest difference from scm § 2.4.6):
 * `account-api.md` documents no `429`; the profile supplies no rate-limit
 * policy, so a stray 429 surfaces through the default-error path as a plain
 * `ApiError` (no backoff, no Retry-After honour).
 *
 * Resilience (§ 2.5): AbortController hard timeout; `401` → whole-session
 * re-login `ApiError`; `403` → inline; `404 ACCOUNT_NOT_FOUND` / `400` / `422`
 * → inline `ApiError`; `503` / timeout / network → `FinanceUnavailableError`
 * (ONLY the finance section degrades).
 *
 * Confidential / F7 (§ 2.4.7): structured logs are server-side only; the token
 * and any finance data are NEVER logged — the log `path` carries the sanitised
 * `logPath` route shape (no `accountId`, even path-encoded).
 */

// ---------------------------------------------------------------------------
// pagination helper
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  // Number arithmetic on the **page/size index numbers** is fine — these
  // are NOT money amounts (F5 invariant is amount-only).
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        FINANCE_MAX_PAGE_SIZE,
        Math.max(1, size ?? FINANCE_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
}

// ---------------------------------------------------------------------------
// transactions — GET /api/finance/accounts/{accountId}/transactions
//   account-api.md envelope = { data: [ Transaction ],
//     meta: { page, size, totalElements, timestamp } }
//   Filters: ?page=&size=&type=&status=
// ---------------------------------------------------------------------------

export async function listTransactions(
  accountId: string,
  params: TransactionsQueryParams = {},
): Promise<TransactionsResponse> {
  const qs = new URLSearchParams();
  if (params.type) qs.set('type', params.type);
  if (params.status) qs.set('status', params.status);
  pageParams(qs, params.page, params.size);
  return callFinance(
    {
      path: `/api/finance/accounts/${encodeURIComponent(accountId)}/transactions?${qs.toString()}`,
      logPath: '/api/finance/accounts/{id}/transactions',
    },
    (json) => TransactionsResponseSchema.parse(json),
  );
}
