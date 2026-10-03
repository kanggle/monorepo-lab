import { NextResponse } from 'next/server';
import { createArtistGroup } from '@/features/fan-directory/api/fan-api';
import { CreateGroupBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan artist-groups route (TASK-MONO-751):
 *   POST /api/fan/artist-groups → create (201)
 * No GET: the producer has no group list endpoint (artist-api § Artist groups) — a group is
 * reached by id.
 */
export async function POST(req: Request) {
  const requestId = newRequestId();
  const body = await parseBody(req, CreateGroupBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await createArtistGroup(body), { status: 201 });
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
