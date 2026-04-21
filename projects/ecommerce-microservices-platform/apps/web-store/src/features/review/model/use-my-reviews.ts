import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getMyReviews } from '../api/review-api';
import { reviewKeys } from './query-keys';

export function useMyReviews(page: number, size: number) {
  return useQuery({
    queryKey: reviewKeys.myReviewList({ page, size }),
    queryFn: () => getMyReviews({ page, size }),
    placeholderData: keepPreviousData,
  });
}
