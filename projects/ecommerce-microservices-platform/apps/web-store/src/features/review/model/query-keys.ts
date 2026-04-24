export const reviewKeys = {
  all: ['reviews'] as const,
  lists: () => [...reviewKeys.all, 'list'] as const,
  list: (params: Record<string, unknown>) => [...reviewKeys.lists(), params] as const,
  summaries: () => [...reviewKeys.all, 'summary'] as const,
  summary: (productId: string) => [...reviewKeys.summaries(), productId] as const,
  myReviews: () => [...reviewKeys.all, 'my'] as const,
  myReviewList: (params: Record<string, unknown>) => [...reviewKeys.myReviews(), params] as const,
};
