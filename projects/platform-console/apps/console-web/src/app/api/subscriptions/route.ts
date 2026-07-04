import { NextResponse } from 'next/server';
import { createSubscription } from '@/features/subscriptions/api/subscriptions-api';
import { SubscribeBodySchema, mapError, badRequest, newRequestId } from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin subscription SUBSCRIBE proxy (POST) for the client screen
 * (TASK-PC-FE-183). The operator token + active tenant + `X-Operator-Reason`
 * are attached server-side in the api layer; the tenant id is resolved
 * server-side (never client-supplied) so a client can only ever subscribe its
 * OWN active tenant.
 *
 * 401 → 401 (re-login); 403 → inline (lacks subscription.manage);
 * 409 SUBSCRIPTION_ALREADY_EXISTS → inline (client offers resume);
 * 503/timeout → 503 (subscription surface degrades only).
 */
export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = SubscribeBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await createSubscription(body.domainKey, body.reason);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
