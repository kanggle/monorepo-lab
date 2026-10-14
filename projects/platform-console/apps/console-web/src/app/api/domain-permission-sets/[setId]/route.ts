import { NextResponse } from 'next/server';
import {
  getDomainPermissionSet,
  updateDomainPermissionSet,
  deleteDomainPermissionSet,
} from '@/features/domain-permission-sets/api/domain-permission-sets-api';
import {
  UpdateSetBodySchema,
  SetReasonBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin domain-permission-set DETAIL + EDIT + DELETE proxy
 * (TASK-PC-FE-338 / ADR-MONO-083, admin-api.md § 도메인 업무 권한 세트
 * rows 2/4/5). GET is a READ (no mutation headers); PATCH carries the reason
 * as `X-Operator-Reason` ONLY (NO `Idempotency-Key` — a repeat PATCH is a
 * natural full-replace, not a double-submit risk); DELETE carries the reason
 * in the request BODY (never the query string) and returns `204` →
 * `new NextResponse(null, { status: 204 })` (NEVER `NextResponse.json(...)`
 * on a 204 → Next 500 trap). Next 15 dynamic params are a Promise. A server
 * `404 DOMAIN_PERMISSION_SET_NOT_FOUND` / `409 DOMAIN_PERMISSION_SET_READ_ONLY`
 * / `409 DOMAIN_PERMISSION_SET_IN_USE` is surfaced verbatim via `mapError`.
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ setId: string }> },
) {
  const requestId = newRequestId();
  const { setId } = await params;
  try {
    const result = await getDomainPermissionSet(setId);
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ setId: string }> },
) {
  const requestId = newRequestId();
  const { setId } = await params;
  let body;
  try {
    body = UpdateSetBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await updateDomainPermissionSet(
      setId,
      { name: body.name, description: body.description, roles: body.roles },
      body.reason,
    );
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function DELETE(
  req: Request,
  { params }: { params: Promise<{ setId: string }> },
) {
  const requestId = newRequestId();
  const { setId } = await params;
  let body;
  try {
    body = SetReasonBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    await deleteDomainPermissionSet(setId, body.reason);
    return new NextResponse(null, { status: 204 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
