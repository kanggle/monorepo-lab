import { FinanceUnavailableError } from './errors';
import {
  callFlatEnvelopeGateway,
  type FlatEnvelopeGatewayProfile,
} from './flat-envelope-gateway';
import {
  AccountSchema,
  type Account,
  BalancesResponseSchema,
  type BalancesResponse,
} from './finance-accounts-types';

/**
 * Shared server-side finance **`account-service` READ** client — account
 * by id + per-currency balances (TASK-PC-FE-009 producer binding /
 * TASK-PC-FE-259 promotion).
 *
 * ── WHY THIS LIVES IN `shared/` ──
 * `getAccount` / `getBalances` and the account+balance wire shapes are
 * consumed by **two** features: `features/finance-ops` (the `/finance/accounts`
 * screen) and `features/finance-overview` (the `/finance` landing snapshot,
 * which previously reached into `@/features/finance-ops/api/*` directly).
 * Per `architecture.md` § Forbidden Dependencies —
 *
 *   > 같은 계층 `features/A → features/B` 상호 참조 금지
 *   > (공유 가치는 `shared/` 로 승격).
 *
 * — the shared read is promoted here rather than cross-imported. Same
 * promotion pattern as `shared/api/rbac-catalog.ts` (which cites the same
 * rule). Only the **shared read** moved: `listTransactions` and the
 * transaction wire shapes stay feature-local in `features/finance-ops`
 * (single consumer), and {@link callFinance} is exported so that client
 * keeps using this exact hardened call site.
 *
 * `features/finance-ops/api/finance-api.ts` re-exports `getAccount` /
 * `getBalances` and `features/finance-ops/api/types.ts` re-exports the wire
 * shapes, so the feature's documented public surface
 * (console-integration-contract § 2.4.7) is unchanged.
 *
 * ── SERVER-ONLY (the wire shapes are NOT here) ──
 * This module reaches `next/headers` through the flat-envelope gateway, so it
 * must never be pulled into a client bundle. The pure-zod account + balance
 * shapes live in `shared/api/finance-accounts-types.ts`, which is what
 * `features/finance-ops/api/types.ts` (imported by client components)
 * re-exports.
 *
 * ── BEHAVIOUR (moved verbatim from `features/finance-ops/api/*`) ──
 * Server-only by construction. The token + any data never reach client JS —
 * client components call the same-origin `/api/finance/**` proxy routes.
 *
 * PER-DOMAIN CREDENTIAL — REUSE of § 2.4.5: the DOMAIN-FACING IAM OIDC token
 * (`getDomainFacingToken()`), NEVER `getOperatorToken()` (the #569 invariant
 * is GAP-domain-scoped). finance resolves the tenant from the JWT
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
 *
 * F5 MONEY INVARIANT: balance amounts are minor-units **strings**, rendered
 * only through `shared/lib/money.ts` `formatMoney` — never coerced to a JS
 * `Number` (a test grep-asserts this against this module too).
 *
 * TOLERANCE invariant (§ 2.4.7): every read shape is permissive — unknown /
 * future account `status` values parse to a generic string value and NEVER
 * throw. Only the fields the UI strictly needs are required; everything else
 * is passthrough.
 */

// ---------------------------------------------------------------------------
// hardened call site
// ---------------------------------------------------------------------------

export interface FinanceCallOptions {
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
 *
 * Exported so `features/finance-ops`'s feature-local `listTransactions`
 * (single consumer — deliberately NOT promoted) uses the SAME call site.
 */
export async function callFinance<T>(
  opts: FinanceCallOptions,
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
