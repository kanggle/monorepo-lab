import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { usePromotion } from '@/features/promotion-management/hooks/use-promotion';

vi.mock('@/features/promotion-management/api/promotion-api', () => ({
  getPromotion: vi.fn().mockResolvedValue({
    promotionId: 'p1',
    name: '여름 세일',
    description: '여름 할인',
    discountType: 'FIXED',
    discountValue: 5000,
    maxDiscountAmount: 10000,
    maxIssuanceCount: 1000,
    issuedCount: 500,
    startDate: '2026-06-01T00:00:00Z',
    endDate: '2026-06-30T00:00:00Z',
    status: 'ACTIVE',
    createdAt: '2026-05-01T00:00:00Z',
    updatedAt: '2026-05-15T00:00:00Z',
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('usePromotion', () => {
  it('프로모션 상세를 조회한다', async () => {
    const { result } = renderHook(() => usePromotion('p1'), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.name).toBe('여름 세일');
    expect(result.current.data?.promotionId).toBe('p1');
  });

  it('promotionId가 빈 문자열이면 쿼리를 실행하지 않는다', () => {
    const { result } = renderHook(() => usePromotion(''), {
      wrapper: createWrapper(),
    });

    expect(result.current.fetchStatus).toBe('idle');
  });
});
