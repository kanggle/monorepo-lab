import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import {
  DelegationListResponseSchema,
  type DelegationListResponse,
  type DelegationGrant,
  DelegationGrantSchema,
  type CreateDelegationInput,
} from './delegation-types';

/**
 * Server-side erp `approval-service` delegation grant client
 * (TASK-PC-FE-054 — PC-FE-053 follow-up; realises TASK-ERP-BE-013).
 * Consumes the §v2.1 AMENDMENT endpoints (base path `/api/erp/approval`):
 *
 *   read   listDelegations (GET /delegations ?role=DELEGATOR|DELEGATE)
 *   write  createDelegation (POST /delegations) + revokeDelegation (POST /{id}/revoke)
 *
 * Server-only by construction (same posture as `approval-api.ts`): imported
 * exclusively from server components + the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/erp/approval/delegations/**` proxy routes which attach the HttpOnly
 * credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.8 (NOT re-derived) ──
 * Identical credential posture to `approval-api.ts`: the DOMAIN-FACING GAP
 * OIDC token (`getDomainFacingToken()`), NEVER `getOperatorToken()`. erp
 * resolves the tenant from the JWT `tenant_id` claim — the console sends
 * NO `X-Tenant-Id`. The delegator identity is the JWT `sub` — NOT in the
 * request body (the producer derives it from the token).
 *
 * MUTATION discipline (producer §v2.1 delegation endpoints):
 *   - create + revoke each carry a console-generated `Idempotency-Key`.
 *   - The delegation `reason` rides in the request BODY (NOT in the
 *     `X-Operator-Reason` header — unlike the approval transitions, the
 *     delegation endpoints do not define an operator-reason audit header).
 *
 * Error envelope: the flat erp shape `{ code, message, details?,
 * timestamp }`. The delegation-specific codes — 422 `DELEGATION_INVALID`
 * (self-delegation / invalid period), 404 `DELEGATION_NOT_FOUND`,
 * 403 `PERMISSION_DENIED`/`TENANT_FORBIDDEN`, 400
 * `VALIDATION_ERROR`/`IDEMPOTENCY_KEY_REQUIRED`, 409
 * `IDEMPOTENCY_KEY_CONFLICT` — surface as `ApiError` (inline actionable,
 * no crash). Resilience (§ 2.5): 401 → re-login; 503 / timeout →
 * ErpUnavailableError (ONLY the delegation section degrades).
 */

interface CallOptions {
  /** Path relative to `${ERP_BASE_URL}`. */
  path: string;
  /** Sanitised path shape for logging (no record id). */
  logPath: string;
  method?: 'GET' | 'POST';
  body?: unknown;
  /** `Idempotency-Key` header — required on create + revoke. */
  idempotencyKey?: string;
}

/** Parses the erp FLAT error envelope. Defensive — a missing / non-JSON
 *  body degrades to a synthetic code rather than throwing. */
async function parseDelegationError(
  res: Response,
): Promise<{
  code: string;
  message: string;
  details?: unknown;
  timestamp?: string;
}> {
  let code = `HTTP_${res.status}`;
  let message = `erp delegation request failed (${res.status})`;
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
 * Single hardened call site. Resolves the domain-facing IAM OIDC token,
 * applies the timeout, maps the erp FLAT error envelope to the § 2.5
 * resilience taxonomy. No 429 / Retry-After / backoff branch (erp has no
 * documented rate-limit — identical to the approval surface).
 */
async function callDelegation<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('erp_delegation_no_gap_session', {
      requestId,
      path: opts.logPath,
    });
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
    // NOTE: deliberately NO `X-Tenant-Id` — erp resolves tenant from the
    // JWT `tenant_id` claim (§ 2.4.8 tenant-model divergence).
    // NOTE: deliberately NO `X-Operator-Reason` — the delegation reason
    // rides in the BODY only (unlike the approval transition surface).
  };
  const method = opts.method ?? 'GET';
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
      const e = await parseDelegationError(res);
      logger.warn('erp_delegation_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseDelegationError(res);
      logger.warn('erp_delegation_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseDelegationError(res);
      logger.warn('erp_delegation_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.logPath,
      });
      throw new ErpUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'erp delegation unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR / IDEMPOTENCY_KEY_REQUIRED, 404
      // DELEGATION_NOT_FOUND, 409 IDEMPOTENCY_KEY_CONFLICT, 422
      // DELEGATION_INVALID — inline actionable (no crash).
      const e = await parseDelegationError(res);
      logger.warn('erp_delegation_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info('erp_delegation_ok', {
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
      logger.warn('erp_delegation_timeout', {
        requestId,
        timeoutMs: env.ERP_TIMEOUT_MS,
        path: opts.logPath,
      });
      throw new ErpUnavailableError(
        'timeout',
        'TIMEOUT',
        'erp delegation call timed out',
      );
    }
    logger.error('erp_delegation_error', { requestId, path: opts.logPath });
    throw new ErpUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'erp delegation call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// reads.
// ---------------------------------------------------------------------------

/** `GET /api/erp/approval/delegations[?role=DELEGATOR|DELEGATE]` — list
 *  grants where the caller is the delegator (or delegate). */
export async function listDelegations(
  role?: 'DELEGATOR' | 'DELEGATE',
): Promise<DelegationListResponse> {
  const qs = role ? `?role=${encodeURIComponent(role)}` : '';
  return callDelegation(
    {
      path: `/api/erp/approval/delegations${qs}`,
      logPath: '/api/erp/approval/delegations',
    },
    (json) => DelegationListResponseSchema.parse(json),
  );
}

// ---------------------------------------------------------------------------
// writes — create + revoke (each carries an Idempotency-Key).
// ---------------------------------------------------------------------------

/** `POST /api/erp/approval/delegations` — create a delegation grant.
 *  The delegator identity is the JWT `sub`; NOT sent in the body.
 *  Reason rides in the body (NOT X-Operator-Reason). */
export async function createDelegation(
  input: CreateDelegationInput,
  idempotencyKey: string,
): Promise<DelegationGrant> {
  return callDelegation(
    {
      path: '/api/erp/approval/delegations',
      logPath: '/api/erp/approval/delegations',
      method: 'POST',
      idempotencyKey,
      body: {
        delegateId: input.delegateId,
        validFrom: input.validFrom,
        ...(input.validTo ? { validTo: input.validTo } : {}),
        ...(input.reason ? { reason: input.reason } : {}),
      },
    },
    (json) => {
      // The producer returns the grant directly (201 body = grant).
      return DelegationGrantSchema.parse(json);
    },
  );
}

/** `POST /api/erp/approval/delegations/{id}/revoke` — revoke a delegation
 *  grant. Reason is REQUIRED and rides in the BODY. */
export async function revokeDelegation(
  id: string,
  reason: string,
  idempotencyKey: string,
): Promise<DelegationGrant> {
  return callDelegation(
    {
      path: `/api/erp/approval/delegations/${encodeURIComponent(id)}/revoke`,
      logPath: '/api/erp/approval/delegations/{id}/revoke',
      method: 'POST',
      idempotencyKey,
      body: { reason },
    },
    (json) => {
      return DelegationGrantSchema.parse(json);
    },
  );
}
