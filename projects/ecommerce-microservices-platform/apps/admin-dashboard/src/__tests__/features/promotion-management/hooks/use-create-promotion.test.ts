import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { useCreatePromotion } from '@/features/promotion-management/hooks/use-create-promotion';

const mockCreatePromotion = vi.fn().mockResolvedValue({ promotionId: 'new-1' });

vi.mock('@/features/promotion-management/api/promotion-api', () => ({
  createPromotion: (...args: unknown[]) => mockCreatePromotion(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('useCreatePromotion', () => {
  let alertSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    mockCreatePromotion.mockClear();
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {});
  });

  afterEach(() => {
    alertSpy.mockRestore();
  });

  it('프로모션 생성 mutation을 호출한다', async () => {
    const { result } = renderHook(() => useCreatePromotion(), {
      wrapper: createWrapper(),
    });

    const data = {
      name: '여름 세일',
      description: '여름 할인',
      discountType: 'FIXED' as const,
      discountValue: 5000,
      maxDiscountAmount: 10000,
      maxIssuanceCount: 1000,
      startDate: '2026-06-01T00:00:00.000Z',
      endDate: '2026-06-30T23:59:59.999Z',
    };

    await result.current.mutateAsync(data);

    expect(mockCreatePromotion).toHaveBeenCalledWith(data, expect.anything());
  });

  it('실패 시 기본 메시지로 alert를 표시한다', async () => {
    mockCreatePromotion.mockRejectedValueOnce(null);

    const { result } = renderHook(() => useCreatePromotion(), {
      wrapper: createWrapper(),
    });

    result.current.mutate({
      name: '세일',
      description: '',
      discountType: 'FIXED',
      discountValue: 1000,
      maxDiscountAmount: 0,
      maxIssuanceCount: 100,
      startDate: '2026-06-01T00:00:00.000Z',
      endDate: '2026-06-30T23:59:59.999Z',
    });

    await waitFor(() => {
      expect(result.current.isError).toBe(true);
    });

    expect(alertSpy).toHaveBeenCalledWith('프로모션 생성에 실패했습니다.');
  });
});
