// Search domain types based on specs/contracts/http/search-api.md

export type SearchSortOrder = 'relevance' | 'price_asc' | 'price_desc' | 'newest';

export interface SearchProductItem {
  productId: string;
  name: string;
  price: number;
  status: string;
  thumbnailUrl: string;
  categoryId: string;
  score: number;
}

export interface CategoryFacet {
  id: string;
  /** Backend의 search index에는 카테고리 이름이 없어 null로 내려올 수 있음. UI는 null을 fallback 처리할 것. */
  name: string | null;
  count: number;
}

export interface PriceRangeFacet {
  /** null = 해당 버킷의 하한 없음 (예: "10,000원 이하" 버킷의 min) */
  min: number | null;
  /** null = 해당 버킷의 상한 없음 (예: "100,000원 이상" 버킷의 max) */
  max: number | null;
  count: number;
}

export interface SearchFacets {
  categories: CategoryFacet[];
  priceRanges: PriceRangeFacet[];
}

export interface SearchRequest {
  q: string;
  categoryId?: string;
  minPrice?: number;
  maxPrice?: number;
  status?: string;
  sort?: SearchSortOrder;
  page?: number;
  size?: number;
}

export interface SearchResponse {
  query: string;
  content: SearchProductItem[];
  facets: SearchFacets;
  page: number;
  size: number;
  totalElements: number;
}
