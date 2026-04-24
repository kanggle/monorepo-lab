// Wishlist domain types based on specs/contracts/http/wishlist-api.md

export interface AddWishlistRequest {
  productId: string;
}

export interface AddWishlistResponse {
  wishlistItemId: string;
  productId: string;
}

export interface WishlistItem {
  wishlistItemId: string;
  productId: string;
  productName: string | null;
  productPrice: number;
  productStatus: string;
  addedAt: string;
}

export interface WishlistCheckResponse {
  productId: string;
  inWishlist: boolean;
  wishlistItemId: string | null;
}
