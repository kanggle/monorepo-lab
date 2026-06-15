import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, LedgerUnavailableError } from '@/shared/api/errors';
import {
  TrialBalanceSchema,
  type TrialBalance,
  JournalEntrySchema,
  type JournalEntry,
  PeriodSchema,
  type Period,
  PeriodsResponseSchema,
  type PeriodsResponse,
  type PeriodsQueryParams,
  DiscrepancySchema,
  type Discrepancy,
  DiscrepanciesResponseSchema,
  type DiscrepanciesResponse,
  type DiscrepanciesQueryParams,
  type ResolveDiscrepancyBody,
  AccountBalanceSchema,
  type AccountBalance,
  AccountEntriesResponseSchema,
  type AccountEntriesResponse,
  type AccountEntriesQueryParams,
  StatementSchema,
  type Statement,
  PositionLotsResponseSchema,
  type PositionLotsResponse,
  FxRatesResponseSchema,
  type FxRatesResponse,
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
async function callLedger<T>(
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

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
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

// ---------------------------------------------------------------------------
// trial balance — GET /api/finance/ledger/trial-balance
//   ledger-api.md § 4 envelope = { data: TrialBalance, meta }. READ-ONLY.
//   Index-style browsable read (no input — tenant-scoped from the JWT).
// ---------------------------------------------------------------------------

export async function getTrialBalance(): Promise<TrialBalance> {
  return callLedger(
    {
      path: '/api/finance/ledger/trial-balance',
      logPath: '/api/finance/ledger/trial-balance',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return TrialBalanceSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// accounting periods (list) — GET /api/finance/ledger/periods?page=&size=
//   ledger-api.md § 7 envelope = { data: [ Period (no snapshot) ], meta }.
// ---------------------------------------------------------------------------

export async function listPeriods(
  params: PeriodsQueryParams = {},
): Promise<PeriodsResponse> {
  const qs = new URLSearchParams();
  pageParams(qs, params.page, params.size);
  return callLedger(
    {
      path: `/api/finance/ledger/periods?${qs.toString()}`,
      logPath: '/api/finance/ledger/periods',
    },
    (json) => PeriodsResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// accounting period (detail) — GET /api/finance/ledger/periods/{periodId}
//   ledger-api.md § 8 envelope = { data: Period (+ snapshot when CLOSED),
//   meta }. 404 ACCOUNTING_PERIOD_NOT_FOUND.
// ---------------------------------------------------------------------------

export async function getPeriod(periodId: string): Promise<Period> {
  return callLedger(
    {
      path: `/api/finance/ledger/periods/${encodeURIComponent(periodId)}`,
      // confidential / F7 — the log path carries NO periodId.
      logPath: '/api/finance/ledger/periods/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return PeriodSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// journal entry (detail) — GET /api/finance/ledger/entries/{entryId}
//   ledger-api.md § 1 envelope = { data: JournalEntry, meta }. READ-ONLY.
//   Id-driven (no list/search GET — the honest ledger constraint). 404
//   JOURNAL_ENTRY_NOT_FOUND.
// ---------------------------------------------------------------------------

export async function getJournalEntry(
  entryId: string,
): Promise<JournalEntry> {
  return callLedger(
    {
      path: `/api/finance/ledger/entries/${encodeURIComponent(entryId)}`,
      // confidential / F7 — the log path carries NO entryId.
      logPath: '/api/finance/ledger/entries/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return JournalEntrySchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// reconciliation discrepancies (queue) —
//   GET /api/finance/ledger/reconciliation/discrepancies?status=&page=&size=
//   reconciliation-api.md § 4 envelope = { data: [ Discrepancy ], meta }.
// ---------------------------------------------------------------------------

export async function listDiscrepancies(
  params: DiscrepanciesQueryParams = {},
): Promise<DiscrepanciesResponse> {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  pageParams(qs, params.page, params.size);
  return callLedger(
    {
      path: `/api/finance/ledger/reconciliation/discrepancies?${qs.toString()}`,
      logPath: '/api/finance/ledger/reconciliation/discrepancies',
    },
    (json) => DiscrepanciesResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// reconciliation discrepancy (detail) —
//   GET /api/finance/ledger/reconciliation/discrepancies/{id}
//   reconciliation-api.md § 5 envelope = { data: Discrepancy (+ resolution
//   when RESOLVED), meta }. 404 RECONCILIATION_DISCREPANCY_NOT_FOUND.
// ---------------------------------------------------------------------------

export async function getDiscrepancy(id: string): Promise<Discrepancy> {
  return callLedger(
    {
      path: `/api/finance/ledger/reconciliation/discrepancies/${encodeURIComponent(id)}`,
      // confidential / F7 — the log path carries NO discrepancyId.
      logPath: '/api/finance/ledger/reconciliation/discrepancies/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return DiscrepancySchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// reconciliation discrepancy RESOLVE (the ledger's FIRST and ONLY mutation) —
//   POST /api/finance/ledger/reconciliation/discrepancies/{id}/resolve
//   reconciliation-api.md § 2 — request `{ resolutionType, note }`, 200 →
//   the discrepancy with `status: "RESOLVED"` + a `resolution` sub-object.
//
//   Header matrix (honest, producer-faithful — § 2.4.7.1):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - body `{ resolutionType, note }` (Content-Type added by callLedger);
//     - **NO `Idempotency-Key`** (the producer defines none for resolve; the
//       `409 RECONCILIATION_ALREADY_RESOLVED` state guard is the
//       double-submit defence — NOT a fabricated header);
//     - **NO `X-Operator-Reason`** (the reason rides in the body `note`);
//     - **NO `X-Tenant-Id`** (tenant from the JWT claim).
//
//   Errors map via the SAME taxonomy as the reads: 409
//   RECONCILIATION_ALREADY_RESOLVED / 422 RECONCILIATION_PERIOD_LOCKED / 404
//   RECONCILIATION_DISCREPANCY_NOT_FOUND / 400 → ApiError (inline
//   actionable); 503 / timeout → LedgerUnavailableError (the ledger section
//   degrades, the resolve affordance re-enables on retry).
// ---------------------------------------------------------------------------

export async function resolveDiscrepancy(
  id: string,
  input: ResolveDiscrepancyBody,
): Promise<Discrepancy> {
  return callLedger(
    {
      path: `/api/finance/ledger/reconciliation/discrepancies/${encodeURIComponent(id)}/resolve`,
      // confidential / F7 — the log path carries NO discrepancyId.
      logPath: '/api/finance/ledger/reconciliation/discrepancies/{id}/resolve',
      method: 'POST',
      body: { resolutionType: input.resolutionType, note: input.note },
    },
    (json) => {
      // The producer 200 body is the success envelope `{ data, meta }` — the
      // discrepancy with `status: "RESOLVED"` + `resolution`.
      const env = (json ?? {}) as { data?: unknown };
      return DiscrepancySchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// account balance — GET /api/finance/ledger/accounts/{ledgerAccountCode}/balance
//   ledger-api.md § 3 envelope = { data: AccountBalance, meta }. READ-ONLY.
//   Id-driven (the colon-form code is URL-encoded on the path). 404
//   LEDGER_ACCOUNT_NOT_FOUND. This read adds NO mutation artifact.
//
//   F7 (§ 2.4.7.1 confidential / TASK-PC-FE-074): the account code is
//   confidential — the sanitised `logPath` carries NO account code (only
//   the `{code}` placeholder, consistent with the entryId / periodId /
//   discrepancyId / accountCode sanitisation pattern).
//
//   STRICTLY GET — NO method/body, NO Idempotency-Key, NO X-Operator-Reason,
//   NO X-Tenant-Id (all handled by `callLedger`). No 429 branch (the ledger
//   has no documented 429 — the no-429 honesty, TASK-PC-FE-072 / § 2.4.7.1).
// ---------------------------------------------------------------------------

export async function getAccountBalance(
  ledgerAccountCode: string,
): Promise<AccountBalance> {
  return callLedger(
    {
      path: `/api/finance/ledger/accounts/${encodeURIComponent(ledgerAccountCode)}/balance`,
      // confidential / F7 — the log path carries NO account code.
      logPath: '/api/finance/ledger/accounts/{code}/balance',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return AccountBalanceSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// account entries — GET /api/finance/ledger/accounts/{ledgerAccountCode}/entries
//   ledger-api.md § 2 envelope = { data: [ AccountEntryLine ], meta }.
//   Paginated, most-recent first. READ-ONLY. Id-driven (code URL-encoded).
//   404 LEDGER_ACCOUNT_NOT_FOUND. This read adds NO mutation artifact.
//
//   F7: the sanitised `logPath` carries NO account code.
//   STRICTLY GET — same honesty constraints as `getAccountBalance`.
// ---------------------------------------------------------------------------

export async function getAccountEntries(
  ledgerAccountCode: string,
  params: AccountEntriesQueryParams = {},
): Promise<AccountEntriesResponse> {
  const qs = new URLSearchParams();
  pageParams(qs, params.page, params.size);
  return callLedger(
    {
      path: `/api/finance/ledger/accounts/${encodeURIComponent(ledgerAccountCode)}/entries?${qs.toString()}`,
      // confidential / F7 — the log path carries NO account code.
      logPath: '/api/finance/ledger/accounts/{code}/entries',
    },
    (json) => AccountEntriesResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// reconciliation statement-detail — TASK-PC-FE-075
//   GET /api/finance/ledger/reconciliation/statements/{id}
//   reconciliation-api.md § 3 envelope = { data: Statement, meta }.
//   STRICTLY READ-ONLY — this read adds NO mutation artifact. The statement
//   view is id-driven (the ledger has no statement list/search GET — the
//   honest constraint, same as entries + accounts).
//
//   Header matrix (honest, producer-faithful — § 2.4.7.1):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
//       NO X-Tenant-Id (handled by callLedger).
//     - No 429 branch (the ledger has no documented 429).
//
//   Errors (SAME taxonomy as the other reads):
//     - 404 RECONCILIATION_STATEMENT_NOT_FOUND → ApiError (inline
//       actionable; the lookup form stays mounted).
//     - 503 / timeout → LedgerUnavailableError (ledger section degrades).
//
//   F7 (§ 2.4.7.1 confidential): the sanitised `logPath` carries NO
//   statementId — only the `{id}` placeholder (consistent with the
//   entryId / periodId / discrepancyId / accountCode sanitisation pattern).
// ---------------------------------------------------------------------------

/**
 * `getStatement(statementId)` — reads the reconciliation statement detail.
 * READ-ONLY. The domain-facing IAM OIDC access token is attached by
 * `callLedger`; NEVER `getOperatorToken()`. Id-driven; `statementId` is
 * `encodeURIComponent`-encoded on the path. The sanitised `logPath` carries
 * NO statementId (F7). `404 RECONCILIATION_STATEMENT_NOT_FOUND` → ApiError;
 * `503`/timeout → LedgerUnavailableError. Adds NO mutation artifact.
 */
export async function getStatement(statementId: string): Promise<Statement> {
  return callLedger(
    {
      path: `/api/finance/ledger/reconciliation/statements/${encodeURIComponent(statementId)}`,
      // confidential / F7 — the log path carries NO statementId.
      logPath: '/api/finance/ledger/reconciliation/statements/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return StatementSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// FX position open-lots drill — TASK-PC-FE-091
//   § 12 GET /api/finance/ledger/settlements/{ledgerAccountCode}/{currency}/lots
//   ledger-api.md § 12 envelope = { data: { lots[], totalRemainingForeignMinor,
//   totalCarryingBaseMinor, lotCount }, meta }. STRICTLY READ-ONLY — this read
//   adds NO mutation. id-driven by (ledgerAccountCode, currency): the
//   colon-form account code (e.g. `CUSTOMER_WALLET:acc-1`) is
//   `encodeURIComponent`-encoded on the producer path; `currency` is a
//   3-letter ISO-4217 code, also `encodeURIComponent`-encoded for safety.
//
//   Header matrix (honest, producer-faithful — § 2.4.7.1):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
//       NO X-Tenant-Id (handled by callLedger).
//     - No 429 branch (the ledger has no documented 429).
//
//   Errors (SAME taxonomy as the other reads):
//     - empty position → 200 with lots: [] / totals "0" / lotCount 0 (the
//       producer's empty-state — NOT a 404; the UI renders an empty-state
//       message, never an error).
//     - 400 VALIDATION_ERROR (unsupported currency) → ApiError (inline
//       actionable; the lookup form stays mounted).
//     - 403 TENANT_FORBIDDEN → ApiError (inline "not scoped").
//     - 503 / timeout → LedgerUnavailableError (ledger section degrades).
//
//   F7 (§ 2.4.7.1 confidential): the sanitised `logPath` carries NEITHER the
//   account code NOR the currency — only the `{code}/{currency}` placeholder
//   (consistent with the accountCode / statementId sanitisation pattern).
// ---------------------------------------------------------------------------

/**
 * `getPositionLots(ledgerAccountCode, currency)` — reads the open FX
 * acquisition lots for one `(account, currency)` position. READ-ONLY. The
 * domain-facing IAM OIDC access token is attached by `callLedger`; NEVER
 * `getOperatorToken()`. id-driven; both the account code AND the currency are
 * `encodeURIComponent`-encoded on the path. The sanitised `logPath` carries
 * NEITHER the account code NOR the currency (F7). An empty position is a
 * normal `200` (`lots: []`, totals `"0"`); `400 VALIDATION_ERROR` (bad
 * currency) → ApiError; `503`/timeout → LedgerUnavailableError. Adds NO
 * mutation artifact. All `*Minor` fields survive as F5 minor-units STRINGS.
 */
export async function getPositionLots(
  ledgerAccountCode: string,
  currency: string,
): Promise<PositionLotsResponse> {
  return callLedger(
    {
      path: `/api/finance/ledger/settlements/${encodeURIComponent(ledgerAccountCode)}/${encodeURIComponent(currency)}/lots`,
      // confidential / F7 — the log path carries NEITHER account code NOR currency.
      logPath: '/api/finance/ledger/settlements/{code}/{currency}/lots',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return PositionLotsResponseSchema.parse(env.data);
    },
  );
}

// ---------------------------------------------------------------------------
// FX 환율 피드 대시보드 — TASK-PC-FE-092
//   GET /api/finance/ledger/fx-rates (FIN-BE-033)
//   Producer envelope = { data: { feedEnabled, rates: [...] }, meta }.
//   STRICTLY READ-ONLY — global list, no path/query parameters.
//
//   Header matrix (honest, producer-faithful — § 2.4.7.1 REUSE):
//     - the SAME domain-facing IAM OIDC token (NEVER getOperatorToken());
//     - GET only — NO body, NO Idempotency-Key, NO X-Operator-Reason,
//       NO X-Tenant-Id (handled by callLedger).
//     - No 429 branch (the ledger has no documented 429).
//
//   `rate` is a decimal **string** (F5 — NEVER Number/parseFloat/parseInt).
//   An empty cache → 200 with `rates: []` (NOT a 404 — empty-state).
//   `logPath` is a fixed constant (no id/code/currency to sanitise).
// ---------------------------------------------------------------------------

/**
 * `getFxRates()` — reads the FX feed cache from the ledger service.
 * Returns `{ feedEnabled, rates }` where each rate carries a pair of
 * currency codes, the exact decimal `rate` **string** (F5 — NOT a float),
 * freshness timestamps, `ageSeconds` (duration, not money), and `stale`.
 * READ-ONLY. The domain-facing IAM OIDC access token is attached by
 * `callLedger`; NEVER `getOperatorToken()`. No path parameters — global
 * list. An empty cache is a normal `200` (`rates: []`) — NOT a 404.
 */
export async function getFxRates(): Promise<FxRatesResponse> {
  return callLedger(
    {
      path: '/api/finance/ledger/fx-rates',
      // No id / code / currency to sanitise — the path is already generic.
      logPath: '/api/finance/ledger/fx-rates',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return FxRatesResponseSchema.parse(env.data);
    },
  );
}
