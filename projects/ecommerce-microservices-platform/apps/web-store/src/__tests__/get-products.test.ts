import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGetProducts = vi.hoisted(() => vi.fn());

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

vi.mock('@repo/api-client', () => ({
  createProductApi: vi.fn(() => ({
    getProducts: mockGetProducts,
  })),
}));

vi.mock('@/entities/product/api/fallback-images', () => ({
  fallbackThumbnail: vi.fn((name: string) => `fallback-thumb-${name}.jpg`),
}));

import { getProducts } from '@/entities/product/api/get-products';

describe('getProducts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('API에서 상품 목록을 정상적으로 조회한다', async () => {
    const apiResponse = {
      content: [
        { id: 'p1', name: '상품1', status: 'ON_SALE', price: 20000, thumbnailUrl: 'thumb.jpg', categoryId: 'cat-1' },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
    };
    mockGetProducts.mockResolvedValueOnce(apiResponse);

    const result = await getProducts({ page: 0, size: 10 });

    expect(mockGetProducts).toHaveBeenCalledWith({ page: 0, size: 10 });
    expect(result.content).toHaveLength(1);
    expect(result.content[0].thumbnailUrl).toBe('thumb.jpg');
  });

  it('썸네일이 없는 상품에 폴백 썸네일을 적용한다', async () => {
    const apiResponse = {
      content: [
        { id: 'p1', name: '썸네일없는상품', status: 'ON_SALE', price: 15000, thumbnailUrl: '', categoryId: 'cat-1' },
      ],
      page: 0,
      size: 10,
      totalElements: 1,
    };
    mockGetProducts.mockResolvedValueOnce(apiResponse);

    const result = await getProducts();

    expect(result.content[0].thumbnailUrl).toBe('fallback-thumb-썸네일없는상품.jpg');
  });

  it('파라미터 없이 호출하면 기본값으로 API를 호출한다', async () => {
    const apiResponse = { content: [], page: 0, size: 10, totalElements: 0 };
    mockGetProducts.mockResolvedValueOnce(apiResponse);

    await getProducts();

    expect(mockGetProducts).toHaveBeenCalledWith(undefined);
  });

  it('정상 응답에 포함된 상품 id는 그대로 보존되며 mock-* 형태로 대체되지 않는다', async () => {
    const apiResponse = {
      content: [
        { id: '11111111-1111-1111-1111-111111111111', name: '상품A', status: 'ON_SALE', price: 10000, thumbnailUrl: 'a.jpg', categoryId: 'cat-1' },
        { id: '22222222-2222-2222-2222-222222222222', name: '상품B', status: 'ON_SALE', price: 20000, thumbnailUrl: 'b.jpg', categoryId: 'cat-1' },
      ],
      page: 0,
      size: 10,
      totalElements: 2,
    };
    mockGetProducts.mockResolvedValueOnce(apiResponse);

    const result = await getProducts();

    for (const item of result.content) {
      expect(item.id).not.toMatch(/^mock-/);
    }
    expect(result.content.map((p) => p.id)).toEqual([
      '11111111-1111-1111-1111-111111111111',
      '22222222-2222-2222-2222-222222222222',
    ]);
  });

  it('API 에러 시 목 데이터로 폴백하지 않고 에러를 그대로 전파한다', async () => {
    const error = new Error('Server error');
    mockGetProducts.mockRejectedValueOnce(error);

    await expect(getProducts({ page: 0, size: 2 })).rejects.toThrow('Server error');
  });

  it('API 에러 시 파라미터 없이 호출해도 에러가 전파된다', async () => {
    mockGetProducts.mockRejectedValueOnce(new Error('Network error'));

    await expect(getProducts()).rejects.toThrow('Network error');
  });
});
