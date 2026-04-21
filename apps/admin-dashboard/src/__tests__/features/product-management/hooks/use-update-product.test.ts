import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useUpdateProduct } from '@/features/product-management/hooks/use-update-product';

const mockUpdateProduct = vi.fn().mockResolvedValue({ id: 'p1' });

vi.mock('@/features/product-management/api/product-api', () => ({
  updateProduct: (...args: unknown[]) => mockUpdateProduct(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useUpdateProduct', () => {
  beforeEach(() => {
    mockUpdateProduct.mockClear();
  });

  it('상품 수정 mutation을 실행한다', async () => {
    const { result } = renderHook(() => useUpdateProduct(), {
      wrapper: createWrapper(),
    });

    const params = {
      productId: 'p1',
      data: { name: '수정된 상품', description: '수정 설명', price: 8000, status: 'ON_SALE' as const },
    };

    await result.current.mutateAsync(params);

    expect(mockUpdateProduct).toHaveBeenCalledWith('p1', params.data);
  });

  it('수정 실패 시 alert로 에러 메시지를 표시한다', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    mockUpdateProduct.mockRejectedValueOnce({
      code: 'PRODUCT_NOT_FOUND',
      message: '상품을 찾을 수 없습니다.',
      timestamp: '2026-03-25T00:00:00Z',
    });

    const { result } = renderHook(() => useUpdateProduct(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      productId: 'p1',
      data: { name: '수정된 상품', description: '설명', price: 8000, status: 'ON_SALE' as const },
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('상품을 찾을 수 없습니다.');
    alertSpy.mockRestore();
  });
});
