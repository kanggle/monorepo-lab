import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import PromotionsPage from '@/app/(admin)/promotions/page';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
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
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('PromotionsPage', () => {
  it('Suspense fallback으로 로딩 스피너를 표시한다', () => {
    render(<PromotionsPage />, { wrapper: createWrapper() });

    expect(screen.getByRole('status')).toBeInTheDocument();
    expect(screen.getByText('로딩 중...')).toBeInTheDocument();
  });

  it('페이지 제목을 표시한다', () => {
    render(<PromotionsPage />, { wrapper: createWrapper() });

    expect(screen.getByText('프로모션 관리')).toBeInTheDocument();
  });

  it('프로모션 등록 버튼을 표시한다', () => {
    render(<PromotionsPage />, { wrapper: createWrapper() });

    expect(screen.getByText('프로모션 등록')).toBeInTheDocument();
  });

  it('데이터 로드 후 프로모션 목록을 표시한다', async () => {
    render(<PromotionsPage />, { wrapper: createWrapper() });

    expect(await screen.findByText('여름 세일')).toBeInTheDocument();
  });
});
