import { NextResponse } from 'next/server';
import {
  listSellers,
  registerSeller,
} from '@/features/ecommerce-ops/api/sellers-api';
import {
  RegisterSellerBodySchema,
  mapEcommerceError,
  badRequest,
  tryParse,
  newRequestId,
} from '../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce sellers proxy — list + register
 * (TASK-PC-FE-090 — ADR-MONO-031 § 2.4.10 7th area):
 *   GET  /api/ecommerce/sellers?page=&size= → list
 *   POST /api/ecommerce/sellers             → register (201)
 *
 * Domain-facing IAM OIDC token attached server-side (NOT the operator token —
 * § 2.4.10); NO X-Tenant-Id; NO Idempotency-Key. Zod-validated before
 * reaching the upstream (defence-in-depth). Targets ECOMMERCE_ADMIN_BASE_URL
 * (sellers live under /api/admin/sellers — admin subtree, not public path).
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
    const result = await listSellers({ page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();

  let body;
  try {
    body = tryParse(RegisterSellerBodySchema, await req.json());
  } catch {
    return badRequest();
  }
  if (body === null) return badRequest();

  try {
    const result = await registerSeller(body);
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
