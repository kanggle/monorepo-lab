import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useCreateProduct } from '@/features/product-management/hooks/use-create-product';

const mockCreateProduct = vi.fn().mockResolvedValue({ id: 'new-1' });

vi.mock('@/features/product-management/api/product-api', () => ({
  createProduct: (...args: unknown[]) => mockCreateProduct(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useCreateProduct', () => {
  beforeEach(() => {
    mockCreateProduct.mockClear();
  });

  it('상품 생성 mutation을 실행한다', async () => {
    const { result } = renderHook(() => useCreateProduct(), {
      wrapper: createWrapper(),
    });

    const data = {
      name: '새 상품',
      description: '설명',
      price: 5000,
      categoryId: 'cat1',
      variants: [{ optionName: '기본', stock: 10, additionalPrice: 0 }],
    };

    await result.current.mutateAsync(data);

    expect(mockCreateProduct).toHaveBeenCalledWith(data, expect.anything());
  });

  it('생성 실패 시 alert로 에러 메시지를 표시한다', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    mockCreateProduct.mockRejectedValueOnce({
      code: 'VALIDATION_ERROR',
      message: '입력값을 확인해주세요.',
      timestamp: '2026-03-25T00:00:00Z',
    });

    const { result } = renderHook(() => useCreateProduct(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      name: '',
      description: '',
      price: -1,
      categoryId: 'cat1',
      variants: [],
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('입력값을 확인해주세요.');
    alertSpy.mockRestore();
  });
});
