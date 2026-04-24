import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useProduct } from '@/features/product-management/hooks/use-product';

vi.mock('@/features/product-management/api/product-api', () => ({
  getProduct: vi.fn().mockResolvedValue({
    id: '1',
    name: '상품 A',
    description: '설명',
    price: 10000,
    status: 'ON_SALE',
    categoryId: 'cat1',
    variants: [
      { id: 'v1', optionName: '기본', stock: 100, additionalPrice: 0 },
    ],
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useProduct', () => {
  it('상품 상세 정보를 조회한다', async () => {
    const { result } = renderHook(() => useProduct('1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.name).toBe('상품 A');
    expect(result.current.data?.variants).toHaveLength(1);
  });

  it('productId가 빈 문자열이면 쿼리를 비활성화한다', () => {
    const { result } = renderHook(() => useProduct(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.isFetching).toBe(false);
  });
});
