import { getServerEnv } from '@/shared/config/env';
import { getOperatorToken, getActiveTenant } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, SubscriptionsUnavailableError } from '@/shared/api/errors';

/**
 * Server-side IAM admin-service tenant domain-subscription client — hardened
 * HTTP core (TASK-PC-FE-183 / ADR-MONO-023, admin-api.md § Subscription
 * Management, BE-343). Mirrors the operators-client `callGapOperators` core
 * (feature-isolated own copy — architecture forbids cross-feature import of the
 * operators core).
 *
 * Auth invariant (§ 2.1 trust boundary): the `/api/admin/**` credential is the
 * EXCHANGED operator token (`getOperatorToken()`), NEVER the IAM OIDC access
 * token. Absent ⇒ `401 TOKEN_INVALID`, no fetch. A self-onboarded tenant owner
 * holds this operator token (from the onboarding re-exchange, PC-FE-182) with
 * `subscription.manage` via their `TENANT_BILLING_ADMIN` grant.
 *
 * Tenant invariant: the owner's active tenant is always sent as `X-Tenant-Id`
 * (`getActiveTenant()`); absent ⇒ `400 NO_ACTIVE_TENANT` (never an empty
 * header — no cross-tenant leak). The producer enforces `subscription.manage`
 * is tenant-scoped, so the owner can only ever manage their own tenant's
 * subscriptions.
 *
 * Header matrix (admin-api.md § Subscription): BOTH the subscribe POST and the
 * status PATCH require `X-Operator-Reason` (percent-encoded — HTTP header
 * values are ISO-8859-1, so a Korean reason must be encoded; the producer
 * decodes). NO `Idempotency-Key` (the producer documents none).
 *
 * Resilience (§ 2.5): AbortController hard timeout; 401 → `ApiError`
 * (re-login); 403/404/409/400 → `ApiError` (inline actionable — the 409
 * ALREADY_EXISTS drives the resume affordance); 503/timeout →
 * `SubscriptionsUnavailableError` (subscription surface degrades only). The
 * operator token is never logged.
 */

export const SUBSCRIPTIONS_PREFIX = '/api/admin/subscriptions';

type HttpMethod = 'POST' | 'PATCH';

export interface SubscriptionCallOptions {
  method: HttpMethod;
  path: string;
  /** Operator-entered audit reason → `X-Operator-Reason` (required by both). */
  reason: string;
  body: unknown;
}

export async function callSubscriptions<T>(
  opts: SubscriptionCallOptions,
  parse: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  const token = await getOperatorToken();
  if (!token) {
    logger.warn('subscriptions_no_operator_session', {
      requestId,
      path: opts.path,
    });
    throw new ApiError(401, 'TOKEN_INVALID', 'No operator session');
  }

  const tenant = await getActiveTenant();
  if (!tenant) {
    logger.warn('subscriptions_no_active_tenant', {
      requestId,
      path: opts.path,
    });
    throw new ApiError(400, 'NO_ACTIVE_TENANT', 'No active tenant selected');
  }

  const reason = opts.reason.trim();
  if (reason === '') {
    logger.warn('subscriptions_no_reason', { requestId, path: opts.path });
    throw new ApiError(
      400,
      'REASON_REQUIRED',
      'An operator reason is required for this action',
    );
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Tenant-Id': tenant,
    // Percent-encode (TASK-MONO-176): Korean reasons are non-Latin-1; the
    // producer's OperatorReasonDecodingFilter decodes back to UTF-8.
    'X-Operator-Reason': encodeURIComponent(reason),
    'X-Request-Id': requestId,
  };

  const controller = new AbortController();
  const timer = setTimeout(
    () => controller.abort(),
    env.SUBSCRIPTIONS_TIMEOUT_MS,
  );

  try {
    const res = await fetch(`${env.IAM_ADMIN_API_BASE}${opts.path}`, {
      method: opts.method,
      headers,
      body: JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const errBody = (await res.json().catch(() => ({}))) as { code?: string };
      logger.warn('subscriptions_unauthorized', { requestId, status: 401 });
      throw new ApiError(401, errBody.code ?? 'TOKEN_INVALID', 'session expired');
    }

    if (res.status === 503) {
      const errBody = (await res.json().catch(() => ({}))) as { code?: string };
      const code = errBody.code ?? 'DOWNSTREAM_ERROR';
      logger.warn('subscriptions_degraded', { requestId, status: 503, code });
      throw new SubscriptionsUnavailableError(
        code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        code,
        'IAM subscription service unavailable',
      );
    }

    if (!res.ok) {
      // 403 PERMISSION_DENIED, 400 REASON_REQUIRED/VALIDATION_ERROR,
      // 404 TENANT_NOT_FOUND/SUBSCRIPTION_NOT_FOUND,
      // 409 SUBSCRIPTION_ALREADY_EXISTS/SUBSCRIPTION_TRANSITION_INVALID
      // → inline actionable (no crash, no re-login loop).
      const errBody = (await res.json().catch(() => ({}))) as {
        code?: string;
        message?: string;
        timestamp?: string;
      };
      logger.warn('subscriptions_request_error', {
        requestId,
        status: res.status,
        code: errBody.code,
      });
      throw new ApiError(
        res.status,
        errBody.code ?? `HTTP_${res.status}`,
        errBody.message ?? `subscription request failed (${res.status})`,
        errBody.timestamp,
      );
    }

    logger.info('subscriptions_ok', { requestId, status: res.status });
    return parse(await res.json());
  } catch (err) {
    if (
      err instanceof ApiError ||
      err instanceof SubscriptionsUnavailableError
    ) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('subscriptions_timeout', {
        requestId,
        timeoutMs: env.SUBSCRIPTIONS_TIMEOUT_MS,
      });
      throw new SubscriptionsUnavailableError(
        'timeout',
        'TIMEOUT',
        'IAM subscription call timed out',
      );
    }
    logger.error('subscriptions_error', { requestId });
    throw new SubscriptionsUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'IAM subscription call failed',
    );
  } finally {
    clearTimeout(timer);
  }
}
