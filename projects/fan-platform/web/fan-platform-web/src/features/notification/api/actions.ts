'use server';
import { revalidatePath } from 'next/cache';
import { gatewayFetch } from '@/shared/api/client';
import { getFanSession } from '@/shared/auth/session';
import { ApiError } from '@/shared/api/errors';

/**
 * Mark a single notification READ (`POST /api/v1/notifications/{id}/read`). A 404
 * (foreign / unknown id — the inbox no-leak contract returns 404, never 403) is
 * an idempotent UI no-op: the user intent ("this is read / dismissed") is
 * satisfied either way. Auth / transport errors still throw.
 */
export async function markNotificationRead(id: string): Promise<void> {
  const session = await getFanSession();
  try {
    await gatewayFetch(`/api/v1/notifications/${encodeURIComponent(id)}/read`, {
      accessToken: session.accessToken,
      method: 'POST',
      body: {},
    });
  } catch (err) {
    if (err instanceof ApiError && err.status === 404) {
      revalidatePath('/notifications');
      return;
    }
    throw err;
  }
  revalidatePath('/notifications');
}

/**
 * Mark every supplied unread id READ. There is no bulk endpoint, so this fans
 * out the per-id read concurrently; 404s are tolerated (idempotent no-op). One
 * revalidation after the fan-out settles.
 */
export async function markAllNotificationsRead(ids: string[]): Promise<void> {
  if (ids.length === 0) return;
  const session = await getFanSession();
  await Promise.all(
    ids.map((id) =>
      gatewayFetch(`/api/v1/notifications/${encodeURIComponent(id)}/read`, {
        accessToken: session.accessToken,
        method: 'POST',
        body: {},
      }).catch((err) => {
        if (err instanceof ApiError && err.status === 404) return;
        throw err;
      }),
    ),
  );
  revalidatePath('/notifications');
}
