import { getServerEnv } from '@/shared/config/env';
import { getOperatorToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, AccountsUnavailableError } from '@/shared/api/errors';
import {
  AccountPageSchema,
  type AccountPage,
  type AccountSearchParams,
  LockResultSchema,
  type LockResult,
  UnlockResultSchema,
  type UnlockResult,
  BulkLockResultSchema,
  type BulkLockResult,
  RevokeSessionResultSchema,
  type RevokeSessionResult,
  GdprDeleteResultSchema,
  type GdprDeleteResult,
  AccountExportSchema,
  type AccountExport,
  type MutationReason,
} from './types';

/**
 * Server-side IAM admin-service accounts client (TASK-PC-FE-002).
 *
 * Server-only by construction (same posture as `registry-client.ts` /
 * `operator-token-exchange.ts`): imported exclusively from server components
 * and the `runtime = 'nodejs'` route handlers; `getServerEnv()` throws
 * outside the server runtime. The operator token + account PII never reach
 * client JS — client components call the same-origin `/api/accounts/**`
 * proxy routes, which attach the HttpOnly operator token here server-side.
 *
 * Auth invariant (console-integration-contract § 2.1/§ 2.4.1 — the #569
 * trust boundary): every call authenticates with the EXCHANGED operator
 * token (`getOperatorToken()`), NEVER the IAM OIDC access token. An absent
 * operator token ⇒ no usable operator session ⇒ `401 TOKEN_INVALID` (the
 * caller re-logins; the fetch is NOT made — no silent GAP-token fallback).
 *
 * Tenant invariant (§ 2.4 / multi-tenant): the operator's selected active
 * tenant is always sent as `X-Tenant-Id` (`getActiveTenant()`). When none is
 * selected the call is blocked with `400 NO_ACTIVE_TENANT` — never an empty
 * header (no cross-tenant leak).
 *
 * Mutation invariant (§ 2.4.1 / audit-heavy / integration-heavy I4): every
 * mutation carries the operator-entered `X-Operator-Reason` (+ body `reason`)
 * and a caller-supplied `Idempotency-Key` (stable across one confirmed
 * action, fresh per a new attempt). The api layer NEVER fabricates a reason —
 * the UI must collect it; an empty reason is rejected here before any fetch.
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * 401/403 → `ApiError` (forced re-login); 503/timeout →
 * `AccountsUnavailableError` (accounts section degrades only);
 * 400/404/409/422 → `ApiError` (inline actionable, no crash).
 *
 * Logging: structured, server-side only; the operator token and account PII
 * (emails) are NEVER logged (redacted) — § 2.6 logging invariant.
 */

const ADMIN_PREFIX = '/api/admin';

interface CallOptions {
  method: 'GET' | 'POST';
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (mutations only). */
  reason?: string;
  /** Stable per a single confirmed action → `Idempotency-Key`. */
  idempotencyKey?: string;
  /** JSON body (mutations). */
  body?: unknown;
}

/** Redaction guard — never let an account email reach a structured log. */
function logAccountRef(accountId: string): string {
  return accountId;
}

/**
 * Single hardened call site. Resolves the operator token + active tenant,
 * applies the timeout, and maps the producer error envelope to the
 * §2.5 resilience taxonomy.
 */
async function callGapAdmin<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Trust boundary: the /api/admin/** credential is the EXCHANGED operator
  // token — never the IAM OIDC access token. Absent ⇒ 401, no fetch.
  const token = await getOperatorToken();
  if (!token) {
    logger.warn('accounts_no_operator_session', { requestId, path: opts.path });
    throw new ApiError(401, 'TOKEN_INVALID', 'No operator session');
  }

  // Multi-tenant: always send the selected tenant; block (no empty header)
  // when none is selected — never a cross-tenant / unscoped call.
  const tenant = await getActiveTenant();
  if (!tenant) {
    logger.warn('accounts_no_active_tenant', { requestId, path: opts.path });
    throw new ApiError(
      400,
      'NO_ACTIVE_TENANT',
      'No active tenant selected',
    );
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Tenant-Id': tenant,
    'X-Request-Id': requestId,
  };

  // Mutation audit + idempotency (§ 2.4.1). The reason MUST be non-empty —
  // the UI's reason-capture gate is the source; this is the fail-safe.
  if (opts.method === 'POST') {
    const reason = opts.reason?.trim() ?? '';
    if (reason === '') {
      logger.warn('accounts_mutation_no_reason', {
        requestId,
        path: opts.path,
      });
      throw new ApiError(
        400,
        'REASON_REQUIRED',
        'An operator reason is required for this action',
      );
    }
    if (!opts.idempotencyKey) {
      throw new ApiError(
        400,
        'VALIDATION_ERROR',
        'An idempotency key is required for this action',
      );
    }
    // TASK-MONO-176: percent-encode so a non-Latin-1 reason (e.g. Korean)
    // does not make `fetch()` throw on the ByteString header; the producer
    // (OperatorReasonDecodingFilter) decodes it back to the original UTF-8.
    headers['X-Operator-Reason'] = encodeURIComponent(reason);
    headers['Idempotency-Key'] = opts.idempotencyKey;
  }

  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ACCOUNTS_TIMEOUT_MS);

  try {
    const res = await fetch(`${env.IAM_ADMIN_API_BASE}${opts.path}`, {
      method: opts.method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401 || res.status === 403) {
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
      };
      logger.warn('accounts_unauthorized', {
        requestId,
        status: res.status,
        code: errBody.code,
        path: opts.path,
      });
      // No partial authed state — caller forces a clean re-login.
      throw new ApiError(
        res.status,
        errBody.code ?? (res.status === 401 ? 'TOKEN_INVALID' : 'PERMISSION_DENIED'),
        res.status === 401 ? 'session expired' : 'not permitted',
      );
    }

    if (res.status === 503) {
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
      };
      const code = errBody.code ?? 'DOWNSTREAM_ERROR';
      logger.warn('accounts_degraded', {
        requestId,
        status: 503,
        code,
        path: opts.path,
      });
      // Accounts section degrades only — shell stays intact (§ 2.5).
      throw new AccountsUnavailableError(
        code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        code,
        'IAM accounts service unavailable',
      );
    }

    if (!res.ok) {
      // 400 STATE_TRANSITION_INVALID / REASON_REQUIRED / VALIDATION_ERROR,
      // 404 ACCOUNT_NOT_FOUND, 409 IDEMPOTENCY_KEY_CONFLICT,
      // 422 BATCH_SIZE_EXCEEDED → inline actionable error (no crash).
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
        timestamp?: string;
      };
      logger.warn('accounts_request_error', {
        requestId,
        status: res.status,
        code: errBody.code,
        path: opts.path,
      });
      throw new ApiError(
        res.status,
        errBody.code ?? `HTTP_${res.status}`,
        errBody.message ?? `accounts request failed (${res.status})`,
        errBody.timestamp,
      );
    }

    const json = await res.json();
    logger.info('accounts_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || err instanceof AccountsUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('accounts_timeout', {
        requestId,
        timeoutMs: env.ACCOUNTS_TIMEOUT_MS,
        path: opts.path,
      });
      throw new AccountsUnavailableError(
        'timeout',
        'TIMEOUT',
        'IAM accounts call timed out',
      );
    }
    logger.error('accounts_error', { requestId, path: opts.path });
    throw new AccountsUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'IAM accounts call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// 1. search / list — GET /api/admin/accounts
// ---------------------------------------------------------------------------

export async function searchAccounts(
  params: AccountSearchParams = {},
): Promise<AccountPage> {
  const qs = new URLSearchParams();
  if (params.email && params.email.trim() !== '') {
    qs.set('email', params.email.trim());
  } else {
    qs.set('page', String(params.page ?? 0));
    qs.set('size', String(params.size ?? 20));
  }
  // TASK-BE-357: scope the search/list to the active tenant (mirror of the audit
  // view — `audit-api.ts` TASK-PC-FE-043). The producer scopes by this `tenantId`
  // query param (NOT `X-Tenant-Id`) and gates it against the operator's effective
  // scope (403 TENANT_SCOPE_DENIED → surfaced inline by the accounts screen). An
  // explicit `params.tenantId` (SUPER_ADMIN cross-tenant) overrides. A missing
  // active tenant is blocked in `callGapAdmin` (400 NO_ACTIVE_TENANT) before fetch.
  const tenant = await getActiveTenant();
  const scopeTenant = params.tenantId ?? tenant;
  if (scopeTenant) qs.set('tenantId', scopeTenant);
  return callGapAdmin(
    { method: 'GET', path: `${ADMIN_PREFIX}/accounts?${qs.toString()}` },
    (json) => AccountPageSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 2. detail — derived from the search/list item (single-lookup by email is
//    the primary; an explicit id lookup is a 1-element list filter). The
//    producer has no dedicated GET-by-id, so detail composes the summary
//    plus the per-account ops below.
// ---------------------------------------------------------------------------

export async function getAccountByEmail(
  email: string,
): Promise<AccountPage> {
  return searchAccounts({ email });
}

// ---------------------------------------------------------------------------
// 3. lock — POST /api/admin/accounts/{accountId}/lock
// ---------------------------------------------------------------------------

export async function lockAccount(
  accountId: string,
  reason: MutationReason,
  idempotencyKey: string,
): Promise<LockResult> {
  return callGapAdmin(
    {
      method: 'POST',
      path: `${ADMIN_PREFIX}/accounts/${encodeURIComponent(accountId)}/lock`,
      reason: reason.reason,
      idempotencyKey,
      body: { reason: reason.reason, ticketId: reason.ticketId },
    },
    (json) => LockResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 4. unlock — POST /api/admin/accounts/{accountId}/unlock
// ---------------------------------------------------------------------------

export async function unlockAccount(
  accountId: string,
  reason: MutationReason,
  idempotencyKey: string,
): Promise<UnlockResult> {
  return callGapAdmin(
    {
      method: 'POST',
      path: `${ADMIN_PREFIX}/accounts/${encodeURIComponent(accountId)}/unlock`,
      reason: reason.reason,
      idempotencyKey,
      body: { reason: reason.reason, ticketId: reason.ticketId },
    },
    (json) => UnlockResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 5. bulk-lock — POST /api/admin/accounts/bulk-lock (partial-failure)
// ---------------------------------------------------------------------------

export async function bulkLockAccounts(
  accountIds: string[],
  reason: MutationReason,
  idempotencyKey: string,
): Promise<BulkLockResult> {
  return callGapAdmin(
    {
      method: 'POST',
      path: `${ADMIN_PREFIX}/accounts/bulk-lock`,
      reason: reason.reason,
      idempotencyKey,
      body: {
        accountIds,
        reason: reason.reason,
        ticketId: reason.ticketId,
      },
    },
    (json) => BulkLockResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 6. revoke-session — POST /api/admin/sessions/{accountId}/revoke
// ---------------------------------------------------------------------------

export async function revokeSessions(
  accountId: string,
  reason: MutationReason,
  idempotencyKey: string,
): Promise<RevokeSessionResult> {
  logger.debug('accounts_revoke_request', {
    account: logAccountRef(accountId),
  });
  return callGapAdmin(
    {
      method: 'POST',
      path: `${ADMIN_PREFIX}/sessions/${encodeURIComponent(accountId)}/revoke`,
      reason: reason.reason,
      idempotencyKey,
      body: { reason: reason.reason },
    },
    (json) => RevokeSessionResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 7. gdpr-delete — POST /api/admin/accounts/{accountId}/gdpr-delete
//    (irreversible — UI double-confirms + typed confirmation before this)
// ---------------------------------------------------------------------------

export async function gdprDeleteAccount(
  accountId: string,
  reason: MutationReason,
  idempotencyKey: string,
): Promise<GdprDeleteResult> {
  return callGapAdmin(
    {
      method: 'POST',
      path: `${ADMIN_PREFIX}/accounts/${encodeURIComponent(accountId)}/gdpr-delete`,
      reason: reason.reason,
      idempotencyKey,
      body: { reason: reason.reason, ticketId: reason.ticketId },
    },
    (json) => GdprDeleteResultSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 8. export — GET /api/admin/accounts/{accountId}/export
//    (unmasked PII; producer requires X-Operator-Reason — read but audited)
// ---------------------------------------------------------------------------

export async function exportAccount(
  accountId: string,
  reason: string,
): Promise<AccountExport> {
  const trimmed = reason.trim();
  if (trimmed === '') {
    throw new ApiError(
      400,
      'REASON_REQUIRED',
      'An operator reason is required for export',
    );
  }
  // export is GET but producer mandates X-Operator-Reason; not an
  // idempotency-bearing mutation (no Idempotency-Key per the contract).
  const env = getServerEnv();
  const requestId = newRequestId();
  const token = await getOperatorToken();
  if (!token) {
    throw new ApiError(401, 'TOKEN_INVALID', 'No operator session');
  }
  const tenant = await getActiveTenant();
  if (!tenant) {
    throw new ApiError(400, 'NO_ACTIVE_TENANT', 'No active tenant selected');
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ACCOUNTS_TIMEOUT_MS);
  try {
    const res = await fetch(
      `${env.IAM_ADMIN_API_BASE}${ADMIN_PREFIX}/accounts/${encodeURIComponent(accountId)}/export`,
      {
        method: 'GET',
        headers: {
          Accept: 'application/json',
          Authorization: `Bearer ${token}`,
          'X-Tenant-Id': tenant,
          // TASK-MONO-176: percent-encode (ByteString header safety); producer decodes.
          'X-Operator-Reason': encodeURIComponent(trimmed),
          'X-Request-Id': requestId,
        },
        cache: 'no-store',
        signal: controller.signal,
      },
    );

    if (res.status === 401 || res.status === 403) {
      const b = (await res.json().catch(() => ({}))) as { code?: string };
      throw new ApiError(
        res.status,
        b.code ?? (res.status === 401 ? 'TOKEN_INVALID' : 'PERMISSION_DENIED'),
        res.status === 401 ? 'session expired' : 'not permitted',
      );
    }
    if (res.status === 503) {
      const b = (await res.json().catch(() => ({}))) as { code?: string };
      const code = b.code ?? 'DOWNSTREAM_ERROR';
      throw new AccountsUnavailableError(
        code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        code,
        'IAM accounts service unavailable',
      );
    }
    if (!res.ok) {
      const b = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
        timestamp?: string;
      };
      throw new ApiError(
        res.status,
        b.code ?? `HTTP_${res.status}`,
        b.message ?? `export failed (${res.status})`,
        b.timestamp,
      );
    }
    logger.info('accounts_export_ok', {
      requestId,
      account: logAccountRef(accountId),
    });
    return AccountExportSchema.parse(await res.json());
  } catch (err) {
    if (err instanceof ApiError || err instanceof AccountsUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      throw new AccountsUnavailableError(
        'timeout',
        'TIMEOUT',
        'IAM accounts export timed out',
      );
    }
    throw new AccountsUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'IAM accounts export failed',
    );
  } finally {
    clearTimeout(timer);
  }
}
