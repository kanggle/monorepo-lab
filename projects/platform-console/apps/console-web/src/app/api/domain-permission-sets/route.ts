import { NextResponse } from 'next/server';
import {
  listDomainPermissionSets,
  createDomainPermissionSet,
} from '@/features/domain-permission-sets/api/domain-permission-sets-api';
import {
  CreateSetBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin domain-permission-set LIST + CREATE proxy — GET (list, presets
 * first then the active tenant's sets) + POST (create, 201) (TASK-PC-FE-338 /
 * ADR-MONO-083, admin-api.md § 도메인 업무 권한 세트 rows 2-3). GET is a READ
 * (no mutation headers); POST carries the reason as `X-Operator-Reason` +
 * the idempotency key as `Idempotency-Key` server-side. A server
 * `422 DOMAIN_ROLE_NOT_ASSIGNABLE` / `422 DOMAIN_NOT_SUBSCRIBED` /
 * `400 VALIDATION_ERROR` (e.g. the producer's `tenantId='*'` rejection) is
 * surfaced verbatim via `mapError`.
 */
export async function GET() {
  const requestId = newRequestId();
  try {
    const result = await listDomainPermissionSets();
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = CreateSetBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await createDomainPermissionSet(
      { name: body.name, description: body.description, roles: body.roles },
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
