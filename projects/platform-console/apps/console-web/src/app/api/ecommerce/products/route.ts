import { NextResponse } from 'next/server';
import { registerProduct } from '@/features/ecommerce-ops/api/products-api';
import {
  RegisterProductBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **register product** mutation proxy
 * (console-integration-contract § 2.4.10 #3): `POST /admin/products`.
 *
 * The domain-facing IAM OIDC token is attached server-side (NOT the operator
 * token — § 2.4.10); the body is Zod-validated against the producer
 * RegisterProductRequest shape before it reaches the upstream. NO
 * `Idempotency-Key` (producer defines none); confirm-gated in the UI.
 */
export async function POST(req: Request) {
  const requestId = newRequestId();

  let body;
  try {
    body = tryParse(RegisterProductBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await registerProduct(body);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
