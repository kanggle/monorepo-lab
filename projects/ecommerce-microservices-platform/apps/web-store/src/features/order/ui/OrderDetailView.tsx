'use client';

import { OrderStatusBadge } from '@/entities/order';
import { ErrorMessage } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { DetailHeader } from '@/shared/ui';
import { useOrderDetail, CANCELLABLE_STATUSES } from '../model/use-order-detail';
import { OrderItemsSection } from './OrderItemsSection';
import { OrderShippingInfo } from './OrderShippingInfo';
import { OrderPaymentInfo } from './OrderPaymentInfo';
import { ShippingTracker } from './ShippingTracker';
import { formatDateTime } from '@/shared/lib';

interface Props {
  orderId: string;
}

function OrderDetailSkeleton() {
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-8)' }}>
        <Skeleton width="120px" height="28px" />
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          <Skeleton width="72px" height="24px" borderRadius="var(--radius-full)" />
          <Skeleton width="64px" height="30px" borderRadius="var(--radius-md)" />
        </div>
      </div>

      <section style={{ marginBottom: 'var(--space-8)' }}>
        <Skeleton width="100px" height="18px" />
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: 'var(--space-2) 0', borderBottom: '1px solid var(--color-border-light)' }}>
            <Skeleton width="60%" height="14px" />
            <Skeleton width="80px" height="14px" />
          </div>
        ))}
        <div style={{ textAlign: 'right', marginTop: 'var(--space-2)' }}>
          <Skeleton width="140px" height="18px" borderRadius="var(--radius-sm)" />
        </div>
      </section>

      <section style={{ marginBottom: 'var(--space-8)' }}>
        <Skeleton width="110px" height="18px" />
        <div style={{ marginTop: 'var(--space-2)', display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
          <Skeleton width="80px" height="14px" />
          <Skeleton width="120px" height="14px" />
          <Skeleton width="70%" height="14px" />
        </div>
      </section>

      <section style={{ marginBottom: 'var(--space-8)' }}>
        <Skeleton width="100px" height="18px" />
        <div style={{ marginTop: 'var(--space-2)', display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
          <Skeleton width="140px" height="14px" />
          <Skeleton width="120px" height="14px" />
        </div>
      </section>

      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
        <Skeleton width="180px" height="12px" />
        <Skeleton width="200px" height="12px" />
      </div>
    </div>
  );
}

export function OrderDetailView({ orderId }: Props) {
  const {
    order,
    payment,
    paymentError,
    isLoading,
    error,
    isCancelling,
    handleCancel,
    retryLoad,
  } = useOrderDetail(orderId);

  return (
    <main className="container" style={{ maxWidth: '800px', paddingTop: 'var(--space-8)', paddingBottom: 'var(--space-16)' }}>
      {isLoading && <OrderDetailSkeleton />}
      {error && <ErrorMessage message={error} onRetry={retryLoad} />}

      {order && (
        <div>
          <DetailHeader
            title="주문 상세"
            backHref="/my/orders"
            backLabel="주문내역"
            actions={<OrderStatusBadge status={order.status} />}
          />

          <OrderItemsSection items={order.items} totalPrice={order.totalPrice} />

          <OrderShippingInfo shippingAddress={order.shippingAddress} />

          <ShippingTracker orderId={orderId} />

          <OrderPaymentInfo payment={payment} paymentError={paymentError} />

          <section style={{ marginBottom: 'var(--space-8)' }}>
            <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
              주문일: {formatDateTime(order.createdAt)}
            </p>
            <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
              최종 수정일: {formatDateTime(order.updatedAt)}
            </p>
          </section>

          {CANCELLABLE_STATUSES.has(order.status) && (
            <button
              type="button"
              onClick={handleCancel}
              disabled={isCancelling}
              className="btn"
              style={{
                backgroundColor: 'var(--color-error)',
                color: 'var(--color-white)',
              }}
            >
              {isCancelling ? '취소 처리 중...' : '주문 취소'}
            </button>
          )}
        </div>
      )}
    </main>
  );
}
