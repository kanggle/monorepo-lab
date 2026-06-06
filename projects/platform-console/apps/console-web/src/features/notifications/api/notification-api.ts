import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, ErpUnavailableError } from '@/shared/api/errors';
import {
  NotificationListResponseSchema,
  type NotificationListResponse,
  NotificationDetailResponseSchema,
  type Notification,
  type NotificationInboxQueryParams,
  NOTIFICATION_DEFAULT_PAGE_SIZE,
  NOTIFICATION_MAX_PAGE_SIZE,
} from './notification-types';

/**
 * Server-side erp `notification-service` in-app inbox client (TASK-PC-FE-052 —
 * ADR-MONO-016 § D3 notification first increment). Consumes the UNCHANGED
 * producer `notification-api.md` (base path `/api/erp/notifications`):
 *
 *   read   listNotifications / getNotification
 *   write  markNotificationRead (idempotent, no Idempotency-Key)
 *
 * Server-only by construction (same posture as `approval-api.ts`): imported
 * exclusively from server components + the `runtime = 'nodejs'` route
 * handlers; `getServerEnv()` throws outside the server runtime. The token
 * + any data never reach client JS — client components call the same-origin
 * `/api/erp/notifications/**` proxy routes which attach the HttpOnly
 * credential here server-side.
 *
 * ── PER-DOMAIN CREDENTIAL — REUSE of § 2.4.8 ──
 * Identical credential posture to `approval-api.ts`: the DOMAIN-FACING GAP
 * OIDC token (`getDomainFacingToken()` — the assumed tenant-scoped token when
 * the operator has switched, else the base access token; net-zero), NEVER
 * `getOperatorToken()`. erp resolves the tenant from the JWT `tenant_id ∈
 * {erp,*}` claim producer-side — the console sends NO `X-Tenant-Id`.
 *
 * MUTATION discipline (naturally idempotent mark-read — no Idempotency-Key):
 *   - `POST /notifications/{id}/read` is a state-converging assignment; re-
 *     marking an already-read notification is a no-op (same `readAt`). Because
 *     no replay produces a divergent result, the transactional `Idempotency-Key`
 *     mechanism (which guards accumulating mutations such as approval transitions)
 *     does NOT apply here. No body, no Idempotency-Key, no X-Operator-Reason.
 *
 * Error envelope: the flat erp shape `{ code, message, details?, timestamp }`.
 * Notification-specific code — 404 `NOTIFICATION_NOT_FOUND` (foreign/unknown
 * id treated as non-existent to avoid existence leak). 403
 * `TENANT_FORBIDDEN`/`PERMISSION_DENIED` surfaces as `ApiError` (inline
 * actionable — non-erp operators degrade the bell gracefully). Resilience
 * (§ 2.5): 401 → ApiError(401); 403 → ApiError(403); 503 / timeout / network
 * → ErpUnavailableError (ONLY the notification bell degrades). NO 429 branch
 * (erp has no documented rate-limit — identical to the masterdata / approval
 * surfaces).
 *
 * Confidential / audit-minimal: structured logs are server-side only; the
 * IAM token, notification content, and actor ids are NEVER logged (redacted)
 * — the log payloads carry ONLY `requestId` + a sanitised route shape (a
 * literal `{id}` placeholder, never the URL or id value).
 */

interface CallOptions {
  /** Path relative to `${ERP_BASE_URL}` (includes any encoded `{id}` +
   *  the search params). */
  path: string;
  /** Sanitised path shape for logging (no record id — e.g.
   *  `/api/erp/notifications/{id}`). */
  logPath: string;
  method?: 'GET' | 'POST';
}

/** Parses the erp FLAT error envelope (`{ code, message, details?,
 *  timestamp }`). Defensive: a missing / non-JSON body degrades to a
 *  synthetic code rather than throwing. */
async function parseNotificationError(
  res: Response,
): Promise<{
  code: string;
  message: string;
  details?: unknown;
  timestamp?: string;
}> {
  let code = `HTTP_${res.status}`;
  let message = `erp notification request failed (${res.status})`;
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
 * documented rate-limit — identical to the masterdata / approval surfaces).
 */
async function callNotification<T>(
  opts: CallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Domain-facing IAM OIDC token (assumed-when-switched, else base) —
  // NEVER getOperatorToken() (the #569 invariant is GAP-domain-scoped).
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('erp_notification_no_gap_session', {
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
    // NOTE: NO `Idempotency-Key` — mark-read is naturally idempotent
    // (state-converging assignment; no accumulating side effect).
    // NOTE: NO `X-Operator-Reason` — notification-service has no reason slot.
  };
  const method = opts.method ?? 'GET';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ERP_TIMEOUT_MS);
  try {
    const res = await fetch(`${env.ERP_BASE_URL}${opts.path}`, {
      method,
      headers,
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseNotificationError(res);
      logger.warn('erp_notification_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseNotificationError(res);
      logger.warn('erp_notification_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.logPath,
      });
      // Token not erp-scoped / insufficient READ gate / TENANT_FORBIDDEN /
      // non-erp operator → inline degrade (bell stays inert, shell intact).
      throw new ApiError(403, e.code || 'TENANT_FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseNotificationError(res);
      logger.warn('erp_notification_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.logPath,
      });
      throw new ErpUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'erp notification unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR / 404 NOTIFICATION_NOT_FOUND — inline actionable
      // (no crash). A stray 429 lands here (no Retry-After / backoff — erp has
      // no documented rate-limit).
      const e = await parseNotificationError(res);
      logger.warn('erp_notification_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.logPath,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    const json = await res.json();
    logger.info('erp_notification_ok', {
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
      logger.warn('erp_notification_timeout', {
        requestId,
        timeoutMs: env.ERP_TIMEOUT_MS,
        path: opts.logPath,
      });
      throw new ErpUnavailableError(
        'timeout',
        'TIMEOUT',
        'erp notification call timed out',
      );
    }
    logger.error('erp_notification_error', { requestId, path: opts.logPath });
    throw new ErpUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'erp notification call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

// ---------------------------------------------------------------------------
// query-string helpers.
// ---------------------------------------------------------------------------

function inboxQs(params: NotificationInboxQueryParams): string {
  const qs = new URLSearchParams();
  // `unread` is ONLY set when explicitly provided — omitting is the producer
  // default (all). A boolean `false` is a meaningful filter (read-only), so
  // we check `!== undefined` not just truthiness.
  if (params.unread !== undefined) qs.set('unread', String(params.unread));
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set(
    'size',
    String(
      Math.min(
        NOTIFICATION_MAX_PAGE_SIZE,
        Math.max(1, params.size ?? NOTIFICATION_DEFAULT_PAGE_SIZE),
      ),
    ),
  );
  return qs.toString();
}

/** Parses a detail / mutation response envelope into a `Notification`. */
function parseNotification(json: unknown): Notification {
  const env = (json ?? {}) as { data?: unknown };
  return NotificationDetailResponseSchema.parse({
    data: env.data,
    meta: (json as { meta?: unknown })?.meta ?? {},
  }).data;
}

// ---------------------------------------------------------------------------
// reads.
// ---------------------------------------------------------------------------

/** `GET /api/erp/notifications` — the caller's inbox (paged; optional
 *  unread filter). */
export async function listNotifications(
  params: NotificationInboxQueryParams = {},
): Promise<NotificationListResponse> {
  return callNotification(
    {
      path: `/api/erp/notifications?${inboxQs(params)}`,
      logPath: '/api/erp/notifications',
    },
    (json) => NotificationListResponseSchema.parse(json),
  );
}

/** `GET /api/erp/notifications/{id}` — single notification (must belong to
 *  the caller; 404 `NOTIFICATION_NOT_FOUND` for foreign / unknown id). */
export async function getNotification(id: string): Promise<Notification> {
  return callNotification(
    {
      path: `/api/erp/notifications/${encodeURIComponent(id)}`,
      logPath: '/api/erp/notifications/{id}',
    },
    parseNotification,
  );
}

// ---------------------------------------------------------------------------
// write — naturally idempotent mark-read (no Idempotency-Key, no body).
// ---------------------------------------------------------------------------

/** `POST /api/erp/notifications/{id}/read` — idempotent mark-read. No body,
 *  no `Idempotency-Key` (the operation is a state-converging assignment —
 *  re-marking is a no-op preserving the original `readAt`). */
export async function markNotificationRead(id: string): Promise<Notification> {
  return callNotification(
    {
      path: `/api/erp/notifications/${encodeURIComponent(id)}/read`,
      logPath: '/api/erp/notifications/{id}/read',
      method: 'POST',
    },
    parseNotification,
  );
}
