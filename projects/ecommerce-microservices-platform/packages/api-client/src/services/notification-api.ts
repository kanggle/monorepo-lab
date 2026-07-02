import type { ApiClient } from '../client';
import type {
  PaginatedResponse,
  NotificationSummary,
  NotificationDetail,
  NotificationPreferences,
  UpdateNotificationPreferencesRequest,
  VapidPublicKeyResponse,
  RegisterPushSubscriptionRequest,
  RegisterPushSubscriptionResponse,
  ListPushSubscriptionsResponse,
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

    // Web Push (VAPID) — browser subscription management (TASK-FE-083).
    getVapidPublicKey: () =>
      client.get<VapidPublicKeyResponse>(
        '/api/notifications/vapid-public-key',
      ),

    registerPushSubscription: (data: RegisterPushSubscriptionRequest) =>
      client.post<RegisterPushSubscriptionResponse>(
        '/api/notifications/me/push-subscriptions',
        data,
      ),

    // List the user's registered push devices/browsers (TASK-FE-085).
    listPushSubscriptions: () =>
      client.get<ListPushSubscriptionsResponse>(
        '/api/notifications/me/push-subscriptions',
      ),

    deletePushSubscription: (endpoint: string) =>
      client.delete<void>(
        '/api/notifications/me/push-subscriptions',
        { data: { endpoint } },
      ),
  };
}
