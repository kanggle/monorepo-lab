// Notification types

export type NotificationChannel = 'EMAIL' | 'SMS' | 'PUSH';

export type NotificationStatus = 'PENDING' | 'SENT' | 'FAILED';

export type NotificationTemplateType =
  | 'ORDER_PLACED'
  | 'PAYMENT_COMPLETED'
  | 'SHIPPING_STATUS_CHANGED'
  | 'WELCOME';

export interface NotificationSummary {
  notificationId: string;
  channel: NotificationChannel;
  subject: string;
  status: NotificationStatus;
  sentAt: string;
  createdAt: string;
}

export interface NotificationDetail {
  notificationId: string;
  channel: NotificationChannel;
  subject: string;
  body: string;
  status: NotificationStatus;
  sentAt: string;
  createdAt: string;
}

export interface NotificationPreferences {
  userId: string;
  emailEnabled: boolean;
  smsEnabled: boolean;
  pushEnabled: boolean;
}

export interface UpdateNotificationPreferencesRequest {
  emailEnabled: boolean;
  smsEnabled: boolean;
  pushEnabled: boolean;
}

// Web Push (VAPID) subscription types (TASK-BE-464 / TASK-FE-083)

export interface VapidPublicKeyResponse {
  publicKey: string;
}

export interface PushSubscriptionKeys {
  p256dh: string;
  auth: string;
}

/** W3C PushSubscription serialization posted to register a browser subscription. */
export interface RegisterPushSubscriptionRequest {
  endpoint: string;
  expirationTime?: number | null;
  keys: PushSubscriptionKeys;
}

export interface RegisterPushSubscriptionResponse {
  subscriptionId: string;
}

/**
 * One registered Web Push device/browser in the user's subscription list
 * (TASK-FE-085). Keys (p256dh/auth) are never exposed; `endpoint` lets the
 * current browser mark its own row. `userAgent` is the header captured at
 * registration (may be null) — the client parses it into a device label.
 */
export interface PushSubscriptionDevice {
  id: string;
  endpoint: string;
  userAgent: string | null;
  createdAt: string;
}

export interface ListPushSubscriptionsResponse {
  subscriptions: PushSubscriptionDevice[];
}

// Notification Template types (admin)

export interface NotificationTemplateSummary {
  templateId: string;
  type: NotificationTemplateType;
  channel: NotificationChannel;
  subject: string;
  createdAt: string;
}

export interface NotificationTemplateDetail {
  templateId: string;
  type: NotificationTemplateType;
  channel: NotificationChannel;
  subject: string;
  body: string;
  createdAt: string;
}

export interface CreateNotificationTemplateRequest {
  type: NotificationTemplateType;
  channel: NotificationChannel;
  subject: string;
  body: string;
}

export interface UpdateNotificationTemplateRequest {
  subject: string;
  body: string;
}

export interface NotificationTemplateResponse {
  templateId: string;
}

export interface NotificationTemplateListParams {
  page?: number;
  size?: number;
}
