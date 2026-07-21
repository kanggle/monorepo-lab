import { NextResponse } from 'next/server';
import {
  listProducts,
  registerProduct,
} from '@/features/ecommerce-ops/api/products-api';
import {
  RegisterProductRequestSchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce **product list** read proxy
 * (console-integration-contract § 2.4.10 #1): `GET /admin/products`.
 *
 * Used by the `useProducts` client hook for re-query on status-filter /
 * category / pagination (the server-rendered first page is seeded as
 * `initialData`; only a filter/page change reaches this handler). The
 * domain-facing IAM OIDC token is attached server-side in `products-api.ts`
 * — NOT the operator token (§ 2.4.10); NO X-Tenant-Id (ecommerce resolves the
 * tenant from the JWT claim).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const status = searchParams.get('status') ?? undefined;
  const categoryId = searchParams.get('categoryId') ?? undefined;
  const page = searchParams.has('page')
    ? Number(searchParams.get('page'))
    : undefined;
  const size = searchParams.has('size')
    ? Number(searchParams.get('size'))
    : undefined;

  try {
    const result = await listProducts({ status, categoryId, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

/**
 * Same-origin ecommerce **register product** mutation proxy
 * (console-integration-contract § 2.4.10 #3): `POST /admin/products`.
 *
 * The domain-facing IAM OIDC token is attached server-side (NOT the operator
 * token — § 2.4.10); the body is Zod-validated before it reaches the upstream.
 * The producer now REQUIRES `Idempotency-Key` (TASK-BE-536); the console mints
 * it once per confirmed create and sends it in the body (TASK-PC-FE-252) — this
 * route strips it out and passes it as a separate arg (kept out of the producer
 * body).
 */
export async function POST(req: Request) {
  const requestId = newRequestId();

  let parsed;
  try {
    parsed = tryParse(RegisterProductRequestSchema, await req.json());
  } catch {
    return badRequest();
  }
  if (parsed === null) return badRequest();
  const { idempotencyKey, ...body } = parsed;

  try {
    const result = await registerProduct(body, idempotencyKey);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
