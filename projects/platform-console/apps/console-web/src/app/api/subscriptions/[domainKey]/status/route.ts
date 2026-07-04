import { NextResponse } from 'next/server';
import { changeSubscriptionStatus } from '@/features/subscriptions/api/subscriptions-api';
import {
  StatusBodySchema,
  SubscribableDomainKeySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin subscription STATUS proxy (PATCH) — suspend / resume / cancel
 * (TASK-PC-FE-183). `domainKey` comes from the path; the tenant id is resolved
 * server-side from the active-tenant cookie (never client-supplied), so a
 * client can only transition its OWN active tenant's subscription.
 *
 * 401 → 401; 403 → inline; 404 SUBSCRIPTION_NOT_FOUND → inline;
 * 409 SUBSCRIPTION_TRANSITION_INVALID → inline (state-machine guard);
 * 503/timeout → 503.
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ domainKey: string }> },
) {
  const requestId = newRequestId();
  const { domainKey } = await params;
  const key = SubscribableDomainKeySchema.safeParse(domainKey);
  if (!key.success) return badRequest();

  let body;
  try {
    body = StatusBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await changeSubscriptionStatus(
      key.data,
      body.status,
      body.reason,
    );
    return NextResponse.json(result, { status: 200 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
