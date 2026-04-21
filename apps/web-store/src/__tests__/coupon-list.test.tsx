import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { CouponSummary, PaginatedResponse } from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/my/coupons',
}));

vi.mock('@repo/ui', () => ({
  LoadingSpinner: () => <div data-testid="loading-spinner">로딩 중...</div>,
  ErrorMessage: ({ message, onRetry }: { message: string; onRetry: () => void }) => (
    <div data-testid="error-message">
      {message}
      <button onClick={onRetry}>재시도</button>
    </div>
  ),
  EmptyState: ({ message }: { message: string }) => (
    <div data-testid="empty-state">{message}</div>
  ),
}));

vi.mock('@/features/coupon/api/coupon-api');

import { getMyCoupons } from '@/features/coupon/api/coupon-api';
import { CouponList } from '@/features/coupon/ui/CouponList';

const mockGetMyCoupons = vi.mocked(getMyCoupons);

const MOCK_COUPONS: CouponSummary[] = [
  {
    couponId: 'coupon-1',
    promotionId: 'promo-1',
    promotionName: '봄맞이 할인',
    discountType: 'FIXED',
    discountValue: 5000,
    maxDiscountAmount: 0,
    status: 'ISSUED',
    issuedAt: '2026-03-01T00:00:00Z',
    expiresAt: '2026-04-30T23:59:59Z',
  },
  {
    couponId: 'coupon-2',
    promotionId: 'promo-2',
    promotionName: '사용된 쿠폰',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    maxDiscountAmount: 10000,
    status: 'USED',
    issuedAt: '2026-02-01T00:00:00Z',
    expiresAt: '2026-03-31T23:59:59Z',
  },
];

function createPaginatedResponse(
  content: CouponSummary[],
  page = 0,
  size = 20,
  totalElements = content.length,
): PaginatedResponse<CouponSummary> {
  return { content, page, size, totalElements };
}

describe('CouponList', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('로딩 중일 때 쿠폰 카드가 표시되지 않는다', () => {
    mockGetMyCoupons.mockReturnValue(new Promise(() => {}));

    render(<TestQueryProvider><CouponList /></TestQueryProvider>);

    expect(screen.queryAllByTestId('coupon-card')).toHaveLength(0);
    expect(screen.queryByTestId('empty-state')).not.toBeInTheDocument();
  });

  it('쿠폰 목록을 렌더링한다', async () => {
    mockGetMyCoupons.mockResolvedValueOnce(createPaginatedResponse(MOCK_COUPONS));

    render(<TestQueryProvider><CouponList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
    });
    expect(screen.getByText('봄맞이 할인')).toBeInTheDocument();
    expect(screen.getByText('사용된 쿠폰')).toBeInTheDocument();
  });

  it('쿠폰이 없으면 빈 상태를 표시한다', async () => {
    mockGetMyCoupons.mockResolvedValueOnce(createPaginatedResponse([]));

    render(<TestQueryProvider><CouponList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
    expect(screen.getByText('보유한 쿠폰이 없습니다.')).toBeInTheDocument();
  });

  it('에러 발생 시 에러 메시지를 표시한다', async () => {
    mockGetMyCoupons.mockRejectedValueOnce(new Error('fail'));

    render(<TestQueryProvider><CouponList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });
    expect(screen.getByText('쿠폰 목록을 불러오는데 실패했습니다.')).toBeInTheDocument();
  });

  it('에러 후 재시도 버튼을 클릭하면 다시 로드한다', async () => {
    mockGetMyCoupons.mockRejectedValueOnce(new Error('fail'));

    const user = userEvent.setup();
    render(<TestQueryProvider><CouponList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getByTestId('error-message')).toBeInTheDocument();
    });

    mockGetMyCoupons.mockResolvedValueOnce(createPaginatedResponse(MOCK_COUPONS));
    await user.click(screen.getByText('재시도'));

    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
    });
  });

  it('상태 필터를 변경할 수 있다', async () => {
    mockGetMyCoupons.mockResolvedValueOnce(createPaginatedResponse(MOCK_COUPONS));

    const user = userEvent.setup();
    render(<TestQueryProvider><CouponList /></TestQueryProvider>);

    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
    });

    const issuedCoupons = [MOCK_COUPONS[0]];
    mockGetMyCoupons.mockResolvedValueOnce(createPaginatedResponse(issuedCoupons));

    const filterButtons = screen.getByTestId('status-filter');
    const issuedButton = filterButtons.querySelector('button:nth-child(2)') as HTMLElement;
    await user.click(issuedButton);

    await waitFor(() => {
      expect(mockGetMyCoupons).toHaveBeenCalledWith({ page: 0, size: 20, status: 'ISSUED' });
    });
  });

  describe('페이지네이션', () => {
    const MANY_COUPONS_RESPONSE = {
      content: MOCK_COUPONS,
      page: 0,
      size: 20,
      totalElements: 50,
    };

    it('페이지네이션 컨트롤을 표시한다', async () => {
      mockGetMyCoupons.mockResolvedValue(MANY_COUPONS_RESPONSE);

      render(<TestQueryProvider><CouponList /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
      });

      expect(screen.getByText('1 / 3')).toBeInTheDocument();
      expect(screen.getByLabelText('이전 페이지')).toBeInTheDocument();
      expect(screen.getByLabelText('다음 페이지')).toBeInTheDocument();
    });

    it('첫 페이지에서 이전 버튼이 비활성화된다', async () => {
      mockGetMyCoupons.mockResolvedValue(MANY_COUPONS_RESPONSE);

      render(<TestQueryProvider><CouponList /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
      });

      expect(screen.getByLabelText('이전 페이지')).toBeDisabled();
    });

    it('다음 버튼 클릭 시 다음 페이지를 로드한다', async () => {
      mockGetMyCoupons.mockResolvedValueOnce(MANY_COUPONS_RESPONSE);

      const user = userEvent.setup();
      render(<TestQueryProvider><CouponList /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
      });

      mockGetMyCoupons.mockResolvedValueOnce({ ...MANY_COUPONS_RESPONSE, page: 1 });
      await user.click(screen.getByLabelText('다음 페이지'));

      await waitFor(() => {
        expect(screen.getByText('2 / 3')).toBeInTheDocument();
      });
    });

    it('페이지 크기를 변경할 수 있다', async () => {
      mockGetMyCoupons.mockResolvedValue(MANY_COUPONS_RESPONSE);

      const user = userEvent.setup();
      render(<TestQueryProvider><CouponList /></TestQueryProvider>);

      await waitFor(() => {
        expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
      });

      await user.selectOptions(screen.getByLabelText('페이지 크기:'), '10');

      await waitFor(() => {
        expect(mockGetMyCoupons).toHaveBeenCalledWith({ page: 0, size: 10, status: undefined });
      });
    });
  });
});
