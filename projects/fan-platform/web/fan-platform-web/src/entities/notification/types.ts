/** Notification kind — produced by the membership lifecycle consumer. */
export type NotificationType = 'WELCOME' | 'CANCELLATION';

/** Stored read state. */
export type NotificationStatus = 'UNREAD' | 'READ';

/**
 * In-app notification (the `NotificationResponse` payload of
 * `GET /api/v1/notifications` / `POST /{id}/read`). The backend uses
 * `@JsonInclude(NON_NULL)`, so `membershipId` (non-membership rows) and `readAt`
 * (still UNREAD) are **absent on the wire**, not `null`.
 */
export interface Notification {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  status: NotificationStatus;
  read: boolean;
  membershipId?: string;
  createdAt: string;
  readAt?: string;
}

/**
 * List response `data`. NOTE: the inbox returns a **bare array** under `data`
 * (with paging in `meta`), unlike membership list's `{ data: { content: [] } }`.
 */
export type NotificationListData = Notification[];
