import type { OrderDetail } from '@repo/types';
import { PriceDisplay } from '@/shared/ui';

interface Props {
  items: OrderDetail['items'];
  totalPrice: OrderDetail['totalPrice'];
}

export function OrderItemsSection({ items, totalPrice }: Props) {
  return (
    <section style={{ marginBottom: 'var(--space-8)' }}>
      <h2 className="section-title">주문 상품</h2>
      {items.map((item) => (
        <div
          key={`${item.productId}-${item.variantId}`}
          style={{ display: 'flex', justifyContent: 'space-between', padding: 'var(--space-2) 0', borderBottom: '1px solid var(--color-border-light)' }}
        >
          <span>{item.productName} ({item.optionName}) × {item.quantity}</span>
          <PriceDisplay amount={item.unitPrice * item.quantity} className="price" />
        </div>
      ))}
      <div style={{ textAlign: 'right', marginTop: 'var(--space-2)', fontWeight: 'var(--font-weight-bold)', fontSize: 'var(--font-size-lg)' }}>
        합계: <PriceDisplay amount={totalPrice} unitStyle={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-normal)', color: 'var(--color-text-secondary)', marginLeft: '2px' }} />
      </div>
    </section>
  );
}
