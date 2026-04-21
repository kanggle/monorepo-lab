import { useQuery } from '@tanstack/react-query';
import { isApiError } from '@repo/types/guards';
import { getMyNotification } from '../api/notification-api';
import { notificationKeys } from './query-keys';

export interface UseNotificationDetailReturn {
  notification: import('@repo/types').NotificationDetail | null;
  isLoading: boolean;
  error: string;
  retryLoad: () => void;
}

export function useNotificationDetail(notificationId: string): UseNotificationDetailReturn {
  const query = useQuery({
    queryKey: notificationKeys.detail(notificationId),
    queryFn: () => getMyNotification(notificationId),
    enabled: !!notificationId,
  });

  const error = query.error
    ? isApiError(query.error) && query.error.code === 'NOTIFICATION_NOT_FOUND'
      ? '알림을 찾을 수 없습니다.'
      : '알림 정보를 불러오는데 실패했습니다.'
    : '';

  return {
    notification: query.data ?? null,
    isLoading: query.isLoading,
    error,
    retryLoad: () => query.refetch(),
  };
}
