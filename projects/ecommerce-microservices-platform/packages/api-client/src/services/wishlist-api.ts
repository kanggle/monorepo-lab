import type { ApiClient } from '../client';
import type {
  AddWishlistRequest,
  AddWishlistResponse,
  WishlistItem,
  WishlistCheckResponse,
  PaginatedResponse,
} from '@repo/types';

export function createWishlistApi(client: ApiClient) {
  return {
    addToWishlist: (data: AddWishlistRequest) =>
      client.post<AddWishlistResponse>('/api/wishlists', data),

    removeFromWishlist: (wishlistItemId: string) =>
      client.delete<void>(`/api/wishlists/${wishlistItemId}`),

    getMyWishlist: (params?: { page?: number; size?: number }) =>
      client.get<PaginatedResponse<WishlistItem>>('/api/wishlists/me', {
        params,
      }),

    checkWishlist: (productId: string) =>
      client.get<WishlistCheckResponse>('/api/wishlists/me/check', {
        params: { productId },
      }),
  };
}
