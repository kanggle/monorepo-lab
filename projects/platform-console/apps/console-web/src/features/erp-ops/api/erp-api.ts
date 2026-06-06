import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
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
  EmployeeOrgViewListResponseSchema,
  type EmployeeOrgViewListResponse,
  EmployeeOrgViewDetailResponseSchema,
  type EmployeeOrgViewDetailResponse,
  DelegationFactListResponseSchema,
  type DelegationFactListResponse,
  DelegationFactDetailResponseSchema,
  type DelegationFactDetailResponse,
  type ErpListQueryParams,
  type ErpDetailQueryParams,
  type OrgViewListQueryParams,
  type DelegationFactListQueryParams,
  type CreateDepartmentInput,
  type UpdateDepartmentInput,
  type RetireDepartmentInput,
  type MoveDepartmentParentInput,
  type CreateEmployeeInput,
  type UpdateEmployeeInput,
  type CreateJobGradeInput,
  type UpdateJobGradeInput,
  type CreateCostCenterInput,
  type UpdateCostCenterInput,
  type CreateBusinessPartnerInput,
  type UpdateBusinessPartnerInput,
  ERP_DEFAULT_PAGE_SIZE,
  ERP_MAX_PAGE_SIZE,
} from './types';

/**
 * Server-side erp `masterdata-service` operations client (TASK-PC-FE-010 —
 * ADR-MONO-013 Phase 6, the FOURTH non-IAM federated domain and the
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
 * finance: the erp `masterdata-service` validates a IAM RS256 JWT
 * (ADR-001) against IAM JWKS, `tenant_id ∈ { erp, * }` enforced
 * producer-side from the JWT claim (erp `iam-integration.md` §
 * platform-console Operator Read Consumer — TASK-ERP-BE-002 merged
 * 2026-05-20). erp has NO token-exchange.
 *
 * Therefore this client uses `getAccessToken()` (the GAP-session
 * HttpOnly cookie) and NEVER `getOperatorToken()` — exactly like
 * `finance-api.ts` / `scm-api.ts` / `wms-api.ts`, and the EXACT
 * INVERSE of the IAM `features/{accounts,audit,operators,dashboards}`
 * clients. The #569 trust-boundary invariant is GAP-domain-scoped
 * and does NOT generalise to erp. A test pins this (the
 * `getOperatorToken` path MUST be absent for erp; the cross-domain
 * regression covers GAP=operator-token / wms=GAP-OIDC / scm=GAP-OIDC
 * / finance=GAP-OIDC / **erp=GAP-OIDC** — 5 domains).
 *
 * Tenant invariant (§ 2.4.8 / reuse of § 2.4.5): erp resolves the
 * tenant from the JWT `tenant_id` claim (`∈ {erp,*}`) — NOT an
 * `X-Tenant-Id` header. The console therefore does NOT send
 * `X-Tenant-Id` to erp; the tenant rides inside the IAM OIDC token.
 * erp rejects cross-tenant producer-side (`403 TENANT_FORBIDDEN`).
 *
 * READ + DEPARTMENT WRITE PILOT (§ 2.4.8): the 10 read functions
 * (for each of 5 masters a `list*(params)` + a `get*ById(id, params?)`)
 * are pure `GET` — NO `Idempotency-Key`, NO `X-Operator-Reason`, NO
 * body (a test pins their absence on the read path). The **department**
 * master additionally exposes a WRITE PILOT (TASK-PC-FE-046):
 * `createDepartment` / `updateDepartment` / `retireDepartment` /
 * `moveDepartmentParent` (consuming the UNCHANGED producer
 * `masterdata-api.md` § Department mutations). The OTHER FOUR masters
 * remain read-only — NO write function exists for them (a test pins
 * that absence). erp writes carry an `Idempotency-Key` + a JSON body;
 * `reason` rides in the body ONLY where the producer has a slot
 * (retire / move-parent) — the console NEVER sends `X-Operator-Reason`
 * (erp does not read it). The v2 `approval-service` /
 * `read-model-service` / future `admin-service` surfaces stay out of
 * scope. Carrying the FE-007 alert-ack OR the IAM § 2.4.1 mutation
 * scaffolding (X-Operator-Reason) here is a defect (tests assert it).
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
 * WHOLE-SESSION IAM re-login — not a per-section degrade); `403` →
 * `ApiError` (inline "not available / not scoped"); `404
 * MASTERDATA_NOT_FOUND` → `ApiError` (inline actionable "no such
 * record"); `400` / `422` → `ApiError` (inline actionable, no crash);
 * `503` / timeout / network → `ErpUnavailableError` (ONLY the erp
 * section degrades — shell + GAP/wms/scm/finance sections intact).
 *
 * Confidential / audit-heavy (§ 2.4.8): structured logs are
 * server-side only; the IAM access token, employee PII (names /
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
  /** HTTP method. Defaults to `GET` (the read surface). The
   *  department write PILOT (TASK-PC-FE-046, § 2.4.8) supplies
   *  `POST` / `PATCH`. Reads NEVER set this (the "pure GET" test
   *  pins their method = GET, no body, no mutation headers). */
  method?: 'GET' | 'POST' | 'PATCH';
  /** JSON request body for a mutation (department write PILOT only).
   *  Undefined on reads — a test asserts reads carry no body. */
  body?: unknown;
  /** `Idempotency-Key` header value — REQUIRED on every department
   *  mutation (E1 / transactional T1), generated console-side per
   *  attempt. Undefined on reads (asserted absent on the read path). */
  idempotencyKey?: string;
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
 * Single hardened call site. Resolves the IAM OIDC access token,
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
  //    the IAM OIDC ACCESS token directly. NEVER getOperatorToken() —
  //    that is the GAP-domain (§ 2.6 exchanged) credential; erp
  //    would reject it (wrong issuer/type). The #569 invariant is
  //    GAP-domain-scoped.
  //    ── ADR-MONO-020 D4 / § 2.7: the DOMAIN-FACING IAM OIDC token — the
  //    ASSUMED (tenant-scoped) token when the operator has switched, else
  //    the base access token (net-zero). Still NOT the operator token.
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('erp_no_gap_session', {
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
    // NOTE: deliberately NO `X-Tenant-Id` — erp resolves tenant
    // from the JWT `tenant_id` claim (§ 2.4.8 reuse of the § 2.4.5
    // divergence) — UNCHANGED for the department write PILOT.
    // NOTE: NEVER `X-Operator-Reason` — erp has no producer slot for
    // it (that is a GAP/admin-service concept). The department write
    // `reason` rides in the BODY where the producer has a slot
    // (retire / move-parent), § 2.4.8.
  };
  const method = opts.method ?? 'GET';
  // Department write PILOT (§ 2.4.8): mutations carry an
  // `Idempotency-Key` (E1 / transactional T1) + a JSON body. Reads
  // set NEITHER — the "every read is a pure GET" test pins their
  // absence on the read path.
  if (opts.idempotencyKey !== undefined) {
    headers['Idempotency-Key'] = opts.idempotencyKey;
  }
  if (opts.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ERP_TIMEOUT_MS);
  try {
    const res = await fetch(`${env.ERP_BASE_URL}${opts.path}`, {
      method,
      headers,
      body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
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
      // IAM OIDC session expired → whole-session re-login (no
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
// 1b. departments — WRITE PILOT (TASK-PC-FE-046 / § 2.4.8 *Department
//   write binding (PILOT)*). The FIRST erp console write. Consumes the
//   UNCHANGED producer `masterdata-api.md` § Department mutations:
//     POST  /api/erp/masterdata/departments              (create)
//     PATCH /api/erp/masterdata/departments/{id}          (update)
//     POST  /api/erp/masterdata/departments/{id}/retire   (retire)
//     POST  /api/erp/masterdata/departments/{id}/move-parent (move-parent)
//   Each carries an `Idempotency-Key` (generated console-side per
//   attempt). `reason` rides in the BODY only where the producer has a
//   slot (retire required / move-parent optional) — NEVER an
//   `X-Operator-Reason` header (erp does not read it). The other four
//   masters have NO write functions (a test pins that absence).
// ---------------------------------------------------------------------------

/** Parses a department mutation response envelope (`{ data, meta }`)
 *  into the `Department` — same tolerant shape as `getDepartmentById`. */
function parseDepartmentData(json: unknown): Department {
  const env = (json ?? {}) as { data?: unknown };
  return DepartmentDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

export async function createDepartment(
  input: CreateDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: '/api/erp/masterdata/departments',
      logPath: '/api/erp/masterdata/departments',
      method: 'POST',
      idempotencyKey,
      body: {
        code: input.code,
        name: input.name,
        ...(input.parentId !== undefined ? { parentId: input.parentId } : {}),
        ...(input.effectiveFrom ? { effectiveFrom: input.effectiveFrom } : {}),
      },
    },
    parseDepartmentData,
  );
}

export async function updateDepartment(
  id: string,
  input: UpdateDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/departments/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: {
        ...(input.name !== undefined ? { name: input.name } : {}),
        ...(input.effectiveFrom ? { effectiveFrom: input.effectiveFrom } : {}),
      },
    },
    parseDepartmentData,
  );
}

export async function retireDepartment(
  id: string,
  input: RetireDepartmentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/departments/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason: input.reason },
    },
    parseDepartmentData,
  );
}

export async function moveDepartmentParent(
  id: string,
  input: MoveDepartmentParentInput,
  idempotencyKey: string,
): Promise<Department> {
  return callErp(
    {
      path: `/api/erp/masterdata/departments/${encodeURIComponent(id)}/move-parent`,
      logPath: '/api/erp/masterdata/departments/{id}/move-parent',
      method: 'POST',
      idempotencyKey,
      body: {
        newParentId: input.newParentId,
        effectiveFrom: input.effectiveFrom,
        ...(input.reason ? { reason: input.reason } : {}),
      },
    },
    parseDepartmentData,
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

// ---------------------------------------------------------------------------
// 6. additional masters WRITE (TASK-PC-FE-048 — employees / job-grades /
//   cost-centers / business-partners create/update/retire, generalising the
//   department pilot to all 5 masters). Same hardened callErp (method/body/
//   idempotencyKey); `reason` rides in the body on retire only (NEVER an
//   X-Operator-Reason header); credential = IAM OIDC domain-facing token
//   (unchanged). Producer `masterdata-api.md` § <master> is canonical.
// ---------------------------------------------------------------------------

function parseEmployeeData(json: unknown): Employee {
  const env = (json ?? {}) as { data?: unknown };
  return EmployeeDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}
function parseJobGradeData(json: unknown): JobGrade {
  const env = (json ?? {}) as { data?: unknown };
  return JobGradeDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}
function parseCostCenterData(json: unknown): CostCenter {
  const env = (json ?? {}) as { data?: unknown };
  return CostCenterDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}
function parseBusinessPartnerData(json: unknown): BusinessPartner {
  const env = (json ?? {}) as { data?: unknown };
  return BusinessPartnerDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

/** Drops undefined keys so optional fields are omitted from the wire body
 *  (a `PATCH` with `{ name: undefined }` would otherwise serialize nothing
 *  meaningful; the producer wants only the changed fields). */
function compact<T extends Record<string, unknown>>(obj: T): Partial<T> {
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(obj)) {
    if (v !== undefined) out[k] = v;
  }
  return out as Partial<T>;
}

// employees ------------------------------------------------------------------
export async function createEmployee(
  input: CreateEmployeeInput,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: '/api/erp/masterdata/employees',
      logPath: '/api/erp/masterdata/employees',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseEmployeeData,
  );
}
export async function updateEmployee(
  id: string,
  input: UpdateEmployeeInput,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/employees/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseEmployeeData,
  );
}
export async function retireEmployee(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<Employee> {
  return callErp(
    {
      path: `/api/erp/masterdata/employees/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/employees/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseEmployeeData,
  );
}

// job-grades -----------------------------------------------------------------
export async function createJobGrade(
  input: CreateJobGradeInput,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: '/api/erp/masterdata/job-grades',
      logPath: '/api/erp/masterdata/job-grades',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseJobGradeData,
  );
}
export async function updateJobGrade(
  id: string,
  input: UpdateJobGradeInput,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/job-grades/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseJobGradeData,
  );
}
export async function retireJobGrade(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<JobGrade> {
  return callErp(
    {
      path: `/api/erp/masterdata/job-grades/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/job-grades/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseJobGradeData,
  );
}

// cost-centers ---------------------------------------------------------------
export async function createCostCenter(
  input: CreateCostCenterInput,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: '/api/erp/masterdata/cost-centers',
      logPath: '/api/erp/masterdata/cost-centers',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseCostCenterData,
  );
}
export async function updateCostCenter(
  id: string,
  input: UpdateCostCenterInput,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/cost-centers/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseCostCenterData,
  );
}
export async function retireCostCenter(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<CostCenter> {
  return callErp(
    {
      path: `/api/erp/masterdata/cost-centers/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/cost-centers/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseCostCenterData,
  );
}

// business-partners ----------------------------------------------------------
export async function createBusinessPartner(
  input: CreateBusinessPartnerInput,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: '/api/erp/masterdata/business-partners',
      logPath: '/api/erp/masterdata/business-partners',
      method: 'POST',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseBusinessPartnerData,
  );
}
export async function updateBusinessPartner(
  id: string,
  input: UpdateBusinessPartnerInput,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}`,
      logPath: '/api/erp/masterdata/business-partners/{id}',
      method: 'PATCH',
      idempotencyKey,
      body: compact({ ...input }),
    },
    parseBusinessPartnerData,
  );
}
export async function retireBusinessPartner(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<BusinessPartner> {
  return callErp(
    {
      path: `/api/erp/masterdata/business-partners/${encodeURIComponent(id)}/retire`,
      logPath: '/api/erp/masterdata/business-partners/{id}/retire',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    parseBusinessPartnerData,
  );
}

// ---------------------------------------------------------------------------
// 7. read-model — employee org-view (TASK-PC-FE-049 — ADR-MONO-016 § D3)
//   GET /api/erp/read-model/employees  (?asOf=&page=&size=&departmentId=&status=)
//   GET /api/erp/read-model/employees/{id} (?asOf=)
//
//   READ-ONLY (E5) — the read-model holds no domain logic. There is NO
//   mutation function here and no mutation call for this surface. The
//   credential is UNCHANGED (same `getDomainFacingToken()` / IAM OIDC path
//   as the masterdata reads; NEVER `getOperatorToken()`). `?asOf` (E3)
//   threads through verbatim via the same `callErp` helper.
// ---------------------------------------------------------------------------

function orgViewListQs(params: OrgViewListQueryParams): string {
  const qs = new URLSearchParams();
  if (params.asOf) qs.set('asOf', params.asOf);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(Math.min(ERP_MAX_PAGE_SIZE, Math.max(1, params.size ?? ERP_DEFAULT_PAGE_SIZE))),
  );
  if (params.departmentId) qs.set('departmentId', params.departmentId);
  if (params.status) qs.set('status', params.status);
  return qs.toString();
}

/**
 * Paginated employee org-view list from `read-model-service`.
 * Produces the eventually-consistent projection (employee +
 * resolved department hierarchy + cost center + job grade).
 * READ-ONLY — no write function exists or will exist for this
 * surface (E5 — the read-model re-emits nothing).
 */
export async function listEmployeeOrgViews(
  params: OrgViewListQueryParams = {},
): Promise<EmployeeOrgViewListResponse> {
  return callErp(
    {
      path: `/api/erp/read-model/employees?${orgViewListQs(params)}`,
      logPath: '/api/erp/read-model/employees',
    },
    (json) => EmployeeOrgViewListResponseSchema.parse(json),
  );
}

/**
 * Single employee org-view from `read-model-service` by aggregate id.
 * The `meta.unresolved` array (when present) names the references
 * that have not yet been projected — the consumer MUST surface a
 * "동기화 중" badge for those fields and MUST NOT fabricate values.
 * READ-ONLY — same credential + `callErp` path as the list.
 */
export async function getEmployeeOrgView(
  id: string,
  params: ErpDetailQueryParams = {},
): Promise<EmployeeOrgViewDetailResponse> {
  const qs = params.asOf ? `?asOf=${encodeURIComponent(params.asOf)}` : '';
  return callErp(
    {
      path: `/api/erp/read-model/employees/${encodeURIComponent(id)}${qs}`,
      logPath: '/api/erp/read-model/employees/{id}',
    },
    (json) => EmployeeOrgViewDetailResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// 8. read-model — delegation facts (TASK-PC-FE-055 — ADR-MONO-013 §D3.1)
//   GET /api/erp/read-model/delegations  (?delegatorId=&delegateId=&status=&activeAt=&page=&size=)
//   GET /api/erp/read-model/delegations/{grantId}
//
//   READ-ONLY (E5) — the read-model holds no domain logic. There is NO
//   mutation function here and no mutation call for this surface. The
//   credential is UNCHANGED (same `getDomainFacingToken()` / IAM OIDC path
//   as all other erp reads; NEVER `getOperatorToken()`). NO `X-Tenant-Id`
//   (erp resolves tenant from JWT claim). NO `X-Operator-Reason` (read-only).
// ---------------------------------------------------------------------------

function delegationListQs(params: DelegationFactListQueryParams): string {
  const qs = new URLSearchParams();
  if (params.delegatorId) qs.set('delegatorId', params.delegatorId);
  if (params.delegateId) qs.set('delegateId', params.delegateId);
  if (params.status) qs.set('status', params.status);
  if (params.activeAt) qs.set('activeAt', params.activeAt);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(Math.min(ERP_MAX_PAGE_SIZE, Math.max(1, params.size ?? ERP_DEFAULT_PAGE_SIZE))),
  );
  return qs.toString();
}

/**
 * Paginated delegation-fact list from `read-model-service`.
 * Org_scope-aware (E6): operator's org_scope constrains the delegator's
 * department subtree; `["*"]`/unset = all (net-zero).
 * READ-ONLY — no write function exists or will exist for this surface (E5).
 */
export async function listDelegationFacts(
  params: DelegationFactListQueryParams = {},
): Promise<DelegationFactListResponse> {
  return callErp(
    {
      path: `/api/erp/read-model/delegations?${delegationListQs(params)}`,
      logPath: '/api/erp/read-model/delegations',
    },
    (json) => DelegationFactListResponseSchema.parse(json),
  );
}

/**
 * Single delegation fact from `read-model-service` by grant id.
 * The latest state only — authoritative grant audit history lives on
 * `approval-service`. READ-ONLY — same credential + `callErp` path as
 * the list.
 */
export async function getDelegationFact(
  grantId: string,
): Promise<DelegationFactDetailResponse> {
  return callErp(
    {
      path: `/api/erp/read-model/delegations/${encodeURIComponent(grantId)}`,
      logPath: '/api/erp/read-model/delegations/{grantId}',
    },
    (json) => DelegationFactDetailResponseSchema.parse(json),
  );
}
