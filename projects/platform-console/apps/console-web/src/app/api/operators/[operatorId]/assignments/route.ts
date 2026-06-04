import { NextResponse } from 'next/server';
import { listOperatorAssignments } from '@/features/operators/api/operators-api';
import { mapError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin operator-assignments proxy (TASK-PC-FE-050 / TASK-BE-339).
 * READ only — `GET /api/admin/operators/{operatorId}/assignments` scoped to
 * the active tenant (`X-Tenant-Id`, attached server-side). The operator
 * token + tenant are attached by the api layer; this proxy never fabricates
 * a reason / idempotency key (it is a pure read). The response is
 * `{ assignments: [...] }` (0 or 1 rows for the active tenant — empty ⇒
 * home-tenant-only operator with no explicit assignment).
 *
 * GET-ONLY: no POST/PUT/PATCH/DELETE handler is exported — Next.js returns
 * 405 for any other method on this route (a test pins the absence + the
 * 405). The server-only credential (`getOperatorToken` via the api layer)
 * never reaches client JS.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ operatorId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId } = await params;
  try {
    const result = await listOperatorAssignments(operatorId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
