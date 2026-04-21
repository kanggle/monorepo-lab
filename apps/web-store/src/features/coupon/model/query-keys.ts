export const couponKeys = {
  all: ['coupons'] as const,
  lists: () => [...couponKeys.all, 'list'] as const,
  list: (params: Record<string, unknown>) => [...couponKeys.lists(), params] as const,
};
