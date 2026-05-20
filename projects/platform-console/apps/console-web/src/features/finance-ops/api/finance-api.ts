import { getServerEnv } from '@/shared/config/env';
import { getAccessToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, FinanceUnavailableError } from '@/shared/api/errors';
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
 * ADR-MONO-013 Phase 5, the THIRD non-GAP federated domain — closes the
 * non-GAP federation cycle: wms → scm → finance). STRICTLY READ-ONLY.
 *
 * Server-only by construction (same posture as `scm-api.ts` / `wms-api.ts` /
 * `accounts-api.ts`): imported exclusively from server components and the
 * `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The token + any data never reach client JS — client
 * components call the same-origin `/api/finance/**` proxy routes, which
 * attach the HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of the § 2.4.5 rule (NOT re-derived) ──
 *
 * The normative per-domain credential rule is DEFINED in
 * console-integration-contract § 2.4.5 (each binding declares its own
 * credential against its producer; never blanket-apply one domain's auth
 * to another). finance REUSES it with the SAME outcome as wms / scm: the
 * finance `account-service` validates a GAP RS256 JWT (ADR-001) against
 * GAP JWKS, `tenant_id ∈ { finance, * }` enforced producer-side from
 * the JWT claim (finance `gap-integration.md` § platform-console
 * Operator Read Consumer — TASK-FIN-BE-005). finance has NO
 * token-exchange.
 *
 * Therefore this client uses `getAccessToken()` (the GAP-session
 * HttpOnly cookie) and NEVER `getOperatorToken()` — exactly like
 * `scm-api.ts` / `wms-api.ts`, and the EXACT INVERSE of the GAP
 * `features/{accounts,audit,operators,dashboards}` clients. The #569
 * trust-boundary invariant is GAP-domain-scoped and does NOT generalise
 * to finance. A test pins this (the `getOperatorToken` path MUST be
 * absent for finance; the cross-domain regression covers
 * GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC / finance=GAP-OIDC).
 *
 * Tenant invariant (§ 2.4.7 / reuse of § 2.4.5): finance resolves the
 * tenant from the JWT `tenant_id` claim (`∈ {finance,*}`) — NOT an
 * `X-Tenant-Id` header. The console therefore does NOT send
 * `X-Tenant-Id` to finance; the tenant rides inside the GAP OIDC token.
 * finance rejects cross-tenant producer-side (`403 TENANT_FORBIDDEN`).
 *
 * READ-ONLY (§ 2.4.7, NORMATIVE): every call is a pure GET. There is
 * NO mutation anywhere — NO `Idempotency-Key`, NO `X-Operator-Reason`,
 * NO body, NO finance write call (`POST /accounts` / `/kyc/upgrade` /
 * `/holds` / `/holds/{holdId}/capture|release` / `/transfers`), NO v2
 * `admin-service` surface. Carrying the FE-007 alert-ack OR the GAP
 * § 2.4.1 mutation scaffolding here is a defect (tests assert their
 * absence). finance v1 has NO account list/search GET — this client
 * intentionally exposes no list/search function (account-id-driven —
 * the honest finance constraint).
 *
 * Error envelope (§ 2.4.7 / § 2.5): finance uses the FLAT shape
 * `{ code, message, details?, timestamp }` — DISTINCT from wms's
 * NESTED `{ error: { code … } }`. The wire shape is byte-identical to
 * scm's flat envelope, but finance is a DISTINCT producer with its
 * own error-code vocabulary (e.g. `ACCOUNT_NOT_FOUND`,
 * `TENANT_FORBIDDEN`). `parseFinanceError()` reads the flat shape
 * against the finance vocabulary. A test demonstrates that a
 * wms-nested body is NOT mis-parsed (no accidental GAP/wms parser
 * cross-wire).
 *
 * **NO 429 handling** (§ 2.4.7, honest difference from scm § 2.4.6):
 * `account-api.md` § Error code → HTTP status has NO `429` entry. This
 * client does NOT add a Retry-After / backoff branch. If finance ever
 * returns a `429` it falls through to the default-error path (a
 * surfaced `ApiError` — the UI rendering it as an unexpected error is
 * better than a fabricated backoff). A test asserts the absence
 * (mock-feeds a 429 and verifies the client did NOT retry-storm and
 * did NOT take a Retry-After branch).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard
 * timeout (no unbounded default); `401` → `ApiError` (forced
 * WHOLE-SESSION GAP re-login — not a per-section degrade); `403` →
 * `ApiError` (inline "not available / not scoped"); `404
 * ACCOUNT_NOT_FOUND` → `ApiError` (inline actionable "no such
 * account"); `400` / `422` → `ApiError` (inline actionable, no crash);
 * `503` / timeout / network → `FinanceUnavailableError` (ONLY the
 * finance section degrades — shell + GAP/wms/scm sections intact).
 *
 * Confidential / F7 (§ 2.4.7): structured logs are server-side only;
 * the GAP access token and any finance data (balances, transactions,
 * account refs, owner refs) are NEVER logged (redacted) — the log
 * payloads below carry ONLY `requestId` + `path` (no `accountId`
 * even in path-encoded form leaks because the log field is the
 * sanitised route shape, not the URL).
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
 * Parses the finance FLAT error envelope
 * (`{ code, message, details?, timestamp }`). Defensive: a missing /
 * nested (wms-shaped) / non-JSON body degrades to a synthetic code
 * rather than throwing (the producer is the authority for the real
 * code; this never crashes the console on a malformed error body).
 * A wms-nested parser would MISS the finance flat `code` — this is
 * the per-domain envelope correctness pinned by tests. NOTE: finance
 * is a DISTINCT producer from scm — even though the wire shape is
 * byte-identical, this parser is intentionally finance-local (its
 * `details` field is preserved for downstream rendering).
 */
async function parseFinanceError(
  res: Response,
): Promise<{
  code: string;
  message: string;
  details?: unknown;
  timestamp?: string;
}> {
  let code = `HTTP_${res.status}`;
  let message = `finance request failed (${res.status})`;
  let details: unknown | undefined;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as {
      code?: string;
      message?: string;
      details?: unknown;
      timestamp?: string;
    };
    if (body && typeof body === 'object') {
      if (typeof body.code === 'string') code = body.code;
      if (typeof body.message === 'string') message = body.message;
      if ('details' in body) details = body.details;
      if (typeof body.timestamp === 'string') timestamp = body.timestamp;
    }
  } catch {
    /* keep the synthetic defaults — never throw on a bad error body */
  }
  return { code, message, details, timestamp };
}

/**
 * Single hardened call site. Resolves the GAP OIDC access token,
 * applies the timeout, maps the finance FLAT error envelope to the
 * § 2.5 resilience taxonomy.
 *
 * **No 429 / Retry-After / backoff branch** (§ 2.4.7, honest difference
 * from scm). If finance ever returned a 429 it would surface as a
 * generic `ApiError(429, …)` through the default `!res.ok` path — but
 * never as a retry, never as a `Retry-After` honour. A test asserts
 * this absence.
 */
async function callFinance<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential (§ 2.4.7 reusing § 2.4.5): finance requires
  //    the GAP OIDC ACCESS token directly. NEVER getOperatorToken() —
  //    that is the GAP-domain (§ 2.6 exchanged) credential; finance
  //    would reject it (wrong issuer/type). The #569 invariant is
  //    GAP-domain-scoped.
  const token = await getAccessToken();
  if (!token) {
    logger.warn('finance_no_gap_session', {
      requestId,
      path: opts.logPath,
    });
    // No GAP OIDC session ⇒ whole-session re-login (not a per-section
    // degrade — no partial authed state).
    throw new ApiError(401, 'UNAUTHORIZED', 'No GAP session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // NOTE: deliberately NO `X-Tenant-Id` — finance resolves tenant
    // from the JWT `tenant_id` claim (§ 2.4.7 reuse of the § 2.4.5
    // divergence).
    // NOTE: read-only — NO `Idempotency-Key`, NO `X-Operator-Reason`,
    // NO `Content-Type` (no body).
  };

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.FINANCE_TIMEOUT_MS,
  );
  try {
    const res = await fetch(`${env.FINANCE_BASE_URL}${opts.path}`, {
      method: 'GET',
      headers,
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseFinanceError(res);
      logger.warn('finance_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.logPath,
      });
      // GAP OIDC session expired → whole-session re-login (no partial
      // authed state — NOT a per-section degrade).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseFinanceError(res);
      logger.warn('finance_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.logPath,
      });
      // Token not finance-scoped / insufficient scope → inline "not
      // available / not scoped" (no crash, no re-login loop).
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseFinanceError(res);
      logger.warn('finance_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.logPath,
      });
      // ONLY the finance section degrades — shell + GAP/wms/scm
      // sections intact.
      throw new FinanceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'finance unavailable',
      );
    }

    if (!res.ok) {
      // 400/422 VALIDATION_ERROR, 404 ACCOUNT_NOT_FOUND, etc. — inline
      // actionable (no crash). NOTE — a 429 from finance would land
      // HERE (no Retry-After / backoff branch; finance has no
      // documented 429 — the absence is an honest difference from
      // scm § 2.4.6, asserted by test).
      const e = await parseFinanceError(res);
      logger.warn('finance_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    // Log ONLY status + the sanitised path — NEVER the response body
    // (confidential + F7).
    logger.info('finance_ok', {
      requestId,
      status: res.status,
      path: opts.logPath,
    });
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || err instanceof FinanceUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('finance_timeout', {
        requestId,
        timeoutMs: env.FINANCE_TIMEOUT_MS,
        path: opts.logPath,
      });
      throw new FinanceUnavailableError(
        'timeout',
        'TIMEOUT',
        'finance call timed out',
      );
    }
    logger.error('finance_error', { requestId, path: opts.logPath });
    throw new FinanceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'finance call failed',
    );
  } finally {
    clearTimeout(timer);
  }
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
