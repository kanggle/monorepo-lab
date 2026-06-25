import { NextResponse } from 'next/server';
import { ApiError } from '@/shared/api/errors';
import {
  getProduct,
  updateProduct,
  deleteProduct,
} from '@/features/ecommerce-ops/api/products-api';
import {
  UpdateProductBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **product detail** read proxy
 * (console-integration-contract § 2.4.10 #2): `GET /products/{id}` (public read
 * path — the admin controller has no `GET /{id}`).
 *
 * Used by the `useProduct` client hook: the server-rendered detail is seeded as
 * `initialData`, but a mount refetch (`staleTime: 0`) AND every post-mutation
 * `invalidateQueries` re-fetch this same-origin route. Without a `GET` handler
 * the route 405s (TASK-PC-FE-132). The domain-facing IAM OIDC token is attached
 * server-side in `products-api.ts`; NO `X-Tenant-Id` (ecommerce resolves the
 * tenant from the JWT claim). Errors map identically to the collection route
 * (404 PRODUCT_NOT_FOUND / 503 section-degrade) via `mapEcommerceError`.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getProduct(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

/**
 * Same-origin ecommerce **update product** mutation proxy
 * (console-integration-contract § 2.4.10 #4): `PATCH /admin/products/{id}`.
 *
 * Partial update (the producer UpdateProductRequest is all-optional). The
 * domain-facing IAM OIDC token is attached server-side; the body is
 * Zod-validated. NO `Idempotency-Key`; confirm-gated. On `409 CONFLICT`
 * (optimistic lock) the error is surfaced inline → refetch + retry-prompt.
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;

  let body;
  try {
    body = tryParse(UpdateProductBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await updateProduct(id, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

/**
 * Same-origin ecommerce **delete product** mutation proxy
 * (console-integration-contract § 2.4.10 #5): `DELETE /admin/products/{id}`.
 * Producer returns 204; the proxy returns 204. Confirm-gated in the UI.
 *
 * **Idempotent delete (TASK-PC-FE-131)**: the producer soft-deletes, so a second
 * delete of an already-removed product returns `404 PRODUCT_NOT_FOUND`. The
 * delete goal (product absent) is already satisfied, so we render that as `204`
 * rather than a hard failure — consistent with the § 2.4 operator-action
 * idempotency principle ("the console renders the result"; `platform-console` is
 * not `transactional`). The producer contract is unchanged; only the console
 * seam treats not-found-on-delete as success. All other errors (401/403/409/422/
 * 503/timeout) keep their existing `mapEcommerceError` mapping.
 */
export async function DELETE(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    await deleteProduct(id);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      return new NextResponse(null, { status: 204 });
    }
    return mapEcommerceError(err, requestId);
  }
}
