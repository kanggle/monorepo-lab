import Link from 'next/link';
import type { OrderSummary } from '@repo/types';
import { OrderStatusBadge } from './OrderStatusBadge';
import { formatDateTime } from '@/shared/lib';

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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <p style={{ margin: '0 0 var(--space-1)', fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
            {formatDateTime(order.createdAt)}
          </p>
          {order.firstItemName && (
            <p style={{
              margin: '0 0 var(--space-1)',
              fontSize: 'var(--font-size-sm)',
              fontWeight: 'var(--font-weight-semibold)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
              maxWidth: '280px',
            }}>
              {order.firstItemName}{order.itemCount > 1 ? ` 외 ${order.itemCount - 1}건` : ''}
            </p>
          )}
          <p style={{ margin: 0, fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
            {order.totalPrice.toLocaleString()}원
          </p>
        </div>
        <OrderStatusBadge status={order.status} />
      </div>
    </Link>
  );
}
