import { apiClient } from '@/shared/config/api';
import { createProductApi } from '@repo/api-client';
import type { PaginatedResponse, ProductSummary, ProductListParams } from '@repo/types';
import { fallbackThumbnail } from './fallback-images';

const productApi = createProductApi(apiClient);

/**
 * Fetch a paginated product list from product-service.
 *
 * On failure the error is propagated to the caller. Do NOT reintroduce a
 * silent mock fallback here — mock ids (e.g. `"mock-1"`) are not valid UUIDs
 * and would crash downstream write paths like WishlistButton / cart / order.
 * See TASK-FE-061.
 */
export async function getProducts(
  params?: ProductListParams,
): Promise<PaginatedResponse<ProductSummary>> {
  const result = await productApi.getProducts(params);
  result.content = result.content.map((p) => ({
    ...p,
    thumbnailUrl: p.thumbnailUrl || fallbackThumbnail(p.name),
  }));
  return result;
}
