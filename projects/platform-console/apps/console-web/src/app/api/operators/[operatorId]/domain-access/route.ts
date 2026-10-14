import { NextResponse } from 'next/server';
import { getOperatorDomainAccess } from '@/features/operators/api/operators-domain-access-api';
import { mapError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin per-operator ADR-MONO-083 「업무 권한」 READ proxy (TASK-PC-FE-338
 * slice b — admin-api.md § 도메인 업무 권한 세트 row 6). GET only — no mutation
 * headers. A server `404 OPERATOR_NOT_FOUND` / `403 PERMISSION_DENIED` /
 * `403 TENANT_SCOPE_DENIED` is surfaced verbatim via `mapError`.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ operatorId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId } = await params;
  try {
    const result = await getOperatorDomainAccess(operatorId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
