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
