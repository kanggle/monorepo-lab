import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockSearchProducts = vi.hoisted(() => vi.fn());

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

vi.mock('@repo/api-client', () => ({
  createSearchApi: vi.fn(() => ({
    searchProducts: mockSearchProducts,
  })),
}));

import { searchProducts } from '@/features/search/api/search-products';
import type { SearchRequest, SearchResponse } from '@repo/types';

const EMPTY_FACETS: SearchResponse['facets'] = { categories: [], priceRanges: [] };

describe('searchProducts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('검색 파라미터를 전달하여 상품을 검색한다', async () => {
    const request: SearchRequest = {
      q: '티셔츠',
      page: 0,
      size: 10,
    };
    const response: SearchResponse = {
      query: '티셔츠',
      content: [
        {
          productId: 'p1',
          name: '화이트 티셔츠',
          price: 29000,
          status: 'ACTIVE',
          thumbnailUrl: 'thumb.jpg',
          categoryId: 'cat-1',
          score: 1.0,
        },
      ],
      facets: EMPTY_FACETS,
      page: 0,
      size: 10,
      totalElements: 1,
    };
    mockSearchProducts.mockResolvedValueOnce(response);

    const result = await searchProducts(request);

    expect(mockSearchProducts).toHaveBeenCalledWith(request);
    expect(result).toEqual(response);
  });

  it('빈 검색 결과를 정상적으로 반환한다', async () => {
    const request: SearchRequest = {
      q: '존재하지않는상품',
      page: 0,
      size: 10,
    };
    const response: SearchResponse = {
      query: '존재하지않는상품',
      content: [],
      facets: EMPTY_FACETS,
      page: 0,
      size: 10,
      totalElements: 0,
    };
    mockSearchProducts.mockResolvedValueOnce(response);

    const result = await searchProducts(request);

    expect(result.content).toHaveLength(0);
    expect(result.totalElements).toBe(0);
  });

  it('API 에러를 그대로 전파한다', async () => {
    const request: SearchRequest = {
      q: '에러 테스트',
      page: 0,
      size: 10,
    };
    const error = new Error('Search service unavailable');
    mockSearchProducts.mockRejectedValueOnce(error);

    await expect(searchProducts(request)).rejects.toThrow('Search service unavailable');
  });

  it('필터 조건이 포함된 검색 요청을 전달한다', async () => {
    const request: SearchRequest = {
      q: '청바지',
      page: 0,
      size: 20,
      categoryId: 'cat-clothing',
      minPrice: 30000,
      maxPrice: 80000,
    };
    const response: SearchResponse = {
      query: '청바지',
      content: [],
      facets: EMPTY_FACETS,
      page: 0,
      size: 20,
      totalElements: 0,
    };
    mockSearchProducts.mockResolvedValueOnce(response);

    await searchProducts(request);

    expect(mockSearchProducts).toHaveBeenCalledWith(request);
  });
});
