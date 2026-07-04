import { getActiveTenant } from '@/shared/lib/session';
import { ApiError } from '@/shared/api/errors';
import {
  callSubscriptions,
  SUBSCRIPTIONS_PREFIX,
} from './subscriptions-client';
import {
  SubscriptionResultSchema,
  type SubscriptionResult,
  type SubscriptionStatus,
} from './types';
import type { SubscribableDomainKey } from './domains';

/**
 * Tenant domain-subscription mutations (TASK-PC-FE-183 / ADR-MONO-023,
 * admin-api.md § Subscription Management). Server-only — called from the
 * same-origin `/api/subscriptions/**` proxy routes with the HttpOnly operator
 * token + active tenant attached in {@link callSubscriptions}.
 *
 * The tenant is ALWAYS the caller's active tenant (an owner manages only their
 * own tenant's subscriptions — the producer's `subscription.manage` is
 * tenant-scoped). The client never supplies a tenant id; it is resolved here
 * server-side from the active-tenant cookie, so a client can never target
 * another tenant.
 */

async function requireActiveTenant(): Promise<string> {
  const tenant = await getActiveTenant();
  if (!tenant) {
    throw new ApiError(400, 'NO_ACTIVE_TENANT', 'No active tenant selected');
  }
  return tenant;
}

/** POST /api/admin/subscriptions — subscribe the active tenant to a domain. */
export async function createSubscription(
  domainKey: SubscribableDomainKey,
  reason: string,
): Promise<SubscriptionResult> {
  const tenantId = await requireActiveTenant();
  return callSubscriptions(
    {
      method: 'POST',
      path: SUBSCRIPTIONS_PREFIX,
      reason,
      body: { tenantId, domainKey },
    },
    (json) => SubscriptionResultSchema.parse(json),
  );
}

/**
 * PATCH /api/admin/subscriptions/{tenantId}/{domainKey}/status — transition an
 * existing subscription (suspend / resume / cancel). The producer's
 * SubscriptionStatus state machine guards the transition.
 */
export async function changeSubscriptionStatus(
  domainKey: SubscribableDomainKey,
  status: SubscriptionStatus,
  reason: string,
): Promise<SubscriptionResult> {
  const tenantId = await requireActiveTenant();
  return callSubscriptions(
    {
      method: 'PATCH',
      path: `${SUBSCRIPTIONS_PREFIX}/${encodeURIComponent(
        tenantId,
      )}/${encodeURIComponent(domainKey)}/status`,
      reason,
      body: { status },
    },
    (json) => SubscriptionResultSchema.parse(json),
  );
}
