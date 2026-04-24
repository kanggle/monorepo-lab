import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RecentOrdersTable } from '@/widgets/dashboard/RecentOrdersTable';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

vi.mock('@/features/order-management/api/order-api', () => ({
  getOrders: vi.fn().mockResolvedValue({
    content: [
      {
        orderId: 'order-1234-abcd',
        userId: 'u1',
        status: 'PENDING',
        totalPrice: 50000,
        itemCount: 3,
        firstItemName: '노트북',
        createdAt: '2026-04-13T10:00:00Z',
      },
    ],
    totalElements: 1,
    page: 0,
    size: 5,
  }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('RecentOrdersTable', () => {
  it('최근 주문을 표시한다', async () => {
    render(<RecentOrdersTable />, { wrapper: createWrapper() });

    expect(await screen.findByText('50,000원')).toBeInTheDocument();
    expect(screen.getByText('노트북 외 2건')).toBeInTheDocument();
    expect(screen.getByText('order-12')).toBeInTheDocument();
  });
});
