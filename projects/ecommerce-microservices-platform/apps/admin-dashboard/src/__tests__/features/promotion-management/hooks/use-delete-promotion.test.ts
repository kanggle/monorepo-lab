import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useDeletePromotion } from '@/features/promotion-management/hooks/use-delete-promotion';

const mockDeletePromotion = vi.fn().mockResolvedValue(undefined);

vi.mock('@/features/promotion-management/api/promotion-api', () => ({
  deletePromotion: (...args: unknown[]) => mockDeletePromotion(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useDeletePromotion', () => {
  let alertSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockDeletePromotion.mockClear();
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  afterEach(() => {
    alertSpy.mockRestore();
  });

  it('프로모션 삭제 mutation을 호출한다', async () => {
    const { result } = renderHook(() => useDeletePromotion(), {
      wrapper: createWrapper(),
    });

    await result.current.mutateAsync('p1');

    expect(mockDeletePromotion).toHaveBeenCalledWith('p1', expect.anything());
  });

  it('실패 시 기본 메시지로 alert를 표시한다', async () => {
    mockDeletePromotion.mockRejectedValueOnce(null);

    const { result } = renderHook(() => useDeletePromotion(), {
      wrapper: createWrapper(),
    });

    result.current.mutate('p1');

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('프로모션 삭제에 실패했습니다.');
  });
});
