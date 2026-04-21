'use client';

import Link from 'next/link';
import { OrderStatusBadge } from '@/entities/order';
import { ErrorMessage } from '@repo/ui';
import { Skeleton } from '@/shared/ui/Skeleton';
import { useOrderDetail, CANCELLABLE_STATUSES } from '../model/use-order-detail';
import { OrderItemsSection } from './OrderItemsSection';
import { OrderShippingInfo } from './OrderShippingInfo';
import { OrderPaymentInfo } from './OrderPaymentInfo';
import { ShippingTracker } from './ShippingTracker';

interface Props {
  orderId: string;
}

function OrderDetailSkeleton() {
  return (
    <div>
      <Skeleton width="80px" height="14px" borderRadius="var(--radius-sm)" />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 'var(--space-4)', marginBottom: 'var(--space-8)' }}>
        <Skeleton width="120px" height="28px" />
        <Skeleton width="72px" height="24px" borderRadius="var(--radius-full)" />
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
          <Link href="/my/orders" style={{ display: 'inline-block', fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', marginBottom: 'var(--space-4)', textDecoration: 'none' }}>
            &larr; 주문내역
          </Link>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-8)' }}>
            <h1 className="page-title" style={{ margin: 0 }}>주문 상세</h1>
            <OrderStatusBadge status={order.status} />
          </div>

          <OrderItemsSection items={order.items} totalPrice={order.totalPrice} />

          <OrderShippingInfo shippingAddress={order.shippingAddress} />

          <ShippingTracker orderId={orderId} />

          <OrderPaymentInfo payment={payment} paymentError={paymentError} />

          <section style={{ marginBottom: 'var(--space-8)' }}>
            <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
              주문일: {new Date(order.createdAt).toLocaleString('ko-KR')}
            </p>
            <p style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
              최종 수정일: {new Date(order.updatedAt).toLocaleString('ko-KR')}
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
