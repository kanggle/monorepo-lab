import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { CouponSummary, PaginatedResponse } from '@repo/types';
import { TestQueryProvider } from './test-utils';

vi.mock('next/navigation', () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => '/checkout',
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
import { CouponSelector } from '@/features/coupon/ui/CouponSelector';

const mockGetMyCoupons = vi.mocked(getMyCoupons);

function futureDateISO(): string {
  const d = new Date();
  d.setFullYear(d.getFullYear() + 1);
  return d.toISOString();
}

function pastDateISO(): string {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  return d.toISOString();
}

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
    expiresAt: futureDateISO(),
  },
  {
    couponId: 'coupon-2',
    promotionId: 'promo-2',
    promotionName: '여름 특가',
    discountType: 'PERCENTAGE',
    discountValue: 10,
    maxDiscountAmount: 10000,
    status: 'ISSUED',
    issuedAt: '2026-03-01T00:00:00Z',
    expiresAt: futureDateISO(),
  },
];

function createPaginatedResponse(
  content: CouponSummary[],
): PaginatedResponse<CouponSummary> {
  return { content, page: 0, size: 100, totalElements: content.length };
}

describe('CouponSelector', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('쿠폰 선택 버튼을 표시한다', () => {
    mockGetMyCoupons.mockReturnValue(new Promise(() => {}));

    render(
      <TestQueryProvider>
        <CouponSelector orderAmount={30000} onCouponApplied={vi.fn()} />
      </TestQueryProvider>,
    );

    expect(screen.getByText('쿠폰 선택')).toBeInTheDocument();
  });

  it('쿠폰 선택 버튼 클릭 시 쿠폰 목록을 표시한다', async () => {
    mockGetMyCoupons.mockResolvedValue(createPaginatedResponse(MOCK_COUPONS));

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <CouponSelector orderAmount={30000} onCouponApplied={vi.fn()} />
      </TestQueryProvider>,
    );

    await user.click(screen.getByText('쿠폰 선택'));

    await waitFor(() => {
      expect(screen.getByTestId('coupon-selector-list')).toBeInTheDocument();
    });
    expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
  });

  it('FIXED 쿠폰 선택 시 클라이언트에서 할인을 계산한다', async () => {
    mockGetMyCoupons.mockResolvedValue(createPaginatedResponse(MOCK_COUPONS));

    const onCouponApplied = vi.fn();
    const user = userEvent.setup();

    render(
      <TestQueryProvider>
        <CouponSelector orderAmount={30000} onCouponApplied={onCouponApplied} />
      </TestQueryProvider>,
    );

    await user.click(screen.getByText('쿠폰 선택'));

    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
    });

    await user.click(screen.getAllByTestId('coupon-card')[0]);

    expect(onCouponApplied).toHaveBeenCalledWith({
      couponId: 'coupon-1',
      discountAmount: 5000,
      finalAmount: 25000,
    });
  });

  it('PERCENTAGE 쿠폰 선택 시 클라이언트에서 할인을 계산한다', async () => {
    mockGetMyCoupons.mockResolvedValue(createPaginatedResponse(MOCK_COUPONS));

    const onCouponApplied = vi.fn();
    const user = userEvent.setup();

    render(
      <TestQueryProvider>
        <CouponSelector orderAmount={30000} onCouponApplied={onCouponApplied} />
      </TestQueryProvider>,
    );

    await user.click(screen.getByText('쿠폰 선택'));

    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
    });

    // 30000 * 10% = 3000, maxDiscountAmount=10000 이므로 3000 적용
    await user.click(screen.getAllByTestId('coupon-card')[1]);

    expect(onCouponApplied).toHaveBeenCalledWith({
      couponId: 'coupon-2',
      discountAmount: 3000,
      finalAmount: 27000,
    });
  });

  it('사용 가능한 쿠폰이 없으면 빈 상태를 표시한다', async () => {
    mockGetMyCoupons.mockResolvedValue(createPaginatedResponse([]));

    const user = userEvent.setup();
    render(
      <TestQueryProvider>
        <CouponSelector orderAmount={30000} onCouponApplied={vi.fn()} />
      </TestQueryProvider>,
    );

    await user.click(screen.getByText('쿠폰 선택'));

    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
    expect(screen.getByText('사용 가능한 쿠폰이 없습니다.')).toBeInTheDocument();
  });

  it('선택한 쿠폰을 해제할 수 있다', async () => {
    mockGetMyCoupons.mockResolvedValue(createPaginatedResponse(MOCK_COUPONS));

    const onCouponApplied = vi.fn();
    const user = userEvent.setup();

    render(
      <TestQueryProvider>
        <CouponSelector orderAmount={30000} onCouponApplied={onCouponApplied} />
      </TestQueryProvider>,
    );

    // Open and select
    await user.click(screen.getByText('쿠폰 선택'));
    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
    });
    await user.click(screen.getAllByTestId('coupon-card')[0]);

    expect(onCouponApplied).toHaveBeenCalledWith({
      couponId: 'coupon-1',
      discountAmount: 5000,
      finalAmount: 25000,
    });

    // Close panel, should show selected coupon
    await user.click(screen.getByText('닫기'));

    await waitFor(() => {
      expect(screen.getByTestId('selected-coupon')).toBeInTheDocument();
    });

    // Remove coupon
    await user.click(screen.getByText('해제'));

    expect(onCouponApplied).toHaveBeenLastCalledWith(null);
  });

  it('만료된 쿠폰 선택 시 만료 메시지를 표시하고 선택하지 않는다', async () => {
    const expiredCoupon: CouponSummary = {
      couponId: 'coupon-expired',
      promotionId: 'promo-expired',
      promotionName: '만료된 쿠폰',
      discountType: 'FIXED',
      discountValue: 3000,
      maxDiscountAmount: 0,
      status: 'ISSUED',
      issuedAt: '2025-01-01T00:00:00Z',
      expiresAt: pastDateISO(),
    };

    mockGetMyCoupons.mockResolvedValue(
      createPaginatedResponse([expiredCoupon]),
    );

    const onCouponApplied = vi.fn();
    const user = userEvent.setup();

    render(
      <TestQueryProvider>
        <CouponSelector orderAmount={30000} onCouponApplied={onCouponApplied} />
      </TestQueryProvider>,
    );

    await user.click(screen.getByText('쿠폰 선택'));

    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(1);
    });

    await user.click(screen.getAllByTestId('coupon-card')[0]);

    expect(screen.getByTestId('coupon-expired-message')).toBeInTheDocument();
    expect(screen.getByText('쿠폰이 만료되었습니다')).toBeInTheDocument();
    expect(onCouponApplied).toHaveBeenCalledWith(null);
  });

  it('쿠폰 선택 시 apply API를 호출하지 않는다', async () => {
    mockGetMyCoupons.mockResolvedValue(createPaginatedResponse(MOCK_COUPONS));

    const { applyCoupon: mockApplyCoupon } = await import(
      '@/features/coupon/api/coupon-api'
    );

    const user = userEvent.setup();

    render(
      <TestQueryProvider>
        <CouponSelector orderAmount={30000} onCouponApplied={vi.fn()} />
      </TestQueryProvider>,
    );

    await user.click(screen.getByText('쿠폰 선택'));
    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(2);
    });

    await user.click(screen.getAllByTestId('coupon-card')[0]);

    expect(mockApplyCoupon).not.toHaveBeenCalled();
  });
});
