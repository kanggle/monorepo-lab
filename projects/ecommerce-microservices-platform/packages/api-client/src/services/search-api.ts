import type { ApiClient } from '../client';
import type { SearchRequest, SearchResponse } from '@repo/types';

export function createSearchApi(client: ApiClient) {
  return {
    searchProducts: (params: SearchRequest) =>
      client.get<SearchResponse>('/api/search/products', { params }),
  };
}
