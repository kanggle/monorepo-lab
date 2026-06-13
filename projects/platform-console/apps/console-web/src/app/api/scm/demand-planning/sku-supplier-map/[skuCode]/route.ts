import { NextResponse } from 'next/server';
import {
  getSupplierMap,
  putSupplierMap,
} from '@/features/scm-config/api/demand-planning-seed-api';
import { SupplierMapInputSchema } from '@/features/scm-config/api/types';
import { mapReplenishmentError, badRequest, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin scm demand-planning **sku-supplier-map** seed/config proxy
 * (console-integration-contract § 2.4.6.2). GET inspects the per-SKU mapping;
 * PUT upserts it (idempotent — the body IS the FULL row). This is the
 * operational fix-path for FE-077's `SKU_SUPPLIER_UNMAPPED`.
 *
 * The domain-facing IAM OIDC token is attached server-side in the seed client
 * (NOT the operator token — § 2.4.6.2). NO `Idempotency-Key`, NO
 * `X-Operator-Reason`. `supplierId` is free-text/uuid (no supplier master in
 * v1). A producer `404 MAPPING_NOT_FOUND` is surfaced as a typed
 * `{ found: false }` 200 — "not configured yet → create", NOT a 404 error toast.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ skuCode: string }> },
) {
  const requestId = newRequestId();
  const { skuCode } = await params;
  try {
    const result = await getSupplierMap(skuCode);
    return NextResponse.json(result);
  } catch (err) {
    return mapReplenishmentError(err, requestId);
  }
}

export async function PUT(
  req: Request,
  { params }: { params: Promise<{ skuCode: string }> },
) {
  const requestId = newRequestId();
  const { skuCode } = await params;

  let body;
  try {
    const raw = await req.json();
    const parsed = SupplierMapInputSchema.safeParse(raw);
    if (!parsed.success) return badRequest();
    body = parsed.data;
  } catch {
    return badRequest();
  }

  try {
    const result = await putSupplierMap(skuCode, body);
    return NextResponse.json(result);
  } catch (err) {
    return mapReplenishmentError(err, requestId);
  }
}
