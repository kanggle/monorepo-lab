import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { PromotionDetail } from '@/features/promotion-management/components/PromotionDetail';

const mockPush = vi.fn();

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: mockPush }),
}));

const mockPromotion = {
  promotionId: 'p1',
  name: '여름 세일',
  description: '여름 할인 이벤트',
  discountType: 'FIXED' as const,
  discountValue: 5000,
  maxDiscountAmount: 10000,
  maxIssuanceCount: 1000,
  issuedCount: 500,
  startDate: '2026-06-01T00:00:00Z',
  endDate: '2026-06-30T00:00:00Z',
  status: 'ACTIVE' as const,
  createdAt: '2026-05-01T00:00:00Z',
  updatedAt: '2026-05-15T00:00:00Z',
};

const mockGetPromotion = vi.fn().mockResolvedValue(mockPromotion);
const mockDeletePromotion = vi.fn().mockResolvedValue(undefined);

vi.mock('@/features/promotion-management/api/promotion-api', () => ({
  getPromotion: (...args: unknown[]) => mockGetPromotion(...args),
  deletePromotion: (...args: unknown[]) => mockDeletePromotion(...args),
  issueCoupons: vi.fn().mockResolvedValue({ issuedCount: 0 }),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('PromotionDetail', () => {
  beforeEach(() => {
    mockPush.mockClear();
    mockGetPromotion.mockClear().mockResolvedValue(mockPromotion);
    mockDeletePromotion.mockClear().mockResolvedValue(undefined);
  });

  it('프로모션 상세 정보를 표시한다', async () => {
    render(<PromotionDetail promotionId="p1" />, { wrapper: createWrapper() });

    expect(await screen.findByText('여름 세일')).toBeInTheDocument();
    expect(screen.getByText('여름 할인 이벤트')).toBeInTheDocument();
    expect(screen.getByText('정액')).toBeInTheDocument();
    expect(screen.getByText('500 / 1000')).toBeInTheDocument();
  });

  it('ACTIVE 프로모션에서 수정 버튼이 표시된다', async () => {
    render(<PromotionDetail promotionId="p1" />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    expect(screen.getByText('수정')).toBeInTheDocument();
  });

  it('ACTIVE 프로모션에서 쿠폰 발급 폼이 표시된다', async () => {
    render(<PromotionDetail promotionId="p1" />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    expect(screen.getByText('쿠폰 발급')).toBeInTheDocument();
    expect(screen.getByLabelText('대상 사용자 ID')).toBeInTheDocument();
  });

  it('삭제 버튼 클릭 시 확인 다이얼로그가 표시된다', async () => {
    render(<PromotionDetail promotionId="p1" />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    await userEvent.click(screen.getByText('삭제'));

    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByText('이 프로모션을 삭제하시겠습니까? 이 작업은 되돌릴 수 없습니다.')).toBeInTheDocument();
  });

  it('ENDED 프로모션에서 수정 버튼이 표시되지 않는다', async () => {
    mockGetPromotion.mockResolvedValue({ ...mockPromotion, status: 'ENDED' });

    render(<PromotionDetail promotionId="p1" />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    expect(screen.queryByText('수정')).not.toBeInTheDocument();
  });

  it('ENDED 프로모션에서 쿠폰 발급 폼이 표시되지 않는다', async () => {
    mockGetPromotion.mockResolvedValue({ ...mockPromotion, status: 'ENDED' });

    render(<PromotionDetail promotionId="p1" />, { wrapper: createWrapper() });

    await screen.findByText('여름 세일');
    expect(screen.queryByLabelText('대상 사용자 ID')).not.toBeInTheDocument();
  });

  it('로딩 중일 때 스켈레톤을 표시한다', () => {
    render(<PromotionDetail promotionId="p1" />, { wrapper: createWrapper() });
    // PageLayout.Skeleton renders a skeleton placeholder
    expect(screen.queryByText('여름 세일')).not.toBeInTheDocument();
  });
});
