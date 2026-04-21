import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useChangeOrderStatus } from '@/features/order-management/hooks/use-change-order-status';

const mockChangeOrderStatus = vi.fn().mockResolvedValue(undefined);

vi.mock('@/features/order-management/api/order-api', () => ({
  changeOrderStatus: (...args: unknown[]) => mockChangeOrderStatus(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useChangeOrderStatus', () => {
  beforeEach(() => {
    mockChangeOrderStatus.mockClear();
  });

  it('주문 상태 변경 mutation을 실행한다', async () => {
    const { result } = renderHook(() => useChangeOrderStatus('order-1'), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync('CONFIRMED');

    expect(mockChangeOrderStatus).toHaveBeenCalledWith('order-1', 'CONFIRMED');
  });

  it('다른 상태로 변경할 수 있다', async () => {
    const { result } = renderHook(() => useChangeOrderStatus('order-2'), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync('SHIPPED');

    expect(mockChangeOrderStatus).toHaveBeenCalledWith('order-2', 'SHIPPED');
  });

  it('상태 변경 실패 시 isError가 true가 된다', async () => {
    mockChangeOrderStatus.mockRejectedValueOnce(new Error('변경 실패'));

    const { result } = renderHook(() => useChangeOrderStatus('order-1'), {
      wrapper: createWrapper(),
    });

    result.current.mutate('CANCELLED');

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });
  });
});
