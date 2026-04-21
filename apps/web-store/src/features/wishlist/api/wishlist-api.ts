import { apiClient } from '@/shared/config/api';
import { createWishlistApi } from '@repo/api-client';
import type {
  AddWishlistResponse,
  WishlistItem,
  WishlistCheckResponse,
  PaginatedResponse,
} from '@repo/types';

const wishlistApi = createWishlistApi(apiClient);

export async function addToWishlist(productId: string): Promise<AddWishlistResponse> {
  return wishlistApi.addToWishlist({ productId });
}

export async function removeFromWishlist(wishlistItemId: string): Promise<void> {
  return wishlistApi.removeFromWishlist(wishlistItemId);
}

export async function getMyWishlist(
  page = 0,
  size = 20,
): Promise<PaginatedResponse<WishlistItem>> {
  return wishlistApi.getMyWishlist({ page, size });
}

export async function checkWishlist(productId: string): Promise<WishlistCheckResponse> {
  return wishlistApi.checkWishlist(productId);
}
