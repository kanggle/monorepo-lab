import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RevenueTrendChart } from '@/widgets/dashboard/RevenueTrendChart';
import { getOrders } from '@/features/order-management/api/order-api';

vi.mock('@/features/order-management/api/order-api', () => ({
  getOrders: vi.fn(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

const mockedGetOrders = vi.mocked(getOrders);

describe('RevenueTrendChart', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-04-13T05:00:00Z'));
    mockedGetOrders.mockReset();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('로딩 중 스켈레톤을 표시한다', () => {
    mockedGetOrders.mockImplementation(() => new Promise(() => {}));
    render(<RevenueTrendChart />, { wrapper: createWrapper() });
    expect(screen.getByLabelText('매출 추이 로딩 중')).toBeInTheDocument();
  });

  it('매출이 0이면 빈 상태 메시지를 표시한다', async () => {
    mockedGetOrders.mockResolvedValue({
      content: [],
      page: 0,
      size: 200,
      totalElements: 0,
    });
    render(<RevenueTrendChart />, { wrapper: createWrapper() });
    expect(await screen.findByText('최근 7일 매출 데이터가 없습니다.')).toBeInTheDocument();
  });

  it('매출이 있으면 SVG 차트를 렌더링한다', async () => {
    mockedGetOrders.mockResolvedValue({
      content: [
        {
          orderId: 'o1',
          userId: 'u1',
          status: 'PENDING' as const,
          totalPrice: 10000,
          itemCount: 1,
          firstItemName: 'A',
          createdAt: '2026-04-13T04:00:00Z',
        },
      ],
      page: 0,
      size: 200,
      totalElements: 1,
    });
    const { container } = render(<RevenueTrendChart />, { wrapper: createWrapper() });
    await waitFor(() => {
      expect(container.querySelector('svg path')).not.toBeNull();
    });
  });

  it('totalElements가 PAGE_SIZE를 초과하면 경고를 표시한다', async () => {
    mockedGetOrders.mockResolvedValue({
      content: [
        {
          orderId: 'o1',
          userId: 'u1',
          status: 'PENDING' as const,
          totalPrice: 10000,
          itemCount: 1,
          firstItemName: 'A',
          createdAt: '2026-04-13T04:00:00Z',
        },
      ],
      page: 0,
      size: 200,
      totalElements: 500,
    });
    render(<RevenueTrendChart />, { wrapper: createWrapper() });
    expect(await screen.findByText(/최근 200건 주문 기준 집계/)).toBeInTheDocument();
  });

  it('에러 발생 시 ListError를 표시한다', async () => {
    mockedGetOrders.mockRejectedValue(new Error('fail'));
    render(<RevenueTrendChart />, { wrapper: createWrapper() });
    expect(await screen.findByText('매출 추이를 불러오지 못했습니다.')).toBeInTheDocument();
  });
});
