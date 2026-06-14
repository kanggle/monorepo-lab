import { NextResponse } from 'next/server';
import {
  updateImage,
  deleteImage,
} from '@/features/ecommerce-ops/api/images-api';
import {
  UpdateImageBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **update image** mutation proxy
 * (console-integration-contract § 2.4.10 #13):
 * `PATCH /admin/products/{id}/images/{imageId}` — sortOrder / isPrimary. The
 * console requires at least one field (the Zod schema refines an empty PATCH
 * away → 422). Setting `isPrimary=true` demotes the prior primary producer-side
 * (server authority), so the hook invalidates the product detail too. NO
 * `Idempotency-Key`.
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string; imageId: string }> },
) {
  const requestId = newRequestId();
  const { id, imageId } = await params;

  let body;
  try {
    body = tryParse(UpdateImageBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await updateImage(id, imageId, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

/**
 * Same-origin ecommerce **delete image** mutation proxy
 * (console-integration-contract § 2.4.10 #14):
 * `DELETE /admin/products/{id}/images/{imageId}`. Producer returns 204 (and
 * promotes the lowest-sortOrder image to primary if the deleted one was
 * primary); the proxy returns 204. Confirm-gated in the UI.
 */
export async function DELETE(
  _req: Request,
  { params }: { params: Promise<{ id: string; imageId: string }> },
) {
  const requestId = newRequestId();
  const { id, imageId } = await params;
  try {
    await deleteImage(id, imageId);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
