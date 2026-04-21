'use client';

import type { ApplyCouponResponse } from '@repo/types';
import { ErrorMessage, EmptyState } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { useCouponSelection } from '../model/use-coupon-selection';
import { formatDiscountValue } from '../lib/format-discount';
import { CouponCard } from './CouponCard';

interface CouponSelectorProps {
  orderAmount: number;
  onCouponApplied: (result: ApplyCouponResponse | null) => void;
}

export function CouponSelector({ orderAmount, onCouponApplied }: CouponSelectorProps) {
  const {
    isOpen,
    selectedCouponId,
    expiredMessage,
    coupons,
    isLoading,
    isError,
    refetch,
    selectedCoupon,
    handleSelect,
    handleRemoveCoupon,
    toggleOpen,
  } = useCouponSelection({ orderAmount, onCouponApplied });

  return (
    <section style={{ marginBottom: 'var(--space-8)' }}>
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 'var(--space-3)',
      }}>
        <h2 className="section-title" style={{ margin: 0 }}>쿠폰</h2>
        <button
          type="button"
          className="btn"
          style={{ fontSize: 'var(--font-size-sm)' }}
          onClick={toggleOpen}
        >
          {isOpen ? '닫기' : selectedCoupon ? '변경' : '쿠폰 선택'}
        </button>
      </div>

      {selectedCoupon && !isOpen && (
        <div
          data-testid="selected-coupon"
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            padding: 'var(--space-3) var(--space-4)',
            border: '1px solid var(--color-primary)',
            borderRadius: 'var(--radius-md)',
            background: 'var(--color-primary-50, #f0f4ff)',
          }}
        >
          <div>
            <div style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-semibold)' }}>
              {selectedCoupon.promotionName}
            </div>
            <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
              {formatDiscountValue(selectedCoupon)}
            </div>
          </div>
          <button
            type="button"
            className="btn"
            style={{ fontSize: 'var(--font-size-xs)' }}
            onClick={handleRemoveCoupon}
          >
            해제
          </button>
        </div>
      )}

      {isOpen && (
        <div data-testid="coupon-selector-list">
          {isLoading && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
              {Array.from({ length: 2 }).map((_, i) => (
                <div key={i} style={{ padding: 'var(--space-3)', border: '1px solid var(--color-border-light)', borderRadius: 'var(--radius-md)' }}>
                  <Skeleton width="50%" height="18px" />
                  <div style={{ marginTop: 'var(--space-2)' }}>
                    <Skeleton width="70%" height="14px" />
                  </div>
                </div>
              ))}
            </div>
          )}
          {isError && <ErrorMessage message="쿠폰 목록을 불러오는데 실패했습니다." onRetry={() => refetch()} />}
          {!isLoading && !isError && coupons.length === 0 && (
            <EmptyState message="사용 가능한 쿠폰이 없습니다." />
          )}
          {expiredMessage && (
            <div
              data-testid="coupon-expired-message"
              role="alert"
              style={{
                padding: 'var(--space-2) var(--space-3)',
                marginBottom: 'var(--space-2)',
                fontSize: 'var(--font-size-sm)',
                color: 'var(--color-danger)',
                border: '1px solid var(--color-danger)',
                borderRadius: 'var(--radius-sm)',
                background: 'var(--color-danger-50, #fff5f5)',
              }}
            >
              {expiredMessage}
            </div>
          )}
          {coupons.map((coupon) => (
            <CouponCard
              key={coupon.couponId}
              coupon={coupon}
              selectable
              selected={coupon.couponId === selectedCouponId}
              onSelect={handleSelect}
            />
          ))}
        </div>
      )}
    </section>
  );
}
