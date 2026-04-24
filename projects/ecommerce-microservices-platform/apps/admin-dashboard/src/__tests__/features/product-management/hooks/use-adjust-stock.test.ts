import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useAdjustStock } from '@/features/product-management/hooks/use-adjust-stock';

const mockAdjustStock = vi.fn().mockResolvedValue({ variantId: 'v1', stock: 15 });

vi.mock('@/features/product-management/api/product-api', () => ({
  adjustStock: (...args: unknown[]) => mockAdjustStock(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useAdjustStock', () => {
  beforeEach(() => {
    mockAdjustStock.mockClear();
  });

  it('재고 조정 mutation을 실행한다', async () => {
    const { result } = renderHook(() => useAdjustStock(), {
      wrapper: createWrapper(),
    });

    const params = {
      productId: 'p1',
      data: { variantId: 'v1', quantity: 5, reason: '입고' },
    };

    await result.current.mutateAsync(params);

    expect(mockAdjustStock).toHaveBeenCalledWith('p1', params.data);
  });

  it('재고 조정 실패 시 alert로 에러 메시지를 표시한다', async () => {
    const alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
    mockAdjustStock.mockRejectedValueOnce({
      code: 'INSUFFICIENT_STOCK',
      message: '재고가 부족합니다.',
      timestamp: '2026-03-25T00:00:00Z',
    });

    const { result } = renderHook(() => useAdjustStock(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      productId: 'p1',
      data: { variantId: 'v1', quantity: -999, reason: '출고' },
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('재고가 부족합니다.');
    alertSpy.mockRestore();
  });
});
