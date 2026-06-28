/**
 * `features/notifications` public API (TASK-PC-FE-052).
 *
 * The shell layout (`app/(console)/layout.tsx`) imports `NotificationBell`
 * from here — never from internal module paths.
 */
export { NotificationBell } from './components/NotificationBell';
export type {
  Notification,
  AggregatedNotification,
  NotificationInboxResponse,
  NotificationDetailResponse,
  NotificationMeta,
  NotificationInboxQueryParams,
} from './api/notification-types';
export { isApprovalSource } from './api/notification-types';
export { NOTIFICATION_KEY, notificationInboxKey } from './api/notification-keys';
