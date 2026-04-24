import { useQuery } from '@tanstack/react-query';
import { getMyProfile } from '../api/user-profile-api';
import { userKeys } from '@/entities/user';

export function useProfile() {
  return useQuery({
    queryKey: userKeys.profile(),
    queryFn: getMyProfile,
  });
}
