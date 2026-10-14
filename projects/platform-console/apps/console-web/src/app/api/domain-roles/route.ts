import { NextResponse } from 'next/server';
import { listDomainRoles } from '@/features/domain-permission-sets/api/domain-permission-sets-api';
import { mapError, newRequestId } from '../domain-permission-sets/_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin domain-ROLE-CATALOG proxy — GET only (TASK-PC-FE-338 /
 * ADR-MONO-083, admin-api.md § 도메인 업무 권한 세트 row 1). No mutation
 * headers (`X-Operator-Reason` / `Idempotency-Key`) — this is a pure read,
 * tenant-independent catalog.
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const result = await listDomainRoles();
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}
