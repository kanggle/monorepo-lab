import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getMyWishlist } from '../api/wishlist-api';
import { wishlistKeys } from './query-keys';

export function useWishlist(page: number, size: number) {
  return useQuery({
    queryKey: wishlistKeys.list({ page, size }),
    queryFn: () => getMyWishlist(page, size),
    placeholderData: keepPreviousData,
  });
}
