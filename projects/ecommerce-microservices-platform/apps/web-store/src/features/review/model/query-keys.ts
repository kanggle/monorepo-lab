import { createListQueryKeys } from '@/shared/lib/query-keys';

const reviewBase = createListQueryKeys('reviews');

export const reviewKeys = {
  ...reviewBase,
  summaries: () => [...reviewBase.all, 'summary'] as const,
  summary: (productId: string) => [...reviewKeys.summaries(), productId] as const,
  myReviews: () => [...reviewBase.all, 'my'] as const,
  myReviewList: (params: Record<string, unknown>) => [...reviewKeys.myReviews(), params] as const,
};
