import { NextResponse } from 'next/server';
import {
  listPeriods,
  openPeriod,
} from '@/features/ecommerce-ops/api/settlements-api';
import { OpenPeriodBodySchema } from '@/features/ecommerce-ops/api/settlement-types';
import {
  mapEcommerceError,
  newRequestId,
  badRequest,
  tryParse,
} from '@/app/api/ecommerce/products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce settlement periods proxy:
 *   GET  /api/ecommerce/settlements/periods?page=&size= → list (Phase A)
 *   POST /api/ecommerce/settlements/periods              → open (Phase B, 201)
 *
 * Domain-facing IAM OIDC token server-side; NO X-Tenant-Id; NO Idempotency-Key.
 * POST body is Zod SHAPE-validated (defence-in-depth); the `from < to` ordering
 * gate stays producer-authoritative (422 PERIOD_WINDOW_INVALID passthrough). 4xx
 * bodies are preserved as-is (no 500 오변환).
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const page = searchParams.has('page')
    ? Number(searchParams.get('page'))
    : undefined;
  const size = searchParams.has('size')
    ? Number(searchParams.get('size'))
    : undefined;

  try {
    const result = await listPeriods({ page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();

  let body;
  try {
    body = tryParse(OpenPeriodBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await openPeriod(body);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
