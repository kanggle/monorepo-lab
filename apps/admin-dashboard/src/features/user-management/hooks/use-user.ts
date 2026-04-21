import { useQuery } from '@tanstack/react-query';
import { getUser } from '../api/user-api';
import { userKeys } from './query-keys';

export function useUser(userId: string) {
  return useQuery({
    queryKey: userKeys.detail(userId),
    queryFn: () => getUser(userId),
    enabled: !!userId,
  });
}
