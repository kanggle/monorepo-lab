import { NextResponse } from 'next/server';
import { getArtistGroup } from '@/features/fan-directory/api/fan-api';
import { mapFanError, newRequestId } from '../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan artist-group [id] route (TASK-MONO-751):
 *   GET /api/fan/artist-groups/{id} → group + members
 */
export async function GET(
  _req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  try {
    return NextResponse.json(await getArtistGroup(id));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
