import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
import {
  NotificationTemplateListSchema,
  type NotificationTemplateList,
  NotificationTemplateDetailSchema,
  type NotificationTemplateDetail,
  NotificationMutationResponseSchema,
  type NotificationMutationResponse,
  type NotificationTemplateListParams,
  type CreateTemplateBody,
  type UpdateTemplateBody,
  NOTIFICATION_DEFAULT_PAGE_SIZE,
  NOTIFICATION_MAX_PAGE_SIZE,
} from './notification-types';

/**
 * Server-side ecommerce `notification-service` operations client (TASK-PC-FE-089 —
 * ADR-MONO-031 Phase 5b). Drives the in-console notification template operator
 * surface: list / detail / create / update (no delete).
 *
 * ── BASE URL RESOLUTION (notification-service path) ─────────────────────────
 *
 * notification-service exposes endpoints at `/api/notifications/templates` —
 * the **non-admin** path (same model as promotions/shippings, NOT the
 * `/api/admin/**` subtree). Therefore this client uses `ECOMMERCE_PUBLIC_BASE_URL`
 * (defaults to `http://ecommerce.local/api`) with path `/notifications/templates`,
 * yielding: `http://ecommerce.local/api/notifications/templates`
 *
 * ── AUTH MODEL (identical to promotions-api / shippings-api — § 2.4.10) ─────
 *
 * Uses `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC token —
 * net-zero; ADR-MONO-020 D4). NEVER `getOperatorToken()` (that is the
 * IAM-domain credential — wrong issuer/type for ecommerce). Tenant rides in
 * the JWT `tenant_id` claim — the console sends NO `X-Tenant-Id`.
 * NO `Idempotency-Key` (producer defines none — § 2.4.10).
 *
 * ── ERROR ENVELOPE (flat { code, message, timestamp } — same as promotions) ──
 *
 * Producer codes: 400 VALIDATION_ERROR, 403 ACCESS_DENIED, 404 TEMPLATE_NOT_FOUND,
 * 409 TEMPLATE_ALREADY_EXISTS (duplicate type+channel within tenant).
 *
 * ── TYPE/CHANNEL IMMUTABILITY ────────────────────────────────────────────────
 * After creation, `type` and `channel` are immutable. The update body accepts
 * ONLY `{ subject, body }` — never send type/channel on update.
 */

type Method = 'GET' | 'POST' | 'PUT' | 'DELETE';

interface CallOptions {
  method: Method;
  /** Absolute base. Notifications use ECOMMERCE_PUBLIC_BASE_URL (see above). */
  base: string;
  /** Path relative to `base` (e.g. `/notifications/templates`). */
  path: string;
  /** Typed mutation body; `undefined` for reads. */
  body?: unknown;
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }`).
 * Defensive: a missing / non-JSON body degrades to a synthetic code rather
 * than throwing.
 */
async function parseNotificationError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce notification request failed (${res.status})`;
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
 * Single hardened call site. Resolves the domain-facing IAM OIDC token,
 * applies the timeout, and maps the ecommerce flat error envelope to the
 * § 2.5 resilience taxonomy.
 */
async function callNotification<T>(
  opts: CallOptions,
  parse?: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Per-domain credential selection (§ 2.4.10): use getDomainFacingToken(),
  // NEVER getOperatorToken() (the ecommerce gateway requires the IAM OIDC
  // token; the #569 invariant is GAP-domain-scoped).
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('ecommerce_notification_no_gap_session', {
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
  // NO `X-Tenant-Id` — ecommerce resolves tenant from the JWT `tenant_id`
  // claim (gateway-injected; § 2.4.10 tenant invariant).
  // NO `Idempotency-Key` — the producer defines none (§ 2.4.10).
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.ECOMMERCE_TIMEOUT_MS,
  );

  try {
    const res = await fetch(`${opts.base}${opts.path}`, {
      method: opts.method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseNotificationError(res);
      logger.warn('ecommerce_notification_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseNotificationError(res);
      logger.warn('ecommerce_notification_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseNotificationError(res);
      logger.warn('ecommerce_notification_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ecommerce notification-service unavailable',
      );
    }

    if (!res.ok) {
      // 400 VALIDATION_ERROR, 404 TEMPLATE_NOT_FOUND, 409 TEMPLATE_ALREADY_EXISTS
      // → inline actionable (no crash).
      const e = await parseNotificationError(res);
      logger.warn('ecommerce_notification_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info('ecommerce_notification_ok', {
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
    if (
      err instanceof ApiError ||
      err instanceof EcommerceUnavailableError
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('ecommerce_notification_timeout', {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        'ecommerce notification-service call timed out',
      );
    }
    logger.error('ecommerce_notification_error', {
      requestId,
      path: opts.path,
    });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ecommerce notification-service call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}

function clampSize(size?: number): number {
  return Math.min(
    NOTIFICATION_MAX_PAGE_SIZE,
    Math.max(1, size ?? NOTIFICATION_DEFAULT_PAGE_SIZE),
  );
}

// ===========================================================================
// READS
// ===========================================================================

/** GET /api/notifications/templates?page=&size= (paginated summary list). */
export function listTemplates(
  params: NotificationTemplateListParams = {},
): Promise<NotificationTemplateList> {
  const env = getServerEnv();
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return callNotification(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/notifications/templates?${qs.toString()}`,
    },
    (j) => NotificationTemplateListSchema.parse(j),
  );
}

/** GET /api/notifications/templates/{templateId} (full detail incl. body). */
export function getTemplate(
  id: string,
): Promise<NotificationTemplateDetail> {
  const env = getServerEnv();
  return callNotification(
    {
      method: 'GET',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/notifications/templates/${encodeURIComponent(id)}`,
    },
    (j) => NotificationTemplateDetailSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (confirm-gated in the UI; NO Idempotency-Key)
// ===========================================================================

/** POST /api/notifications/templates (create). Returns { templateId }. */
export function createTemplate(
  body: CreateTemplateBody,
): Promise<NotificationMutationResponse> {
  const env = getServerEnv();
  return callNotification(
    {
      method: 'POST',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: '/notifications/templates',
      body,
    },
    (j) => NotificationMutationResponseSchema.parse(j),
  );
}

/** PUT /api/notifications/templates/{templateId} (update subject+body only).
 *  NOTE: type/channel are immutable — NEVER send them in the update body. */
export function updateTemplate(
  id: string,
  body: UpdateTemplateBody,
): Promise<NotificationMutationResponse> {
  const env = getServerEnv();
  return callNotification(
    {
      method: 'PUT',
      base: env.ECOMMERCE_PUBLIC_BASE_URL,
      path: `/notifications/templates/${encodeURIComponent(id)}`,
      body,
    },
    (j) => NotificationMutationResponseSchema.parse(j),
  );
}
