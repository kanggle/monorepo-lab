import { NextResponse } from 'next/server';
import { changeArtistGroupAgency } from '@/features/fan-directory/api/fan-api';
import { ChangeAffiliationBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan artist-group AFFILIATION route (TASK-MONO-751 — TASK-MONO-748):
 *   PATCH /api/fan/artist-groups/{id}/agency `{ "agencyId": "<id>" | null }`
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  const body = await parseBody(req, ChangeAffiliationBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await changeArtistGroupAgency(id, body));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
