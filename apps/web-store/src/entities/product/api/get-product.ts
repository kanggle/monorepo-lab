import { apiClient } from '@/shared/config/api';
import { createProductApi } from '@repo/api-client';
import type { ProductDetail } from '@repo/types';
import { fallbackImages } from './fallback-images';

const productApi = createProductApi(apiClient);

/**
 * Fetch a single product detail from product-service.
 *
 * On failure the error is propagated to the caller. Do NOT reintroduce a
 * silent mock fallback here — mock ids (e.g. `"mock-1"`) are not valid UUIDs
 * and would crash downstream write paths like WishlistButton / cart / order.
 * See TASK-FE-061.
 */
export async function getProduct(id: string): Promise<ProductDetail | null> {
  const product = await productApi.getProduct(id);
  if (!product) return null;
  if (product.images?.length) {
    // API returns image objects {imageId, url, sortOrder, isPrimary} — extract URLs
    product.images = (product.images as any[]).map((img: any) =>
      typeof img === 'string' ? img : img.url
    );
  } else {
    product.images = product.thumbnailUrl
      ? [product.thumbnailUrl]
      : fallbackImages(product.name);
  }
  return product;
}
