import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import {
  ApprovalListResponseSchema,
  type ApprovalListResponse,
  ApprovalDetailResponseSchema,
  type ApprovalRequest,
  type ApprovalListQueryParams,
  type ApprovalInboxQueryParams,
  type CreateApprovalInput,
  APPROVAL_DEFAULT_PAGE_SIZE,
  APPROVAL_MAX_PAGE_SIZE,
} from './approval-types';

/**
 * Server-side erp `approval-service` workflow client (TASK-PC-FE-051 —
 * ADR-MONO-016 § D3.1 parity slice). Consumes the UNCHANGED producer
 * `approval-api.md` (base path `/api/erp/approval`):
 *
 *   read   listApprovalRequests / getApprovalRequest / listApprovalInbox
 *   write  createApprovalRequest (DRAFT) + the 4 transitions
 *          submitApproval / approveApproval / rejectApproval /
 *          withdrawApproval
 *
 * Server-only by construction (same posture as `erp-api.ts`): imported
 * exclusively from server components + the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token
 * + any data never reach client JS — client components call the
 * same-origin `/api/erp/approval/**` proxy routes which attach the
 * HttpOnly credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.8 (NOT re-derived) ──
 * Identical credential posture to `erp-api.ts`: the DOMAIN-FACING GAP
 * OIDC token (`getDomainFacingToken()` — the assumed tenant-scoped token
 * when the operator has switched, else the base access token; net-zero),
 * NEVER `getOperatorToken()`. erp resolves the tenant from the JWT
 * `tenant_id ∈ {erp,*}` claim producer-side — the console sends NO
 * `X-Tenant-Id`. The approval-service enforces `erp.read` (reads) /
 * `erp.write` (mutations) + the E3 transition-authorization (approver /
 * submitter / no-self-approval) producer-side.
 *
 * MUTATION discipline (producer § Mutating endpoints — erp E4 / T1):
 *   - create + the 4 transitions each carry a console-generated
 *     `Idempotency-Key` (the api caller supplies it per attempt).
 *   - `reject` / `withdraw` REQUIRE a reason; `approve` optional; the
 *     reason rides in the request BODY **and** is echoed via the
 *     `X-Operator-Reason` header for the audit trail (producer § Operator
 *     reason). This is the SOLE erp surface that sends `X-Operator-Reason`
 *     (the masterdata surface deliberately never does — erp has no reason
 *     slot there).
 *
 * Error envelope: the flat erp shape `{ code, message, details?,
 * timestamp }`. The approval-specific codes — 403
 * `APPROVAL_NOT_AUTHORIZED_APPROVER`, 409
 * `APPROVAL_STATUS_TRANSITION_INVALID` / `APPROVAL_ALREADY_FINALIZED`,
 * 422 `APPROVAL_ROUTE_INVALID`, 404 `APPROVAL_REQUEST_NOT_FOUND`,
 * `IDEMPOTENCY_*` — surface as `ApiError` (inline actionable, no crash).
 * Resilience (§ 2.5): 401 → ApiError(401) whole-session re-login; 403 →
 * ApiError(403) inline; 503 / timeout / network → ErpUnavailableError
 * (ONLY the approval section degrades). NO 429 branch (erp has no
 * documented rate-limit — identical to the masterdata surface).
 *
 * Confidential / audit-heavy: structured logs are server-side only; the
 * GAP token, the request title / subject / actor ids, and any reason text
 * are NEVER logged (redacted) — the log payloads carry ONLY `requestId` +
 * a sanitised route shape (a literal `{id}` placeholder, never the URL).
 */

interface CallOptions {
  /** Path relative to `${ERP_BASE_URL}` (includes any encoded `{id}` +
   *  the search params). */
  path: string;
  /** Sanitised path shape for logging (no record id — e.g.
   *  `/api/erp/approval/requests/{id}`). */
  logPath: string;
  method?: 'GET' | 'POST';
  body?: unknown;
  /** `Idempotency-Key` header — required on create + the 4 transitions. */
  idempotencyKey?: string;
  /** `X-Operator-Reason` header — set ONLY on the transitions that record
   *  a reason (the producer echoes it for the audit trail). Reads + create
   *  never set it. */
  operatorReason?: string;
}

/** Parses the erp FLAT error envelope (`{ code, message, details?,
 *  timestamp }`). Defensive: a missing / non-JSON body degrades to a
 *  synthetic code rather than throwing. */
async function parseApprovalError(
  res: Response,
): Promise<{
  code: string;
  message: string;
  details?: unknown;
  timestamp?: string;
}> {
  let code = `HTTP_${res.status}`;
  let message = `erp approval request failed (${res.status})`;
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
 * Single hardened call site. Resolves the domain-facing GAP OIDC token,
 * applies the timeout, maps the erp FLAT error envelope to the § 2.5
 * resilience taxonomy. No 429 / Retry-After / backoff branch (erp has no
 * documented rate-limit — identical to the masterdata surface).
 */
async function callApproval<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Domain-facing GAP OIDC token (assumed-when-switched, else base) —
  // NEVER getOperatorToken() (the #569 invariant is GAP-domain-scoped).
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('erp_approval_no_gap_session', {
      requestId,
      path: opts.logPath,
    });
    throw new ApiError(401, 'UNAUTHORIZED', 'No GAP session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // NOTE: deliberately NO `X-Tenant-Id` — erp resolves tenant from the
    // JWT `tenant_id` claim (§ 2.4.8 tenant-model divergence).
  };
  const method = opts.method ?? 'GET';
  if (opts.idempotencyKey !== undefined) {
    headers['Idempotency-Key'] = opts.idempotencyKey;
  }
  if (opts.operatorReason !== undefined) {
    // Audit echo (producer § Operator reason) — the reason ALSO rides in
    // the body; this header mirrors it for the immutable audit trail.
    headers['X-Operator-Reason'] = opts.operatorReason;
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
      const e = await parseApprovalError(res);
      logger.warn('erp_approval_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseApprovalError(res);
      logger.warn('erp_approval_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.logPath,
      });
      // Token not erp-scoped / insufficient scope / data scope / NOT the
      // request's approver|submitter (E3) / external traffic → inline.
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseApprovalError(res);
      logger.warn('erp_approval_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.logPath,
      });
      throw new ErpUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'erp approval unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR / IDEMPOTENCY_KEY_REQUIRED, 404
      // APPROVAL_REQUEST_NOT_FOUND, 409 APPROVAL_STATUS_TRANSITION_INVALID
      // / APPROVAL_ALREADY_FINALIZED / IDEMPOTENCY_KEY_CONFLICT, 422
      // APPROVAL_ROUTE_INVALID — inline actionable (no crash). A stray
      // 429 lands here (no Retry-After / backoff — erp has no documented
      // rate-limit).
      const e = await parseApprovalError(res);
      logger.warn('erp_approval_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info('erp_approval_ok', {
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
      logger.warn('erp_approval_timeout', {
        requestId,
        timeoutMs: env.ERP_TIMEOUT_MS,
        path: opts.logPath,
      });
      throw new ErpUnavailableError(
        'timeout',
        'TIMEOUT',
        'erp approval call timed out',
      );
    }
    logger.error('erp_approval_error', { requestId, path: opts.logPath });
    throw new ErpUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'erp approval call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// query-string helpers.
// ---------------------------------------------------------------------------

function pageParams(qs: URLSearchParams, page?: number, size?: number): void {
  qs.set('page', String(Math.max(0, page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        APPROVAL_MAX_PAGE_SIZE,
        Math.max(1, size ?? APPROVAL_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
}

function listQs(params: ApprovalListQueryParams): string {
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.role) qs.set('role', params.role);
  pageParams(qs, params.page, params.size);
  return qs.toString();
}

function inboxQs(params: ApprovalInboxQueryParams): string {
  const qs = new URLSearchParams();
  pageParams(qs, params.page, params.size);
  return qs.toString();
}

/** Parses a detail / mutation response envelope into the
 *  `ApprovalRequest` — tolerant `{ data, meta }` extraction. */
function parseApprovalRequest(json: unknown): ApprovalRequest {
  const env = (json ?? {}) as { data?: unknown };
  return ApprovalDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

// ---------------------------------------------------------------------------
// reads.
// ---------------------------------------------------------------------------

/** `GET /api/erp/approval/requests` — scope-aware list. */
export async function listApprovalRequests(
  params: ApprovalListQueryParams = {},
): Promise<ApprovalListResponse> {
  return callApproval(
    {
      path: `/api/erp/approval/requests?${listQs(params)}`,
      logPath: '/api/erp/approval/requests',
    },
    (json) => ApprovalListResponseSchema.parse(json),
  );
}

/** `GET /api/erp/approval/requests/{id}` — detail incl. history. */
export async function getApprovalRequest(
  id: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}`,
      logPath: '/api/erp/approval/requests/{id}',
    },
    parseApprovalRequest,
  );
}

/** `GET /api/erp/approval/inbox` — the caller's pending SUBMITTED queue. */
export async function listApprovalInbox(
  params: ApprovalInboxQueryParams = {},
): Promise<ApprovalListResponse> {
  return callApproval(
    {
      path: `/api/erp/approval/inbox?${inboxQs(params)}`,
      logPath: '/api/erp/approval/inbox',
    },
    (json) => ApprovalListResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// writes — create + the 4 transitions (each carries an Idempotency-Key).
// ---------------------------------------------------------------------------

/** `POST /api/erp/approval/requests` — create a DRAFT request.
 * v2.0: when `input.approverIds` is a non-empty array, sends the
 * multi-stage body (`approverIds`); otherwise sends the legacy
 * single-approver body (`approverId`). Exactly one is forwarded. */
export async function createApprovalRequest(
  input: CreateApprovalInput,
  idempotencyKey: string,
): Promise<ApprovalRequest> {
  // Build the approver payload — multi-stage (v2.0) vs legacy (v1).
  const approverPayload =
    input.approverIds && input.approverIds.length > 0
      ? { approverIds: input.approverIds }
      : { approverId: input.approverId };

  return callApproval(
    {
      path: '/api/erp/approval/requests',
      logPath: '/api/erp/approval/requests',
      method: 'POST',
      idempotencyKey,
      body: {
        subjectType: input.subjectType,
        subjectId: input.subjectId,
        title: input.title,
        ...approverPayload,
        ...(input.reason ? { reason: input.reason } : {}),
      },
    },
    parseApprovalRequest,
  );
}

/** `POST /api/erp/approval/requests/{id}/submit` — DRAFT → SUBMITTED.
 *  No body fields / no reason (the route was fixed at create time). */
export async function submitApproval(
  id: string,
  idempotencyKey: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}/submit`,
      logPath: '/api/erp/approval/requests/{id}/submit',
      method: 'POST',
      idempotencyKey,
      body: {},
    },
    parseApprovalRequest,
  );
}

/** `POST /api/erp/approval/requests/{id}/approve` — SUBMITTED → APPROVED.
 *  Reason OPTIONAL (echoed via `X-Operator-Reason` when present). */
export async function approveApproval(
  id: string,
  idempotencyKey: string,
  reason?: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}/approve`,
      logPath: '/api/erp/approval/requests/{id}/approve',
      method: 'POST',
      idempotencyKey,
      ...(reason ? { operatorReason: reason } : {}),
      body: reason ? { reason } : {},
    },
    parseApprovalRequest,
  );
}

/** `POST /api/erp/approval/requests/{id}/reject` — SUBMITTED → REJECTED.
 *  Reason REQUIRED (echoed via `X-Operator-Reason`). */
export async function rejectApproval(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}/reject`,
      logPath: '/api/erp/approval/requests/{id}/reject',
      method: 'POST',
      idempotencyKey,
      operatorReason: reason,
      body: { reason },
    },
    parseApprovalRequest,
  );
}

/** `POST /api/erp/approval/requests/{id}/withdraw` — SUBMITTED → WITHDRAWN.
 *  Reason REQUIRED (echoed via `X-Operator-Reason`). Submitter-only (E3). */
export async function withdrawApproval(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<ApprovalRequest> {
  return callApproval(
    {
      path: `/api/erp/approval/requests/${encodeURIComponent(id)}/withdraw`,
      logPath: '/api/erp/approval/requests/{id}/withdraw',
      method: 'POST',
      idempotencyKey,
      operatorReason: reason,
      body: { reason },
    },
    parseApprovalRequest,
  );
}
