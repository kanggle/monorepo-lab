import { NextResponse } from 'next/server';
import { addVariant } from '@/features/ecommerce-ops/api/products-api';
import {
  AddVariantBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **add variant** mutation proxy
 * (console-integration-contract § 2.4.10 #6):
 * `POST /admin/products/{id}/variants`.
 *
 * The domain-facing IAM OIDC token is attached server-side; the body is
 * Zod-validated against AddVariantRequest. NO `Idempotency-Key`; confirm-gated.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(AddVariantBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await addVariant(id, body);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
