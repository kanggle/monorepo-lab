import type { CheckoutCartItem } from '../model/types';
import { PriceDisplay } from '@/shared/ui';

interface OrderItemsSectionProps {
  items: CheckoutCartItem[];
  totalAmount: number;
  discountAmount?: number;
}

export function OrderItemsSection({ items, totalAmount, discountAmount = 0 }: OrderItemsSectionProps) {
  const finalAmount = totalAmount - discountAmount;
  return (
    <section style={{ marginBottom: 'var(--space-8)' }}>
      <h2 className="section-title">주문 상품</h2>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
        {items.map((item) => (
          <div
            key={`${item.productId}-${item.variantId}`}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: 'var(--space-3)',
              padding: 'var(--space-3)',
              border: '1px solid var(--color-border-light)',
              borderRadius: 'var(--radius-md)',
            }}
          >
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-medium)' }}>
                {item.productName}
              </div>
              <div style={{ fontSize: 'var(--font-size-xs)', color: 'var(--color-text-secondary)' }}>
                {item.optionName} × {item.quantity}
              </div>
            </div>
            <span style={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-bold)', whiteSpace: 'nowrap', flexShrink: 0 }} className="price">
              <PriceDisplay amount={item.price * item.quantity} />
            </span>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 'var(--space-4)', textAlign: 'right' }}>
        <div style={{ fontWeight: 'var(--font-weight-bold)', fontSize: 'var(--font-size-lg)' }}>
          상품 합계: <span className="price"><PriceDisplay amount={totalAmount} unitStyle={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-normal)', color: 'var(--color-text-secondary)', marginLeft: '2px' }} /></span>
        </div>
        {discountAmount > 0 && (
          <div
            data-testid="discount-amount"
            style={{
              marginTop: 'var(--space-2)',
              fontSize: 'var(--font-size-md)',
              color: 'var(--color-danger)',
            }}
          >
            쿠폰 할인: <span>-<PriceDisplay amount={discountAmount} unitStyle={{ fontSize: 'var(--font-size-sm)', marginLeft: '2px' }} /></span>
          </div>
        )}
        <div style={{
          marginTop: 'var(--space-2)',
          fontWeight: 'var(--font-weight-bold)',
          fontSize: 'var(--font-size-lg)',
        }}>
          결제 금액: <span className="price"><PriceDisplay amount={finalAmount} unitStyle={{ fontSize: 'var(--font-size-sm)', fontWeight: 'var(--font-weight-normal)', color: 'var(--color-text-secondary)', marginLeft: '2px' }} /></span>
        </div>
      </div>
    </section>
  );
}
