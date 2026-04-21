import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement } from 'react';
import { usePromotions } from '@/features/promotion-management/hooks/use-promotions';

const mockPush = vi.fn();
let mockSearchParams = new URLSearchParams();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
  useSearchParams: () => mockSearchParams,
}));

vi.mock('@/features/promotion-management/api/promotion-api', () => ({
  getPromotions: vi.fn().mockResolvedValue({
    content: [
      {
        promotionId: 'p1',
        name: '여름 세일',
        discountType: 'FIXED',
        discountValue: 5000,
        maxIssuanceCount: 1000,
        issuedCount: 500,
        startDate: '2026-06-01T00:00:00Z',
        endDate: '2026-06-30T00:00:00Z',
        status: 'ACTIVE',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) =>
    createElement(QueryClientProvider, { client: queryClient }, children);
}

describe('usePromotions', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockSearchParams = new URLSearchParams();
  });

  it('프로모션 목록을 조회한다', async () => {
    const { result } = renderHook(() => usePromotions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.data?.content).toHaveLength(1);
    expect(result.current.data?.content[0].name).toBe('여름 세일');
  });

  it('pagination 정보를 반환한다', async () => {
    const { result } = renderHook(() => usePromotions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(result.current.pagination.page).toBe(0);
    expect(result.current.pagination.totalPages).toBe(1);
  });

  it('setFilter로 URL 파라미터를 변경한다', async () => {
    const { result } = renderHook(() => usePromotions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    result.current.filters.setFilter('status', 'ACTIVE');
    expect(mockPush).toHaveBeenCalledWith('?status=ACTIVE&page=0');
  });

  it('status 필터를 API에 전달한다', async () => {
    const { getPromotions } = await import(
      '@/features/promotion-management/api/promotion-api'
    );

    mockSearchParams = new URLSearchParams('status=ACTIVE');

    const { result } = renderHook(() => usePromotions(), {
      wrapper: createWrapper(),
    });

    await waitFor(() => {
      expect(result.current.isSuccess).toBe(true);
    });

    expect(getPromotions).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'ACTIVE' }),
    );
  });
});
