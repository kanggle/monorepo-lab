import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, LedgerUnavailableError } from '@/shared/api/errors';
import {
  LEDGER_DEFAULT_PAGE_SIZE,
  LEDGER_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side finance `ledger-service` operations client (TASK-PC-FE-072 —
 * § 2.4.7.1, the SECOND finance-product service bound by the console,
 * alongside the FE-009 `account-service`, exactly as the FE-057 wms
 * outbound surface is the second wms service alongside the FE-007 admin
 * surface). STRICTLY READ-ONLY.
 *
 * Server-only by construction (same posture as `finance-api.ts` /
 * `scm-api.ts` / `wms-api.ts`): imported exclusively from server components
 * and the `runtime = 'nodejs'` route handlers; `getServerEnv()` throws
 * outside the server runtime. The token + any data never reach client JS —
 * client components call the same-origin `/api/ledger/**` proxy routes,
 * which attach the HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.5 VIA the § 2.4.7 finance
 *    binding (NOT re-derived) ──
 *
 * The ledger sits behind the SAME finance gateway hostname
 * (`finance.local`) as the account-service, on a DISTINCT path prefix
 * (`/api/finance/ledger/**` vs `/api/finance/accounts/**`), and validates
 * the SAME credential: a IAM RS256 JWT (ADR-001) against IAM JWKS,
 * `tenant_id` accepted by the finance dual-accept gate
 * (`finance` / `*` / `entitled_domains ∋ finance`), `finance.read` scope,
 * responses tenant-scoped (the same finance `iam-integration.md`
 * § platform-console Operator Read Consumer basis as § 2.4.7 —
 * TASK-FIN-BE-005). finance has NO token-exchange.
 *
 * Therefore this client uses `getDomainFacingToken()` (the assumed
 * tenant-scoped token when the operator has switched, else the base IAM
 * OIDC access token — ADR-MONO-020 D4 / § 2.7) and NEVER
 * `getOperatorToken()` — exactly like `finance-api.ts` / `scm-api.ts` /
 * `wms-api.ts`, and the EXACT INVERSE of the GAP
 * `features/{accounts,audit,operators,dashboards}` clients. The #569
 * trust-boundary invariant is GAP-domain-scoped and does NOT generalise to
 * the ledger. A test pins this (the `getOperatorToken` path MUST be absent
 * for the ledger).
 *
 * Tenant invariant (§ 2.4.7.1 reuse of § 2.4.7 / § 2.4.5): the ledger
 * resolves the tenant from the JWT `tenant_id` claim (`∈ {finance,*}`) —
 * NOT an `X-Tenant-Id` header. The console therefore does NOT send
 * `X-Tenant-Id` to the ledger; the tenant rides inside the IAM OIDC token.
 * The ledger rejects cross-tenant producer-side (`403 TENANT_FORBIDDEN`).
 *
 * READ + ONE MUTATION (§ 2.4.7.1, NORMATIVE): the six reads are pure GETs.
 * As of TASK-PC-FE-073 there is EXACTLY ONE operator mutation —
 * `resolveDiscrepancy()` (`POST .../discrepancies/{id}/resolve`, the F8
 * operator review close, consuming the *existing* `reconciliation-api.md`
 * § 2 endpoint). The resolve carries a body `{ resolutionType, note }` +
 * `Content-Type: application/json`, the SAME domain-facing token, and —
 * the honest header difference — **NO `Idempotency-Key`** (the producer
 * defines none for resolve; the `409 RECONCILIATION_ALREADY_RESOLVED` state
 * guard is the double-submit defence) and **NO `X-Operator-Reason`** (the
 * reason rides in the body `note`). Every OTHER ledger mutation stays OUT —
 * NO `POST /entries` manual posting, NO `/revaluations`, NO `/settlements`,
 * NO `/reconciliation/statements` ingest. Carrying the FE-007 alert-ack OR
 * the GAP § 2.4.1 destructive mutation scaffolding here is a defect (tests
 * assert their absence). The ledger has NO list/search GET over entries —
 * this client intentionally exposes no entry list/search function
 * (entry-id-driven — the honest ledger constraint, same as the FE-009
 * account surface). Account-level drill + statement-detail reads exist in
 * the producer but are forward-declared (not fabricated here).
 *
 * Error envelope (§ 2.4.7.1 / § 2.5): the ledger uses the FLAT shape
 * `{ code, message, details?, timestamp }` — the SAME finance producer
 * family + wire shape as the account-service (DISTINCT from wms's NESTED
 * `{ error: { code … } }`), with the finance error-code vocabulary
 * (`JOURNAL_ENTRY_NOT_FOUND`, `ACCOUNTING_PERIOD_NOT_FOUND`,
 * `RECONCILIATION_DISCREPANCY_NOT_FOUND`, `TENANT_FORBIDDEN`).
 * `parseLedgerError()` reads the flat shape. A test demonstrates a
 * wms-nested body is NOT mis-parsed.
 *
 * **NO 429 handling** (§ 2.4.7.1, honest difference from scm § 2.4.6): the
 * ledger contracts (`ledger-api.md` / `reconciliation-api.md`) have NO
 * `429` entry. This client does NOT add a Retry-After / backoff branch. If
 * the ledger ever returns a `429` it falls through to the default-error
 * path (a surfaced `ApiError`). A test asserts the absence (mock-feeds a
 * 429 and verifies the client did NOT retry-storm and did NOT take a
 * Retry-After branch).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout
 * (no unbounded default); `401` → `ApiError` (forced WHOLE-SESSION IAM
 * re-login — not a per-section degrade); `403` → `ApiError` (inline "not
 * available / not scoped"); `404` (`JOURNAL_ENTRY_NOT_FOUND` /
 * `ACCOUNTING_PERIOD_NOT_FOUND` / `RECONCILIATION_DISCREPANCY_NOT_FOUND`) →
 * `ApiError` (inline actionable); `400` / `422` → `ApiError` (inline
 * actionable, no crash); `503` / timeout / network → `LedgerUnavailableError`
 * (ONLY the ledger section degrades — shell + IAM/wms/scm/finance-account/
 * erp sections intact).
 *
 * Confidential / F7 (§ 2.4.7.1): structured logs are server-side only; the
 * IAM access token and any ledger data (balances, journal lines, account
 * codes, reconciliation amounts) are NEVER logged (redacted) — the log
 * payloads below carry ONLY `requestId` + the sanitised `logPath` (no
 * `entryId` / `periodId` / `discrepancyId`, even path-encoded).
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
 * Parses the finance FLAT error envelope
 * (`{ code, message, details?, timestamp }`). Defensive: a missing /
 * nested (wms-shaped) / non-JSON body degrades to a synthetic code rather
 * than throwing (the producer is the authority for the real code; this
 * never crashes the console on a malformed error body). A wms-nested parser
 * would MISS the ledger flat `code` — this is the per-domain envelope
 * correctness pinned by tests.
 */
async function parseLedgerError(
  res: Response,
): Promise<{
  code: string;
  message: string;
  details?: unknown;
  timestamp?: string;
}> {
  let code = `HTTP_${res.status}`;
  let message = `ledger request failed (${res.status})`;
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
 * Single hardened call site. Resolves the domain-facing IAM OIDC access
 * token, applies the timeout, maps the finance FLAT error envelope to the
 * § 2.5 resilience taxonomy.
 *
 * **No 429 / Retry-After / backoff branch** (§ 2.4.7.1, honest difference
 * from scm). If the ledger ever returned a 429 it would surface as a
 * generic `ApiError(429, …)` through the default `!res.ok` path — but never
 * as a retry, never as a `Retry-After` honour. A test asserts this absence.
 */
export async function callLedger<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential (§ 2.4.7.1 reusing § 2.4.7 / § 2.4.5): the
  //    ledger requires the IAM OIDC ACCESS token directly. NEVER
  //    getOperatorToken() — that is the GAP-domain (§ 2.6 exchanged)
  //    credential; the ledger would reject it. The #569 invariant is
  //    GAP-domain-scoped.
  //    ── ADR-MONO-020 D4 / § 2.7: the DOMAIN-FACING IAM OIDC token — the
  //    ASSUMED (tenant-scoped) token when the operator has switched, else
  //    the base access token (net-zero). Still NOT the operator token.
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('ledger_no_gap_session', {
      requestId,
      path: opts.logPath,
    });
    // No IAM OIDC session ⇒ whole-session re-login (not a per-section
    // degrade — no partial authed state).
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // NOTE: deliberately NO `X-Tenant-Id` — the ledger resolves tenant
    // from the JWT `tenant_id` claim (§ 2.4.7.1 reuse of the § 2.4.7 /
    // § 2.4.5 divergence).
    // NOTE: deliberately NO `Idempotency-Key` (the resolve producer defines
    // none — `reconciliation-api.md` § 2; the
    // `409 RECONCILIATION_ALREADY_RESOLVED` state guard is the double-submit
    // defence) and NO `X-Operator-Reason` (the resolve reason rides in the
    // body `note`). The reads carry no body at all.
  };
  const method = opts.method ?? 'GET';
  if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.LEDGER_TIMEOUT_MS,
  );
  try {
    const res = await fetch(`${env.LEDGER_BASE_URL}${opts.path}`, {
      method,
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseLedgerError(res);
      logger.warn('ledger_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.logPath,
      });
      // IAM OIDC session expired → whole-session re-login (no partial
      // authed state — NOT a per-section degrade).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseLedgerError(res);
      logger.warn('ledger_forbidden', {
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
      const e = await parseLedgerError(res);
      logger.warn('ledger_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.logPath,
      });
      // ONLY the ledger section degrades — shell + IAM/wms/scm/
      // finance-account/erp sections intact.
      throw new LedgerUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ledger unavailable',
      );
    }

    if (!res.ok) {
      // 400/422 VALIDATION_ERROR, 404 JOURNAL_ENTRY_NOT_FOUND /
      // ACCOUNTING_PERIOD_NOT_FOUND / RECONCILIATION_DISCREPANCY_NOT_FOUND,
      // etc. — inline actionable (no crash). NOTE — a 429 from the ledger
      // would land HERE (no Retry-After / backoff branch; the ledger has
      // no documented 429 — the absence is an honest difference from scm
      // § 2.4.6, asserted by test).
      const e = await parseLedgerError(res);
      logger.warn('ledger_request_error', {
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
    logger.info('ledger_ok', {
      requestId,
      status: res.status,
      path: opts.logPath,
    });
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || err instanceof LedgerUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('ledger_timeout', {
        requestId,
        timeoutMs: env.LEDGER_TIMEOUT_MS,
        path: opts.logPath,
      });
      throw new LedgerUnavailableError(
        'timeout',
        'TIMEOUT',
        'ledger call timed out',
      );
    }
    logger.error('ledger_error', { requestId, path: opts.logPath });
    throw new LedgerUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ledger call failed',
    );
  } finally {
    clearTimeout(timer);
  }
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
