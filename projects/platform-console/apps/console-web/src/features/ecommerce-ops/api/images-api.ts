import { getServerEnv } from '@/shared/config/env';
import {
  callEcommerce,
  type EcommerceCallLabel,
} from './ecommerce-client';
import {
  ImageListSchema,
  type ImageList,
  ImageItemSchema,
  type ImageItem,
  PresignedUrlResponseSchema,
  type PresignedUrlResponse,
  RegisterImageResponseSchema,
  type RegisterImageResponse,
  type PresignedUrlBody,
  type RegisterImageBody,
  type UpdateImageBody,
} from './image-types';

/**
 * Server-side ecommerce `product-service` **image** operations client
 * (TASK-PC-FE-082 — the Phase 1b CLOSING facet, ADR-MONO-031). Drives the
 * in-console product-image operator surface embedded in the product detail:
 * list (#10) / presigned upload url (#11) / register (#12) / update (#13) /
 * delete (#14).
 *
 * Server-only by construction (same posture as `products-api.ts` /
 * `orders-api.ts`): imported exclusively from server components and the
 * `runtime = 'nodejs'` route handlers; `getServerEnv()` throws outside the
 * server runtime. The token never reaches client JS — client components call
 * the same-origin `/api/ecommerce/products/{id}/images/**` proxy routes, which
 * attach the HttpOnly credential here server-side.
 *
 * ── THE AUTH MODEL (console-integration-contract § 2.4.10) ─────────────────
 *
 * Identical to `products-api.ts` (the image endpoints are the SAME
 * `AdminProductImageController` operator-plane subtree, BE-366): per
 * ADR-MONO-017 D2.A this surface is console-web → ecommerce gateway DIRECT (no
 * console-bff write leg). The ecommerce gateway requires `account_type=
 * OPERATOR` on the IAM OIDC token. Therefore this client uses
 * `getDomainFacingToken()` (the assumed tenant-scoped IAM OIDC token when the
 * operator switched to a customer, else the base access token — net-zero;
 * ADR-MONO-020 D4) and NEVER `getOperatorToken()` (that is the IAM
 * `/api/admin/**` exchanged credential — wrong issuer/type here; the #569
 * invariant is GAP-domain-scoped). A test pins that `getOperatorToken` is
 * never called.
 *
 * Tenant invariant (§ 2.4.10): ecommerce resolves the tenant from the JWT
 * `tenant_id ∈ {ecommerce,*}` claim — the console does NOT send `X-Tenant-Id`.
 *
 * Mutation discipline (§ 2.4.10): NO `Idempotency-Key` / `version` — the
 * producer defines none; confirm-gate (UI) + producer state guards
 * (`409 CONFLICT`, `422 IMAGE_LIMIT_EXCEEDED`, `400 MEDIA_VALIDATION_FAILED`)
 * are the double-submit / conflict defence.
 *
 * IMPORTANT — the presigned byte upload is NOT in this module. The actual file
 * bytes are PUT by the BROWSER directly to the S3 `uploadUrl` returned by #11
 * (the presign IS the authorization; no OIDC token / cookie is attached to
 * that cross-origin PUT, and the console server never proxies the bytes — that
 * is the entire point of a presigned URL). This module only mints the URL
 * (#11) and registers the resulting `objectKey` (#12).
 *
 * Error envelope (§ 2.4.10 / § 2.5): ecommerce uses the FLAT shape
 * `{ code, message, timestamp }` (the shared `ErrorResponse.of`). The image
 * codes: `404 IMAGE_NOT_FOUND`/`MEDIA_NOT_FOUND`,
 * `422 IMAGE_LIMIT_EXCEEDED`, `400 MEDIA_VALIDATION_FAILED`/`VALIDATION_ERROR`,
 * `409 CONFLICT`, `503 STORAGE_UNAVAILABLE`. `parseEcommerceError()` tolerates
 * an absent / non-JSON body without crashing.
 *
 * Resilience (§ 2.5 / integration-heavy I1): AbortController hard timeout;
 * `401` → `ApiError` (whole-session re-login); `403` → `ApiError` (inline);
 * `404`/`400`/`422`/`409` → `ApiError` (inline actionable); `503`/timeout/
 * network → `EcommerceUnavailableError` (ONLY the ecommerce section degrades).
 *
 * NOTE: this re-implements the single hardened call site inline (rather than
 * importing from `products-api.ts`) so `products-api.ts` stays 0-change — the
 * same convention the orders facet (`orders-api.ts`) followed.
 */

/**
 * Per-slice observability + message label for the product-image surface. NOTE
 * the message stems diverge from the plain product slice: `product-image
 * service` / `product-image call` (preserved verbatim) and the `image` parser
 * noun + `ecommerce_image_*` log events.
 */
const IMAGE_LABEL: EcommerceCallLabel = {
  event: 'image',
  errorNoun: 'image',
  unavailableLabel: 'product-image service',
  timedOutLabel: 'product-image',
  failedLabel: 'product-image',
};

/**
 * Thin image-subtree wrapper over the shared {@link callEcommerce} core. The
 * image endpoints are always under `ECOMMERCE_ADMIN_BASE_URL` (the
 * `AdminProductImageController` operator-plane subtree), so this wrapper injects
 * that base + the image label — keeping every image call site's `{ method, path }`
 * shape unchanged. The presigned-byte S3 PUT (the browser-direct step) remains
 * outside this module, exactly as before.
 */
function callEcommerceImage<T>(
  opts: { method: string; path: string; body?: unknown },
  parse?: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  return callEcommerce(
    {
      method: opts.method,
      base: env.ECOMMERCE_ADMIN_BASE_URL,
      path: opts.path,
      body: opts.body,
    },
    parse,
    IMAGE_LABEL,
  );
}

function imagesBase(productId: string): string {
  return `/products/${encodeURIComponent(productId)}/images`;
}

// ===========================================================================
// READS
// ===========================================================================

/** #10 — GET /admin/products/{id}/images (operator list, sortOrder-ordered). */
export function listImages(productId: string): Promise<ImageList> {
  return callEcommerceImage(
    { method: 'GET', path: imagesBase(productId) },
    (j) => ImageListSchema.parse(j),
  );
}

// ===========================================================================
// MUTATIONS (confirm/explicit-action gated; NO Idempotency-Key)
// ===========================================================================

/** #11 — POST /admin/products/{id}/images/upload-url (mint presigned PUT URL).
 *  The returned `uploadUrl` is handed to the browser for a DIRECT S3 PUT. */
export function createImageUploadUrl(
  productId: string,
  body: PresignedUrlBody,
): Promise<PresignedUrlResponse> {
  return callEcommerceImage(
    { method: 'POST', path: `${imagesBase(productId)}/upload-url`, body },
    (j) => PresignedUrlResponseSchema.parse(j),
  );
}

/** #12 — POST /admin/products/{id}/images (register an uploaded objectKey; the
 *  producer HEAD-checks the object exists → 404 MEDIA_NOT_FOUND otherwise). */
export function registerImage(
  productId: string,
  body: RegisterImageBody,
): Promise<RegisterImageResponse> {
  return callEcommerceImage(
    { method: 'POST', path: imagesBase(productId), body },
    (j) => RegisterImageResponseSchema.parse(j),
  );
}

/** #13 — PATCH /admin/products/{id}/images/{imageId} (sortOrder / isPrimary). */
export function updateImage(
  productId: string,
  imageId: string,
  body: UpdateImageBody,
): Promise<ImageItem> {
  return callEcommerceImage(
    {
      method: 'PATCH',
      path: `${imagesBase(productId)}/${encodeURIComponent(imageId)}`,
      body,
    },
    (j) => ImageItemSchema.parse(j),
  );
}

/** #14 — DELETE /admin/products/{id}/images/{imageId} (204 No Content). */
export function deleteImage(
  productId: string,
  imageId: string,
): Promise<void> {
  return callEcommerceImage({
    method: 'DELETE',
    path: `${imagesBase(productId)}/${encodeURIComponent(imageId)}`,
  });
}
