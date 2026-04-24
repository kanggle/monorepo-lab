import type { OrderDetail } from '@repo/types';
import { maskPhone } from '@/shared/lib/mask-phone';

interface Props {
  shippingAddress: OrderDetail['shippingAddress'];
}

export function OrderShippingInfo({ shippingAddress }: Props) {
  return (
    <section style={{ marginBottom: 'var(--space-8)' }}>
      <h2 className="section-title">배송지 정보</h2>
      <p style={{ margin: 'var(--space-1) 0' }}>{shippingAddress.recipient}</p>
      <p style={{ margin: 'var(--space-1) 0' }}>{maskPhone(shippingAddress.phone)}</p>
      <p style={{ margin: 'var(--space-1) 0' }}>
        ({shippingAddress.zipCode}) {shippingAddress.address1} {shippingAddress.address2}
      </p>
    </section>
  );
}
