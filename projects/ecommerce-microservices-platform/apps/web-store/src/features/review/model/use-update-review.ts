import { useMutation, useQueryClient } from '@tanstack/react-query';
import { updateReview } from '../api/review-api';
import { reviewKeys } from './query-keys';
import type { UpdateReviewRequest } from '@repo/types';

export function useUpdateReview() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ reviewId, data }: { reviewId: string; data: UpdateReviewRequest }) =>
      updateReview(reviewId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reviewKeys.all });
    },
  });
}
