import { logger } from '@/shared/lib/logger';
import { ApiError } from '@/shared/api/errors';
import { callAdminGateway } from '@/shared/api/iam-gateway';
import {
  ADMIN_PREFIX,
  ACCOUNTS_PROFILE,
  callGapAdmin,
  searchAccounts,
} from '@/shared/api/iam-accounts-read';
import {
  type AccountPage,
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
 *
 * TASK-PC-FE-259 — the search/list READ (`searchAccounts`), the
 * `ACCOUNTS_PROFILE` and the hardened {@link callGapAdmin} call site were
 * promoted to `shared/api/iam-accounts-read.ts` because `features/dashboards`
 * (§ 2.4.4 composed overview) and `features/iam-overview` (`/iam` landing) also
 * consume the read, and `architecture.md` § Forbidden Dependencies bars a
 * `features/A → features/B` import ("공유 가치는 `shared/` 로 승격"). The read
 * is re-exported below so this module remains the accounts public client
 * surface pinned by console-integration-contract § 3.1 **row 1**
 * (`features/accounts` `searchAccounts`) — the 16-row parity attestation is
 * unchanged. Every MUTATION below (§ 3.1 rows 3–8) stays feature-local and
 * uses the SAME shared call site.
 */
export { searchAccounts } from '@/shared/api/iam-accounts-read';

/** Redaction guard — never let an account email reach a structured log. */
function logAccountRef(accountId: string): string {
  return accountId;
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
