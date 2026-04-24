import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
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
import MyCouponsPage from '@/app/(store)/my/coupons/page';

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
];

function createPaginatedResponse(
  content: CouponSummary[],
): PaginatedResponse<CouponSummary> {
  return { content, page: 0, size: 20, totalElements: content.length };
}

describe('MyCouponsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('쿠폰 목록 페이지를 렌더링한다', async () => {
    mockGetMyCoupons.mockResolvedValueOnce(createPaginatedResponse(MOCK_COUPONS));

    render(
      <TestQueryProvider>
        <MyCouponsPage />
      </TestQueryProvider>,
    );

    expect(screen.getByText('쿠폰')).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getAllByTestId('coupon-card')).toHaveLength(1);
    });
    expect(screen.getByText('봄맞이 할인')).toBeInTheDocument();
  });

  it('쿠폰이 없으면 빈 상태를 표시한다', async () => {
    mockGetMyCoupons.mockResolvedValueOnce(createPaginatedResponse([]));

    render(
      <TestQueryProvider>
        <MyCouponsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('empty-state')).toBeInTheDocument();
    });
  });

  it('상태 필터 버튼들을 표시한다', async () => {
    mockGetMyCoupons.mockResolvedValueOnce(createPaginatedResponse(MOCK_COUPONS));

    render(
      <TestQueryProvider>
        <MyCouponsPage />
      </TestQueryProvider>,
    );

    await waitFor(() => {
      expect(screen.getByTestId('status-filter')).toBeInTheDocument();
    });
    expect(screen.getByText('전체')).toBeInTheDocument();
    expect(screen.getByText('사용가능')).toBeInTheDocument();
    expect(screen.getByText('사용완료')).toBeInTheDocument();
    expect(screen.getByText('만료')).toBeInTheDocument();
  });
});
