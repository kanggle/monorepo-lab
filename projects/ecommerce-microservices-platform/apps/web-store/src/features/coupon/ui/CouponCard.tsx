'use client';

import type { CouponSummary, CouponStatus } from '@repo/types';
import { StatusBadge } from '@/shared/ui';
import { formatDiscountValue, formatMaxDiscount } from '../lib/format-discount';

const STATUS_LABELS: Record<CouponStatus, string> = {
  ISSUED: '사용가능',
  USED: '사용완료',
  EXPIRED: '만료',
};

const STATUS_COLORS: Record<CouponStatus, string> = {
  ISSUED: 'var(--color-success)',
  USED: 'var(--color-text-secondary)',
  EXPIRED: 'var(--color-danger)',
};

interface CouponCardProps {
  coupon: CouponSummary;
  selectable?: boolean;
  selected?: boolean;
  disabled?: boolean;
  onSelect?: (couponId: string) => void;
}

export function CouponCard({ coupon, selectable, selected, disabled, onSelect }: CouponCardProps) {
  const isDisabled = disabled || coupon.status !== 'ISSUED';
  const maxDiscount = formatMaxDiscount(coupon);

  function handleClick() {
    if (selectable && !isDisabled && onSelect) {
      onSelect(coupon.couponId);
    }
  }

  return (
    <div
      data-testid="coupon-card"
      role={selectable ? 'button' : undefined}
      tabIndex={selectable && !isDisabled ? 0 : undefined}
      aria-disabled={isDisabled || undefined}
      aria-pressed={selectable ? selected : undefined}
      onClick={handleClick}
      onKeyDown={selectable && !isDisabled ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleClick(); } } : undefined}
      style={{
        padding: 'var(--space-4) var(--space-5)',
        border: selected
          ? '2px solid var(--color-primary)'
          : '1px solid var(--color-border-light)',
        borderRadius: 'var(--radius-md)',
        marginBottom: 'var(--space-3)',
        opacity: isDisabled ? 0.5 : 1,
        cursor: selectable && !isDisabled ? 'pointer' : 'default',
        background: selected ? 'var(--color-primary-50, #f0f4ff)' : 'var(--color-white)',
        transition: 'border var(--transition-fast), background var(--transition-fast)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{
            fontSize: 'var(--font-size-lg)',
            fontWeight: 'var(--font-weight-bold)',
            marginBottom: 'var(--space-1)',
            color: isDisabled ? 'var(--color-text-secondary)' : 'var(--color-primary)',
          }}>
            {formatDiscountValue(coupon)}
          </div>
          <div style={{
            fontSize: 'var(--font-size-sm)',
            fontWeight: 'var(--font-weight-medium)',
            marginBottom: 'var(--space-1)',
          }}>
            {coupon.promotionName}
          </div>
          {maxDiscount && (
            <div style={{
              fontSize: 'var(--font-size-xs)',
              color: 'var(--color-text-secondary)',
              marginBottom: 'var(--space-1)',
            }}>
              {maxDiscount}
            </div>
          )}
          <div style={{
            fontSize: 'var(--font-size-xs)',
            color: 'var(--color-text-secondary)',
          }}>
            {new Date(coupon.expiresAt).toLocaleDateString('ko-KR')}까지
          </div>
        </div>
        <StatusBadge status={coupon.status} labels={STATUS_LABELS} colors={STATUS_COLORS} />
      </div>
    </div>
  );
}
