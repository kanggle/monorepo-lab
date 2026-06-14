import { NextResponse } from 'next/server';
import {
  listPromotions,
  createPromotion,
} from '@/features/ecommerce-ops/api/promotions-api';
import {
  CreatePromotionBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce promotions proxy — list + create
 * (TASK-PC-FE-086 — ADR-031 Phase 3b):
 *   GET  /api/ecommerce/promotions?status&page&size → list
 *   POST /api/ecommerce/promotions                  → create (201)
 *
 * Domain-facing IAM OIDC token attached server-side (NOT the operator token —
 * § 2.4.10); NO X-Tenant-Id; NO Idempotency-Key. Body Zod-validated before
 * reaching the upstream (defence-in-depth). mapEcommerceError imported from
 * the products _proxy (same EcommerceUnavailableError class, same mapper).
 */

export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const status = searchParams.get('status') ?? undefined;
  const page = Number(searchParams.get('page') ?? 0);
  const size = Number(searchParams.get('size') ?? 20);

  try {
    const result = await listPromotions({ status, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();

  let body;
  try {
    body = tryParse(CreatePromotionBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await createPromotion(body);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
