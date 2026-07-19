import { NextResponse } from 'next/server';
import {
  listGroups,
  createGroup,
} from '@/features/operator-groups/api/operator-groups-api';
import {
  CreateGroupBodySchema,
  mapError,
  badRequest,
  newRequestId,
} from './_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin operator-group LIST proxy (GET) + CREATE proxy (POST) for client
 * components (TASK-PC-FE-250 / ADR-MONO-046) — the typed api-client's single
 * backend entry point (no browser-direct IAM call, architecture.md § Forbidden
 * Dependencies). The HttpOnly operator token + active tenant are attached
 * server-side in the api layer; the reason rides as `X-Operator-Reason` and the
 * create idempotency key as `Idempotency-Key`.
 *
 * GET → `listGroups` (actor-scope confined by the server). POST → `createGroup`
 * (201). 401 → 401 (re-login); 503/timeout → 503 (운영자 그룹 section degrades
 * only); 400 NO_ACTIVE_TENANT → 400; 403/404/409 producer → inline actionable.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const url = new URL(req.url);
  const tenantId = url.searchParams.get('tenantId') ?? undefined;
  const pageParam = url.searchParams.get('page');
  const sizeParam = url.searchParams.get('size');
  try {
    const result = await listGroups({
      tenantId: tenantId === '' ? undefined : tenantId,
      page: pageParam !== null ? Number(pageParam) : undefined,
      size: sizeParam !== null ? Number(sizeParam) : undefined,
    });
    return NextResponse.json(result);
  } catch (err) {
    return mapError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  let body;
  try {
    body = CreateGroupBodySchema.parse(await req.json());
  } catch {
    return badRequest();
  }
  try {
    const result = await createGroup(
      { tenantId: body.tenantId, name: body.name, description: body.description },
      body.reason,
      body.idempotencyKey,
    );
    return NextResponse.json(result, { status: 201 });
  } catch (err) {
    return mapError(err, requestId);
  }
}
