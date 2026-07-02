import { NextResponse } from 'next/server';
import { provisionSeller } from '@/features/ecommerce-ops/api/sellers-api';
import { mapEcommerceError, newRequestId } from '../../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce seller PROVISION proxy
 * (TASK-PC-FE-154 — ADR-MONO-042 D3): POST /api/ecommerce/sellers/{id}/provision
 * → re-provision a PENDING_PROVISIONING seller.
 *
 * Bodyless POST; producer answers 204 (idempotent — already-ACTIVE is a no-op).
 * Domain-facing IAM OIDC token attached server-side; NO X-Tenant-Id; NO
 * Idempotency-Key. 403/404/503 mapped by the shared ecommerce error mapper.
 */
export async function POST(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    await provisionSeller(id);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
