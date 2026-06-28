/**
 * Client-safe TanStack Query key factories for `features/notifications`
 * (TASK-PC-FE-052).
 *
 * Intentionally **dependency-free** so the client hooks
 * (`hooks/use-notifications.ts`) can import the keys without dragging any
 * server-only code (cookie/session reads) into the client bundle.
 */

export const NOTIFICATION_KEY = 'notifications';

/** Key for the inbox list query (unread filter + pagination). */
export function notificationInboxKey(
  unread: boolean | undefined,
  page: number,
  size: number,
) {
  return [
    NOTIFICATION_KEY,
    'inbox',
    unread !== undefined ? String(unread) : null,
    page,
    size,
  ] as const;
}
