import { useQuery } from '@tanstack/react-query';
import { getMyPreferences } from '../api/notification-api';
import { notificationKeys } from './query-keys';

export function useNotificationPreferences() {
  return useQuery({
    queryKey: notificationKeys.preferences(),
    queryFn: getMyPreferences,
  });
}
