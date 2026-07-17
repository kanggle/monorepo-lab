import { FinanceUnavailableError } from '@/shared/api/errors';
import {
  callFlatEnvelopeGateway,
  type FlatEnvelopeGatewayProfile,
} from '@/shared/api/flat-envelope-gateway';
import {
  AccountSchema,
  type Account,
  BalancesResponseSchema,
  type BalancesResponse,
  TransactionsResponseSchema,
  type TransactionsResponse,
  type TransactionsQueryParams,
  FINANCE_DEFAULT_PAGE_SIZE,
  FINANCE_MAX_PAGE_SIZE,
} from './types';

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

interface CallOptions {
  /** Path relative to `${FINANCE_BASE_URL}` (e.g.
   *  `/api/finance/accounts/{id}`). */
  path: string;
  /** Sanitised path shape for logging (no accountId / no PII —
   *  e.g. `/api/finance/accounts/{id}/balances`). */
  logPath: string;
}

/**
 * finance profile for the shared {@link callFlatEnvelopeGateway} core: degrades
 * via {@link FinanceUnavailableError} and logs `finance_*` events against the
 * finance `account-service` at `${FINANCE_BASE_URL}` (timeout
 * `FINANCE_TIMEOUT_MS`). No rate-limit policy (finance documents no 429).
 */
const FINANCE_PROFILE: FlatEnvelopeGatewayProfile = {
  logPrefix: 'finance',
  requestFailedLabel: 'finance request failed',
  resolveDefaults: (env) => ({
    baseUrl: env.FINANCE_BASE_URL,
    timeoutMs: env.FINANCE_TIMEOUT_MS,
  }),
  makeUnavailable: (reason, code, message) =>
    new FinanceUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof FinanceUnavailableError,
  messages: {
    degraded: 'finance unavailable',
    timeout: 'finance call timed out',
    network: 'finance call failed',
  },
};

/**
 * Single hardened call site — a thin GET wrapper over the shared
 * {@link callFlatEnvelopeGateway} core with the {@link FINANCE_PROFILE}.
 * Read-only: no method/body is passed, so the core sends a GET with no
 * `Content-Type` / mutation headers.
 */
async function callFinance<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const { raw } = await callFlatEnvelopeGateway(
    { path: opts.path, logPath: opts.logPath },
    parse,
    FINANCE_PROFILE,
  );
  return raw;
}

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
// account by id — GET /api/finance/accounts/{accountId}
//   account-api.md envelope = { data: Account, meta }. READ-ONLY.
// ---------------------------------------------------------------------------

export async function getAccount(accountId: string): Promise<Account> {
  return callFinance(
    {
      path: `/api/finance/accounts/${encodeURIComponent(accountId)}`,
      // confidential / F7 — the log path carries NO accountId.
      logPath: '/api/finance/accounts/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return AccountSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// balances — GET /api/finance/accounts/{accountId}/balances
//   account-api.md envelope = { data: [ Balance ], meta }. READ-ONLY.
// ---------------------------------------------------------------------------

export async function getBalances(
  accountId: string,
): Promise<BalancesResponse> {
  return callFinance(
    {
      path: `/api/finance/accounts/${encodeURIComponent(accountId)}/balances`,
      logPath: '/api/finance/accounts/{id}/balances',
    },
    (json) => BalancesResponseSchema.parse(json),
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
