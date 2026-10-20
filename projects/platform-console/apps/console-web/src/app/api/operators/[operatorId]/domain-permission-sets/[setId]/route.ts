import { NextResponse } from 'next/server';
import { revokeOperatorDomainSet } from '@/features/operators/api/operators-domain-access-api';
import {
  DomainSetReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin per-operator ADR-MONO-083 「업무 권한」 PERSONAL-REVOKE proxy
 * (TASK-PC-FE-338 slice b — admin-api.md § 도메인 업무 권한 세트 row 8). DELETE
 * only — returns `204` → `new NextResponse(null, { status: 204 })` (NEVER
 * `NextResponse.json(...)` on a 204 → Next 500 trap). The reason travels in
 * the request BODY (never the query string — operator-authored free text).
 * Removes ONLY this set's `DIRECT`/`MIGRATION` rows — `GROUP:*` rows survive
 * (the producer's own rule; the dialog hides this button on a group-only
 * set). A server `404 OPERATOR_NOT_FOUND` /
 * `404 DOMAIN_PERMISSION_SET_NOT_FOUND` (no DIRECT/MIGRATION row to remove,
 * including a group-only set) is surfaced verbatim via `mapError`. Next 15
 * dynamic params are a Promise.
 */
export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ operatorId: string; setId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId, setId } = await params;
  let body;
  try {
    body = DomainSetReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    // TASK-PC-FE-354 — `allTenants` ⇒ `X-Tenant-Id: *` (removes the source
    // whose tenantId is '*'); omitted ⇒ the active tenant.
    await revokeOperatorDomainSet(operatorId, setId, body.reason, {
      allTenants: body.allTenants === true,
    });
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
