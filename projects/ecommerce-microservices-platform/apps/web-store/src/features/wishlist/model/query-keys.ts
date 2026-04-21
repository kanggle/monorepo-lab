export const wishlistKeys = {
  all: ['wishlists'] as const,
  lists: () => [...wishlistKeys.all, 'list'] as const,
  list: (params: Record<string, unknown>) => [...wishlistKeys.lists(), params] as const,
  checks: () => [...wishlistKeys.all, 'check'] as const,
  check: (productId: string) => [...wishlistKeys.checks(), productId] as const,
};
