/**
 * Client-safe TanStack Query key factories for `features/notifications`
 * (TASK-PC-FE-052).
 *
 * Server-only modules (`notification-api.ts` reads cookies via
 * `getDomainFacingToken`) must NOT be imported from client components.
 * This module is intentionally **dependency-free** so the client hooks
 * (`hooks/use-notifications.ts`) can import the keys without dragging
 * server-only code into the client bundle.
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

/** Key for a single notification detail query. */
export function notificationDetailKey(id: string) {
  return [NOTIFICATION_KEY, 'detail', id] as const;
}
