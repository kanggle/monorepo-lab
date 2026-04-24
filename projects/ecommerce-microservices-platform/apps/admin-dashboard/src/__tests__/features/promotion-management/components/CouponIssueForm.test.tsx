import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { CouponIssueForm } from '@/features/promotion-management/components/CouponIssueForm';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn() }),
}));

const mockIssueCoupons = vi.fn().mockResolvedValue({ issuedCount: 3 });

vi.mock('@/features/promotion-management/api/promotion-api', () => ({
  issueCoupons: (...args: unknown[]) => mockIssueCoupons(...args),
}));

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

describe('CouponIssueForm', () => {
  beforeEach(() => {
    mockIssueCoupons.mockClear().mockResolvedValue({ issuedCount: 3 });
  });

  it('사용자 ID 입력 필드를 표시한다', () => {
    render(<CouponIssueForm promotionId="p1" />, { wrapper: createWrapper() });

    expect(screen.getByLabelText('대상 사용자 ID')).toBeInTheDocument();
  });

  it('사용자 ID가 없으면 발급 버튼이 비활성화된다', () => {
    render(<CouponIssueForm promotionId="p1" />, { wrapper: createWrapper() });

    const button = screen.getByRole('button', { name: /쿠폰 발급/i });
    expect(button).toBeDisabled();
  });

  it('사용자 ID 입력 후 발급 인원 수가 표시된다', async () => {
    render(<CouponIssueForm promotionId="p1" />, { wrapper: createWrapper() });

    await userEvent.type(screen.getByLabelText('대상 사용자 ID'), 'user1\nuser2\nuser3');

    expect(screen.getByText('쿠폰 발급 (3명)')).toBeInTheDocument();
  });

  it('쉼표로 구분한 사용자 ID도 인식한다', async () => {
    render(<CouponIssueForm promotionId="p1" />, { wrapper: createWrapper() });

    await userEvent.type(screen.getByLabelText('대상 사용자 ID'), 'user1,user2');

    expect(screen.getByText('쿠폰 발급 (2명)')).toBeInTheDocument();
  });

  it('발급 성공 시 성공 메시지를 표시한다', async () => {
    render(<CouponIssueForm promotionId="p1" />, { wrapper: createWrapper() });

    await userEvent.type(screen.getByLabelText('대상 사용자 ID'), 'user1\nuser2\nuser3');
    await userEvent.click(screen.getByRole('button', { name: /쿠폰 발급/i }));

    await waitFor(() => {
      expect(screen.getByText('3건의 쿠폰이 발급되었습니다.')).toBeInTheDocument();
    });
  });

  it('발급 실패 시 에러 메시지를 표시한다', async () => {
    mockIssueCoupons.mockRejectedValue({ code: 'PROMOTION_NOT_ACTIVE', message: '프로모션이 활성 상태가 아닙니다.' });

    render(<CouponIssueForm promotionId="p1" />, { wrapper: createWrapper() });

    await userEvent.type(screen.getByLabelText('대상 사용자 ID'), 'user1');
    await userEvent.click(screen.getByRole('button', { name: /쿠폰 발급/i }));

    await waitFor(() => {
      expect(screen.getByText('프로모션이 활성 상태가 아닙니다.')).toBeInTheDocument();
    });
  });
});
