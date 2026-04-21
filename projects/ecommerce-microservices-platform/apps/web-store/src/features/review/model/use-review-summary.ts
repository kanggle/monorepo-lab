import { useQuery } from '@tanstack/react-query';
import { getProductReviewSummary } from '../api/review-api';
import { reviewKeys } from './query-keys';

export function useReviewSummary(productId: string) {
  return useQuery({
    queryKey: reviewKeys.summary(productId),
    queryFn: () => getProductReviewSummary(productId),
  });
}
