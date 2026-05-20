import { getServerEnv } from '@/shared/config/env';
import { getAccessToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import {
  DepartmentListResponseSchema,
  type DepartmentListResponse,
  DepartmentDetailResponseSchema,
  type Department,
  EmployeeListResponseSchema,
  type EmployeeListResponse,
  EmployeeDetailResponseSchema,
  type Employee,
  JobGradeListResponseSchema,
  type JobGradeListResponse,
  JobGradeDetailResponseSchema,
  type JobGrade,
  CostCenterListResponseSchema,
  type CostCenterListResponse,
  CostCenterDetailResponseSchema,
  type CostCenter,
  BusinessPartnerListResponseSchema,
  type BusinessPartnerListResponse,
  BusinessPartnerDetailResponseSchema,
  type BusinessPartner,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side erp `masterdata-service` operations client (TASK-PC-FE-010 —
 * ADR-MONO-013 Phase 6, the FOURTH non-GAP federated domain and the
 * FIRST internal-system-primary confirmation: wms / scm / finance /
 * **erp**). STRICTLY READ-ONLY.
 *
 * Server-only by construction (same posture as `finance-api.ts` /
 * `scm-api.ts` / `wms-api.ts`): imported exclusively from server
 * components and the `runtime = 'nodejs'` route handlers;
 * `getServerEnv()` throws outside the server runtime. The token + any
 * data never reach client JS — client components call the same-origin
 * `/api/erp/**` proxy routes, which attach the HttpOnly credential
 * here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of the § 2.4.5 rule (NOT re-derived) ──
 *
 * The normative per-domain credential rule is DEFINED in
 * console-integration-contract § 2.4.5 (each binding declares its own
 * credential against its producer; never blanket-apply one domain's
 * auth to another). erp REUSES it with the SAME outcome as wms / scm /
 * finance: the erp `masterdata-service` validates a GAP RS256 JWT
 * (ADR-001) against GAP JWKS, `tenant_id ∈ { erp, * }` enforced
 * producer-side from the JWT claim (erp `gap-integration.md` §
 * platform-console Operator Read Consumer — TASK-ERP-BE-002 merged
 * 2026-05-20). erp has NO token-exchange.
 *
 * Therefore this client uses `getAccessToken()` (the GAP-session
 * HttpOnly cookie) and NEVER `getOperatorToken()` — exactly like
 * `finance-api.ts` / `scm-api.ts` / `wms-api.ts`, and the EXACT
 * INVERSE of the GAP `features/{accounts,audit,operators,dashboards}`
 * clients. The #569 trust-boundary invariant is GAP-domain-scoped
 * and does NOT generalise to erp. A test pins this (the
 * `getOperatorToken` path MUST be absent for erp; the cross-domain
 * regression covers GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC
 * / finance=GAP-OIDC / **erp=GAP-OIDC** — 5 domains).
 *
 * Tenant invariant (§ 2.4.8 / reuse of § 2.4.5): erp resolves the
 * tenant from the JWT `tenant_id` claim (`∈ {erp,*}`) — NOT an
 * `X-Tenant-Id` header. The console therefore does NOT send
 * `X-Tenant-Id` to erp; the tenant rides inside the GAP OIDC token.
 * erp rejects cross-tenant producer-side (`403 TENANT_FORBIDDEN`).
 *
 * READ-ONLY (§ 2.4.8, NORMATIVE): every call is a pure GET. There
 * is NO mutation anywhere — NO `Idempotency-Key`, NO `X-Operator-
 * Reason`, NO body, NO erp write call (the 16 mutation endpoints —
 * 5×`POST` create / 5×`PATCH` / 5×`POST /retire` / 1×`POST .../move-
 * parent` — are operator-domain mutation, NOT operator-parity), NO
 * v2 `approval-service` / `read-model-service` / future
 * `admin-service` surface. Carrying the FE-007 alert-ack OR the GAP
 * § 2.4.1 mutation scaffolding here is a defect (tests assert their
 * absence). This client exposes only 10 read functions: for each of
 * 5 masters, a `list*(params)` and a `get*ById(id, params?)`.
 *
 * E3 ASOF THREAD-THROUGH (§ 2.4.8, CORE INVARIANT): the `asOf`
 * query parameter MUST thread through every list / detail call to
 * the producer verbatim. The producer returns the state-at-that-
 * instant (NOT the current state). A test asserts that when the
 * caller supplies `asOf=2025-01-01`, the producer client receives
 * `asOf=2025-01-01` verbatim (the core erp UX defect to avoid is
 * silently dropping `asOf` and rendering current state instead of
 * historical state — that is an E3 violation).
 *
 * Error envelope (§ 2.4.8 / § 2.5): erp uses the FLAT shape
 * `{ code, message, details?, timestamp }` — DISTINCT from wms's
 * NESTED `{ error: { code … } }`. The wire shape is byte-identical
 * to scm's and finance's flat envelope, but erp is a DISTINCT
 * producer with its own error-code vocabulary (e.g.
 * `MASTERDATA_NOT_FOUND`, `TENANT_FORBIDDEN`, `DATA_SCOPE_FORBIDDEN`,
 * `EXTERNAL_TRAFFIC_REJECTED`). `parseErpError()` reads the flat
 * shape against the erp vocabulary. A test demonstrates that a
 * wms-nested body is NOT mis-parsed (no accidental cross-wire); each
 * domain owns its own parser even when the wire shape is identical.
 *
 * **NO 429 handling** (§ 2.4.8, identical to finance § 2.4.7 — honest
 * difference from scm § 2.4.6): `masterdata-api.md` § Error code →
 * HTTP status has NO `429` entry. This client does NOT add a
 * Retry-After / backoff branch. If erp ever returns a `429` it falls
 * through to the default-error path (a surfaced `ApiError` — the
 * UI rendering it as an unexpected error is better than a fabricated
 * backoff). A test asserts the absence (mock-feeds a 429 and
 * verifies the client did NOT retry-storm and did NOT take a
 * Retry-After branch).
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard
 * timeout (no unbounded default); `401` → `ApiError` (forced
 * WHOLE-SESSION GAP re-login — not a per-section degrade); `403` →
 * `ApiError` (inline "not available / not scoped"); `404
 * MASTERDATA_NOT_FOUND` → `ApiError` (inline actionable "no such
 * record"); `400` / `422` → `ApiError` (inline actionable, no crash);
 * `503` / timeout / network → `ErpUnavailableError` (ONLY the erp
 * section degrades — shell + GAP/wms/scm/finance sections intact).
 *
 * Confidential / audit-heavy (§ 2.4.8): structured logs are
 * server-side only; the GAP access token, employee PII (names /
 * contact), business-partner financial details (`paymentTerms`),
 * cost-center sensitive attrs, and any record ids in path-form are
 * NEVER logged (redacted) — the log payloads below carry ONLY
 * `requestId` + a sanitised route shape (no `{id}` substitution
 * leaks because the log field is the route shape with a literal
 * `{id}` placeholder, NOT the URL).
 */

interface CallOptions {
  /** Path relative to `${ERP_BASE_URL}` (e.g.
   *  `/api/erp/masterdata/departments`). The path is built by the
   *  caller including any encoded `{id}` AND the URLSearchParams
   *  for `asOf` + filters + pagination — this client never mutates
   *  it. */
  path: string;
  /** Sanitised path shape for logging (no record id / no PII —
   *  e.g. `/api/erp/masterdata/departments/{id}`). */
  logPath: string;
}

/**
 * Parses the erp FLAT error envelope
 * (`{ code, message, details?, timestamp }`). Defensive: a missing /
 * nested (wms-shaped) / non-JSON body degrades to a synthetic code
 * rather than throwing (the producer is the authority for the real
 * code; this never crashes the console on a malformed error body).
 * A wms-nested parser would MISS the erp flat `code` — this is the
 * per-domain envelope correctness pinned by tests. NOTE: erp is a
 * DISTINCT producer from scm / finance — even though the wire shape
 * is byte-identical, this parser is intentionally erp-local (its
 * `details` field is preserved for downstream rendering).
 */
async function parseErpError(
  res: Response,
): Promise<{
  code: string;
  message: string;
  details?: unknown;
  timestamp?: string;
}> {
  let code = `HTTP_${res.status}`;
  let message = `erp request failed (${res.status})`;
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
 * applies the timeout, maps the erp FLAT error envelope to the
 * § 2.5 resilience taxonomy.
 *
 * **No 429 / Retry-After / backoff branch** (§ 2.4.8, honest
 * difference from scm; identical to finance § 2.4.7). If erp ever
 * returned a 429 it would surface as a generic `ApiError(429, …)`
 * through the default `!res.ok` path — but never as a retry,
 * never as a `Retry-After` honour. A test asserts this absence.
 */
async function callErp<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // ── Per-domain credential (§ 2.4.8 reusing § 2.4.5): erp requires
  //    the GAP OIDC ACCESS token directly. NEVER getOperatorToken() —
  //    that is the GAP-domain (§ 2.6 exchanged) credential; erp
  //    would reject it (wrong issuer/type). The #569 invariant is
  //    GAP-domain-scoped.
  const token = await getAccessToken();
  if (!token) {
    logger.warn('erp_no_gap_session', {
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
    // NOTE: deliberately NO `X-Tenant-Id` — erp resolves tenant
    // from the JWT `tenant_id` claim (§ 2.4.8 reuse of the § 2.4.5
    // divergence).
    // NOTE: read-only — NO `Idempotency-Key`, NO `X-Operator-Reason`,
    // NO `Content-Type` (no body).
  };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ERP_TIMEOUT_MS);
  try {
    const res = await fetch(`${env.ERP_BASE_URL}${opts.path}`, {
      method: 'GET',
      headers,
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseErpError(res);
      logger.warn('erp_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.logPath,
      });
      // GAP OIDC session expired → whole-session re-login (no
      // partial authed state — NOT a per-section degrade).
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseErpError(res);
      logger.warn('erp_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.logPath,
      });
      // Token not erp-scoped / insufficient scope / outside org
      // subtree (E6) / external traffic at the internal-only
      // boundary (E7) → inline "not available / not scoped".
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseErpError(res);
      logger.warn('erp_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.logPath,
      });
      // ONLY the erp section degrades — shell + GAP/wms/scm/finance
      // sections intact.
      throw new ErpUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'erp unavailable',
      );
    }

    if (!res.ok) {
      // 400/422 VALIDATION_ERROR, 404 MASTERDATA_NOT_FOUND, etc. —
      // inline actionable (no crash). NOTE — a 429 from erp would
      // land HERE (no Retry-After / backoff branch; erp has no
      // documented 429 — the absence is an honest difference from
      // scm § 2.4.6, identical to finance § 2.4.7, asserted by
      // test).
      const e = await parseErpError(res);
      logger.warn('erp_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    // Log ONLY status + the sanitised path — NEVER the response
    // body (confidential + audit-heavy).
    logger.info('erp_ok', {
      requestId,
      status: res.status,
      path: opts.logPath,
    });
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || err instanceof ErpUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('erp_timeout', {
        requestId,
        timeoutMs: env.ERP_TIMEOUT_MS,
        path: opts.logPath,
      });
      throw new ErpUnavailableError(
        'timeout',
        'TIMEOUT',
        'erp call timed out',
      );
    }
    logger.error('erp_error', { requestId, path: opts.logPath });
    throw new ErpUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'erp call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// query-string helpers — `asOf` is the E3 first-class thread-through;
// `active` / `page` / `size` / per-master filters are passthroughs.
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        ERP_MAX_PAGE_SIZE,
        Math.max(1, size ?? ERP_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
}

/**
 * Builds the query string for any erp list read. CORE E3 invariant:
 * when `asOf` is supplied it threads through to the producer
 * verbatim (the producer returns the state-at-that-instant; the
 * console NEVER substitutes current state). When `asOf` is omitted
 * the producer resolves to "today" (UTC) per `masterdata-api.md`.
 */
function listQs(params: ErpListQueryParams): string {
  const qs = new URLSearchParams();
  // E3 thread-through — verbatim, no transformation. This is the
  // single point that pins the asOf-pass-through invariant for
  // every list call.
  if (params.asOf) qs.set('asOf', params.asOf);
  if (params.active !== undefined) qs.set('active', String(params.active));
  if (params.filters) {
    for (const [k, v] of Object.entries(params.filters)) {
      if (v !== undefined && v !== null && v !== '') qs.set(k, v);
    }
  }
  pageParams(qs, params.page, params.size);
  return qs.toString();
}

/**
 * Builds the query string for any erp detail read. Only `asOf` is
 * producer-defined; this is the same E3 thread-through invariant
 * as `listQs`.
 */
function detailQs(params: ErpDetailQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  const s = qs.toString();
  return s ? `?${s}` : '';
}

// ---------------------------------------------------------------------------
// 1. departments — list + detail
//   GET /api/erp/masterdata/departments
//   GET /api/erp/masterdata/departments/{id}
// ---------------------------------------------------------------------------

export async function listDepartments(
  params: ErpListQueryParams = {},
): Promise<DepartmentListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments?${listQs(params)}`,
      logPath: '/api/erp/masterdata/departments',
    },
    (json) => DepartmentListResponseSchema.parse(json),
  );
}

export async function getDepartmentById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}${detailQs(params)}`,
      // confidential — the log path carries no record id.
      logPath: '/api/erp/masterdata/departments/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return DepartmentDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 2. employees — list + detail
//   GET /api/erp/masterdata/employees
//   GET /api/erp/masterdata/employees/{id}
// ---------------------------------------------------------------------------

export async function listEmployees(
  params: ErpListQueryParams = {},
): Promise<EmployeeListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees?${listQs(params)}`,
      logPath: '/api/erp/masterdata/employees',
    },
    (json) => EmployeeListResponseSchema.parse(json),
  );
}

export async function getEmployeeById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/employees/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return EmployeeDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 3. job-grades — list (producer orders by displayOrder asc) + detail
//   GET /api/erp/masterdata/job-grades
//   GET /api/erp/masterdata/job-grades/{id}
// ---------------------------------------------------------------------------

export async function listJobGrades(
  params: ErpListQueryParams = {},
): Promise<JobGradeListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades?${listQs(params)}`,
      logPath: '/api/erp/masterdata/job-grades',
    },
    (json) => JobGradeListResponseSchema.parse(json),
  );
}

export async function getJobGradeById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/job-grades/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return JobGradeDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 4. cost-centers — list + detail
//   GET /api/erp/masterdata/cost-centers
//   GET /api/erp/masterdata/cost-centers/{id}
// ---------------------------------------------------------------------------

export async function listCostCenters(
  params: ErpListQueryParams = {},
): Promise<CostCenterListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers?${listQs(params)}`,
      logPath: '/api/erp/masterdata/cost-centers',
    },
    (json) => CostCenterListResponseSchema.parse(json),
  );
}

export async function getCostCenterById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/cost-centers/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return CostCenterDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}

// ---------------------------------------------------------------------------
// 5. business-partners — list + detail (confidential paymentTerms;
//   producer enforces F7-equivalent masking — console never logs)
//   GET /api/erp/masterdata/business-partners
//   GET /api/erp/masterdata/business-partners/{id}
// ---------------------------------------------------------------------------

export async function listBusinessPartners(
  params: ErpListQueryParams = {},
): Promise<BusinessPartnerListResponse> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners?${listQs(params)}`,
      logPath: '/api/erp/masterdata/business-partners',
    },
    (json) => BusinessPartnerListResponseSchema.parse(json),
  );
}

export async function getBusinessPartnerById(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}${detailQs(params)}`,
      logPath: '/api/erp/masterdata/business-partners/{id}',
    },
    (json) => {
      const env = (json ?? {}) as { data?: unknown };
      return BusinessPartnerDetailResponseSchema.parse({
        data: env.data,
        meta: (json as { meta?: unknown })?.meta ?? {},
      }).data;
    },
  );
}
