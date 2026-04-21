'use client';

import { Suspense } from 'react';
import { useSearchParams } from 'next/navigation';
import { useRequireAuth } from '@/features/auth';
import { PaymentWidget } from '@/features/checkout';
import { NarrowContainer } from '@/shared/ui';

function PaymentPageContent() {
  const searchParams = useSearchParams();
  const { isReady } = useRequireAuth();

  const orderId = searchParams.get('orderId');
  const amount = Number(searchParams.get('amount'));
  const orderName = searchParams.get('orderName') ?? '주문';

  if (!isReady) return null;

  if (!orderId || !amount || isNaN(amount)) {
    return (
      <div className="container" style={{ paddingTop: 'var(--space-16)', textAlign: 'center' }}>
        <p style={{ color: 'var(--color-error)' }}>잘못된 접근입니다.</p>
      </div>
    );
  }

  return (
    <NarrowContainer>
      <h1 className="page-title">결제하기</h1>
      <PaymentWidget orderId={orderId} amount={amount} orderName={orderName} />
    </NarrowContainer>
  );
}

export default function PaymentPage() {
  return (
    <Suspense fallback={null}>
      <PaymentPageContent />
    </Suspense>
  );
}
