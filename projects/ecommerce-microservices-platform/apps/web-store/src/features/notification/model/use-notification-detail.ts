import { useQuery } from '@tanstack/react-query';
import { mapQueryError } from '@/shared/lib/map-query-error';
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

  const error = mapQueryError(query.error, {
    notFoundCode: 'NOTIFICATION_NOT_FOUND',
    notFoundMessage: '알림을 찾을 수 없습니다.',
    fallbackMessage: '알림 정보를 불러오는데 실패했습니다.',
  });

  return {
    notification: query.data ?? null,
    isLoading: query.isLoading,
    error,
    retryLoad: () => query.refetch(),
  };
}
