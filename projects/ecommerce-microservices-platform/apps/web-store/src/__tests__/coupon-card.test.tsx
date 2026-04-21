import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { CouponSummary } from '@repo/types';
import { CouponCard } from '@/features/coupon/ui/CouponCard';

const MOCK_FIXED_COUPON: CouponSummary = {
  couponId: 'coupon-1',
  promotionId: 'promo-1',
  promotionName: '봄맞이 할인',
  discountType: 'FIXED',
  discountValue: 5000,
  maxDiscountAmount: 0,
  status: 'ISSUED',
  issuedAt: '2026-03-01T00:00:00Z',
  expiresAt: '2026-04-30T23:59:59Z',
};

const MOCK_PERCENTAGE_COUPON: CouponSummary = {
  couponId: 'coupon-2',
  promotionId: 'promo-2',
  promotionName: '여름 특가',
  discountType: 'PERCENTAGE',
  discountValue: 10,
  maxDiscountAmount: 10000,
  status: 'ISSUED',
  issuedAt: '2026-03-01T00:00:00Z',
  expiresAt: '2026-06-30T23:59:59Z',
};

const MOCK_USED_COUPON: CouponSummary = {
  ...MOCK_FIXED_COUPON,
  couponId: 'coupon-3',
  status: 'USED',
};

const MOCK_EXPIRED_COUPON: CouponSummary = {
  ...MOCK_FIXED_COUPON,
  couponId: 'coupon-4',
  status: 'EXPIRED',
};

describe('CouponCard', () => {
  it('정액 할인 쿠폰 정보를 표시한다', () => {
    render(<CouponCard coupon={MOCK_FIXED_COUPON} />);

    expect(screen.getByText('5,000원 할인')).toBeInTheDocument();
    expect(screen.getByText('봄맞이 할인')).toBeInTheDocument();
    expect(screen.getByText('사용가능')).toBeInTheDocument();
    expect(screen.getByText(/2026/)).toBeInTheDocument();
  });

  it('정률 할인 쿠폰에 최대 할인금액을 표시한다', () => {
    render(<CouponCard coupon={MOCK_PERCENTAGE_COUPON} />);

    expect(screen.getByText('10% 할인')).toBeInTheDocument();
    expect(screen.getByText('최대 10,000원')).toBeInTheDocument();
  });

  it('사용완료 상태를 표시한다', () => {
    render(<CouponCard coupon={MOCK_USED_COUPON} />);

    expect(screen.getByText('사용완료')).toBeInTheDocument();
  });

  it('만료 상태를 표시한다', () => {
    render(<CouponCard coupon={MOCK_EXPIRED_COUPON} />);

    expect(screen.getByText('만료')).toBeInTheDocument();
  });

  it('사용완료/만료 쿠폰은 반투명하게 표시된다', () => {
    const { container } = render(<CouponCard coupon={MOCK_USED_COUPON} />);

    const card = container.querySelector('[data-testid="coupon-card"]');
    expect(card).toHaveStyle({ opacity: '0.5' });
  });

  it('selectable 모드에서 클릭하면 onSelect를 호출한다', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();

    render(<CouponCard coupon={MOCK_FIXED_COUPON} selectable onSelect={onSelect} />);

    await user.click(screen.getByTestId('coupon-card'));
    expect(onSelect).toHaveBeenCalledWith('coupon-1');
  });

  it('사용 불가 쿠폰은 selectable이어도 클릭할 수 없다', async () => {
    const user = userEvent.setup();
    const onSelect = vi.fn();

    render(<CouponCard coupon={MOCK_USED_COUPON} selectable onSelect={onSelect} />);

    await user.click(screen.getByTestId('coupon-card'));
    expect(onSelect).not.toHaveBeenCalled();
  });

  it('선택된 상태일 때 aria-pressed가 true이다', () => {
    render(<CouponCard coupon={MOCK_FIXED_COUPON} selectable selected onSelect={vi.fn()} />);

    expect(screen.getByTestId('coupon-card')).toHaveAttribute('aria-pressed', 'true');
  });
});
