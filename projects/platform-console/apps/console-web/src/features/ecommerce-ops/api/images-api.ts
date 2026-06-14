import { getServerEnv } from '@/shared/config/env';
import { getDomainFacingToken } from '@/shared/lib/session';
import { logger, newRequestId } from '@/shared/lib/logger';
import { ApiError, EcommerceUnavailableError } from '@/shared/api/errors';
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

type Method = 'GET' | 'POST' | 'PATCH' | 'DELETE';

interface CallOptions {
  method: Method;
  /** Path relative to `ECOMMERCE_ADMIN_BASE_URL`
   *  (e.g. `/products/{id}/images`). */
  path: string;
  body?: unknown;
}

/**
 * Parses the ecommerce FLAT error envelope (`{ code, message, timestamp }`).
 * Defensive: a missing / non-JSON body degrades to a synthetic code rather
 * than throwing.
 */
async function parseEcommerceError(
  res: Response,
): Promise<{ code: string; message: string; timestamp?: string }> {
  let code = `HTTP_${res.status}`;
  let message = `ecommerce image request failed (${res.status})`;
  let timestamp: string | undefined;
  try {
    const body = (await res.json()) as {
      code?: string;
      message?: string;
      timestamp?: string;
    };
    if (body && typeof body === 'object') {
      code = body.code ?? code;
      message = body.message ?? message;
      timestamp = body.timestamp;
    }
  } catch {
    /* keep the synthetic defaults — never throw on a bad error body */
  }
  return { code, message, timestamp };
}

/**
 * Single hardened call site for the image admin subtree. Resolves the
 * domain-facing IAM OIDC token, applies the timeout, and maps the ecommerce
 * flat error envelope to the § 2.5 resilience taxonomy. `parse` is `undefined`
 * for a 204 (DELETE).
 */
async function callEcommerceImage<T>(
  opts: CallOptions,
  parse?: (json: unknown) => T,
): Promise<T> {
  const env = getServerEnv();
  const requestId = newRequestId();

  // Per-domain credential (§ 2.4.10): the DOMAIN-FACING IAM OIDC token, NEVER
  // getOperatorToken() (that is the IAM-domain exchanged credential).
  const token = await getDomainFacingToken();
  if (!token) {
    logger.warn('ecommerce_image_no_gap_session', {
      requestId,
      path: opts.path,
    });
    throw new ApiError(401, 'UNAUTHORIZED', 'No IAM session');
  }

  const headers: Record<string, string> = {
    Accept: 'application/json',
    Authorization: `Bearer ${token}`,
    'X-Request-Id': requestId,
  };
  // NO `X-Tenant-Id` (gateway resolves tenant from the JWT claim); NO
  // `Idempotency-Key` (producer defines none) — § 2.4.10.
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), env.ECOMMERCE_TIMEOUT_MS);

  try {
    const res = await fetch(`${env.ECOMMERCE_ADMIN_BASE_URL}${opts.path}`, {
      method: opts.method,
      headers,
      body: opts.body === undefined ? undefined : JSON.stringify(opts.body),
      cache: 'no-store',
      signal: controller.signal,
    });

    if (res.status === 401) {
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_image_unauthorized', {
        requestId,
        status: 401,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(401, e.code || 'UNAUTHORIZED', 'session expired');
    }

    if (res.status === 403) {
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_image_forbidden', {
        requestId,
        status: 403,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(403, e.code || 'FORBIDDEN', 'not permitted');
    }

    if (res.status === 503) {
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_image_degraded', {
        requestId,
        status: 503,
        code: e.code,
        path: opts.path,
      });
      // 503 STORAGE_UNAVAILABLE / CIRCUIT_OPEN — ONLY the ecommerce section
      // degrades; the console shell + other sections stay intact.
      throw new EcommerceUnavailableError(
        e.code === 'CIRCUIT_OPEN' ? 'circuit_open' : 'downstream',
        e.code || 'SERVICE_UNAVAILABLE',
        'ecommerce product-image service unavailable',
      );
    }

    if (!res.ok) {
      // 404 IMAGE_NOT_FOUND / MEDIA_NOT_FOUND / PRODUCT_NOT_FOUND,
      // 422 IMAGE_LIMIT_EXCEEDED, 400 MEDIA_VALIDATION_FAILED / VALIDATION_ERROR,
      // 409 CONFLICT → inline actionable (no crash).
      const e = await parseEcommerceError(res);
      logger.warn('ecommerce_image_request_error', {
        requestId,
        status: res.status,
        code: e.code,
        path: opts.path,
      });
      throw new ApiError(res.status, e.code, e.message, e.timestamp);
    }

    logger.info('ecommerce_image_ok', {
      requestId,
      status: res.status,
      path: opts.path,
    });

    // 204 No Content (DELETE) — nothing to parse.
    if (res.status === 204 || parse === undefined) {
      return undefined as T;
    }
    const json = await res.json();
    return parse(json);
  } catch (err) {
    if (err instanceof ApiError || err instanceof EcommerceUnavailableError) {
      throw err;
    }
    if (err instanceof Error && err.name === 'AbortError') {
      logger.warn('ecommerce_image_timeout', {
        requestId,
        timeoutMs: env.ECOMMERCE_TIMEOUT_MS,
        path: opts.path,
      });
      throw new EcommerceUnavailableError(
        'timeout',
        'TIMEOUT',
        'ecommerce product-image call timed out',
      );
    }
    logger.error('ecommerce_image_error', { requestId, path: opts.path });
    throw new EcommerceUnavailableError(
      'downstream',
      'NETWORK_ERROR',
      'ecommerce product-image call failed',
    );
  } finally {
    clearTimeout(timer);
  }
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
