import type { SearchSortOrder } from '@repo/types';

export interface SearchFilters {
  q: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  sort?: SearchSortOrder;
  page?: number;
  size?: number;
}
