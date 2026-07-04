import { NextResponse } from 'next/server';
import { z } from 'zod';
import { ApiError, SubscriptionsUnavailableError } from '@/shared/api/errors';
import { logger, newRequestId } from '@/shared/lib/logger';

/**
 * Shared error → HTTP mapping + body schemas for the subscription proxy routes
 * (TASK-PC-FE-183 / admin-api.md § Subscription Management / § 2.5). Mirrors
 * the operators `_proxy` mapping:
 *   - 401 → forced re-login;
 *   - 400 NO_ACTIVE_TENANT → tenant gate;
 *   - 403/404/409/400 producer errors → inline actionable (passthrough — the
 *     409 SUBSCRIPTION_ALREADY_EXISTS drives the client resume affordance);
 *   - 503/timeout → 503 degrade (subscription surface only).
 *
 * The `domainKey` is validated against the 5 subscribable domains (iam is the
 * identity plane, never subscribable). The `reason` travels in the same-origin
 * JSON body and becomes the `X-Operator-Reason` header only inside
 * `callSubscriptions` (the proxy never re-derives the header set).
 */

export const SubscribableDomainKeySchema = z.enum([
  'wms',
  'scm',
  'finance',
  'erp',
  'ecommerce',
]);

/** subscribe: domainKey + reason. */
export const SubscribeBodySchema = z
  .object({
    domainKey: SubscribableDomainKeySchema,
    reason: z.string(),
  })
  .strict();
export type SubscribeBody = z.infer<typeof SubscribeBodySchema>;

/** status transition: status + reason (domainKey from the path). */
export const StatusBodySchema = z
  .object({
    status: z.enum(['ACTIVE', 'SUSPENDED', 'CANCELLED']),
    reason: z.string(),
  })
  .strict();
export type StatusBody = z.infer<typeof StatusBodySchema>;

export function mapError(err: unknown, requestId: string): NextResponse {
  if (err instanceof ApiError && err.status === 401) {
    return NextResponse.json(
      { code: err.code, message: 'session expired' },
      { status: 401 },
    );
  }
  if (err instanceof ApiError && err.code === 'NO_ACTIVE_TENANT') {
    return NextResponse.json(
      { code: 'NO_ACTIVE_TENANT', message: 'no active tenant selected' },
      { status: 400 },
    );
  }
  if (err instanceof ApiError) {
    // 403 PERMISSION_DENIED / 400 REASON_REQUIRED|VALIDATION_ERROR /
    // 404 TENANT_NOT_FOUND|SUBSCRIPTION_NOT_FOUND /
    // 409 SUBSCRIPTION_ALREADY_EXISTS|SUBSCRIPTION_TRANSITION_INVALID
    // → inline actionable passthrough.
    return NextResponse.json(
      { code: err.code, message: err.message },
      { status: err.status },
    );
  }
  if (err instanceof SubscriptionsUnavailableError) {
    logger.warn('subscriptions_proxy_degraded', {
      requestId,
      reason: err.reason,
    });
    return NextResponse.json(
      { code: err.code, message: 'subscriptions unavailable' },
      { status: 503 },
    );
  }
  logger.error('subscriptions_proxy_error', { requestId });
  return NextResponse.json(
    { code: 'DOWNSTREAM_ERROR', message: 'subscriptions unavailable' },
    { status: 503 },
  );
}

export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

export { newRequestId };
