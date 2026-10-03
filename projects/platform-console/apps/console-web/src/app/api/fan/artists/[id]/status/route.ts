import { NextResponse } from 'next/server';
import { changeArtistStatus } from '@/features/fan-directory/api/fan-api';
import { ArtistStatusBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan artist STATUS route (TASK-MONO-751):
 *   PATCH /api/fan/artists/{id}/status `{ "status": "PUBLISHED" | "ARCHIVED", reason? }`
 * Allowed: DRAFT → PUBLISHED, DRAFT/PUBLISHED → ARCHIVED (producer state machine).
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  const body = await parseBody(req, ArtistStatusBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await changeArtistStatus(id, body));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
