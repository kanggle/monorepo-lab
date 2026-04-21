import { useMutation, useQueryClient } from '@tanstack/react-query';
import type { UpdateUserProfileRequest } from '@repo/types';
import { updateMyProfile } from '../api/user-profile-api';
import { userKeys } from '@/entities/user';

export function useUpdateProfile() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (data: UpdateUserProfileRequest) => updateMyProfile(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: userKeys.profile() });
    },
  });
}
