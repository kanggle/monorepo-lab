import { describe, it, expect, vi, beforeEach } from 'vitest';

const mockGetProduct = vi.hoisted(() => vi.fn());

vi.mock('@/shared/config/api', () => ({
  apiClient: {},
}));

vi.mock('@repo/api-client', () => ({
  createProductApi: vi.fn(() => ({
    getProduct: mockGetProduct,
  })),
}));

vi.mock('@/entities/product/api/fallback-images', () => ({
  fallbackImages: vi.fn((name: string) => [`fallback-${name}-1.jpg`, `fallback-${name}-2.jpg`]),
}));

import { getProduct } from '@/entities/product/api/get-product';

describe('getProduct', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('API에서 상품 상세 정보를 정상적으로 조회한다', async () => {
    const product = {
      id: '11111111-1111-1111-1111-111111111111',
      name: '테스트 상품',
      description: '설명',
      status: 'ON_SALE',
      price: 29000,
      categoryId: 'cat-1',
      images: ['real-img.jpg'],
      variants: [],
    };
    mockGetProduct.mockResolvedValueOnce(product);

    const result = await getProduct('11111111-1111-1111-1111-111111111111');

    expect(mockGetProduct).toHaveBeenCalledWith('11111111-1111-1111-1111-111111111111');
    expect(result).toEqual(product);
    expect(result?.id).not.toMatch(/^mock-/);
  });

  it('API 응답에 이미지가 없으면 폴백 이미지를 설정한다', async () => {
    const product = {
      id: '22222222-2222-2222-2222-222222222222',
      name: '이미지없는상품',
      description: '설명',
      status: 'ON_SALE',
      price: 15000,
      categoryId: 'cat-1',
      images: [],
      variants: [],
    };
    mockGetProduct.mockResolvedValueOnce(product);

    const result = await getProduct('22222222-2222-2222-2222-222222222222');

    expect(result).not.toBeNull();
    expect(result!.images).toEqual([
      { imageId: 'fallback-0', url: 'fallback-이미지없는상품-1.jpg', sortOrder: 0, isPrimary: true },
      { imageId: 'fallback-1', url: 'fallback-이미지없는상품-2.jpg', sortOrder: 1, isPrimary: false },
    ]);
  });

  it('API 응답이 null이면 null을 반환한다', async () => {
    mockGetProduct.mockResolvedValueOnce(null);

    const result = await getProduct('nonexistent');

    expect(result).toBeNull();
  });

  it('API 에러 시 목 데이터로 폴백하지 않고 에러를 그대로 전파한다', async () => {
    const error = new Error('Network error');
    mockGetProduct.mockRejectedValueOnce(error);

    await expect(getProduct('mock-1')).rejects.toThrow('Network error');
  });

  it('API 에러 시 다른 ID에 대해서도 폴백 없이 에러가 전파된다', async () => {
    mockGetProduct.mockRejectedValueOnce(new Error('Server 500'));

    await expect(getProduct('unknown-id')).rejects.toThrow('Server 500');
  });
});
