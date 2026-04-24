import { useQuery } from '@tanstack/react-query';
import { checkWishlist } from '../api/wishlist-api';
import { wishlistKeys } from './query-keys';

export function useWishlistCheck(productId: string, enabled: boolean) {
  return useQuery({
    queryKey: wishlistKeys.check(productId),
    queryFn: () => checkWishlist(productId),
    enabled,
    staleTime: 30_000,
  });
}
