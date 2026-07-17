import { LedgerUnavailableError } from '@/shared/api/errors';
import {
  callFlatEnvelopeGateway,
  type FlatEnvelopeGatewayProfile,
} from '@/shared/api/flat-envelope-gateway';
import {
  LEDGER_DEFAULT_PAGE_SIZE,
  LEDGER_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side finance `ledger-service` operations client (TASK-PC-FE-072 —
 * § 2.4.7.1, the SECOND finance-product service bound by the console alongside
 * the FE-009 `account-service`). As of TASK-PC-FE-243 the hardened call site is
 * the shared {@link callFlatEnvelopeGateway} FLAT-envelope core; this file
 * supplies the {@link LEDGER_PROFILE}. Behaviour is IDENTICAL to the
 * pre-consolidation per-client copy.
 *
 * Server-only by construction (same posture as `finance-api.ts` / `scm-api.ts`
 * / `wms-api.ts`). The token + any data never reach client JS — client
 * components call the same-origin `/api/ledger/**` proxy routes.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.5 VIA the § 2.4.7 finance binding ──
 * The ledger sits behind the SAME finance gateway hostname as the
 * account-service, on a DISTINCT path prefix (`/api/finance/ledger/**`), and
 * validates the SAME credential: the DOMAIN-FACING IAM OIDC token
 * (`getDomainFacingToken()`), NEVER `getOperatorToken()` (the #569 invariant is
 * GAP-domain-scoped). The ledger resolves the tenant from the JWT
 * `tenant_id ∈ {finance,*}` claim — the console sends NO `X-Tenant-Id`.
 *
 * READ + ONE MUTATION (§ 2.4.7.1, NORMATIVE): the six reads are pure GETs. The
 * ONLY operator mutation is `resolveDiscrepancy()`
 * (`POST .../discrepancies/{id}/resolve`) — it carries a body
 * `{ resolutionType, note }` + `Content-Type`, the SAME domain-facing token,
 * and — the honest header difference — **NO `Idempotency-Key`** (the producer
 * defines none; the `409 RECONCILIATION_ALREADY_RESOLVED` state guard is the
 * double-submit defence) and **NO `X-Operator-Reason`** (the reason rides in the
 * body `note`). The `CallOptions` intentionally exposes NO `idempotencyKey`
 * accessor, so the core never attaches that header for the ledger.
 *
 * Error envelope (§ 2.4.7.1 / § 2.5): the ledger uses the FLAT shape
 * `{ code, message, details?, timestamp }` (the SAME finance producer family +
 * wire shape as the account-service; DISTINCT from wms's NESTED
 * `{ error: { code } }`), with the finance vocabulary (`JOURNAL_ENTRY_NOT_FOUND`,
 * `ACCOUNTING_PERIOD_NOT_FOUND`, `RECONCILIATION_DISCREPANCY_NOT_FOUND`,
 * `TENANT_FORBIDDEN`). The shared parser reads the flat shape; a wms-nested body
 * is NOT mis-parsed.
 *
 * NO rate-limit handling (§ 2.4.7.1, honest difference from scm § 2.4.6): the
 * ledger contracts document no `429`; the profile supplies no rate-limit policy,
 * so a stray 429 surfaces through the default-error path as a plain `ApiError`
 * (no backoff, no Retry-After honour).
 *
 * Resilience (§ 2.5): AbortController hard timeout; `401` → whole-session
 * re-login `ApiError`; `403` → inline; `404` (entry/period/recon) / `400` /
 * `422` → inline `ApiError`; `503` / timeout / network →
 * `LedgerUnavailableError` (ONLY the ledger section degrades).
 *
 * Confidential / F7 (§ 2.4.7.1): structured logs are server-side only; the
 * token and any ledger data are NEVER logged — the log `path` carries the
 * sanitised `logPath` route shape (no `entryId` / `periodId` / `discrepancyId`,
 * even path-encoded).
 */

interface CallOptions {
  /** Path relative to `${LEDGER_BASE_URL}` (e.g.
   *  `/api/finance/ledger/entries/{id}`). */
  path: string;
  /** Sanitised path shape for logging (no entryId / periodId /
   *  discrepancyId — e.g. `/api/finance/ledger/entries/{id}`). */
  logPath: string;
  /** HTTP method — defaults to `GET` (the six reads). The resolve mutation
   *  is the only `POST`. NO PUT / PATCH / DELETE is ever issued. */
  method?: 'GET' | 'POST';
  /** Request body — present ONLY on the resolve mutation
   *  (`{ resolutionType, note }`). When present, a `Content-Type:
   *  application/json` header is added; the reads carry NO body.
   *  Deliberately NO `idempotencyKey` accessor — the resolve producer
   *  defines no `Idempotency-Key` (the `409 RECONCILIATION_ALREADY_RESOLVED`
   *  state guard is the double-submit defence). */
  body?: unknown;
}

/**
 * ledger profile for the shared {@link callFlatEnvelopeGateway} core: degrades
 * via {@link LedgerUnavailableError} and logs `ledger_*` events against the
 * finance `ledger-service` at `${LEDGER_BASE_URL}` (timeout `LEDGER_TIMEOUT_MS`).
 * No rate-limit policy (the ledger documents no 429); no idempotency fail-fast
 * guard (the resolve mutation defines no `Idempotency-Key`).
 */
const LEDGER_PROFILE: FlatEnvelopeGatewayProfile = {
  logPrefix: 'ledger',
  requestFailedLabel: 'ledger request failed',
  resolveDefaults: (env) => ({
    baseUrl: env.LEDGER_BASE_URL,
    timeoutMs: env.LEDGER_TIMEOUT_MS,
  }),
  makeUnavailable: (reason, code, message) =>
    new LedgerUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof LedgerUnavailableError,
  messages: {
    degraded: 'ledger unavailable',
    timeout: 'ledger call timed out',
    network: 'ledger call failed',
  },
};

/**
 * Single hardened call site — a thin wrapper over the shared
 * {@link callFlatEnvelopeGateway} core with the {@link LEDGER_PROFILE}. The
 * reads pass no method/body (GET, no mutation headers); the resolve mutation
 * passes `method: 'POST'` + a body (no `Idempotency-Key` accessor exists).
 */
export async function callLedger<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const { raw } = await callFlatEnvelopeGateway(
    {
      path: opts.path,
      logPath: opts.logPath,
      method: opts.method,
      body: opts.body,
    },
    parse,
    LEDGER_PROFILE,
  );
  return raw;
}

// ---------------------------------------------------------------------------
// pagination helper
// ---------------------------------------------------------------------------

export function pageParams(
  qs: URLSearchParams,
  page?: number,
  size?: number,
): void {
  // Number arithmetic on the **page/size index numbers** is fine — these
  // are NOT money amounts (F5 invariant is amount-only).
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        LEDGER_MAX_PAGE_SIZE,
        Math.max(1, size ?? LEDGER_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
}
