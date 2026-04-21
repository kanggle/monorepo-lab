import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getProductReviews } from '../api/review-api';
import { reviewKeys } from './query-keys';

export function useProductReviews(productId: string, page: number, size: number) {
  return useQuery({
    queryKey: reviewKeys.list({ productId, page, size }),
    queryFn: () => getProductReviews(productId, { page, size }),
    placeholderData: keepPreviousData,
  });
}
