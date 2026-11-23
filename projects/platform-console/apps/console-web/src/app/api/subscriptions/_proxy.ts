import { z } from 'zod';
import { SubscriptionsUnavailableError } from '@/shared/api/errors';
import { newRequestId } from '@/shared/lib/logger';
import { makeIamProxyErrorMapper } from '@/shared/api/proxy-factory';

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

export const mapError = makeIamProxyErrorMapper({
  logPrefix: 'subscriptions',
  UnavailableErrorClass: SubscriptionsUnavailableError,
  unavailableMessage: 'subscriptions unavailable',
});

export { badRequest } from '@/shared/api/proxy-factory';

export { newRequestId };
