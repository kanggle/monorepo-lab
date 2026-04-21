import { useMutation, useQueryClient } from '@tanstack/react-query';
import { deleteReview } from '../api/review-api';
import { reviewKeys } from './query-keys';

export function useDeleteReview() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (reviewId: string) => deleteReview(reviewId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: reviewKeys.all });
    },
  });
}
