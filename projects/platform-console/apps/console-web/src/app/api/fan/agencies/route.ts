import { NextResponse } from 'next/server';
import { createAgency, listAgencies } from '@/features/fan-directory/api/fan-api';
import { CreateAgencyBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan agencies route (TASK-MONO-751 — ADR-MONO-079 D4-A):
 *   GET  /api/fan/agencies?page=&size= → `GET  /api/v1/agencies` (unwrapped to a page)
 *   POST /api/fan/agencies             → `POST /api/v1/agencies` (201)
 *
 * Domain-facing token (the platform operator's assumed `fan-platform` token) attached
 * server-side; NO `X-Tenant-Id`. Body validated before the upstream.
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const page = searchParams.has('page') ? Number(searchParams.get('page')) : undefined;
  const size = searchParams.has('size') ? Number(searchParams.get('size')) : undefined;
  try {
    return NextResponse.json(await listAgencies({ page, size }));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  const body = await parseBody(req, CreateAgencyBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await createAgency(body), { status: 201 });
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
