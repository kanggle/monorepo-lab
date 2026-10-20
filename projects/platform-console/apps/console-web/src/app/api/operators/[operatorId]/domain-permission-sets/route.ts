import { NextResponse } from 'next/server';
import { grantOperatorDomainSet } from '@/features/operators/api/operators-domain-access-api';
import {
  GrantDomainSetBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin per-operator ADR-MONO-083 「업무 권한」 DIRECT-grant proxy
 * (TASK-PC-FE-338 slice b — admin-api.md § 도메인 업무 권한 세트 row 7). POST
 * only (201) — `X-Operator-Reason` ONLY (NO `Idempotency-Key`: a repeat is
 * `409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED`, never a double-submit retry).
 * A server `404 OPERATOR_NOT_FOUND` / `404 DOMAIN_PERMISSION_SET_NOT_FOUND` /
 * `409 DOMAIN_PERMISSION_SET_ALREADY_GRANTED` /
 * `422 DOMAIN_NOT_SUBSCRIBED` is surfaced verbatim via `mapError`.
 */
export async function POST(
  req: Request,
  { params }: { params: Promise<{ operatorId: string }> },
) {
  const requestId = newRequestId();
  const { operatorId } = await params;
  let body;
  try {
    body = GrantDomainSetBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    // TASK-PC-FE-354 — `allTenants` ⇒ `X-Tenant-Id: *` (the '*' target's
    // «모든 테넌트» row); omitted ⇒ the active tenant.
    const result = await grantOperatorDomainSet(operatorId, body.setId, body.reason, {
      allTenants: body.allTenants === true,
    });
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
