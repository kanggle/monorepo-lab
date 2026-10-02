import { NextResponse } from 'next/server';
import { getAgency, renameAgency } from '@/features/fan-directory/api/fan-api';
import { RenameAgencyBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan agency [id] route (TASK-MONO-751):
 *   GET   /api/fan/agencies/{id} → detail
 *   PATCH /api/fan/agencies/{id} → rename (`{ name }`)
 * There is no DELETE — an agency is ARCHIVED (`./status`), never removed (artist-api).
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    return NextResponse.json(await getAgency(id));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}

export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  const body = await parseBody(req, RenameAgencyBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await renameAgency(id, body));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
