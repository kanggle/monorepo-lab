'use client';

import { Skeleton } from '@/shared/ui/Skeleton';
import { useShippingTracking } from '../model/use-shipping-tracking';
import { getStepIndex, getDeliveredDate } from '../lib/shipping-steps';
import { ShippingStepIndicator } from './ShippingStepIndicator';

interface Props {
  orderId: string;
}

export function ShippingTracker({ orderId }: Props) {
  const { shipping, isLoading, isNotFound, error } = useShippingTracking(orderId);

  if (isLoading) {
    return (
      <section style={{ marginBottom: 'var(--space-8)' }}>
        <Skeleton width="110px" height="18px" />
        <div style={{ marginTop: 'var(--space-4)', display: 'flex', gap: 'var(--space-4)' }}>
          {Array.from({ length: 4 }).map((_, i) => (
            <Skeleton key={i} width="60px" height="60px" borderRadius="var(--radius-md)" />
          ))}
        </div>
      </section>
    );
  }

  if (error) {
    return (
      <section style={{ marginBottom: 'var(--space-8)' }}>
        <h2 className="section-title">배송 추적</h2>
        <p style={{ color: 'var(--color-error)' }}>{error}</p>
      </section>
    );
  }

  if (isNotFound) {
    return (
      <section style={{ marginBottom: 'var(--space-8)' }}>
        <h2 className="section-title">배송 추적</h2>
        <p style={{ color: 'var(--color-text-secondary)' }}>
          배송 준비 중입니다. 배송이 시작되면 추적 정보가 표시됩니다.
        </p>
      </section>
    );
  }

  if (!shipping) return null;

  const currentIndex = getStepIndex(shipping.status);
  const deliveredDate = getDeliveredDate(shipping);
  const showTrackingInfo = currentIndex >= 1; // SHIPPED 이후

  return (
    <section style={{ marginBottom: 'var(--space-8)' }}>
      <h2 className="section-title">배송 추적</h2>

      <ShippingStepIndicator currentIndex={currentIndex} />

      {/* Tracking Info */}
      {showTrackingInfo && (
        <div style={{ marginTop: 'var(--space-2)' }}>
          <p style={{ margin: 'var(--space-1) 0', color: 'var(--color-text-secondary)' }}>
            택배사: {shipping.carrier ?? '정보 없음'}
          </p>
          <p style={{ margin: 'var(--space-1) 0', color: 'var(--color-text-secondary)' }}>
            운송장 번호: {shipping.trackingNumber ?? '정보 없음'}
          </p>
        </div>
      )}

      {/* Delivered Date */}
      {deliveredDate && (
        <p style={{ margin: 'var(--space-1) 0', color: 'var(--color-text-secondary)' }}>
          배송 완료일: {new Date(deliveredDate).toLocaleString('ko-KR')}
        </p>
      )}
    </section>
  );
}
