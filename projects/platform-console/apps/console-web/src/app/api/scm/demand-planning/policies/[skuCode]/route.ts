import { NextResponse } from 'next/server';
import {
  getPolicy,
  putPolicy,
} from '@/features/scm-config/api/demand-planning-seed-api';
import { ReorderPolicyInputSchema } from '@/features/scm-config/api/types';
import { mapReplenishmentError, badRequest, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm demand-planning **reorder-policy** seed/config proxy
 * (console-integration-contract § 2.4.6.2). GET inspects the per-SKU policy;
 * PUT upserts it (idempotent — the body IS the FULL row).
 *
 * The domain-facing IAM OIDC token is attached server-side in the seed client
 * (NOT the operator token — § 2.4.6.2 reusing the § 2.4.6 per-domain credential
 * rule). NO `Idempotency-Key`, NO `X-Operator-Reason` (the producer defines
 * neither). A producer `404 POLICY_NOT_FOUND` is surfaced as a typed
 * `{ found: false }` 200 — "not configured yet → create", NOT a 404 error toast.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ skuCode: string }> },
) {
  const requestId = newRequestId();
  const { skuCode } = await params;
  try {
    const result = await getPolicy(skuCode);
    return NextResponse.json(result);
  } catch (err) {
    return mapReplenishmentError(err, requestId);
  }
}

export async function PUT(
  req: Request,
  { params }: { params: Promise<{ skuCode: string }> },
) {
  const requestId = newRequestId();
  const { skuCode } = await params;

  // The PUT body IS the full row — validate its shape server-side before
  // forwarding (a malformed body → 422, never a forwarded bad upsert).
  let body;
  try {
    const raw = await req.json();
    const parsed = ReorderPolicyInputSchema.safeParse(raw);
    if (!parsed.success) return badRequest();
    body = parsed.data;
  } catch {
    return badRequest();
  }

  try {
    const result = await putPolicy(skuCode, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapReplenishmentError(err, requestId);
  }
}
