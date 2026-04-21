import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TodayOrdersKpi } from '@/widgets/dashboard/TodayOrdersKpi';
import { getOrders } from '@/features/order-management/api/order-api';

vi.mock('@/features/order-management/api/order-api', () => ({
  getOrders: vi.fn(),
}));

const mockedGetOrders = vi.mocked(getOrders);

const baseOrders = [
  {
    orderId: 'o1',
    userId: 'u1',
    status: 'PENDING' as const,
    totalPrice: 20000,
    itemCount: 1,
    firstItemName: 'A',
    createdAt: '2026-04-13T04:00:00Z',
  },
  {
    orderId: 'o2',
    userId: 'u1',
    status: 'CANCELLED' as const,
    totalPrice: 99999,
    itemCount: 1,
    firstItemName: 'B',
    createdAt: '2026-04-13T04:00:00Z',
  },
  {
    orderId: 'o3',
    userId: 'u1',
    status: 'CONFIRMED' as const,
    totalPrice: 5000,
    itemCount: 1,
    firstItemName: 'C',
    createdAt: '2026-04-10T04:00:00Z',
  },
];

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('TodayOrdersKpi', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-04-13T05:00:00Z'));
    mockedGetOrders.mockReset();
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it('오늘 주문 수와 매출을 표시한다 (취소/타일 제외)', async () => {
    mockedGetOrders.mockResolvedValue({
      content: baseOrders,
      totalElements: 3,
      page: 0,
      size: 100,
    });
    render(<TodayOrdersKpi />, { wrapper: createWrapper() });
    expect(await screen.findByText('1건')).toBeInTheDocument();
    expect(screen.getByText('20,000원')).toBeInTheDocument();
  });

  it('totalElements가 PAGE_SIZE를 초과하면 경고 문구를 포함한다', async () => {
    mockedGetOrders.mockResolvedValue({
      content: baseOrders,
      totalElements: 500,
      page: 0,
      size: 100,
    });
    render(<TodayOrdersKpi />, { wrapper: createWrapper() });
    expect(await screen.findByText(/최근 100건 기준/)).toBeInTheDocument();
  });
});
