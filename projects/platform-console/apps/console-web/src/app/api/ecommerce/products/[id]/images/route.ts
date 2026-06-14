import { NextResponse } from 'next/server';
import {
  listImages,
  registerImage,
} from '@/features/ecommerce-ops/api/images-api';
import {
  RegisterImageBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce product-image **list** read proxy
 * (console-integration-contract § 2.4.10 #10): `GET /admin/products/{id}/images`.
 * The domain-facing IAM OIDC token is attached server-side. On `503`
 * STORAGE_UNAVAILABLE the section degrades (the mapper → 503).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await listImages(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

/**
 * Same-origin ecommerce **register image** mutation proxy
 * (console-integration-contract § 2.4.10 #12): `POST /admin/products/{id}/images`.
 *
 * Registers an already-uploaded `objectKey` (the browser PUT the bytes to the
 * #11 presigned URL directly — those bytes never pass through here). The
 * producer HEAD-checks the object exists (→ 404 MEDIA_NOT_FOUND) and enforces
 * the per-product image limit (→ 422 IMAGE_LIMIT_EXCEEDED). Body Zod-validated;
 * NO `Idempotency-Key`. Producer returns 201.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(RegisterImageBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await registerImage(id, body);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
