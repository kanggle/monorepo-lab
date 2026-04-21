'use client';

import { useTossPayment } from '../model/use-toss-payment';
import { PriceDisplay } from '@/shared/ui';

interface PaymentWidgetProps {
  orderId: string;
  amount: number;
  orderName: string;
}

export function PaymentWidget({ orderId, amount, orderName }: PaymentWidgetProps) {
  const { isReady, error, requestPayment } = useTossPayment();

  async function handlePayment() {
    try {
      await requestPayment({ orderId, amount, orderName });
    } catch {
      // Toss SDK handles redirect on error
    }
  }

  if (error) {
    return (
      <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}>
        <p role="alert" style={{ color: 'var(--color-error)', marginBottom: 'var(--space-4)' }}>
          {error}
        </p>
        <button
          className="btn btn-primary"
          onClick={() => window.location.reload()}
        >
          다시 시도
        </button>
      </div>
    );
  }

  return (
    <div style={{ textAlign: 'center', padding: 'var(--space-8)' }}>
      <p style={{ fontSize: 'var(--font-size-lg)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-2)' }}>
        {orderName}
      </p>
      <p style={{ fontSize: 'var(--font-size-2xl)', fontWeight: 'var(--font-weight-bold)', marginBottom: 'var(--space-6)' }}>
        <PriceDisplay amount={amount} className="price" unitStyle={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)', marginLeft: '2px' }} />
      </p>
      <button
        type="button"
        className="btn btn-accent btn-lg"
        style={{ width: '100%' }}
        disabled={!isReady}
        onClick={handlePayment}
      >
        {isReady ? '카드 결제하기' : '결제 준비 중...'}
      </button>
    </div>
  );
}
