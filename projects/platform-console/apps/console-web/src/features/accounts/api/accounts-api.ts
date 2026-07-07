import { getActiveTenant } from '@/shared/lib/session';
import { logger } from '@/shared/lib/logger';
import { ApiError, AccountsUnavailableError } from '@/shared/api/errors';
import {
  callAdminGateway,
  type AdminGatewayProfile,
} from '@/shared/api/iam-gateway';
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
 * Server-side IAM admin-service accounts client — a thin wrapper over the shared
 * {@link callAdminGateway} core (TASK-PC-FE-208 dedup; originally
 * TASK-PC-FE-002).
 *
 * Auth invariant (console-integration-contract § 2.1/§ 2.4.1 — the #569 trust
 * boundary): every call authenticates with the EXCHANGED operator token
 * (`getOperatorToken()`), NEVER the IAM OIDC access token. Absent ⇒
 * `401 TOKEN_INVALID`, no fetch. The active tenant is always sent as
 * `X-Tenant-Id` (`getActiveTenant()`); absent ⇒ `400 NO_ACTIVE_TENANT`.
 *
 * Mutation invariant (§ 2.4.1): every mutation (POST) carries the
 * operator-entered `X-Operator-Reason` (+ body `reason`) AND a caller-supplied
 * `Idempotency-Key` — enforced by the profile's `forceMutationHeaders` (an empty
 * reason / missing key is rejected here before any fetch; the api layer NEVER
 * fabricates a reason).
 *
 * Resilience (§ 2.5): AbortController hard timeout; 401/403 → `ApiError` (forced
 * re-login — `forbiddenMode: 'auth'`); 503/timeout → {@link
 * AccountsUnavailableError} (accounts section degrades only); 400/404/409/422 →
 * `ApiError` (inline actionable). Logging: structured, server-side only; the
 * operator token and account PII (emails) are NEVER logged (redacted).
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
 * accounts profile for the shared {@link callAdminGateway} core: the IAM
 * accounts surface (`ACCOUNTS_TIMEOUT_MS`) that degrades via
 * {@link AccountsUnavailableError} and logs `accounts_*` events. Unlike the
 * newer operators/partnerships clients, accounts (a) handles 403 TOGETHER with
 * 401 (`forbiddenMode: 'auth'`) and (b) FORCES `X-Operator-Reason` +
 * `Idempotency-Key` on every mutation (`forceMutationHeaders: true`).
 */
const ACCOUNTS_PROFILE: AdminGatewayProfile = {
  logPrefix: 'accounts',
  requestFailedLabel: 'accounts request failed',
  resolveTimeoutMs: (env) => env.ACCOUNTS_TIMEOUT_MS,
  makeUnavailable: (reason, code, message) =>
    new AccountsUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof AccountsUnavailableError,
  messages: {
    degraded: 'IAM accounts service unavailable',
    timeout: 'IAM accounts call timed out',
    network: 'IAM accounts call failed',
  },
  forbiddenMode: 'auth',
  forceMutationHeaders: true,
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callAdminGateway} core with the {@link ACCOUNTS_PROFILE}.
 */
async function callGapAdmin<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  return callAdminGateway(
    {
      method: opts.method,
      path: opts.path,
      reason: opts.reason,
      idempotencyKey: opts.idempotencyKey,
      body: opts.body,
    },
    parse,
    ACCOUNTS_PROFILE,
  );
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
    // TASK-BE-475 / TASK-PC-FE-181: optional lifecycle-status filter — list branch
    // only (the producer ignores it on the email single-lookup). Absent ⇒ all statuses.
    if (params.status && params.status.trim() !== '') {
      qs.set('status', params.status.trim());
    }
  }
  // TASK-BE-357: scope the search/list to the active tenant (mirror of the audit
  // view — `audit-api.ts` TASK-PC-FE-043). The producer scopes by this `tenantId`
  // query param (NOT `X-Tenant-Id`) and gates it against the operator's effective
  // scope (403 TENANT_SCOPE_DENIED → surfaced inline by the accounts screen). An
  // explicit `params.tenantId` (SUPER_ADMIN cross-tenant) overrides. A missing
  // active tenant is blocked in the gateway (400 NO_ACTIVE_TENANT) before fetch.
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
//    (unmasked PII; producer requires X-Operator-Reason — a GET-with-reason,
//     NOT an idempotency-bearing mutation, so no Idempotency-Key per the
//     contract. The `failLabel` preserves the '<...> (status)' !ok message.)
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
  // GET-with-reason: the shared core sets X-Operator-Reason (percent-encoded)
  // when a reason is supplied, and no Idempotency-Key (none passed). `failLabel`
  // preserves the original 'export failed (<status>)' !ok fallback message.
  return callAdminGateway(
    {
      method: 'GET',
      path: `${ADMIN_PREFIX}/accounts/${encodeURIComponent(accountId)}/export`,
      reason: trimmed,
      failLabel: 'export failed',
    },
    (json) => AccountExportSchema.parse(json),
    ACCOUNTS_PROFILE,
  );
}
