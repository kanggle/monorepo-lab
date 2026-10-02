import { NextResponse } from 'next/server';
import { linkAgencyStoreSeller } from '@/features/fan-directory/api/fan-api';
import { LinkStoreSellerBodySchema } from '@/features/fan-directory/api/fan-types';
import { badRequest, mapFanError, newRequestId, parseBody } from '../../../_proxy';

export const runtime = 'nodejs';

/**
 * Same-origin fan agency STORE-SELLER route (TASK-MONO-751 — ADR-MONO-079 D2):
 *   PATCH /api/fan/agencies/{id}/store-seller `{ "storeSellerId": "<seller_id>" | null }`
 *
 * artist-service verifies a value against the store BEFORE saving: 422
 * `STORE_SELLER_NOT_FOUND` / `STORE_SELLER_CLOSED`, and 503
 * `STORE_SELLER_LOOKUP_UNAVAILABLE` when it cannot ask — which, until TASK-MONO-759 wires the
 * transport, is EVERY value. Those codes pass through unchanged (the 503 keeps its producer
 * code via `FanUnavailableError`), so the screen can say «nothing was saved» rather than
 * «fan is down». `null` clears the link without asking the store.
 */
export async function PATCH(
  req: Request,
  { params }: { params: Promise<{ id: string }> },
) {
  const requestId = newRequestId();
  const { id } = await params;
  const body = await parseBody(req, LinkStoreSellerBodySchema);
  if (body === null) return badRequest();
  try {
    return NextResponse.json(await linkAgencyStoreSeller(id, body));
  } catch (err) {
    return mapFanError(err, requestId);
  }
}
