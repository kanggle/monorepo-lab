import { NextResponse } from 'next/server';
import { createArtist, listArtists } from '@/features/fan-directory/api/fan-api';
import { CreateArtistBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan artists route (TASK-MONO-751):
 *   GET  /api/fan/artists?q=&page=&size= → directory search (PUBLISHED only — producer rule)
 *   POST /api/fan/artists                → register (201, starts DRAFT)
 */
export async function GET(req: Request) {
  const requestId = newRequestId();
  const { searchParams } = new URL(req.url);
  const page = searchParams.has('page') ? Number(searchParams.get('page')) : undefined;
  const size = searchParams.has('size') ? Number(searchParams.get('size')) : undefined;
  const q = searchParams.get('q') ?? undefined;
  try {
    return NextResponse.json(await listArtists({ page, size, q }));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}

export async function POST(req: Request) {
  const requestId = newRequestId();
  const body = await parseBody(req, CreateArtistBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await createArtist(body), { status: 201 });
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
