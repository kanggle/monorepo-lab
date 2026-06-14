import { NextResponse } from 'next/server';
import { listShippings } from '@/features/ecommerce-ops/api/shippings-api';
import {
  mapEcommerceError,
  newRequestId,
} from '../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce shippings proxy — list
 * (TASK-PC-FE-088 — ADR-031 Phase 4b):
 *   GET  /api/ecommerce/shippings?status&page&size → paginated list
 *
 * Domain-facing IAM OIDC token attached server-side (NOT the operator token —
 * § 2.4.10.3); NO X-Tenant-Id; NO Idempotency-Key.
 * Base URL: ECOMMERCE_PUBLIC_BASE_URL + /shippings (non-admin path, mirrors
 * promotions — NOT ECOMMERCE_ADMIN_BASE_URL; shipping has no /api/admin route).
 */

export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const status = searchParams.get('status') ?? undefined;
  const page = Number(searchParams.get('page') ?? 0);
  const size = Number(searchParams.get('size') ?? 20);

  try {
    const result = await listShippings({ status, page, size });
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
