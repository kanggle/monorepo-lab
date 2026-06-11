import 'server-only';
import { gatewayFetch } from '@/shared/api/client';
import type { Notification, NotificationStatus } from '@/entities/notification';

/**
 * The caller's notifications (newest first). The inbox returns a **bare array**
 * under `data` (paging in `meta`), so we read `res.data` directly. Returns `[]`
 * on any error so the bell / inbox degrade to "no notifications" rather than
 * breaking the page — a notification-service outage must not take down every
 * authed page (the bell lives in the shared header).
 */
export async function getNotifications(
  accessToken: string | null,
  status?: NotificationStatus,
  page = 0,
  size = 50,
): Promise<Notification[]> {
  try {
    const res = await gatewayFetch<Notification[]>('/api/v1/notifications', {
      accessToken,
      query: { status, page, size },
      cache: 'no-store',
    });
    return res.data ?? [];
  } catch {
    return [];
  }
}

/** Recent notifications for the header dropdown (all statuses, newest first). */
export async function getRecentNotifications(
  accessToken: string | null,
  limit = 10,
): Promise<Notification[]> {
  return getNotifications(accessToken, undefined, 0, limit);
}

/**
 * Accurate unread count for the badge. Reads `meta.totalElements` of a
 * `status=UNREAD` query (the full count, not just the returned page). Returns 0
 * on error.
 */
export async function getUnreadCount(accessToken: string | null): Promise<number> {
  try {
    const res = await gatewayFetch<Notification[]>('/api/v1/notifications', {
      accessToken,
      query: { status: 'UNREAD', page: 0, size: 1 },
      cache: 'no-store',
    });
    const total = res.meta?.totalElements;
    if (typeof total === 'number') return total;
    return res.data?.length ?? 0;
  } catch {
    return 0;
  }
}
