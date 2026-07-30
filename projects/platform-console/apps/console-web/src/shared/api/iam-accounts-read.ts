import { getActiveTenant } from '@/shared/lib/session';
import { AccountsUnavailableError } from './errors';
import { callAdminGateway, type AdminGatewayProfile } from './iam-gateway';
import {
  AccountPageSchema,
  type AccountPage,
  type AccountSearchParams,
} from './iam-accounts-types';

/**
 * Shared server-side IAM `admin-service` **accounts READ** client —
 * `GET /api/admin/accounts` (search / list) + its wire shapes
 * (TASK-PC-FE-002 producer binding / TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * `searchAccounts` has **three** consuming features: `features/accounts`
 * (the `/accounts` operator screen), `features/dashboards` (the § 2.4.4
 * composed operator overview) and `features/iam-overview` (the `/iam`
 * landing snapshot). The latter two previously imported it straight out of
 * `@/features/accounts/api/accounts-api`, which `architecture.md`
 * § Forbidden Dependencies bars —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * Same promotion pattern as `shared/api/rbac-catalog.ts`, whose header cites
 * the same rule for the same reason.
 *
 * ── ONLY THE READ MOVED (deliberate) ──
 * The privilege-sensitive accounts MUTATIONS (lock / unlock / bulk-lock /
 * revoke-session / gdpr-delete / export) have a single consuming feature and
 * stay in `features/accounts/api/accounts-api.ts`, where
 * `console-integration-contract.md` § 3.1 rows 3–8 place them. That module
 * imports {@link ACCOUNTS_PROFILE} + {@link callGapAdmin} from here so every
 * accounts call still goes through ONE hardened call site, and it re-exports
 * `searchAccounts` so § 3.1 **row 1** (`features/accounts` `searchAccounts`)
 * stays literally true and `tests/unit/parity-verification.test.ts` keeps
 * attesting all 16 rows unchanged.
 *
 * ── SERVER-ONLY (the wire shapes are NOT here) ──
 * This module reaches `shared/lib/session.ts` → `next/headers`, so it must
 * never be pulled into a client bundle. The pure-zod search/list shapes live in
 * `shared/api/iam-accounts-types.ts`, which is what
 * `features/accounts/api/types.ts` (imported by client components) re-exports.
 *
 * ── BEHAVIOUR (moved verbatim from `features/accounts/api/*`) ──
 * Auth invariant (console-integration-contract § 2.1/§ 2.4.1 — the #569 trust
 * boundary): every call authenticates with the EXCHANGED operator token
 * (`getOperatorToken()`), NEVER the IAM OIDC access token. Absent ⇒
 * `401 TOKEN_INVALID`, no fetch. The active tenant is always sent as
 * `X-Tenant-Id` (`getActiveTenant()`); absent ⇒ `400 NO_ACTIVE_TENANT`.
 *
 * Mutation invariant (§ 2.4.1): every mutation (POST) carries the
 * operator-entered `X-Operator-Reason` (+ body `reason`) AND a caller-supplied
 * `Idempotency-Key` — enforced by the profile's `forceMutationHeaders` (an empty
 * reason / missing key is rejected before any fetch; the api layer NEVER
 * fabricates a reason).
 *
 * Resilience (§ 2.5): AbortController hard timeout; 401/403 → `ApiError` (forced
 * re-login — `forbiddenMode: 'auth'`); 503/timeout → {@link
 * AccountsUnavailableError} (accounts section degrades only); 400/404/409/422 →
 * `ApiError` (inline actionable). Logging: structured, server-side only; the
 * operator token and account PII (emails) are NEVER logged (redacted).
 */

export const ADMIN_PREFIX = '/api/admin';

export interface AccountsCallOptions {
  method: 'GET' | 'POST';
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (mutations only). */
  reason?: string;
  /** Stable per a single confirmed action → `Idempotency-Key`. */
  idempotencyKey?: string;
  /** JSON body (mutations). */
  body?: unknown;
}

/**
 * accounts profile for the shared {@link callAdminGateway} core: the IAM
 * accounts surface (`ACCOUNTS_TIMEOUT_MS`) that degrades via
 * {@link AccountsUnavailableError} and logs `accounts_*` events. Unlike the
 * newer operators/partnerships clients, accounts (a) handles 403 TOGETHER with
 * 401 (`forbiddenMode: 'auth'`) and (b) FORCES `X-Operator-Reason` +
 * `Idempotency-Key` on every mutation (`forceMutationHeaders: true`).
 */
export const ACCOUNTS_PROFILE: AdminGatewayProfile = {
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
 * Exported so the feature-local accounts mutations use the SAME call site.
 */
export async function callGapAdmin<T>(
  opts: AccountsCallOptions,
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
// search / list — GET /api/admin/accounts (§ 3.1 row 1)
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
