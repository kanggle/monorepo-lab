import { NextResponse } from 'next/server';
import { createImageUploadUrl } from '@/features/ecommerce-ops/api/images-api';
import {
  PresignedUrlBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **presigned upload URL** mutation proxy
 * (console-integration-contract § 2.4.10 #11):
 * `POST /admin/products/{id}/images/upload-url`.
 *
 * Mints a short-lived S3 presigned PUT URL for the operator's chosen file. The
 * domain-facing IAM OIDC token is attached server-side. The response
 * `{ uploadUrl, objectKey, expiresAt }` is returned verbatim to the client,
 * which then PUTs the bytes DIRECTLY to `uploadUrl` (browser → S3) — the
 * console server NEVER proxies the file bytes (the whole point of a presigned
 * URL). Body Zod-validated (`{ contentType, contentLength }`); the producer
 * re-validates content-type/length (→ 400 MEDIA_VALIDATION_FAILED).
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(PresignedUrlBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await createImageUploadUrl(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
