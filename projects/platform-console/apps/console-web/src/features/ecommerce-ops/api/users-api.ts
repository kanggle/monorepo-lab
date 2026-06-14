import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import {
  UserListSchema,
  type UserList,
  UserDetailSchema,
  type UserDetail,
  type UserListParams,
  USER_DEFAULT_PAGE_SIZE,
  USER_MAX_PAGE_SIZE,
} from './user-types';

/**
 * Server-side ecommerce `user-service` READ-ONLY operations client
 * (TASK-PC-FE-084 — the users facet of ADR-MONO-031 Phase 2b). Drives the
 * in-console user list and detail screens.
 *
 * Server-only by construction (same posture as `orders-api.ts`): imported
 * exclusively from server components and the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token +
 * any data never reach client JS — client components call the same-origin
 * `/api/ecommerce/users/**` proxy routes, which attach the HttpOnly
 * credential here server-side.
 *
 * ── THE AUTH MODEL (same as orders-api.ts — § 2.4.10) ─────────────────────
 *
 * Per ADR-MONO-017 D2.A this surface is console-web → ecommerce gateway
 * DIRECT (no console-bff write leg). The ecommerce gateway requires
 * `account_type=OPERATOR` on the IAM OIDC token (BE-367). Therefore this
 * client uses `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC
 * token when the operator switched to a customer, else the base access token
 * — net-zero; ADR-MONO-020 D4) and NEVER `getOperatorToken()`. A test pins
 * that `getOperatorToken` is never called.
 *
 * Tenant invariant (§ 2.4.10): ecommerce resolves the tenant from the JWT
 * `tenant_id ∈ {ecommerce,*}` claim — the console does NOT send `X-Tenant-Id`.
 *
 * READ-ONLY discipline: this surface has NO mutations. There are NO POST/PATCH/
 * DELETE endpoints, NO Idempotency-Key, NO state machine transitions.
 *
 * Error envelope (§ 2.4.10 / § 2.5): ecommerce uses the FLAT shape
 * `{ code, message, timestamp }`. `parseUserError()` reads the flat shape and
 * tolerates an absent / non-JSON body without crashing.
 *
 * Resilience (§ 2.5):
 *   - `401` → `ApiError(401)` (whole-session re-login).
 *   - `403` → `ApiError(403)` (inline "not available to your role").
 *   - `404` USER_PROFILE_NOT_FOUND → `ApiError(404)` (inline not-found).
 *   - `503`/timeout/network → `EcommerceUnavailableError` (section degrades).
 */

type Method = 'GET';

interface CallOptions {
  method: Method;
  base: string;
  path: string;
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }`).
 * Defensive: a missing / non-JSON body degrades to a synthetic code rather
 * than throwing.
 */
async function parseUserError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce user request failed (${res.status})`;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as {
      code?: string;
      message?: string;
      timestamp?: string;
    };
    if (body && typeof body === 'object') {
      code = body.code ?? code;
      message = body.message ?? message;
      timestamp = body.timestamp;
    }
  } catch {
    /* keep the synthetic defaults — never throw on a bad error body */
  }
  return { code, message, timestamp };
}

/**
 * Single hardened call site for user-service. Resolves the domain-facing
 * IAM OIDC token, applies the timeout, and maps the ecommerce flat error
 * envelope to the § 2.5 resilience taxonomy.
 */
async function callUser<T>(
  opts: CallOptions,
  parse?: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Per-domain credential selection (§ 2.4.10): NEVER getOperatorToken().
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('ecommerce_user_no_gap_session', {
      requestId,
      path: opts.path,
    });
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
  };
  // NOTE: deliberately NO `X-Tenant-Id` — ecommerce resolves tenant from the
  // JWT `tenant_id` claim (gateway-injected; § 2.4.10 tenant invariant).
  // NOTE: NO `Idempotency-Key` — read-only surface; no mutations defined.

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ECOMMERCE_TIMEOUT_MS);

  try {
    const res = await fetch(`${opts.base}${opts.path}`, {
      method: opts.method,
      headers,
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseUserError(res);
      logger.warn('ecommerce_user_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseUserError(res);
      logger.warn('ecommerce_user_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseUserError(res);
      logger.warn('ecommerce_user_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // ONLY the ecommerce section degrades — shell + other sections intact.
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ecommerce user-service unavailable',
      );
    }

    if (!res.ok) {
      // 404 USER_PROFILE_NOT_FOUND — inline actionable (no crash).
      const e = await parseUserError(res);
      logger.warn('ecommerce_user_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info('ecommerce_user_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });

    if (parse === undefined) {
      return undefined as T;
    }
    const json = await res.json();
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || err instanceof EcommerceUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('ecommerce_user_timeout', {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        'ecommerce user-service call timed out',
      );
    }
    logger.error('ecommerce_user_error', { requestId, path: opts.path });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ecommerce user-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

function clampSize(size?: number): number {
  return Math.min(
    USER_MAX_PAGE_SIZE,
    Math.max(1, size ?? USER_DEFAULT_PAGE_SIZE),
  );
}

// ===========================================================================
// READS
// ===========================================================================

/** GET /admin/users?status&email&page&size (paginated user summaries). */
export function listUsers(params: UserListParams = {}): Promise<UserList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  if (params.status) qs.set('status', params.status);
  if (params.email) qs.set('email', params.email);
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callUser(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/users?${qs.toString()}`,
    },
    (j) => UserListSchema.parse(j),
  );
}

/** GET /admin/users/{userId} (user detail). */
export function getUser(userId: string): Promise<UserDetail> {
  const env = getServerEnv();
  return callUser(
    {
      method: 'GET',
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: `/users/${encodeURIComponent(userId)}`,
    },
    (j) => UserDetailSchema.parse(j),
  );
}
