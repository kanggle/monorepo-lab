import { NextResponse } from 'next/server';
import { changeArtistAgency } from '@/features/fan-directory/api/fan-api';
import { ChangeAffiliationBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan artist AFFILIATION route (TASK-MONO-751 — TASK-MONO-748 AC-2):
 *   PATCH /api/fan/artists/{id}/agency `{ "agencyId": "<id>" | null }` — null = unaffiliated.
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
    return NextResponse.json(await changeArtistAgency(id, body));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
