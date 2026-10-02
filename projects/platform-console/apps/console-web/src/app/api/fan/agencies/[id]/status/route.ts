import { NextResponse } from 'next/server';
import { archiveAgency } from '@/features/fan-directory/api/fan-api';
import { ArchiveAgencyBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan agency ARCHIVE route (TASK-MONO-751):
 *   PATCH /api/fan/agencies/{id}/status `{ "status": "ARCHIVED" }` — the only target.
 * Existing affiliations are kept; new ones to it are refused (artist-api § Archive).
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  const body = await parseBody(req, ArchiveAgencyBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await archiveAgency(id));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
