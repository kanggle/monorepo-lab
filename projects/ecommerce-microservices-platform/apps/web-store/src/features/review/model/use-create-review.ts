import { useMutation, useQueryClient } from '@tanstack/react-query';
import { createReview } from '../api/review-api';
import { reviewKeys } from './query-keys';

export function useCreateReview() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: createReview,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reviewKeys.all });
    },
  });
}
