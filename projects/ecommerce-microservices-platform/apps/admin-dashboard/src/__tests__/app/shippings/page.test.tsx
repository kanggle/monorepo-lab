import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import ShippingsPage from '@/app/(admin)/shippings/page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
}));

vi.mock('@/features/shipping-management/api/shipping-api', () => ({
  getShippings: vi.fn().mockResolvedValue({
    content: [
      {
        shippingId: 's1',
        orderId: 'order-1234-5678-abcd',
        status: 'PREPARING',
        trackingNumber: null,
        carrier: null,
        createdAt: '2026-04-01T10:00:00Z',
        updatedAt: '2026-04-01T10:00:00Z',
      },
    ],
    totalElements: 1,
    page: 0,
    size: 20,
  }),
  updateShippingStatus: vi.fn(),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('ShippingsPage', () => {
  it('Suspense fallback으로 로딩 스피너를 표시한다', () => {
    render(<ShippingsPage />, { wrapper: createWrapper() });

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('로딩 중...')).toBeInTheDocument();
  });

  it('데이터 로드 후 배송 목록을 표시한다', async () => {
    render(<ShippingsPage />, { wrapper: createWrapper() });

    expect(await screen.findByText('order-12...')).toBeInTheDocument();
  });

  it('페이지 타이틀이 배송 관리로 표시된다', async () => {
    render(<ShippingsPage />, { wrapper: createWrapper() });

    expect(await screen.findByText('배송 관리')).toBeInTheDocument();
  });
});
