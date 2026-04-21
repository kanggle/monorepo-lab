import { apiClient } from '@/shared/config/api';
import { createNotificationApi } from '@repo/api-client';
import type {
  PaginatedResponse,
  NotificationSummary,
  NotificationDetail,
  NotificationPreferences,
  UpdateNotificationPreferencesRequest,
} from '@repo/types';

const notificationApi = createNotificationApi(apiClient);

export async function getMyNotifications(
  page = 0,
  size = 20,
): Promise<PaginatedResponse<NotificationSummary>> {
  return notificationApi.getMyNotifications({ page, size });
}

export async function getMyNotification(
  notificationId: string,
): Promise<NotificationDetail> {
  return notificationApi.getMyNotification(notificationId);
}

export async function getMyPreferences(): Promise<NotificationPreferences> {
  return notificationApi.getMyPreferences();
}

export async function updateMyPreferences(
  data: UpdateNotificationPreferencesRequest,
): Promise<NotificationPreferences> {
  return notificationApi.updateMyPreferences(data);
}
