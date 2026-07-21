import { NextResponse } from 'next/server';
import { issueCoupons } from '@/features/ecommerce-ops/api/promotions-api';
import {
  IssueCouponRequestSchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce promotion coupon-issue proxy
 * (TASK-PC-FE-086 — ADR-031 Phase 3b):
 *   POST /api/ecommerce/promotions/{id}/coupons/issue → issue coupons (201)
 *
 * Body: { userIds: string[] } (≥1 non-empty id required — Zod-validated).
 * Domain-facing IAM OIDC token server-side; NO X-Tenant-Id. The producer now
 * REQUIRES `Idempotency-Key` (TASK-BE-536); the console mints it per confirmed
 * issue and sends it in the body (TASK-PC-FE-252) — stripped out here and passed
 * as a separate arg. 422 PROMOTION_NOT_ACTIVE / COUPON_LIMIT_EXCEEDED →
 * passthrough (inline actionable). Mirrors products [id]/stock route shape.
 */

export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let parsed;
  try {
    parsed = tryParse(IssueCouponRequestSchema, await req.json());
  } catch {
    return badRequest();
  }
  if (parsed === null) return badRequest();
  const { idempotencyKey, ...body } = parsed;

  try {
    const result = await issueCoupons(id, body, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
