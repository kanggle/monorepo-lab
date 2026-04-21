import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PromotionList } from '@/features/promotion-management/components/PromotionList';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
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
      {
        promotionId: 'p2',
        name: '겨울 프로모션',
        discountType: 'PERCENTAGE',
        discountValue: 10,
        maxIssuanceCount: 500,
        issuedCount: 0,
        startDate: '2026-12-01T00:00:00Z',
        endDate: '2026-12-31T00:00:00Z',
        status: 'SCHEDULED',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 2,
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

describe('PromotionList', () => {
  it('프로모션 목록을 테이블에 표시한다', async () => {
    render(<PromotionList />, { wrapper: createWrapper() });

    expect(await screen.findByText('여름 세일')).toBeInTheDocument();
    expect(screen.getByText('겨울 프로모션')).toBeInTheDocument();
  });

  it('할인 유형을 올바르게 표시한다', async () => {
    render(<PromotionList />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    expect(screen.getByText('정액')).toBeInTheDocument();
    expect(screen.getByText('정률')).toBeInTheDocument();
  });

  it('할인값을 올바르게 표시한다', async () => {
    render(<PromotionList />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    expect(screen.getByText('5,000원')).toBeInTheDocument();
    expect(screen.getByText('10%')).toBeInTheDocument();
  });

  it('발급 현황을 표시한다', async () => {
    render(<PromotionList />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    expect(screen.getByText('500 / 1000')).toBeInTheDocument();
    expect(screen.getByText('0 / 500')).toBeInTheDocument();
  });

  it('로딩 중일 때 스피너를 표시한다', () => {
    render(<PromotionList />, { wrapper: createWrapper() });
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('상태 필터를 표시한다', async () => {
    render(<PromotionList />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    expect(screen.getByRole('combobox')).toBeInTheDocument();
    expect(screen.getByText('전체 상태')).toBeInTheDocument();
  });
});
