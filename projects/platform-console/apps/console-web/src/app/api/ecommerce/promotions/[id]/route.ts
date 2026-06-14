import { NextResponse } from 'next/server';
import {
  getPromotion,
  updatePromotion,
  deletePromotion,
} from '@/features/ecommerce-ops/api/promotions-api';
import {
  UpdatePromotionBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce promotion [id] proxy
 * (TASK-PC-FE-086 — ADR-031 Phase 3b):
 *   GET    /api/ecommerce/promotions/{id} → detail
 *   PUT    /api/ecommerce/promotions/{id} → update (full replace) [NOT PATCH]
 *   DELETE /api/ecommerce/promotions/{id} → delete (204)
 *
 * Domain-facing IAM OIDC token server-side; Zod body validation; NO
 * Idempotency-Key; confirm-gated in the UI.
 */

export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getPromotion(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

export async function PUT(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(UpdatePromotionBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await updatePromotion(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

export async function DELETE(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    await deletePromotion(id);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
