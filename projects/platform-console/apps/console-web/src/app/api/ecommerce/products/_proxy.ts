import { NextResponse } from 'next/server';
import { z } from 'zod';
import { EcommerceUnavailableError } from '@/shared/api/errors';
import { makeProxyErrorMapper } from '@/shared/api/proxy-factory';
import { newRequestId } from '@/shared/lib/logger';
import {
  RegisterProductBodySchema,
  UpdateProductBodySchema,
  AddVariantBodySchema,
  UpdateVariantBodySchema,
  AdjustStockBodySchema,
  CreatePromotionBodySchema,
  UpdatePromotionBodySchema,
  IssueCouponBodySchema,
} from '@/features/ecommerce-ops/api/types';
import {
  PresignedUrlBodySchema,
  RegisterImageBodySchema,
  UpdateImageBodySchema,
} from '@/features/ecommerce-ops/api/image-types';

/**
 * Shared error → HTTP mapping + body validators for the ecommerce-ops product
 * same-origin proxy routes (console-integration-contract § 2.4.10 / § 2.5).
 * The HttpOnly **domain-facing IAM OIDC access token** is attached server-side
 * in `products-api.ts` — NOT the IAM exchanged operator token (the ecommerce
 * gateway requires the IAM OIDC token with `account_type=OPERATOR`; the #569
 * invariant is GAP-domain-scoped — § 2.4.10). Mirrors the `wms-outbound/_proxy`
 * shape but for the ecommerce FLAT envelope.
 *
 *   - 401 → 401 (the client api-client triggers a WHOLE-SESSION re-login).
 *   - 403 → 403 (role-insufficient → inline "not available to your role").
 *   - 400 / 404 / 422 VALIDATION_ERROR / 409 CONFLICT → passthrough (inline
 *     actionable, no crash; the 409 CONFLICT path drives a refetch + retry).
 *   - 503 / timeout / network → 503 (ONLY the ecommerce section degrades).
 *
 * No token / product data is ever logged. NO `Idempotency-Key` is added (the
 * producer defines none — § 2.4.10): confirm-gate + producer state guards.
 */

export const mapEcommerceError = makeProxyErrorMapper(
  'ecommerce',
  EcommerceUnavailableError,
);

/** A 422 for an invalid request body (Zod parse failure). */
export function badRequest(): NextResponse {
  return NextResponse.json(
    { code: 'VALIDATION_ERROR', message: 'invalid request body' },
    { status: 422 },
  );
}

// Re-export the producer-matched body schemas so the route handlers validate
// the client payload before it reaches the upstream (defence-in-depth — the
// producer validates too, but the proxy never forwards a malformed body).
export {
  RegisterProductBodySchema,
  UpdateProductBodySchema,
  AddVariantBodySchema,
  UpdateVariantBodySchema,
  AdjustStockBodySchema,
  // image facet (TASK-PC-FE-082 — § 2.4.10 #11/#12/#13)
  PresignedUrlBodySchema,
  RegisterImageBodySchema,
  UpdateImageBodySchema,
  // promotions facet (TASK-PC-FE-086 — ADR-031 Phase 3b)
  CreatePromotionBodySchema,
  UpdatePromotionBodySchema,
  IssueCouponBodySchema,
  newRequestId,
};

/** A generic parse helper: returns the parsed value or null on failure. */
export function tryParse<T>(schema: z.ZodType<T>, value: unknown): T | null {
  const result = schema.safeParse(value);
  return result.success ? result.data : null;
}
