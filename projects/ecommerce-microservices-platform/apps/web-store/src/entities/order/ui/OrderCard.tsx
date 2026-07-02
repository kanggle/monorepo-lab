import Link from 'next/link';
import type { OrderSummary } from '@repo/types';
import { OrderStatusBadge } from './OrderStatusBadge';

interface OrderCardProps {
  order: OrderSummary;
}

export function OrderCard({ order }: OrderCardProps) {
  return (
    <Link
      href={`/my/orders/${order.orderId}`}
      className="card"
      style={{
        display: 'block',
        padding: 'var(--space-4) var(--space-5)',
        textDecoration: 'none',
        color: 'inherit',
        marginBottom: 'var(--space-3)',
      }}
    >
      {/* Line 1: date (left) + status badge (right). */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 'var(--space-1)' }}>
        <p style={{ margin: 0, fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
          {new Date(order.createdAt).toLocaleString('ko-KR')}
        </p>
        <OrderStatusBadge status={order.status} />
      </div>
      {/* Line 2: product name · total price on a single truncating line. Name and
          price are separate spans so each keeps its own textContent + styling. */}
      <p style={{
        margin: 0,
        fontSize: 'var(--font-size-sm)',
        overflow: 'hidden',
        textOverflow: 'ellipsis',
        whiteSpace: 'nowrap',
      }}>
        {order.firstItemName && (
          <>
            <span style={{ fontWeight: 'var(--font-weight-semibold)' }}>
              {order.firstItemName}{order.itemCount > 1 ? ` 외 ${order.itemCount - 1}건` : ''}
            </span>
            <span style={{ color: 'var(--color-text-secondary)' }}> · </span>
          </>
        )}
        <span style={{ color: 'var(--color-text-secondary)' }}>
          {order.totalPrice.toLocaleString()}원
        </span>
      </p>
    </Link>
  );
}
