import { NextResponse } from 'next/server';
import { getArtist, updateArtist } from '@/features/fan-directory/api/fan-api';
import { UpdateArtistBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan artist [id] route (TASK-MONO-751):
 *   GET   /api/fan/artists/{id} → detail (DRAFT/ARCHIVED visible to an admin-tier token)
 *   PATCH /api/fan/artists/{id} → profile update (no `accountId` — immutable by contract)
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    return NextResponse.json(await getArtist(id));
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
  const body = await parseBody(req, UpdateArtistBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await updateArtist(id, body));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
