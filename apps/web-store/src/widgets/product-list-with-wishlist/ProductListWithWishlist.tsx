'use client';

import type { ProductSummary } from '@repo/types';
import { ProductList } from '@/features/product';
import { WishlistButton } from '@/features/wishlist';

interface ProductListWithWishlistProps {
  products: ProductSummary[];
}

export function ProductListWithWishlist({ products }: ProductListWithWishlistProps) {
  return (
    <ProductList
      products={products}
      renderAction={(product) => <WishlistButton productId={product.id} />}
    />
  );
}
