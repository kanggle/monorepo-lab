'use client';

import { usePaymentConfirmation } from '@/features/checkout';

export default function PaymentSuccessPage() {
  const { status, errorMessage, goToCart, retry } = usePaymentConfirmation();

  if (status === 'invalid') {
    return (
      <div className="container" style={{ paddingTop: 'var(--space-16)', textAlign: 'center' }}>
        <p role="alert" style={{ color: 'var(--color-error)', marginBottom: 'var(--space-4)' }}>
          결제 정보가 올바르지 않습니다.
        </p>
        <button className="btn btn-primary" onClick={goToCart}>
          장바구니로 이동
        </button>
      </div>
    );
  }

  if (status === 'pending') {
    return (
      <div className="container" style={{ paddingTop: 'var(--space-16)', textAlign: 'center' }}>
        <p>결제 승인 중...</p>
      </div>
    );
  }

  if (status === 'error') {
    return (
      <div className="container" style={{ paddingTop: 'var(--space-16)', textAlign: 'center' }}>
        <p role="alert" style={{ color: 'var(--color-error)', marginBottom: 'var(--space-4)' }}>
          {errorMessage}
        </p>
        <button className="btn btn-primary" onClick={retry}>
          다시 시도
        </button>
      </div>
    );
  }

  return null;
}
