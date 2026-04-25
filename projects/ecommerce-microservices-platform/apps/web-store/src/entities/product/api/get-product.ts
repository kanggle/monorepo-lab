import { apiClient } from '@/shared/config/api';
import { createProductApi } from '@repo/api-client';
import type { ProductDetail, ProductImageSummary } from '@repo/types';
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
  if (!product.images?.length) {
    const urls = product.thumbnailUrl ? [product.thumbnailUrl] : fallbackImages(product.name);
    product.images = urls.map((url, i): ProductImageSummary => ({
      imageId: `fallback-${i}`,
      url,
      sortOrder: i,
      isPrimary: i === 0,
    }));
  }
  return product;
}
