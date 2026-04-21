import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PendingOrdersKpi } from '@/widgets/dashboard/PendingOrdersKpi';
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

describe('PendingOrdersKpi', () => {
  beforeEach(() => {
    mockedGetOrders.mockReset();
  });

  it('PENDING과 CONFIRMED totalElements 합을 표시한다', async () => {
    mockedGetOrders
      .mockResolvedValueOnce({ content: [], page: 0, size: 1, totalElements: 5 })
      .mockResolvedValueOnce({ content: [], page: 0, size: 1, totalElements: 3 });

    render(<PendingOrdersKpi />, { wrapper: createWrapper() });

    expect(await screen.findByText('8건')).toBeInTheDocument();
    expect(screen.getByText('PENDING + CONFIRMED')).toBeInTheDocument();
  });

  it('로딩 중 스켈레톤을 표시한다', () => {
    mockedGetOrders.mockImplementation(() => new Promise(() => {}));
    render(<PendingOrdersKpi />, { wrapper: createWrapper() });
    expect(screen.getByLabelText('로딩 중')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockedGetOrders.mockRejectedValue(new Error('fail'));
    render(<PendingOrdersKpi />, { wrapper: createWrapper() });
    expect(await screen.findByText('주문 데이터를 불러오지 못했습니다.')).toBeInTheDocument();
  });
});
