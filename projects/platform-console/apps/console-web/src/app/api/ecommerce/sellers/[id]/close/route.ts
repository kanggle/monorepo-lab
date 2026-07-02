import { NextResponse } from 'next/server';
import { closeSeller } from '@/features/ecommerce-ops/api/sellers-api';
import { mapEcommerceError, newRequestId } from '../../../products/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin ecommerce seller CLOSE proxy
 * (TASK-PC-FE-154 — ADR-MONO-042 D4): POST /api/ecommerce/sellers/{id}/close
 * → seller → CLOSED (terminal) + deactivate the backing account.
 *
 * Bodyless POST; producer answers 204 (idempotent, null-safe). Domain-facing IAM
 * OIDC token attached server-side; NO X-Tenant-Id; NO Idempotency-Key.
 * 403/404/503 mapped by the shared ecommerce error mapper.
 */
export async function POST(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    await closeSeller(id);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapEcommerceError(err, requestId);
  }
}
