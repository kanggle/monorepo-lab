'use server';
import { revalidatePath } from 'next/cache';
import { gatewayFetch } from '@/shared/api/client';
import { getFanSession } from '@/shared/auth/session';
import { ApiError } from '@/shared/api/errors';

/**
 * Follow / unfollow server actions. Idempotent at the UI level — `409
 * ALREADY_FOLLOWING` and `404 NOT_FOLLOWING` are swallowed because the user
 * intent ("be in follow state X") is satisfied either way.
 *
 * 🔴 `artistAccountId` and `artistId` are two different identifiers and both are
 * needed (TASK-FAN-BE-045). The API follows the **account** — community-service
 * refuses a target that is not a live `artists.account_id`. The detail route is
 * keyed on the artist **entity id** (`/artists/[id]`). They coincide only for the
 * backfilled demo rows, so revalidating with the account id looks correct in the
 * demo and silently revalidates nothing for a real artist.
 */
export async function followArtist(artistAccountId: string, artistId: string): Promise<void> {
  const session = await getFanSession();
  try {
    await gatewayFetch('/api/v1/community/follows', {
      accessToken: session.accessToken,
      method: 'POST',
      body: { artistAccountId },
    });
  } catch (err) {
    if (err instanceof ApiError && err.code === 'ALREADY_FOLLOWING') return;
    throw err;
  }
  revalidatePath(`/artists/${artistId}`);
  revalidatePath('/');
}

export async function unfollowArtist(artistAccountId: string, artistId: string): Promise<void> {
  const session = await getFanSession();
  try {
    await gatewayFetch(`/api/v1/community/follows/${encodeURIComponent(artistAccountId)}`, {
      accessToken: session.accessToken,
      method: 'DELETE',
    });
  } catch (err) {
    if (err instanceof ApiError && err.code === 'NOT_FOLLOWING') return;
    throw err;
  }
  revalidatePath(`/artists/${artistId}`);
  revalidatePath('/');
}
