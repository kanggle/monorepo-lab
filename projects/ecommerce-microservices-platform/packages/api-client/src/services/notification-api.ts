import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  NotificationSummary,
  NotificationDetail,
  NotificationPreferences,
  UpdateNotificationPreferencesRequest,
} from '@repo/types';

export function createNotificationApi(client: ApiClient) {
  return {
    getMyNotifications: (params?: { page?: number; size?: number }) =>
      client.get<PaginatedResponse<NotificationSummary>>(
        '/api/notifications/me',
        { params },
      ),

    getMyNotification: (notificationId: string) =>
      client.get<NotificationDetail>(
        `/api/notifications/me/${notificationId}`,
      ),

    getMyPreferences: () =>
      client.get<NotificationPreferences>(
        '/api/notifications/me/preferences',
      ),

    updateMyPreferences: (data: UpdateNotificationPreferencesRequest) =>
      client.put<NotificationPreferences>(
        '/api/notifications/me/preferences',
        data,
      ),
  };
}
