import { apiClient } from '@/shared/config/api';
import { createSearchApi } from '@repo/api-client';
import type { SearchRequest, SearchResponse } from '@repo/types';

const searchApi = createSearchApi(apiClient);

export async function searchProducts(params: SearchRequest): Promise<SearchResponse> {
  return searchApi.searchProducts(params);
}
