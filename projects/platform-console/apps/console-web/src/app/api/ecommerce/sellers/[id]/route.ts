import { NextResponse } from 'next/server';
import { getSeller } from '@/features/ecommerce-ops/api/sellers-api';
import {
  mapEcommerceError,
  newRequestId,
} from '../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce seller [id] proxy
 * (TASK-PC-FE-090 — ADR-MONO-031 § 2.4.10 7th area):
 *   GET /api/ecommerce/sellers/{id} → detail
 *
 * Domain-facing IAM OIDC token server-side; NO X-Tenant-Id; NO
 * Idempotency-Key. READ-ONLY — no PUT/DELETE (producer defines none, v1).
 */

export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    const result = await getSeller(id);
    return NextResponse.json(result);
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
