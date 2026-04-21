import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useUpdatePromotion } from '@/features/promotion-management/hooks/use-update-promotion';

const mockUpdatePromotion = vi.fn().mockResolvedValue({ promotionId: 'p1' });

vi.mock('@/features/promotion-management/api/promotion-api', () => ({
  updatePromotion: (...args: unknown[]) => mockUpdatePromotion(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useUpdatePromotion', () => {
  let alertSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockUpdatePromotion.mockClear();
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  afterEach(() => {
    alertSpy.mockRestore();
  });

  it('프로모션 수정 mutation을 promotionId와 데이터로 호출한다', async () => {
    const { result } = renderHook(() => useUpdatePromotion(), {
      wrapper: createWrapper(),
    });

    const data = {
      name: '수정된 세일',
      description: '설명',
      discountType: 'FIXED' as const,
      discountValue: 7000,
      maxDiscountAmount: 15000,
      maxIssuanceCount: 500,
      startDate: '2026-06-01T00:00:00.000Z',
      endDate: '2026-06-30T23:59:59.999Z',
    };

    await result.current.mutateAsync({ promotionId: 'p1', data });

    expect(mockUpdatePromotion).toHaveBeenCalledWith('p1', data);
  });

  it('실패 시 기본 메시지로 alert를 표시한다', async () => {
    mockUpdatePromotion.mockRejectedValueOnce(null);

    const { result } = renderHook(() => useUpdatePromotion(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      promotionId: 'p1',
      data: {
        name: '세일',
        description: '',
        discountType: 'FIXED',
        discountValue: 1000,
        maxDiscountAmount: 0,
        maxIssuanceCount: 100,
        startDate: '2026-06-01T00:00:00.000Z',
        endDate: '2026-06-30T23:59:59.999Z',
      },
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('프로모션 수정에 실패했습니다.');
  });
});
