import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getMyNotifications } from '../api/notification-api';
import { notificationKeys } from './query-keys';

export function useNotifications(page: number, size: number) {
  return useQuery({
    queryKey: notificationKeys.list({ page, size }),
    queryFn: () => getMyNotifications(page, size),
    placeholderData: keepPreviousData,
  });
}
