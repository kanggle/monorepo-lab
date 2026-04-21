import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import OrdersPage from '@/app/(admin)/orders/page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/features/order-management/api/order-api', () => ({
  getOrders: vi.fn().mockResolvedValue({
    content: [
      { orderId: 'o1', userId: 'u1', status: 'PENDING', totalPrice: 30000, itemCount: 2, createdAt: '2026-03-20T10:00:00Z' },
    ],
    totalElements: 1,
    page: 0,
    size: 20,
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

describe('OrdersPage', () => {
  it('Suspense fallback으로 로딩 스피너를 표시한다', () => {
    render(<OrdersPage />, { wrapper: createWrapper() });

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('로딩 중...')).toBeInTheDocument();
  });

  it('데이터 로드 후 주문 목록을 표시한다', async () => {
    render(<OrdersPage />, { wrapper: createWrapper() });

    expect(await screen.findByText('30,000원')).toBeInTheDocument();
  });
});
